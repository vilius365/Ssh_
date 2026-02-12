package com.remoteclaude.app.ssh

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SshConnectionStateTest {

    // ---------------------------------------------------------------
    // Sealed class hierarchy
    // ---------------------------------------------------------------

    @Test
    fun `Disconnected is a subclass of SshConnectionState`() {
        val state: SshConnectionState = SshConnectionState.Disconnected
        assertTrue(state is SshConnectionState.Disconnected)
    }

    @Test
    fun `Connecting is a subclass of SshConnectionState`() {
        val state: SshConnectionState = SshConnectionState.Connecting
        assertTrue(state is SshConnectionState.Connecting)
    }

    @Test
    fun `Connected holds hostname port and username`() {
        val state = SshConnectionState.Connected(
            hostname = "server.example.com",
            port = 2222,
            username = "admin",
        )

        assertTrue(state is SshConnectionState)
        assertEquals("server.example.com", state.hostname)
        assertEquals(2222, state.port)
        assertEquals("admin", state.username)
    }

    @Test
    fun `Error holds message and cause`() {
        val cause = RuntimeException("connection refused")
        val state = SshConnectionState.Error(
            message = "Failed to connect",
            cause = cause,
        )

        assertTrue(state is SshConnectionState)
        assertEquals("Failed to connect", state.message)
        assertEquals(cause, state.cause)
        assertEquals("connection refused", state.cause?.message)
    }

    @Test
    fun `Error cause is optional and defaults to null`() {
        val state = SshConnectionState.Error(message = "timeout")

        assertEquals("timeout", state.message)
        assertNull(state.cause)
    }

    // ---------------------------------------------------------------
    // Exhaustive when expression
    // ---------------------------------------------------------------

    @Test
    fun `when expression covers all states`() {
        val states = listOf(
            SshConnectionState.Disconnected,
            SshConnectionState.Connecting,
            SshConnectionState.Connected("host", 22, "user"),
            SshConnectionState.Error("err"),
        )

        for (state in states) {
            val label = when (state) {
                is SshConnectionState.Disconnected -> "disconnected"
                is SshConnectionState.Connecting -> "connecting"
                is SshConnectionState.Connected -> "connected"
                is SshConnectionState.Error -> "error"
            }
            assertTrue(label.isNotBlank())
        }
    }

    // ---------------------------------------------------------------
    // Data class equality
    // ---------------------------------------------------------------

    @Test
    fun `Connected data class equality works correctly`() {
        val a = SshConnectionState.Connected("host", 22, "user")
        val b = SshConnectionState.Connected("host", 22, "user")
        val c = SshConnectionState.Connected("other", 22, "user")

        assertEquals(a, b)
        assertTrue(a != c)
    }

    @Test
    fun `Error data class equality includes message and cause`() {
        val cause = RuntimeException("reason")
        val a = SshConnectionState.Error("msg", cause)
        val b = SshConnectionState.Error("msg", cause)
        val c = SshConnectionState.Error("different", cause)

        assertEquals(a, b)
        assertTrue(a != c)
    }

    @Test
    fun `Disconnected and Connecting are singletons`() {
        assertTrue(SshConnectionState.Disconnected === SshConnectionState.Disconnected)
        assertTrue(SshConnectionState.Connecting === SshConnectionState.Connecting)
    }
}
