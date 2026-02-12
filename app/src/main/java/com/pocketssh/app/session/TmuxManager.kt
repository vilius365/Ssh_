package com.pocketssh.app.session

import com.pocketssh.app.data.model.TmuxSession

/**
 * Interface for managing tmux sessions on a remote server over SSH.
 *
 * All operations execute tmux commands through the active SSH connection.
 * Uses normal mode (not control mode) -- tmux renders panes/windows server-side,
 * and we display the combined output in the terminal emulator.
 */
interface TmuxManager {

    /**
     * Check if tmux is installed on the remote server.
     *
     * @return true if tmux is available, false otherwise
     */
    suspend fun isTmuxAvailable(): Boolean

    /**
     * List all tmux sessions on the remote server.
     *
     * @return list of tmux sessions with their metadata
     */
    suspend fun listSessions(): List<TmuxSession>

    /**
     * Create a new tmux session or attach to an existing one.
     * Uses `tmux new-session -A -s <name>` for idempotent create-or-attach.
     *
     * @param sessionName the tmux session name
     * @return the command string to execute in the shell
     */
    fun getAttachCommand(sessionName: String): String

    /**
     * Kill a tmux session by name.
     *
     * @param sessionName the tmux session name to kill
     */
    suspend fun killSession(sessionName: String)
}
