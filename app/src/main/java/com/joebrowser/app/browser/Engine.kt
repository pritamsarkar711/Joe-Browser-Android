package com.joebrowser.app.browser

/**
 * The engines the app can be.
 *
 * Every engine is built in. There is nothing to download, nothing to install and nothing
 * that hands the page over to another app: the profile chooses an engine and the page is
 * rendered by Joe Browser, wearing that engine's identity.
 *
 * That is the whole point of an anti-detect browser. A real Chrome on the device would be
 * a different program with its own profile, its own storage and its own idea of what the
 * machine is; the profile would be describing a machine the browser had never seen. An
 * engine here is the same rendering core with a different name on it, so everything the
 * profile says about itself is true of the program that is actually running.
 */
enum class Engine(
    val key: String,
    val label: String,
    /** The name the browser calls itself in the interface. */
    val brand: String,
    /** The word on the home button. */
    val tagline: String,
    /** The accent colour of the engine's own interface. */
    val accent: Long,
    /** The engine keeps a tab strip the way a desktop browser does. */
    val desktopStyle: Boolean,
) {
    /** Joe Browser's own engine. */
    JOE(
        key = "joe",
        label = "Joe Browser",
        brand = "Joe Browser",
        tagline = "Your own browser",
        accent = 0xFF2C6BD6,
        desktopStyle = true,
    ),

    CHROME(
        key = "chrome",
        label = "Chrome",
        brand = "Chrome",
        tagline = "Google's browser",
        accent = 0xFF4285F4,
        desktopStyle = true,
    ),

    CHROMIUM(
        key = "chromium",
        label = "Chromium",
        brand = "Chromium",
        tagline = "The open browser",
        accent = 0xFF3B7DD8,
        desktopStyle = true,
    ),

    BRAVE(
        key = "brave",
        label = "Brave",
        brand = "Brave",
        tagline = "Blocks the trackers",
        accent = 0xFFFB542B,
        desktopStyle = true,
    ),

    EDGE(
        key = "edge",
        label = "Edge",
        brand = "Edge",
        tagline = "Microsoft's browser",
        accent = 0xFF0F7CBE,
        desktopStyle = true,
    ),

    FIREFOX(
        key = "firefox",
        label = "Firefox",
        brand = "Firefox",
        tagline = "Not Chromium",
        accent = 0xFFFF7139,
        desktopStyle = false,
    );

    val accentColor: androidx.compose.ui.graphics.Color
        get() = androidx.compose.ui.graphics.Color(accent)

    companion object {
        fun byKey(key: String?): Engine =
            entries.firstOrNull { it.key.equals(key, ignoreCase = true) } ?: JOE

        /** The engines the profile editor offers. */
        val SELECTABLE: List<Engine> = listOf(JOE, CHROME, CHROMIUM, BRAVE, EDGE, FIREFOX)
    }
}
