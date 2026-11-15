package com.joebrowser.app.browser

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.webkit.Profile as WebViewProfile
import androidx.webkit.ProfileStore
import androidx.webkit.ScriptHandler
import androidx.webkit.UserAgentMetadata
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.joebrowser.app.data.model.Profile
import com.joebrowser.app.fingerprint.UserAgentBuilder
import java.util.concurrent.Executor

/**
 * Prepares a WebView so that it looks and behaves like the profile it belongs to.
 *
 * Three things have to line up for a profile to be convincing: the identity the page
 * sees, the identity the server sees in the headers, and the identity the browser engine
 * keeps between sessions. The first two are set here, and the third comes from binding the
 * WebView to a named profile, which gives the profile its own cookies, its own cache and
 * its own storage, untouched by any other profile in the app.
 */
object WebEngine {

    /** The name of the WebView profile a browser profile is bound to. */
    fun profileName(profileId: String): String = "joe_" + profileId.replace(Regex("[^A-Za-z0-9]"), "")

    /** True when this WebView can give each profile its own storage. */
    fun supportsMultiProfile(): Boolean =
        WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)

    /** True when this WebView can be told a user agent and a client-hint set. */
    fun supportsUserAgentMetadata(): Boolean =
        WebViewFeature.isFeatureSupported(WebViewFeature.USER_AGENT_METADATA)

    /** True when this WebView can be told a proxy. */
    fun supportsProxyOverride(): Boolean =
        WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)

    /** True when this WebView can run a script before the page's own scripts. */
    fun supportsDocumentStartScript(): Boolean =
        WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)

    /**
     * Binds the WebView to the profile's own storage.
     *
     * This has to happen before the WebView loads anything, so it is done the moment the
     * view is created and never again.
     */
    fun bindProfile(webView: WebView, profileId: String): Boolean {
        if (!supportsMultiProfile()) return false
        return try {
            WebViewCompat.setProfile(webView, profileName(profileId))
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Applies the settings every profile needs.
     */
    @SuppressLint("SetJavaScriptEnabled")
    fun configure(webView: WebView, profile: Profile, darkMode: Boolean) {
        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.loadsImagesAutomatically = true
        settings.mediaPlaybackRequiresUserGesture = false
        settings.useWideViewPort = false
        settings.loadWithOverviewMode = false
        settings.setSupportZoom(true)
        settings.builtInZoomControls = true
        settings.displayZoomControls = false
        settings.cacheMode = WebSettings.LOAD_DEFAULT
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        settings.javaScriptCanOpenWindowsAutomatically = true
        settings.setSupportMultipleWindows(false)
        settings.userAgentString = resolvedUserAgent(profile)

        // A browser engine on a phone tags every request with the name of the app it
        // lives in. No desktop browser does that, and a header that names the package is
        // the shortest way there is to say "this is a WebView".
        try {
            if (WebViewFeature.isFeatureSupported(WebViewFeature.REQUESTED_WITH_HEADER_ALLOW_LIST)) {
                WebSettingsCompat.setRequestedWithHeaderOriginAllowList(settings, emptySet())
            }
        } catch (e: Exception) {}

        // The cache mode matters for a profile that is supposed to be a fresh machine.
        if (profile.freshSession) {
            settings.cacheMode = WebSettings.LOAD_NO_CACHE
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            settings.safeBrowsingEnabled = false
        }

        webView.isScrollbarFadingEnabled = false
        webView.overScrollMode = WebView.OVER_SCROLL_NEVER
        webView.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        webView.isVerticalScrollBarEnabled = true
        webView.isHorizontalScrollBarEnabled = false

        applyUserAgentMetadata(webView, profile)

        try {
            CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)
        } catch (e: Exception) {}
    }

    /** The user agent the profile reports. */
    fun resolvedUserAgent(profile: Profile): String {
        val resolved = UserAgentBuilder.resolve(profile.fp, profile.browserKind, profile.browserVersion)
        return resolved.userAgent
    }

    /**
     * Sets the client hints so the headers and the script agree.
     *
     * A browser that sends one brand list in its headers and another in
     * `navigator.userAgentData` is telling the checker that something is rewriting it.
     */
    private fun applyUserAgentMetadata(webView: WebView, profile: Profile) {
        if (!supportsUserAgentMetadata()) return
        try {
            val resolved = UserAgentBuilder.resolve(profile.fp, profile.browserKind, profile.browserVersion)
            val brands = ArrayList<UserAgentMetadata.BrandVersion>()
            for ((brand, version) in resolved.brands) {
                brands.add(
                    UserAgentMetadata.BrandVersion.Builder()
                        .setBrand(brand)
                        .setMajorVersion(version.substringBefore('.'))
                        .setFullVersion(if (version.contains('.')) version else "$version.0.0.0")
                        .build()
                )
            }
            val metadata = UserAgentMetadata.Builder()
                .setBrandVersionList(brands)
                .setFullVersion(resolved.fullVersion)
                .setPlatform(
                    when (resolved.platform) {
                        "Win32" -> "Windows"
                        "MacIntel" -> "macOS"
                        "Linux x86_64" -> "Linux"
                        "iPhone", "iPad" -> "iOS"
                        else -> "Android"
                    }
                )
                .setPlatformVersion(resolved.platformVersion)
                .setArchitecture(resolved.architecture)
                .setModel(resolved.model)
                .setMobile(resolved.mobile)
                .setBitness(64)
                .setWow64(resolved.wow64)
                .build()
            WebSettingsCompat.setUserAgentMetadata(webView.settings, metadata)
        } catch (e: Exception) {
            // An older WebView simply keeps its own hints.
        }
    }

    /**
     * Points the WebView at the local relay.
     *
     * The relay is on the loopback address and holds the credentials, so the browser never
     * sees them and never has to authenticate by itself.
     */
    fun applyProxy(webView: WebView, relayPort: Int, executor: Executor, onDone: (Boolean) -> Unit) {
        if (!supportsProxyOverride()) {
            onDone(false)
            return
        }
        if (relayPort <= 0) {
            clearProxy(executor, onDone)
            return
        }
        try {
            val config = androidx.webkit.ProxyConfig.Builder()
                .addProxyRule("127.0.0.1:$relayPort")
                .addBypassRule("localhost")
                .addBypassRule("127.0.0.1")
                .build()
            androidx.webkit.ProxyController.getInstance().setProxyOverride(config, executor) {
                onDone(true)
            }
        } catch (e: Exception) {
            onDone(false)
        }
    }

    /** Removes the proxy override. */
    fun clearProxy(executor: Executor, onDone: (Boolean) -> Unit) {
        if (!supportsProxyOverride()) {
            onDone(false)
            return
        }
        try {
            androidx.webkit.ProxyController.getInstance().clearProxyOverride(executor) { onDone(true) }
        } catch (e: Exception) {
            onDone(false)
        }
    }

    /**
     * Installs the scripts that run before the page's own code.
     */
    fun installScripts(webView: WebView, scripts: List<String>): Boolean {
        if (!supportsDocumentStartScript()) return false
        return try {
            for (script in scripts) {
                WebViewCompat.addDocumentStartJavaScript(webView, script, setOf("*"))
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Erases everything the profile has stored.
     */
    fun clearProfileData(context: Context, profile: Profile) {
        try {
            if (supportsMultiProfile()) {
                val store = ProfileStore.getInstance()
                val webProfile: WebViewProfile = store.getOrCreateProfile(profileName(profile.id))
                webProfile.cookieManager.removeAllCookies(null)
                webProfile.cookieManager.flush()
                webProfile.webStorage.deleteAllData()
                webProfile.geolocationPermissions.clearAll()
            }
        } catch (e: Exception) {}
        try {
            context.cacheDir.deleteRecursively()
            context.cacheDir.mkdirs()
        } catch (e: Exception) {}
    }
}
