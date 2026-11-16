package com.joebrowser.app.browser

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.joebrowser.app.data.model.Profile
import com.joebrowser.app.proxy.ProxyRelay

/**
 * One tab.
 *
 * A tab owns its own view, so switching tabs is a switch of views rather than a reload.
 * A browser that reloads the page every time you come back to a tab is not a browser, it
 * is a list of links.
 */
class BrowserTab(
    val id: String,
    val profile: Profile,
    val engine: Engine,
) {
    var view: WebView? = null
    var url: String by mutableStateOf("")
    var title: String by mutableStateOf("")
    var progress: Int by mutableStateOf(100)
    var canGoBack: Boolean by mutableStateOf(false)
    var canGoForward: Boolean by mutableStateOf(false)
    var loading: Boolean by mutableStateOf(false)
    var favicon: Bitmap? = null

    /** The desktop site toggle for this tab. */
    var desktopSite: Boolean by mutableStateOf(false)

    /** The reader mode toggle for this tab. */
    var readerMode: Boolean by mutableStateOf(false)
}

/**
 * The browsing session of one profile.
 *
 * It owns the tabs, the route to the internet and the pieces of state the interface needs
 * but the page does not: the bookmarks, the downloads and the history of what was typed.
 */
class BrowserSession(
    private val context: Context,
    val profile: Profile,
) {
    val engine: Engine = Engine.byKey(profile.browserKind)

    val tabs = mutableListOf<BrowserTab>()
    var currentIndex: Int = -1

    /** The local relay the pages go through, when the profile has a proxy. */
    var relay: ProxyRelay? = null
        private set

    /** Called when the session wants the interface to say something. */
    var onMessage: ((String) -> Unit)? = null

    /** Called when the session wants a new page to open in the interface. */
    var onOpenUrl: ((String) -> Unit)? = null

    private val runtime = RuntimeBuilder(context)
    private val scripts by lazy {
        listOf(runtime.buildFingerprintScript(profile), runtime.buildToolsScript(profile))
    }

    /**
     * The app's own pages are served over a secure address rather than from the file
     * system.
     *
     * A page on `file://` is not a secure context, and a page that is not a secure
     * context cannot do half of what a browser is asked to do. The loader gives the
     * identity check the same footing as any other page on the internet.
     */
    private val assetLoader by lazy {
        androidx.webkit.WebViewAssetLoader.Builder()
            .addPathHandler(
                "/assets/",
                androidx.webkit.WebViewAssetLoader.AssetsPathHandler(context),
            )
            .build()
    }

    /** The bookmarks of the profile. */
    val bookmarks = BookmarkStore(context)

    /** The downloads of the profile. */
    val downloads = DownloadStore(context)

    /** The pages the profile has been to, most recent first. */
    val history by lazy { com.joebrowser.app.data.store.HistoryStore(context) }

    val current: BrowserTab? get() = tabs.getOrNull(currentIndex)

    // -----------------------------------------------------------------------------------------
    // Tabs
    // -----------------------------------------------------------------------------------------

    fun openTab(url: String, select: Boolean = true): BrowserTab {
        val tab = BrowserTab(newId(), profile, engine)
        tabs.add(tab)
        if (select) currentIndex = tabs.size - 1
        build(tab, url)
        return tab
    }

    fun closeTab(index: Int) {
        if (index !in tabs.indices) return
        val tab = tabs.removeAt(index)
        tab.view?.let { v ->
            (v.parent as? android.view.ViewGroup)?.removeView(v)
            v.destroy()
        }
        if (tabs.isEmpty()) {
            currentIndex = -1
            openTab(START_PAGE)
        } else if (currentIndex >= tabs.size) {
            currentIndex = tabs.size - 1
        }
    }

    fun select(index: Int) {
        if (index in tabs.indices) currentIndex = index
    }

    /**
     * Opens the identity check.
     *
     * The page reads everything the browser will tell a site, compares it with what the
     * profile says, and shows the ones that disagree. It is the difference between
     * believing the profile is consistent and having checked.
     */
    fun openAudit(): BrowserTab = openTab(AUDIT_URL)



    fun newId(): String = java.util.UUID.randomUUID().toString().replace("-", "").take(12)

    // -----------------------------------------------------------------------------------------
    // The engine
    // -----------------------------------------------------------------------------------------

    @SuppressLint("SetJavaScriptEnabled")
    private fun build(tab: BrowserTab, url: String) {
        val view = WebView(context).apply {
            layoutParams = android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            )
        }
        tab.view = view
        val settings = view.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.mediaPlaybackRequiresUserGesture = false
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        settings.setSupportMultipleWindows(false)
        settings.javaScriptCanOpenWindowsAutomatically = false
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true
        settings.setSupportZoom(true)
        settings.builtInZoomControls = true
        settings.displayZoomControls = false
        settings.userAgentString = WebEngine.resolvedUserAgent(profile)
        settings.cacheMode = if (profile.freshSession) {
            WebSettings.LOAD_NO_CACHE
        } else {
            WebSettings.LOAD_DEFAULT
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) settings.safeBrowsingEnabled = false

        view.isVerticalScrollBarEnabled = true
        view.isHorizontalScrollBarEnabled = false
        view.overScrollMode = WebView.OVER_SCROLL_NEVER

        WebEngine.bindProfile(view, profile.id)
        WebEngine.configure(view, profile, false)

        val interceptor = ProfileRequestInterceptor(profile) { relay?.port ?: 0 }

        view.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                v: WebView,
                request: WebResourceRequest,
            ): WebResourceResponse? {
                // The app's own pages are served from the app's own assets.
                if (request.url.host == "appassets.androidplatform.net") {
                    return assetLoader.shouldInterceptRequest(request.url)
                }
                return interceptor.intercept(request)
            }

            override fun onPageStarted(v: WebView, u: String, f: Bitmap?) {
                tab.url = u
                tab.title = u
                tab.loading = true
                tab.progress = 10
                if (tab.readerMode) applyReaderMode(tab, false)
            }

            override fun onPageFinished(v: WebView, u: String) {
                tab.url = u
                tab.title = v.title ?: u
                tab.loading = false
                tab.progress = 100
                tab.canGoBack = v.canGoBack()
                tab.canGoForward = v.canGoForward()
                if (tab.desktopSite) applyDesktopSite(tab, true)
                if (tab.readerMode) applyReaderMode(tab, true)
                runCatching { history.record(profile.id, u, v.title ?: "") }
            }
        }

        view.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(v: WebView, newProgress: Int) {
                tab.progress = newProgress
                tab.loading = newProgress < 100
            }

            override fun onReceivedIcon(v: WebView, icon: Bitmap?) {
                if (icon != null) tab.favicon = icon
            }
        }

        view.setDownloadListener { url, userAgent, disposition, mime, length ->
            downloads.enqueue(context, url, userAgent, disposition, mime)
            onMessage?.invoke("Download started")
        }

        // The scripts that make the page look like the profile.
        val all = scripts + runtime.buildAuditConfig(profile)
        if (!WebEngine.installScripts(view, all)) {
            for (script in all) view.evaluateJavascript(script, null)
        }

        view.loadUrl(if (url.isBlank()) START_PAGE else url)
    }

    /** The page that opens when nothing else is asked for. */
    private val START_PAGE: String
        get() = profile.startUrl.ifBlank { "https://ipfighter.com/browser-fingerprint" }

    // -----------------------------------------------------------------------------------------
    // The route
    // -----------------------------------------------------------------------------------------

    fun start() {
        if (relay != null) return
        if (!profile.proxy.enabled) return
        val r = ProxyRelay({ profile.proxy })
        r.onFallback = { endpoint ->
            onMessage?.invoke("Proxy $endpoint stopped responding. The page is on a direct connection.")
        }
        r.onOffline = { endpoint ->
            onMessage?.invoke("No connection through $endpoint.")
        }
        r.start()
        relay = r
        WebEngine.applyProxy(
            current?.view ?: return,
            r.port,
            { it.run() },
        ) { ok ->
            if (!ok) onMessage?.invoke("This engine cannot use a proxy override.")
        }
    }

    fun stop() {
        relay?.close()
        relay = null
        for (tab in tabs) {
            tab.view?.let { v ->
                (v.parent as? android.view.ViewGroup)?.removeView(v)
                v.destroy()
            }
        }
        tabs.clear()
        currentIndex = -1
    }

    // -----------------------------------------------------------------------------------------
    // Page tricks
    // -----------------------------------------------------------------------------------------

    /** Asks the page for the wide layout, the way a desktop site does. */
    fun applyDesktopSite(tab: BrowserTab, on: Boolean) {
        tab.desktopSite = on
        runCatching {
            tab.view?.settings?.useWideViewPort = !on
            tab.view?.settings?.loadWithOverviewMode = !on
        }
        tab.view?.reload()
    }

    /**
     * Turns the page into plain text.
     *
     * The article is pulled out of the page and shown on its own, with the links kept.
     */
    fun applyReaderMode(tab: BrowserTab, on: Boolean) {
        tab.readerMode = on
        val view = tab.view ?: return
        if (!on) {
            view.evaluateJavascript("try{document.getElementById('jb-reader')&&document.getElementById('jb-reader').remove();}catch(e){}", null)
            return
        }
        view.evaluateJavascript(READER_SCRIPT, null)
    }

    companion object {
        /** The app's own identity check, served over a secure address. */
        const val AUDIT_URL = "https://appassets.androidplatform.net/assets/runtime/audit.html"

        private const val READER_SCRIPT = """
(function(){
  try {
    var old = document.getElementById('jb-reader');
    if (old) old.remove();
    var pick = document.querySelector('article') || document.querySelector('main') || document.body;
    if (!pick) return;
    var box = document.createElement('div');
    box.id = 'jb-reader';
    box.style.cssText = 'position:fixed;inset:0;z-index:2147483647;background:#fff;color:#16181d;' +
      'overflow:auto;padding:28px 22px 60px;line-height:1.72;font-size:17px;' +
      'font-family:-apple-system,BlinkMacSystemFont,Segoe UI,Roboto,sans-serif;';
    var h = document.createElement('h1');
    h.textContent = document.title || '';
    h.style.cssText = 'font-size:24px;margin:0 0 18px;line-height:1.3;';
    var c = pick.cloneNode(true);
    c.querySelectorAll('script,style,nav,footer,aside,form').forEach(function(n){n.remove();});
    box.appendChild(h);
    box.appendChild(c);
    var close = document.createElement('div');
    close.textContent = 'Close';
    close.style.cssText = 'position:fixed;top:14px;right:16px;background:#16181d;color:#fff;' +
      'padding:8px 14px;border-radius:10px;font-size:14px;cursor:pointer;z-index:2147483647;';
    close.addEventListener('click', function(){ box.remove(); });
    box.appendChild(close);
    document.body.appendChild(box);
  } catch (e) {}
})();
"""
    }
}
