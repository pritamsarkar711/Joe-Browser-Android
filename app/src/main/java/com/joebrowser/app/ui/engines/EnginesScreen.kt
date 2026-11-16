package com.joebrowser.app.ui.engines

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.TravelExplore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.joebrowser.app.browser.Engine
import com.joebrowser.app.fingerprint.FingerprintFactory
import com.joebrowser.app.fingerprint.UserAgentBuilder
import com.joebrowser.app.ui.AppState
import com.joebrowser.app.ui.components.ChipTone
import com.joebrowser.app.ui.components.JoeCard
import com.joebrowser.app.ui.components.JoeChip
import com.joebrowser.app.ui.components.Note
import com.joebrowser.app.ui.components.SectionLabel
import com.joebrowser.app.ui.theme.JoePalette

/**
 * The engines.
 *
 * Every engine here is built into the app. There is nothing to download, nothing to
 * install and nothing that hands the page over to another program: the profile chooses
 * an engine, the page is rendered here, and the identity it carries is the identity of
 * the engine it names.
 *
 * That is the whole point. A real Chrome on the device would be a different program with
 * its own idea of what the machine is, and the profile would be describing a machine the
 * browser had never seen.
 */
@Composable
fun EnginesScreen(
    state: AppState,
    palette: JoePalette,
    onMessage: (String, Boolean) -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val webViewVersion = remember { com.joebrowser.app.browser.BrowserEngines.webViewVersion(context) }

    Column(
        Modifier
            .fillMaxSize()
            .background(palette.bg)
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text("Engines", style = MaterialTheme.typography.headlineMedium, color = palette.text)
            Text(
                "Built into the app",
                style = MaterialTheme.typography.bodyMedium,
                color = palette.textFaint,
            )
        }

        LazyColumn(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                SectionLabel(palette, "This device")
                JoeCard(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
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
                                "Rendering engine",
                                style = MaterialTheme.typography.titleMedium,
                                color = palette.text,
                            )
                            Text(
                                text = if (webViewVersion.isNotBlank()) {
                                    "Chromium $webViewVersion · every profile runs on this"
                                } else {
                                    "Every profile runs on this"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = palette.textFaint,
                            )
                        }
                        JoeChip(palette = palette, text = "In use", tone = ChipTone.Success)
                    }
                }
            }

            item { SectionLabel(palette, "Engines") }

            items(Engine.SELECTABLE.size) { index ->
                val engine = Engine.SELECTABLE[index]
                val inUse = state.profiles.any { it.browserKind == engine.key }
                EngineCard(
                    engine = engine,
                    palette = palette,
                    inUse = inUse,
                    userAgent = previewUserAgent(state, engine),
                )
            }

            item {
                SectionLabel(palette, "How it works")
                JoeCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Note(
                            palette = palette,
                            text = "The engine decides the name on the user agent and the " +
                                "brand in the client hints. The rest of the identity — the " +
                                "screen, the hardware, the graphics, the clock — is the " +
                                "profile's own.",
                        )
                        Spacer(Modifier.height(10.dp))
                        Note(
                            palette = palette,
                            text = "A profile reports the build that is really running. A " +
                                "version the engine does not match is the one thing a " +
                                "page can find out about, and it finds it.",
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EngineCard(
    engine: Engine,
    palette: JoePalette,
    inUse: Boolean,
    userAgent: String,
) {
    JoeCard(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(engine.accentColor.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    engine.label.take(1),
                    style = MaterialTheme.typography.titleMedium,
                    color = engine.accentColor,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        engine.label,
                        style = MaterialTheme.typography.titleMedium,
                        color = palette.text,
                    )
                    if (inUse) {
                        Spacer(Modifier.width(8.dp))
                        Icon(
                            Icons.Outlined.Check, "In use",
                            tint = palette.success, modifier = Modifier.size(14.dp),
                        )
                    }
                }
                Text(
                    engine.tagline,
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.textFaint,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    userAgent,
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.textFaint,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** The user agent an engine produces for the first profile in the workspace. */
private fun previewUserAgent(state: AppState, engine: Engine): String {
    val profile = state.profiles.firstOrNull()
    val fp = profile?.fp ?: com.joebrowser.app.data.model.FingerprintConfig()
    return UserAgentBuilder.resolve(fp, engine.key, profile?.browserVersion).userAgent
}


