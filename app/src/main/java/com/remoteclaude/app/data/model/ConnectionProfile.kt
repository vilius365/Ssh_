package com.remoteclaude.app.data.model

/**
 * Domain model for an SSH connection profile.
 */
data class ConnectionProfile(
    val id: Long = 0,
    val nickname: String,
    val hostname: String,
    val port: Int = 22,
    val username: String,
    val sshKeyId: String? = null,
    val lastConnectedAt: Long? = null,
    val defaultTmuxSession: String? = null,
    val isDefault: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
) {
    /** Formatted display string, e.g. `user@host` or `user@host:2222`. */
    val displayAddress: String
        get() = "$username@$hostname${if (port != 22) ":$port" else ""}"
}
