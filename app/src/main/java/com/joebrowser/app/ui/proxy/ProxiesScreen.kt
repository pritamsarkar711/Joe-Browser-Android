package com.joebrowser.app.ui.proxy

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
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.joebrowser.app.data.model.ProxyRecord
import com.joebrowser.app.data.store.toRecord
import com.joebrowser.app.proxy.ProxyParser
import com.joebrowser.app.ui.AppState
import com.joebrowser.app.ui.components.ChipTone
import com.joebrowser.app.ui.components.EmptyState
import com.joebrowser.app.ui.components.JoeCard
import com.joebrowser.app.ui.components.JoeChip
import com.joebrowser.app.ui.components.JoeField
import com.joebrowser.app.ui.components.Note
import com.joebrowser.app.ui.components.RowDivider
import com.joebrowser.app.ui.components.SectionLabel
import com.joebrowser.app.ui.theme.JoePalette

/**
 * The proxy library.
 *
 * Adding a proxy has to be easy, because the people who use proxies paste them in bulk
 * and in every shape there is. The dialog therefore accepts a whole block of text and
 * reads each line on its own, and the fields it fills are the ones the user can correct
 * afterwards.
 */
@Composable
fun ProxiesScreen(
    state: AppState,
    palette: JoePalette,
    onSave: (ProxyRecord) -> Unit,
    onDelete: (String) -> Unit,
    onCheck: (ProxyRecord) -> Unit,
    onCheckAll: () -> Unit,
) {
    var editing by remember { mutableStateOf<ProxyRecord?>(null) }
    var pasting by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<ProxyRecord?>(null) }

    Column(
        Modifier
            .fillMaxSize()
            .background(palette.bg)
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text("Proxies", style = MaterialTheme.typography.headlineMedium, color = palette.text)
            Text(
                "${state.proxies.size} saved",
                style = MaterialTheme.typography.bodyMedium,
                color = palette.textFaint,
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        editing = ProxyRecord(type = "http", name = "", host = "", port = 0)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = palette.accent, contentColor = palette.onAccent
                    ),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Icon(Icons.Outlined.Add, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Add proxy")
                }
                OutlinedButton(
                    onClick = { pasting = true },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = palette.text),
                ) {
                    Icon(Icons.Outlined.ContentPaste, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Paste list")
                }
                OutlinedButton(
                    onClick = onCheckAll,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = palette.text),
                ) {
                    Icon(Icons.Outlined.Bolt, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Check all")
                }
            }
        }

        if (state.proxies.isEmpty()) {
            EmptyState(
                palette = palette,
                icon = Icons.Outlined.Public,
                title = "No proxies saved yet",
                message = "Add one to use it in profiles. A whole list can be pasted at once.",
            )
        } else {
            LazyColumn(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 16.dp, end = 16.dp, bottom = 32.dp
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(state.proxies, key = { it.id }) { record ->
                    ProxyRow(
                        record = record,
                        palette = palette,
                        checking = state.checkingProxyId == record.id,
                        lastCheck = state.lastCheck[record.id],
                        onCheck = { onCheck(record) },
                        onEdit = { editing = record },
                        onDelete = { confirmDelete = record },
                    )
                }
            }
        }
    }

    editing?.let { record ->
        ProxyDialog(
            palette = palette,
            record = record,
            onDismiss = { editing = null },
            onSave = {
                onSave(it)
                editing = null
            },
        )
    }

    if (pasting) {
        PasteDialog(
            palette = palette,
            onDismiss = { pasting = false },
            onSave = { list ->
                list.forEach { onSave(it) }
                pasting = false
            },
        )
    }

    confirmDelete?.let { record ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            containerColor = palette.card,
            title = { Text("Delete proxy", color = palette.text) },
            text = { Text("Remove \"${record.display}\" from the library?", color = palette.textDim) },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(record.id)
                    confirmDelete = null
                }) { Text("Delete", color = palette.danger) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = null }) { Text("Cancel", color = palette.textDim) }
            },
        )
    }
}

@Composable
private fun ProxyRow(
    record: ProxyRecord,
    palette: JoePalette,
    checking: Boolean,
    lastCheck: com.joebrowser.app.proxy.ProxyCheck?,
    onCheck: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
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
                Icon(Icons.Outlined.Public, null, tint = palette.accent, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    record.display,
                    style = MaterialTheme.typography.titleMedium,
                    color = palette.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = buildString {
                        append(record.type.uppercase())
                        append(" · ")
                        append(record.host)
                        append(":")
                        append(record.port)
                        if (record.lastIp.isNotBlank()) {
                            append(" · ")
                            append(record.lastIp)
                        }
                        if (record.lastCity.isNotBlank()) {
                            append(" · ")
                            append(record.lastCity)
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.textFaint,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (record.rotationCount > 0) {
                    Spacer(Modifier.height(4.dp))
                    JoeChip(
                        palette = palette,
                        text = "${record.rotationCount} rotation${if (record.rotationCount == 1) "" else "s"}",
                        tone = ChipTone.Warn,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            when {
                checking -> CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = palette.accent,
                )
                lastCheck != null -> JoeChip(
                    palette = palette,
                    text = if (lastCheck.ok) "${lastCheck.latencyMs} ms" else "Offline",
                    tone = if (lastCheck.ok) ChipTone.Success else ChipTone.Danger,
                )
                else -> JoeChip(
                    palette = palette,
                    text = when (record.lastStatus) {
                        "online" -> "Online"
                        "offline" -> "Offline"
                        else -> "Unchecked"
                    },
                    tone = when (record.lastStatus) {
                        "online" -> ChipTone.Success
                        "offline" -> ChipTone.Danger
                        else -> ChipTone.Neutral
                    },
                )
            }
            IconButton(onClick = onCheck) {
                Icon(Icons.Outlined.Bolt, "Check this proxy", tint = palette.textDim, modifier = Modifier.size(18.dp))
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Outlined.Edit, "Edit", tint = palette.textDim, modifier = Modifier.size(18.dp))
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Outlined.Delete, "Delete", tint = palette.textDim, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun ProxyDialog(
    palette: JoePalette,
    record: ProxyRecord,
    onDismiss: () -> Unit,
    onSave: (ProxyRecord) -> Unit,
) {
    var draft by remember(record.id) { mutableStateOf(record) }
    var address by remember(record.id) {
        mutableStateOf(if (record.host.isBlank()) "" else "${record.host}:${record.port}")
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.card,
        title = { Text(if (record.id.isBlank()) "Add proxy" else "Edit proxy", color = palette.text) },
        text = {
            Column {
                JoeField(
                    palette = palette,
                    value = draft.name,
                    onValueChange = { draft = draft.copy(name = it) },
                    label = "Name",
                    placeholder = "Home IP · Frankfurt",
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("http", "https", "socks5").forEach { type ->
                        JoeChip(
                            palette = palette,
                            text = type.uppercase(),
                            tone = if (draft.type == type) ChipTone.Accent else ChipTone.Neutral,
                            modifier = Modifier.clickable { draft = draft.copy(type = type) },
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                JoeField(
                    palette = palette,
                    value = address,
                    onValueChange = { text ->
                        address = text
                        val parsed = ProxyParser.parseLine(text)
                        if (parsed.enabled) {
                            draft = draft.copy(
                                host = parsed.host, port = parsed.port,
                                user = if (parsed.user.isNotEmpty()) parsed.user else draft.user,
                                password = if (parsed.password.isNotEmpty()) parsed.password else draft.password,
                            )
                        }
                    },
                    label = "Address",
                    placeholder = "proxy.example.com:8080",
                )
                Spacer(Modifier.height(10.dp))
                JoeField(
                    palette = palette,
                    value = draft.user,
                    onValueChange = { draft = draft.copy(user = it) },
                    label = "Username",
                    placeholder = "Optional",
                )
                Spacer(Modifier.height(10.dp))
                JoeField(
                    palette = palette,
                    value = draft.password,
                    onValueChange = { draft = draft.copy(password = it) },
                    label = "Password",
                    placeholder = "Optional",
                )
                Spacer(Modifier.height(12.dp))
                Note(palette = palette, text = "Passwords are encrypted on this device and never leave it.")
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (draft.host.isBlank() || draft.port <= 0) return@TextButton
                onSave(draft)
            }) { Text("Save", color = palette.accent) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = palette.textDim) }
        },
    )
}

@Composable
private fun PasteDialog(
    palette: JoePalette,
    onDismiss: () -> Unit,
    onSave: (List<ProxyRecord>) -> Unit,
) {
    var text by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.card,
        title = { Text("Paste proxies", color = palette.text) },
        text = {
            Column {
                Text(
                    "One per line. A full link is read as well: socks5://user:pass@host:1080",
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.textFaint,
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(170.dp),
                    placeholder = {
                        Text(
                            "1.2.3.4:8080\nsocks5://user:pass@5.6.7.8:1080",
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
        },
        confirmButton = {
            TextButton(onClick = {
                val parsed = ProxyParser.parseMany(text)
                if (parsed.isEmpty()) return@TextButton
                onSave(parsed.map { it.toRecord(name = it.host) })
            }) { Text("Add", color = palette.accent) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = palette.textDim) }
        },
    )
}
