package com.remoteclaude.app.ui.settings

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.remoteclaude.app.security.KeyStorageManager
import com.remoteclaude.app.ssh.SshKeyInfo
import com.remoteclaude.app.ssh.SshKeyManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "KeyManagerVM"

data class KeyManagerUiState(
    val keys: List<SshKeyInfo> = emptyList(),
    val isLoading: Boolean = false,
    val showGenerateDialog: Boolean = false,
    val showImportDialog: Boolean = false,
    val importFileName: String = "",
    val error: String? = null,
    val isOperationInProgress: Boolean = false,
)

@HiltViewModel
class KeyManagerViewModel @Inject constructor(
    private val sshKeyManager: SshKeyManager,
    private val keyStorageManager: KeyStorageManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(KeyManagerUiState())
    val uiState: StateFlow<KeyManagerUiState> = _uiState.asStateFlow()

    /** Held outside UI state because ByteArray breaks data class equality. */
    private var pendingImportBytes: ByteArray? = null

    init {
        loadKeys()
    }

    private fun loadKeys() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val keys = sshKeyManager.listKeys()
            _uiState.value = _uiState.value.copy(keys = keys, isLoading = false)
        }
    }

    fun deleteKey(keyId: String) {
        viewModelScope.launch {
            sshKeyManager.deleteKey(keyId)
            loadKeys()
        }
    }

    // -- Generate Key ----------------------------------------------------------

    fun showGenerateDialog() {
        _uiState.value = _uiState.value.copy(showGenerateDialog = true, error = null)
    }

    fun hideGenerateDialog() {
        _uiState.value = _uiState.value.copy(showGenerateDialog = false, error = null)
    }

    fun generateKey(name: String, algorithm: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isOperationInProgress = true, error = null)
            try {
                when (algorithm) {
                    "ed25519" -> keyStorageManager.generateEd25519Key(name)
                    "rsa-4096" -> keyStorageManager.generateRsaKey(name)
                    else -> error("Unknown algorithm: $algorithm")
                }
                _uiState.value = _uiState.value.copy(
                    showGenerateDialog = false,
                    isOperationInProgress = false,
                )
                loadKeys()
            } catch (e: Exception) {
                Log.e(TAG, "Key generation failed", e)
                _uiState.value = _uiState.value.copy(
                    error = e.message ?: "Key generation failed",
                    isOperationInProgress = false,
                )
            }
        }
    }

    // -- Import Key ------------------------------------------------------------

    /**
     * Called by the Screen after the file picker returns.
     * Stores the raw bytes and opens the import dialog.
     */
    fun onImportFileSelected(fileName: String, fileBytes: ByteArray) {
        pendingImportBytes = fileBytes
        _uiState.value = _uiState.value.copy(
            showImportDialog = true,
            importFileName = fileName,
            error = null,
        )
    }

    fun hideImportDialog() {
        pendingImportBytes?.fill(0)
        pendingImportBytes = null
        _uiState.value = _uiState.value.copy(
            showImportDialog = false,
            importFileName = "",
            error = null,
        )
    }

    fun performImport(name: String, passphrase: String?) {
        val bytes = pendingImportBytes ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isOperationInProgress = true, error = null)
            try {
                val pass = if (passphrase.isNullOrEmpty()) null else passphrase.toCharArray()
                keyStorageManager.importKey(name, bytes, pass)
                pendingImportBytes = null // bytes zeroed internally by importKey
                _uiState.value = _uiState.value.copy(
                    showImportDialog = false,
                    importFileName = "",
                    isOperationInProgress = false,
                )
                loadKeys()
            } catch (e: Exception) {
                Log.e(TAG, "Key import failed", e)
                _uiState.value = _uiState.value.copy(
                    error = e.message ?: "Import failed",
                    isOperationInProgress = false,
                )
            }
        }
    }
}
