package com.joebrowser.app.data.store

import android.content.Context
import com.joebrowser.app.core.AppPaths
import com.joebrowser.app.data.model.ExtensionRecord
import com.joebrowser.app.data.model.HistoryEntry
import java.util.UUID

/**
 * The extension library.
 */
class ExtensionStore(private val context: Context) {

    private val file get() = AppPaths.extensionsFile(context)

    fun load(): List<ExtensionRecord> = JsonStore.read(context, file, emptyList<ExtensionRecord>()) { text ->
        JsonStore.json.decodeFromString(kotlinx.serialization.builtins.ListSerializer(ExtensionRecord.serializer()), text)
    }.filter { it.id.isNotBlank() }

    fun save(records: List<ExtensionRecord>): Boolean = JsonStore.write(
        context, file,
        JsonStore.pretty.encodeToString(kotlinx.serialization.builtins.ListSerializer(ExtensionRecord.serializer()), records)
    )

    fun add(record: ExtensionRecord): ExtensionRecord {
        val list = load().toMutableList()
        val entry = record.copy(id = if (record.id.isBlank()) UUID.randomUUID().toString().replace("-", "") else record.id)
        // The same extension added twice replaces the old row instead of sitting next to it.
        val i = list.indexOfFirst { it.storeId.isNotBlank() && it.storeId == entry.storeId }
        if (i >= 0) list[i] = entry else list.add(entry)
        save(list)
        return entry
    }

    fun update(record: ExtensionRecord) {
        val list = load().toMutableList()
        val i = list.indexOfFirst { it.id == record.id }
        if (i >= 0) {
            list[i] = record
            save(list)
        }
    }

    fun delete(id: String) {
        val list = load()
        val record = list.firstOrNull { it.id == id }
        record?.let { r ->
            if (r.folder.isNotBlank()) {
                runCatching { java.io.File(r.folder).deleteRecursively() }
            }
        }
        save(list.filter { it.id != id })
    }

    fun find(id: String): ExtensionRecord? = load().firstOrNull { it.id == id }
}

/**
 * The pages each profile visited.
 */
class HistoryStore(private val context: Context) {

    private val file get() = AppPaths.historyFile(context)
    private val maxEntries = 3000

    fun load(profileId: String? = null): List<HistoryEntry> {
        val all = JsonStore.read(context, file, emptyList<HistoryEntry>()) { text ->
            JsonStore.json.decodeFromString(kotlinx.serialization.builtins.ListSerializer(HistoryEntry.serializer()), text)
        }
        return if (profileId.isNullOrBlank()) all else all.filter { it.profileId == profileId }
    }

    /** Records a visit. The same URL in the same profile is moved to the top, not duplicated. */
    fun record(profileId: String, url: String, title: String) {
        val all = load().toMutableList()
        all.removeAll { it.profileId == profileId && it.url == url }
        all.add(
            HistoryEntry(
                id = UUID.randomUUID().toString().replace("-", ""),
                profileId = profileId,
                url = url,
                title = title.ifBlank { url },
                visitedAt = System.currentTimeMillis(),
            )
        )
        val trimmed = if (all.size > maxEntries) all.takeLast(maxEntries) else all
        JsonStore.write(
            context, file,
            JsonStore.json.encodeToString(kotlinx.serialization.builtins.ListSerializer(HistoryEntry.serializer()), trimmed)
        )
    }

    fun clear(profileId: String? = null) {
        if (profileId.isNullOrBlank()) {
            runCatching { file.delete() }
            return
        }
        val kept = load().filter { it.profileId != profileId }
        JsonStore.write(
            context, file,
            JsonStore.json.encodeToString(kotlinx.serialization.builtins.ListSerializer(HistoryEntry.serializer()), kept)
        )
    }
}
