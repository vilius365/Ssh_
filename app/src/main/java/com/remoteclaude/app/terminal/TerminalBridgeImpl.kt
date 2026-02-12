package com.remoteclaude.app.terminal

import android.util.Log
import com.remoteclaude.app.ssh.SshManager
import com.termux.terminal.TerminalEmulator
import com.termux.terminal.TerminalOutput
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream

/**
 * Concrete bridge between SSH I/O streams and the Termux [TerminalEmulator].
 *
 * Reads bytes from SSH stdout in a tight coroutine loop and feeds them into the
 * emulator via [TerminalEmulator.append]. User input flows in the opposite
 * direction: the emulator's [TerminalOutput] callback writes keystrokes to SSH
 * stdin.
 *
 * All access to the [TerminalEmulator] is synchronized through [emulatorLock]
 * because the emulator is not thread-safe.
 */
class TerminalBridgeImpl(
    private val sshManager: SshManager,
) : TerminalBridge {

    companion object {
        private const val TAG = "TerminalBridge"
        private const val READ_BUFFER_SIZE = 8192
        private const val DEFAULT_SCROLLBACK_ROWS = 10_000
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** FIFO channel for SSH writes — batched by a single consumer coroutine. */
    private val writeChannel = Channel<ByteArray>(Channel.UNLIMITED)
    private var writeJob: Job? = null

    private var emulator: TerminalEmulator? = null
    private val emulatorLock = Any()

    private var readJob: Job? = null
    private var sshStdout: InputStream? = null
    private var sshStdin: OutputStream? = null

    /** Current terminal title, captured from the [TerminalOutput.titleChanged] callback. */
    @Volatile
    private var currentTitle: String = ""

    private val _terminalState = MutableStateFlow(TerminalState())

    /** Observable terminal state for the UI layer. */
    override val terminalState: StateFlow<TerminalState> = _terminalState.asStateFlow()

    /**
     * [TerminalOutput] callback -- invoked by the emulator when it has bytes to
     * send back to the remote side (e.g. user keystrokes, escape responses)
     * and when terminal state changes occur (title, bell, colors, clipboard).
     */
    private val terminalOutput = object : TerminalOutput() {
        override fun write(data: ByteArray, offset: Int, count: Int) {
            writeChannel.trySend(data.copyOfRange(offset, offset + count))
        }

        override fun titleChanged(oldTitle: String?, newTitle: String?) {
            currentTitle = newTitle ?: ""
            emitState()
        }

        override fun onCopyTextToClipboard(text: String?) {
            // Clipboard integration handled at UI layer; no-op here.
        }

        override fun onPasteTextFromClipboard() {
            // Clipboard integration handled at UI layer; no-op here.
        }

        override fun onBell() {
            // Bell/vibration handled at UI layer if desired; no-op here.
        }

        override fun onColorsChanged() {
            emitState()
        }
    }

    /**
     * Minimal [TerminalSessionClient] required by the [TerminalEmulator] constructor.
     *
     * We bypass [TerminalSession] entirely (SSH provides our I/O), so these
     * callbacks are mostly no-ops. Logging methods delegate to [Log].
     */
    private val sessionClient = object : TerminalSessionClient {
        override fun onTextChanged(changedSession: TerminalSession) {}
        override fun onTitleChanged(changedSession: TerminalSession) {}
        override fun onSessionFinished(finishedSession: TerminalSession) {}
        override fun onCopyTextToClipboard(session: TerminalSession, text: String?) {}
        override fun onPasteTextFromClipboard(session: TerminalSession?) {}
        override fun onBell(session: TerminalSession) {}
        override fun onColorsChanged(session: TerminalSession) {}
        override fun onTerminalCursorStateChange(state: Boolean) {}
        override fun getTerminalCursorStyle(): Int = 0

        override fun logError(tag: String?, message: String?) {
            Log.e(tag ?: TAG, message ?: "")
        }

        override fun logWarn(tag: String?, message: String?) {
            Log.w(tag ?: TAG, message ?: "")
        }

        override fun logInfo(tag: String?, message: String?) {
            Log.i(tag ?: TAG, message ?: "")
        }

        override fun logDebug(tag: String?, message: String?) {
            Log.d(tag ?: TAG, message ?: "")
        }

        override fun logVerbose(tag: String?, message: String?) {
            Log.v(tag ?: TAG, message ?: "")
        }

        override fun logStackTraceWithMessage(tag: String?, message: String?, e: Exception?) {
            Log.e(tag ?: TAG, message, e)
        }

        override fun logStackTrace(tag: String?, e: Exception?) {
            Log.e(tag ?: TAG, "Stack trace", e)
        }
    }

    override fun attach(
        sshStdout: InputStream,
        sshStdin: OutputStream,
        columns: Int,
        rows: Int,
    ) {
        this.sshStdout = sshStdout
        this.sshStdin = sshStdin
        start(columns, rows)
    }

    override fun detach() {
        stop()
        sshStdout = null
        sshStdin = null
        // Fix C2: Cancel the scope to prevent leaked coroutines (e.g. from resize())
        scope.cancel()
    }

    override fun resize(columns: Int, rows: Int) {
        synchronized(emulatorLock) {
            emulator?.resize(columns, rows)
        }
        scope.launch {
            try {
                sshManager.resizeTerminal(columns, rows)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send window-change to SSH", e)
            }
        }
        emitState()
    }

    override fun write(data: ByteArray) {
        writeChannel.trySend(data)
    }

    /**
     * Returns the [TerminalEmulator] so the UI can read the screen buffer.
     *
     * Callers must **not** mutate emulator state directly; use [write] and
     * [resize] instead.
     */
    fun getEmulator(): TerminalEmulator? = emulator

    // ------------------------------------------------------------------
    // Internal lifecycle
    // ------------------------------------------------------------------

    /**
     * Create the emulator, start the read loop, and begin relaying data.
     */
    private fun start(columns: Int, rows: Int) {
        synchronized(emulatorLock) {
            emulator = TerminalEmulator(
                terminalOutput,
                columns,
                rows,
                DEFAULT_SCROLLBACK_ROWS,
                sessionClient,
            )
        }

        readJob = scope.launch {
            readLoop()
        }

        writeJob = scope.launch {
            writeLoop()
        }

        renderJob = scope.launch {
            renderLoop()
        }

        _terminalState.value = TerminalState(
            isRunning = true,
            columns = columns,
            rows = rows,
            emulator = emulator,
        )
    }

    /**
     * Stop the read loop and tear down the emulator.
     *
     * The read loop's blocking `stream.read()` will exit when the SSH layer
     * closes the stream (on disconnect). [Job.cancel] marks the coroutine
     * for cancellation so it exits at the next check point.
     */
    private fun stop() {
        val job = readJob
        readJob = null
        val wJob = writeJob
        writeJob = null
        val rJob = renderJob
        renderJob = null

        job?.cancel()
        wJob?.cancel()
        rJob?.cancel()

        synchronized(emulatorLock) {
            emulator = null
        }

        currentTitle = ""
        _terminalState.value = TerminalState(isRunning = false)
    }

    /**
     * Batching write loop: waits for at least one byte array from the channel,
     * drains all queued data, writes it in one batch, then flushes once.
     * This collapses many single-byte writes into efficient SSH packets.
     */
    private suspend fun writeLoop() {
        val batch = ByteArrayOutputStream()
        val stream = sshStdin ?: return

        try {
            while (currentCoroutineContext().isActive) {
                // Suspend until at least one write arrives
                val first = writeChannel.receive()
                batch.write(first)
                // Drain everything else currently queued (non-blocking)
                while (true) {
                    val next = writeChannel.tryReceive().getOrNull() ?: break
                    batch.write(next)
                }
                // Single write + flush for the entire batch
                val bytes = batch.toByteArray()
                stream.write(bytes)
                stream.flush()
                batch.reset()
            }
        } catch (e: Exception) {
            if (currentCoroutineContext().isActive) {
                Log.e(TAG, "Write loop error", e)
            }
        }
    }

    /**
     * Tight loop that reads SSH stdout and feeds bytes into the emulator.
     *
     * Runs on [Dispatchers.IO]. Exits when the stream returns EOF (-1) or
     * the coroutine is cancelled.
     */
    private suspend fun readLoop() {
        val buffer = ByteArray(READ_BUFFER_SIZE)
        val stream = sshStdout ?: return

        try {
            while (true) {
                val bytesRead = stream.read(buffer)
                if (bytesRead == -1) {
                    Log.i(TAG, "SSH stdout EOF -- connection closed")
                    break
                }
                if (bytesRead > 0) {
                    synchronized(emulatorLock) {
                        emulator?.append(buffer, bytesRead)
                    }
                    emitState()
                }
            }
        } catch (e: Exception) {
            if (currentCoroutineContext().isActive) {
                Log.e(TAG, "Read loop error", e)
            }
        } finally {
            _terminalState.value = _terminalState.value.copy(isRunning = false)
        }
    }

    /** Dirty flag set by readLoop; consumed by the render ticker. */
    @Volatile
    private var stateDirty = false
    private var renderJob: Job? = null
    private var epoch = 0L

    /**
     * Mark terminal state as changed. The render ticker will pick it up
     * at the next frame boundary (~30 fps) so we never overwhelm Compose.
     */
    private fun emitState() {
        stateDirty = true
    }

    /** Periodic tick that pushes the latest emulator snapshot to the UI. */
    private suspend fun renderLoop() {
        while (currentCoroutineContext().isActive) {
            delay(33) // ~30 fps — sufficient for terminal
            if (stateDirty) {
                stateDirty = false
                // Snapshot emulator state under lock, then emit outside lock
                // to minimize lock contention with readLoop.
                var state: TerminalState? = null
                synchronized(emulatorLock) {
                    val emu = emulator ?: return@synchronized
                    state = TerminalState(
                        isRunning = true,
                        title = currentTitle,
                        columns = emu.mColumns,
                        rows = emu.mRows,
                        cursorRow = emu.getCursorRow(),
                        cursorCol = emu.getCursorCol(),
                        cursorVisible = emu.shouldCursorBeVisible(),
                        activeTranscriptRows = emu.screen.activeTranscriptRows,
                        emulator = emu,
                        renderEpoch = ++epoch,
                    )
                }
                state?.let { _terminalState.value = it }
            }
        }
    }
}
