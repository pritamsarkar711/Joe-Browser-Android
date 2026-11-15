package com.joebrowser.app.core

import android.content.Context
import java.io.File

/**
 * Where the app keeps its files.
 *
 * Everything lives under the app's private storage. Nothing a profile does is visible to
 * another app, and nothing survives an uninstall except what the user explicitly exports.
 */
object AppPaths {

    fun root(context: Context): File =
        File(context.filesDir, "joe-browser").apply { if (!exists()) mkdirs() }

    fun profilesDir(context: Context): File =
        File(root(context), "profiles").apply { if (!exists()) mkdirs() }

    fun profileDir(context: Context, profileId: String): File =
        File(profilesDir(context), profileId).apply { if (!exists()) mkdirs() }

    fun trashDir(context: Context): File =
        File(root(context), "trash").apply { if (!exists()) mkdirs() }

    fun extensionsDir(context: Context): File =
        File(root(context), "extensions").apply { if (!exists()) mkdirs() }

    fun importedExtensionsDir(context: Context): File =
        File(extensionsDir(context), "imported").apply { if (!exists()) mkdirs() }

    fun browsersDir(context: Context): File =
        File(root(context), "browsers").apply { if (!exists()) mkdirs() }

    fun logsDir(context: Context): File =
        File(root(context), "logs").apply { if (!exists()) mkdirs() }

    fun exportsDir(context: Context): File =
        File(root(context), "exports").apply { if (!exists()) mkdirs() }

    fun profilesFile(context: Context): File = File(profilesDir(context), "profiles.json")

    fun settingsFile(context: Context): File = File(root(context), "settings.json")

    fun proxiesFile(context: Context): File = File(root(context), "proxies.json")

    fun extensionsFile(context: Context): File = File(extensionsDir(context), "extensions.json")

    fun historyFile(context: Context): File = File(root(context), "history.json")

    /** The WebView data directory suffix for a profile. */
    fun dataSuffix(profileId: String): String = "jb_" + profileId.replace(Regex("[^A-Za-z0-9]"), "")

    /** Human readable byte size. */
    fun human(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> String.format(java.util.Locale.ROOT, "%.0f KB", bytes / 1024.0)
        bytes < 1024L * 1024 * 1024 -> String.format(java.util.Locale.ROOT, "%.1f MB", bytes / 1048576.0)
        else -> String.format(java.util.Locale.ROOT, "%.2f GB", bytes / 1073741824.0)
    }
}
