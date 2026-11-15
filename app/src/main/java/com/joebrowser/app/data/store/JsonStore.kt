package com.joebrowser.app.data.store

import android.content.Context
import com.joebrowser.app.core.AppPaths
import kotlinx.serialization.json.Json

/**
 * Reads and writes the app's JSON files.
 *
 * Every write goes to a temporary file first and is then moved into place, so a write that
 * is cut short by the process being killed leaves the previous file intact rather than a
 * half-written one. A file that cannot be parsed is set aside instead of being replaced:
 * a corrupt library must look corrupt, not look empty, because the difference between
 * "you have no proxies" and "the proxy file could not be read" matters a great deal.
 */
internal object JsonStore {

    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
        isLenient = true
        coerceInputValues = true
    }

    val pretty = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
        isLenient = true
        coerceInputValues = true
    }

    fun <T> read(context: Context, file: java.io.File, fallback: T, decode: (String) -> T): T {
        if (!file.exists()) return fallback
        return try {
            val text = file.readText()
            if (text.isBlank()) fallback else decode(text)
        } catch (e: Exception) {
            val stamp = java.text.SimpleDateFormat("yyyyMMddHHmmss", java.util.Locale.ROOT)
                .format(java.util.Date())
            runCatching { java.io.File(file.parentFile, file.name + ".broken-" + stamp) }
                .getOrNull()?.let { broken ->
                    runCatching { file.renameTo(broken) }
                }
            fallback
        }
    }

    fun write(context: Context, file: java.io.File, text: String): Boolean {
        return try {
            file.parentFile?.mkdirs()
            val tmp = java.io.File(file.parentFile, file.name + "." + System.nanoTime() + ".tmp")
            tmp.writeText(text, Charsets.UTF_8)

            // A rename replaces the target in one step. On Android the rename is the
            // system's own, so the file is never briefly missing: a reader that arrives
            // in the middle of a save sees the old file or the new one, never nothing.
            // Deleting the target first would open exactly that gap.
            var replaced = tmp.renameTo(file)
            if (!replaced) {
                // A rename can be refused, for instance when the file is held open
                // elsewhere. A copy is slower but it works.
                file.delete()
                tmp.copyTo(file, overwrite = true)
                replaced = true
            }
            tmp.delete()
            replaced
        } catch (e: Exception) {
            false
        }
    }
}
