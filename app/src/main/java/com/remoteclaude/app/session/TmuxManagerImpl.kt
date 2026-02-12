package com.remoteclaude.app.session

import android.util.Log
import com.remoteclaude.app.data.model.TmuxSession
import com.remoteclaude.app.ssh.SshManager
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "TmuxManagerImpl"

/** Delimiter used in tmux format strings to split fields. */
private const val FIELD_DELIMITER = "|"

/**
 * tmux format string for list-sessions.
 * Fields: session_name | session_windows | session_attached | session_activity
 */
private const val LIST_FORMAT =
    "#{session_name}${FIELD_DELIMITER}#{session_windows}${FIELD_DELIMITER}" +
        "#{session_attached}${FIELD_DELIMITER}#{session_activity}"

private const val EXPECTED_FIELD_COUNT = 4

/** Regex for valid tmux session names: alphanumeric, hyphens, underscores, dots. */
private val SESSION_NAME_REGEX = Regex("^[a-zA-Z0-9._-]+$")

@Singleton
class TmuxManagerImpl @Inject constructor(
    private val sshManager: SshManager,
) : TmuxManager {

    override suspend fun isTmuxAvailable(): Boolean {
        val result = sshManager.executeCommand("which tmux && tmux -V") ?: return false
        val available = result.exitCode == 0
        if (available) {
            Log.i(TAG, "tmux available: ${result.stdout.trim()}")
        } else {
            Log.i(TAG, "tmux not available on remote server")
        }
        return available
    }

    override suspend fun listSessions(): List<TmuxSession> {
        val result = sshManager.executeCommand(
            "tmux list-sessions -F '$LIST_FORMAT' 2>/dev/null"
        ) ?: return emptyList()

        if (result.exitCode != 0) {
            // Exit code 1 with "no server running" is normal when there are no sessions
            Log.d(TAG, "tmux list-sessions exited with ${result.exitCode}")
            return emptyList()
        }

        return result.stdout
            .lineSequence()
            .filter { it.isNotBlank() }
            .mapNotNull { line -> parseTmuxSession(line) }
            .toList()
    }

    override fun getAttachCommand(sessionName: String): String {
        require(SESSION_NAME_REGEX.matches(sessionName)) {
            "Invalid tmux session name: '$sessionName'. " +
                "Only alphanumeric characters, hyphens, underscores, and dots are allowed."
        }
        return "tmux new-session -A -s $sessionName"
    }

    override suspend fun killSession(sessionName: String) {
        require(SESSION_NAME_REGEX.matches(sessionName)) {
            "Invalid tmux session name: '$sessionName'. " +
                "Only alphanumeric characters, hyphens, underscores, and dots are allowed."
        }

        val result = sshManager.executeCommand("tmux kill-session -t $sessionName")
        if (result == null) {
            Log.w(TAG, "Cannot kill session '$sessionName': not connected")
            return
        }
        if (result.exitCode != 0) {
            Log.w(TAG, "Failed to kill tmux session '$sessionName': ${result.stderr.trim()}")
        } else {
            Log.i(TAG, "Killed tmux session '$sessionName'")
        }
    }

    /**
     * Parse a single line of tmux list-sessions output into a [TmuxSession].
     *
     * Expected format: `name|windowCount|attachedCount|activityTimestamp`
     */
    private fun parseTmuxSession(line: String): TmuxSession? {
        val fields = line.split(FIELD_DELIMITER)
        if (fields.size != EXPECTED_FIELD_COUNT) {
            Log.w(TAG, "Unexpected tmux list-sessions line format: $line")
            return null
        }

        val name = fields[0]
        val windowCount = fields[1].toIntOrNull() ?: 0
        val attachedCount = fields[2].toIntOrNull() ?: 0
        val activityEpoch = fields[3].toLongOrNull() ?: 0L

        return TmuxSession(
            name = name,
            windowCount = windowCount,
            isAttached = attachedCount > 0,
            lastActivity = activityEpoch,
        )
    }
}
