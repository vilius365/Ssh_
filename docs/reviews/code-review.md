# Code Review: Android SSH Terminal App

**Reviewer**: Code Reviewer Agent
**Date**: 2026-02-11
**Scope**: Full codebase review - 40 Kotlin source files under `app/src/main/java/com/remoteclaude/app/`

---

## Critical Issues (Must Fix Before Shipping)

### C1. `TerminalSessionManager` is Singleton but `SshManager` is also Singleton -- Only One Connection at a Time

**Files**: `TerminalSessionManagerImpl.kt:14-16`, `SshManagerImpl.kt:27`, `AppModule.kt:70-71`

The `TerminalSessionManager` stores bridges keyed by `profileId` (implying multi-connection support), but `SshManager` is a `@Singleton` with a single `connection`/`shellSession`. If the user opens terminal for profile A, then navigates back and opens profile B, `SshManagerImpl.connect()` calls `closeInternal()` first -- killing profile A's connection. The bridge for profile A still exists in the `bridges` map, but its SSH streams are dead.

**Impact**: Silent data loss / broken sessions when switching between connections.

**Suggested Fix**: Either (a) scope `SshManager` per connection (factory pattern) so each bridge gets its own SSH connection, or (b) remove the multi-bridge `Map<Long, TerminalBridgeImpl>` design and only allow one active connection at a time (making the UI enforce this).

```kotlin
// Option (a): Factory approach
interface SshManagerFactory {
    fun create(): SshManager
}

// Then in TerminalSessionManagerImpl:
fun getOrCreateBridge(profileId: Long): TerminalBridge {
    return bridges.getOrPut(profileId) {
        val ssh = sshManagerFactory.create()
        TerminalBridgeImpl(ssh)
    }
}
```

---

### C2. `TerminalBridgeImpl` Leaks its CoroutineScope -- Never Cancelled

**File**: `TerminalBridgeImpl.kt:43`

```kotlin
private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
```

This scope is never cancelled. When `detach()` is called, `readJob` is cancelled, but the parent scope's `SupervisorJob` remains active forever. The `resize()` method also launches coroutines on this scope (`scope.launch { sshManager.resizeTerminal(...) }`). Over time, if bridges are created and destroyed, these orphaned scopes accumulate.

**Suggested Fix**: Cancel the scope in `detach()` or add an explicit `close()`/`destroy()` method:

```kotlin
fun detach() {
    stop()
    sshStdout = null
    sshStdin = null
    scope.cancel() // Clean up the scope
}
```

---

### C3. `KeyManagerViewModel.deleteKey()` Never Actually Deletes the Key

**File**: `KeyManagerViewModel.kt:39-43`

```kotlin
fun deleteKey(keyId: String) {
    viewModelScope.launch {
        // Reload after deletion (actual deletion would be in the key manager implementation)
        loadKeys()
    }
}
```

The method only reloads the list without calling `keyStorageManager.deleteKey(keyId)`. Keys will appear to survive deletion because the underlying storage is never modified.

**Suggested Fix**:
```kotlin
fun deleteKey(keyId: String) {
    viewModelScope.launch {
        sshKeyManager.deleteKey(keyId) // Actually need KeyStorageManager or add to SshKeyManager interface
        loadKeys()
    }
}
```

Note: `SshKeyManager` interface doesn't expose `deleteKey()`. Either add it to the interface or inject `KeyStorageManager` directly into `KeyManagerViewModel`.

---

### C4. `ConnectionEditorViewModel.testConnection()` Mutates Shared `SshManager` Singleton

**File**: `ConnectionEditorViewModel.kt:161-204`

The test connection feature calls `sshManager.connect()` then `sshManager.disconnect()` on the shared singleton `SshManager`. If the user has an active terminal session in the background, calling `connect()` will destroy it (because `SshManagerImpl.connect()` calls `closeInternal()` first).

**Impact**: Testing a connection from the editor screen kills any active SSH session.

**Suggested Fix**: Use a separate, non-singleton `SshManager` instance for test connections, or use a low-level socket/auth test that doesn't go through the shared connection.

---

### C5. `executeCommand()` Can Deadlock on Large Output

**File**: `SshManagerImpl.kt:130-132`

```kotlin
val stdoutText = execSession.stdout.bufferedReader().readText()
val stderrText = execSession.stderr.bufferedReader().readText()
```

Both `readText()` calls are blocking and sequential. If the command produces enough output to fill the stdout pipe buffer AND also writes to stderr, `stdout.readText()` will never return because the remote side is blocked waiting for the stderr buffer to drain. This is a classic pipe deadlock.

**Suggested Fix**: Read stdout and stderr concurrently:
```kotlin
val stdoutDeferred = async { execSession.stdout.bufferedReader().readText() }
val stderrDeferred = async { execSession.stderr.bufferedReader().readText() }
val stdoutText = stdoutDeferred.await()
val stderrText = stderrDeferred.await()
```

---

### C6. Race Condition in `TerminalBridgeImpl.emitState()`

**File**: `TerminalBridgeImpl.kt:287-301`

```kotlin
private fun emitState() {
    val emu = emulator ?: return    // <-- reads emulator outside lock
    synchronized(emulatorLock) {     // <-- then acquires lock
        _terminalState.value = TerminalState(
            ...
            emulator = emu,
        )
    }
}
```

The `emulator` field is read outside the `synchronized` block, then used inside it. Between the null check and the lock acquisition, `stop()` could set `emulator = null`, causing the state to reference a destroyed emulator. The callback from `readLoop()` can call `emitState()` concurrently with `stop()`.

**Suggested Fix**:
```kotlin
private fun emitState() {
    synchronized(emulatorLock) {
        val emu = emulator ?: return
        _terminalState.value = TerminalState(
            isRunning = true,
            title = currentTitle,
            columns = emu.mColumns,
            rows = emu.mRows,
            cursorRow = emu.getCursorRow(),
            cursorCol = emu.getCursorCol(),
            cursorVisible = emu.shouldCursorBeVisible(),
            emulator = emu,
        )
    }
}
```

---

## Important Issues (Should Fix)

### I1. `TofuHostKeyVerifier` Accepts All Host Keys Without Warning the User

**File**: `SshManagerImpl.kt:184-199`

The TOFU verifier blindly returns `true` for all host keys. There is no persistent known_hosts storage and no user prompt. This is a MITM vulnerability. The TODO comment acknowledges this.

**Suggested Fix**: At minimum, store accepted host keys and warn the user when a key changes. Before first shipping, implement persistent host key storage with a user confirmation dialog for new/changed keys.

---

### I2. `TerminalSessionManagerImpl.bridges` is Not Thread-Safe

**File**: `TerminalSessionManagerImpl.kt:18`

```kotlin
private val bridges = mutableMapOf<Long, TerminalBridgeImpl>()
```

The `mutableMapOf` is not thread-safe, but `getOrCreateBridge()` can be called from ViewModel coroutines on different dispatchers while `removeBridge()` and `removeAll()` may run concurrently.

**Suggested Fix**: Use `ConcurrentHashMap` or add synchronization:
```kotlin
private val bridges = ConcurrentHashMap<Long, TerminalBridgeImpl>()
```

---

### I3. `TerminalViewModel.connect()` Flow Collection Blocks Forever

**File**: `TerminalViewModel.kt:99-104`

```kotlin
termBridge.terminalState
    .combine(_uiState) { termState, uiState ->
        uiState.copy(terminalState = termState)
    }
    .collect { _uiState.value = it }
```

This `.collect {}` suspends the coroutine launched in `connect()` indefinitely. As a result, `repository.updateLastConnected(profileId)` on line 107 is **never reached** because `collect` never completes.

**Suggested Fix**: Use `launchIn(viewModelScope)` instead of `collect`, or move the `updateLastConnected` call before the collect:
```kotlin
repository.updateLastConnected(profileId)

termBridge.terminalState
    .combine(_uiState) { termState, uiState ->
        uiState.copy(terminalState = termState)
    }
    .launchIn(viewModelScope)
```

---

### I4. `TerminalViewModel.observeConnectionState()` Creates an Infinite Feedback Loop

**File**: `TerminalViewModel.kt:114-119`

```kotlin
sshManager.connectionState
    .combine(_uiState) { connState, uiState ->
        uiState.copy(connectionState = connState)
    }
    .launchIn(viewModelScope)
```

Combining `_uiState` with an external flow, then writing back to `_uiState`, creates a re-entrant emission cycle. Every time `_uiState` is updated (from any source), this combine re-triggers, which writes to `_uiState` again. While `StateFlow` deduplicates by equality (same `data class` value), this is fragile: if any other field changes simultaneously, it triggers redundant emissions.

**Suggested Fix**: Use `map` instead of `combine`, and maintain `connectionState` separately or collect it into `_uiState` without combining with `_uiState` itself:
```kotlin
sshManager.connectionState
    .onEach { connState ->
        _uiState.update { it.copy(connectionState = connState) }
    }
    .launchIn(viewModelScope)
```

---

### I5. `ConnectionRepositoryImpl.setDefault()` Has a Race Condition

**File**: `ConnectionRepositoryImpl.kt:41-44`

```kotlin
override suspend fun setDefault(id: Long) {
    dao.clearDefaults()
    val existing = dao.getById(id) ?: return
    dao.upsert(existing.copy(isDefault = true, updatedAt = System.currentTimeMillis()))
}
```

Three separate database operations without a transaction. Between `clearDefaults()` and the `upsert()`, a crash would leave no default profile. Between `getById()` and `upsert()`, another coroutine could modify the profile.

**Suggested Fix**: Wrap in a Room `@Transaction` or use `withTransaction`:
```kotlin
override suspend fun setDefault(id: Long) {
    dao.setDefaultTransactional(id) // Define @Transaction in DAO
}
```

---

### I6. `AppDatabase` Uses `fallbackToDestructiveMigration()` at Version 2

**File**: `AppModule.kt:42-49`

This means any schema change will wipe all user data (connection profiles) without warning. While acceptable for alpha, this must be replaced with proper migration before shipping.

**Suggested Fix**: Add a `Migration(1, 2)` object and remove `fallbackToDestructiveMigration()`.

---

### I7. `SshKeyManagerAdapter` Does Not Expose `deleteKey()` or `renameKey()`

**File**: `SshKeyManagerAdapter.kt:13-25`, `SshKeyManager.kt:10-33`

The `SshKeyManager` interface only has `getPrivateKeyBytes()`, `getPublicKeyString()`, and `listKeys()`. The `KeyManagerViewModel` injects `SshKeyManager` but needs delete/rename functionality. This means the KeyManager UI cannot actually delete or manage keys through the DI-provided interface.

**Suggested Fix**: Extend `SshKeyManager` with `deleteKey()` and `renameKey()`, or inject `KeyStorageManager` directly where full CRUD is needed.

---

### I8. `TerminalViewModel` tmux Commands Are Vulnerable to Injection

**File**: `TerminalViewModel.kt:204-224`

```kotlin
fun attachSession(sessionName: String) {
    sendInput("tmux attach-session -t $sessionName\n".toByteArray())
}

fun killSession(sessionName: String) {
    sshManager.executeCommand("tmux kill-session -t $sessionName")
}
```

Unlike `TmuxManagerImpl` which validates session names against `SESSION_NAME_REGEX`, the `TerminalViewModel` directly interpolates `sessionName` into shell commands without validation. A session name containing `; rm -rf /` would execute arbitrary commands.

**Suggested Fix**: Validate session names before use, or reuse `TmuxManager.getAttachCommand()`:
```kotlin
fun attachSession(sessionName: String) {
    sendInput("${tmuxManager.getAttachCommand(sessionName)}\n".toByteArray())
}
```

---

### I9. `TerminalCanvas` Catches and Silently Ignores All Exceptions

**File**: `TerminalCanvas.kt:204`

```kotlin
} catch (_: Exception) {
    // Silently skip cells that can't be read (e.g. during buffer update)
}
```

This swallows every exception type including `OutOfMemoryError`, `SecurityException`, etc. At minimum, narrow the catch to `IndexOutOfBoundsException` or whatever the actual expected exception is.

---

### I10. `TerminalBridgeImpl` Does Not Implement the `TerminalBridge` Interface for `terminalState`

**File**: `TerminalBridgeImpl.kt:33-35`, `TerminalBridge.kt:13`

`TerminalBridge` interface does not expose `terminalState: StateFlow<TerminalState>`. The `TerminalViewModel` must downcast from `TerminalBridge` to `TerminalBridgeImpl` (line 93: `as? TerminalBridgeImpl`), and `TerminalSessionManagerImpl` exposes `getBridge()` returning the concrete type. This breaks the interface abstraction.

**Suggested Fix**: Add `val terminalState: StateFlow<TerminalState>` to the `TerminalBridge` interface, and remove the downcast.

---

## Suggestions (Nice to Have)

### S1. `TerminalState.emulator` Reference in Data Class

**File**: `TerminalState.kt:12-22`

Storing a mutable `TerminalEmulator` reference inside a `data class` is risky. The `copy()` and `equals()` methods will compare by reference, and holding this reference in `StateFlow` means the emulator object is shared across threads. Consider making the emulator accessible through a separate channel rather than embedding it in the state.

---

### S2. `TerminalCanvas` Font Metrics Are Approximate

**File**: `TerminalCanvas.kt:67-68`

```kotlin
val charWidth = fontSizePx * 0.6f
val charHeight = fontSizePx * 1.2f
```

These hardcoded ratios will be inaccurate for different fonts. Consider using `Paint.measureText()` for the actual monospace character width and `Paint.FontMetrics` for the actual height.

---

### S3. `ConnectionEditorUiState.isValid` Does Not Validate IPv6

**File**: `ConnectionEditorViewModel.kt:44-48`

The `HOSTNAME_REGEX` and `IP_REGEX` only handle hostnames and IPv4. IPv6 addresses (e.g., `::1`, `[2001:db8::1]`) will fail validation.

---

### S4. Settings Are Not Applied to the Terminal

**File**: `SettingsViewModel.kt`, `TerminalViewModel.kt`

The `SettingsViewModel` stores `fontFamily`, `fontSize`, `bellMode`, `scrollbackLines` in DataStore, but the `TerminalViewModel` uses its own `fontSize` state (defaulting to 14) without reading from DataStore. Settings changes have no effect on the terminal.

---

### S5. `TerminalBridgeImpl` Should Use `Dispatchers.IO.limitedParallelism(1)` for Read Loop

**File**: `TerminalBridgeImpl.kt:43`

Using `Dispatchers.IO` directly is fine, but a single-threaded dispatcher would prevent the rare possibility of two read loops running simultaneously if `attach()` is called before a previous `readJob` fully terminates.

---

### S6. Consider Adding `@VisibleForTesting` Annotations

Several internal methods (mapping functions, parsers) would benefit from `@VisibleForTesting` annotations to make testability explicit without exposing them in the public API.

---

### S7. `TerminalViewModel.refreshSessions()` Duplicates `TmuxManagerImpl` Parsing Logic

**File**: `TerminalViewModel.kt:178-202`

The session listing and parsing logic is duplicated between `TerminalViewModel.refreshSessions()` and `TmuxManagerImpl.listSessions()`. The ViewModel should delegate to `TmuxManager.listSessions()` instead of running raw commands.

---

### S8. `ConnectionEditorScreen` SSH Key Field Click Handler

**File**: `ConnectionEditorScreen.kt:175-178`

```kotlin
modifier = Modifier
    .fillMaxWidth()
    .clickable { viewModel.showKeyPicker() },
enabled = true,
```

The `clickable` modifier is on the `OutlinedTextField` container but the field is `readOnly = true`. On some Material 3 versions, tapping the text field itself may not trigger the `clickable` because the field consumes the touch event. Consider using `interactionSource` or wrapping in a `Box(Modifier.clickable)`.

---

## Positive Notes

1. **Clean MVVM architecture**: Clear separation between data layer (Room + Repository), domain interfaces, and UI (ViewModel + Compose). The interface-based design with DI bindings is well-structured.

2. **Good use of sealed classes**: `SshConnectionState`, `BiometricResult`, `TestConnectionResult`, and `Screen` navigation routes use sealed classes/objects effectively for type-safe state representation.

3. **Security-conscious key storage**: The `KeyStorageManagerImpl` properly uses Google Tink with Android Keystore master key, zeros private key bytes in `finally` blocks, and implements secure deletion with random overwrite.

4. **Proper coroutine synchronization in SshManagerImpl**: The `Mutex` usage for protecting connection state, `withContext(Dispatchers.IO)` for blocking I/O, and `StateFlow` for observable state are well-done.

5. **Input validation in TmuxManagerImpl**: Session name validation with regex before shell interpolation prevents command injection at this layer.

6. **Biometric integration**: The `BiometricHelperImpl` correctly uses `suspendCancellableCoroutine`, handles all error codes, includes cancellation support, and falls back to device credentials.

7. **UI polish**: Auto-hiding overlays, swipe gestures, ANSI color support, pinch-to-zoom, extra keys bar -- the terminal UI is feature-rich and follows Material 3 patterns.

8. **Good DI structure**: Clean split between `AppModule` (provides) and `BindingsModule` (binds). All interfaces have proper singleton scoping where appropriate.

---

## DI Verification Summary

| Interface | Implementation | Binding | Status |
|-----------|---------------|---------|--------|
| `ConnectionRepository` | `ConnectionRepositoryImpl` | `BindingsModule.bindConnectionRepository` | OK |
| `SshManager` | `SshManagerImpl` | `BindingsModule.bindSshManager` | OK |
| `TmuxManager` | `TmuxManagerImpl` | `BindingsModule.bindTmuxManager` | OK |
| `KeyStorageManager` | `KeyStorageManagerImpl` | `BindingsModule.bindKeyStorageManager` | OK |
| `SshKeyManager` | `SshKeyManagerAdapter` | `BindingsModule.bindSshKeyManager` | OK |
| `BiometricHelper` | `BiometricHelperImpl` | `BindingsModule.bindBiometricHelper` | OK |
| `TerminalSessionManager` | `TerminalSessionManagerImpl` | `BindingsModule.bindTerminalSessionManager` | OK |
| `AppDatabase` | Room builder | `AppModule.provideAppDatabase` | OK |
| `ConnectionProfileDao` | From AppDatabase | `AppModule.provideConnectionProfileDao` | OK |
| `DataStore<Preferences>` | preferencesDataStore | `AppModule.provideDataStore` | OK |

All bindings match implementations. All `@Inject constructor` parameters are satisfiable by the DI graph.

## Navigation Verification

| Route | Screen Composable | Arguments | Status |
|-------|-------------------|-----------|--------|
| `connection_list` | `ConnectionListScreen` | None | OK |
| `connection_editor/{profileId}` | `ConnectionEditorScreen` | `profileId: Long` | OK |
| `terminal/{profileId}` | `TerminalScreen` | `profileId: Long` | OK |
| `settings` | `SettingsScreen` | None | OK |
| `key_manager` | `KeyManagerScreen` | None | OK |

All routes match their composable destinations. Navigation arguments are correctly typed as `NavType.LongType`.

---

## Issue Count Summary

| Severity | Count |
|----------|-------|
| Critical | 6 |
| Important | 10 |
| Suggestions | 8 |
