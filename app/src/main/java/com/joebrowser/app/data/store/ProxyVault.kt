package com.joebrowser.app.data.store

import android.content.Context
import com.joebrowser.app.core.AppPaths
import com.joebrowser.app.core.SecretBox
import com.joebrowser.app.data.model.ProxyConfig
import com.joebrowser.app.data.model.ProxyRecord
import java.util.UUID

/**
 * The saved proxies.
 *
 * Passwords are encrypted with a key that lives in the device keystore. The consequence is
 * deliberate and matches the desktop build: a vault copied to another device yields empty
 * passwords rather than a secret handed to a stranger.
 */
class ProxyVault(private val context: Context) {

    private val file get() = AppPaths.proxiesFile(context)

    fun load(): List<ProxyRecord> {
        val raw = JsonStore.read(context, file, emptyList<ProxyRecord>()) { text ->
            JsonStore.json.decodeFromString(kotlinx.serialization.builtins.ListSerializer(ProxyRecord.serializer()), text)
        }
        return raw.filter { it.id.isNotBlank() }.map { it.decryptPassword() }
    }

    fun save(records: List<ProxyRecord>): Boolean {
        val encodable = records.map { it.encryptPassword() }
        return JsonStore.write(
            context, file,
            JsonStore.pretty.encodeToString(kotlinx.serialization.builtins.ListSerializer(ProxyRecord.serializer()), encodable)
        )
    }

    fun add(record: ProxyRecord): ProxyRecord {
        val list = load().toMutableList()
        val entry = record.copy(id = if (record.id.isBlank()) UUID.randomUUID().toString().replace("-", "") else record.id)
        list.add(entry)
        save(list)
        return entry
    }

    fun update(record: ProxyRecord) {
        val list = load().toMutableList()
        val i = list.indexOfFirst { it.id == record.id }
        if (i >= 0) {
            list[i] = record
            save(list)
        }
    }

    fun delete(id: String) {
        save(load().filter { it.id != id })
    }

    fun find(id: String): ProxyRecord? = load().firstOrNull { it.id == id }

    /**
     * Writes the result of a check back onto the record.
     *
     * A failure keeps the last known location: the exit node did not stop being in
     * Frankfurt just because one request timed out, and losing that would make the list
     * useless exactly when the user needs it.
     */
    fun applyCheck(id: String, ok: Boolean, ip: String, country: String, countryCode: String,
                   city: String, timezone: String, error: String): ProxyRecord? {
        val list = load().toMutableList()
        val i = list.indexOfFirst { it.id == id }
        if (i < 0) return null
        val old = list[i]
        val updated = if (ok) {
            old.copy(
                lastStatus = "online",
                lastIp = ip,
                lastCountry = country,
                lastCountryCode = countryCode,
                lastCity = city,
                lastCheckedAt = System.currentTimeMillis(),
                note = old.note,
            )
        } else {
            old.copy(
                lastStatus = "offline",
                lastCheckedAt = System.currentTimeMillis(),
                note = if (error.isNotBlank()) error else old.note,
            )
        }
        list[i] = updated
        save(list)
        return updated
    }

    /** Counts an address change against the record. */
    fun noteRotation(id: String, newIp: String): ProxyRecord? {
        val list = load().toMutableList()
        val i = list.indexOfFirst { it.id == id }
        if (i < 0) return null
        val old = list[i]
        val updated = old.copy(
            lastIp = newIp,
            rotationCount = old.rotationCount + 1,
            lastStatus = "online",
            lastCheckedAt = System.currentTimeMillis(),
        )
        list[i] = updated
        save(list)
        return updated
    }
}

// -------------------------------------------------------------------------------------------
// Password handling
// -------------------------------------------------------------------------------------------

private fun ProxyRecord.encryptPassword(): ProxyRecord {
    if (password.isEmpty()) {
        // An unreadable blob must never be silently wiped by an empty password.
        return copy(password = "")
    }
    return copy(password = SecretBox.encrypt(password))
}

private fun ProxyRecord.decryptPassword(): ProxyRecord =
    copy(password = SecretBox.decrypt(password))

/** Turns a vault record into the profile configuration that uses it. */
fun ProxyRecord.toConfig(): ProxyConfig = ProxyConfig(
    type = type,
    host = host,
    port = port,
    user = user,
    password = password,
    lastIp = lastIp,
    lastCountry = lastCountry,
    lastCountryCode = lastCountryCode,
    lastTimezone = lastTimezone,
    lastCity = lastCity,
    lastStatus = lastStatus,
    lastCheckedAt = lastCheckedAt,
    rotationCount = rotationCount,
    label = name,
)

/** Turns a profile configuration into a vault record. */
fun ProxyConfig.toRecord(name: String = "", note: String = ""): ProxyRecord = ProxyRecord(
    id = UUID.randomUUID().toString().replace("-", ""),
    name = name,
    type = type,
    host = host,
    port = port,
    user = user,
    password = password,
    lastIp = lastIp,
    lastCountry = lastCountry,
    lastCountryCode = lastCountryCode,
    lastTimezone = lastTimezone,
    lastCity = lastCity,
    lastStatus = lastStatus,
    lastCheckedAt = lastCheckedAt,
    rotationCount = rotationCount,
    created = System.currentTimeMillis(),
    note = note,
)
