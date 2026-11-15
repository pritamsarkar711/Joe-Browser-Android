package com.joebrowser.app.ui.history

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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.joebrowser.app.data.model.HistoryEntry
import com.joebrowser.app.ui.components.EmptyState
import com.joebrowser.app.ui.components.JoeCard
import com.joebrowser.app.ui.components.JoeChip
import com.joebrowser.app.ui.theme.JoePalette
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The pages the profiles visited.
 */
@Composable
fun HistoryScreen(
    palette: JoePalette,
    entries: List<HistoryEntry>,
    onBack: () -> Unit,
    onClear: () -> Unit,
) {
    val context = LocalContext.current
    val formatter = remember { SimpleDateFormat("d MMM, HH:mm", Locale.getDefault()) }

    Column(
        Modifier
            .fillMaxSize()
            .background(palette.bg)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Outlined.ArrowBack, contentDescription = "Back", tint = palette.textDim)
            }
            Column(Modifier.weight(1f)) {
                Text("History", style = MaterialTheme.typography.titleLarge, color = palette.text)
                Text(
                    "${entries.size} pages",
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.textFaint,
                )
            }
            IconButton(onClick = onClear) {
                Icon(Icons.Outlined.Delete, contentDescription = "Clear", tint = palette.textDim)
            }
        }

        if (entries.isEmpty()) {
            EmptyState(
                palette = palette,
                icon = Icons.Outlined.History,
                title = "Nothing recorded yet",
                message = "The pages a profile visits are listed here so a link that was passed through can be found again.",
            )
        } else {
            LazyColumn(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(entries.sortedByDescending { it.visitedAt }, key = { it.id }) { entry ->
                    JoeCard(Modifier.fillMaxWidth()) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    runCatching {
                                        context.startActivity(
                                            android.content.Intent(
                                                android.content.Intent.ACTION_VIEW,
                                                android.net.Uri.parse(entry.url),
                                            )
                                        )
                                    }
                                }
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    entry.title.ifBlank { entry.url },
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = palette.text,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    entry.url,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = palette.textFaint,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            JoeChip(palette = palette, text = formatter.format(Date(entry.visitedAt)))
                            IconButton(onClick = {
                                val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                                    as? android.content.ClipboardManager
                                clipboard?.setPrimaryClip(
                                    android.content.ClipData.newPlainText("url", entry.url)
                                )
                            }) {
                                Icon(
                                    Icons.Outlined.ContentCopy, "Copy link",
                                    tint = palette.textFaint, modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
