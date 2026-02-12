package com.pocketssh.app.session

import com.pocketssh.app.ssh.CommandResult
import com.pocketssh.app.ssh.SshManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TmuxManagerImplTest {

    private lateinit var sshManager: SshManager
    private lateinit var tmuxManager: TmuxManagerImpl

    @Before
    fun setUp() {
        sshManager = mockk(relaxed = true)
        tmuxManager = TmuxManagerImpl(sshManager)
    }

    // ---------------------------------------------------------------
    // isTmuxAvailable
    // ---------------------------------------------------------------

    @Test
    fun `isTmuxAvailable returns true when exit code is 0`() = runTest {
        coEvery { sshManager.executeCommand(any(), any()) } returns CommandResult(
            stdout = "/usr/bin/tmux\ntmux 3.4",
            stderr = "",
            exitCode = 0,
        )

        assertTrue(tmuxManager.isTmuxAvailable())
    }

    @Test
    fun `isTmuxAvailable returns false when exit code is non-zero`() = runTest {
        coEvery { sshManager.executeCommand(any(), any()) } returns CommandResult(
            stdout = "",
            stderr = "tmux: not found",
            exitCode = 1,
        )

        assertFalse(tmuxManager.isTmuxAvailable())
    }

    @Test
    fun `isTmuxAvailable returns false when executeCommand returns null`() = runTest {
        coEvery { sshManager.executeCommand(any(), any()) } returns null

        assertFalse(tmuxManager.isTmuxAvailable())
    }

    // ---------------------------------------------------------------
    // listSessions
    // ---------------------------------------------------------------

    @Test
    fun `listSessions parses valid output with multiple sessions`() = runTest {
        coEvery { sshManager.executeCommand(any(), any()) } returns CommandResult(
            stdout = "main|3|1|1707657600\nclaude|1|0|1707654000\n",
            stderr = "",
            exitCode = 0,
        )

        val sessions = tmuxManager.listSessions()

        assertEquals(2, sessions.size)

        assertEquals("main", sessions[0].name)
        assertEquals(3, sessions[0].windowCount)
        assertTrue(sessions[0].isAttached)
        assertEquals(1707657600L, sessions[0].lastActivity)

        assertEquals("claude", sessions[1].name)
        assertEquals(1, sessions[1].windowCount)
        assertFalse(sessions[1].isAttached)
        assertEquals(1707654000L, sessions[1].lastActivity)
    }

    @Test
    fun `listSessions parses single session`() = runTest {
        coEvery { sshManager.executeCommand(any(), any()) } returns CommandResult(
            stdout = "dev|2|0|1707650000\n",
            stderr = "",
            exitCode = 0,
        )

        val sessions = tmuxManager.listSessions()

        assertEquals(1, sessions.size)
        assertEquals("dev", sessions[0].name)
        assertEquals(2, sessions[0].windowCount)
        assertFalse(sessions[0].isAttached)
    }

    @Test
    fun `listSessions returns empty list when executeCommand returns null`() = runTest {
        coEvery { sshManager.executeCommand(any(), any()) } returns null

        val sessions = tmuxManager.listSessions()

        assertTrue(sessions.isEmpty())
    }

    @Test
    fun `listSessions returns empty list on non-zero exit code`() = runTest {
        coEvery { sshManager.executeCommand(any(), any()) } returns CommandResult(
            stdout = "",
            stderr = "no server running on /tmp/tmux-1000/default",
            exitCode = 1,
        )

        val sessions = tmuxManager.listSessions()

        assertTrue(sessions.isEmpty())
    }

    @Test
    fun `listSessions skips malformed lines`() = runTest {
        coEvery { sshManager.executeCommand(any(), any()) } returns CommandResult(
            stdout = "valid|2|1|1707657600\nbadline\nalso|bad\n",
            stderr = "",
            exitCode = 0,
        )

        val sessions = tmuxManager.listSessions()

        assertEquals(1, sessions.size)
        assertEquals("valid", sessions[0].name)
    }

    @Test
    fun `listSessions handles empty stdout`() = runTest {
        coEvery { sshManager.executeCommand(any(), any()) } returns CommandResult(
            stdout = "",
            stderr = "",
            exitCode = 0,
        )

        val sessions = tmuxManager.listSessions()

        assertTrue(sessions.isEmpty())
    }

    @Test
    fun `listSessions handles non-numeric fields gracefully`() = runTest {
        coEvery { sshManager.executeCommand(any(), any()) } returns CommandResult(
            stdout = "test|abc|xyz|nope\n",
            stderr = "",
            exitCode = 0,
        )

        val sessions = tmuxManager.listSessions()

        // Should still parse, with defaults of 0 for invalid numbers
        assertEquals(1, sessions.size)
        assertEquals("test", sessions[0].name)
        assertEquals(0, sessions[0].windowCount)
        assertFalse(sessions[0].isAttached)
        assertEquals(0L, sessions[0].lastActivity)
    }

    // ---------------------------------------------------------------
    // getAttachCommand
    // ---------------------------------------------------------------

    @Test
    fun `getAttachCommand returns correct tmux command for simple name`() {
        val command = tmuxManager.getAttachCommand("main")
        assertEquals("tmux new-session -A -s main", command)
    }

    @Test
    fun `getAttachCommand accepts hyphens underscores and dots`() {
        assertEquals(
            "tmux new-session -A -s my-session_1.0",
            tmuxManager.getAttachCommand("my-session_1.0"),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `getAttachCommand rejects session name with spaces`() {
        tmuxManager.getAttachCommand("my session")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `getAttachCommand rejects session name with semicolons`() {
        tmuxManager.getAttachCommand("session;rm -rf /")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `getAttachCommand rejects session name with backticks`() {
        tmuxManager.getAttachCommand("session`whoami`")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `getAttachCommand rejects session name with dollar signs`() {
        tmuxManager.getAttachCommand("session\$(whoami)")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `getAttachCommand rejects empty session name`() {
        tmuxManager.getAttachCommand("")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `getAttachCommand rejects session name with pipe`() {
        tmuxManager.getAttachCommand("session|cat /etc/passwd")
    }

    // ---------------------------------------------------------------
    // killSession
    // ---------------------------------------------------------------

    @Test
    fun `killSession calls executeCommand with correct command`() = runTest {
        coEvery { sshManager.executeCommand(any(), any()) } returns CommandResult(
            stdout = "",
            stderr = "",
            exitCode = 0,
        )

        tmuxManager.killSession("mySession")

        coVerify { sshManager.executeCommand("tmux kill-session -t mySession", any()) }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `killSession rejects invalid session name`() = runTest {
        tmuxManager.killSession("bad;name")
    }

    @Test
    fun `killSession handles null result gracefully`() = runTest {
        coEvery { sshManager.executeCommand(any(), any()) } returns null

        // Should not throw
        tmuxManager.killSession("orphan")
    }

    @Test
    fun `killSession handles non-zero exit code without throwing`() = runTest {
        coEvery { sshManager.executeCommand(any(), any()) } returns CommandResult(
            stdout = "",
            stderr = "session not found: ghost",
            exitCode = 1,
        )

        // Should not throw
        tmuxManager.killSession("ghost")
    }
}
