package com.remoteclaude.app.ui.connections

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.remoteclaude.app.data.model.ConnectionProfile
import com.remoteclaude.app.data.repository.ConnectionRepository
import com.remoteclaude.app.ssh.SshKeyInfo
import com.remoteclaude.app.ssh.SshKeyManager
import com.remoteclaude.app.ssh.SshManagerImpl
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ConnectionEditorUiState(
    val nickname: String = "",
    val hostname: String = "",
    val port: String = "22",
    val username: String = "",
    val sshKeyId: String? = null,
    val sshKeyName: String? = null,
    val availableKeys: List<SshKeyInfo> = emptyList(),
    val isEditing: Boolean = false,
    val isSaving: Boolean = false,
    val isTesting: Boolean = false,
    val testResult: TestConnectionResult? = null,
    val showKeyPicker: Boolean = false,
    val nicknameError: String? = null,
    val hostnameError: String? = null,
    val portError: String? = null,
    val usernameError: String? = null,
) {
    val isValid: Boolean
        get() = nickname.isNotBlank() &&
            hostname.isNotBlank() &&
            isValidHostname(hostname) &&
            username.isNotBlank() &&
            port.toIntOrNull() in 1..65535

    companion object {
        private val HOSTNAME_REGEX = Regex(
            "^([a-zA-Z0-9]([a-zA-Z0-9\\-]*[a-zA-Z0-9])?\\.)*[a-zA-Z0-9]([a-zA-Z0-9\\-]*[a-zA-Z0-9])?$"
        )
        private val IP_REGEX = Regex(
            "^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$"
        )

        fun isValidHostname(hostname: String): Boolean {
            val trimmed = hostname.trim()
            return trimmed.isNotEmpty() &&
                (IP_REGEX.matches(trimmed) || HOSTNAME_REGEX.matches(trimmed))
        }
    }
}

sealed class TestConnectionResult {
    data object Success : TestConnectionResult()
    data class Failure(val message: String) : TestConnectionResult()
}

@HiltViewModel
class ConnectionEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: ConnectionRepository,
    private val sshKeyManager: SshKeyManager,
) : ViewModel() {

    private val profileId: Long = savedStateHandle["profileId"] ?: -1L

    private val _uiState = MutableStateFlow(ConnectionEditorUiState())
    val uiState: StateFlow<ConnectionEditorUiState> = _uiState.asStateFlow()

    init {
        loadKeys()
        if (profileId > 0) {
            viewModelScope.launch {
                repository.getById(profileId)?.let { profile ->
                    val keyName = profile.sshKeyId?.let { keyId ->
                        sshKeyManager.listKeys().find { it.id == keyId }?.name
                    }
                    _uiState.value = ConnectionEditorUiState(
                        nickname = profile.nickname,
                        hostname = profile.hostname,
                        port = profile.port.toString(),
                        username = profile.username,
                        sshKeyId = profile.sshKeyId,
                        sshKeyName = keyName,
                        isEditing = true,
                        availableKeys = _uiState.value.availableKeys,
                    )
                }
            }
        }
    }

    private fun loadKeys() {
        viewModelScope.launch {
            val keys = sshKeyManager.listKeys()
            _uiState.value = _uiState.value.copy(availableKeys = keys)
        }
    }

    fun updateNickname(value: String) {
        _uiState.value = _uiState.value.copy(
            nickname = value,
            nicknameError = if (value.isBlank()) "Required" else null,
        )
    }

    fun updateHostname(value: String) {
        _uiState.value = _uiState.value.copy(
            hostname = value,
            hostnameError = when {
                value.isBlank() -> "Required"
                !ConnectionEditorUiState.isValidHostname(value) -> "Enter a valid hostname or IP"
                else -> null
            },
        )
    }

    fun updatePort(value: String) {
        _uiState.value = _uiState.value.copy(
            port = value,
            portError = when {
                value.isBlank() -> "Required"
                value.toIntOrNull() == null -> "Must be a number"
                value.toInt() !in 1..65535 -> "Port must be 1-65535"
                else -> null
            },
        )
    }

    fun updateUsername(value: String) {
        _uiState.value = _uiState.value.copy(
            username = value,
            usernameError = if (value.isBlank()) "Required" else null,
        )
    }

    fun selectSshKey(keyInfo: SshKeyInfo?) {
        _uiState.value = _uiState.value.copy(
            sshKeyId = keyInfo?.id,
            sshKeyName = keyInfo?.name,
            showKeyPicker = false,
        )
    }

    fun showKeyPicker() {
        loadKeys()
        _uiState.value = _uiState.value.copy(showKeyPicker = true)
    }

    fun hideKeyPicker() {
        _uiState.value = _uiState.value.copy(showKeyPicker = false)
    }

    fun testConnection() {
        val state = _uiState.value
        if (!state.isValid) return

        viewModelScope.launch {
            _uiState.value = state.copy(isTesting = true, testResult = null)
            try {
                val keyId = state.sshKeyId
                if (keyId == null) {
                    _uiState.value = _uiState.value.copy(
                        isTesting = false,
                        testResult = TestConnectionResult.Failure(
                            "No SSH key selected"
                        ),
                    )
                    return@launch
                }
                val keyBytes = sshKeyManager.getPrivateKeyBytes(keyId)
                if (keyBytes == null) {
                    _uiState.value = _uiState.value.copy(
                        isTesting = false,
                        testResult = TestConnectionResult.Failure(
                            "SSH key not found -- it may have been deleted"
                        ),
                    )
                    return@launch
                }

                // Fix C4: Use a temporary SshManager instance so we don't kill active sessions
                val testSshManager = SshManagerImpl()
                try {
                    testSshManager.connect(
                        hostname = state.hostname.trim(),
                        port = state.port.toIntOrNull() ?: 22,
                        username = state.username.trim(),
                        privateKeyBytes = keyBytes,
                        columns = 80,
                        rows = 24,
                    )
                    _uiState.value = _uiState.value.copy(
                        isTesting = false,
                        testResult = TestConnectionResult.Success,
                    )
                } finally {
                    testSshManager.disconnect()
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isTesting = false,
                    testResult = TestConnectionResult.Failure(
                        e.message ?: "Unknown error"
                    ),
                )
            }
        }
    }

    fun save(onComplete: () -> Unit) {
        val state = _uiState.value
        if (!state.isValid) return

        viewModelScope.launch {
            _uiState.value = state.copy(isSaving = true)
            val profile = ConnectionProfile(
                id = if (state.isEditing) profileId else 0,
                nickname = state.nickname.trim(),
                hostname = state.hostname.trim(),
                port = state.port.toIntOrNull() ?: 22,
                username = state.username.trim(),
                sshKeyId = state.sshKeyId,
            )
            repository.save(profile)
            _uiState.value = state.copy(isSaving = false)
            onComplete()
        }
    }
}
