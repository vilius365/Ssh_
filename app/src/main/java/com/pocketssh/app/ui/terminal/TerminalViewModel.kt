package com.pocketssh.app.ui.terminal

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pocketssh.app.data.model.TmuxSession
import com.pocketssh.app.data.repository.ConnectionRepository
import com.pocketssh.app.session.TmuxManager
import com.pocketssh.app.ssh.SshConnectionState
import com.pocketssh.app.ssh.SshKeyManager
import com.pocketssh.app.ssh.SshManager
import com.pocketssh.app.terminal.TerminalBridge
import com.pocketssh.app.terminal.TerminalSessionManager
import com.pocketssh.app.terminal.TerminalState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import javax.inject.Inject

data class HostDiagnostic(
    val hostname: String,
    val port: Int,
    val dnsResolved: Boolean,
    val portOpen: Boolean,
)

data class TerminalUiState(
    val connectionState: SshConnectionState = SshConnectionState.Disconnected,
    val terminalState: TerminalState = TerminalState(),
    val profileNickname: String = "",
    val currentSessionName: String? = null,
    val sessions: List<TmuxSession> = emptyList(),
    val fontSize: Int = 10,
    val showSessionManager: Boolean = false,
    val showDisconnectDialog: Boolean = false,
    val showFontSizeOverlay: Boolean = false,
    val hostDiagnostic: HostDiagnostic? = null,
)

@HiltViewModel
class TerminalViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val sshManager: SshManager,
    private val sshKeyManager: SshKeyManager,
    private val repository: ConnectionRepository,
    private val sessionManager: TerminalSessionManager,
    private val tmuxManager: TmuxManager,
    private val dataStore: DataStore<Preferences>,
) : ViewModel() {

    companion object {
        private const val TAG = "TerminalVM"
        private const val MIN_FONT_SIZE = 8
        private const val MAX_FONT_SIZE = 24
    }

    private val profileId: Long = savedStateHandle["profileId"] ?: -1L

    private val _uiState = MutableStateFlow(TerminalUiState())
    val uiState: StateFlow<TerminalUiState> = _uiState.asStateFlow()

    // Fix I10: Use interface type, not concrete TerminalBridgeImpl
    private var bridge: TerminalBridge? = null
    private var profileHostname: String? = null
    private var profilePort: Int = 22

    init {
        loadProfile()
        observeConnectionState()
        loadFontSizeFromSettings()
    }

    private fun loadProfile() {
        viewModelScope.launch {
            repository.getById(profileId)?.let { profile ->
                profileHostname = profile.hostname
                profilePort = profile.port
                _uiState.value = _uiState.value.copy(
                    profileNickname = profile.nickname,
                )
                connect(profile.hostname, profile.port, profile.username, profile.sshKeyId)
            }
        }
    }

    private fun connect(hostname: String, port: Int, username: String, keyId: String?) {
        viewModelScope.launch {
            try {
                // Fix H-3: Abort with error if key is configured but not found
                if (keyId == null) {
                    Log.e(TAG, "No SSH key configured for this connection")
                    _uiState.update {
                        it.copy(connectionState = SshConnectionState.Error(
                            "No SSH key selected. Edit connection and choose a key."
                        ))
                    }
                    return@launch
                }
                val keyBytes = sshKeyManager.getPrivateKeyBytes(keyId)
                if (keyBytes == null) {
                    Log.e(TAG, "SSH key not found: $keyId")
                    _uiState.update {
                        it.copy(connectionState = SshConnectionState.Error(
                            "SSH key not found -- it may have been deleted"
                        ))
                    }
                    return@launch
                }

                sshManager.connect(
                    hostname = hostname,
                    port = port,
                    username = username,
                    privateKeyBytes = keyBytes,
                    columns = 80,
                    rows = 34,
                )

                // Fix I3: Update last connected before the collect (which never returns)
                repository.updateLastConnected(profileId)

                // Fix I10: Use interface type, no downcast needed
                val termBridge = sessionManager.getOrCreateBridge(profileId)
                bridge = termBridge
                val stdout = sshManager.stdout
                val stdin = sshManager.stdin
                if (stdout != null && stdin != null) {
                    termBridge.attach(stdout, stdin, 80, 34)
                    // Fix I3: Use onEach + launchIn instead of blocking collect
                    termBridge.terminalState
                        .onEach { termState ->
                            _uiState.update { it.copy(terminalState = termState) }
                        }
                        .launchIn(viewModelScope)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Connection failed", e)
            }
        }
    }

    private fun observeConnectionState() {
        // Fix I4: Use onEach instead of combine(_uiState) to prevent feedback loop
        sshManager.connectionState
            .onEach { connState ->
                _uiState.update { it.copy(connectionState = connState) }
                if (connState is SshConnectionState.Error && connState.cause is IOException) {
                    runHostDiagnostic()
                } else {
                    _uiState.update { it.copy(hostDiagnostic = null) }
                }
            }
            .launchIn(viewModelScope)
    }

    private fun runHostDiagnostic() {
        val host = profileHostname ?: return
        val port = profilePort
        _uiState.update { it.copy(hostDiagnostic = null) }
        viewModelScope.launch {
            val diagnostic = withContext(Dispatchers.IO) {
                var dnsOk = false
                var portOk = false
                try {
                    InetAddress.getByName(host)
                    dnsOk = true
                } catch (_: Exception) {
                    // DNS resolution failed
                }
                if (dnsOk) {
                    try {
                        Socket().use { socket ->
                            socket.connect(InetSocketAddress(host, port), 3000)
                        }
                        portOk = true
                    } catch (_: Exception) {
                        // Port unreachable
                    }
                }
                HostDiagnostic(
                    hostname = host,
                    port = port,
                    dnsResolved = dnsOk,
                    portOpen = portOk,
                )
            }
            // Only update if still in Error state
            if (_uiState.value.connectionState is SshConnectionState.Error) {
                _uiState.update { it.copy(hostDiagnostic = diagnostic) }
            }
        }
    }

    // Fix S4: Read initial font size from DataStore settings
    private fun loadFontSizeFromSettings() {
        viewModelScope.launch {
            try {
                val prefs = dataStore.data.first()
                val savedFontSize = prefs[intPreferencesKey("font_size")] ?: 10
                _uiState.update { it.copy(fontSize = savedFontSize) }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to load font settings", e)
            }
        }
    }

    fun sendInput(data: ByteArray) {
        bridge?.write(data)
    }

    fun resizeTerminal(columns: Int, rows: Int) {
        bridge?.resize(columns, rows)
    }

    fun increaseFontSize() {
        val current = _uiState.value.fontSize
        if (current < MAX_FONT_SIZE) {
            val newSize = current + 1
            _uiState.value = _uiState.value.copy(
                fontSize = newSize,
                showFontSizeOverlay = true,
            )
            saveFontSize(newSize)
        }
    }

    fun decreaseFontSize() {
        val current = _uiState.value.fontSize
        if (current > MIN_FONT_SIZE) {
            val newSize = current - 1
            _uiState.value = _uiState.value.copy(
                fontSize = newSize,
                showFontSizeOverlay = true,
            )
            saveFontSize(newSize)
        }
    }

    fun setFontSize(size: Int) {
        val clamped = size.coerceIn(MIN_FONT_SIZE, MAX_FONT_SIZE)
        _uiState.value = _uiState.value.copy(
            fontSize = clamped,
            showFontSizeOverlay = true,
        )
        saveFontSize(clamped)
    }

    private fun saveFontSize(size: Int) {
        viewModelScope.launch {
            try {
                dataStore.edit { prefs ->
                    prefs[intPreferencesKey("font_size")] = size
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to save font size", e)
            }
        }
    }

    fun hideFontSizeOverlay() {
        _uiState.value = _uiState.value.copy(showFontSizeOverlay = false)
    }

    fun showSessionManager() {
        refreshSessions()
        _uiState.value = _uiState.value.copy(showSessionManager = true)
    }

    fun hideSessionManager() {
        _uiState.value = _uiState.value.copy(showSessionManager = false)
    }

    fun showDisconnectDialog() {
        _uiState.value = _uiState.value.copy(showDisconnectDialog = true)
    }

    fun hideDisconnectDialog() {
        _uiState.value = _uiState.value.copy(showDisconnectDialog = false)
    }

    fun refreshSessions() {
        viewModelScope.launch {
            try {
                // Fix S7: Delegate to TmuxManager instead of duplicating parsing logic
                val sessions = tmuxManager.listSessions()
                _uiState.update { it.copy(sessions = sessions) }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to list sessions", e)
            }
        }
    }

    fun attachSession(sessionName: String) {
        viewModelScope.launch {
            // Fix I8/H-1: Route through TmuxManager which validates session names
            val command = tmuxManager.getAttachCommand(sessionName)
            sendInput("$command\n".toByteArray())
            _uiState.update {
                it.copy(
                    currentSessionName = sessionName,
                    showSessionManager = false,
                )
            }
        }
    }

    fun detachSession() {
        sendInput("tmux detach-client\n".toByteArray())
        _uiState.update { it.copy(currentSessionName = null) }
    }

    fun killSession(sessionName: String) {
        viewModelScope.launch {
            // Fix I8/H-1: Route through TmuxManager which validates session names
            tmuxManager.killSession(sessionName)
            refreshSessions()
        }
    }

    fun createNewSession() {
        viewModelScope.launch {
            sendInput("tmux new-session\n".toByteArray())
            _uiState.value = _uiState.value.copy(showSessionManager = false)
            refreshSessions()
        }
    }

    fun reconnect() {
        viewModelScope.launch {
            repository.getById(profileId)?.let { profile ->
                connect(profile.hostname, profile.port, profile.username, profile.sshKeyId)
            }
        }
    }

    fun disconnect() {
        viewModelScope.launch {
            bridge?.detach()
            bridge = null
            sshManager.disconnect()
            _uiState.value = _uiState.value.copy(showDisconnectDialog = false)
        }
    }

    override fun onCleared() {
        super.onCleared()
        sessionManager.removeBridge(profileId)
    }
}
