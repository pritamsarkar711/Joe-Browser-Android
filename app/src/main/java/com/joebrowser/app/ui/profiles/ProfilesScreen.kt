package com.joebrowser.app.ui.profiles

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
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Sort
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.joebrowser.app.data.model.Profile
import com.joebrowser.app.fingerprint.FingerprintIdentity
import com.joebrowser.app.ui.AppState
import com.joebrowser.app.ui.components.ChipTone
import com.joebrowser.app.ui.components.EmptyState
import com.joebrowser.app.ui.components.JoeCard
import com.joebrowser.app.ui.components.JoeChip
import com.joebrowser.app.ui.components.StatusDot
import com.joebrowser.app.ui.theme.JoePalette

/**
 * The profile library.
 *
 * A profile card carries the things that decide whether it is the profile you meant to
 * open: the browser it names, the operating system it claims, the screen it reports, the
 * route it takes and the fingerprint it prints. All of it on one card, because a list of
 * twelve identities that differ only in a name is a list you cannot work with.
 */
@Composable
fun ProfilesScreen(
    state: AppState,
    palette: JoePalette,
    onOpen: (String) -> Unit,
    onNew: () -> Unit,
    onHistory: () -> Unit,
    onSearch: (String) -> Unit,
    onSort: (Int) -> Unit,
    onDelete: (Profile) -> Unit,
    onDuplicate: (Profile) -> Unit,
    onLaunch: (Profile) -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Header(
                palette = palette,
                count = state.profiles.size,
                shown = state.visibleProfiles.size,
                search = state.search,
                sortBy = state.sortBy,
                onSearch = onSearch,
                onSort = onSort,
                onHistory = onHistory,
            )

            if (state.visibleProfiles.isEmpty()) {
                if (state.profiles.isEmpty()) {
                    EmptyState(
                        palette = palette,
                        icon = Icons.Outlined.Layers,
                        title = "No profiles yet",
                        message = "Create your first identity to give it a fingerprint, a proxy and its own storage.",
                        action = {
                            ExtendedFloatingActionButton(
                                onClick = onNew,
                                containerColor = palette.accent,
                                contentColor = palette.onAccent,
                                icon = { Icon(Icons.Outlined.Add, null) },
                                text = { Text("Create profile") },
                            )
                        },
                    )
                } else {
                    EmptyState(
                        palette = palette,
                        icon = Icons.Outlined.Search,
                        title = "No matching profiles",
                        message = "Try another name, group, tag or proxy address.",
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(state.visibleProfiles, key = { it.id }) { profile ->
                        ProfileCard(
                            profile = profile,
                            palette = palette,
                            onOpen = { onOpen(profile.id) },
                            onLaunch = { onLaunch(profile) },
                            onEdit = { onOpen(profile.id) },
                            onDuplicate = { onDuplicate(profile) },
                            onDelete = { onDelete(profile) },
                        )
                    }
                }
            }
        }

        // The empty state already carries the same action, and two buttons that do one
        // thing are one button too many.
        if (state.profiles.isNotEmpty()) {
            ExtendedFloatingActionButton(
                onClick = onNew,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(20.dp),
                containerColor = palette.accent,
                contentColor = palette.onAccent,
                icon = { Icon(Icons.Outlined.Add, null) },
                text = { Text("New profile") },
            )
        }
    }
}

@Composable
private fun Header(
    palette: JoePalette,
    count: Int,
    shown: Int,
    search: String,
    sortBy: Int,
    onSearch: (String) -> Unit,
    onSort: (Int) -> Unit,
    onHistory: () -> Unit,
) {
    val sortLabels = listOf("recent", "name", "created", "used", "system", "group")
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    "Profiles",
                    style = MaterialTheme.typography.headlineMedium,
                    color = palette.text,
                )
                Text(
                    text = if (shown == count) "$count profiles" else "$shown of $count profiles",
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.textFaint,
                )
            }
            IconButton(onClick = onHistory) {
                Icon(Icons.Outlined.History, contentDescription = "History", tint = palette.textDim)
            }
            IconButton(onClick = { onSort((sortBy + 1) % sortLabels.size) }) {
                Icon(Icons.Outlined.Sort, contentDescription = "Sort", tint = palette.textDim)
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = search,
                onValueChange = onSearch,
                modifier = Modifier.weight(1f),
                singleLine = true,
                placeholder = {
                    Text(
                        "Search profiles",
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.textFaint,
                    )
                },
                leadingIcon = {
                    Icon(Icons.Outlined.Search, null, tint = palette.textFaint, modifier = Modifier.size(18.dp))
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
            Spacer(Modifier.width(8.dp))
            JoeChip(
                palette = palette,
                text = "Sort: ${sortLabels[sortBy.coerceIn(0, sortLabels.size - 1)]}",
                tone = ChipTone.Neutral,
            )
        }
    }
}

@Composable
private fun ProfileCard(
    profile: Profile,
    palette: JoePalette,
    onOpen: () -> Unit,
    onLaunch: () -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val hash = remember(profile.fp) { FingerprintIdentity.overallHash(profile.fp) }

    JoeCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(palette.accentOnBg),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        profile.initials,
                        style = MaterialTheme.typography.titleMedium,
                        color = palette.accent,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        profile.name.ifBlank { "Untitled profile" },
                        style = MaterialTheme.typography.titleLarge,
                        color = palette.text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = listOf(
                            com.joebrowser.app.fingerprint.UserAgentBuilder.prettyEngine(profile.browserKind),
                            profile.groupOrUngrouped,
                            if (profile.freshSession) "fresh session" else null,
                        ).filterNotNull().joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.textFaint,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Box {
                    IconButton(onClick = { menu = true }) {
                        Icon(Icons.Outlined.MoreVert, contentDescription = "More", tint = palette.textFaint)
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text("Open profile") },
                            leadingIcon = { Icon(Icons.Outlined.PlayArrow, null) },
                            onClick = { menu = false; onLaunch() },
                        )
                        DropdownMenuItem(
                            text = { Text("Edit profile") },
                            leadingIcon = { Icon(Icons.Outlined.Edit, null) },
                            onClick = { menu = false; onEdit() },
                        )
                        DropdownMenuItem(
                            text = { Text("Duplicate") },
                            leadingIcon = { Icon(Icons.Outlined.ContentCopy, null) },
                            onClick = { menu = false; onDuplicate() },
                        )
                        DropdownMenuItem(
                            text = { Text("Delete profile", color = palette.danger) },
                            leadingIcon = { Icon(Icons.Outlined.Delete, null, tint = palette.danger) },
                            onClick = { menu = false; onDelete() },
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // The chips that tell two profiles apart.
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                JoeChip(
                    palette = palette,
                    text = "${profile.fp.os} · ${profile.fp.screenW}×${profile.fp.screenH}",
                    tone = ChipTone.Accent,
                )
                if (profile.proxy.enabled) {
                    JoeChip(
                        palette = palette,
                        text = "PROXY ${profile.proxy.lastCountryCode.ifBlank { "" }}".trim(),
                        icon = Icons.Outlined.Public,
                        tone = ChipTone.Warn,
                    )
                } else {
                    JoeChip(palette = palette, text = "Direct", tone = ChipTone.Neutral)
                }
            }

            Spacer(Modifier.height(8.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                JoeChip(palette = palette, text = profile.fp.timezoneShort.ifBlank { profile.fp.timezone })
                JoeChip(palette = palette, text = profile.fp.language)
                Spacer(Modifier.weight(1f))
                JoeChip(palette = palette, text = hash)
            }

            if (profile.tags.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    profile.tags.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                        .take(4).forEach { tag ->
                            JoeChip(palette = palette, text = "#$tag")
                        }
                }
            }

            Spacer(Modifier.height(14.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusDot(
                    palette = palette,
                    tone = if (profile.lastOpened > 0) ChipTone.Success else ChipTone.Neutral,
                    size = 7,
                )
                Spacer(Modifier.width(7.dp))
                Text(
                    text = when {
                        profile.lastOpened == 0L -> "Never opened"
                        System.currentTimeMillis() - profile.lastOpened < 90_000L -> "Opened just now"
                        else -> "Opened ${profile.openCount} times"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.textFaint,
                    modifier = Modifier.weight(1f),
                )
                Box(
                    Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(palette.accent)
                        .clickable { onLaunch() }
                        .padding(horizontal = 18.dp, vertical = 9.dp)
                ) {
                    Text(
                        "Open profile",
                        style = MaterialTheme.typography.labelLarge,
                        color = palette.onAccent,
                    )
                }
            }
        }
    }
}
