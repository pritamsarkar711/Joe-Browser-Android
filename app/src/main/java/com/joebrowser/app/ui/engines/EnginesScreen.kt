package com.joebrowser.app.ui.engines

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.TravelExplore
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.joebrowser.app.browser.BrowserEngines
import com.joebrowser.app.ui.AppState
import com.joebrowser.app.ui.components.ChipTone
import com.joebrowser.app.ui.components.JoeCard
import com.joebrowser.app.ui.components.JoeChip
import com.joebrowser.app.ui.components.Note
import com.joebrowser.app.ui.components.SectionLabel
import com.joebrowser.app.ui.theme.JoePalette
import kotlinx.coroutines.launch

/**
 * The browser engines.
 *
 * The engine that renders a page on a phone is the system WebView, and its version is the
 * number a profile has to report. The other engines are the browsers on the device: a
 * profile can name one of them, and the app can fetch the official package for the ones
 * that are not there yet.
 */
@Composable
fun EnginesScreen(
    state: AppState,
    palette: JoePalette,
    onMessage: (String, Boolean) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var downloading by remember { mutableStateOf("") }
    var progress by remember { mutableStateOf(0) }

    val engines = remember { BrowserEngines.all() }
    val webViewVersion = remember { BrowserEngines.webViewVersion(context) }

    Column(
        Modifier
            .fillMaxSize()
            .background(palette.bg)
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text("Engines", style = MaterialTheme.typography.headlineMedium, color = palette.text)
            Text(
                "The browser that renders the pages",
                style = MaterialTheme.typography.bodyMedium,
                color = palette.textFaint,
            )
        }

        LazyColumn(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                SectionLabel(palette, "This device")
                JoeCard(Modifier.fillMaxWidth()) {
                    Column {
                        Row(
                            Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(palette.accentOnBg),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.Outlined.TravelExplore, null,
                                    tint = palette.accent, modifier = Modifier.size(20.dp),
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "System WebView",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = palette.text,
                                )
                                Text(
                                    text = if (webViewVersion.isNotBlank()) {
                                        "Version $webViewVersion · renders every profile"
                                    } else {
                                        "The engine that renders every profile"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = palette.textFaint,
                                )
                            }
                            JoeChip(palette = palette, text = "In use", tone = ChipTone.Success)
                        }
                    }
                }
            }

            item {
                SectionLabel(palette, "Available")
            }

            items(
                count = engines.count { !it.isWebView },
                key = { index -> engines.filter { !it.isWebView }[index].key },
            ) { index ->
                val engine = engines.filter { !it.isWebView }[index]
                val installed = remember(engine.key) {
                    BrowserEngines.installedVersion(context, engine)
                }
                JoeCard(Modifier.fillMaxWidth()) {
                    Column {
                        Row(
                            Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    engine.label,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = palette.text,
                                )
                                Text(
                                    text = if (installed.isNotBlank()) {
                                        "Installed · version $installed"
                                    } else {
                                        "Not installed on this device"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = palette.textFaint,
                                )
                            }
                            if (installed.isNotBlank()) {
                                JoeChip(palette = palette, text = "Installed", tone = ChipTone.Success)
                            }
                        }
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .padding(bottom = 14.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            OutlinedButton(
                                onClick = {
                                    runCatching {
                                        context.startActivity(BrowserEngines.storeIntent(engine))
                                    }.onFailure { onMessage("No app can open the store.", true) }
                                },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = palette.text),
                            ) {
                                Icon(Icons.Outlined.OpenInNew, null, modifier = Modifier.size(15.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Get it")
                            }
                            if (engine.downloadUrls.isNotEmpty()) {
                                OutlinedButton(
                                    onClick = {
                                        downloading = engine.key
                                        progress = 0
                                        scope.launch {
                                            val result = BrowserEngines.download(
                                                context, engine,
                                            ) { percent, _ -> progress = percent }
                                            downloading = ""
                                            result.onSuccess { file ->
                                                val intent = BrowserEngines.installIntent(context, file)
                                                if (intent != null) {
                                                    runCatching { context.startActivity(intent) }
                                                } else {
                                                    onMessage(
                                                        "The package was saved, but the installer could not be started.",
                                                        true,
                                                    )
                                                }
                                            }.onFailure {
                                                onMessage(it.message ?: "The download failed.", true)
                                            }
                                        }
                                    },
                                    enabled = downloading.isEmpty(),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = palette.text),
                                ) {
                                    if (downloading == engine.key) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(14.dp),
                                            strokeWidth = 2.dp,
                                            color = palette.accent,
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text("$progress%")
                                    } else {
                                        Icon(Icons.Outlined.Download, null, modifier = Modifier.size(15.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Download")
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item {
                SectionLabel(palette, "How it works")
                JoeCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Note(
                            palette = palette,
                            text = "A profile reports the version of the engine it names. " +
                                "A build that is not on the device is a build nobody can reproduce, " +
                                "and a scanner notices that long before it notices anything else.",
                        )
                        Spacer(Modifier.height(10.dp))
                        Note(
                            palette = palette,
                            text = "Downloaded packages are handed to the system installer, which asks you to confirm. " +
                                "Nothing is ever installed without you.",
                        )
                    }
                }
            }
        }
    }
}
