# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

PocketSSH is an Android SSH terminal emulator app built with Jetpack Compose. It connects to remote servers via SSH, renders terminal output using the Termux terminal emulator library, and supports tmux session management.

## Build & Test Commands

```bash
# Build and install on connected device/emulator
./gradlew installDebug

# Clean build (use when cached APK doesn't reflect changes)
./gradlew clean installDebug

# Run all unit tests
./gradlew testDebugUnitTest

# Run a single test class
./gradlew testDebugUnitTest --tests "com.pocketssh.app.data.repository.ConnectionRepositoryImplTest"

# Run release build (minified, shrunk)
./gradlew assembleRelease
```

- **Min SDK**: 29, **Target/Compile SDK**: 35, **Java**: 17
- **Kotlin**: 2.1.0, **AGP**: 8.7.3, **Compose BOM**: 2024.12.01
- Debug builds use `.debug` application ID suffix

## Architecture

### Data Flow: SSH Connection to Screen

```
User selects profile → TerminalViewModel
  → SshManager.connect() [trilead-ssh2 via ConnectBot sshlib]
  → TerminalSessionManager.getOrCreateBridge(profileId)
  → TerminalBridge.attach(stdout, stdin, cols, rows)
    → read coroutine: SSH stdout → TerminalEmulator.append()
    → write coroutine: user input → SSH stdin
  → TerminalState (StateFlow) → TerminalCanvas (Compose Canvas)
```

### Key Abstractions (all bound via Hilt in `di/AppModule.kt`)

| Interface | Impl | Scope | Purpose |
|-----------|------|-------|---------|
| `SshManager` | `SshManagerImpl` | Singleton | SSH connection lifecycle, PTY, streams |
| `TerminalBridge` | `TerminalBridgeImpl` | Per-profile | Bridges SSH I/O ↔ Termux TerminalEmulator |
| `TerminalSessionManager` | `TerminalSessionManagerImpl` | Singleton | Factory for bridges, keyed by profileId |
| `ConnectionRepository` | `ConnectionRepositoryImpl` | Singleton | Room-backed CRUD for SSH profiles |
| `KeyStorageManager` | `KeyStorageManagerImpl` | Singleton | SSH key gen/import/storage (Android Keystore + AES-256-GCM) |
| `TmuxManager` | `TmuxManagerImpl` | Singleton | Remote tmux session queries via `SshManager.executeCommand()` |

### Package Layout (`app/src/main/java/com/pocketssh/app/`)

- `data/` - Room database (`claude_terminal.db`, version 2), entities, DAOs, repository
- `di/` - Hilt modules (`@Provides` for Room/DataStore, `@Binds` for all interfaces)
- `security/` - SSH key management with Android Keystore encryption, biometric gating
- `ssh/` - ConnectBot sshlib wrapper, connection state machine (`SshConnectionState` sealed class)
- `terminal/` - Termux TerminalEmulator bridge, state flow, session management
- `session/` - Tmux session listing/attach/kill via SSH command execution
- `ui/` - Pure Compose UI (no XML layouts), Material 3

### Navigation (5 screens in `ui/navigation/NavGraph.kt`)

ConnectionList (start) → ConnectionEditor(profileId) | Terminal(profileId) | Settings → KeyManager

### Terminal Rendering (`ui/terminal/TerminalCanvas.kt`)

- Compose Canvas draws directly from `TerminalEmulator.screen` buffer
- Character cell sizing uses `Paint.measureText("M")` for actual monospace advance width
- Text drawn in color-runs (batched `drawText` per consecutive same-color chars)
- Row height: `fontSizePx * 1.2f`, baseline at `charHeight * 0.8f`
- Column/row count dynamically calculated from container size in `TerminalScreen.kt`

## External Libraries

- **ConnectBot sshlib** (`org.connectbot:sshlib:2.2.25`) - SSH transport (trilead-ssh2 fork). Uses `com.trilead.ssh2.Connection`, `Session`. PTY type: `xterm-256color`.
- **Termux terminal-emulator** (`com.github.termux.termux-app:terminal-emulator:v0.118.1`) - ANSI terminal state machine. JitPack dependency. `TerminalEmulator` is NOT thread-safe; access guarded by `emulatorLock` in `TerminalBridgeImpl`.

## Known Architectural Limitations

- **Single SSH connection**: `SshManager` is a singleton — only one active SSH connection at a time. `TerminalSessionManager` stores multiple bridges but switching connections silently drops the previous one.
- **TerminalBridgeImpl scope**: The internal `CoroutineScope` is not cancelled on detach — potential memory leak on repeated attach/detach cycles.

## Testing

Tests in `app/src/test/` use JUnit 4 + MockK + kotlinx-coroutines-test (`runTest`). Key test files:
- `ConnectionRepositoryImplTest` - Entity↔domain mapping, CRUD
- `TmuxManagerImplTest` - Session parsing, name validation
- `TerminalBridgeImplTest` - Bridge lifecycle
- `SshConnectionStateTest` - State transitions
- `ConnectionEditorViewModelTest` - ViewModel logic

## Documentation

Design research and review documents are in `docs/`:
- `docs/research/` - SSH library evaluation, UI/UX design decisions, security architecture
- `docs/reviews/` - Code review findings, QA report, security review
