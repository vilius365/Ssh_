package com.remoteclaude.app.ssh

/**
 * Interface for managing SSH keys used in authentication.
 *
 * The implementation handles loading private key material from secure storage
 * for use during SSH authentication. Key material is decrypted on demand and
 * zeroed from memory after use.
 */
interface SshKeyManager {

    /**
     * Retrieve the private key bytes for a given key ID.
     * The returned bytes must be zeroed by the caller after use.
     *
     * @param keyId the unique identifier of the stored SSH key
     * @return decrypted private key bytes, or null if the key is not found
     */
    suspend fun getPrivateKeyBytes(keyId: String): ByteArray?

    /**
     * Retrieve the public key in OpenSSH format (e.g., "ssh-ed25519 AAAA...").
     *
     * @param keyId the unique identifier of the stored SSH key
     * @return the public key string, or null if the key is not found
     */
    suspend fun getPublicKeyString(keyId: String): String?

    /**
     * List all stored SSH key identifiers with their metadata.
     */
    suspend fun listKeys(): List<SshKeyInfo>

    /**
     * Delete a stored SSH key.
     *
     * @param keyId the unique identifier of the stored SSH key
     */
    suspend fun deleteKey(keyId: String)

    /**
     * Rename a stored SSH key.
     *
     * @param keyId the unique identifier of the stored SSH key
     * @param newName the new human-readable name
     */
    suspend fun renameKey(keyId: String, newName: String)
}

/**
 * Metadata about a stored SSH key.
 */
data class SshKeyInfo(
    val id: String,
    val name: String,
    val algorithm: String,
    val fingerprintSha256: String,
    val createdAt: Long,
)
