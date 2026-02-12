package com.pocketssh.app.ui.connections

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [ConnectionEditorUiState] validation logic.
 *
 * The ViewModel itself requires Hilt and SavedStateHandle which need an
 * Android instrumented test environment. Here we test the pure validation
 * logic which is the most critical part for correctness.
 */
class ConnectionEditorViewModelTest {

    // ---------------------------------------------------------------
    // isValid
    // ---------------------------------------------------------------

    @Test
    fun `valid form with hostname is valid`() {
        val state = ConnectionEditorUiState(
            nickname = "My Server",
            hostname = "server.example.com",
            port = "22",
            username = "admin",
        )

        assertTrue(state.isValid)
    }

    @Test
    fun `valid form with IP address is valid`() {
        val state = ConnectionEditorUiState(
            nickname = "Local",
            hostname = "192.168.1.100",
            port = "22",
            username = "root",
        )

        assertTrue(state.isValid)
    }

    @Test
    fun `blank nickname is invalid`() {
        val state = ConnectionEditorUiState(
            nickname = "",
            hostname = "server.example.com",
            port = "22",
            username = "admin",
        )

        assertFalse(state.isValid)
    }

    @Test
    fun `whitespace-only nickname is invalid`() {
        val state = ConnectionEditorUiState(
            nickname = "   ",
            hostname = "server.example.com",
            port = "22",
            username = "admin",
        )

        assertFalse(state.isValid)
    }

    @Test
    fun `blank hostname is invalid`() {
        val state = ConnectionEditorUiState(
            nickname = "Test",
            hostname = "",
            port = "22",
            username = "admin",
        )

        assertFalse(state.isValid)
    }

    @Test
    fun `blank username is invalid`() {
        val state = ConnectionEditorUiState(
            nickname = "Test",
            hostname = "server.example.com",
            port = "22",
            username = "",
        )

        assertFalse(state.isValid)
    }

    // ---------------------------------------------------------------
    // Port validation
    // ---------------------------------------------------------------

    @Test
    fun `port 0 is invalid`() {
        val state = ConnectionEditorUiState(
            nickname = "Test",
            hostname = "server.example.com",
            port = "0",
            username = "admin",
        )

        assertFalse(state.isValid)
    }

    @Test
    fun `port 1 is valid`() {
        val state = ConnectionEditorUiState(
            nickname = "Test",
            hostname = "server.example.com",
            port = "1",
            username = "admin",
        )

        assertTrue(state.isValid)
    }

    @Test
    fun `port 65535 is valid`() {
        val state = ConnectionEditorUiState(
            nickname = "Test",
            hostname = "server.example.com",
            port = "65535",
            username = "admin",
        )

        assertTrue(state.isValid)
    }

    @Test
    fun `port 65536 is invalid`() {
        val state = ConnectionEditorUiState(
            nickname = "Test",
            hostname = "server.example.com",
            port = "65536",
            username = "admin",
        )

        assertFalse(state.isValid)
    }

    @Test
    fun `negative port is invalid`() {
        val state = ConnectionEditorUiState(
            nickname = "Test",
            hostname = "server.example.com",
            port = "-1",
            username = "admin",
        )

        assertFalse(state.isValid)
    }

    @Test
    fun `non-numeric port is invalid`() {
        val state = ConnectionEditorUiState(
            nickname = "Test",
            hostname = "server.example.com",
            port = "abc",
            username = "admin",
        )

        assertFalse(state.isValid)
    }

    @Test
    fun `empty port is invalid`() {
        val state = ConnectionEditorUiState(
            nickname = "Test",
            hostname = "server.example.com",
            port = "",
            username = "admin",
        )

        assertFalse(state.isValid)
    }

    // ---------------------------------------------------------------
    // Hostname validation (isValidHostname)
    // ---------------------------------------------------------------

    @Test
    fun `isValidHostname accepts simple hostname`() {
        assertTrue(ConnectionEditorUiState.isValidHostname("myserver"))
    }

    @Test
    fun `isValidHostname accepts fully qualified domain name`() {
        assertTrue(ConnectionEditorUiState.isValidHostname("server.example.com"))
    }

    @Test
    fun `isValidHostname accepts hostname with hyphens`() {
        assertTrue(ConnectionEditorUiState.isValidHostname("my-server.example.com"))
    }

    @Test
    fun `isValidHostname accepts valid IPv4 address`() {
        assertTrue(ConnectionEditorUiState.isValidHostname("192.168.1.100"))
    }

    @Test
    fun `isValidHostname accepts boundary IP 0-0-0-0`() {
        assertTrue(ConnectionEditorUiState.isValidHostname("0.0.0.0"))
    }

    @Test
    fun `isValidHostname accepts boundary IP 255-255-255-255`() {
        assertTrue(ConnectionEditorUiState.isValidHostname("255.255.255.255"))
    }

    @Test
    fun `isValidHostname rejects empty string`() {
        assertFalse(ConnectionEditorUiState.isValidHostname(""))
    }

    @Test
    fun `isValidHostname rejects hostname with spaces`() {
        assertFalse(ConnectionEditorUiState.isValidHostname("my server"))
    }

    @Test
    fun `isValidHostname rejects hostname with special characters`() {
        assertFalse(ConnectionEditorUiState.isValidHostname("server@example.com"))
    }

    @Test
    fun `isValidHostname rejects hostname starting with hyphen`() {
        assertFalse(ConnectionEditorUiState.isValidHostname("-server.example.com"))
    }

    @Test
    fun `isValidHostname rejects IP with octet over 255`() {
        assertFalse(ConnectionEditorUiState.isValidHostname("256.1.1.1"))
    }

    @Test
    fun `isValidHostname rejects whitespace-only`() {
        assertFalse(ConnectionEditorUiState.isValidHostname("   "))
    }

    // ---------------------------------------------------------------
    // ConnectionEditorUiState defaults
    // ---------------------------------------------------------------

    @Test
    fun `default state has port 22`() {
        val state = ConnectionEditorUiState()
        assertEquals("22", state.port)
    }

    @Test
    fun `default state is not editing`() {
        val state = ConnectionEditorUiState()
        assertFalse(state.isEditing)
    }

    @Test
    fun `default state has no errors`() {
        val state = ConnectionEditorUiState()
        assertNull(state.nicknameError)
        assertNull(state.hostnameError)
        assertNull(state.portError)
        assertNull(state.usernameError)
    }

    @Test
    fun `default state is not saving or testing`() {
        val state = ConnectionEditorUiState()
        assertFalse(state.isSaving)
        assertFalse(state.isTesting)
        assertNull(state.testResult)
    }

    // ---------------------------------------------------------------
    // TestConnectionResult
    // ---------------------------------------------------------------

    @Test
    fun `TestConnectionResult Success is singleton`() {
        val a: TestConnectionResult = TestConnectionResult.Success
        val b: TestConnectionResult = TestConnectionResult.Success
        assertTrue(a === b)
    }

    @Test
    fun `TestConnectionResult Failure preserves message`() {
        val result = TestConnectionResult.Failure("Connection refused")
        assertEquals("Connection refused", result.message)
    }
}
