# UI/UX Design Specification: Claude Terminal (Android SSH App)

**Version**: 1.0
**Target**: Android (min SDK 29), Kotlin, Jetpack Compose, Material 3

---

## Table of Contents

1. [Design Philosophy](#design-philosophy)
2. [Navigation Flow](#navigation-flow)
3. [Color System](#color-system)
4. [Typography](#typography)
5. [Screen Specifications](#screen-specifications)
6. [Gesture Mapping](#gesture-mapping)
7. [Extra Keys Bar](#extra-keys-bar)
8. [Accessibility](#accessibility)
9. [Compose Component Hierarchy](#compose-component-hierarchy)
10. [Competitive Analysis Summary](#competitive-analysis-summary)

---

## 1. Design Philosophy

**Core principle**: The terminal is the product. Everything else exists to get into and out of it as fast as possible.

- **Dark-first**: Dark theme is the default and primary design target. Light theme exists but is secondary.
- **Zero-chrome terminal**: When connected, the terminal owns 100% of usable screen space. No persistent toolbars, no status bars eating pixels.
- **One-tap reconnect**: The most common user action (reconnect to last session) must be achievable in a single tap from app launch.
- **Session persistence is a feature, not a footnote**: Users must always understand that tmux sessions survive disconnects. Visual language reinforces this.
- **Fat-finger tolerant**: Minimum touch target 48dp. Gestures have generous activation zones. Destructive actions require confirmation.

### What We Learned from Competitors

| App | Strengths | Weaknesses |
|-----|-----------|------------|
| **Termux** | Configurable extra keys, powerful, two-row key layout | Intimidating for non-power-users, no SSH connection management |
| **JuiceSSH** | Beautiful Material Design, snippet library, connection groups | Discontinued/removed from Play Store, over-featured |
| **ConnectBot** | Open source, lightweight, stable | Outdated UI (pre-Material), poor touch UX, no extra keys bar |
| **Blink Shell (iOS)** | Minimal chrome, Smart Keys bar, Context bar, session tabs | iOS only, steep learning curve for gestures |

**Key takeaways**:
- Termux's two-row extra keys layout is the gold standard. We adopt and refine it.
- Blink's philosophy of "terminal owns the screen" is correct. We follow it.
- JuiceSSH proved Material Design works for SSH apps. We use Material 3.
- ConnectBot's simplicity is valued by users. We keep the feature set tight.

---

## 2. Navigation Flow

```
App Launch
    |
    v
[Connection List (Home)]
    |
    |--- Tap connection card ---> [Terminal Screen]
    |                                  |
    |--- Tap "Quick Connect" -------> [Terminal Screen] (last used connection)
    |                                  |
    |--- Tap FAB (+) --------------> [Connection Editor]
    |                                  |
    |--- Tap Settings icon ---------> [Settings]
    |
    |
[Terminal Screen]
    |
    |--- Swipe up from bottom ------> [Session Manager (Bottom Sheet)]
    |--- Tap mini-FAB --------------> [Session Manager (Bottom Sheet)]
    |--- Volume Up/Down ------------> Font size adjustment
    |--- Pinch gesture -------------> Font size adjustment
    |--- Long press ----------------> Text selection mode
    |--- Swipe from left edge ------> [Connection List (Back)]
    |--- Back gesture/button -------> Disconnect dialog OR background
    |
    |
[Session Manager (Bottom Sheet)]
    |
    |--- Tap session ---------------> Attach to session
    |--- Tap "New Session" ---------> Create + attach
    |--- Swipe session left --------> Kill session (with confirm)
    |
    |
[Connection Editor]
    |
    |--- Fill form + Save ----------> [Connection List]
    |--- Test Connection -----------> Inline result
    |--- Cancel --------------------> [Connection List]
    |
    |
[Settings]
    |
    |--- Font settings (live preview)
    |--- Theme selection
    |--- Key management
    |--- About
```

### Back Navigation

- From Terminal: Android back gesture shows a "Disconnect?" dialog with options: "Disconnect", "Keep in Background", "Cancel". Default selection is "Keep in Background" to reinforce session persistence.
- From all other screens: Standard Material 3 back navigation.

---

## 3. Color System

### Dark Theme (Primary)

Uses Material 3 dynamic color when available, with the following as the static fallback. The palette is deliberately muted to avoid competing with terminal content.

```
// Surface hierarchy (darkest to lightest)
surface:              #0F1117  (app background, terminal background)
surfaceContainer:     #1A1C24  (cards, bottom sheets)
surfaceContainerHigh: #242730  (elevated cards, dialogs)
surfaceBright:        #2E3140  (hover states, selection)

// Primary (teal accent - readable on dark, not harsh)
primary:              #4FD1C5  (FABs, active indicators, links)
onPrimary:            #003731  (text on primary)
primaryContainer:     #00504A  (chips, badges)
onPrimaryContainer:   #70F5E8  (text on primary container)

// Secondary (muted blue-gray)
secondary:            #B0BEC5  (secondary text, icons)
onSecondary:          #1B2631  (text on secondary)

// Error
error:                #FF6B6B  (disconnected status, errors)
onError:              #1A0000

// Terminal-specific (not Material 3 standard, custom extension)
terminalGreen:        #50FA7B  (connected indicator)
terminalYellow:       #F1FA8C  (warning, pending)
terminalCursor:       #4FD1C5  (blinking cursor, matches primary)
terminalSelection:    #4FD1C540 (40% opacity teal)
```

### Light Theme (Secondary)

```
surface:              #FAFBFC
surfaceContainer:     #F0F1F3
primary:              #00897B  (darker teal for light backgrounds)
onSurface:            #1A1C24
terminalBackground:   #FFFFFF  (or user preference)
terminalForeground:   #1A1C24
```

### Terminal Color Palette (ANSI 16)

The terminal must support the standard ANSI 16-color palette. Default palette based on Dracula theme for dark mode, Solarized Light for light mode. Users can customize.

```
// Dracula-based dark theme defaults
Black:          #282A36    Bright Black:   #6272A4
Red:            #FF5555    Bright Red:     #FF6E6E
Green:          #50FA7B    Bright Green:   #69FF94
Yellow:         #F1FA8C    Bright Yellow:  #FFFFA5
Blue:           #BD93F9    Bright Blue:    #D6ACFF
Magenta:        #FF79C6    Bright Magenta: #FF92DF
Cyan:           #8BE9FD    Bright Cyan:    #A4FFFF
White:          #F8F8F2    Bright White:   #FFFFFF
```

---

## 4. Typography

### Terminal Font

**Default**: JetBrains Mono

**Rationale**: JetBrains Mono has excellent character differentiation (0/O, 1/l/I), was designed specifically for code reading, supports ligatures (optional), and renders well at small sizes on mobile screens. It is open source (OFL) and freely bundled.

**Bundled alternatives** (user-selectable):
1. **JetBrains Mono** - Default. Best readability.
2. **Fira Code** - Popular alternative. Good ligature support.
3. **Hack** - Clean, no-nonsense. Slightly wider.
4. **Inconsolata** - Compact. Good for smaller screens.

**Font sizing scale** (in sp):

| Label | Size (sp) | Use Case |
|-------|-----------|----------|
| Tiny | 10 | Maximum information density |
| Small | 12 | Dense but readable |
| Default | 14 | Recommended default |
| Medium | 16 | Comfortable reading |
| Large | 18 | Accessibility / tablets |
| XL | 22 | High visibility |

- Pinch-to-zoom and volume key adjustment use a continuous scale (1sp increments) within the 8-24sp range.
- Font size persists per-connection (users may want different sizes for different servers).
- The font size indicator briefly appears as an overlay (like volume indicator) during adjustment, then fades.

### UI Font

Use Material 3 default typography (system font / Roboto). The terminal font is only for terminal content. Do not use monospace for UI elements like labels, buttons, or navigation.

**Exception**: Connection hostnames and usernames display in monospace in the connection list for visual distinction.

### Typography Scale (UI)

```
titleLarge:    22sp / Medium    (screen titles)
titleMedium:   16sp / Medium    (card titles, connection names)
bodyLarge:     16sp / Regular   (primary body text)
bodyMedium:    14sp / Regular   (secondary body text)
bodySmall:     12sp / Regular   (captions, timestamps)
labelLarge:    14sp / Medium    (button labels)
labelMedium:   12sp / Medium    (chips, badges)
```

---

## 5. Screen Specifications

### 5.1 Connection List (Home Screen)

**Purpose**: Launch pad. Get into a terminal session as fast as possible.

**Layout**:
```
+------------------------------------------+
| Claude Terminal              [gear icon]  |   <- TopAppBar (small)
+------------------------------------------+
|                                           |
| [  Quick Connect: dev-server  ] [->]      |   <- Prominent quick-connect card
|     Last: 2 hours ago                     |
|                                           |
|  --- Saved Connections ---                |
|                                           |
|  +--------------------------------------+ |
|  | dev-server                      [*]  | |   <- Connection card
|  | user@192.168.1.100:22               | |
|  | Connected 2h ago | 3 sessions       | |
|  +--------------------------------------+ |
|                                           |
|  +--------------------------------------+ |
|  | production                           | |
|  | deploy@prod.example.com:22          | |
|  | Last: 3 days ago                     | |
|  +--------------------------------------+ |
|                                           |
|  +--------------------------------------+ |
|  | staging                              | |
|  | admin@staging.example.com:2222      | |
|  | Last: 1 week ago                     | |
|  +--------------------------------------+ |
|                                           |
|                                     [+]   |   <- FAB (add new connection)
+------------------------------------------+
```

**Components**:

- **TopAppBar**: Small Material 3 top app bar. Title "Claude Terminal". Settings gear icon on the right. No navigation icon (this is the root screen).
- **Quick Connect Card**: Elevated card at the top. Shows the last-used connection. Tapping it immediately connects and attaches to the most recent tmux session. Shows connection nickname, host, and "Last: X ago" timestamp. Right arrow icon indicates immediate action. This card is hidden if no connections exist yet.
- **Connection Cards**: Standard Material 3 outlined cards in a LazyColumn. Each card shows:
  - Nickname (titleMedium, monospace)
  - user@host:port (bodySmall, monospace, secondary color)
  - Last connected timestamp (bodySmall, tertiary color)
  - Active session count badge (if any sessions are alive)
  - Status dot: green (has active sessions), gray (no active sessions)
- **Swipe Actions**:
  - Swipe right: Edit (blue background, pencil icon)
  - Swipe left: Delete (red background, trash icon, with confirmation dialog)
- **FAB**: Standard Material 3 FAB in bottom-right. "+" icon. Opens Connection Editor.
- **Empty State**: When no connections exist, show centered illustration with text: "No connections yet" and a prominent "Add Connection" button.

**Sorting**: Connections sorted by last-connected timestamp (most recent first). The quick-connect card always shows the #1 item.

### 5.2 Terminal Screen

**Purpose**: The main screen. This is where 90% of time is spent. Maximum screen real estate for terminal content.

**Layout (keyboard hidden)**:
```
+------------------------------------------+
|                                           |
|  user@server:~$ claude                    |
|                                           |
|  [Claude Code terminal output...]         |
|                                           |
|                                           |
|                                           |
|                                           |
|                                           |
|                                           |
|                                           |
|                                           |
|                                           |
|                                           |
|                                           |
|                          [*] (mini-FAB)   |   <- Semi-transparent, bottom-right
|------------------------------------------+
| [status: connected] [session: main]       |   <- Thin status strip (24dp)
+------------------------------------------+
```

**Layout (keyboard visible)**:
```
+------------------------------------------+
|                                           |
|  user@server:~$ claude                    |
|                                           |
|  [Claude Code terminal output...]         |
|                                           |
|                                           |
|                                           |
+------------------------------------------+
| ESC | TAB | CTRL| ALT | -  | / | |  | ~ |  <- Extra keys row 1 (40dp)
| UP  | DN  | LFT | RGT |HOME|END|PgU|PgD |  <- Extra keys row 2 (40dp)
+------------------------------------------+
| [        Software Keyboard              ] |
| [                                       ] |
| [                                       ] |
+------------------------------------------+
```

**Components**:

- **Terminal View**: Custom Compose component wrapping a Canvas-based terminal renderer. Fills all available space. Background color from terminal theme. Renders monospace glyphs at the configured font size. Handles scroll-back buffer (minimum 10,000 lines). Cursor blinks at 500ms interval with the primary color.
- **Status Strip**: A thin (24dp) bar at the bottom of the terminal (above extra keys when keyboard is visible, at the very bottom when hidden). Contains:
  - Connection status: green dot + "Connected" or red dot + "Disconnected"
  - Current tmux session name
  - Tapping the strip opens the Session Manager bottom sheet
  - Auto-hides after 3 seconds of inactivity when keyboard is hidden (tap terminal to show briefly)
- **Mini-FAB**: 40dp semi-transparent floating action button in the bottom-right corner. 30% opacity when idle, 80% on touch. Tapping opens the Session Manager bottom sheet. Auto-hides after 5 seconds of no interaction (appears on any tap).
- **Extra Keys Bar**: Two-row bar that appears above the software keyboard. Detailed in section 7.
- **Text Selection Mode**: Activated by long-press. Shows selection handles. Floating toolbar with Copy button appears near selection. Tapping outside selection exits selection mode.
- **Font Size Overlay**: When pinch-zooming or using volume keys, a centered overlay shows the current font size (e.g., "14sp") for 1 second, then fades.

**Immersive Mode**: The terminal screen uses edge-to-edge rendering. The system status bar is translucent with the terminal background color bleeding through. Navigation bar is hidden (gesture navigation assumed for SDK 29+).

**Disconnection Handling**: When connection drops:
1. Status strip turns red: "Disconnected - Tap to reconnect"
2. Terminal content remains visible (frozen at last state)
3. Tapping the status strip or the red mini-FAB attempts reconnection
4. On reconnect, automatically reattach to the last tmux session
5. Brief green flash on status strip confirms reconnection

### 5.3 Session Manager (Bottom Sheet)

**Purpose**: Manage tmux sessions without leaving the terminal. Quick session switching.

**Layout**:
```
+------------------------------------------+
|           --- (drag handle) ---           |
|                                           |
|  Sessions on dev-server                   |  <- Title
|                                           |
|  +--------------------------------------+ |
|  | * main                    [detach]   | |  <- Active session (highlighted)
|  |   3 windows | attached 2h ago        | |
|  +--------------------------------------+ |
|                                           |
|  +--------------------------------------+ |
|  |   claude-project           [attach]  | |  <- Inactive session
|  |   1 window | detached 30m ago        | |
|  +--------------------------------------+ |
|                                           |
|  +--------------------------------------+ |
|  |   monitoring              [attach]   | |
|  |   2 windows | detached 1d ago        | |
|  +--------------------------------------+ |
|                                           |
|  [+ New Session]                          |  <- Text button
|                                           |
+------------------------------------------+
```

**Components**:

- **Modal Bottom Sheet**: Material 3 modal bottom sheet. Drag handle at top. Scrim behind. Dismissible by dragging down or tapping scrim.
- **Session Cards**: Each tmux session is a row with:
  - Session name (bold if active, regular if detached)
  - Window count and last activity timestamp
  - Action button: "Attach" for detached sessions, "Detach" for the active one
  - Active session has a primary-color left border accent (4dp)
  - Swipe left to reveal "Kill" action (red, with confirmation dialog: "Kill session 'name'? This will close all windows and processes.")
- **New Session Button**: Text button at the bottom. Tapping creates a new tmux session with an auto-generated name (e.g., "session-3") and immediately attaches to it.
- **Refresh**: Pull-down on the session list refreshes the tmux session listing.

### 5.4 Connection Editor

**Purpose**: Add or edit a server connection. Clean form, nothing fancy.

**Layout**:
```
+------------------------------------------+
| <- Connection Editor          [Test] [Save]|  <- TopAppBar
+------------------------------------------+
|                                           |
|  Nickname                                 |
|  +--------------------------------------+ |
|  | dev-server                           | |  <- OutlinedTextField
|  +--------------------------------------+ |
|                                           |
|  Hostname                                 |
|  +--------------------------------------+ |
|  | 192.168.1.100                        | |
|  +--------------------------------------+ |
|                                           |
|  Port                                     |
|  +--------------------------------------+ |
|  | 22                                   | |  <- Number input, default 22
|  +--------------------------------------+ |
|                                           |
|  Username                                 |
|  +--------------------------------------+ |
|  | admin                                | |
|  +--------------------------------------+ |
|                                           |
|  SSH Key                                  |
|  +--------------------------------------+ |
|  | my-key (RSA 4096)            [...]   | |  <- Dropdown/picker
|  +--------------------------------------+ |
|                                           |
|  [ Test Connection ]                      |  <- Outlined button
|  (result: "Connected successfully" / err) |
|                                           |
+------------------------------------------+
```

**Components**:

- **TopAppBar**: Small Material 3 top app bar. Back arrow. Title "New Connection" or "Edit Connection". Save button (enabled only when form is valid). Test button (outlined style).
- **Form Fields**: Material 3 OutlinedTextField for each field. Nickname and hostname are required. Port defaults to 22. Username is required.
- **SSH Key Selector**: Tapping opens a bottom sheet with available keys. Each key shows: name, type (RSA/Ed25519), fingerprint preview, creation date. "Import Key" and "Generate Key" options at the bottom.
- **Test Connection Button**: Outlined button. On tap, shows a circular progress indicator inline. Result appears below as a success (green check + "Connected successfully") or error (red X + error message). Timeout after 10 seconds.
- **Validation**: Real-time validation. Hostname validates as IP or domain. Port validates as 1-65535. Visual error states on invalid fields using Material 3 error styling.
- **Save**: Disabled until all required fields are valid. On save, navigates back to Connection List.

### 5.5 Settings Screen

**Purpose**: App-level preferences. Keep it simple.

**Layout**:
```
+------------------------------------------+
| <- Settings                               |
+------------------------------------------+
|                                           |
|  APPEARANCE                               |  <- Section header
|                                           |
|  Theme                                    |
|  [System] [Dark] [Light]                  |  <- Segmented button
|                                           |
|  Font Family                              |
|  JetBrains Mono                     [>]  |  <- Opens font picker
|                                           |
|  Font Size                                |
|  10 ---[====o========]--- 24             |  <- Slider
|                                           |
|  +--------------------------------------+ |
|  | user@server:~$ ls -la                | |  <- Live font preview
|  | drwxr-xr-x  2 user user 4096 ...    | |
|  | -rw-r--r--  1 user user  220 ...    | |
|  +--------------------------------------+ |
|                                           |
|  TERMINAL                                 |
|                                           |
|  Scrollback Lines                         |
|  [10,000]                           [>]  |
|                                           |
|  Bell                                     |
|  [Vibrate] [Sound] [None]                |  <- Segmented button
|                                           |
|  KEYS                                     |
|                                           |
|  Manage SSH Keys                    [>]  |  <- Navigate to key manager
|                                           |
|  ABOUT                                    |
|                                           |
|  Version 1.0.0                            |
|  Open Source Licenses               [>]  |
|                                           |
+------------------------------------------+
```

**Components**:

- **Theme Selector**: Material 3 segmented button. Three options: System, Dark, Light. Changes apply immediately.
- **Font Family Picker**: Bottom sheet with font options. Each option shows the font name rendered in that font. Tapping selects and dismisses.
- **Font Size Slider**: Material 3 Slider. Range 8-24sp. Step 1sp. Live preview updates as slider moves.
- **Live Preview**: A small terminal-styled box showing sample text in the currently selected font and size. Dark background regardless of theme. Updates in real-time as font settings change.
- **Key Manager**: Separate screen. Lists all stored SSH keys with: name, type, fingerprint, creation date. Actions: rename, delete, export public key. Import and generate options.

---

## 6. Gesture Mapping

### Terminal Screen Gestures

| Gesture | Action | Notes |
|---------|--------|-------|
| **Tap** | Focus terminal + show keyboard | Also briefly shows status strip and mini-FAB |
| **Long press** | Enter text selection mode | Selection handles appear at press point |
| **Long press + drag** | Select text | Extends selection from initial press point |
| **Double tap** | Select word under cursor | Standard word selection |
| **Pinch out** | Increase font size | Smooth, continuous. Overlay shows current size. |
| **Pinch in** | Decrease font size | Smooth, continuous. Overlay shows current size. |
| **Two-finger scroll** | Scroll terminal history | Vertical only. Single finger sends input to terminal. |
| **Swipe from left edge** | Navigate back / show connection list | 20dp activation zone from screen edge |
| **Swipe up from bottom** | Open session manager | Only when keyboard is hidden. 48dp activation zone. |
| **Volume Up** | Increase font size by 1sp | Alternative to pinch. Works with one hand. |
| **Volume Down** | Decrease font size by 1sp | Alternative to pinch. Works with one hand. |

### Connection List Gestures

| Gesture | Action | Notes |
|---------|--------|-------|
| **Tap card** | Connect to server | Immediately connects and attaches to last session |
| **Swipe card right** | Reveal edit action | Blue background, pencil icon |
| **Swipe card left** | Reveal delete action | Red background, trash icon, requires confirmation |
| **Long press card** | Enter multi-select mode | For bulk operations (future feature) |

### Session Manager Gestures

| Gesture | Action | Notes |
|---------|--------|-------|
| **Tap session** | Attach to session | Dismisses bottom sheet, switches to session |
| **Swipe session left** | Reveal kill action | Red, requires confirmation dialog |
| **Drag handle down** | Dismiss bottom sheet | Standard Material 3 behavior |
| **Tap scrim** | Dismiss bottom sheet | Standard Material 3 behavior |

### Global Gestures

| Gesture | Action | Notes |
|---------|--------|-------|
| **System back** | Context-dependent | Terminal: disconnect dialog. Others: navigate back. |

---

## 7. Extra Keys Bar

The extra keys bar is critical for terminal usability on mobile. Modeled after Termux's two-row layout with improvements.

### Layout

```
Row 1 (Primary): | ESC | TAB | CTRL | ALT  |  -  |  /  |  |  |  ~  |
Row 2 (Navigation): |  UP | DOWN| LEFT | RIGHT|HOME |END  |PgUp|PgDn|
```

### Design Details

- **Height**: Each row is 40dp (total 80dp for both rows). This balances touch target size with screen space conservation.
- **Background**: `surfaceContainer` color (#1A1C24 dark). 1dp top border in `surfaceBright` for visual separation from keyboard.
- **Key styling**: Each key is a Material 3 `FilledTonalButton` variant (custom). Background `surfaceBright` (#2E3140). Text `onSurface`. Pressed state uses `primaryContainer`.
- **Modifier behavior (CTRL, ALT)**: Toggle keys. Tap once to activate (key highlights with `primary` color). Next character sent includes the modifier. Double-tap to lock (key shows persistent highlight + underline). Tap again to release. This matches Blink Shell's behavior and is more intuitive than Termux's single-press approach.
- **Key spacing**: 4dp gap between keys. Keys fill available width equally.
- **Visibility**: Only shown when software keyboard is visible. Slides in/out with the keyboard using `WindowInsets.ime` animation.

### Key Descriptions

| Key | Label | Behavior |
|-----|-------|----------|
| ESC | ESC | Sends escape character (0x1B) |
| TAB | TAB | Sends tab character (0x09) |
| CTRL | CTRL | Toggle modifier. Combines with next keypress. |
| ALT | ALT | Toggle modifier. Combines with next keypress. |
| - | - | Sends hyphen/minus |
| / | / | Sends forward slash |
| \| | \| | Sends pipe character |
| ~ | ~ | Sends tilde |
| UP | arrow up icon | Sends ANSI up arrow sequence |
| DOWN | arrow down icon | Sends ANSI down arrow sequence |
| LEFT | arrow left icon | Sends ANSI left arrow sequence |
| RIGHT | arrow right icon | Sends ANSI right arrow sequence |
| HOME | HOME | Sends home sequence |
| END | END | Sends end sequence |
| PgUp | PgU | Sends page up sequence |
| PgDn | PgD | Sends page down sequence |

### Alternative: Swipeable Extra Keys

For advanced users, each key supports swipe gestures for secondary actions:

| Base Key | Swipe Up | Notes |
|----------|----------|-------|
| ESC | F1-F12 popup | Shows a small popup grid of function keys |
| CTRL | CTRL+C shortcut | Common interrupt pattern |
| - | _ (underscore) | Related character |
| / | \ (backslash) | Related character |
| \| | & (ampersand) | Related shell character |
| ~ | ` (backtick) | Related character |

---

## 8. Accessibility

### Content Descriptions

- All interactive elements have `contentDescription` for TalkBack.
- Terminal content is announced as "Terminal output" with the last line of visible text.
- Connection status changes are announced ("Connected to dev-server", "Connection lost").
- Session switches are announced ("Attached to session main").

### Touch Targets

- All interactive elements: minimum 48dp x 48dp touch target.
- Extra keys: 40dp height is acceptable because the horizontal width exceeds 48dp.
- Mini-FAB: 48dp diameter (visual is 40dp, touch target is 48dp).

### Font Scaling

- Terminal font size is independent of system font scale (users control it directly).
- UI elements (connection list, settings) respect system font scaling via `sp` units.

### Color Contrast

- All text meets WCAG AA contrast ratio (4.5:1 for body text, 3:1 for large text).
- Terminal default colors (Dracula theme) have been selected for readability, not just aesthetics.
- Status indicators use both color AND shape/icon (green dot + "Connected" text, not just a green dot).

### Keyboard Navigation

- Full keyboard navigation support when external keyboard is connected.
- Extra keys bar is hidden when external keyboard is detected.
- Tab order follows visual layout order.
- Focus indicators visible on all interactive elements.

### Screen Reader Support

- Terminal output is accessible as a text buffer.
- New output is announced as it appears (configurable: on/off, verbosity level).
- Selection mode provides audio feedback for selection boundaries.

---

## 9. Compose Component Hierarchy

```
App
├── ClaudeTerminalTheme (MaterialTheme wrapper)
│   ├── NavHost
│   │   ├── ConnectionListScreen
│   │   │   ├── TopAppBar
│   │   │   ├── QuickConnectCard
│   │   │   ├── LazyColumn
│   │   │   │   └── ConnectionCard (repeating)
│   │   │   │       ├── ConnectionStatusDot
│   │   │   │       ├── ConnectionInfo
│   │   │   │       └── SessionCountBadge
│   │   │   ├── EmptyStateView
│   │   │   └── FloatingActionButton
│   │   │
│   │   ├── TerminalScreen
│   │   │   ├── TerminalView (custom Canvas-based composable)
│   │   │   │   ├── TerminalRenderer (draws glyphs)
│   │   │   │   ├── CursorRenderer (blinking cursor)
│   │   │   │   ├── SelectionOverlay (highlight layer)
│   │   │   │   └── ScrollbackBuffer (data layer)
│   │   │   ├── StatusStrip
│   │   │   │   ├── ConnectionStatusIndicator
│   │   │   │   └── SessionNameChip
│   │   │   ├── ExtraKeysBar
│   │   │   │   ├── ExtraKeyRow (Row 1: modifiers + symbols)
│   │   │   │   │   └── ExtraKey (repeating)
│   │   │   │   └── ExtraKeyRow (Row 2: navigation)
│   │   │   │       └── ExtraKey (repeating)
│   │   │   ├── MiniFAB
│   │   │   ├── FontSizeOverlay
│   │   │   ├── DisconnectDialog
│   │   │   └── SessionManagerSheet (ModalBottomSheet)
│   │   │       ├── SessionList
│   │   │       │   └── SessionCard (repeating)
│   │   │       │       ├── SessionInfo
│   │   │       │       └── SessionAction
│   │   │       └── NewSessionButton
│   │   │
│   │   ├── ConnectionEditorScreen
│   │   │   ├── TopAppBar (with Save action)
│   │   │   ├── ConnectionForm
│   │   │   │   ├── OutlinedTextField (nickname)
│   │   │   │   ├── OutlinedTextField (hostname)
│   │   │   │   ├── OutlinedTextField (port)
│   │   │   │   ├── OutlinedTextField (username)
│   │   │   │   └── KeySelector
│   │   │   │       └── KeyPickerSheet (ModalBottomSheet)
│   │   │   └── TestConnectionButton
│   │   │       └── TestResultIndicator
│   │   │
│   │   └── SettingsScreen
│   │       ├── TopAppBar
│   │       ├── LazyColumn
│   │       │   ├── ThemeSelector (SegmentedButton)
│   │       │   ├── FontFamilySelector
│   │       │   │   └── FontPickerSheet (ModalBottomSheet)
│   │       │   ├── FontSizeSlider
│   │       │   ├── FontPreview (terminal-styled box)
│   │       │   ├── ScrollbackSetting
│   │       │   ├── BellSelector (SegmentedButton)
│   │       │   └── KeyManagerLink
│   │       └── KeyManagerScreen (nested navigation)
│   │           ├── KeyList
│   │           │   └── KeyCard (repeating)
│   │           ├── ImportKeyButton
│   │           └── GenerateKeyButton
│   │               └── KeyGenerationDialog
│   │
│   └── SnackbarHost (global snackbar for notifications)
```

### Key Composable Contracts

```kotlin
// Terminal view - the core rendering surface
@Composable
fun TerminalView(
    state: TerminalState,
    fontSize: TextUnit,
    fontFamily: FontFamily,
    colorScheme: TerminalColorScheme,
    onInput: (String) -> Unit,
    onSelectionChanged: (String?) -> Unit,
    modifier: Modifier = Modifier
)

// Extra keys bar
@Composable
fun ExtraKeysBar(
    onKeyPress: (TerminalKey) -> Unit,
    onModifierToggle: (Modifier, Boolean) -> Unit,
    activeModifiers: Set<TerminalModifier>,
    modifier: Modifier = Modifier
)

// Connection card
@Composable
fun ConnectionCard(
    connection: ConnectionProfile,
    activeSessions: Int,
    onConnect: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
)

// Session card
@Composable
fun SessionCard(
    session: TmuxSession,
    isActive: Boolean,
    onAttach: () -> Unit,
    onDetach: () -> Unit,
    onKill: () -> Unit,
    modifier: Modifier = Modifier
)
```

### State Management

- **ViewModel per screen**: `ConnectionListViewModel`, `TerminalViewModel`, `ConnectionEditorViewModel`, `SettingsViewModel`.
- **Terminal state**: Managed by `TerminalViewModel` which holds the `TerminalState` (buffer, cursor position, scroll position, selection). Updated by SSH data stream.
- **Connection state**: `ConnectionRepository` as single source of truth. Room database for persistence.
- **Preferences**: DataStore for settings (font size, theme, etc.).
- **Navigation**: Compose Navigation with type-safe routes.

---

## 10. Competitive Analysis Summary

### What We Take

| Feature | Source | Our Adaptation |
|---------|--------|----------------|
| Two-row extra keys | Termux | Refined with toggle modifiers instead of hold |
| Full-screen terminal | Blink Shell | Status strip auto-hides, mini-FAB fades |
| Material Design connection list | JuiceSSH | Material 3 with quick-connect card |
| Open source ethos | ConnectBot | Fully open source |
| Smart modifier keys | Blink Shell | Tap-to-toggle, double-tap-to-lock |

### What We Avoid

| Anti-pattern | Source | Why |
|--------------|--------|-----|
| Cluttered toolbar | JuiceSSH | Steals terminal space. We use auto-hiding elements. |
| No extra keys | ConnectBot | Essential for terminal use on mobile. |
| Over-configurable extra keys | Termux | Sensible defaults that work for 90% of users. |
| Tab-based session switching | Blink Shell | Bottom sheet is more thumb-friendly on large phones. |
| Persistent status bar | Most apps | Status strip auto-hides. Terminal gets every pixel. |

---

## Design Decisions Log

| Decision | Rationale |
|----------|-----------|
| Dark theme as default | Terminal users overwhelmingly prefer dark. Reduces eye strain. |
| JetBrains Mono as default font | Best readability at small sizes, designed for code, open source. |
| Two-row extra keys (not one) | One row requires tiny keys or missing essential keys. Two rows balance touch targets with completeness. |
| Bottom sheet for sessions (not tabs) | Tabs consume permanent screen space. Bottom sheet is on-demand. More thumb-friendly on modern tall phones. |
| Volume keys for font size | One-handed operation. Pinch requires two hands. Both options available. |
| Quick-connect card | Reduces most common action to one tap. Data shows most users connect to the same server repeatedly. |
| Auto-hiding status strip | Maximum terminal space while still providing status on demand. |
| Toggle modifiers (not hold) | Holding CTRL and tapping another key is awkward on touchscreen. Toggle is more ergonomic. |
| Per-connection font size | Different servers may have different terminal widths or use cases. |
| Dracula color palette default | High contrast, widely recognized, pleasant for extended use. |
