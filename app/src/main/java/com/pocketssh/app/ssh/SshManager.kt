package com.pocketssh.app.ssh

import kotlinx.coroutines.flow.StateFlow
import java.io.InputStream
import java.io.OutputStream

/**
 * Interface for managing SSH connections.
 *
 * Provides methods to connect, disconnect, and manage an interactive SSH session
 * backed by ConnectBot sshlib. The connection provides raw I/O streams that the
 * terminal emulator reads from and writes to.
 */
interface SshManager {

    /** Current connection state as an observable flow. */
    val connectionState: StateFlow<SshConnectionState>

    /**
     * Establish an SSH connection and open an interactive shell session with a PTY.
     *
     * @param hostname the remote host to connect to
     * @param port the SSH port (default 22)
     * @param username the remote username
     * @param privateKeyBytes the SSH private key bytes for authentication (zeroed after use)
     * @param columns initial terminal width in columns
     * @param rows initial terminal height in rows
     */
    suspend fun connect(
        hostname: String,
        port: Int,
        username: String,
        privateKeyBytes: ByteArray,
        columns: Int,
        rows: Int,
    )

    /** Disconnect the current SSH session and close all streams. */
    suspend fun disconnect()

    /**
     * Notify the remote side of a terminal size change (SIGWINCH).
     *
     * @param columns new terminal width in columns
     * @param rows new terminal height in rows
     */
    suspend fun resizeTerminal(columns: Int, rows: Int)

    /**
     * Execute a command on a separate SSH exec channel and return the output.
     *
     * Opens a new channel, runs the command, waits for completion, and returns the
     * stdout output along with the exit code. This does NOT interfere with the
     * interactive shell session.
     *
     * @param command the command to execute
     * @param timeoutMs maximum time to wait for the command to complete
     * @return the command result, or null if not connected
     */
    suspend fun executeCommand(command: String, timeoutMs: Long = 10_000L): CommandResult?

    /** The stdout stream from the remote shell. Null when not connected. */
    val stdout: InputStream?

    /** The stdin stream to the remote shell. Null when not connected. */
    val stdin: OutputStream?

    /** The stderr stream from the remote shell. Null when not connected. */
    val stderr: InputStream?
}

/**
 * Result of executing a command on a separate SSH exec channel.
 */
data class CommandResult(
    val stdout: String,
    val stderr: String,
    val exitCode: Int,
)
