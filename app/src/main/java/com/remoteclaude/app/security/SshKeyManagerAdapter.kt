package com.remoteclaude.app.security

import com.remoteclaude.app.ssh.SshKeyInfo
import com.remoteclaude.app.ssh.SshKeyManager
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Adapter that exposes [KeyStorageManager] through the [SshKeyManager] interface
 * consumed by the SSH and UI layers.
 */
@Singleton
class SshKeyManagerAdapter @Inject constructor(
    private val keyStorageManager: KeyStorageManager,
) : SshKeyManager {

    override suspend fun getPrivateKeyBytes(keyId: String): ByteArray? =
        keyStorageManager.getDecryptedPrivateKey(keyId)

    override suspend fun getPublicKeyString(keyId: String): String? =
        keyStorageManager.getPublicKeyString(keyId)

    override suspend fun listKeys(): List<SshKeyInfo> =
        keyStorageManager.listKeys()

    // Fix C3: Expose delete/rename through the SshKeyManager interface
    override suspend fun deleteKey(keyId: String) =
        keyStorageManager.deleteKey(keyId)

    override suspend fun renameKey(keyId: String, newName: String) =
        keyStorageManager.renameKey(keyId, newName)
}
