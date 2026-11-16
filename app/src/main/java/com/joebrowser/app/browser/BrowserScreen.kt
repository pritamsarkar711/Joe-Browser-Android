package com.joebrowser.app.browser

import android.content.Context
import android.webkit.WebView
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.DesktopMac
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.FindInPage
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.joebrowser.app.ui.theme.JoePalette
import kotlinx.coroutines.launch

/**
 * The browser screen.
 *
 * The layout is the one a phone browser has used for years and that everybody knows: a
 * bar with the address, a page, and a row of five buttons at the bottom. What is different
 * here is what is behind them — every tab is its own page with the profile's identity,
 * and every one of them talks to the internet through the profile's own route.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserScreen(
    session: BrowserSession,
    palette: JoePalette,
    onMessage: (String) -> Unit,
    onBackPressed: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var editing by remember { mutableStateOf(false) }
    var showTabs by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showBookmarks by remember { mutableStateOf(false) }
    var showDownloads by remember { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf(false) }
    var findOpen by remember { mutableStateOf(false) }
    var findText by remember { mutableStateOf("") }
    var bookmarks by remember { mutableStateOf(session.bookmarks.load()) }
    var downloads by remember { mutableStateOf(session.downloads.load()) }

    val tab = session.current

    // Keep the tab list and the view in step with each other.
    Box(
        Modifier
            .fillMaxSize()
            .background(palette.bg)
            .padding(WindowInsets.statusBars.asPaddingValues()),
    ) {
        Column(Modifier.fillMaxSize()) {

            // ---- the address bar -------------------------------------------------------
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                TabPill(
                    count = session.tabs.size,
                    accent = session.engine.accentColor,
                    onClick = { showTabs = true },
                )
                AddressBar(
                    tab = tab,
                    editing = editing,
                    onStartEdit = { editing = true },
                    onStopEdit = { editing = false },
                    onNavigate = { url ->
                        editing = false
                        tab?.view?.loadUrl(url)
                        session.history.record(session.profile.id, url, url)
                    },
                    accent = session.engine.accentColor,
                )
                IconCircle(
                    icon = Icons.Filled.MoreVert,
                    contentDescription = "Menu",
                    onClick = { showMenu = true },
                )
            }

            // ---- the find bar ----------------------------------------------------------
            AnimatedVisibility(
                visible = findOpen,
                enter = slideInVertically { -it } + fadeIn(),
                exit = slideOutVertically { -it } + fadeOut(),
            ) {
                FindBar(
                    text = findText,
                    accent = session.engine.accentColor,
                    onText = { findText = it },
                    onNext = {
                        tab?.view?.findNext(true)
                    },
                    onPrevious = {
                        tab?.view?.findNext(false)
                    },
                    onClose = {
                        findOpen = false
                        findText = ""
                        tab?.view?.clearMatches()
                    },
                )
            }

            // ---- the page --------------------------------------------------------------
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(palette.bgSoft),
            ) {
                if (tab?.view == null) {
                    Column(
                        Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            session.engine.brand,
                            style = MaterialTheme.typography.headlineSmall,
                            color = palette.text,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            session.engine.tagline,
                            style = MaterialTheme.typography.bodyMedium,
                            color = palette.textFaint,
                        )
                    }
                } else {
                    key(tab.id) {
                        AndroidView(
                            modifier = Modifier.fillMaxSize(),
                            factory = { ctx ->
                                (tab.view ?: WebView(ctx)).apply {
                                    layoutParams = android.view.ViewGroup.LayoutParams(
                                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                    )
                                }
                            },
                        )
                    }
                }

                if (tab != null && tab.loading) {
                    LinearProgressIndicator(
                        progress = { tab.progress / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp)
                            .align(Alignment.TopCenter),
                        color = session.engine.accentColor,
                        trackColor = Color.Transparent,
                    )
                }
            }

            // ---- the buttons -----------------------------------------------------------
            BrowserButtonBar(
                palette = palette,
                accent = session.engine.accentColor,
                canGoBack = tab?.canGoBack == true,
                canGoForward = tab?.canGoForward == true,
                bookmarked = tab?.url?.let { session.bookmarks.contains(it) } == true,
                onBack = {
                    val v = tab?.view ?: return@BrowserButtonBar
                    if (v.canGoBack()) v.goBack() else onBackPressed()
                },
                onForward = { tab?.view?.goForward() },
                onShare = {
                    val url = tab?.url ?: return@BrowserButtonBar
                    shareUrl(context, url)
                },
                onBookmarks = { showBookmarks = true },
                onTabs = { showTabs = true },
            )
        }
    }

    // ---- the tab switcher ------------------------------------------------------------
    if (showTabs) {
        TabSwitcher(
            session = session,
            palette = palette,
            onClose = { showTabs = false },
        )
    }

    // ---- the sheets ------------------------------------------------------------------
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    if (showMenu) {
        ModalBottomSheet(
            onDismissRequest = { showMenu = false },
            sheetState = sheetState,
            containerColor = palette.card,
        ) {
            BrowserMenu(
                palette = palette,
                accent = session.engine.accentColor,
                onNewTab = {
                    showMenu = false
                    session.openTab("")
                },
                onFind = {
                    showMenu = false
                    findOpen = true
                },
                onDownloads = {
                    showMenu = false
                    showDownloads = true
                },
                onBookmarks = {
                    showMenu = false
                    showBookmarks = true
                },
                onHistory = {
                    showMenu = false
                    showHistory = true
                },
                onDesktopSite = {
                    showMenu = false
                    tab?.let { session.applyDesktopSite(it, !it.desktopSite) }
                },
                onReader = {
                    showMenu = false
                    tab?.let { session.applyReaderMode(it, !it.readerMode) }
                },
                onAudit = {
                    showMenu = false
                    session.openAudit()
                },
            )
        }
    }

    if (showBookmarks) {
        ModalBottomSheet(
            onDismissRequest = { showBookmarks = false },
            containerColor = palette.card,
        ) {
            ListSheet(
                palette = palette,
                title = "Bookmarks",
                empty = "Nothing saved yet.",
                items = bookmarks.map { it.title to it.url },
                onOpen = {
                    showBookmarks = false
                    session.openTab(it)
                },
                onRemove = { index ->
                    bookmarks = bookmarks.filterIndexed { i, _ -> i != index }
                    session.bookmarks.remove(bookmarks.getOrNull(index)?.id ?: "")
                },
            )
        }
    }

    if (showDownloads) {
        ModalBottomSheet(
            onDismissRequest = { showDownloads = false },
            containerColor = palette.card,
        ) {
            ListSheet(
                palette = palette,
                title = "Downloads",
                empty = "Nothing downloaded yet.",
                items = downloads.map { it.title to it.path },
                onOpen = { },
                onRemove = { index ->
                    val id = downloads.getOrNull(index)?.id
                    downloads = downloads.filterIndexed { i, _ -> i != index }
                    if (id != null) session.downloads.remove(id)
                },
            )
        }
    }

    if (showHistory) {
        ModalBottomSheet(
            onDismissRequest = { showHistory = false },
            containerColor = palette.card,
        ) {
            ListSheet(
                palette = palette,
                title = "History",
                empty = "Nothing visited yet.",
                items = session.history.load(session.profile.id).take(60)
                    .map { it.title.ifBlank { it.url } to it.url },
                onOpen = {
                    showHistory = false
                    session.openTab(it)
                },
                onRemove = { },
            )
        }
    }
}

// -------------------------------------------------------------------------------------------
// The pieces
// -------------------------------------------------------------------------------------------

@Composable
private fun TabPill(count: Int, accent: Color, onClick: () -> Unit) {
    Box(
        Modifier
            .size(38.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(accent.copy(alpha = 0.12f))
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.titleMedium,
            color = accent,
        )
    }
}

@Composable
private fun AddressBar(
    tab: BrowserTab?,
    editing: Boolean,
    onStartEdit: () -> Unit,
    onStopEdit: () -> Unit,
    onNavigate: (String) -> Unit,
    accent: Color,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(38.dp)
            .clip(RoundedCornerShape(19.dp))
            .background(com.joebrowser.app.ui.theme.LocalJoePalette.current.input)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (editing) {
            var draft by remember { mutableStateOf(tab?.url ?: "") }
            BasicTextField(
                value = draft,
                onValueChange = { draft = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                textStyle = TextStyle(fontSize = 15.sp, color = com.joebrowser.app.ui.theme.LocalJoePalette.current.text),
                cursorBrush = SolidColor(accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = {
                    val target = draft.trim()
                    if (target.isNotEmpty()) {
                        val url = if (target.startsWith("http://") || target.startsWith("https://")) {
                            target
                        } else if (target.contains(' ') || !target.contains('.')) {
                            "https://www.google.com/search?q=" + java.net.URLEncoder.encode(target, "UTF-8")
                        } else {
                            "https://$target"
                        }
                        onNavigate(url)
                    } else {
                        onStopEdit()
                    }
                }),
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (draft.isEmpty()) {
                            Text(
                                "Search or type an address",
                                style = MaterialTheme.typography.bodyMedium,
                                color = com.joebrowser.app.ui.theme.LocalJoePalette.current.textFaint,
                            )
                        }
                        inner()
                    }
                },
            )
        } else {
            Text(
                text = tab?.title?.takeIf { it.isNotBlank() && it != tab?.url }
                    ?: tab?.url?.removePrefix("https://")?.removePrefix("http://")
                    ?: "Search or type an address",
                style = MaterialTheme.typography.bodyMedium,
                color = com.joebrowser.app.ui.theme.LocalJoePalette.current.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .clickable { onStartEdit() },
            )
        }
    }
}

@Composable
private fun IconCircle(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(38.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = com.joebrowser.app.ui.theme.LocalJoePalette.current.textDim,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun BrowserButtonBar(
    palette: JoePalette,
    accent: Color,
    canGoBack: Boolean,
    canGoForward: Boolean,
    bookmarked: Boolean,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onShare: () -> Unit,
    onBookmarks: () -> Unit,
    onTabs: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(palette.bgSoft)
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        Button(
            Icons.Outlined.ArrowBack, "Back",
            tint = if (canGoBack) palette.text else palette.textFaint,
            onClick = onBack,
        )
        Button(
            Icons.Outlined.ArrowForward, "Forward",
            tint = if (canGoForward) palette.text else palette.textFaint,
            onClick = onForward,
        )
        Button(Icons.Outlined.IosShare, "Share", tint = palette.text, onClick = onShare)
        Button(
            if (bookmarked) Icons.Filled.Star else Icons.Outlined.Bookmarks,
            "Bookmarks",
            tint = if (bookmarked) accent else palette.text,
            onClick = onBookmarks,
        )
        Button(Icons.Outlined.GridView, "Tabs", tint = palette.text, onClick = onTabs)
    }
}

@Composable
private fun Button(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    tint: Color,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = tint, modifier = Modifier.size(21.dp))
    }
}

@Composable
private fun FindBar(
    text: String,
    accent: Color,
    onText: (String) -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onClose: () -> Unit,
) {
    val p = com.joebrowser.app.ui.theme.LocalJoePalette.current
    Row(
        Modifier
            .fillMaxWidth()
            .background(p.bgSoft)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(Icons.Outlined.FindInPage, null, tint = p.textFaint, modifier = Modifier.size(18.dp))
        BasicTextField(
            value = text,
            onValueChange = onText,
            modifier = Modifier.weight(1f),
            singleLine = true,
            textStyle = TextStyle(fontSize = 15.sp, color = p.text),
            cursorBrush = SolidColor(accent),
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (text.isEmpty()) {
                        Text("Find on page", style = MaterialTheme.typography.bodyMedium, color = p.textFaint)
                    }
                    inner()
                }
            },
        )
        Text("Previous", style = MaterialTheme.typography.labelMedium, color = accent,
            modifier = Modifier.clickable { onPrevious() })
        Text("Next", style = MaterialTheme.typography.labelMedium, color = accent,
            modifier = Modifier.clickable { onNext() })
        Icon(Icons.Filled.Close, "Close", tint = p.textFaint,
            modifier = Modifier.size(18.dp).clickable { onClose() })
    }
}

/**
 * The tab switcher.
 *
 * A card per tab with its title and address, and a close button on the card itself. A tab
 * that is closed by mistake is a tab that is gone, so the button is the card's own
 * corner and not a menu item three taps away.
 */
@Composable
private fun TabSwitcher(
    session: BrowserSession,
    palette: JoePalette,
    onClose: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(palette.bg.copy(alpha = 0.96f))
            .padding(WindowInsets.systemBars.asPaddingValues()),
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Tabs",
                    style = MaterialTheme.typography.headlineSmall,
                    color = palette.text,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "New tab",
                    style = MaterialTheme.typography.labelLarge,
                    color = session.engine.accentColor,
                    modifier = Modifier.clickable {
                        session.openTab("")
                        onClose()
                    },
                )
            }
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(session.tabs.size) { index ->
                    val tab = session.tabs.getOrNull(index) ?: return@items
                    val selected = index == session.currentIndex
                    Box(
                        Modifier
                            .height(150.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(palette.card)
                            .border(
                                width = if (selected) 2.dp else 1.dp,
                                color = if (selected) session.engine.accentColor else palette.line,
                                shape = RoundedCornerShape(12.dp),
                            )
                            .clickable {
                                session.select(index)
                                onClose()
                            },
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text(
                                tab.title.ifBlank { "New tab" },
                                style = MaterialTheme.typography.titleMedium,
                                color = palette.text,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                tab.url,
                                style = MaterialTheme.typography.bodySmall,
                                color = palette.textFaint,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Box(
                            Modifier
                                .align(Alignment.TopEnd)
                                .padding(6.dp)
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(palette.panel2)
                                .clickable { session.closeTab(index) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Filled.Close, "Close tab",
                                tint = palette.textDim, modifier = Modifier.size(14.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * The menu.
 *
 * The items are the ones a browser actually has, and nothing else. A menu that lists
 * thirty things is a menu nobody reads.
 */
@Composable
private fun BrowserMenu(
    palette: JoePalette,
    accent: Color,
    onNewTab: () -> Unit,
    onFind: () -> Unit,
    onDownloads: () -> Unit,
    onBookmarks: () -> Unit,
    onHistory: () -> Unit,
    onDesktopSite: () -> Unit,
    onReader: () -> Unit,
    onAudit: () -> Unit,
) {
    Column(Modifier.padding(bottom = 24.dp)) {
        MenuRow(Icons.Outlined.Search, "New tab", palette, accent, onNewTab)
        MenuRow(Icons.Outlined.FindInPage, "Find on page", palette, accent, onFind)
        MenuRow(Icons.Outlined.Bookmarks, "Bookmarks", palette, accent, onBookmarks)
        MenuRow(Icons.Outlined.History, "History", palette, accent, onHistory)
        MenuRow(Icons.Outlined.Download, "Downloads", palette, accent, onDownloads)
        MenuRow(Icons.Outlined.DesktopMac, "Desktop site", palette, accent, onDesktopSite)
        MenuRow(Icons.Outlined.MenuBook, "Reader mode", palette, accent, onReader)
        MenuRow(Icons.Outlined.Fingerprint, "Identity check", palette, accent, onAudit)
    }
}

@Composable
private fun MenuRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    palette: JoePalette,
    accent: Color,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 22.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(icon, null, tint = palette.textDim, modifier = Modifier.size(20.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, color = palette.text)
    }
}

/**
 * A sheet with a list in it.
 */
@Composable
private fun ListSheet(
    palette: JoePalette,
    title: String,
    empty: String,
    items: List<Pair<String, String>>,
    onOpen: (String) -> Unit,
    onRemove: (Int) -> Unit,
) {
    Column(Modifier.padding(bottom = 24.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            color = palette.text,
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 8.dp),
        )
        if (items.isEmpty()) {
            Text(
                empty,
                style = MaterialTheme.typography.bodyMedium,
                color = palette.textFaint,
                modifier = Modifier.padding(horizontal = 22.dp, vertical = 14.dp),
            )
        } else {
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                items(items.size) { index ->
                    val item = items[index]
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onOpen(item.second) }
                            .padding(horizontal = 22.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                item.first,
                                style = MaterialTheme.typography.bodyLarge,
                                color = palette.text,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                item.second,
                                style = MaterialTheme.typography.bodySmall,
                                color = palette.textFaint,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Icon(
                            Icons.Filled.Close, "Remove",
                            tint = palette.textFaint,
                            modifier = Modifier
                                .size(20.dp)
                                .clickable { onRemove(index) },
                        )
                    }
                }
            }
        }
    }
}

private fun shareUrl(context: Context, url: String) {
    runCatching {
        context.startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, url)
                },
                null,
            )
        )
    }
}
