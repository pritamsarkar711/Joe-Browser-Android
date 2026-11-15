package com.joebrowser.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.joebrowser.app.ui.theme.JoePalette

/**
 * A panel with a title and a hairline border.
 *
 * The card is the app's one container. There is no shadow: depth comes from the neutral
 * ramp, and a shadow on a phone reads as dirt.
 */
@Composable
fun JoeCard(
    modifier: Modifier = Modifier,
    palette: JoePalette = com.joebrowser.app.ui.theme.LocalJoePalette.current,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(palette.card)
            .border(1.dp, palette.line, RoundedCornerShape(12.dp)),
    ) { content() }
}

/** A section heading: small, semibold, dim, uppercase. */
@Composable
fun SectionLabel(palette: JoePalette, text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = palette.textFaint,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier.padding(horizontal = 4.dp, vertical = 8.dp),
    )
}

/** A row inside a card: a label on the left, a control or a value on the right. */
@Composable
fun SettingRow(
    palette: JoePalette,
    label: String,
    modifier: Modifier = Modifier,
    hint: String = "",
    onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = palette.text,
            )
            if (hint.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.textFaint,
                )
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(12.dp))
            trailing()
        }
    }
}

/** A hairline between two rows of a card. */
@Composable
fun RowDivider(palette: JoePalette, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(palette.lineSoft)
    )
}

/**
 * A small rounded label.
 *
 * The chip is the app's most repeated element: it carries a piece of status in a way that
 * never competes with the content next to it.
 */
@Composable
fun JoeChip(
    palette: JoePalette,
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    tone: ChipTone = ChipTone.Neutral,
) {
    val (bg, fg) = when (tone) {
        ChipTone.Neutral -> palette.panel2 to palette.textDim
        ChipTone.Accent -> palette.accentOnBg to palette.accentText
        ChipTone.Success -> palette.success.copy(alpha = 0.14f) to palette.success
        ChipTone.Danger -> palette.danger.copy(alpha = 0.14f) to palette.danger
        ChipTone.Warn -> palette.warn.copy(alpha = 0.16f) to palette.warn
    }
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(13.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = fg,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

enum class ChipTone { Neutral, Accent, Success, Danger, Warn }

/** A coloured dot, used for status. */
@Composable
fun StatusDot(palette: JoePalette, tone: ChipTone, modifier: Modifier = Modifier, size: Int = 8) {
    val color = when (tone) {
        ChipTone.Neutral -> palette.textFaint
        ChipTone.Accent -> palette.accent
        ChipTone.Success -> palette.success
        ChipTone.Danger -> palette.danger
        ChipTone.Warn -> palette.warn
    }
    Box(
        modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(color)
    )
}

/** A field that matches the app's surfaces. */
@Composable
fun JoeField(
    palette: JoePalette,
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    singleLine: Boolean = true,
    enabled: Boolean = true,
    leading: ImageVector? = null,
    trailing: @Composable (() -> Unit)? = null,
    keyboardOptions: androidx.compose.foundation.text.KeyboardOptions =
        androidx.compose.foundation.text.KeyboardOptions.Default,
    isError: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        label = { Text(label, style = MaterialTheme.typography.bodySmall) },
        placeholder = if (placeholder.isBlank()) null else {
            { Text(placeholder, style = MaterialTheme.typography.bodyMedium, color = palette.textFaint) }
        },
        singleLine = singleLine,
        enabled = enabled,
        isError = isError,
        shape = RoundedCornerShape(10.dp),
        leadingIcon = leading?.let {
            { Icon(it, contentDescription = null, tint = palette.textFaint, modifier = Modifier.size(18.dp)) }
        },
        trailingIcon = trailing,
        keyboardOptions = keyboardOptions,
        textStyle = MaterialTheme.typography.bodyLarge,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = palette.input,
            unfocusedContainerColor = palette.input,
            disabledContainerColor = palette.input,
            errorContainerColor = palette.input,
            focusedBorderColor = palette.accent,
            unfocusedBorderColor = palette.line,
            disabledBorderColor = palette.line,
            errorBorderColor = palette.danger,
            focusedLabelColor = palette.accent,
            unfocusedLabelColor = palette.textFaint,
            focusedTextColor = palette.text,
            unfocusedTextColor = palette.text,
            disabledTextColor = palette.textFaint,
            cursorColor = palette.accent,
        ),
    )
}

/**
 * The empty state.
 *
 * A screen with nothing in it should still say what to do next, so every empty state
 * carries a line of explanation and, where it makes sense, the button that fixes it.
 */
@Composable
fun EmptyState(
    palette: JoePalette,
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    action: @Composable (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(palette.accentOnBg),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = palette.accent, modifier = Modifier.size(26.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = palette.text,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = palette.textFaint,
            modifier = Modifier.fillMaxWidth(),
        )
        if (action != null) {
            Spacer(Modifier.height(20.dp))
            action()
        }
    }
}

/** A small informational note. */
@Composable
fun Note(
    palette: JoePalette,
    text: String,
    modifier: Modifier = Modifier,
    tone: ChipTone = ChipTone.Neutral,
) {
    val color = when (tone) {
        ChipTone.Danger -> palette.danger
        ChipTone.Warn -> palette.warn
        ChipTone.Success -> palette.success
        ChipTone.Accent -> palette.accentText
        ChipTone.Neutral -> palette.textFaint
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(palette.panel2)
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(Icons.Outlined.Info, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = palette.textDim)
    }
}

/** A hairline card that can be tapped. */
@Composable
fun TappableCard(
    palette: JoePalette,
    modifier: Modifier = Modifier,
    border: BorderStroke? = null,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(palette.card)
            .then(
                if (border != null) Modifier.border(border, RoundedCornerShape(12.dp))
                else Modifier.border(1.dp, palette.line, RoundedCornerShape(12.dp))
            )
            .clickable { onClick() },
    ) { content() }
}

/** A key and value pair, used in the read-only summary panels. */
@Composable
fun KeyValue(
    palette: JoePalette,
    key: String,
    value: String,
    modifier: Modifier = Modifier,
    monospace: Boolean = false,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = key,
            style = MaterialTheme.typography.bodyMedium,
            color = palette.textFaint,
            modifier = Modifier.width(124.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = palette.text,
            modifier = Modifier.weight(1f),
        )
    }
}
