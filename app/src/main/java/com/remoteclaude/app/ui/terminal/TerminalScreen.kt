package com.remoteclaude.app.ui.terminal

import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.remoteclaude.app.R
import com.remoteclaude.app.ssh.SshConnectionState
import com.remoteclaude.app.ui.theme.DarkSurface
import kotlinx.coroutines.delay

/**
 * Full-screen terminal composable. The core screen where users interact with
 * their remote SSH session. Owns 100% of available screen space with auto-hiding
 * overlays for status, session management, and font size feedback.
 */
@Composable
fun TerminalScreen(
    onNavigateBack: () -> Unit,
    viewModel: TerminalViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Modifier key state (CTRL, ALT toggles)
    var activeModifiers by remember { mutableStateOf(emptySet<TerminalModifier>()) }

    // Status strip and mini-FAB auto-hide
    var showOverlays by remember { mutableStateOf(true) }
    LaunchedEffect(showOverlays) {
        if (showOverlays) {
            delay(3_000)
            showOverlays = false
        }
    }

    // Font size overlay auto-hide
    LaunchedEffect(uiState.showFontSizeOverlay) {
        if (uiState.showFontSizeOverlay) {
            delay(1_000)
            viewModel.hideFontSizeOverlay()
        }
    }

    // Check if keyboard is visible
    val imeInsets = WindowInsets.ime
    val density = LocalDensity.current
    val imeVisible = imeInsets.getBottom(density) > 0

    val keyboardController = LocalSoftwareKeyboardController.current
    val inputFocusRequester = remember { FocusRequester() }
    // Let text accumulate naturally so the IME stays connected.
    // Track how many characters we've already forwarded to the terminal.
    var textFieldValue by remember { mutableStateOf(TextFieldValue("")) }
    var sentLength by remember { mutableIntStateOf(0) }

    // Auto-focus the input field when connected (enables hardware keyboard on emulator)
    LaunchedEffect(uiState.connectionState) {
        if (uiState.connectionState is SshConnectionState.Connected) {
            inputFocusRequester.requestFocus()
        }
    }

    // Handle back button
    BackHandler {
        viewModel.showDisconnectDialog()
    }

    // Scrollback state: 0 = at bottom (live), >0 = scrolled into history
    var scrollOffset by remember { mutableIntStateOf(0) }
    var scrollAccumulator by remember { mutableFloatStateOf(0f) }

    /** Send raw bytes through the modifier pipeline to the SSH session. */
    fun sendTerminalInput(data: ByteArray) {
        val modifiedData = applyModifiers(data, activeModifiers)
        viewModel.sendInput(modifiedData)
        activeModifiers = emptySet()
        // Snap to bottom when user types
        scrollOffset = 0
        scrollAccumulator = 0f
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkSurface)
            .windowInsetsPadding(WindowInsets.statusBars)
            .imePadding()
            .onKeyEvent { event ->
                // Volume key font sizing
                if (event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                    when (event.nativeKeyEvent.keyCode) {
                        KeyEvent.KEYCODE_VOLUME_UP -> {
                            viewModel.increaseFontSize()
                            true
                        }
                        KeyEvent.KEYCODE_VOLUME_DOWN -> {
                            viewModel.decreaseFontSize()
                            true
                        }
                        else -> false
                    }
                } else {
                    false
                }
            },
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Track terminal container size for dynamic column/row calculation
            var terminalSize by remember { mutableStateOf(IntSize.Zero) }
            val fontSizePx = with(density) { uiState.fontSize.sp.toPx() }
            // Measure actual monospace advance to match TerminalCanvas rendering
            val charWidthPx = remember(fontSizePx) {
                android.graphics.Paint().apply {
                    typeface = android.graphics.Typeface.MONOSPACE
                    textSize = fontSizePx
                }.measureText("M")
            }
            val charHeightPx = fontSizePx * 1.2f
            LaunchedEffect(terminalSize, uiState.fontSize) {
                if (terminalSize.width > 0 && terminalSize.height > 0) {
                    val cols = (terminalSize.width / charWidthPx).toInt()
                    val rows = (terminalSize.height / charHeightPx).toInt()
                    if (cols > 0 && rows > 0) {
                        viewModel.resizeTerminal(cols, rows)
                    }
                }
            }

            // Terminal canvas takes all available space.
            // Single pointerInput handles tap, scroll (pan.y), and pinch-to-zoom
            // in one block to avoid gesture conflicts between separate detectors.
            Box(modifier = Modifier
                .weight(1f)
                .padding(horizontal = 4.dp)
                .onSizeChanged { terminalSize = it }
                .pointerInput(Unit) {
                    detectTapGestures {
                        // Tap anywhere to focus hidden input & show keyboard
                        inputFocusRequester.requestFocus()
                        keyboardController?.show()
                        showOverlays = true
                    }
                }
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        // Scroll: pan.y movement
                        if (pan.y != 0f) {
                            val maxScroll = uiState.terminalState.activeTranscriptRows
                            scrollAccumulator += pan.y
                            val lineDelta = (scrollAccumulator / charHeightPx).toInt()
                            if (lineDelta != 0) {
                                scrollOffset = (scrollOffset + lineDelta).coerceIn(0, maxScroll)
                                scrollAccumulator -= lineDelta * charHeightPx
                            }
                        }
                        // Pinch-to-zoom: adjust font size
                        if (zoom != 1f) {
                            val currentSize = uiState.fontSize
                            val newSize = (currentSize * zoom).toInt().coerceIn(8, 24)
                            if (newSize != currentSize) {
                                viewModel.setFontSize(newSize)
                            }
                        }
                    }
                }
            ) {
                // Canvas renders the terminal (no gesture handling)
                TerminalCanvas(
                    terminalState = uiState.terminalState,
                    fontSize = uiState.fontSize.sp,
                    scrollOffset = scrollOffset,
                    onInput = { data -> sendTerminalInput(data) },
                    onFontSizeChange = { viewModel.setFontSize(it) },
                    onTap = { showOverlays = true },
                )

                // Zero-size hidden input field for IME connection only.
                // Kept tiny to prevent the system from rendering a visible
                // cursor/handle on physical devices (the old fullMaxSize()
                // approach caused a phantom cursor at the top-left corner).
                BasicTextField(
                    value = textFieldValue,
                    onValueChange = { newValue ->
                        val newText = newValue.text
                        val newLen = newText.length
                        if (newLen > sentLength) {
                            // New characters typed — send only the unsent portion
                            val added = newText.substring(sentLength)
                            // Convert newlines to carriage return — terminals expect \r for Enter
                            val terminalBytes = added.replace('\n', '\r')
                                .toByteArray(Charsets.UTF_8)
                            sendTerminalInput(terminalBytes)
                        } else if (newLen < sentLength) {
                            // Characters deleted (backspace)
                            val deletedCount = sentLength - newLen
                            repeat(deletedCount) {
                                sendTerminalInput(byteArrayOf(0x7F))
                            }
                        }
                        sentLength = newLen
                        // Accept the IME value as-is (don't reset — that kills the IME)
                        textFieldValue = newValue
                        // Periodically trim to avoid unbounded growth
                        if (newLen > 500) {
                            textFieldValue = TextFieldValue("")
                            sentLength = 0
                        }
                    },
                    modifier = Modifier
                        .size(1.dp)
                        .alpha(0f)
                        .focusRequester(inputFocusRequester)
                        .onPreviewKeyEvent { event ->
                            // Intercept hardware key events BEFORE BasicTextField handles them
                            if (event.type == KeyEventType.KeyDown) {
                                val native = event.nativeKeyEvent
                                when (native.keyCode) {
                                    KeyEvent.KEYCODE_ENTER -> {
                                        sendTerminalInput(byteArrayOf(0x0D))
                                        true
                                    }
                                    KeyEvent.KEYCODE_DEL -> {
                                        sendTerminalInput(byteArrayOf(0x7F))
                                        true
                                    }
                                    KeyEvent.KEYCODE_TAB -> {
                                        sendTerminalInput(byteArrayOf(0x09))
                                        true
                                    }
                                    KeyEvent.KEYCODE_ESCAPE -> {
                                        sendTerminalInput(byteArrayOf(0x1B))
                                        true
                                    }
                                    KeyEvent.KEYCODE_DPAD_UP -> {
                                        sendTerminalInput(byteArrayOf(0x1B, 0x5B, 0x41))
                                        true
                                    }
                                    KeyEvent.KEYCODE_DPAD_DOWN -> {
                                        sendTerminalInput(byteArrayOf(0x1B, 0x5B, 0x42))
                                        true
                                    }
                                    KeyEvent.KEYCODE_DPAD_RIGHT -> {
                                        sendTerminalInput(byteArrayOf(0x1B, 0x5B, 0x43))
                                        true
                                    }
                                    KeyEvent.KEYCODE_DPAD_LEFT -> {
                                        sendTerminalInput(byteArrayOf(0x1B, 0x5B, 0x44))
                                        true
                                    }
                                    else -> {
                                        // Hardware keyboard: forward printable characters
                                        val unicodeChar = native.unicodeChar
                                        if (unicodeChar != 0) {
                                            val chars = String(Character.toChars(unicodeChar))
                                            sendTerminalInput(chars.toByteArray(Charsets.UTF_8))
                                            true
                                        } else {
                                            false
                                        }
                                    }
                                }
                            } else false
                        },
                    textStyle = TextStyle(color = Color.Transparent, fontSize = 1.sp),
                    cursorBrush = SolidColor(Color.Transparent),
                    keyboardOptions = KeyboardOptions(
                        autoCorrectEnabled = false,
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.None,
                    ),
                )

                // Font size overlay (centered)
                if (uiState.showFontSizeOverlay) {
                    Box(modifier = Modifier.align(Alignment.Center)) {
                        FontSizeOverlay(fontSize = uiState.fontSize)
                    }
                }

                // Mini-FAB (bottom-right, auto-hiding)
                if (showOverlays && !imeVisible) {
                    SmallFloatingActionButton(
                        onClick = { viewModel.showSessionManager() },
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(16.dp)
                            .alpha(0.7f),
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = stringResource(R.string.open_sessions),
                        )
                    }
                }

                // Floating extra keys — right edge tab that expands on tap
                ExtraKeysBar(
                    onKeyPress = { data -> sendTerminalInput(data) },
                    activeModifiers = activeModifiers,
                    onModifierToggle = { mod ->
                        activeModifiers = if (mod in activeModifiers) {
                            activeModifiers - mod
                        } else {
                            activeModifiers + mod
                        }
                    },
                    modifier = Modifier.align(Alignment.CenterEnd),
                )

                // Connection state overlay (Connecting / Disconnected only — Error uses dialog)
                val connectionState = uiState.connectionState
                if (connectionState is SshConnectionState.Connecting ||
                    connectionState is SshConnectionState.Disconnected
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable {
                                if (connectionState is SshConnectionState.Disconnected) {
                                    viewModel.reconnect()
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = when (connectionState) {
                                    is SshConnectionState.Connecting -> stringResource(R.string.connecting)
                                    is SshConnectionState.Disconnected -> stringResource(R.string.disconnected)
                                    else -> ""
                                },
                                color = when (connectionState) {
                                    is SshConnectionState.Connecting -> MaterialTheme.colorScheme.primary
                                    else -> MaterialTheme.colorScheme.error
                                },
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            if (connectionState is SshConnectionState.Disconnected) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = stringResource(R.string.reconnect),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Session Manager bottom sheet
    if (uiState.showSessionManager) {
        SessionManagerSheet(
            sessions = uiState.sessions,
            currentSessionName = uiState.currentSessionName,
            serverName = uiState.profileNickname,
            onAttach = viewModel::attachSession,
            onDetach = { viewModel.detachSession() },
            onKill = viewModel::killSession,
            onNewSession = { viewModel.createNewSession() },
            onDismiss = { viewModel.hideSessionManager() },
        )
    }

    // Connection error dialog
    val connectionState = uiState.connectionState
    if (connectionState is SshConnectionState.Error) {
        ConnectionErrorDialog(
            errorMessage = connectionState.message,
            hostDiagnostic = uiState.hostDiagnostic,
            isNetworkError = connectionState.cause is java.io.IOException,
            onRetry = { viewModel.reconnect() },
            onGoBack = onNavigateBack,
        )
    }

    // Disconnect dialog
    if (uiState.showDisconnectDialog) {
        DisconnectDialog(
            onDisconnect = {
                viewModel.disconnect()
                onNavigateBack()
            },
            onKeepInBackground = {
                viewModel.hideDisconnectDialog()
                onNavigateBack()
            },
            onCancel = { viewModel.hideDisconnectDialog() },
        )
    }
}

/**
 * Centered overlay showing the current font size during adjustment.
 */
@Composable
private fun FontSizeOverlay(fontSize: Int) {
    Box(
        modifier = Modifier
            .background(
                MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.85f),
                MaterialTheme.shapes.medium,
            )
            .padding(horizontal = 24.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.font_size_indicator, fontSize),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * Dialog shown on back navigation from terminal. Offers disconnect, background, or cancel.
 */
@Composable
private fun DisconnectDialog(
    onDisconnect: () -> Unit,
    onKeepInBackground: () -> Unit,
    onCancel: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.disconnect_title)) },
        text = { Text(stringResource(R.string.disconnect_message)) },
        confirmButton = {
            TextButton(onClick = onKeepInBackground) {
                Text(stringResource(R.string.keep_in_background))
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onCancel) {
                    Text(stringResource(R.string.cancel))
                }
                TextButton(onClick = onDisconnect) {
                    Text(
                        text = stringResource(R.string.disconnect_action),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
    )
}

/**
 * Dialog shown when SSH connection fails. Shows the error message, optional
 * host reachability diagnostics, and Retry/Go Back actions.
 */
@Composable
private fun ConnectionErrorDialog(
    errorMessage: String,
    hostDiagnostic: HostDiagnostic?,
    isNetworkError: Boolean,
    onRetry: () -> Unit,
    onGoBack: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text(stringResource(R.string.connection_failed_title)) },
        text = {
            Column {
                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (isNetworkError) {
                    Spacer(modifier = Modifier.height(12.dp))
                    if (hostDiagnostic != null) {
                        DiagnosticRow(
                            pass = hostDiagnostic.dnsResolved,
                            passText = stringResource(R.string.dns_resolved),
                            failText = stringResource(R.string.dns_failed),
                        )
                        if (hostDiagnostic.dnsResolved) {
                            Spacer(modifier = Modifier.height(4.dp))
                            DiagnosticRow(
                                pass = hostDiagnostic.portOpen,
                                passText = stringResource(R.string.port_open, hostDiagnostic.port),
                                failText = stringResource(R.string.port_closed, hostDiagnostic.port),
                            )
                        }
                    } else {
                        Text(
                            text = stringResource(R.string.checking_host),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onRetry) {
                Text(stringResource(R.string.retry))
            }
        },
        dismissButton = {
            TextButton(onClick = onGoBack) {
                Text(stringResource(R.string.go_back))
            }
        },
    )
}

@Composable
private fun DiagnosticRow(pass: Boolean, passText: String, failText: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = if (pass) "\u2713" else "\u2717",
            color = if (pass) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(modifier = Modifier.padding(start = 8.dp))
        Text(
            text = if (pass) passText else failText,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Apply active CTRL/ALT modifiers to the input data.
 * CTRL: Masks the byte with 0x1F (standard control character mapping).
 * ALT: Prepends ESC (0x1B) before the data.
 */
private fun applyModifiers(data: ByteArray, modifiers: Set<TerminalModifier>): ByteArray {
    var result = data

    if (TerminalModifier.CTRL in modifiers && result.isNotEmpty()) {
        // CTRL modifier: convert first byte to control character
        result = result.clone()
        val firstByte = result[0].toInt() and 0xFF
        if (firstByte in 0x61..0x7A) {
            // a-z -> Ctrl+A through Ctrl+Z
            result[0] = (firstByte - 0x60).toByte()
        } else if (firstByte in 0x41..0x5A) {
            // A-Z -> Ctrl+A through Ctrl+Z
            result[0] = (firstByte - 0x40).toByte()
        }
    }

    if (TerminalModifier.ALT in modifiers) {
        // ALT modifier: prepend ESC
        result = byteArrayOf(0x1B) + result
    }

    return result
}
