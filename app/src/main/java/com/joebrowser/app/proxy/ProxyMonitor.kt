package com.joebrowser.app.proxy

import android.content.Context
import com.joebrowser.app.data.model.Profile
import com.joebrowser.app.data.store.ProfileStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The proxy outage and rotation guard.
 *
 * A rotating proxy is the most dangerous thing in an anti-detect workflow: the session
 * keeps working, the account keeps running, and the address underneath it changes without
 * anybody noticing until the account is already flagged. This watcher polls the public
 * address of every profile that has a proxy and reports the two events that matter:
 *
 *  - the proxy stopped answering, which means the session is running on the device's own
 *    address and the user should know before they sign in;
 *  - the public address changed, which means the exit moved.
 *
 * Two consecutive failures are needed before an outage is reported. A single failed request
 * is usually a hiccup, and an alert that cries wolf gets ignored.
 */
class ProxyMonitor(
    context: Context,
    private val scope: CoroutineScope,
    private val onEvent: (ProxyEvent) -> Unit,
) {

    data class ProxyEvent(
        val profileId: String,
        val profileName: String,
        val kind: Kind,
        val from: String = "",
        val to: String = "",
        val detail: String = "",
    ) {
        enum class Kind { OFFLINE, BACK_ONLINE, ROTATED, FELL_BACK }
    }

    private val appContext = context.applicationContext
    private val state = ConcurrentHashMap<String, WatchState>()
    private val running = AtomicBoolean(false)
    private var job: Job? = null

    private class WatchState(
        var baselineIp: String = "",
        var fails: Int = 0,
        var down: Boolean = false,
        var lastCheckedAt: Long = 0L,
    )

    /** Starts the watcher. Safe to call twice. */
    fun start() {
        if (!running.compareAndSet(false, true)) return
        job = scope.launch {
            // The first check waits a moment so a freshly opened profile is not polled
            // before its proxy has had a chance to answer.
            delay(12_000)
            while (isActive) {
                runCatching { tick() }
                delay(60_000)
            }
        }
    }

    fun stop() {
        running.set(false)
        job?.cancel()
        job = null
    }

    /**
     * One pass over every profile that wants watching.
     */
    private suspend fun tick() {
        val profiles = ProfileStore(appContext).load()
        for (p in profiles) {
            if (!p.tools.proxyWatch) continue
            if (!p.proxy.enabled) continue
            runCatching { check(p) }
        }
    }

    /** Checks one profile and raises the event, if any. */
    suspend fun check(profile: Profile) {
        val result = ProxyTester.test(profile.proxy, fallbackDirect = false)
        val watch = state.getOrPut(profile.id) { WatchState() }
        watch.lastCheckedAt = System.currentTimeMillis()

        if (!result.ok || result.ip.isBlank()) {
            watch.fails++
            if (watch.fails >= 2 && !watch.down) {
                watch.down = true
                watch.fails = 0
                onEvent(
                    ProxyEvent(
                        profileId = profile.id,
                        profileName = profile.name,
                        kind = ProxyEvent.Kind.OFFLINE,
                        detail = result.error.ifBlank { "The proxy did not answer" },
                    )
                )
            }
            return
        }

        watch.fails = 0

        if (watch.baselineIp.isBlank()) {
            watch.baselineIp = result.ip
            return
        }

        if (watch.baselineIp != result.ip) {
            val from = watch.baselineIp
            watch.baselineIp = result.ip
            onEvent(
                ProxyEvent(
                    profileId = profile.id,
                    profileName = profile.name,
                    kind = ProxyEvent.Kind.ROTATED,
                    from = from,
                    to = result.ip,
                    detail = result.country.ifBlank { result.countryCode },
                )
            )
            return
        }

        if (watch.down) {
            watch.down = false
            onEvent(
                ProxyEvent(
                    profileId = profile.id,
                    profileName = profile.name,
                    kind = ProxyEvent.Kind.BACK_ONLINE,
                    to = result.ip,
                )
            )
        }
    }

    /** The address the watcher last saw for a profile, or an empty string. */
    fun baselineFor(profileId: String): String = state[profileId]?.baselineIp ?: ""
}
