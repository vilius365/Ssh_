package com.pocketssh.app.ssh

import android.util.Log
import com.trilead.ssh2.ChannelCondition
import com.trilead.ssh2.Connection
import com.trilead.ssh2.KnownHosts
import com.trilead.ssh2.ServerHostKeyVerifier
import com.trilead.ssh2.Session
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "SshManagerImpl"
private const val CONNECT_TIMEOUT_MS = 30_000
private const val KEY_EXCHANGE_TIMEOUT_MS = 30_000

@Singleton
class SshManagerImpl @Inject constructor() : SshManager {

    private val _connectionState = MutableStateFlow<SshConnectionState>(SshConnectionState.Disconnected)
    override val connectionState: StateFlow<SshConnectionState> = _connectionState.asStateFlow()

    private val mutex = Mutex()
    private var connection: Connection? = null
    private var shellSession: Session? = null

    override val stdout: InputStream? get() = shellSession?.stdout
    override val stdin: OutputStream? get() = shellSession?.stdin
    override val stderr: InputStream? get() = shellSession?.stderr

    override suspend fun connect(
        hostname: String,
        port: Int,
        username: String,
        privateKeyBytes: ByteArray,
        columns: Int,
        rows: Int,
    ) {
        withContext(Dispatchers.IO) {
            mutex.withLock {
                var pemChars: CharArray? = null
                try {
                    // Clean up any existing connection first
                    closeInternal()

                    _connectionState.value = SshConnectionState.Connecting
                    Log.i(TAG, "Connecting to $hostname:$port as $username")

                    val conn = Connection(hostname, port)
                    conn.connect(
                        TofuHostKeyVerifier(),
                        CONNECT_TIMEOUT_MS,
                        KEY_EXCHANGE_TIMEOUT_MS,
                    )

                    // Key bytes are already OpenSSH PEM text from KeyStorageManager
                    pemChars = privateKeyBytesToChars(privateKeyBytes)
                    val authResult = conn.authenticateWithPublicKey(
                        username,
                        pemChars,
                        null,
                    )

                    if (authResult != true) {
                        Log.w(TAG, "Authentication failed for $username@$hostname:$port")
                        conn.close()
                        _connectionState.value = SshConnectionState.Error(
                            "Authentication failed for $username@$hostname"
                        )
                        return@withContext
                    }

                    val sess = conn.openSession()
                    sess.requestPTY("xterm-256color", columns, rows, 0, 0, null)
                    sess.startShell()

                    connection = conn
                    shellSession = sess
                    _connectionState.value = SshConnectionState.Connected(hostname, port, username)
                    Log.i(TAG, "Connected to $hostname:$port")
                } catch (e: IOException) {
                    Log.e(TAG, "Connection failed: ${e.message}", e)
                    val message = when (e) {
                        is UnknownHostException -> "Could not resolve hostname '$hostname'"
                        is ConnectException -> "Connection refused by $hostname:$port"
                        is SocketTimeoutException -> "Connection timed out (${CONNECT_TIMEOUT_MS / 1000}s)"
                        is NoRouteToHostException -> "No route to host $hostname"
                        else -> "Network error: ${e.message}"
                    }
                    _connectionState.value = SshConnectionState.Error(message, e)
                } catch (e: Exception) {
                    Log.e(TAG, "Unexpected error during connect: ${e.message}", e)
                    _connectionState.value = SshConnectionState.Error(
                        e.message ?: "Connection failed",
                        e,
                    )
                } finally {
                    privateKeyBytes.fill(0)
                    pemChars?.fill('\u0000')
                }
            }
        }
    }

    override suspend fun disconnect() = withContext(Dispatchers.IO) {
        mutex.withLock {
            Log.i(TAG, "Disconnecting")
            closeInternal()
            _connectionState.value = SshConnectionState.Disconnected
        }
    }

    override suspend fun resizeTerminal(columns: Int, rows: Int) {
        withContext(Dispatchers.IO) {
            mutex.withLock {
                shellSession?.resizePTY(columns, rows, 0, 0)
                    ?: Log.w(TAG, "resizeTerminal called but no active session")
            }
        }
    }

    override suspend fun executeCommand(
        command: String,
        timeoutMs: Long,
    ): CommandResult? = withContext(Dispatchers.IO) {
        val conn = mutex.withLock { connection } ?: return@withContext null

        var execSession: Session? = null
        try {
            execSession = conn.openSession()
            execSession.execCommand(command)

            // Fix C5: Read stdout and stderr concurrently to prevent pipe deadlock
            val (stdoutText, stderrText) = coroutineScope {
                val stdoutDeferred = async { execSession.stdout.bufferedReader().readText() }
                val stderrDeferred = async { execSession.stderr.bufferedReader().readText() }
                stdoutDeferred.await() to stderrDeferred.await()
            }

            execSession.waitForCondition(
                ChannelCondition.EXIT_STATUS or ChannelCondition.CLOSED or ChannelCondition.EOF,
                timeoutMs,
            )

            val exitCode = execSession.exitStatus ?: -1

            CommandResult(
                stdout = stdoutText,
                stderr = stderrText,
                exitCode = exitCode,
            )
        } catch (e: IOException) {
            Log.e(TAG, "Command execution failed: ${e.message}", e)
            null
        } finally {
            try {
                execSession?.close()
            } catch (_: Exception) {
                // Ignore close errors on exec channel
            }
        }
    }

    /**
     * Convert OpenSSH PEM bytes (from [KeyStorageManager]) to a char array
     * for Trilead SSH2's authenticateWithPublicKey.
     */
    private fun privateKeyBytesToChars(pemBytes: ByteArray): CharArray {
        val pem = String(pemBytes, Charsets.UTF_8)
        val firstLine = pem.lines().firstOrNull { it.isNotBlank() } ?: "(empty)"
        Log.i(TAG, "PEM key: ${pem.length} chars, first line: $firstLine")
        if (!pem.contains("-----BEGIN")) {
            Log.e(TAG, "WARNING: Decrypted key does not contain PEM header! " +
                "First 40 bytes: ${pemBytes.take(40).map { it.toInt() and 0xFF }}")
        }
        return pem.toCharArray()
    }

    /**
     * Closes the shell session and connection without changing state.
     * Must be called while holding [mutex].
     */
    private fun closeInternal() {
        try {
            shellSession?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Error closing shell session: ${e.message}")
        }
        try {
            connection?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Error closing connection: ${e.message}")
        }
        shellSession = null
        connection = null
    }
}

/**
 * Trust-on-first-use host key verifier.
 *
 * TODO: Implement persistent known_hosts storage. Currently accepts all host keys
 *  and logs the fingerprint. This is insecure against MITM attacks.
 */
private class TofuHostKeyVerifier : ServerHostKeyVerifier {
    override fun verifyServerHostKey(
        hostname: String,
        port: Int,
        serverHostKeyAlgorithm: String,
        serverHostKey: ByteArray,
    ): Boolean {
        val fingerprint = KnownHosts.createHexFingerprint(serverHostKeyAlgorithm, serverHostKey)
        Log.i(
            TAG,
            "Host key for $hostname:$port ($serverHostKeyAlgorithm): $fingerprint " +
                "[TOFU: auto-accepted, TODO: implement persistent known_hosts]"
        )
        return true
    }
}
