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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.joebrowser.app.data.model.FingerprintConfig
import com.joebrowser.app.data.model.Profile
import com.joebrowser.app.data.model.ProxyConfig
import com.joebrowser.app.data.store.toRecord
import com.joebrowser.app.fingerprint.BrowserVersions
import com.joebrowser.app.fingerprint.FingerprintCatalog
import com.joebrowser.app.fingerprint.FingerprintFactory
import com.joebrowser.app.fingerprint.FingerprintIdentity
import com.joebrowser.app.fingerprint.UserAgentBuilder
import com.joebrowser.app.proxy.ProxyCheck
import com.joebrowser.app.proxy.ProxyParser
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
 * The profile editor.
 *
 * The tabs follow the order in which a profile is actually built: what it is, what it
 * looks like, how it connects and what it can do. The fingerprint tab is the one that
 * matters most, so it shows the numbers the page will actually see and offers to repair
 * anything that contradicts itself.
 */
@Composable
fun ProfileEditorScreen(
    profileId: String,
    state: AppState,
    palette: JoePalette,
    onBack: () -> Unit,
    onSave: (Profile) -> Unit,
    onDelete: (Profile) -> Unit,
    onLaunch: (Profile) -> Unit,
    onCheckProxy: (com.joebrowser.app.data.model.ProxyRecord) -> Unit,
    onMessage: (String, Boolean) -> Unit,
) {
    val existing = state.profiles.firstOrNull { it.id == profileId }
    val context = androidx.compose.ui.platform.LocalContext.current
    var draft by remember(profileId) {
        mutableStateOf(
            existing?.copy(fp = existing.fp.copy())
                ?: Profile(
                    // A profile that has never been saved still needs its identity, so the
                    // name is given one the moment the editor opens.
                    id = java.util.UUID.randomUUID().toString().replace("-", ""),
                    name = "",
                    browserKind = UserAgentBuilder.ENGINE_CHROME,
                    // The build it reports is the build that is really running. A page can
                    // find out which Chromium it is talking to, and a version the engine
                    // does not match is the one thing a fingerprint scan looks for.
                    browserVersion = com.joebrowser.app.browser.BrowserEngines.webViewVersion(context)
                        .ifBlank { BrowserVersions.LATEST_STABLE },
                    fp = FingerprintFactory.regenerate(FingerprintConfig(), "Android", null),
                )
        )
    }
    var tab by remember { mutableIntStateOf(0) }
    var confirmDelete by remember { mutableStateOf(false) }

    val tabs = listOf("General", "Fingerprint", "Proxy", "Tools")

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
                Text(
                    text = if (existing == null) "New profile" else draft.name.ifBlank { "Untitled profile" },
                    style = MaterialTheme.typography.titleLarge,
                    color = palette.text,
                )
                Text(
                    text = if (existing == null) "A new identity is generated for you" else "Changes apply to the next launch",
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.textFaint,
                )
            }
            Button(
                onClick = {
                    if (draft.name.isBlank()) {
                        onMessage("Give the profile a name first.", true)
                        return@Button
                    }
                    val saved = draft.copy(
                        fp = draft.fp.copy(browserVersion = draft.browserVersion),
                    )
                    onSave(saved)
                    onBack()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = palette.accent,
                    contentColor = palette.onAccent,
                ),
                shape = RoundedCornerShape(10.dp),
            ) { Text("Save") }
        }

        TabRow(
            selectedTabIndex = tab,
            containerColor = palette.bg,
            contentColor = palette.accent,
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = tab == index,
                    onClick = { tab = index },
                    text = {
                        Text(
                            title,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (tab == index) palette.accent else palette.textFaint,
                        )
                    },
                )
            }
        }

        LazyColumn(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (tab) {
                0 -> generalTab(draft, { draft = it }, palette, state, onDelete, existing)
                1 -> fingerprintTab(draft, { draft = it }, palette, onMessage)
                2 -> proxyTab(draft, { draft = it }, palette, state, onCheckProxy, onMessage)
                3 -> toolsTab(draft, { draft = it }, palette)
            }
            item {
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        val saved = draft.copy(fp = draft.fp.copy(browserVersion = draft.browserVersion))
                        onSave(saved)
                        onLaunch(saved)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = palette.accent,
                        contentColor = palette.onAccent,
                    ),
                    shape = RoundedCornerShape(10.dp),
                ) { Text("Save and open") }
                if (existing != null) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { confirmDelete = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = palette.danger),
                        border = androidx.compose.foundation.BorderStroke(1.dp, palette.danger),
                    ) {
                        Icon(Icons.Outlined.Delete, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Delete profile")
                    }
                }
            }
        }
    }

    if (confirmDelete && existing != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = palette.card,
            title = { Text("Delete profile", color = palette.text) },
            text = {
                Text(
                    "Remove \"${existing.name}\" from this device?",
                    color = palette.textDim,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDelete(existing)
                }) { Text("Delete", color = palette.danger) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancel", color = palette.textDim) }
            },
        )
    }
}

// -------------------------------------------------------------------------------------------
// General
// -------------------------------------------------------------------------------------------

private fun androidx.compose.foundation.lazy.LazyListScope.generalTab(
    draft: Profile,
    update: (Profile) -> Unit,
    palette: JoePalette,
    state: AppState,
    onDelete: (Profile) -> Unit,
    existing: Profile?,
) {
    item {
        SectionLabel(palette, "Identity")
        JoeCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                JoeField(
                    palette = palette,
                    value = draft.name,
                    onValueChange = { update(draft.copy(name = it)) },
                    label = "Profile name",
                    placeholder = "Account 1",
                )
                Spacer(Modifier.height(12.dp))
                JoeField(
                    palette = palette,
                    value = draft.notes,
                    onValueChange = { update(draft.copy(notes = it)) },
                    label = "Notes",
                    placeholder = "What this profile is for",
                    singleLine = false,
                )
                Spacer(Modifier.height(12.dp))
                JoeField(
                    palette = palette,
                    value = draft.group,
                    onValueChange = { update(draft.copy(group = it)) },
                    label = "Group",
                    placeholder = "Ungrouped",
                )
                Spacer(Modifier.height(12.dp))
                JoeField(
                    palette = palette,
                    value = draft.tags,
                    onValueChange = { update(draft.copy(tags = it)) },
                    label = "Tags",
                    placeholder = "main, eu, ads",
                )
            }
        }
    }

    item {
        SectionLabel(palette, "Browser")
        JoeCard(Modifier.fillMaxWidth()) {
            Column {
                SettingRow(
                    palette = palette,
                    label = "Browser engine",
                    hint = "The browser the profile reports and the build it names",
                    trailing = {
                        Text(
                            UserAgentBuilder.prettyEngine(draft.browserKind),
                            style = MaterialTheme.typography.bodyMedium,
                            color = palette.text,
                        )
                    },
                )
                RowDivider(palette)
                Column(Modifier.padding(16.dp)) {
                    ChoiceRow(
                        palette = palette,
                        options = UserAgentBuilder.ENGINES,
                        selected = draft.browserKind,
                        onSelect = { engine ->
                            update(draft.copy(browserKind = engine))
                        },
                        label = { UserAgentBuilder.prettyEngine(it) },
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "The engine decides the shape of the user agent and the client hints.",
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.textFaint,
                    )
                }
                RowDivider(palette)
                SettingRow(
                    palette = palette,
                    label = "Reported build",
                    hint = "The four-part build the profile puts in its client hints",
                    trailing = {
                        Text(
                            draft.browserVersion.ifBlank { BrowserVersions.LATEST_STABLE },
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.textFaint,
                        )
                    },
                )
                RowDivider(palette)
                SettingRow(
                    palette = palette,
                    label = "Start page",
                    hint = draft.startUrl,
                    trailing = {
                        Text(
                            "Edit below",
                            style = MaterialTheme.typography.labelMedium,
                            color = palette.accentText,
                        )
                    },
                )
            }
        }
    }

    item {
        SectionLabel(palette, "Start page")
        JoeCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                JoeField(
                    palette = palette,
                    value = draft.startUrl,
                    onValueChange = { update(draft.copy(startUrl = it)) },
                    label = "Address",
                    placeholder = Profile.DEFAULT_START_URL,
                )
                Spacer(Modifier.height(10.dp))
                Note(
                    palette = palette,
                    text = "A profile opened for the first time always goes to browserscan.net, so the identity can be checked straight away.",
                )
            }
        }
    }

    item {
        SectionLabel(palette, "Session")
        JoeCard(Modifier.fillMaxWidth()) {
            Column {
                SettingRow(
                    palette = palette,
                    label = "Fresh session",
                    hint = "Clear cookies, site data and cache when the profile closes",
                    trailing = {
                        Switch(
                            checked = draft.freshSession,
                            onCheckedChange = { update(draft.copy(freshSession = it)) },
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
}

// -------------------------------------------------------------------------------------------
// Fingerprint
// -------------------------------------------------------------------------------------------

private fun androidx.compose.foundation.lazy.LazyListScope.fingerprintTab(
    draft: Profile,
    update: (Profile) -> Unit,
    palette: JoePalette,
    onMessage: (String, Boolean) -> Unit,
) {
    val fp = draft.fp
    val setFp: ((FingerprintConfig) -> Unit) = { update(draft.copy(fp = it)) }

    item {
        SectionLabel(palette, "Operating system")
        JoeCard(Modifier.fillMaxWidth()) {
            Column {
                SettingRow(
                    palette = palette,
                    label = "System",
                    hint = "The operating system the profile claims",
                    trailing = {
                        Text(
                            fp.os,
                            style = MaterialTheme.typography.bodyMedium,
                            color = palette.text,
                        )
                    },
                )
                RowDivider(palette)
                Column(Modifier.padding(16.dp)) {
                    ChoiceRow(
                        palette = palette,
                        options = FingerprintCatalog.OS_LIST.filter { it != FingerprintCatalog.OS_SYSTEM_DEFAULT },
                        selected = fp.os,
                        onSelect = { os ->
                            val rolled = FingerprintFactory.regenerate(fp, os, draft.proxy, hardwareOnly = true)
                            val repaired = FingerprintFactory.coerce(rolled)
                            setFp(rolled)
                            if (repaired > 0) {
                                onMessage("$repaired value was repaired to match the system.", false)
                            }
                        },
                    )
                }
            }
        }
    }

    item {
        SectionLabel(palette, "Hardware")
        JoeCard(Modifier.fillMaxWidth()) {
            Column {
                SettingRow(
                    palette = palette,
                    label = "Screen",
                    hint = "${fp.screenW} × ${fp.screenH} at ${fp.pixelRatio}x",
                    trailing = {
                        Text(
                            fp.deviceType,
                            style = MaterialTheme.typography.bodyMedium,
                            color = palette.text,
                        )
                    },
                )
                RowDivider(palette)
                SettingRow(
                    palette = palette,
                    label = "CPU cores",
                    hint = "navigator.hardwareConcurrency",
                    trailing = {
                        Text("${fp.cores}", style = MaterialTheme.typography.bodyMedium, color = palette.text)
                    },
                )
                RowDivider(palette)
                SettingRow(
                    palette = palette,
                    label = "Memory",
                    hint = "navigator.deviceMemory",
                    trailing = {
                        Text("${fp.memory} GB", style = MaterialTheme.typography.bodyMedium, color = palette.text)
                    },
                )
                RowDivider(palette)
                SettingRow(
                    palette = palette,
                    label = "WebGL",
                    hint = fp.webGlRenderer,
                    trailing = {
                        Text(
                            fp.webGlVendor,
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.textFaint,
                        )
                    },
                )
                RowDivider(palette)
                SettingRow(
                    palette = palette,
                    label = "Android version",
                    hint = "Used in the user agent and the client hints",
                    trailing = {
                        Text(
                            FingerprintFactory.androidVersionFor(fp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = palette.text,
                        )
                    },
                )
                RowDivider(palette)
                SettingRow(
                    palette = palette,
                    label = "Font set",
                    hint = "The fonts the profile advertises",
                    trailing = {
                        Text(
                            fp.fontSet,
                            style = MaterialTheme.typography.bodyMedium,
                            color = palette.text,
                        )
                    },
                )
            }
        }
    }

    item {
        SectionLabel(palette, "Locale")
        JoeCard(Modifier.fillMaxWidth()) {
            Column {
                SettingRow(
                    palette = palette,
                    label = "Time zone",
                    hint = fp.timezone,
                    trailing = {
                        Text(
                            fp.timezoneShort.ifBlank { fp.timezone },
                            style = MaterialTheme.typography.bodyMedium,
                            color = palette.text,
                        )
                    },
                )
                RowDivider(palette)
                SettingRow(
                    palette = palette,
                    label = "Language",
                    hint = fp.acceptLanguage,
                    trailing = {
                        Text(
                            fp.language,
                            style = MaterialTheme.typography.bodyMedium,
                            color = palette.text,
                        )
                    },
                )
                RowDivider(palette)
                SettingRow(
                    palette = palette,
                    label = "Match the proxy",
                    hint = "Line the clock and the language up with the exit node",
                    trailing = {
                        Switch(
                            checked = fp.autoMatchProxy,
                            onCheckedChange = {
                                val next = fp.copy(autoMatchProxy = it)
                                FingerprintFactory.applyLocale(next, draft.proxy)
                                setFp(next)
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
        SectionLabel(palette, "User agent")
        JoeCard(Modifier.fillMaxWidth()) {
            Column {
                SettingRow(
                    palette = palette,
                    label = "Generate automatically",
                    hint = "Build the user agent from the system, the engine and the build",
                    trailing = {
                        Switch(
                            checked = fp.autoUserAgent,
                            onCheckedChange = { setFp(fp.copy(autoUserAgent = it)) },
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
                if (!fp.autoUserAgent) {
                    RowDivider(palette)
                    Column(Modifier.padding(16.dp)) {
                        JoeField(
                            palette = palette,
                            value = fp.userAgentOverride,
                            onValueChange = { setFp(fp.copy(userAgentOverride = it)) },
                            label = "User agent",
                            placeholder = "Mozilla/5.0 ...",
                            singleLine = false,
                        )
                    }
                }
            }
        }
    }

    item {
        SectionLabel(palette, "Privacy")
        JoeCard(Modifier.fillMaxWidth()) {
            Column {
                SwitchRow(palette, "Canvas noise", "Perturb the canvas so two profiles do not share a hash", fp.canvasNoise) {
                    setFp(fp.copy(canvasNoise = it))
                }
                RowDivider(palette)
                SwitchRow(palette, "Audio noise", "Perturb the audio buffer in the same way", fp.audioNoise) {
                    setFp(fp.copy(audioNoise = it))
                }
                RowDivider(palette)
                SwitchRow(palette, "Font noise", "Perturb the measured width of text", fp.fontsNoise) {
                    setFp(fp.copy(fontsNoise = it))
                }
                RowDivider(palette)
                SwitchRow(palette, "Hide the battery", "Report a battery the profile's machine would have", fp.spoofBattery) {
                    setFp(fp.copy(spoofBattery = it))
                }
                RowDivider(palette)
                SwitchRow(palette, "Do not track", "Send the do-not-track header", fp.doNotTrack) {
                    setFp(fp.copy(doNotTrack = it))
                }
                RowDivider(palette)
                SettingRow(
                    palette = palette,
                    label = "WebRTC",
                    hint = when (fp.webRtcMode) {
                        "off" -> "Blocked entirely"
                        "public" -> "The public interface is allowed"
                        else -> "Only the proxy address is exposed"
                    },
                    trailing = {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            listOf("proxy", "public", "off").forEach { mode ->
                                JoeChip(
                                    palette = palette,
                                    text = mode,
                                    tone = if (fp.webRtcMode == mode) ChipTone.Accent else ChipTone.Neutral,
                                    modifier = Modifier.clickable { setFp(fp.copy(webRtcMode = mode)) },
                                )
                            }
                        }
                    },
                )
                RowDivider(palette)
                SettingRow(
                    palette = palette,
                    label = "Location",
                    hint = "What the browser asks a site about where it is",
                    trailing = {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            listOf("proxy", "system", "custom", "block").forEach { mode ->
                                JoeChip(
                                    palette = palette,
                                    text = mode,
                                    tone = if (fp.geoMode == mode) ChipTone.Accent else ChipTone.Neutral,
                                    modifier = Modifier.clickable { setFp(fp.copy(geoMode = mode)) },
                                )
                            }
                        }
                    },
                )
            }
        }
    }

    item {
        SectionLabel(palette, "Identity")
        JoeCard(Modifier.fillMaxWidth()) {
            Column {
                KeyValue(palette, "Signature", FingerprintIdentity.overallHash(fp))
                RowDivider(palette)
                KeyValue(
                    palette, "User agent",
                    UserAgentBuilder.resolve(fp, draft.browserKind, draft.browserVersion).userAgent
                )
                RowDivider(palette)
                KeyValue(palette, "Platform", fp.platform)
                RowDivider(palette)
                KeyValue(palette, "Seed", fp.seed.toString())
            }
        }
    }

    item {
        SectionLabel(palette, "Actions")
        JoeCard(Modifier.fillMaxWidth()) {
            Column {
                SettingRow(
                    palette = palette,
                    label = "Roll a new fingerprint",
                    hint = "Keep the system, change everything else",
                    onClick = {
                        val rolled = FingerprintFactory.regenerate(fp, fp.os, draft.proxy, widePool = true)
                        FingerprintFactory.coerce(rolled)
                        setFp(rolled)
                        onMessage("A new fingerprint was generated.", false)
                    },
                    trailing = {
                        Icon(Icons.Outlined.Refresh, null, tint = palette.accent)
                    },
                )
                RowDivider(palette)
                SettingRow(
                    palette = palette,
                    label = "Repair the fingerprint",
                    hint = "Fix anything that contradicts itself",
                    onClick = {
                        val repaired = FingerprintFactory.coerce(fp)
                        if (repaired == 0) {
                            onMessage("Nothing needed repairing.", false)
                        } else {
                            onMessage("$repaired value was repaired.", false)
                        }
                    },
                    trailing = {
                        Icon(Icons.Outlined.Bolt, null, tint = palette.accent)
                    },
                )
            }
        }
    }
}

// -------------------------------------------------------------------------------------------
// Proxy
// -------------------------------------------------------------------------------------------

private fun androidx.compose.foundation.lazy.LazyListScope.proxyTab(
    draft: Profile,
    update: (Profile) -> Unit,
    palette: JoePalette,
    state: AppState,
    onCheckProxy: (com.joebrowser.app.data.model.ProxyRecord) -> Unit,
    onMessage: (String, Boolean) -> Unit,
) {
    val proxy = draft.proxy

    item {
        SectionLabel(palette, "Route")
        JoeCard(Modifier.fillMaxWidth()) {
            Column {
                SettingRow(
                    palette = palette,
                    label = "Saved proxies",
                    hint = "Pick one from the library",
                    trailing = {
                        Text(
                            if (state.proxies.isEmpty()) "None saved" else "${state.proxies.size} saved",
                            style = MaterialTheme.typography.bodyMedium,
                            color = palette.text,
                        )
                    },
                )
                if (state.proxies.isNotEmpty()) {
                    RowDivider(palette)
                    Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                        state.proxies.forEach { record ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        update(
                                            draft.copy(
                                                proxy = com.joebrowser.app.data.model.ProxyConfig(
                                                    type = record.type, host = record.host, port = record.port,
                                                    user = record.user, password = record.password,
                                                    lastIp = record.lastIp, lastCountry = record.lastCountry,
                                                    lastCountryCode = record.lastCountryCode,
                                                    lastTimezone = record.lastTimezone, lastCity = record.lastCity,
                                                    lastStatus = record.lastStatus,
                                                )
                                            )
                                        )
                                    }
                                    .padding(horizontal = 6.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        record.display,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = palette.text,
                                    )
                                    Text(
                                        text = "${record.type.uppercase()} · ${record.host}:${record.port}" +
                                            if (record.lastIp.isNotBlank()) " · ${record.lastIp}" else "",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = palette.textFaint,
                                    )
                                }
                                JoeChip(
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
                        }
                    }
                }
            }
        }
    }

    item {
        SectionLabel(palette, "Manual")
        JoeCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                ChoiceRow(
                    palette = palette,
                    options = listOf("none", "http", "https", "socks5"),
                    selected = proxy.type,
                    onSelect = { update(draft.copy(proxy = proxy.copy(type = it))) },
                )
                Spacer(Modifier.height(12.dp))
                JoeField(
                    palette = palette,
                    value = proxy.host,
                    onValueChange = { update(draft.copy(proxy = proxy.copy(host = it))) },
                    label = "Host",
                    placeholder = "proxy.example.com",
                )
                Spacer(Modifier.height(12.dp))
                JoeField(
                    palette = palette,
                    value = if (proxy.port == 0) "" else proxy.port.toString(),
                    onValueChange = {
                        val port = it.toIntOrNull() ?: 0
                        update(draft.copy(proxy = proxy.copy(port = port)))
                    },
                    label = "Port",
                    placeholder = "8080",
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = KeyboardType.Number
                    ),
                )
                Spacer(Modifier.height(12.dp))
                JoeField(
                    palette = palette,
                    value = proxy.user,
                    onValueChange = { update(draft.copy(proxy = proxy.copy(user = it))) },
                    label = "Username",
                    placeholder = "Optional",
                )
                Spacer(Modifier.height(12.dp))
                JoeField(
                    palette = palette,
                    value = proxy.password,
                    onValueChange = { update(draft.copy(proxy = proxy.copy(password = it))) },
                    label = "Password",
                    placeholder = "Optional",
                )
                Spacer(Modifier.height(12.dp))
                JoeField(
                    palette = palette,
                    value = "",
                    onValueChange = { text ->
                        if (text.isBlank()) return@JoeField
                        val parsed = ProxyParser.parseLine(text)
                        if (parsed.enabled) {
                            update(draft.copy(proxy = parsed))
                            onMessage("The proxy link was read.", false)
                        } else {
                            onMessage("That link could not be read.", true)
                        }
                    },
                    label = "Paste a proxy link",
                    placeholder = "socks5://user:pass@host:1080",
                )
            }
        }
    }

    item {
        SectionLabel(palette, "Status")
        JoeCard(Modifier.fillMaxWidth()) {
            Column {
                SettingRow(
                    palette = palette,
                    label = "Connection",
                    hint = proxy.detail,
                    trailing = {
                        JoeChip(
                            palette = palette,
                            text = proxy.statusLabel,
                            tone = when (proxy.lastStatus) {
                                "online" -> ChipTone.Success
                                "offline" -> ChipTone.Danger
                                else -> ChipTone.Neutral
                            },
                        )
                    },
                )
                if (proxy.lastIp.isNotBlank()) {
                    RowDivider(palette)
                    KeyValue(palette, "Exit address", proxy.lastIp)
                }
                if (proxy.lastCountry.isNotBlank() || proxy.lastCity.isNotBlank()) {
                    RowDivider(palette)
                    KeyValue(
                        palette, "Location",
                        listOf(proxy.lastCity, proxy.lastCountry).filter { it.isNotBlank() }.joinToString(", ")
                    )
                }
                if (proxy.lastTimezone.isNotBlank()) {
                    RowDivider(palette)
                    KeyValue(palette, "Time zone", proxy.lastTimezone)
                }
                RowDivider(palette)
                SwitchRow(
                    palette,
                    "Fall back to a direct connection",
                    "Keep the session alive when the proxy stops answering",
                    proxy.fallbackDirect,
                ) {
                    update(draft.copy(proxy = proxy.copy(fallbackDirect = it)))
                }
            }
        }
    }
}

// -------------------------------------------------------------------------------------------
// Tools
// -------------------------------------------------------------------------------------------

private fun androidx.compose.foundation.lazy.LazyListScope.toolsTab(
    draft: Profile,
    update: (Profile) -> Unit,
    palette: JoePalette,
) {
    val tools = draft.tools
    val setTools: ((com.joebrowser.app.data.model.ToolConfig) -> Unit) = { update(draft.copy(tools = it)) }

    item {
        SectionLabel(palette, "Built in")
        JoeCard(Modifier.fillMaxWidth()) {
            Column {
                SwitchRow(
                    palette,
                    "Paste as human",
                    "Type the clipboard one character at a time, with the pauses a person takes",
                    tools.humanPaste,
                ) { setTools(tools.copy(humanPaste = it)) }
                RowDivider(palette)
                SettingRow(
                    palette = palette,
                    label = "Typing speed",
                    hint = "How long the pause between two characters is",
                    trailing = {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            FingerprintCatalog.PASTE_SPEEDS.forEach { (key, label) ->
                                JoeChip(
                                    palette = palette,
                                    text = label,
                                    tone = if (tools.humanPasteSpeed == key) ChipTone.Accent else ChipTone.Neutral,
                                    modifier = Modifier.clickable { setTools(tools.copy(humanPasteSpeed = key)) },
                                )
                            }
                        }
                    },
                )
                RowDivider(palette)
                SwitchRow(
                    palette,
                    "Watch the proxy",
                    "Warn when the public address changes or the proxy stops answering",
                    tools.proxyWatch,
                ) { setTools(tools.copy(proxyWatch = it)) }
                RowDivider(palette)
                SettingRow(
                    palette = palette,
                    label = "Check every",
                    hint = "How often the public address is read",
                    trailing = {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            listOf(30 to "30 s", 60 to "1 min", 120 to "2 min", 300 to "5 min", 600 to "10 min")
                                .forEach { (seconds, label) ->
                                    JoeChip(
                                        palette = palette,
                                        text = label,
                                        tone = if (tools.proxyWatchInterval == seconds) ChipTone.Accent else ChipTone.Neutral,
                                        modifier = Modifier.clickable {
                                            setTools(tools.copy(proxyWatchInterval = seconds))
                                        },
                                    )
                                }
                        }
                    },
                )
                RowDivider(palette)
                SwitchRow(
                    palette,
                    "Restore the right-click menu",
                    "Bring back the context menu, copying and selection on pages that block them",
                    tools.enableRightClick,
                ) { setTools(tools.copy(enableRightClick = it)) }
                RowDivider(palette)
                SwitchRow(
                    palette,
                    "Unlock selection",
                    "Also remove the drag and select blockers a page installs",
                    tools.unlockSelection,
                ) { setTools(tools.copy(unlockSelection = it)) }
                RowDivider(palette)
                SwitchRow(
                    palette,
                    "Install the profile's extensions",
                    "Load the extension the profile was given every time it opens",
                    tools.autoInstallExtensions,
                ) { setTools(tools.copy(autoInstallExtensions = it)) }
            }
        }
    }

    item {
        SectionLabel(palette, "About")
        JoeCard(Modifier.fillMaxWidth()) {
            Column {
                Note(
                    palette = palette,
                    text = "These tools are part of Joe Browser itself. They are not extensions and nothing has to be installed for them to work.",
                )
            }
        }
    }
}

// -------------------------------------------------------------------------------------------
// Small pieces
// -------------------------------------------------------------------------------------------

@Composable
private fun ChoiceRow(
    palette: JoePalette,
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    label: (String) -> String = { it },
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.chunked(3).forEach { row ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                row.forEach { option ->
                    JoeChip(
                        palette = palette,
                        text = label(option),
                        tone = if (selected == option) ChipTone.Accent else ChipTone.Neutral,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onSelect(option) },
                    )
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun SwitchRow(
    palette: JoePalette,
    label: String,
    hint: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    SettingRow(
        palette = palette,
        label = label,
        hint = hint,
        trailing = {
            Switch(
                checked = checked,
                onCheckedChange = onChange,
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
