package com.joebrowser.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.joebrowser.app.core.AppPaths
import com.joebrowser.app.data.model.AppSettings
import com.joebrowser.app.ui.AppState
import com.joebrowser.app.ui.components.ChipTone
import com.joebrowser.app.ui.components.JoeCard
import com.joebrowser.app.ui.components.JoeChip
import com.joebrowser.app.ui.components.JoeField
import com.joebrowser.app.ui.components.KeyValue
import com.joebrowser.app.ui.components.Note
import com.joebrowser.app.ui.components.RowDivider
import com.joebrowser.app.ui.components.SectionLabel
import com.joebrowser.app.ui.components.SettingRow
import com.joebrowser.app.ui.theme.JoePalette

/**
 * The settings.
 */
@Composable
fun SettingsScreen(
    state: AppState,
    palette: JoePalette,
    onSave: (AppSettings) -> Unit,
    onMessage: (String, Boolean) -> Unit,
    onClearHistory: () -> Unit,
) {
    val context = LocalContext.current
    var draft by remember(state.settings) { mutableStateOf(state.settings) }
    var confirmClear by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .background(palette.bg)
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text("Settings", style = MaterialTheme.typography.headlineMedium, color = palette.text)
            Text(
                "Browser and storage",
                style = MaterialTheme.typography.bodyMedium,
                color = palette.textFaint,
            )
        }

        LazyColumn(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 16.dp, end = 16.dp, bottom = 32.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                SectionLabel(palette, "Appearance")
                JoeCard(Modifier.fillMaxWidth()) {
                    Column {
                        SettingRow(
                            palette = palette,
                            label = "Theme",
                            hint = when (draft.themeMode) {
                                1 -> "Always dark"
                                2 -> "Always light"
                                else -> "Follow the system"
                            },
                            trailing = {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    listOf(0 to "System", 1 to "Dark", 2 to "Light").forEach { (value, label) ->
                                        JoeChip(
                                            palette = palette,
                                            text = label,
                                            tone = if (draft.themeMode == value) ChipTone.Accent else ChipTone.Neutral,
                                            modifier = Modifier.clickable {
                                                draft = draft.copy(themeMode = value)
                                                // The theme is the one setting a person
                                                // expects to see change straight away.
                                                onSave(draft)
                                            },
                                        )
                                    }
                                }
                            },
                        )
                        RowDivider(palette)
                        SettingRow(
                            palette = palette,
                            label = "Interface language",
                            hint = "The language the app's own words are in",
                            trailing = {
                                Text(
                                    languageName(draft.uiLanguage),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = palette.text,
                                )
                            },
                        )
                    }
                }
            }

            item {
                SectionLabel(palette, "Browser")
                JoeCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        JoeField(
                            palette = palette,
                            value = draft.defaultStartUrl,
                            onValueChange = { draft = draft.copy(defaultStartUrl = it) },
                            label = "Start page",
                            placeholder = "https://ipfighter.com/browser-fingerprint",
                        )
                        Spacer(Modifier.height(12.dp))
                        JoeField(
                            palette = palette,
                            value = draft.defaultGroup,
                            onValueChange = { draft = draft.copy(defaultGroup = it) },
                            label = "Default group",
                            placeholder = "Ungrouped",
                        )
                    }
                }
            }

            item {
                SectionLabel(palette, "Behaviour")
                JoeCard(Modifier.fillMaxWidth()) {
                    Column {
                        SettingRow(
                            palette = palette,
                            label = "Load the built-in tools",
                            hint = "Paste as human and the proxy guard",
                            trailing = {
                                Switch(
                                    checked = draft.enableExtensions,
                                    onCheckedChange = { draft = draft.copy(enableExtensions = it) },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = palette.onAccent,
                                        checkedTrackColor = palette.accent,
                                        uncheckedThumbColor = palette.textFaint,
                                        uncheckedTrackColor = palette.panel2,
                                        uncheckedBorderColor = palette.line,
                                    ),
                                )
                            },
                        )
                        RowDivider(palette)
                        SettingRow(
                            palette = palette,
                            label = "Load the extension library",
                            hint = "The extensions added under Extensions",
                            trailing = {
                                Switch(
                                    checked = draft.enableCustomExtensions,
                                    onCheckedChange = { draft = draft.copy(enableCustomExtensions = it) },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = palette.onAccent,
                                        checkedTrackColor = palette.accent,
                                        uncheckedThumbColor = palette.textFaint,
                                        uncheckedTrackColor = palette.panel2,
                                        uncheckedBorderColor = palette.line,
                                    ),
                                )
                            },
                        )
                        RowDivider(palette)
                        SettingRow(
                            palette = palette,
                            label = "Keep the browsing history",
                            hint = "Record the pages each profile visits",
                            trailing = {
                                Switch(
                                    checked = draft.historyEnabled,
                                    onCheckedChange = { draft = draft.copy(historyEnabled = it) },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = palette.onAccent,
                                        checkedTrackColor = palette.accent,
                                        uncheckedThumbColor = palette.textFaint,
                                        uncheckedTrackColor = palette.panel2,
                                        uncheckedBorderColor = palette.line,
                                    ),
                                )
                            },
                        )
                        RowDivider(palette)
                        SettingRow(
                            palette = palette,
                            label = "Keep deleted profiles in the recycle bin",
                            hint = "A deleted profile can be brought back",
                            trailing = {
                                Switch(
                                    checked = draft.useTrash,
                                    onCheckedChange = { draft = draft.copy(useTrash = it) },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = palette.onAccent,
                                        checkedTrackColor = palette.accent,
                                        uncheckedThumbColor = palette.textFaint,
                                        uncheckedTrackColor = palette.panel2,
                                        uncheckedBorderColor = palette.line,
                                    ),
                                )
                            },
                        )
                        RowDivider(palette)
                        SettingRow(
                            palette = palette,
                            label = "Proxy notifications",
                            hint = "Tell me when the exit address changes or the proxy dies",
                            trailing = {
                                Switch(
                                    checked = draft.proxyNotifications,
                                    onCheckedChange = { draft = draft.copy(proxyNotifications = it) },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = palette.onAccent,
                                        checkedTrackColor = palette.accent,
                                        uncheckedThumbColor = palette.textFaint,
                                        uncheckedTrackColor = palette.panel2,
                                        uncheckedBorderColor = palette.line,
                                    ),
                                )
                            },
                        )
                    }
                }
            }

            item {
                SectionLabel(palette, "Storage")
                JoeCard(Modifier.fillMaxWidth()) {
                    Column {
                        KeyValue(palette, "Data folder", AppPaths.root(context).absolutePath)
                        RowDivider(palette)
                        SettingRow(
                            palette = palette,
                            label = "Clear the history",
                            hint = "Every page every profile visited",
                            onClick = { confirmClear = true },
                            trailing = {
                                Icon(Icons.Outlined.Delete, null, tint = palette.danger, modifier = Modifier.size(18.dp))
                            },
                        )
                    }
                }
            }

            item {
                SectionLabel(palette, "Developer")
                JoeCard(Modifier.fillMaxWidth()) {
                    Column {
                        SettingRow(
                            palette = palette,
                            label = "Web inspector",
                            hint = "Let the engine be inspected. A page can find an open inspector, so leave this off unless you are looking for something.",
                            trailing = {
                                Switch(
                                    checked = draft.developerMode,
                                    onCheckedChange = {
                                        draft = draft.copy(developerMode = it)
                                        onSave(draft)
                                    },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = palette.onAccent,
                                        checkedTrackColor = palette.accent,
                                        uncheckedThumbColor = palette.textFaint,
                                        uncheckedTrackColor = palette.panel2,
                                        uncheckedBorderColor = palette.line,
                                    ),
                                )
                            },
                        )
                    }
                }
            }

            item {
                SectionLabel(palette, "About")
                JoeCard(Modifier.fillMaxWidth()) {
                    Column {
                        KeyValue(palette, "Joe Browser", "1.16.0 · Android")
                        RowDivider(palette)
                        KeyValue(palette, "Application id", "com.joebrowser.app")
                        RowDivider(palette)
                        KeyValue(palette, "Engine", "Chromium WebView")
                        RowDivider(palette)
                        Column(Modifier.padding(16.dp)) {
                            Note(
                                palette = palette,
                                text = "Every profile is a separate identity with its own storage, its own cookies and its own " +
                                    "fingerprint. Nothing leaves this device unless you send it somewhere yourself.",
                            )
                        }
                    }
                }
            }

            item {
                Button(
                    onClick = {
                        onSave(draft)
                        onMessage("Settings saved.", false)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = palette.accent, contentColor = palette.onAccent
                    ),
                    shape = RoundedCornerShape(10.dp),
                ) { Text("Save changes") }
            }
        }
    }

    if (confirmClear) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmClear = false },
            containerColor = palette.card,
            title = { Text("Clear the history", color = palette.text) },
            text = { Text("Every page every profile visited will be forgotten.", color = palette.textDim) },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    confirmClear = false
                    onClearHistory()
                }) { Text("Clear", color = palette.danger) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { confirmClear = false }) {
                    Text("Cancel", color = palette.textDim)
                }
            },
        )
    }
}

private fun languageName(key: String): String = when (key) {
    "de" -> "Deutsch"
    "es" -> "Español"
    "fr" -> "Français"
    "it" -> "Italiano"
    "pt" -> "Português"
    "ru" -> "Русский"
    "tr" -> "Türkçe"
    "zh" -> "简体中文"
    else -> "English"
}
