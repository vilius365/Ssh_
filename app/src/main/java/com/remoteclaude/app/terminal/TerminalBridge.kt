package com.remoteclaude.app.terminal

import kotlinx.coroutines.flow.StateFlow
import java.io.InputStream
import java.io.OutputStream

/**
 * Bridge between SSH I/O streams and the Termux terminal emulator.
 *
 * Reads bytes from SSH stdout and feeds them into the terminal emulator.
 * When the user types, the terminal emulator's output callback sends bytes
 * to SSH stdin.
 */
interface TerminalBridge {

    // Fix I10: Expose terminalState on the interface so consumers don't need downcasts
    /** Observable terminal state for the UI layer. */
    val terminalState: StateFlow<TerminalState>

    /**
     * Attach the bridge to SSH streams and start relaying data.
     *
     * @param sshStdout the SSH channel stdout to read from
     * @param sshStdin the SSH channel stdin to write to
     * @param columns initial terminal width
     * @param rows initial terminal height
     */
    fun attach(sshStdout: InputStream, sshStdin: OutputStream, columns: Int, rows: Int)

    /** Detach from SSH streams and stop relaying data. */
    fun detach()

    /** Resize the terminal emulator buffer. */
    fun resize(columns: Int, rows: Int)

    /**
     * Write user input to the terminal (which forwards it to SSH stdin).
     *
     * @param data the bytes to send
     */
    fun write(data: ByteArray)
}
