package com.joebrowser.app.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.joebrowser.app.data.model.AppSettings
import com.joebrowser.app.data.model.ExtensionRecord
import com.joebrowser.app.data.model.Profile
import com.joebrowser.app.data.model.ProxyRecord
import com.joebrowser.app.data.store.ExtensionStore
import com.joebrowser.app.data.store.HistoryStore
import com.joebrowser.app.data.store.ProfileStore
import com.joebrowser.app.data.store.ProxyVault
import com.joebrowser.app.data.store.SettingsStore
import com.joebrowser.app.proxy.ProxyCheck
import com.joebrowser.app.proxy.ProxyTester
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The state the whole app reads from.
 */
data class AppState(
    val profiles: List<Profile> = emptyList(),
    val proxies: List<ProxyRecord> = emptyList(),
    val extensions: List<ExtensionRecord> = emptyList(),
    val settings: AppSettings = AppSettings(),
    val search: String = "",
    val sortBy: Int = 0,
    val busy: String = "",
    val message: String = "",
    val messageIsError: Boolean = false,
    val checkingProxyId: String = "",
    val lastCheck: Map<String, ProxyCheck> = emptyMap(),
) {
    /** The profiles that match the search and the sort order. */
    val visibleProfiles: List<Profile>
        get() {
            val q = search.trim().lowercase()
            val filtered = if (q.isEmpty()) profiles else profiles.filter {
                it.name.lowercase().contains(q) ||
                    it.group.lowercase().contains(q) ||
                    it.tags.lowercase().contains(q) ||
                    it.notes.lowercase().contains(q) ||
                    it.fp.os.lowercase().contains(q) ||
                    it.fp.language.lowercase().contains(q) ||
                    it.proxy.host.lowercase().contains(q)
            }
            return when (sortBy) {
                1 -> filtered.sortedBy { it.name.lowercase() }
                2 -> filtered.sortedByDescending { it.created }
                3 -> filtered.sortedByDescending { it.openCount }
                4 -> filtered.sortedBy { it.fp.os }
                5 -> filtered.sortedBy { it.groupOrUngrouped }
                else -> filtered.sortedByDescending { it.lastOpened }
            }
        }
}

/**
 * The app's single source of truth.
 */
class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val profileStore = ProfileStore(app)
    private val vault = ProxyVault(app)
    private val extensionStore = ExtensionStore(app)
    private val settingsStore = SettingsStore(app)
    private val historyStore = HistoryStore(app)

    private val _state = MutableStateFlow(AppState())
    val state: StateFlow<AppState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val profiles = withContext(Dispatchers.IO) { profileStore.load() }
            val proxies = withContext(Dispatchers.IO) { vault.load() }
            val extensions = withContext(Dispatchers.IO) { extensionStore.load() }
            val settings = withContext(Dispatchers.IO) { settingsStore.load() }
            _state.value = _state.value.copy(
                profiles = profiles,
                proxies = proxies,
                extensions = extensions,
                settings = settings,
            )
        }
    }

    fun setSearch(text: String) {
        _state.value = _state.value.copy(search = text)
    }

    fun setSort(value: Int) {
        _state.value = _state.value.copy(sortBy = value)
    }

    fun setMessage(text: String, isError: Boolean = false) {
        _state.value = _state.value.copy(message = text, messageIsError = isError)
    }

    fun clearMessage() {
        _state.value = _state.value.copy(message = "")
    }

    fun setBusy(text: String) {
        _state.value = _state.value.copy(busy = text)
    }

    // ---------------------------------------------------------------------------------------
    // Profiles
    // ---------------------------------------------------------------------------------------

    fun saveProfile(profile: Profile) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) { profileStore.save(profile) }
            if (!ok) setMessage("The profile could not be written to disk.", true)
            refresh()
        }
    }

    fun deleteProfile(profile: Profile) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                profileStore.delete(profile, _state.value.settings.useTrash)
            }
            refresh()
        }
    }

    fun duplicateProfile(profile: Profile): Profile? {
        var copy: Profile? = null
        viewModelScope.launch {
            copy = withContext(Dispatchers.IO) {
                profileStore.duplicate(profile, _state.value.profiles)
            }
            if (copy != null) {
                withContext(Dispatchers.IO) { profileStore.save(copy!!) }
                setMessage("Profile duplicated.")
                refresh()
            }
        }
        return copy
    }

    fun noteLaunched(profileId: String) {
        viewModelScope.launch {
            val p = withContext(Dispatchers.IO) { profileStore.find(profileId) } ?: return@launch
            withContext(Dispatchers.IO) {
                profileStore.save(
                    p.copy(
                        openCount = p.openCount + 1,
                        lastOpened = System.currentTimeMillis(),
                        launchedOnce = true,
                    )
                )
            }
            refresh()
        }
    }

    // ---------------------------------------------------------------------------------------
    // Proxies
    // ---------------------------------------------------------------------------------------

    fun saveProxy(record: ProxyRecord) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val existing = vault.load().firstOrNull { it.id == record.id }
                if (existing == null) vault.add(record) else vault.update(record)
            }
            refresh()
        }
    }

    fun deleteProxy(id: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { vault.delete(id) }
            refresh()
        }
    }

    /** Checks one proxy and writes the result back onto the record. */
    fun checkProxy(record: ProxyRecord) {
        viewModelScope.launch {
            _state.value = _state.value.copy(checkingProxyId = record.id)
            val result = ProxyTester.test(
                com.joebrowser.app.data.model.ProxyConfig(
                    type = record.type, host = record.host, port = record.port,
                    user = record.user, password = record.password,
                )
            )
            withContext(Dispatchers.IO) {
                vault.applyCheck(
                    record.id, result.ok, result.ip, result.country, result.countryCode,
                    result.city, result.timezone, result.error
                )
            }
            _state.value = _state.value.copy(
                checkingProxyId = "",
                lastCheck = _state.value.lastCheck + (record.id to result),
                message = if (result.ok) {
                    "Working · ${result.ip} · ${result.latencyMs} ms"
                } else {
                    "Offline · ${result.error}"
                },
                messageIsError = !result.ok,
            )
            refresh()
        }
    }

    /** Checks every saved proxy, one after the other. */
    fun checkAllProxies() {
        viewModelScope.launch {
            val list = _state.value.proxies
            if (list.isEmpty()) {
                setMessage("Add a proxy first.")
                return@launch
            }
            setBusy("Checking…")
            var working = 0
            for (record in list) {
                _state.value = _state.value.copy(checkingProxyId = record.id)
                val result = ProxyTester.test(
                    com.joebrowser.app.data.model.ProxyConfig(
                        type = record.type, host = record.host, port = record.port,
                        user = record.user, password = record.password,
                    )
                )
                if (result.ok) working++
                withContext(Dispatchers.IO) {
                    vault.applyCheck(
                        record.id, result.ok, result.ip, result.country, result.countryCode,
                        result.city, result.timezone, result.error
                    )
                }
            }
            setBusy("")
            setMessage(
                "$working of ${list.size} proxies working",
                isError = working == 0
            )
            refresh()
        }
    }

    // ---------------------------------------------------------------------------------------
    // Extensions
    // ---------------------------------------------------------------------------------------

    fun saveExtension(record: ExtensionRecord) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val existing = extensionStore.load().firstOrNull { it.id == record.id }
                if (existing == null) extensionStore.add(record) else extensionStore.update(record)
            }
            refresh()
        }
    }

    fun deleteExtension(id: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { extensionStore.delete(id) }
            refresh()
        }
    }

    // ---------------------------------------------------------------------------------------
    // Settings and history
    // ---------------------------------------------------------------------------------------

    fun saveSettings(settings: AppSettings) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { settingsStore.save(settings) }
            _state.value = _state.value.copy(settings = settings)
        }
    }

    fun recordVisit(profileId: String, url: String, title: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { historyStore.record(profileId, url, title) }
        }
    }

    fun history(profileId: String? = null): List<com.joebrowser.app.data.model.HistoryEntry> =
        historyStore.load(profileId)

    fun clearHistory(profileId: String? = null) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { historyStore.clear(profileId) }
            setMessage("History cleared.")
        }
    }

    fun context(): Context = getApplication()
}
