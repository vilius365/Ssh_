package com.pocketssh.app.terminal

import com.pocketssh.app.ssh.SshManager
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class TerminalBridgeImplTest {

    private lateinit var sshManager: SshManager
    private lateinit var bridge: TerminalBridgeImpl

    @Before
    fun setUp() {
        sshManager = mockk(relaxed = true)
        bridge = TerminalBridgeImpl(sshManager)
    }

    // ---------------------------------------------------------------
    // attach
    // ---------------------------------------------------------------

    @Test
    fun `attach creates emulator with correct dimensions`() {
        val stdout = ByteArrayInputStream(ByteArray(0))
        val stdin = ByteArrayOutputStream()

        bridge.attach(stdout, stdin, 120, 40)

        val state = bridge.terminalState.value
        assertTrue(state.isRunning)
        assertEquals(120, state.columns)
        assertEquals(40, state.rows)
        assertNotNull(state.emulator)
    }

    @Test
    fun `attach creates emulator accessible via getEmulator`() {
        val stdout = ByteArrayInputStream(ByteArray(0))
        val stdin = ByteArrayOutputStream()

        bridge.attach(stdout, stdin, 80, 24)

        val emulator = bridge.getEmulator()
        assertNotNull(emulator)
        assertEquals(80, emulator!!.mColumns)
        assertEquals(24, emulator.mRows)
    }

    // ---------------------------------------------------------------
    // write
    // ---------------------------------------------------------------

    @Test
    fun `write forwards bytes to SSH stdin`() {
        val stdout = ByteArrayInputStream(ByteArray(0))
        val stdin = ByteArrayOutputStream()

        bridge.attach(stdout, stdin, 80, 24)

        val data = "ls -la\n".toByteArray()
        bridge.write(data)

        assertEquals("ls -la\n", stdin.toString())
    }

    @Test
    fun `write with no attached stream does not throw`() {
        // Not attached -- should be a no-op
        bridge.write("hello".toByteArray())
    }

    @Test
    fun `write appends multiple calls`() {
        val stdout = ByteArrayInputStream(ByteArray(0))
        val stdin = ByteArrayOutputStream()

        bridge.attach(stdout, stdin, 80, 24)

        bridge.write("cmd1\n".toByteArray())
        bridge.write("cmd2\n".toByteArray())

        assertEquals("cmd1\ncmd2\n", stdin.toString())
    }

    // ---------------------------------------------------------------
    // resize
    // ---------------------------------------------------------------

    @Test
    fun `resize updates emulator dimensions`() {
        val stdout = ByteArrayInputStream(ByteArray(0))
        val stdin = ByteArrayOutputStream()

        bridge.attach(stdout, stdin, 80, 24)

        bridge.resize(120, 40)

        val emulator = bridge.getEmulator()
        assertNotNull(emulator)
        assertEquals(120, emulator!!.mColumns)
        assertEquals(40, emulator.mRows)
    }

    @Test
    fun `resize calls sshManager resizeTerminal`() {
        val stdout = ByteArrayInputStream(ByteArray(0))
        val stdin = ByteArrayOutputStream()

        bridge.attach(stdout, stdin, 80, 24)

        bridge.resize(100, 50)

        // The resize fires a coroutine launch, so we verify with coVerify eventually
        coVerify(timeout = 1000) { sshManager.resizeTerminal(100, 50) }
    }

    @Test
    fun `resize without attach does not throw`() {
        // No emulator -- should be a no-op
        bridge.resize(120, 40)
    }

    // ---------------------------------------------------------------
    // detach
    // ---------------------------------------------------------------

    @Test
    fun `detach stops running state and clears emulator`() {
        val stdout = ByteArrayInputStream(ByteArray(0))
        val stdin = ByteArrayOutputStream()

        bridge.attach(stdout, stdin, 80, 24)
        assertTrue(bridge.terminalState.value.isRunning)
        assertNotNull(bridge.getEmulator())

        bridge.detach()

        assertFalse(bridge.terminalState.value.isRunning)
        assertNull(bridge.getEmulator())
    }

    @Test
    fun `detach when not attached does not throw`() {
        bridge.detach()

        assertFalse(bridge.terminalState.value.isRunning)
    }

    @Test
    fun `new bridge after detach creates fresh emulator`() {
        // Fix C2: After detach(), scope is cancelled so a new bridge must be created
        // (matches production pattern from C1 fix: new bridge per connection)
        val stdout1 = ByteArrayInputStream(ByteArray(0))
        val stdin1 = ByteArrayOutputStream()

        bridge.attach(stdout1, stdin1, 80, 24)
        bridge.detach()

        val newBridge = TerminalBridgeImpl(sshManager)
        val stdout2 = ByteArrayInputStream(ByteArray(0))
        val stdin2 = ByteArrayOutputStream()

        newBridge.attach(stdout2, stdin2, 132, 43)

        val state = newBridge.terminalState.value
        assertTrue(state.isRunning)
        assertEquals(132, state.columns)
        assertEquals(43, state.rows)
    }

    // ---------------------------------------------------------------
    // terminalState initial
    // ---------------------------------------------------------------

    @Test
    fun `initial terminalState is not running`() {
        val state = bridge.terminalState.value

        assertFalse(state.isRunning)
        assertEquals(80, state.columns)
        assertEquals(24, state.rows)
        assertNull(state.emulator)
    }
}
