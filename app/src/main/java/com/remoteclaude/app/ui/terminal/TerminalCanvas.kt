package com.remoteclaude.app.ui.terminal

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.TextUnit
import com.remoteclaude.app.terminal.TerminalState
import com.remoteclaude.app.ui.theme.AnsiBlack
import com.remoteclaude.app.ui.theme.AnsiBrightBlack
import com.remoteclaude.app.ui.theme.AnsiBrightBlue
import com.remoteclaude.app.ui.theme.AnsiBrightCyan
import com.remoteclaude.app.ui.theme.AnsiBrightGreen
import com.remoteclaude.app.ui.theme.AnsiBrightMagenta
import com.remoteclaude.app.ui.theme.AnsiBrightRed
import com.remoteclaude.app.ui.theme.AnsiBrightWhite
import com.remoteclaude.app.ui.theme.AnsiBrightYellow
import com.remoteclaude.app.ui.theme.AnsiBlue
import com.remoteclaude.app.ui.theme.AnsiCyan
import com.remoteclaude.app.ui.theme.AnsiGreen
import com.remoteclaude.app.ui.theme.AnsiMagenta
import com.remoteclaude.app.ui.theme.AnsiRed
import com.remoteclaude.app.ui.theme.AnsiWhite
import com.remoteclaude.app.ui.theme.AnsiYellow
import com.remoteclaude.app.ui.theme.DarkSurface
import com.remoteclaude.app.ui.theme.TerminalCursor
import com.termux.terminal.TextStyle
import com.termux.terminal.TerminalEmulator
import kotlinx.coroutines.delay

/**
 * Core terminal rendering composable using Compose Canvas.
 *
 * Reads the Termux [TerminalEmulator] screen buffer and draws each character cell
 * with proper foreground/background ANSI colors. Supports blinking cursor and
 * pinch-to-zoom font sizing.
 *
 * Performance: draws text in color-runs (one drawText per run, not per character),
 * uses the font's measured advance width for precise alignment, row-level buffer
 * access, and paint-color deduplication.
 */
@Composable
fun TerminalCanvas(
    terminalState: TerminalState,
    fontSize: TextUnit,
    scrollOffset: Int = 0,
    onInput: (ByteArray) -> Unit,
    onFontSizeChange: (Int) -> Unit,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val fontSizePx = with(density) { fontSize.toPx() }

    // Native paint for text rendering (reused across frames)
    val textPaint = remember(fontSizePx) {
        android.graphics.Paint().apply {
            isAntiAlias = true
            typeface = android.graphics.Typeface.MONOSPACE
            textSize = fontSizePx
            color = android.graphics.Color.WHITE
        }
    }

    // Cell width from actual font metrics — eliminates sub-pixel drift between
    // grid positioning and drawText character advances.
    val charWidth = textPaint.measureText("M")
    val charHeight = fontSizePx * 1.2f

    // Background paint for filled rects (avoid Compose DrawScope overhead for many rects)
    val bgPaint = remember { android.graphics.Paint() }

    // Cursor blink — fixed interval, does NOT restart on cursor move
    var cursorVisible by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(500)
            cursorVisible = !cursorVisible
        }
    }

    // Reusable per-row buffers (allocated once, reused each frame)
    val cols = terminalState.columns
    val rowChars = remember(cols) { CharArray(cols) }
    val fgArgbs = remember(cols) { IntArray(cols) }

    Canvas(
        modifier = modifier.fillMaxSize(),
    ) {
        // Fill background
        drawRect(color = DarkSurface, size = size)

        val emulator = terminalState.emulator ?: return@Canvas
        val screen = emulator.screen
        val rows = terminalState.rows
        val baseline = charHeight * 0.8f
        val nativeCanvas = drawContext.canvas.nativeCanvas
        val defaultFgArgb = DEFAULT_FG_ARGB

        val transcriptRows = terminalState.activeTranscriptRows

        for (i in 0 until rows) {
            val y = i * charHeight
            if (y > size.height) break

            // Map visible row index to emulator external row.
            // externalRow 0..rows-1 = visible screen; negative = scrollback history.
            val externalRow = i - scrollOffset
            if (externalRow < -transcriptRows) continue

            try {
                // Row-level buffer access — read line data ONCE per row
                val internalRow = screen.externalToInternalRow(externalRow)
                val lineObj = screen.allocateFullLineIfNecessary(internalRow)
                val spaceUsed = lineObj.spaceUsed
                val textArr = lineObj.mText
                val textArrLen = textArr.size

                // Single pass: decode styles, draw backgrounds, build row char/color arrays
                for (col in 0 until cols) {
                    val cellStyle = screen.getStyleAt(externalRow, col)

                    // Background (use ARGB int directly — no Color object allocation)
                    val bgIdx = TextStyle.decodeBackColor(cellStyle)
                    val bgArgb = ansiIndexToArgb(bgIdx)
                    if (bgArgb != null) {
                        bgPaint.color = bgArgb
                        val x = col * charWidth
                        nativeCanvas.drawRect(x, y, x + charWidth, y + charHeight, bgPaint)
                    }

                    // Character + foreground color
                    if (col < spaceUsed && col < textArrLen && textArr[col] > ' ') {
                        rowChars[col] = textArr[col]
                        val fgIdx = TextStyle.decodeForeColor(cellStyle)
                        fgArgbs[col] = ansiIndexToArgb(fgIdx) ?: defaultFgArgb
                    } else {
                        rowChars[col] = ' '
                        fgArgbs[col] = defaultFgArgb
                    }
                }

                // Draw text in color-runs: one drawText() per run instead of per character.
                // For typical terminal output (mostly one color), this is ~1-3 calls per row
                // instead of ~69 per-character calls.
                var runStart = 0
                for (col in 1..cols) {
                    if (col == cols || fgArgbs[col] != fgArgbs[runStart]) {
                        // Check if the run has any visible characters
                        var hasContent = false
                        for (i in runStart until col) {
                            if (rowChars[i] != ' ') { hasContent = true; break }
                        }
                        if (hasContent) {
                            textPaint.color = fgArgbs[runStart]
                            nativeCanvas.drawText(
                                rowChars, runStart, col - runStart,
                                runStart * charWidth, y + baseline, textPaint,
                            )
                        }
                        runStart = col
                    }
                }
            } catch (_: Exception) {
                // Screen buffer changed mid-draw (concurrent emulator update); skip row
            }
        }

        // Draw cursor (only visible when not scrolled into history)
        if (terminalState.cursorVisible && cursorVisible) {
            val adjustedCursorRow = terminalState.cursorRow + scrollOffset
            if (adjustedCursorRow in 0 until rows) {
                val cursorX = terminalState.cursorCol * charWidth
                val cursorY = adjustedCursorRow * charHeight
                drawRect(
                    color = TerminalCursor,
                    topLeft = Offset(cursorX, cursorY),
                    size = Size(charWidth, charHeight),
                    alpha = 0.7f,
                )
            }
        }
    }
}

/** Pre-computed default foreground ARGB to avoid repeated Color→Int conversion. */
private val DEFAULT_FG_ARGB = AnsiWhite.toArgbInt()

/**
 * Pre-computed ARGB lookup table for standard ANSI colors 0–15.
 * Index directly with the color index for O(1) lookup, no when() dispatch.
 */
private val ANSI_16_ARGB = intArrayOf(
    AnsiBlack.toArgbInt(),         // 0
    AnsiRed.toArgbInt(),           // 1
    AnsiGreen.toArgbInt(),         // 2
    AnsiYellow.toArgbInt(),        // 3
    AnsiBlue.toArgbInt(),          // 4
    AnsiMagenta.toArgbInt(),       // 5
    AnsiCyan.toArgbInt(),          // 6
    AnsiWhite.toArgbInt(),         // 7
    AnsiBrightBlack.toArgbInt(),   // 8
    AnsiBrightRed.toArgbInt(),     // 9
    AnsiBrightGreen.toArgbInt(),   // 10
    AnsiBrightYellow.toArgbInt(),  // 11
    AnsiBrightBlue.toArgbInt(),    // 12
    AnsiBrightMagenta.toArgbInt(), // 13
    AnsiBrightCyan.toArgbInt(),    // 14
    AnsiBrightWhite.toArgbInt(),   // 15
)

/**
 * Convert ANSI color index to ARGB int directly (avoids Color object creation).
 * Returns null for default/transparent.
 */
private fun ansiIndexToArgb(colorIndex: Int): Int? {
    return when {
        colorIndex in 0..15 -> ANSI_16_ARGB[colorIndex]
        colorIndex in 16..255 -> {
            val idx = colorIndex - 16
            if (idx < 216) {
                val r = (idx / 36) * 51
                val g = ((idx % 36) / 6) * 51
                val b = (idx % 6) * 51
                android.graphics.Color.rgb(r, g, b)
            } else {
                val gray = 8 + (idx - 216) * 10
                android.graphics.Color.rgb(gray, gray, gray)
            }
        }
        else -> null
    }
}

private fun Color.toArgbInt(): Int {
    return android.graphics.Color.argb(
        (alpha * 255).toInt(),
        (red * 255).toInt(),
        (green * 255).toInt(),
        (blue * 255).toInt(),
    )
}
