package com.joebrowser.app.browser

import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.OnBackPressedCallback
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import com.joebrowser.app.BuildConfig
import com.joebrowser.app.data.model.Profile
import com.joebrowser.app.data.store.ProfileStore
import com.joebrowser.app.data.store.SettingsStore
import com.joebrowser.app.ui.theme.JoeTheme
import com.joebrowser.app.ui.theme.palette

/**
 * The browser.
 *
 * It is the app's own browser: the pages are rendered here, the requests are made here,
 * and nothing is handed over to another app. A profile is a separate set of cookies, a
 * separate set of storage and a separate route to the internet, and switching profiles
 * switches all three.
 */
class BrowserActivity : ComponentActivity() {

    companion object {
        const val EXTRA_PROFILE_ID = "profile_id"
        /** Whether the profile has never been opened before. */
        const val EXTRA_FIRST_RUN = "first_run"
        /** A deep link opens a profile directly: joe://profile/<id> */
        const val DEEP_LINK = "joe://profile/"
        /** The page a profile opens on the very first time, so the user sees its identity. */
        const val FIRST_RUN_URL = "https://www.browserscan.net/"
    }

    private var session: BrowserSession? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val profileId = profileIdFrom(intent)
        var loaded = ProfileStore(this).find(profileId)
        if (loaded == null) {
            // The profile may be written to disk at the very moment the browser opens, so
            // a first miss is not the end of it: the file is read again a moment later.
            repeat(8) {
                if (loaded != null) return@repeat
                Thread.sleep(60)
                loaded = ProfileStore(this).find(profileId)
            }
        }
        if (loaded == null) {
            finish()
            return
        }
        val profile = loaded

        // The inspector is only available when the person using the app asked for it.
        runCatching {
            val developer = SettingsStore(this).load().developerMode
            WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG || developer)
        }

        // The language the profile claims has to be the language the engine asks for.
        // A request that says `bn-BD` in the script and `en-US` in the header is two
        // different machines talking at once, and the header is the one a server reads.
        val webContext = runCatching {
            val locale = java.util.Locale.forLanguageTag(profile.fp.language)
            if (locale.language.isNullOrBlank()) {
                this
            } else {
                val config = android.content.res.Configuration(resources.configuration)
                config.setLocale(locale)
                config.setLayoutDirection(locale)
                createConfigurationContext(config)
            }
        }.getOrDefault(this)

        val firstRun = intent.getBooleanExtra(EXTRA_FIRST_RUN, !profile.launchedOnce)
        val start = if (firstRun) FIRST_RUN_URL else profile.startUrl

        val browser = BrowserSession(this, profile)
        session = browser
        browser.onMessage = { toast(it) }
        browser.openTab(start)

        setContent {
            val dark = isSystemInDarkTheme()
            JoeTheme(themeMode = if (dark) 1 else 2) {
                val p = palette(dark)
                var toast by remember { mutableStateOf<String?>(null) }
                BrowserScreen(
                    session = browser,
                    palette = p,
                    onMessage = { text ->
                        toast = text
                        android.widget.Toast.makeText(this@BrowserActivity, text, android.widget.Toast.LENGTH_SHORT).show()
                    },
                    onBackPressed = { finish() },
                )
            }
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val tab = browser.current
                if (tab != null && tab.canGoBack) {
                    tab.view?.goBack()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }

    /** The profile this browser is for, whether it was opened directly or by a link. */
    private fun profileIdFrom(intent: Intent): String {
        val data = intent.data?.toString()
        if (data != null && data.startsWith(DEEP_LINK)) {
            return data.removePrefix(DEEP_LINK).substringBefore('?').trim('/')
        }
        return intent.getStringExtra(EXTRA_PROFILE_ID).orEmpty()
    }

    private fun toast(text: String) {
        runOnUiThread {
            android.widget.Toast.makeText(this, text, android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    override fun onPause() {
        super.onPause()
        session?.current?.view?.onPause()
    }

    override fun onResume() {
        super.onResume()
        session?.current?.view?.onResume()
    }

    override fun onDestroy() {
        session?.stop()
        session = null
        super.onDestroy()
    }
}
