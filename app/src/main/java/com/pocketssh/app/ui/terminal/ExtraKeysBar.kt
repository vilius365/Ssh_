package com.pocketssh.app.ui.terminal

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocketssh.app.R
import com.pocketssh.app.ui.theme.DarkSurfaceBright
import com.pocketssh.app.ui.theme.DarkSurfaceContainer

/** Terminal modifier keys that toggle state (tap-to-activate, double-tap-to-lock). */
enum class TerminalModifier { CTRL, ALT }

/**
 * Floating extra keys panel anchored to the right edge of the terminal.
 *
 * Collapsed: a small tab handle on the right edge.
 * Expanded: a compact 4-column grid of terminal keys slides out.
 */
@Composable
fun ExtraKeysBar(
    onKeyPress: (ByteArray) -> Unit,
    activeModifiers: Set<TerminalModifier>,
    onModifierToggle: (TerminalModifier) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Expanded panel — slides in from right
        AnimatedVisibility(
            visible = expanded,
            enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
            exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut(),
        ) {
            KeysPanel(
                onKeyPress = onKeyPress,
                activeModifiers = activeModifiers,
                onModifierToggle = onModifierToggle,
            )
        }

        // Tab handle — always visible
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp))
                .background(DarkSurfaceContainer.copy(alpha = 0.85f))
                .clickable { expanded = !expanded }
                .padding(horizontal = 4.dp, vertical = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = if (expanded) ">" else "Fn",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

private val panelShape = RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp)
private val keyShape = RoundedCornerShape(4.dp)
private val keyWidthDp = 42.dp
private val keyHeightDp = 32.dp

/**
 * Compact 4-column grid of all extra keys.
 *
 * Layout:
 *   ESC  TAB  CTL  ALT
 *   ^C   -    /    ~
 *   UP   DN   LT   RT
 *   HOM  END  PgU  PgD
 *   claude (full width)
 */
@Composable
private fun KeysPanel(
    onKeyPress: (ByteArray) -> Unit,
    activeModifiers: Set<TerminalModifier>,
    onModifierToggle: (TerminalModifier) -> Unit,
) {
    Column(
        modifier = Modifier
            .clip(panelShape)
            .background(DarkSurfaceContainer.copy(alpha = 0.92f))
            .padding(4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        // Row 1: Modifiers
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            ExtraKey(
                label = stringResource(R.string.key_esc),
                onClick = { onKeyPress(byteArrayOf(0x1B)) },
            )
            ExtraKey(
                label = stringResource(R.string.key_tab),
                onClick = { onKeyPress(byteArrayOf(0x09)) },
            )
            ExtraKey(
                label = stringResource(R.string.key_ctrl),
                isActive = TerminalModifier.CTRL in activeModifiers,
                onClick = { onModifierToggle(TerminalModifier.CTRL) },
            )
            ExtraKey(
                label = stringResource(R.string.key_alt),
                isActive = TerminalModifier.ALT in activeModifiers,
                onClick = { onModifierToggle(TerminalModifier.ALT) },
            )
        }

        // Row 2: Symbols
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            ExtraKey(label = "^C", onClick = { onKeyPress(byteArrayOf(0x03)) })
            ExtraKey(label = "-", onClick = { onKeyPress("-".toByteArray()) })
            ExtraKey(label = "/", onClick = { onKeyPress("/".toByteArray()) })
            ExtraKey(label = "~", onClick = { onKeyPress("~".toByteArray()) })
        }

        // Row 3: Arrows
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            ExtraKeyIcon(
                icon = { Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Up") },
                onClick = { onKeyPress(ANSI_UP) },
            )
            ExtraKeyIcon(
                icon = { Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Down") },
                onClick = { onKeyPress(ANSI_DOWN) },
            )
            ExtraKeyIcon(
                icon = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Left") },
                onClick = { onKeyPress(ANSI_LEFT) },
            )
            ExtraKeyIcon(
                icon = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Right") },
                onClick = { onKeyPress(ANSI_RIGHT) },
            )
        }

        // Row 4: Navigation
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            ExtraKey(
                label = stringResource(R.string.key_home),
                onClick = { onKeyPress(ANSI_HOME) },
            )
            ExtraKey(
                label = stringResource(R.string.key_end),
                onClick = { onKeyPress(ANSI_END) },
            )
            ExtraKey(
                label = stringResource(R.string.key_pgup),
                onClick = { onKeyPress(ANSI_PAGE_UP) },
            )
            ExtraKey(
                label = stringResource(R.string.key_pgdn),
                onClick = { onKeyPress(ANSI_PAGE_DOWN) },
            )
        }

        // Row 5: Quick command
        ExtraKey(
            label = "claude",
            onClick = { onKeyPress("claude\r".toByteArray()) },
            modifier = Modifier.width(keyWidthDp * 4 + 6.dp), // span full panel width
        )
    }
}

/**
 * Compact extra key button with fixed size for the grid layout.
 */
@Composable
private fun ExtraKey(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isActive: Boolean = false,
) {
    Box(
        modifier = modifier
            .width(keyWidthDp)
            .height(keyHeightDp)
            .clip(keyShape)
            .background(
                if (isActive) MaterialTheme.colorScheme.primary
                else DarkSurfaceBright,
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontSize = 10.sp,
            color = if (isActive) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

/**
 * Compact extra key button with an icon instead of text label.
 */
@Composable
private fun ExtraKeyIcon(
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .width(keyWidthDp)
            .height(keyHeightDp)
            .clip(keyShape)
            .background(DarkSurfaceBright)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        icon()
    }
}

// ANSI escape sequences for navigation keys
private val ANSI_UP = byteArrayOf(0x1B, '['.code.toByte(), 'A'.code.toByte())
private val ANSI_DOWN = byteArrayOf(0x1B, '['.code.toByte(), 'B'.code.toByte())
private val ANSI_RIGHT = byteArrayOf(0x1B, '['.code.toByte(), 'C'.code.toByte())
private val ANSI_LEFT = byteArrayOf(0x1B, '['.code.toByte(), 'D'.code.toByte())
private val ANSI_HOME = byteArrayOf(0x1B, '['.code.toByte(), 'H'.code.toByte())
private val ANSI_END = byteArrayOf(0x1B, '['.code.toByte(), 'F'.code.toByte())
private val ANSI_PAGE_UP = byteArrayOf(0x1B, '['.code.toByte(), '5'.code.toByte(), '~'.code.toByte())
private val ANSI_PAGE_DOWN = byteArrayOf(0x1B, '['.code.toByte(), '6'.code.toByte(), '~'.code.toByte())
