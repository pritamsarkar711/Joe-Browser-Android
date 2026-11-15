package com.joebrowser.app.ui.extensions

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Shop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.joebrowser.app.data.model.ExtensionRecord
import com.joebrowser.app.extensions.ExtensionManager
import com.joebrowser.app.ui.AppState
import com.joebrowser.app.ui.components.ChipTone
import com.joebrowser.app.ui.components.EmptyState
import com.joebrowser.app.ui.components.JoeCard
import com.joebrowser.app.ui.components.JoeChip
import com.joebrowser.app.ui.components.Note
import com.joebrowser.app.ui.components.RowDivider
import com.joebrowser.app.ui.components.SectionLabel
import com.joebrowser.app.ui.components.SettingRow
import com.joebrowser.app.ui.theme.JoePalette
import kotlinx.coroutines.launch

/**
 * The extension library.
 *
 * The store is opened inside the app, so the user can look at a listing, read what an
 * extension does and install it without ever leaving. The package is fetched the way the
 * store itself hands it out, unpacked into the app's own folder and then loaded into the
 * profiles that ask for it.
 */
@Composable
fun ExtensionsScreen(
    state: AppState,
    palette: JoePalette,
    onSave: (ExtensionRecord) -> Unit,
    onDelete: (String) -> Unit,
    onMessage: (String, Boolean) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var search by remember { mutableStateOf("") }
    var installing by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0) }
    var confirmDelete by remember { mutableStateOf<ExtensionRecord?>(null) }
    var addingLink by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .background(palette.bg)
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text("Extensions", style = MaterialTheme.typography.headlineMedium, color = palette.text)
            Text(
                "${state.extensions.size} installed",
                style = MaterialTheme.typography.bodyMedium,
                color = palette.textFaint,
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(ExtensionManager.storeSearchUrl(search)))
                        runCatching { context.startActivity(intent) }
                            .onFailure { onMessage("No app can open the store.", true) }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = palette.accent, contentColor = palette.onAccent
                    ),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Icon(Icons.Outlined.Shop, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Store")
                }
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    placeholder = {
                        Text(
                            "Search extensions",
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.textFaint,
                        )
                    },
                    shape = RoundedCornerShape(10.dp),
                    textStyle = MaterialTheme.typography.bodyMedium,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = palette.input,
                        unfocusedContainerColor = palette.input,
                        focusedBorderColor = palette.accent,
                        unfocusedBorderColor = palette.line,
                        cursorColor = palette.accent,
                        focusedTextColor = palette.text,
                        unfocusedTextColor = palette.text,
                    ),
                )
            }
        }

        if (installing) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = palette.accent,
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    "Downloading · $progress%",
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.textDim,
                )
            }
        }

        if (state.extensions.isEmpty()) {
            EmptyState(
                palette = palette,
                icon = Icons.Outlined.Extension,
                title = "No extensions added yet",
                message = "Open the Chrome Web Store, pick an extension and install it here.",
            )
        } else {
            LazyColumn(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 16.dp, end = 16.dp, bottom = 32.dp
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(state.extensions, key = { it.id }) { record ->
                    ExtensionRow(
                        record = record,
                        palette = palette,
                        onToggle = { onSave(record.copy(enabled = it)) },
                        onDelete = { confirmDelete = record },
                        onOpenStore = {
                            val url = record.url.ifBlank { ExtensionManager.storeListingUrl(record.storeId) }
                            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                        },
                    )
                }
            }
        }
    }

    if (addingLink) {
        var link by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { addingLink = false },
            containerColor = palette.card,
            title = { Text("Add an extension", color = palette.text) },
            text = {
                Column {
                    Text(
                        "Paste the link from the Chrome Web Store.",
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.textFaint,
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = link,
                        onValueChange = { link = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = {
                            Text(
                                "https://chromewebstore.google.com/detail/…",
                                style = MaterialTheme.typography.bodySmall,
                                color = palette.textFaint,
                            )
                        },
                        shape = RoundedCornerShape(10.dp),
                        textStyle = MaterialTheme.typography.bodySmall,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = palette.input,
                            unfocusedContainerColor = palette.input,
                            focusedBorderColor = palette.accent,
                            unfocusedBorderColor = palette.line,
                            cursorColor = palette.accent,
                            focusedTextColor = palette.text,
                            unfocusedTextColor = palette.text,
                        ),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val id = ExtensionManager.storeIdFromUrl(link)
                    if (id.isBlank()) {
                        onMessage("That link does not carry an extension id.", true)
                        return@TextButton
                    }
                    addingLink = false
                    installing = true
                    progress = 0
                    scope.launch {
                        val result = ExtensionManager.installFromStore(
                            context, id,
                        ) { percent, _ -> progress = percent }
                        installing = false
                        result.onSuccess {
                            onSave(it)
                            onMessage("${it.name} was installed.", false)
                        }.onFailure {
                            onMessage(it.message ?: "The extension could not be installed.", true)
                        }
                    }
                }) { Text("Install", color = palette.accent) }
            },
            dismissButton = {
                TextButton(onClick = { addingLink = false }) { Text("Cancel", color = palette.textDim) }
            },
        )
    }

    confirmDelete?.let { record ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            containerColor = palette.card,
            title = { Text("Remove extension", color = palette.text) },
            text = { Text("Remove \"${record.name}\" from Joe Browser?", color = palette.textDim) },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(record.id)
                    confirmDelete = null
                }) { Text("Remove", color = palette.danger) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = null }) { Text("Cancel", color = palette.textDim) }
            },
        )
    }
}

@Composable
private fun ExtensionRow(
    record: ExtensionRecord,
    palette: JoePalette,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit,
    onOpenStore: () -> Unit,
) {
    JoeCard(Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(palette.accentOnBg),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.Extension, null, tint = palette.accent, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    record.name.ifBlank { "Extension" },
                    style = MaterialTheme.typography.titleMedium,
                    color = palette.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = buildString {
                        append(if (record.source == "store") "Chrome Web Store" else "Imported")
                        if (record.version.isNotBlank()) {
                            append("  ·  v")
                            append(record.version)
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.textFaint,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onOpenStore) {
                Icon(
                    Icons.Outlined.OpenInNew, "Store listing",
                    tint = palette.textDim, modifier = Modifier.size(18.dp),
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Outlined.Delete, "Remove",
                    tint = palette.textDim, modifier = Modifier.size(18.dp),
                )
            }
            Switch(
                checked = record.enabled,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = palette.onAccent,
                    checkedTrackColor = palette.accent,
                    uncheckedThumbColor = palette.textFaint,
                    uncheckedTrackColor = palette.panel2,
                    uncheckedBorderColor = palette.line,
                ),
            )
        }
    }
}
