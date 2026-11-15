package com.joebrowser.app.extensions

import android.content.Context
import com.joebrowser.app.core.AppPaths
import com.joebrowser.app.data.model.ExtensionRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

/**
 * Downloads, unpacks and runs extensions.
 *
 * The Chrome Web Store packages an extension as a CRX file: a small header followed by an
 * ordinary zip. The header is skipped and the zip is unpacked, which leaves a folder with
 * a manifest, some scripts and whatever else the extension carries.
 *
 * A browser engine cannot install a Chrome extension the way a desktop Chrome can: there
 * is no extension host to hand it to. What can be done, and is done here, is to run the
 * extension's content scripts and stylesheets inside the profile's pages, which is the
 * part of an extension that actually changes what a page does.
 */
object ExtensionManager {

    private const val CRX_MAGIC_0 = 0x43 // C
    private const val CRX_MAGIC_1 = 0x72 // r
    private const val CRX_MAGIC_2 = 0x32 // 2
    private const val CRX_MAGIC_3 = 0x34 // 4

    /** The endpoint the store itself uses to hand out a package. */
    fun storeDownloadUrl(extensionId: String): String =
        "https://clients2.google.com/service/update2/crx?response=redirect" +
            "&prodversion=131.0.0.0&acceptformat=crx2,crx3&x=" +
            java.net.URLEncoder.encode("id=$extensionId&uc", "UTF-8")

    fun storeListingUrl(extensionId: String): String =
        "https://chromewebstore.google.com/detail/$extensionId"

    fun storeSearchUrl(query: String): String =
        if (query.isBlank()) "https://chromewebstore.google.com/"
        else "https://chromewebstore.google.com/search/" +
            java.net.URLEncoder.encode(query, "UTF-8").replace("+", "%20")

    /**
     * Pulls the extension id out of a store link.
     *
     * The link comes in several shapes, and the id is always the last run of thirty-two
     * characters drawn from the letters a to p.
     */
    fun storeIdFromUrl(url: String): String {
        val withoutQuery = url.substringBefore('?')
        val segments = withoutQuery.split('/', '=', '&', '#', ':')
        for (i in segments.indices.reversed()) {
            val s = segments[i]
            if (s.length == 32 && s.all { it in 'a'..'p' || it in 'A'..'P' }) return s.lowercase()
        }
        return ""
    }

    /** True when the string looks like a store id. */
    fun isStoreId(value: String): Boolean =
        value.length == 32 && value.all { it in 'a'..'p' || it in 'A'..'P' }

    // -----------------------------------------------------------------------------------------
    // Download
    // -----------------------------------------------------------------------------------------

    /**
     * Downloads an extension from the store and unpacks it into the library.
     *
     * @return the record, or a failure message.
     */
    suspend fun installFromStore(
        context: Context,
        extensionId: String,
        onProgress: (Int, Long) -> Unit = { _, _ -> },
    ): Result<ExtensionRecord> = withContext(Dispatchers.IO) {
        try {
            val id = extensionId.lowercase()
            val crx = File(AppPaths.importedExtensionsDir(context), "$id.crx")
            val bytes = download(storeDownloadUrl(id), crx, onProgress)
                ?: return@withContext Result.failure(IllegalStateException("The store did not send a package for this id."))
            if (bytes == 0L) {
                return@withContext Result.failure(IllegalStateException("The store sent nothing back for this id."))
            }

            val zipStart = findZipStart(crx)
            if (zipStart < 0) {
                crx.delete()
                return@withContext Result.failure(
                    IllegalStateException("The store did not send an extension package. It may be removed or region locked.")
                )
            }

            val target = File(AppPaths.importedExtensionsDir(context), id)
            val staging = File(AppPaths.importedExtensionsDir(context), "$id.new")
            staging.deleteRecursively()
            target.deleteRecursively()

            val ok = unzipFrom(crx, zipStart, staging)
            crx.delete()
            if (!ok) {
                staging.deleteRecursively()
                return@withContext Result.failure(IllegalStateException("The package could not be unpacked."))
            }
            if (!File(staging, "manifest.json").exists()) {
                staging.deleteRecursively()
                return@withContext Result.failure(
                    IllegalStateException("The downloaded package has no manifest.json, so it cannot be loaded.")
                )
            }

            // A package that wraps the extension in one folder is lifted up a level.
            val children = staging.listFiles() ?: emptyArray()
            if (children.size == 1 && children[0].isDirectory && File(children[0], "manifest.json").exists()) {
                val inner = children[0]
                val temp = File(staging.parentFile, "$id.lift")
                temp.deleteRecursively()
                if (!inner.renameTo(temp)) {
                    inner.listFiles()?.forEach { child ->
                        child.renameTo(File(staging, child.name))
                    }
                    inner.delete()
                } else {
                    temp.listFiles()?.forEach { child ->
                        child.renameTo(File(staging, child.name))
                    }
                    temp.delete()
                }
            }

            staging.copyRecursively(target, overwrite = true)
            staging.deleteRecursively()

            val manifest = readManifest(target)
            if (manifest == null) {
                target.deleteRecursively()
                return@withContext Result.failure(IllegalStateException("The package has no readable manifest."))
            }

            val record = ExtensionRecord(
                id = id,
                name = manifest.optString("name").ifBlank { id },
                version = manifest.optString("version"),
                description = manifest.optString("description"),
                publisher = manifest.optString("author").ifBlank { publisherFromManifest(manifest) },
                storeId = id,
                url = storeListingUrl(id),
                homepage = storeListingUrl(id),
                enabled = true,
                installed = true,
                folder = target.absolutePath,
                installedAt = System.currentTimeMillis(),
                sizeBytes = folderSize(target),
                source = "store",
            )
            Result.success(record)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Downloads a file, reporting progress. Returns the number of bytes, or -1 on failure. */
    private fun download(url: String, dest: File, onProgress: (Int, Long) -> Unit): Long? {
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 20_000
                readTimeout = 30_000
                instanceFollowRedirects = true
                setRequestProperty(
                    "User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
                        "Chrome/131.0.0.0 Safari/537.36"
                )
                setRequestProperty("Accept", "*/*")
            }
            if (connection.responseCode != 200) return null
            val total = connection.contentLength.toLong()
            var written = 0L
            var lastPercent = 0
            connection.inputStream.use { input ->
                dest.outputStream().use { output ->
                    val buffer = ByteArray(16 * 1024)
                    while (true) {
                        val n = input.read(buffer)
                        if (n <= 0) break
                        output.write(buffer, 0, n)
                        written += n
                        if (total > 0) {
                            val percent = ((written * 100) / total).toInt()
                            if (percent != lastPercent) {
                                lastPercent = percent
                                onProgress(percent, total)
                            }
                        }
                    }
                }
            }
            written
        } catch (e: Exception) {
            null
        } finally {
            try { connection?.disconnect() } catch (e: Exception) {}
        }
    }

    /**
     * Finds where the zip starts inside a CRX file.
     *
     * The header is skipped wholesale: the signature and the proof of ownership are not
     * needed to read the payload, and the format has changed twice since CRX2.
     */
    fun findZipStart(file: File): Int {
        val raw = file.readBytes()
        if (raw.size < 4) return -1
        val from = if (raw[0].toInt() == CRX_MAGIC_0 && raw[1].toInt() == CRX_MAGIC_1 &&
            raw[2].toInt() == CRX_MAGIC_2 && raw[3].toInt() == CRX_MAGIC_3
        ) 8 else 0
        for (i in from..raw.size - 4) {
            if (raw[i].toInt() == 0x50 && raw[i + 1].toInt() == 0x4B &&
                raw[i + 2].toInt() == 0x03 && raw[i + 3].toInt() == 0x04
            ) return i
        }
        return -1
    }

    /** Unpacks the zip that starts at the given offset. */
    private fun unzipFrom(file: File, offset: Int, target: File): Boolean {
        return try {
            target.mkdirs()
            file.inputStream().use { input ->
                input.skip(offset.toLong())
                ZipInputStream(input).use { zip ->
                    var entry = zip.nextEntry
                    while (entry != null) {
                        val name = entry.name.replace("\\", "/")
                        // A package must not be able to write outside its own folder.
                        if (name.contains("..")) {
                            zip.closeEntry()
                            entry = zip.nextEntry
                            continue
                        }
                        val out = File(target, name)
                        if (entry.isDirectory) {
                            out.mkdirs()
                        } else {
                            out.parentFile?.mkdirs()
                            out.outputStream().use { sink -> zip.copyTo(sink) }
                        }
                        zip.closeEntry()
                        entry = zip.nextEntry
                    }
                }
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun folderSize(dir: File): Long {
        var total = 0L
        try {
            dir.walkTopDown().filter { it.isFile }.forEach { total += it.length() }
        } catch (e: Exception) {}
        return total
    }

    private fun publisherFromManifest(manifest: JSONObject): String {
        // The key is the extension's public key; its first bytes identify the publisher.
        val key = manifest.optString("key")
        if (key.isBlank()) return ""
        return try {
            val bytes = android.util.Base64.decode(key, android.util.Base64.DEFAULT)
            bytes.take(4).joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            ""
        }
    }

    /** Reads the manifest of an unpacked extension. */
    fun readManifest(folder: File): JSONObject? {
        return try {
            val file = File(folder, "manifest.json")
            if (!file.exists()) return null
            JSONObject(file.readText())
        } catch (e: Exception) {
            null
        }
    }

    // -----------------------------------------------------------------------------------------
    // Running
    // -----------------------------------------------------------------------------------------

    /**
     * The scripts an extension asks to have run in every page.
     *
     * These are the scripts that change what a page does, and they are what a profile
     * gains from an extension.
     */
    fun contentScripts(folder: File): List<String> {
        val manifest = readManifest(folder) ?: return emptyList()
        val out = ArrayList<String>()
        val blocks = manifest.optJSONArray("content_scripts") ?: return out
        for (i in 0 until blocks.length()) {
            val block = blocks.optJSONObject(i) ?: continue
            val js = block.optJSONArray("js") ?: continue
            for (j in 0 until js.length()) {
                val rel = js.optString(j)
                if (rel.isBlank()) continue
                val file = File(folder, rel)
                if (!file.exists()) continue
                out.add(file.readText())
            }
        }
        return out
    }

    /** The stylesheets an extension asks to have applied in every page. */
    fun contentStyles(folder: File): List<String> {
        val manifest = readManifest(folder) ?: return emptyList()
        val out = ArrayList<String>()
        val blocks = manifest.optJSONArray("content_scripts") ?: return out
        for (i in 0 until blocks.length()) {
            val block = blocks.optJSONObject(i) ?: continue
            val css = block.optJSONArray("css") ?: continue
            for (j in 0 until css.length()) {
                val rel = css.optString(j)
                if (rel.isBlank()) continue
                val file = File(folder, rel)
                if (!file.exists()) continue
                out.add("(function(){try{var s=document.createElement('style');" +
                    "s.textContent=" +
                    kotlinx.serialization.json.Json.encodeToString(
                        kotlinx.serialization.json.JsonPrimitive.serializer(),
                        kotlinx.serialization.json.JsonPrimitive(file.readText())
                    ) + ";" +
                    "(document.head||document.documentElement).appendChild(s);}catch(e){}})();")
            }
        }
        return out
    }

    /** The icon of an extension, as a file inside its folder. */
    fun iconFile(folder: File): File? {
        val manifest = readManifest(folder) ?: return null
        val icons = manifest.optJSONObject("icons") ?: return null
        var best: File? = null
        var bestSize = -1
        for (key in icons.keys()) {
            val size = key.toIntOrNull() ?: continue
            val file = File(folder, icons.optString(key))
            if (file.exists() && size > bestSize) {
                best = file
                bestSize = size
            }
        }
        return best
    }
}
