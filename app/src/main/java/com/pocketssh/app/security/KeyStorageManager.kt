package com.pocketssh.app.security

import com.pocketssh.app.ssh.SshKeyInfo

/**
 * Interface for secure SSH key storage.
 *
 * Manages the full lifecycle of SSH keys: generation, import, encrypted storage,
 * retrieval, and deletion. Keys are encrypted at rest using AES-256-GCM with a
 * master key backed by Android Keystore (TEE/StrongBox).
 */
interface KeyStorageManager {

    /**
     * Generate a new Ed25519 SSH key pair.
     *
     * @param name a human-readable name for the key
     * @return metadata about the newly generated key
     */
    suspend fun generateEd25519Key(name: String): SshKeyInfo

    /**
     * Generate a new RSA-4096 SSH key pair.
     *
     * @param name a human-readable name for the key
     * @return metadata about the newly generated key
     */
    suspend fun generateRsaKey(name: String): SshKeyInfo

    /**
     * Import an SSH private key from raw bytes.
     *
     * @param name a human-readable name for the key
     * @param privateKeyBytes the raw private key bytes (PEM or OpenSSH format)
     * @param passphrase optional passphrase if the key is encrypted
     * @return metadata about the imported key
     */
    suspend fun importKey(name: String, privateKeyBytes: ByteArray, passphrase: CharArray? = null): SshKeyInfo

    /**
     * Retrieve the decrypted private key bytes. Caller MUST zero the returned
     * bytes after use.
     *
     * @param keyId the key identifier
     * @return decrypted private key bytes, or null if not found
     */
    suspend fun getDecryptedPrivateKey(keyId: String): ByteArray?

    /**
     * Get the public key in OpenSSH format.
     *
     * @param keyId the key identifier
     * @return the public key string (e.g., "ssh-ed25519 AAAA... name")
     */
    suspend fun getPublicKeyString(keyId: String): String?

    /** List all stored keys. */
    suspend fun listKeys(): List<SshKeyInfo>

    /**
     * Delete a stored key securely (overwrites file before deletion).
     *
     * @param keyId the key identifier
     */
    suspend fun deleteKey(keyId: String)

    /**
     * Rename a stored key.
     *
     * @param keyId the key identifier
     * @param newName the new human-readable name
     */
    suspend fun renameKey(keyId: String, newName: String)
}
