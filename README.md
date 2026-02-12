# PocketSSH

An Android SSH terminal emulator built with Jetpack Compose.

## Features

- **SSH Connectivity** -- Connect to remote servers via SSH using Ed25519 or RSA keys
- **Terminal Emulation** -- Full ANSI terminal rendering powered by the Termux terminal-emulator library
- **Tmux Integration** -- List, attach, detach, create, and kill tmux sessions from an in-app session manager
- **SSH Key Management** -- Generate or import SSH keys, stored securely with Android Keystore (AES-256-GCM encryption)
- **Pinch-to-Zoom** -- Adjust terminal font size with pinch gestures or volume keys
- **Scrollback History** -- Scroll through terminal output history with touch gestures
- **Connection Profiles** -- Save and manage multiple server connections with Room-backed storage
- **Connection Diagnostics** -- Structured error dialog with DNS and port reachability checks on connection failure

## Tech Stack

| Layer | Technology |
|-------|-----------|
| UI | Jetpack Compose, Material 3 |
| DI | Hilt |
| SSH | ConnectBot sshlib (trilead-ssh2) |
| Terminal | Termux terminal-emulator |
| Storage | Room, DataStore |
| Language | Kotlin 2.1, Java 17 |
| Min SDK | 29 (Android 10) |

## Building

```bash
# Debug build and install
./gradlew clean installDebug

# Run unit tests
./gradlew testDebugUnitTest

# Release build
./gradlew assembleRelease
```

## Architecture

```
User selects profile -> TerminalViewModel
  -> SshManager.connect()
  -> TerminalSessionManager.getOrCreateBridge(profileId)
  -> TerminalBridge.attach(stdout, stdin, cols, rows)
    -> read coroutine: SSH stdout -> TerminalEmulator.append()
    -> write coroutine: user input -> SSH stdin
  -> TerminalState (StateFlow) -> TerminalCanvas (Compose Canvas)
```

### Navigation

ConnectionList (start) -> ConnectionEditor | Terminal | Settings -> KeyManager

## Project Structure

```
app/src/main/java/com/remoteclaude/app/
  data/       Room database, entities, DAOs, repository
  di/         Hilt modules
  security/   SSH key management with Android Keystore
  ssh/        ConnectBot sshlib wrapper, connection state machine
  terminal/   Termux TerminalEmulator bridge, state flow, session management
  session/    Tmux session listing/attach/kill
  ui/         Compose UI screens (Material 3)
```

## License

All rights reserved.
