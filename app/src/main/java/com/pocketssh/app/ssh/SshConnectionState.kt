package com.pocketssh.app.ssh

/**
 * Represents the current state of an SSH connection.
 */
sealed class SshConnectionState {
    data object Disconnected : SshConnectionState()
    data object Connecting : SshConnectionState()
    data class Connected(
        val hostname: String,
        val port: Int,
        val username: String,
    ) : SshConnectionState()

    data class Error(val message: String, val cause: Throwable? = null) : SshConnectionState()
}
