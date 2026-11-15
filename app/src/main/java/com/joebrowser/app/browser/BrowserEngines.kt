package com.joebrowser.app.browser

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.joebrowser.app.core.AppPaths
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * The browser engines the app knows about.
 *
 * On a phone the engine that renders a page is the system WebView, so that is the engine
 * a profile actually runs on. The others are the browsers installed on the device: a
 * profile can name one of them, report its version and be launched with it, which is what
 * makes a workspace with several engines possible.
 *
 * Downloads are real: the file is fetched into the app's own folder and handed to the
 * system installer, which asks the user to confirm. Nothing is installed silently.
 */
object BrowserEngines {

    data class Engine(
        val key: String,
        val label: String,
        val packageName: String,
        val storeListing: String,
        val homepage: String,
        /** Candidate direct download addresses, tried in order. */
        val downloadUrls: List<String>,
        /** The version the app itself uses when this engine is selected. */
        val engineVersion: String,
    ) {
        val isWebView: Boolean get() = key == "webview"
    }

    private val ENGINES = listOf(
        Engine(
            key = "webview",
            label = "System WebView",
            packageName = "com.google.android.webview",
            storeListing = "com.google.android.webview",
            homepage = "https://developer.android.com/develop/ui/views/layout/webapps/webview",
            downloadUrls = emptyList(),
            engineVersion = "",
        ),
        Engine(
            key = "chrome",
            label = "Google Chrome",
            packageName = "com.android.chrome",
            storeListing = "com.android.chrome",
            homepage = "https://www.google.com/chrome/",
            downloadUrls = listOf(
                "https://dl.google.com/android/repository/current/chrome-latest.apk",
                "https://dl.google.com/chrome/android/current/Chrome_Android.apk",
            ),
            engineVersion = "",
        ),
        Engine(
            key = "chromium",
            label = "Chromium",
            packageName = "org.chromium.chrome",
            storeListing = "org.chromium.chrome",
            homepage = "https://www.chromium.org/getting-involved/download-chromium/",
            downloadUrls = emptyList(),
            engineVersion = "",
        ),
        Engine(
            key = "firefox",
            label = "Mozilla Firefox",
            packageName = "org.mozilla.firefox",
            storeListing = "org.mozilla.firefox",
            homepage = "https://www.mozilla.org/firefox/new/",
            downloadUrls = emptyList(),
            engineVersion = "",
        ),
        Engine(
            key = "brave",
            label = "Brave Browser",
            packageName = "com.brave.browser",
            storeListing = "com.brave.browser",
            homepage = "https://brave.com/download/",
            downloadUrls = listOf("https://fdn.co/brave/release/android/brave-browser.apk"),
            engineVersion = "",
        ),
        Engine(
            key = "edge",
            label = "Microsoft Edge",
            packageName = "com.microsoft.emmx",
            storeListing = "com.microsoft.emmx",
            homepage = "https://www.microsoft.com/edge",
            downloadUrls = emptyList(),
            engineVersion = "",
        ),
    )

    fun all(): List<Engine> = ENGINES

    fun byKey(key: String): Engine? = ENGINES.firstOrNull { it.key == key }

    /** The engine a profile claims. */
    fun forProfile(profileEngine: String): Engine =
        byKey(profileEngine) ?: byKey("webview")!!

    /**
     * The version of the engine that actually renders pages.
     *
     * This is the WebView's own version, and it is the one number that has to be right:
     * a profile that claims Chrome 157 while the engine is Chromium 118 is claiming a
     * feature set it does not have.
     */
    fun webViewVersion(context: Context): String {
        return try {
            val info = context.packageManager.getPackageInfo("com.google.android.webview", 0)
            info.versionName ?: ""
        } catch (e: Exception) {
            try {
                val info = context.packageManager.getPackageInfo("com.android.webview", 0)
                info.versionName ?: ""
            } catch (e2: Exception) {
                try {
                    val info = context.packageManager.getPackageInfo("com.android.chrome", 0)
                    info.versionName ?: ""
                } catch (e3: Exception) {
                    ""
                }
            }
        }
    }

    /** The version of a browser installed on the device, or an empty string. */
    fun installedVersion(context: Context, engine: Engine): String {
        return try {
            val info = context.packageManager.getPackageInfo(engine.packageName, 0)
            info.versionName ?: ""
        } catch (e: Exception) {
            ""
        }
    }
    /** True when the browser is installed on the device. */
    fun isInstalled(context: Context, engine: Engine): Boolean {
        return try {
            context.packageManager.getPackageInfo(engine.packageName, 0)
            true
        } catch (e: Exception) {
            false
        }
    }

    /** The engines that are present on this device, plus the system WebView. */
    fun installedEngines(context: Context): List<Pair<Engine, String>> =
        ENGINES.map { it to installedVersion(context, it) }
            .filter { it.first.key == "webview" || it.second.isNotBlank() }

    /**
     * The version a profile should report.
     *
     * A profile that names a browser reports the version of that browser when it is
     * installed, and the engine version otherwise. Reporting a version that nothing on the
     * device can produce would be a lie the client hints would immediately expose.
     */
    fun reportedVersion(context: Context, profileEngine: String): String {
        val engine = forProfile(profileEngine)
        val installed = installedVersion(context, engine)
        if (installed.isNotBlank()) return installed
        return webViewVersion(context)
    }

    /**
     * Downloads an engine package into the app's own folder.
     *
     * @return the downloaded file, or a failure message.
     */
    suspend fun download(
        context: Context,
        engine: Engine,
        onProgress: (Int, Long) -> Unit = { _, _ -> },
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            if (engine.downloadUrls.isEmpty()) {
                return@withContext Result.failure(
                    IllegalStateException("There is no direct package for ${engine.label}. Open the official page instead.")
                )
            }
            var lastError = "The download did not start."
            for (url in engine.downloadUrls) {
                val target = File(AppPaths.browsersDir(context), "${engine.key}.apk")
                val bytes = fetch(url, target, onProgress)
                if (bytes != null && bytes > 0) {
                    // A package is a zip; anything else is an error page.
                    if (!looksLikePackage(target)) {
                        target.delete()
                        lastError = "The server sent a page instead of a package."
                        continue
                    }
                    return@withContext Result.success(target)
                }
                target.delete()
            }
            Result.failure(IllegalStateException(lastError))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** True when the file starts with a zip header, which is what an APK is. */
    fun looksLikePackage(file: File): Boolean {
        return try {
            val head = file.inputStream().use { input ->
                val buffer = ByteArray(4)
                input.read(buffer)
                buffer
            }
            head.size >= 4 && head[0].toInt() == 0x50 && head[1].toInt() == 0x4B &&
                (head[2].toInt() == 0x03 || head[2].toInt() == 0x05 || head[2].toInt() == 0x07)
        } catch (e: Exception) {
            false
        }
    }

    private fun fetch(url: String, dest: File, onProgress: (Int, Long) -> Unit): Long? {
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 25_000
                readTimeout = 60_000
                instanceFollowRedirects = true
                setRequestProperty(
                    "User-Agent",
                    "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) " +
                        "Chrome/131.0.0.0 Mobile Safari/537.36"
                )
            }
            if (connection.responseCode != 200) return null
            val total = connection.contentLength.toLong()
            var written = 0L
            var lastPercent = 0
            connection.inputStream.use { input ->
                dest.outputStream().use { output ->
                    val buffer = ByteArray(32 * 1024)
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

    /** The intent that asks the system to install a package. */
    fun installIntent(context: Context, file: File): Intent? {
        return try {
            val uri = androidx.core.content.FileProvider.getUriForFile(
                context,
                context.packageName + ".fileprovider",
                file
            )
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        } catch (e: Exception) {
            null
        }
    }

    /** The intent that opens a browser's listing in the store. */
    fun storeIntent(engine: Engine): Intent {
        val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Uri.parse("market://details?id=${engine.storeListing}")
        } else {
            Uri.parse("https://play.google.com/store/apps/details?id=${engine.storeListing}")
        }
        return Intent(Intent.ACTION_VIEW, uri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
}
