# PocketSSH - Play Store Screenshot Strategy

## Requirements

- **Format:** Phone screenshots, 9:16 portrait (1080x1920 recommended)
- **Count:** 5 screenshots (Play Store allows 2-8)
- **Style:** Device-framed with caption text overlays
- **Caption background:** Dark (#0F1117) with teal (#4FD1C5) accent elements
- **Caption font:** Clean sans-serif, white text, 1-2 lines max

## Screenshot Plan

### Shot 1 - Connection List (First Impression)

**What's on screen:** Connection list with 3-4 sample server profiles (e.g., "Production Server", "Dev Environment", "Home Lab") showing hostnames and connection status indicators.

**Caption overlay:** "Manage All Your Servers"

**Value communicated:** App is organized, supports multiple connections, looks clean and modern.

**Setup notes:** Populate with realistic-looking connection profiles before capturing.

---

### Shot 2 - Active Terminal Session (Core Feature)

**What's on screen:** A live terminal session with visible command output. Show something recognizable like `htop`, `ls -la`, or a `docker ps` listing. Terminal should show colored output to demonstrate 256-color support.

**Caption overlay:** "Full Terminal Experience"

**Value communicated:** This is a real, functional terminal -- not a toy. Color support, readable text.

**Setup notes:** SSH into a real server or use a local environment. Run a command that produces colorful, recognizable output.

---

### Shot 3 - Tmux Session Manager (Differentiator)

**What's on screen:** The tmux session list showing 2-3 active sessions with names (e.g., "main", "monitoring", "deploy"). Show the attach/kill action options.

**Caption overlay:** "Built-in Tmux Support"

**Value communicated:** Differentiator from competitors. Users can manage persistent sessions directly from the app without typing tmux commands.

**Setup notes:** Create multiple tmux sessions on a remote server before capturing.

---

### Shot 4 - SSH Key Management (Security Angle)

**What's on screen:** The key manager screen showing a generated key pair with biometric lock icon. Show the key generation or import options.

**Caption overlay:** "Secure Key Authentication"

**Value communicated:** Security is taken seriously. Keys are hardware-encrypted. Biometric protection available.

**Setup notes:** Generate a test key pair and enable biometric protection before capturing.

---

### Shot 5 - Connection Editor (Ease of Use)

**What's on screen:** The connection editor form filled out with a sample server. Show fields for hostname, port, username, and authentication method selector.

**Caption overlay:** "Connect in Seconds"

**Value communicated:** Simple setup process. No complex configuration needed to get started.

**Setup notes:** Fill in the form with a realistic example (e.g., host: myserver.com, port: 22, user: admin).

## Device Frame

- **Recommended:** Use a Pixel 7 device frame (matches development target)
- **Tool:** Android Studio screenshot tool, or [screenshots.pro](https://screenshots.pro), or Figma device mockup plugin
- **Alternative:** Frameless with rounded corners (16px radius) and subtle drop shadow

## Caption Layout

```
+---------------------------+
|                           |
|   Caption Text (white)    |  <-- Top 20% of image
|   Subtitle (teal, small)  |
|                           |
+---------------------------+
|                           |
|                           |
|     App Screenshot        |  <-- Bottom 80%, device-framed
|                           |
|                           |
+---------------------------+
```

- Background: solid #0F1117 or very subtle gradient to #1a1d2e
- Caption text: 28-32pt white, bold
- Optional subtitle: 18pt #4FD1C5
- Screenshot scaled to fit with device frame

## Ordering Rationale

1. **Connection list first** -- users see this immediately, establishes the app's purpose
2. **Terminal second** -- the core product, proves it works
3. **Tmux third** -- the unique differentiator, catches sysadmin attention
4. **Key management fourth** -- addresses security concerns
5. **Connection editor fifth** -- shows ease of getting started
