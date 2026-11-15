package com.joebrowser.app.data.store

import android.content.Context
import com.joebrowser.app.core.AppPaths
import com.joebrowser.app.core.SecretBox
import com.joebrowser.app.data.model.AppSettings
import com.joebrowser.app.data.model.Profile
import com.joebrowser.app.fingerprint.FingerprintFactory
import com.joebrowser.app.fingerprint.FingerprintIdentity
import java.util.UUID

/**
 * The profile library.
 *
 * Each profile is one file, so a profile can be exported, backed up or deleted on its own
 * without touching the rest of the library.
 */
class ProfileStore(private val context: Context) {

    private val dir: java.io.File get() = AppPaths.profilesDir(context)

    /** Every profile, oldest first by creation. */
    fun load(): List<Profile> {
        val out = ArrayList<Profile>()
        val files = dir.listFiles { f -> f.isFile && f.name.endsWith(".json") } ?: return out
        for (f in files) {
            try {
                val p = JsonStore.json.decodeFromString(
                    Profile.serializer(), f.readText()
                )
                if (p.id.isNotBlank()) out.add(p)
            } catch (e: Exception) {
                // A single unreadable profile must not take the whole library with it.
            }
        }
        // A write that was interrupted leaves its temporary file behind. It is of no use
        // to anyone and it only makes the folder look untidy.
        runCatching {
            dir.listFiles { f -> f.isFile && f.name.contains(".tmp") }?.forEach { it.delete() }
        }
        out.sortBy { it.created }
        return out
    }

    fun find(id: String): Profile? = load().firstOrNull { it.id == id }

    /** Writes a profile. Returns false when the file could not be written. */
    fun save(profile: Profile): Boolean {
        val file = java.io.File(dir, profile.id + ".json")
        return JsonStore.write(context, file, JsonStore.pretty.encodeToString(Profile.serializer(), profile))
    }

    /** Removes a profile and its data, keeping a copy in the recycle bin when asked. */
    fun delete(profile: Profile, keepInTrash: Boolean): Boolean {
        return try {
            val dataDir = AppPaths.profileDir(context, profile.id)
            if (keepInTrash) {
                val bin = java.io.File(AppPaths.trashDir(context), profile.id + ".json")
                JsonStore.write(context, bin, JsonStore.pretty.encodeToString(Profile.serializer(), profile))
            }
            dataDir.deleteRecursively()
            java.io.File(dir, profile.id + ".json").delete()
            true
        } catch (e: Exception) {
            false
        }
    }

    /** The profiles in the recycle bin. */
    fun trash(): List<Profile> {
        val out = ArrayList<Profile>()
        val files = AppPaths.trashDir(context).listFiles { f -> f.isFile && f.name.endsWith(".json") }
            ?: return out
        for (f in files) {
            try {
                out.add(JsonStore.json.decodeFromString(Profile.serializer(), f.readText()))
            } catch (e: Exception) {}
        }
        return out
    }

    fun restoreFromTrash(profile: Profile): Boolean = save(profile)

    fun emptyTrash(): Int {
        var n = 0
        AppPaths.trashDir(context).listFiles()?.forEach {
            if (it.deleteRecursively()) n++
        }
        return n
    }

    /**
     * Creates a profile with a fresh, unique fingerprint.
     *
     * The uniqueness loop is the important part. A workspace where two profiles report the
     * same GPU, the same canvas noise and the same clock is one machine with two names,
     * and a checker that compares two profiles sees it immediately. The loop therefore
     * rolls again, and again, until the new profile shares nothing meaningful with the
     * ones already there.
     */
    fun create(
        name: String,
        os: String,
        engine: String,
        startUrl: String = Profile.DEFAULT_START_URL,
        group: String = "",
        proxy: com.joebrowser.app.data.model.ProxyConfig? = null,
        existing: List<Profile> = load(),
    ): Profile {
        var fp = FingerprintFactory.regenerate(
            com.joebrowser.app.data.model.FingerprintConfig(),
            os,
            proxy,
        )

        val pool = existing.map { it.fp }
        var best = fp
        var bestScore = -1
        for (attempt in 0 until 12) {
            val wide = attempt >= 8
            val candidate = FingerprintFactory.regenerate(
                com.joebrowser.app.data.model.FingerprintConfig(),
                os,
                proxy,
                widePool = wide,
                hardwareOnly = true,
            )
            var worst = 0
            for (other in pool) {
                val comparison = FingerprintIdentity.compare(candidate, other)
                if (comparison.score > worst) worst = comparison.score
                if (comparison.relation == FingerprintIdentity.Relation.Identical ||
                    comparison.relation == FingerprintIdentity.Relation.Linked
                ) {
                    worst = maxOf(worst, comparison.score)
                }
            }
            if (worst == 0) {
                best = candidate
                break
            }
            if (worst > bestScore) {
                bestScore = worst
                best = candidate
            }
        }
        fp = best
        FingerprintFactory.coerce(fp)

        // The build a profile reports is the build that is really running.
        //
        // A page can find out which Chromium it is talking to by asking whether the
        // engine understands a feature that arrived in a particular release. A profile
        // that claims a version the engine does not match is caught by that question,
        // and the answer is always the engine's own version.
        val engineVersion = com.joebrowser.app.browser.BrowserEngines.webViewVersion(context)
        val reported = if (com.joebrowser.app.fingerprint.BrowserVersions.isReal(engineVersion)) {
            engineVersion
        } else {
            com.joebrowser.app.fingerprint.BrowserVersions.LATEST_STABLE
        }
        fp.browserVersion = reported

        val now = System.currentTimeMillis()
        return Profile(
            id = UUID.randomUUID().toString().replace("-", ""),
            name = name,
            startUrl = startUrl,
            browserKind = engine,
            browserVersion = reported,
            group = group,
            created = now,
            lastOpened = 0L,
            fp = fp,
            proxy = proxy ?: com.joebrowser.app.data.model.ProxyConfig(),
        )
    }

    /** Copies a profile, giving the copy its own identity. */
    fun duplicate(source: Profile, existing: List<Profile> = load()): Profile {
        val copy = source.copy(
            id = UUID.randomUUID().toString().replace("-", ""),
            name = source.name + " copy",
            created = System.currentTimeMillis(),
            lastOpened = 0L,
            openCount = 0,
            launchedOnce = false,
        )
        val fp = FingerprintFactory.regenerate(
            source.fp,
            source.fp.os,
            source.proxy,
        )
        return copy.copy(fp = fp)
    }
}

/**
 * The application settings.
 */
class SettingsStore(private val context: Context) {

    private val file get() = AppPaths.settingsFile(context)

    fun load(): AppSettings = JsonStore.read(context, file, AppSettings()) { text ->
        JsonStore.json.decodeFromString(AppSettings.serializer(), text)
    }

    fun save(settings: AppSettings): Boolean =
        JsonStore.write(context, file, JsonStore.pretty.encodeToString(AppSettings.serializer(), settings))
}
