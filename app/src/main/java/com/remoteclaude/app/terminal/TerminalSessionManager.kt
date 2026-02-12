package com.remoteclaude.app.terminal

/**
 * Manages terminal emulator instances.
 *
 * Each SSH connection gets its own terminal emulator instance. This manager
 * handles creation, lookup, and cleanup of emulator instances tied to
 * connection profile IDs.
 */
interface TerminalSessionManager {

    /**
     * Get or create a terminal bridge for a connection profile.
     *
     * @param profileId the connection profile ID
     * @return the terminal bridge for this connection
     */
    fun getOrCreateBridge(profileId: Long): TerminalBridge

    /**
     * Remove and clean up the terminal bridge for a connection.
     *
     * @param profileId the connection profile ID
     */
    fun removeBridge(profileId: Long)

    /** Clean up all terminal bridges. */
    fun removeAll()
}
