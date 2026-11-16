package com.joebrowser.app.browser

import android.content.Context
import com.joebrowser.app.core.AppPaths
import com.joebrowser.app.data.store.JsonStore
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer

/**
 * A saved page.
 */
@Serializable
data class Bookmark(
    val id: String = "",
    val title: String = "",
    val url: String = "",
    val created: Long = 0L,
)

/**
 * The bookmarks of the workspace.
 */
class BookmarkStore(private val context: Context) {

    private val file get() = AppPaths.root(context).let { java.io.File(it, "bookmarks.json") }

    fun load(): List<Bookmark> = JsonStore.read(context, file, emptyList<Bookmark>()) { text ->
        JsonStore.json.decodeFromString(ListSerializer(Bookmark.serializer()), text)
    }

    fun save(list: List<Bookmark>) {
        JsonStore.write(
            context, file,
            JsonStore.json.encodeToString(ListSerializer(Bookmark.serializer()), list),
        )
    }

    fun add(url: String, title: String) {
        val list = load().toMutableList()
        if (list.any { it.url == url }) return
        list.add(
            Bookmark(
                id = java.util.UUID.randomUUID().toString().replace("-", ""),
                title = title.ifBlank { url },
                url = url,
                created = System.currentTimeMillis(),
            )
        )
        save(list)
    }

    fun remove(id: String) {
        save(load().filter { it.id != id })
    }

    fun contains(url: String): Boolean = load().any { it.url == url }
}

/**
 * A file the browser fetched.
 */
@Serializable
data class Download(
    val id: String = "",
    val title: String = "",
    val url: String = "",
    val path: String = "",
    val bytes: Long = 0,
    val mime: String = "",
    val at: Long = 0L,
)

/**
 * The downloads of the workspace.
 */
class DownloadStore(private val context: Context) {

    private val file get() = AppPaths.root(context).let { java.io.File(it, "downloads.json") }

    fun load(): List<Download> = JsonStore.read(context, file, emptyList<Download>()) { text ->
        JsonStore.json.decodeFromString(ListSerializer(Download.serializer()), text)
    }

    fun save(list: List<Download>) {
        JsonStore.write(
            context, file,
            JsonStore.json.encodeToString(ListSerializer(Download.serializer()), list),
        )
    }

    /**
     * Starts a download.
     *
     * The file goes into the app's own folder rather than the public one: a browser that
     * is used for work should not scatter its downloads through the device's gallery.
     */
    fun enqueue(
        context: Context,
        url: String,
        userAgent: String?,
        disposition: String?,
        mime: String?,
    ) {
        val name = android.webkit.URLUtil.guessFileName(url, disposition, mime)
        val dir = java.io.File(AppPaths.root(context), "downloads").apply { if (!exists()) mkdirs() }
        val target = java.io.File(dir, name)
        val record = Download(
            id = java.util.UUID.randomUUID().toString().replace("-", ""),
            title = name,
            url = url,
            path = target.absolutePath,
            mime = mime ?: "",
            at = System.currentTimeMillis(),
        )
        val list = load().toMutableList()
        list.add(0, record)
        save(list.take(200))
        runCatching {
            val request = android.app.DownloadManager.Request(android.net.Uri.parse(url))
            request.setTitle(name)
            request.setMimeType(mime)
            request.setDestinationInExternalFilesDir(context, android.os.Environment.DIRECTORY_DOWNLOADS, name)
            userAgent?.let { request.addRequestHeader("User-Agent", it) }
            val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as android.app.DownloadManager
            manager.enqueue(request)
        }
    }

    /** Removes a record and the file it points at. */
    fun remove(id: String) {
        val list = load()
        val record = list.firstOrNull { it.id == id } ?: return
        runCatching { java.io.File(record.path).delete() }
        save(list.filter { it.id != id })
    }
}
