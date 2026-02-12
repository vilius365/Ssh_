package com.pocketssh.app.terminal

import com.pocketssh.app.ssh.SshManager
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages a single active [TerminalBridge] instance.
 *
 * Fix C1: Simplified to single-connection model since SshManager is a singleton
 * with one connection. The previous Map<Long, TerminalBridgeImpl> design was
 * misleading -- switching profiles killed the prior connection silently.
 *
 * TODO: Support multiple concurrent connections by scoping SshManager per bridge.
 */
@Singleton
class TerminalSessionManagerImpl @Inject constructor(
    private val sshManager: SshManager,
) : TerminalSessionManager {

    // Fix C1/I2: Single bridge; no need for ConcurrentHashMap
    private var activeBridge: TerminalBridgeImpl? = null
    private var activeProfileId: Long? = null

    @Synchronized
    override fun getOrCreateBridge(profileId: Long): TerminalBridge {
        val current = activeBridge
        if (current != null && activeProfileId == profileId) {
            return current
        }
        // Tear down any existing bridge before creating a new one
        activeBridge?.detach()
        val bridge = TerminalBridgeImpl(sshManager)
        activeBridge = bridge
        activeProfileId = profileId
        return bridge
    }

    @Synchronized
    override fun removeBridge(profileId: Long) {
        if (activeProfileId == profileId) {
            activeBridge?.detach()
            activeBridge = null
            activeProfileId = null
        }
    }

    @Synchronized
    override fun removeAll() {
        activeBridge?.detach()
        activeBridge = null
        activeProfileId = null
    }
}
