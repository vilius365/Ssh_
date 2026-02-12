package com.remoteclaude.app.terminal

import com.termux.terminal.TerminalEmulator

/**
 * Observable state of a terminal session.
 *
 * The UI layer collects this as a [kotlinx.coroutines.flow.StateFlow] to drive
 * recomposition. The [emulator] reference allows the renderer to read the
 * screen buffer directly for drawing.
 */
data class TerminalState(
    val isRunning: Boolean = false,
    val title: String = "",
    val columns: Int = 69,
    val rows: Int = 24,
    val cursorRow: Int = 0,
    val cursorCol: Int = 0,
    val cursorVisible: Boolean = true,
    /** Number of scrollback rows available above the visible screen. */
    val activeTranscriptRows: Int = 0,
    /** The emulator reference for buffer reads. UI accesses this for rendering. */
    val emulator: TerminalEmulator? = null,
    /** Monotonic counter — ensures StateFlow always emits even when cursor hasn't moved. */
    val renderEpoch: Long = 0,
)
