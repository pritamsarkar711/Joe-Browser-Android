package com.joebrowser.app.ui.profiles

import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Sort
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
 * A list of identities has to be scannable. Everything that tells two profiles apart is on
 * one line, in the order a person reads it: what it is called, what it claims to be, where
 * it goes and what it prints. The rest is one tap away.
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
    var groupFilter by remember { mutableStateOf("") }

    val groups = remember(state.profiles) {
        state.profiles.map { it.groupOrUngrouped }.distinct().sorted()
    }
    val visible = remember(state.visibleProfiles, groupFilter) {
        if (groupFilter.isEmpty()) state.visibleProfiles
        else state.visibleProfiles.filter { it.groupOrUngrouped == groupFilter }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {

            // ---- the bar ------------------------------------------------------------
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "Profiles",
                        style = MaterialTheme.typography.headlineMedium,
                        color = palette.text,
                    )
                    Text(
                        text = when {
                            state.profiles.isEmpty() -> "No profiles yet"
                            visible.size == state.profiles.size -> "${state.profiles.size} profiles"
                            else -> "${visible.size} of ${state.profiles.size}"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.textFaint,
                    )
                }
                IconButton(onClick = onHistory) {
                    Icon(Icons.Outlined.Search, contentDescription = "History", tint = palette.textDim)
                }
                IconButton(onClick = { onSort((state.sortBy + 1) % 6) }) {
                    Icon(Icons.Outlined.Sort, contentDescription = "Sort", tint = palette.textDim)
                }
            }

            // ---- the groups ----------------------------------------------------------
            if (groups.size > 1) {
                LazyRow(
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(bottom = 10.dp),
                ) {
                    item {
                        JoeChip(
                            palette = palette,
                            text = "All",
                            tone = if (groupFilter.isEmpty()) ChipTone.Accent else ChipTone.Neutral,
                            modifier = Modifier.clickable { groupFilter = "" },
                        )
                    }
                    items(groups) { group ->
                        JoeChip(
                            palette = palette,
                            text = group,
                            tone = if (groupFilter == group) ChipTone.Accent else ChipTone.Neutral,
                            modifier = Modifier.clickable {
                                groupFilter = if (groupFilter == group) "" else group
                            },
                        )
                    }
                }
            }

            if (state.profiles.isEmpty()) {
                EmptyState(
                    palette = palette,
                    icon = Icons.Outlined.PlayArrow,
                    title = "No profiles yet",
                    message = "Create one and it gets its own fingerprint, its own storage and its own route.",
                    action = {
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(palette.accent)
                                .clickable { onNew() }
                                .padding(horizontal = 20.dp, vertical = 11.dp),
                        ) {
                            Text(
                                "Create profile",
                                style = MaterialTheme.typography.labelLarge,
                                color = palette.onAccent,
                            )
                        }
                    },
                )
            } else if (visible.isEmpty()) {
                EmptyState(
                    palette = palette,
                    icon = Icons.Outlined.Search,
                    title = "Nothing here",
                    message = "No profile matches this search or group.",
                )
            } else {
                LazyColumn(
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = 16.dp, end = 16.dp, bottom = 96.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(visible, key = { it.id }) { profile ->
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

        FloatingActionButton(
            onClick = onNew,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp),
            containerColor = palette.accent,
            contentColor = palette.onAccent,
        ) {
            Icon(Icons.Filled.Add, contentDescription = "New profile")
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

    JoeCard(
        Modifier
            .fillMaxWidth()
            .clickable { onEdit() }
    ) {
        Column(Modifier.padding(14.dp)) {

            // ---- the name -----------------------------------------------------------
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(palette.accentOnBg),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        profile.initials,
                        style = MaterialTheme.typography.titleMedium,
                        color = palette.accent,
                    )
                }
                Spacer(Modifier.width(11.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        profile.name.ifBlank { "Untitled" },
                        style = MaterialTheme.typography.titleLarge,
                        color = palette.text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(1.dp))
                    Text(
                        text = listOf(
                            com.joebrowser.app.fingerprint.UserAgentBuilder.prettyEngine(profile.browserKind),
                            profile.fp.os,
                        ).joinToString(" · "),
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
                            text = { Text("Open") },
                            leadingIcon = { Icon(Icons.Outlined.PlayArrow, null) },
                            onClick = { menu = false; onLaunch() },
                        )
                        DropdownMenuItem(
                            text = { Text("Edit") },
                            leadingIcon = { Icon(Icons.Outlined.Edit, null) },
                            onClick = { menu = false; onEdit() },
                        )
                        DropdownMenuItem(
                            text = { Text("Duplicate") },
                            leadingIcon = { Icon(Icons.Outlined.ContentCopy, null) },
                            onClick = { menu = false; onDuplicate() },
                        )
                        DropdownMenuItem(
                            text = { Text("Delete", color = palette.danger) },
                            leadingIcon = { Icon(Icons.Outlined.Delete, null, tint = palette.danger) },
                            onClick = { menu = false; onDelete() },
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // ---- what tells them apart ----------------------------------------------
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                JoeChip(
                    palette = palette,
                    text = "${profile.fp.screenW}×${profile.fp.screenH}",
                )
                JoeChip(
                    palette = palette,
                    text = if (profile.proxy.enabled) {
                        profile.proxy.lastCountryCode.ifBlank { "Proxy" }
                    } else {
                        "Direct"
                    },
                    tone = if (profile.proxy.enabled) ChipTone.Warn else ChipTone.Neutral,
                )
                JoeChip(palette = palette, text = profile.fp.language)
                Spacer(Modifier.weight(1f))
                StatusDot(
                    palette = palette,
                    tone = if (profile.lastOpened > 0) ChipTone.Success else ChipTone.Neutral,
                    size = 6,
                )
                Text(hash.take(6), style = MaterialTheme.typography.labelMedium, color = palette.textFaint)
            }

            if (profile.tags.isNotBlank() || profile.groupOrUngrouped != "Ungrouped") {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (profile.groupOrUngrouped != "Ungrouped") {
                        JoeChip(palette = palette, text = profile.groupOrUngrouped, tone = ChipTone.Accent)
                    }
                    profile.tags.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                        .take(3).forEach { tag ->
                            JoeChip(palette = palette, text = "#$tag")
                        }
                }
            }

            Spacer(Modifier.height(12.dp))

            // ---- the action ---------------------------------------------------------
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = when {
                        profile.lastOpened == 0L -> "Never opened"
                        profile.openCount == 1 -> "Opened once"
                        else -> "Opened ${profile.openCount} times"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.textFaint,
                    modifier = Modifier.weight(1f),
                )
                Box(
                    Modifier
                        .clip(RoundedCornerShape(9.dp))
                        .background(palette.accent)
                        .clickable { onLaunch() }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Text(
                        "Open",
                        style = MaterialTheme.typography.labelLarge,
                        color = palette.onAccent,
                    )
                }
            }
        }
    }
}
