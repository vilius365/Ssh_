package com.pocketssh.app.data.model

/**
 * Domain model for a tmux session on a remote server.
 */
data class TmuxSession(
    val name: String,
    val windowCount: Int,
    val isAttached: Boolean,
    val lastActivity: Long,
)
