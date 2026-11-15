package com.joebrowser.app.extensions

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.WebResourceRequest
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
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import com.joebrowser.app.ui.theme.JoePalette
import com.joebrowser.app.ui.theme.JoeTheme
import com.joebrowser.app.ui.theme.palette
import kotlinx.coroutines.launch

/**
 * The Chrome Web Store, inside the app.
 *
 * The store is the only place that knows what an extension really does, so the store is
 * where the user goes. When a listing is opened, the extension's id is taken from the
 * address and the package is fetched the way the store itself hands it out.
 */
class ExtensionStoreActivity : ComponentActivity() {

    companion object {
        const val EXTRA_QUERY = "query"
    }

    private var webView: WebView? = null
    private var detectedId: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val query = intent.getStringExtra(EXTRA_QUERY).orEmpty()

        setContent {
            val dark = androidx.compose.foundation.isSystemInDarkTheme()
            JoeTheme(themeMode = if (dark) 1 else 2) {
                val p = palette(dark)
                StoreScreen(
                    palette = p,
                    onWebViewReady = { view -> attach(view, query) },
                    onBack = { webView?.goBack() },
                    onInstall = { id -> install(id) },
                    detected = { detectedId },
                )
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun attach(view: WebView, query: String) {
        webView = view
        view.settings.javaScriptEnabled = true
        view.settings.domStorageEnabled = true
        view.settings.userAgentString =
            "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/131.0.0.0 Mobile Safari/537.36"
        view.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(v: WebView, request: WebResourceRequest): Boolean {
                val url = request.url.toString()
                if (url.contains("chromewebstore.google.com/detail/")) {
                    detectedId = ExtensionManager.storeIdFromUrl(url)
                }
                return false
            }

            override fun onPageFinished(v: WebView, url: String) {
                if (url.contains("chromewebstore.google.com/detail/")) {
                    detectedId = ExtensionManager.storeIdFromUrl(url)
                }
            }
        }
        view.loadUrl(ExtensionManager.storeSearchUrl(query))
    }

    private fun install(id: String) {
        if (id.isBlank()) {
            android.widget.Toast.makeText(this, "Open an extension listing first.", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        android.widget.Toast.makeText(this, "Downloading the extension…", android.widget.Toast.LENGTH_SHORT).show()
        Thread {
            val result = kotlinx.coroutines.runBlocking {
                ExtensionManager.installFromStore(this@ExtensionStoreActivity, id)
            }
            runOnUiThread {
                result.onSuccess {
                    android.widget.Toast.makeText(
                        this, "${it.name} was installed.", android.widget.Toast.LENGTH_LONG
                    ).show()
                }.onFailure {
                    android.widget.Toast.makeText(
                        this, it.message ?: "The extension could not be installed.",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                }
            }
        }.start()
    }

    override fun onDestroy() {
        webView?.let {
            (it.parent as? ViewGroup)?.removeView(it)
            it.destroy()
        }
        webView = null
        super.onDestroy()
    }
}

@Composable
private fun StoreScreen(
    palette: JoePalette,
    onWebViewReady: (WebView) -> Unit,
    onBack: () -> Unit,
    onInstall: (String) -> Unit,
    detected: () -> String,
) {
    var id by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .background(palette.bg)
            .padding(WindowInsets.systemBars.asPaddingValues()),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Outlined.ArrowBack, contentDescription = "Back", tint = palette.textDim)
            }
            Column(Modifier.weight(1f)) {
                Text(
                    "Chrome Web Store",
                    style = MaterialTheme.typography.titleLarge,
                    color = palette.text,
                )
                Text(
                    text = if (id.isBlank()) "Pick an extension to install" else "Extension $id",
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.textFaint,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Button(
                onClick = { onInstall(id) },
                enabled = id.isNotBlank() && !busy,
                colors = ButtonDefaults.buttonColors(
                    containerColor = palette.accent, contentColor = palette.onAccent
                ),
                shape = RoundedCornerShape(10.dp),
            ) {
                if (busy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                        color = palette.onAccent,
                    )
                } else {
                    Icon(Icons.Outlined.Download, null, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Install")
                }
            }
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
                    WebView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        )
                        onWebViewReady(this)
                    }
                },
            )
        }
    }
}
