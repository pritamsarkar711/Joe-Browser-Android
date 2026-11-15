package com.joebrowser.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

/**
 * The colour palette.
 *
 * The desktop build's rule is that depth comes from a four step neutral ramp and not from
 * shadows, and that the accent is a single blue. The same rule is kept here: a window, a
 * card, a panel and an input are four distinguishable greys, and only the accent colour
 * is ever saturated.
 */
object JoeColors {

    // Dark
    val DarkBg = Color(0xFF17181B)
    val DarkBgSoft = Color(0xFF1C1D21)
    val DarkSidebar = Color(0xFF141518)
    val DarkCard = Color(0xFF232529)
    val DarkPanel = Color(0xFF2A2C31)
    val DarkPanelHover = Color(0xFF313339)
    val DarkPanel2 = Color(0xFF2D2F34)
    val DarkInput = Color(0xFF1B1C20)
    val DarkLine = Color(0xFF33353B)
    val DarkLineSoft = Color(0xFF282A2F)
    val DarkText = Color(0xFFF2F3F5)
    val DarkTextDim = Color(0xFFC3C6CC)
    val DarkTextFaint = Color(0xFF9A9EA6)
    val DarkAccent = Color(0xFF5B9BF7)
    val DarkAccentHover = Color(0xFF74ADFF)
    val DarkAccentPressed = Color(0xFF4485E0)
    val DarkOnAccent = Color(0xFFFFFFFF)
    val DarkButtonBg = Color(0xFF2E3036)
    val DarkButtonBorder = Color(0xFF3B3E45)
    val DarkSuccess = Color(0xFF4EC38A)
    val DarkDanger = Color(0xFFF26D76)
    val DarkWarn = Color(0xFFF2A81B)

    // Light
    val LightBg = Color(0xFFF7F8FA)
    val LightBgSoft = Color(0xFFFFFFFF)
    val LightSidebar = Color(0xFFF1F3F7)
    val LightCard = Color(0xFFFFFFFF)
    val LightPanel = Color(0xFFFFFFFF)
    val LightPanelHover = Color(0xFFF2F4F8)
    val LightPanel2 = Color(0xFFECEEF3)
    val LightInput = Color(0xFFFFFFFF)
    val LightLine = Color(0xFFDFE2E8)
    val LightLineSoft = Color(0xFFEBEDF2)
    val LightText = Color(0xFF16181D)
    val LightTextDim = Color(0xFF4A4E57)
    val LightTextFaint = Color(0xFF6B707A)
    val LightAccent = Color(0xFF2C6BD6)
    val LightAccentHover = Color(0xFF3D7BE6)
    val LightAccentPressed = Color(0xFF2055B0)
    val LightOnAccent = Color(0xFFFFFFFF)
    val LightButtonBg = Color(0xFFFFFFFF)
    val LightButtonBorder = Color(0xFFDADDE4)
    val LightSuccess = Color(0xFF1F8A5F)
    val LightDanger = Color(0xFFC33744)
    val LightWarn = Color(0xFFF2A81B)
}

/** The colours that depend on the mode. */
data class JoePalette(
    val bg: Color,
    val bgSoft: Color,
    val sidebar: Color,
    val card: Color,
    val panel: Color,
    val panelHover: Color,
    val panel2: Color,
    val input: Color,
    val line: Color,
    val lineSoft: Color,
    val text: Color,
    val textDim: Color,
    val textFaint: Color,
    val accent: Color,
    val accentHover: Color,
    val accentPressed: Color,
    val onAccent: Color,
    val buttonBg: Color,
    val buttonBorder: Color,
    val success: Color,
    val danger: Color,
    val warn: Color,
    val isDark: Boolean,
) {
    val accentOnBg: Color = accent.copy(alpha = if (isDark) 0.13f else 0.09f)
    val accentBorder: Color = accent.copy(alpha = if (isDark) 0.51f else 0.43f)
    val accentText: Color = if (isDark) Color(0xFF8CBAFF) else Color(0xFF1C51A8)
    val hoverOverlay: Color = text.copy(alpha = 0.08f)
    val selectionOverlay: Color = text.copy(alpha = 0.10f)
}

val DarkPalette = JoePalette(
    bg = JoeColors.DarkBg,
    bgSoft = JoeColors.DarkBgSoft,
    sidebar = JoeColors.DarkSidebar,
    card = JoeColors.DarkCard,
    panel = JoeColors.DarkPanel,
    panelHover = JoeColors.DarkPanelHover,
    panel2 = JoeColors.DarkPanel2,
    input = JoeColors.DarkInput,
    line = JoeColors.DarkLine,
    lineSoft = JoeColors.DarkLineSoft,
    text = JoeColors.DarkText,
    textDim = JoeColors.DarkTextDim,
    textFaint = JoeColors.DarkTextFaint,
    accent = JoeColors.DarkAccent,
    accentHover = JoeColors.DarkAccentHover,
    accentPressed = JoeColors.DarkAccentPressed,
    onAccent = JoeColors.DarkOnAccent,
    buttonBg = JoeColors.DarkButtonBg,
    buttonBorder = JoeColors.DarkButtonBorder,
    success = JoeColors.DarkSuccess,
    danger = JoeColors.DarkDanger,
    warn = JoeColors.DarkWarn,
    isDark = true,
)

val LightPalette = JoePalette(
    bg = JoeColors.LightBg,
    bgSoft = JoeColors.LightBgSoft,
    sidebar = JoeColors.LightSidebar,
    card = JoeColors.LightCard,
    panel = JoeColors.LightPanel,
    panelHover = JoeColors.LightPanelHover,
    panel2 = JoeColors.LightPanel2,
    input = JoeColors.LightInput,
    line = JoeColors.LightLine,
    lineSoft = JoeColors.LightLineSoft,
    text = JoeColors.LightText,
    textDim = JoeColors.LightTextDim,
    textFaint = JoeColors.LightTextFaint,
    accent = JoeColors.LightAccent,
    accentHover = JoeColors.LightAccentHover,
    accentPressed = JoeColors.LightAccentPressed,
    onAccent = JoeColors.LightOnAccent,
    buttonBg = JoeColors.LightButtonBg,
    buttonBorder = JoeColors.LightButtonBorder,
    success = JoeColors.LightSuccess,
    danger = JoeColors.LightDanger,
    warn = JoeColors.LightWarn,
    isDark = false,
)

/**
 * The type scale.
 *
 * The desktop build promises that the base text is at least 16 px and nothing anywhere is
 * smaller than 14 px. The same promise is kept here.
 */
private fun joeTypography(): Typography {
    val base = FontFamily.Default
    return Typography(
        displaySmall = TextStyle(
            fontFamily = base, fontWeight = FontWeight.SemiBold,
            fontSize = 28.sp, lineHeight = 34.sp, letterSpacing = (-0.4).sp
        ),
        headlineMedium = TextStyle(
            fontFamily = base, fontWeight = FontWeight.SemiBold,
            fontSize = 23.sp, lineHeight = 29.sp, letterSpacing = (-0.2).sp
        ),
        headlineSmall = TextStyle(
            fontFamily = base, fontWeight = FontWeight.SemiBold,
            fontSize = 19.sp, lineHeight = 25.sp
        ),
        titleLarge = TextStyle(
            fontFamily = base, fontWeight = FontWeight.SemiBold,
            fontSize = 17.sp, lineHeight = 23.sp
        ),
        titleMedium = TextStyle(
            fontFamily = base, fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp, lineHeight = 21.sp
        ),
        titleSmall = TextStyle(
            fontFamily = base, fontWeight = FontWeight.Medium,
            fontSize = 14.sp, lineHeight = 19.sp
        ),
        bodyLarge = TextStyle(
            fontFamily = base, fontWeight = FontWeight.Normal,
            fontSize = 16.sp, lineHeight = 23.sp
        ),
        bodyMedium = TextStyle(
            fontFamily = base, fontWeight = FontWeight.Normal,
            fontSize = 15.sp, lineHeight = 21.sp
        ),
        bodySmall = TextStyle(
            fontFamily = base, fontWeight = FontWeight.Normal,
            fontSize = 14.sp, lineHeight = 19.sp
        ),
        labelLarge = TextStyle(
            fontFamily = base, fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp, lineHeight = 18.sp
        ),
        labelMedium = TextStyle(
            fontFamily = base, fontWeight = FontWeight.Medium,
            fontSize = 13.sp, lineHeight = 17.sp
        ),
        labelSmall = TextStyle(
            fontFamily = base, fontWeight = FontWeight.Medium,
            fontSize = 12.sp, lineHeight = 16.sp
        ),
    )
}

val JoeShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(20.dp),
)

/**
 * The app theme.
 *
 * @param themeMode 0 follows the system, 1 is dark, 2 is light.
 */
@Composable
fun JoeTheme(
    themeMode: Int = 0,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        1 -> true
        2 -> false
        else -> isSystemInDarkTheme()
    }
    val palette = if (dark) DarkPalette else LightPalette

    val colorScheme = if (dark) {
        darkColorScheme(
            primary = palette.accent,
            onPrimary = palette.onAccent,
            primaryContainer = palette.accentOnBg,
            onPrimaryContainer = palette.text,
            secondary = palette.accent,
            onSecondary = palette.onAccent,
            background = palette.bg,
            onBackground = palette.text,
            surface = palette.card,
            onSurface = palette.text,
            surfaceVariant = palette.panel2,
            onSurfaceVariant = palette.textDim,
            outline = palette.line,
            outlineVariant = palette.lineSoft,
            error = palette.danger,
            onError = Color.White,
            scrim = Color.Black,
        )
    } else {
        lightColorScheme(
            primary = palette.accent,
            onPrimary = palette.onAccent,
            primaryContainer = palette.accentOnBg,
            onPrimaryContainer = palette.text,
            secondary = palette.accent,
            onSecondary = palette.onAccent,
            background = palette.bg,
            onBackground = palette.text,
            surface = palette.card,
            onSurface = palette.text,
            surfaceVariant = palette.panel2,
            onSurfaceVariant = palette.textDim,
            outline = palette.line,
            outlineVariant = palette.lineSoft,
            error = palette.danger,
            onError = Color.White,
            scrim = Color.Black,
        )
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !dark
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = joeTypography(),
        shapes = JoeShapes,
        content = { androidx.compose.runtime.CompositionLocalProvider(LocalJoePalette provides palette) { content() } },
    )
}

/**
 * The palette for the current theme.
 *
 * The palette is provided through the theme so a component can be written without
 * threading the palette through every call, while a screen that needs to read a colour
 * outside a composable can still pass one in.
 */
val LocalJoePalette = androidx.compose.runtime.staticCompositionLocalOf { DarkPalette }

/** The palette for the current theme. */
@Composable
fun palette(dark: Boolean): JoePalette = if (dark) DarkPalette else LightPalette
