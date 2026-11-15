package com.joebrowser.app.browser

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.URLUtil
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import com.joebrowser.app.data.model.Profile
import com.joebrowser.app.data.store.HistoryStore
import com.joebrowser.app.data.store.ProfileStore
import com.joebrowser.app.proxy.ProxyRelay
import com.joebrowser.app.ui.theme.JoePalette
import com.joebrowser.app.ui.theme.JoeTheme
import com.joebrowser.app.ui.theme.palette
import java.util.concurrent.Executor

/**
 * The browser screen.
 *
 * One profile at a time, with its own storage, its own identity and its own route to the
 * internet. The toolbar carries the tools that belong to the profile rather than to the
 * browser, which is what makes the difference between an anti-detect browser and a
 * WebView with a dark theme.
 */
class BrowserActivity : ComponentActivity() {

    companion object {
        const val EXTRA_PROFILE_ID = "profile_id"
        /** Whether the profile has never been opened before. */
        const val EXTRA_FIRST_RUN = "first_run"
        /** The page a profile opens on the very first time, so the user sees its identity. */
        const val FIRST_RUN_URL = "https://www.browserscan.net/"
    }

    private var webView: WebView? = null
    private var relay: ProxyRelay? = null
    private var profile: Profile? = null
    private val progressState = mutableStateOf(100)
    private val urlState = mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val profileId = intent.getStringExtra(EXTRA_PROFILE_ID).orEmpty()
        // The profile may be written to disk at the very moment the browser opens, so a
        // first miss is not the end of it: the file is read again a moment later.
        var loaded = ProfileStore(this).find(profileId)
        if (loaded == null) {
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
        profile = loaded

        // The language the profile claims has to be the language the engine asks for.
        // A request that says `bn-BD` in the script and `en-US` in the header is two
        // different machines talking at once, and the header is the one a server reads.
        // The engine takes the language from the context it is created with, so the
        // context is the thing that has to carry it.
        var webContext: Context = this
        runCatching {
            val locale = java.util.Locale.forLanguageTag(loaded.fp.language)
            if (!locale.language.isNullOrBlank()) {
                val config = android.content.res.Configuration(resources.configuration)
                config.setLocale(locale)
                config.setLayoutDirection(locale)
                webContext = createConfigurationContext(config)
            }
        }

        setContent {
            val dark = androidx.compose.foundation.isSystemInDarkTheme()
            JoeTheme(themeMode = if (dark) 1 else 2) {
                val p = palette(dark)
                BrowserScreen(
                    profile = loaded,
                    palette = p,
                    url = urlState.value,
                    progress = progressState.value,
                    webContext = webContext,
                    onWebViewReady = { view -> attach(view, loaded) },
                    onNavigate = { url -> webView?.loadUrl(url) },
                    onReload = { webView?.reload() },
                    onBack = { webView?.goBack() },
                    onForward = { webView?.goForward() },
                    onHome = { webView?.loadUrl(loaded.startUrl) },
                    onPasteHuman = { pasteAsHuman() },
                    onClear = { clearData(loaded) },
                    onOpenExternal = { url -> openExternally(url) },
                    onShare = { url -> share(url) },
                )
            }
        }
    }

    // ---------------------------------------------------------------------------------------
    // The engine
    // ---------------------------------------------------------------------------------------

    @SuppressLint("SetJavaScriptEnabled")
    private fun attach(view: WebView, p: Profile) {
        webView = view
        val firstRun = intent.getBooleanExtra(EXTRA_FIRST_RUN, !p.launchedOnce)
        // The inspector is only available when the person using the app asked for it.
        // A debug build always has it, because that is the build that is tested.
        runCatching {
            val developer = com.joebrowser.app.data.store.SettingsStore(this).load().developerMode
            WebView.setWebContentsDebuggingEnabled(
                com.joebrowser.app.BuildConfig.DEBUG || developer
            )
        }
        val settings = view.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.mediaPlaybackRequiresUserGesture = false
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
        settings.setSupportMultipleWindows(false)
        settings.javaScriptCanOpenWindowsAutomatically = false
        settings.cacheMode = if (p.freshSession) {
            android.webkit.WebSettings.LOAD_NO_CACHE
        } else {
            android.webkit.WebSettings.LOAD_DEFAULT
        }
        settings.userAgentString = WebEngine.resolvedUserAgent(p)
        view.isVerticalScrollBarEnabled = false
        view.overScrollMode = WebView.OVER_SCROLL_NEVER

        WebEngine.bindProfile(view, p.id)
        WebEngine.configure(view, p, false)

        // The requests are made by the app so that the headers belong to the profile
        // and not to the engine. Anything the app cannot do safely is handed back.
        val interceptor = ProfileRequestInterceptor(p) { relay?.port ?: 0 }

        view.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                v: WebView,
                request: WebResourceRequest,
            ): WebResourceResponse? = interceptor.intercept(request)

            override fun onPageStarted(v: WebView, u: String, f: android.graphics.Bitmap?) {
                urlState.value = u
                progressState.value = 20
            }

            override fun onPageFinished(v: WebView, u: String) {
                urlState.value = u
                progressState.value = 100
                runCatching { HistoryStore(this@BrowserActivity).record(p.id, u, v.title ?: "") }
            }
        }

        view.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(v: WebView, newProgress: Int) {
                progressState.value = newProgress
            }
        }

        view.setDownloadListener { url, userAgent, contentDisposition, mimeType, contentLength ->
            val name = URLUtil.guessFileName(url, contentDisposition, mimeType)
            try {
                val request = DownloadManager.Request(Uri.parse(url))
                request.setTitle(name)
                request.setMimeType(mimeType)
                request.addRequestHeader("User-Agent", userAgent)
                request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                request.setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_DOWNLOADS, name)
                val manager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                manager.enqueue(request)
                toast("Downloading $name")
            } catch (e: Exception) {
                toast("The download could not be started.")
            }
        }

        // The scripts that make the page look like the profile.
        val runtime = RuntimeBuilder(this)
        val scripts = mutableListOf(runtime.buildFingerprintScript(p), runtime.buildToolsScript(p))
        if (!WebEngine.installScripts(view, scripts)) {
            // An older engine has no document-start hook, so the scripts run as soon as the
            // page starts. It is later than it should be, but it still runs.
            for (script in scripts) {
                view.evaluateJavascript(script, null)
            }
        }

        // The route to the internet.
        if (p.proxy.enabled) {
            val r = ProxyRelay({ p.proxy })
            r.onFallback = { endpoint ->
                toast("Proxy $endpoint stopped responding. The session is on a direct connection, so the real address is visible.")
            }
            r.onOffline = { endpoint ->
                toast("No connection. The profile cannot reach the web through $endpoint.")
            }
            r.start()
            relay = r
            WebEngine.applyProxy(view, r.port, Executor { it.run() }) { ok ->
                if (!ok) toast("This engine cannot use a proxy override. The profile will connect directly.")
            }
        }

        val start = if (firstRun) FIRST_RUN_URL else p.startUrl
        view.loadUrl(start)
    }

    /** Types the clipboard into the page, one character at a time. */
    private fun pasteAsHuman() {
        val view = webView ?: return
        val p = profile ?: return
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        val clip = clipboard.primaryClip ?: return
        if (clip.itemCount == 0) return
        val text = clip.getItemAt(0).coerceToText(this).toString()
        if (text.isEmpty()) {
            toast("The clipboard is empty.")
            return
        }
        view.evaluateJavascript(RuntimeBuilder(this).buildPasteScript(text), null)
        toast("Typing ${text.length} characters like a person.")
    }

    private fun clearData(p: Profile) {
        WebEngine.clearProfileData(this, p)
        webView?.clearCache(true)
        webView?.clearHistory()
        webView?.loadUrl("about:blank")
        toast("Cookies and site data cleared.")
    }

    private fun openExternally(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: ActivityNotFoundException) {
            toast("No app can open this link.")
        }
    }

    private fun share(url: String) {
        try {
            startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, url)
            }, null))
        } catch (e: Exception) {
            toast("The link could not be shared.")
        }
    }

    private fun toast(text: String) {
        runOnUiThread {
            android.widget.Toast.makeText(this, text, android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        relay?.close()
        relay = null
        webView?.let {
            (it.parent as? ViewGroup)?.removeView(it)
            it.destroy()
        }
        webView = null
        super.onDestroy()
    }

    override fun onPause() {
        super.onPause()
        webView?.onPause()
    }

    override fun onResume() {
        super.onResume()
        webView?.onResume()
    }
}

// -------------------------------------------------------------------------------------------
// The screen
// -------------------------------------------------------------------------------------------

@Composable
private fun BrowserScreen(
    profile: Profile,
    palette: JoePalette,
    url: String,
    progress: Int,
    webContext: Context,
    onWebViewReady: (WebView) -> Unit,
    onNavigate: (String) -> Unit,
    onReload: () -> Unit,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onHome: () -> Unit,
    onPasteHuman: () -> Unit,
    onClear: () -> Unit,
    onOpenExternal: (String) -> Unit,
    onShare: (String) -> Unit,
) {
    val context = LocalContext.current
    var menuOpen by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .background(palette.bg)
            .padding(WindowInsets.systemBars.asPaddingValues()),
    ) {
        // The address bar.
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Box(
                Modifier
                    .weight(1f)
                    .height(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(palette.input)
                    .padding(horizontal = 10.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (editing) {
                    var draft by remember { mutableStateOf(url) }
                    val focus = remember { androidx.compose.ui.focus.FocusRequester() }
                    androidx.compose.foundation.text.BasicTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focus)
                            .onFocusChanged { if (it.isFocused) { draft = url } },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = palette.text),
                        cursorBrush = androidx.compose.ui.graphics.SolidColor(palette.accent),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            imeAction = androidx.compose.ui.text.input.ImeAction.Go,
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Uri,
                        ),
                        keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                            onGo = {
                                editing = false
                                val target = draft.trim()
                                if (target.isNotEmpty()) {
                                    val url2 = if (target.startsWith("http://") || target.startsWith("https://")) {
                                        target
                                    } else {
                                        "https://"
                                    }
                                    onNavigate(url2)
                                }
                            }
                        ),
                    )
                } else {
                    Text(
                        text = url.ifBlank { profile.startUrl },
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                editing = true
                            },
                    )
                }
            }
            IconButton(onClick = { menuOpen = true }) {
                Icon(Icons.Outlined.MoreVert, contentDescription = "Menu", tint = palette.textDim)
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text("Paste as human") },
                    onClick = { menuOpen = false; onPasteHuman() },
                    leadingIcon = { Icon(Icons.Outlined.Keyboard, null) },
                )
                DropdownMenuItem(
                    text = { Text("Copy link") },
                    onClick = {
                        menuOpen = false
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("url", url))
                    },
                    leadingIcon = { Icon(Icons.Outlined.ContentCopy, null) },
                )
                DropdownMenuItem(
                    text = { Text("Open in another app") },
                    onClick = { menuOpen = false; onOpenExternal(url) },
                    leadingIcon = { Icon(Icons.Outlined.Info, null) },
                )
                DropdownMenuItem(
                    text = { Text("Share link") },
                    onClick = { menuOpen = false; onShare(url) },
                    leadingIcon = { Icon(Icons.Outlined.Add, null) },
                )
                DropdownMenuItem(
                    text = { Text("Clear cookies and site data") },
                    onClick = { menuOpen = false; onClear() },
                    leadingIcon = { Icon(Icons.Outlined.Shield, null) },
                )
            }
        }

        if (progress in 1..99) {
            LinearProgressIndicator(
                progress = { progress / 100f },
                modifier = Modifier.fillMaxWidth().height(2.dp),
                color = palette.accent,
                trackColor = palette.line,
            )
        }

        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(palette.bgSoft)
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    WebView(webContext).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        )
                        onWebViewReady(this)
                    }
                },
            )
        }

        // The navigation bar.
        Row(
            Modifier
                .fillMaxWidth()
                .background(palette.bgSoft)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Outlined.ArrowBack, contentDescription = "Back", tint = palette.textDim)
            }
            IconButton(onClick = onForward) {
                Icon(Icons.Outlined.ArrowForward, contentDescription = "Forward", tint = palette.textDim)
            }
            IconButton(onClick = onHome) {
                Icon(Icons.Outlined.Home, contentDescription = "Start page", tint = palette.textDim)
            }
            IconButton(onClick = onReload) {
                Icon(Icons.Outlined.Refresh, contentDescription = "Reload", tint = palette.textDim)
            }
            IconButton(onClick = { onNavigate("about:blank") }) {
                Icon(Icons.Outlined.Close, contentDescription = "Stop", tint = palette.textDim)
            }
        }
    }
}
