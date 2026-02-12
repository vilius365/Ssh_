# SSH Libraries and Terminal Emulation Research for Android

**Date**: 2026-02-11
**Purpose**: Evaluate SSH libraries, terminal emulation options, and tmux integration patterns for building an Android SSH terminal app (Kotlin/Jetpack Compose, min SDK 29).

---

## Table of Contents

1. [SSH Libraries Comparison](#1-ssh-libraries-comparison)
2. [Terminal Emulation Options](#2-terminal-emulation-options)
3. [tmux Integration Patterns](#3-tmux-integration-patterns)
4. [Existing App Architectures](#4-existing-app-architectures)
5. [Recommendations](#5-recommendations)

---

## 1. SSH Libraries Comparison

### 1.1 ConnectBot sshlib (RECOMMENDED)

| Attribute | Details |
|-----------|---------|
| **Maven** | `org.connectbot:sshlib:2.2.25` |
| **License** | BSD 3-Clause |
| **Language** | Java (99.6%) |
| **Maintenance** | Active (latest: 2025, ConnectBot 1.9.12 released 2025-04-21) |
| **Stars** | 117 (library), ConnectBot app is widely used |
| **Android-native** | Yes -- built for and battle-tested on Android |

**Key strengths:**

- **Purpose-built for Android SSH**. This is the library that powers ConnectBot, the first and most established SSH client on Android. It has years of real-world Android usage behind it.
- **Modern cryptography**: ChaCha20-Poly1305, AES-GCM, Ed25519, ECDSA, post-quantum `mlkem768x25519-sha256` (requires Java JEP-496).
- **Encrypt-then-MAC** (EtM) variants for all MACs.
- **No BouncyCastle headaches**. Uses Android's built-in crypto providers, which means no provider conflict issues that plague sshj and Apache MINA on Android.
- **Trilead SSH2 heritage** with significant modernization. The API is straightforward for shell/channel operations.

**Key concerns:**

- Smaller community than sshj or Apache MINA (it is a niche Android library).
- Documentation is sparse -- you learn by reading ConnectBot's source code.
- The API is Java-centric, not Kotlin-idiomatic (but perfectly callable from Kotlin).

**Interactive shell example (Kotlin):**

```kotlin
import com.trilead.ssh2.Connection
import com.trilead.ssh2.Session

val connection = Connection(hostname, port)
connection.connect() // Add host key verifier in production

// Key-based authentication
val keyBytes = privateKeyPem.toByteArray()
connection.authenticateWithPublicKey(username, keyBytes, passphrase)

// Open interactive session with PTY
val session: Session = connection.openSession()
session.requestPTY("xterm-256color", 80, 24, 0, 0, null)
session.startShell()

// I/O streams for terminal
val stdout: InputStream = session.stdout
val stdin: OutputStream = session.stdin
val stderr: InputStream = session.stderr
```

### 1.2 sshj (Hierynomus)

| Attribute | Details |
|-----------|---------|
| **Maven** | `com.hierynomus:sshj:0.39.0` |
| **License** | Apache 2.0 |
| **Language** | Java |
| **Maintenance** | Active (latest: February 2024) |
| **Stars** | 2,600+ |

**Key strengths:**

- Clean, modern Java API. Best API design of all the libraries.
- Comprehensive algorithm support: Ed25519, Curve25519, ChaCha20-Poly1305, AES-GCM.
- BouncyCastle now optional (v0.39.0+).
- Good PTY support with `allocateDefaultPTY()` and `allocatePTY()` for custom terminal types, columns, rows.
- Large community, well-documented, many examples.

**Key concerns:**

- **Android compatibility is problematic.** Open issue #905 documents `SSHException: no such algorithm: X25519 for provider BC` on Android. The workaround requires manually removing and re-registering BouncyCastle provider.
- Not designed for Android -- it targets desktop/server Java.
- The X25519/BouncyCastle issue is a fundamental friction point on Android where the platform ships its own (often outdated) BouncyCastle fork.
- Heavier dependency footprint.

**PTY example (from sshj source):**

```kotlin
val ssh = SSHClient()
ssh.addHostKeyVerifier(/* verifier */)
ssh.connect(hostname)
ssh.authPublickey(username, keyProvider)

val session = ssh.startSession()
session.allocatePTY("xterm-256color", 80, 24, 0, 0, mapOf())
val shell = session.startShell()

// Stream-based I/O
val stdout = shell.inputStream
val stdin = shell.outputStream
val stderr = shell.errorStream
```

### 1.3 Apache MINA SSHD

| Attribute | Details |
|-----------|---------|
| **Maven** | `org.apache.sshd:sshd-core:2.17.0` |
| **License** | Apache 2.0 |
| **Language** | Java |
| **Maintenance** | Very active (latest release: January 2026) |
| **Stars** | 1,000+ |

**Key strengths:**

- Most comprehensive SSH implementation. Both client and server.
- Actively maintained by Apache Foundation.
- Has an [official Android documentation page](https://github.com/apache/mina-sshd/blob/master/docs/android.md) with workarounds.
- Extensible architecture with pluggable I/O backends (NIO2, MINA, Netty).

**Key concerns:**

- **The project explicitly states Android is NOT a supported target**: "it is not a stated goal of this project to actively support it, mainly because of the dire lack of available R&D resources."
- Requires manual configuration on Android: `OsUtils.setAndroid()`, custom directory resolvers, BouncyCastle provider juggling.
- MINA and Netty I/O factories **untested on Android**.
- NIO2 backend works but with Android-specific workarounds.
- Heavy dependency tree -- pulls in MINA or Netty plus BouncyCastle.
- **3-4x slower** than JSch in some file transfer benchmarks (though shell usage may not be affected).
- Over-engineered for a client-only use case.

### 1.4 JSch (mwiede fork)

| Attribute | Details |
|-----------|---------|
| **Maven** | `com.github.mwiede:jsch:2.27.7` |
| **License** | BSD |
| **Language** | Java |
| **Maintenance** | Active (latest: November 2025) |

**Key strengths:**

- Drop-in replacement for the classic JSch with modern algorithms.
- Ed25519, Ed448, Curve25519, Curve448, ChaCha20-Poly1305.
- Post-quantum KEX: `mlkem768x25519-sha256`.
- Familiar API -- the most widely documented SSH library in Java.
- Lightweight.

**Key concerns:**

- No explicit Android support or testing.
- Ed25519/Ed448 requires Java 15+ or BouncyCastle -- same provider headaches on Android.
- The API is dated (original JSch design from ~2004).
- Session handling can be unintuitive.

### 1.5 Comparison Matrix

| Feature | ConnectBot sshlib | sshj | Apache MINA | JSch (mwiede) |
|---------|:-:|:-:|:-:|:-:|
| **Android-native** | YES | No | No (documented workarounds) | No |
| **Ed25519** | Yes | Yes | Yes | Yes (Java 15+ or BC) |
| **ChaCha20-Poly1305** | Yes | Yes | Yes | Yes (BC) |
| **Post-quantum KEX** | mlkem768x25519 | No | No | mlkem768x25519 |
| **BouncyCastle required** | No | Optional | Depends on algo | Depends on algo |
| **PTY support** | Yes | Yes | Yes | Yes |
| **API quality** | Decent | Excellent | Good | Dated |
| **Android BouncyCastle conflicts** | None | Known issues | Known issues | Likely |
| **Binary size impact** | Small | Medium | Large | Small |
| **Interactive shell** | Straightforward | Straightforward | Complex setup | Straightforward |

---

## 2. Terminal Emulation Options

### 2.1 Termux terminal-emulator + terminal-view (RECOMMENDED)

| Attribute | Details |
|-----------|---------|
| **Package** | `com.termux:terminal-view` (includes `terminal-emulator` as dependency) |
| **Source** | https://github.com/termux/termux-app/tree/master/terminal-emulator |
| **License** | GPLv3 (Termux app license) |
| **Version** | 0.118.0 (via JitPack) |
| **Min SDK** | 24 (compatible with our min SDK 29) |

**Architecture (14 classes in terminal-emulator, 4+ in terminal-view):**

**terminal-emulator module** (the engine):
- `TerminalEmulator.java` -- Core VT100/xterm escape sequence parser and state machine
- `TerminalBuffer.java` -- Screen buffer with scrollback
- `TerminalRow.java` -- Single row with character styling
- `TerminalSession.java` -- Session lifecycle management
- `TerminalSessionClient.java` -- Callback interface for session events
- `TerminalOutput.java` -- Output coordination
- `TerminalColorScheme.java` / `TerminalColors.java` -- 256-color support
- `TextStyle.java` -- Bold, italic, underline, colors
- `WcWidth.java` -- Unicode character width calculation (CJK, emoji)
- `KeyHandler.java` -- Keyboard input to escape sequence translation
- `ByteQueue.java` -- Byte buffer for I/O
- `JNI.java` -- Native bindings (for local process creation)
- `Logger.java` -- Logging

**terminal-view module** (the Android View):
- `TerminalView.java` -- The main Android View widget
- `TerminalRenderer.java` -- Canvas-based text rendering
- `GestureAndScaleRecognizer.java` -- Touch gestures, pinch-to-zoom
- `TerminalViewClient.java` -- View callback interface
- `support/` -- Supporting utilities
- `textselection/` -- Text selection handling

**Key strengths:**

- **The most battle-tested terminal emulator on Android.** Termux is installed on millions of devices.
- Handles VT100, xterm, xterm-256color escape sequences correctly.
- Full Unicode support including wide characters (CJK), combining characters, emoji.
- Scrollback buffer.
- Window resize (reports new size to the session, which maps to SIGWINCH over SSH).
- 256-color and true color support.
- Text selection, copy/paste.
- Pinch-to-zoom for font size.
- Hardware keyboard support with proper modifier key handling.

**Key concerns:**

- **GPLv3 license.** If the app is open-source under a GPL-compatible license, this is fine. If the app is proprietary or uses an incompatible license, this is a blocker. The app could be released under GPLv3 to comply.
- `TerminalSession` assumes a local process (uses JNI to fork/exec). For SSH, we need to **use `TerminalEmulator` directly** and feed it bytes from the SSH channel instead of a local process. This is doable but requires understanding the internal API.
- The library is designed for the View system, not Jetpack Compose. The `TerminalView` is a custom Android `View` that can be embedded in Compose using `AndroidView { }` interop.
- Native library `libtermux.so` is included but only needed for local process creation -- can be excluded for SSH-only use.

**Integration pattern for SSH:**

```kotlin
// Instead of using TerminalSession (which creates a local process),
// use TerminalEmulator directly and pipe SSH channel bytes through it.

// 1. Create the TerminalEmulator with a TerminalOutput callback
val emulator = TerminalEmulator(
    terminalOutput,  // receives bytes to send BACK to SSH (user input)
    columns,
    rows,
    scrollbackRows
)

// 2. Feed SSH stdout bytes into the emulator
fun onSshDataReceived(data: ByteArray) {
    emulator.append(data, data.size)
    // Trigger UI redraw
}

// 3. When user types, TerminalOutput callback fires with bytes to send to SSH stdin
class SshTerminalOutput : TerminalOutput {
    override fun write(data: ByteArray, offset: Int, count: Int) {
        sshChannel.outputStream.write(data, offset, count)
        sshChannel.outputStream.flush()
    }
}

// 4. Embed TerminalView in Compose
@Composable
fun TerminalScreen() {
    AndroidView(
        factory = { context ->
            TerminalView(context, null).apply {
                // Attach emulator
            }
        }
    )
}
```

### 2.2 Custom Jetpack Compose Terminal (NOT recommended)

Building a terminal emulator from scratch using Compose Canvas:

```kotlin
@Composable
fun TerminalCanvas(buffer: TerminalBuffer) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val textMeasurer = rememberTextMeasurer()
        // Draw each character cell...
        for (row in 0 until buffer.rows) {
            for (col in 0 until buffer.cols) {
                drawText(
                    textMeasurer = textMeasurer,
                    text = buffer.getChar(row, col).toString(),
                    topLeft = Offset(col * cellWidth, row * cellHeight),
                    style = TextStyle(fontFamily = FontFamily.Monospace)
                )
            }
        }
    }
}
```

**Why NOT to do this:**

- A terminal emulator must parse hundreds of escape sequences (CSI, OSC, DCS, etc.). The VT100/xterm spec is enormous.
- Correct Unicode handling (wide characters, combining marks, bidirectional text) is extremely hard.
- Scrollback buffer management, alternate screen buffer, line wrapping are all complex.
- Termux has **years** of bug fixes for edge cases. Starting from scratch means rediscovering all of them.
- Compose Canvas text rendering is slower than Android View Canvas for character-by-character rendering of a terminal grid. The `TextMeasurer` approach adds overhead per character.

**The only valid reason** to build custom would be if GPLv3 licensing is unacceptable AND no other library exists. Even then, forking/porting the Termux code (under GPLv3) to a standalone library is better than starting from zero.

### 2.3 Other Options

**Android Terminal Emulator (jackpal)**: The predecessor to Termux's terminal code. Abandoned since ~2015. Termux's version is strictly superior.

**VT100/ANSI libraries for Java**: Libraries like `jline3` or `lanterna` exist but are designed for creating TUI apps in Java, not for emulating a terminal receiving arbitrary escape sequences. Not applicable here.

---

## 3. tmux Integration Patterns

### 3.1 Basic Session Management Commands

```bash
# Check if tmux is installed
which tmux && tmux -V

# Create a named session (detached)
tmux new-session -d -s claude-session

# Attach to existing session
tmux attach-session -t claude-session

# Detach (from inside): Ctrl+B, D
# Or programmatically:
tmux detach-client -t claude-session

# List sessions
tmux list-sessions

# Kill a session
tmux kill-session -t claude-session

# Check if a specific session exists
tmux has-session -t claude-session 2>/dev/null && echo "exists"
```

### 3.2 SSH Integration Pattern

For our app, the workflow over SSH is:

```bash
# On SSH connect, check for existing session:
tmux has-session -t claude-$SESSION_ID 2>/dev/null

# If exists, attach:
tmux attach-session -t claude-$SESSION_ID

# If not, create and attach:
tmux new-session -s claude-$SESSION_ID

# One-liner that does both:
tmux new-session -A -s claude-$SESSION_ID
# -A = attach if session exists, create if it doesn't
```

**The `-A` flag is the key.** `tmux new-session -A -s <name>` is idempotent: it creates the session if it doesn't exist and attaches to it if it does. This is the single command to run after SSH connection is established.

### 3.3 tmux Control Mode

tmux control mode (`-C` flag) provides a text-based protocol for programmatic control. Instead of rendering terminal output, it sends structured notifications.

**Entering control mode:**

```bash
# Start new session in control mode
tmux -C new-session -A -s claude-session

# Or attach in control mode
tmux -C attach-session -t claude-session
```

**Protocol format:**

```
%begin 1234567890 1 1
<command output>
%end 1234567890 1 1

# Pane output notifications:
%output %0 <escaped-output-data>

# Window events:
%window-add @1
%window-close @1
%window-renamed @1 new-name
%session-renamed $1 new-name
```

**When to use control mode vs normal mode:**

| Aspect | Normal Mode | Control Mode |
|--------|-------------|--------------|
| **Simplicity** | Just pipe bytes | Must parse protocol |
| **Terminal rendering** | Server handles it | Client must handle it |
| **Multiple panes** | Rendered by tmux | Client must layout |
| **Use case** | Single terminal view | Custom multi-pane UI |
| **Recommended for us** | YES | No (adds complexity) |

**Recommendation: Use normal mode.** Control mode is designed for apps like iTerm2 that want to replicate tmux's window/pane layout natively. For our app, we just need a single terminal view that shows the tmux session. Normal mode is vastly simpler -- tmux handles all the pane rendering server-side, and we just display what it sends.

### 3.4 Detecting tmux Availability

```kotlin
// Over SSH, execute a command to check for tmux
fun checkTmuxAvailable(sshSession: Session): Boolean {
    val exec = sshSession.exec("which tmux && tmux -V")
    val output = exec.inputStream.bufferedReader().readText()
    exec.waitForCondition(ChannelCondition.EXIT_STATUS, 5000)
    return exec.exitStatus == 0
}
```

### 3.5 Window Resize (SIGWINCH)

When the Android terminal view resizes (orientation change, keyboard show/hide), we must notify tmux:

```kotlin
// After PTY is allocated, when terminal size changes:
fun onTerminalSizeChanged(cols: Int, rows: Int) {
    // SSH protocol has a window-change request
    sshSession.requestWindowChange(cols, rows, 0, 0)
    // tmux will automatically relay SIGWINCH to the process inside
}
```

The SSH protocol's `window-change` request (RFC 4254, Section 6.7) propagates through to the server PTY, which sends SIGWINCH to tmux, which resizes accordingly. This is built into all the SSH libraries.

---

## 4. Existing App Architectures

### 4.1 Termux

**Architecture**: Monolithic Android app with modular libraries.

- `terminal-emulator`: Pure Java terminal emulation engine. No Android dependencies. Parses escape sequences, manages screen buffer.
- `terminal-view`: Android View that renders the terminal buffer using Canvas. Handles touch, gestures, text selection.
- `termux-shared`: Shared constants and utilities.
- Local process execution via JNI (fork/exec).
- No SSH built-in (uses packages system to install `openssh`).

**What we can learn**: The terminal-emulator/terminal-view split is clean. The emulator is decoupled from the display and from the process source. We can feed SSH bytes in instead of local process bytes.

### 4.2 ConnectBot

**Architecture**: Traditional Android app (Activities, not Compose).

- SSH layer: `org.connectbot:sshlib` (Trilead SSH2 fork).
- Terminal emulation: Custom `vt320` emulator class (originally from JTA - Java Telnet Application).
- Rendering: Custom Android View using Canvas.
- Host database: SQLite.
- Key management: Android Keystore integration.

**What we can learn**: The sshlib is well-proven. However, their custom vt320 emulator is less complete than Termux's -- it has known issues with some escape sequences and Unicode handling. Don't copy their terminal emulator; use Termux's.

### 4.3 JuiceSSH

**Architecture**: Proprietary, closed-source.

- Uses Jetpack Compose for UI (modern approach).
- SSH implementation: Unknown (proprietary).
- Terminal: Custom implementation with theming support.
- Auth: Integrates with Android biometric authentication.
- Plugin system for extensibility.

**What we can learn**: Compose-based SSH apps are viable on Android. They've shipped it successfully. Biometric auth for SSH key access is a good UX pattern.

---

## 5. Recommendations

### 5.1 SSH Library: ConnectBot sshlib

**Use `org.connectbot:sshlib:2.2.25`.**

Rationale:
1. **It is the only library purpose-built for Android SSH.** Every other option (sshj, Apache MINA, JSch) is a Java library that happens to work on Android with workarounds.
2. **No BouncyCastle provider conflicts.** This is the #1 source of pain when using sshj or Apache MINA on Android. Android ships its own BC fork, and adding a full BC JAR causes algorithm resolution failures, class conflicts, and subtle bugs. ConnectBot sshlib avoids this entirely.
3. **Battle-tested on millions of Android devices** via ConnectBot.
4. **Modern crypto**: Ed25519, ChaCha20-Poly1305, AES-GCM, post-quantum KEX.
5. **Small footprint** -- no Netty, no MINA, no BouncyCastle dependency tree.

If the API proves insufficient for some edge case, sshj is the fallback -- but expect to spend time fighting BouncyCastle provider issues on Android.

**Dependency:**

```kotlin
// build.gradle.kts
dependencies {
    implementation("org.connectbot:sshlib:2.2.25")
}
```

### 5.2 Terminal Emulation: Termux terminal-emulator (engine only)

**Use `com.termux:terminal-emulator` from Termux, but NOT `terminal-view`.**

Rationale:
1. Most complete and tested terminal emulation engine available for Android/Java.
2. Handles all VT100/xterm escape sequences correctly.
3. Full Unicode support including wide characters.
4. 256-color and true color.
5. Scrollback buffer.

**Why skip terminal-view**: It is an Android `View`, not a Compose component. While we can wrap it with `AndroidView { }`, it is better to write a Compose-native renderer that reads from `TerminalEmulator`'s buffer. This gives us:
- Native Compose theming and animation.
- Better integration with Compose layout system.
- Control over rendering performance (we can use `drawText` on Canvas for the grid).

However, if the Compose renderer proves too complex or slow, falling back to `TerminalView` via `AndroidView { }` is perfectly acceptable as a pragmatic choice.

**License consideration**: Termux is GPLv3. Our app should be open-source under GPLv3 to comply. If that is unacceptable, the alternative is to study Termux's emulator architecture and write a clean-room implementation (significant effort) or use ConnectBot's `vt320` emulator (weaker escape sequence support).

**Dependency (via JitPack):**

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        maven { url = uri("https://jitpack.io") }
    }
}

// build.gradle.kts
dependencies {
    implementation("com.github.termux.termux-app:terminal-emulator:0.118.0")
    // Only add terminal-view if using the Android View wrapper
    // implementation("com.github.termux.termux-app:terminal-view:0.118.0")
}
```

### 5.3 tmux Integration: Normal Mode with `-A` Flag

**Use `tmux new-session -A -s <session-name>` in normal mode.**

Rationale:
1. Single idempotent command handles both create and attach.
2. Normal mode means tmux renders panes/windows server-side -- we just display the output.
3. No protocol parsing needed (unlike control mode).
4. User can use tmux keybindings naturally (split panes, switch windows, etc.).

**Session lifecycle:**

```
App opens -> SSH connect -> PTY allocate -> exec "tmux new-session -A -s claude"
App closes -> SSH disconnects -> tmux session persists on server
App reopens -> SSH connect -> PTY allocate -> exec "tmux new-session -A -s claude"
                                              (reattaches to existing session)
```

### 5.4 Architecture Overview

```
+---------------------------------------------------+
|  Android App (Kotlin / Jetpack Compose)            |
|                                                    |
|  +--------------------+  +---------------------+  |
|  | Connection Manager |  | SSH Key Manager     |  |
|  | (Profiles, UI)     |  | (Android Keystore)  |  |
|  +--------+-----------+  +----------+----------+  |
|           |                         |              |
|  +--------v-------------------------v----------+   |
|  | SSH Layer (ConnectBot sshlib)               |   |
|  | - Connection, Auth, Channel, PTY            |   |
|  +--------+------------------------------------+   |
|           | byte stream (stdin/stdout/stderr)       |
|  +--------v------------------------------------+   |
|  | tmux Command Layer                          |   |
|  | - "tmux new-session -A -s <name>"           |   |
|  | - Window resize propagation                 |   |
|  | - Session health monitoring                 |   |
|  +--------+------------------------------------+   |
|           | raw terminal bytes                      |
|  +--------v------------------------------------+   |
|  | Terminal Emulator (Termux terminal-emulator)|   |
|  | - Escape sequence parsing                   |   |
|  | - Screen buffer management                  |   |
|  | - Color, Unicode, scrollback                |   |
|  +--------+------------------------------------+   |
|           | screen state (buffer, cursor, colors)   |
|  +--------v------------------------------------+   |
|  | Terminal Renderer                           |   |
|  | Option A: Compose Canvas (preferred)        |   |
|  | Option B: Termux TerminalView via           |   |
|  |           AndroidView (fallback)            |   |
|  +---------------------------------------------+  |
+---------------------------------------------------+
```

### 5.5 Risk Mitigation

| Risk | Mitigation |
|------|------------|
| GPLv3 license of Termux | Release our app as GPLv3 open-source, or write a clean-room terminal emulator |
| ConnectBot sshlib API limitations | Fall back to sshj with BC provider workaround |
| Compose terminal rendering performance | Fall back to Termux TerminalView via AndroidView interop |
| tmux not installed on server | Detect at connect time, offer direct shell as fallback |
| SSH connection drops | Implement reconnection logic; tmux preserves session server-side |

### 5.6 Dependencies Summary

```kotlin
// build.gradle.kts
dependencies {
    // SSH
    implementation("org.connectbot:sshlib:2.2.25")

    // Terminal emulation
    implementation("com.github.termux.termux-app:terminal-emulator:0.118.0")

    // Optional: Terminal view (only if using AndroidView fallback)
    // implementation("com.github.termux.termux-app:terminal-view:0.118.0")
}
```

Required repository:
```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}
```
