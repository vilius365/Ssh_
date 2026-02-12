# Security Review: PocketSSH Android SSH App

> **Reviewer**: Security Review Agent
> **Date**: 2026-02-11
> **Scope**: Full codebase under `app/src/main/java/com/remoteclaude/app/` plus XML configuration and build files
> **Status**: Initial review

---

## Executive Summary

The application demonstrates a strong security foundation. SSH private keys are encrypted at rest using Google Tink (AES-256-GCM) backed by Android Keystore hardware, plaintext key bytes are zeroed in `finally` blocks, backup exclusions are correctly configured, and the network security config blocks cleartext traffic. The tmux session name validation properly prevents command injection via regex allowlisting.

However, there is one **Critical** finding (the TOFU host key verifier auto-accepts all keys, completely disabling MITM protection) and several **High** findings that should be addressed before release, most notably command injection vectors in `TerminalViewModel` where user-controlled session names bypass `TmuxManagerImpl`'s validation regex.

---

## Critical Findings

### C-1: Host Key Verification Completely Disabled (MITM Vulnerability)

**File**: `app/src/main/java/com/remoteclaude/app/ssh/SshManagerImpl.kt:184-199`
**Severity**: CRITICAL

The `TofuHostKeyVerifier` unconditionally returns `true` for all host keys:

```kotlin
private class TofuHostKeyVerifier : ServerHostKeyVerifier {
    override fun verifyServerHostKey(...): Boolean {
        // ...
        return true  // ALWAYS accepts -- no MITM protection
    }
}
```

The TODO comment acknowledges this, but shipping this means:
- An attacker performing a network MITM attack will be transparently accepted
- Users have zero indication their connection is being intercepted
- The SSH protocol's entire authentication model relies on host key verification
- Every single connection is vulnerable, not just first connections

**Remediation**: Implement persistent known_hosts storage (Room or encrypted file). On first connection, prompt the user to verify and accept the host key fingerprint. On subsequent connections, compare against stored keys. On mismatch, block the connection and show a prominent warning. This was specified in the security research document (section 8) but not implemented.

---

## High Findings

### H-1: Command Injection via Unvalidated Session Names in TerminalViewModel

**File**: `app/src/main/java/com/remoteclaude/app/ui/terminal/TerminalViewModel.kt:204-224`
**Severity**: HIGH

`TmuxManagerImpl.getAttachCommand()` and `killSession()` correctly validate session names against `SESSION_NAME_REGEX` (`^[a-zA-Z0-9._-]+$`). However, `TerminalViewModel` bypasses `TmuxManager` entirely and constructs shell commands by string interpolation with unvalidated input:

```kotlin
fun attachSession(sessionName: String) {
    // sessionName comes from UI (parsed from tmux list-sessions output,
    // but also could be crafted if the parsing is manipulated)
    sendInput("tmux attach-session -t $sessionName\n".toByteArray())  // line 206
}

fun killSession(sessionName: String) {
    sshManager.executeCommand("tmux kill-session -t $sessionName")  // line 221
}
```

While `sessionName` currently originates from `refreshSessions()` parsing of tmux output, the data flow is:
1. Remote server stdout -> parsed by `TerminalViewModel.refreshSessions()` -> stored in `sessions` list
2. A malicious server could craft tmux session names containing shell metacharacters (e.g., `foo; rm -rf /`)
3. `killSession()` passes this directly to `sshManager.executeCommand()` which calls `execSession.execCommand(command)` -- this executes the full string as a shell command

**Remediation**: Route all tmux operations through `TmuxManager` which validates session names. In `TerminalViewModel`:
- Replace `sendInput("tmux attach-session -t $sessionName\n".toByteArray())` with `sendInput("${tmuxManager.getAttachCommand(sessionName)}\n".toByteArray())`
- Replace `sshManager.executeCommand("tmux kill-session -t $sessionName")` with `tmuxManager.killSession(sessionName)`
- Additionally, shell-quote session names even after regex validation as defense in depth

### H-2: Biometric Authentication Not Enforced for Key Access

**File**: `app/src/main/java/com/remoteclaude/app/security/KeyStorageManagerImpl.kt:191-197`
**Severity**: HIGH

The `BiometricHelper` interface and implementation exist, but biometric authentication is never actually invoked before decrypting private keys. `getDecryptedPrivateKey()` directly calls `aead.decrypt()` without any biometric gate:

```kotlin
override suspend fun getDecryptedPrivateKey(keyId: String): ByteArray? =
    withContext(Dispatchers.IO) {
        val encFile = encryptedKeyFile(keyId)
        if (!encFile.exists()) return@withContext null
        val encrypted = encFile.readBytes()
        aead.decrypt(encrypted, keyId.toByteArray(Charsets.UTF_8))
    }
```

Neither `TerminalViewModel.connect()` nor `ConnectionEditorViewModel.testConnection()` call `BiometricHelper.authenticate()` before retrieving key material. This means any app or process with access to the unlocked device can trigger key decryption without biometric verification.

The Tink AEAD master key in Android Keystore is also not configured with `setUserAuthenticationRequired(true)` (Tink's `AndroidKeysetManager` uses a simpler key configuration), so the Keystore itself does not enforce biometric authentication.

**Remediation**:
1. Add biometric authentication as a prerequisite before `getDecryptedPrivateKey()` calls
2. Consider configuring the Keystore master key with `setUserAuthenticationRequired(true)` and `setUserAuthenticationParameters(300, AUTH_BIOMETRIC_STRONG or AUTH_DEVICE_CREDENTIAL)` as described in the security research document (section 6)
3. Wire `BiometricHelper.authenticate()` into the connection flow in `TerminalViewModel` and `ConnectionEditorViewModel`

### H-3: Empty ByteArray Used as Key When No SSH Key Selected

**File**: `app/src/main/java/com/remoteclaude/app/ui/terminal/TerminalViewModel.kt:77-81`
**Severity**: HIGH

When no SSH key is configured or when key retrieval returns null, the code passes an empty `ByteArray(0)` to `sshManager.connect()`:

```kotlin
val keyBytes = if (keyId != null) {
    sshKeyManager.getPrivateKeyBytes(keyId) ?: ByteArray(0)
} else {
    ByteArray(0)
}
```

The same pattern exists in `ConnectionEditorViewModel.kt:186`. This has two problems:
1. If a key ID is set but the key is missing (deleted, corrupted), the connection silently falls back to attempting authentication with empty bytes instead of reporting the error
2. The ConnectBot sshlib `authenticateWithPublicKey(username, ByteArray(0), null)` behavior with empty bytes is undefined and may throw cryptic exceptions

**Remediation**: When `keyId != null` but `getPrivateKeyBytes()` returns null, show an error to the user (e.g., "SSH key not found -- it may have been deleted") and abort the connection attempt. Do not silently fall back.

### H-4: Database Not Encrypted

**File**: `app/src/main/java/com/remoteclaude/app/di/AppModule.kt:42-49`
**Severity**: HIGH

The Room database `claude_terminal.db` stores connection profiles including hostnames, usernames, ports, and SSH key IDs in plaintext SQLite. While the database is in app-internal storage (sandboxed), it is vulnerable to extraction on rooted devices and through ADB backup (though `allowBackup="false"` mitigates the backup vector).

```kotlin
fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase =
    Room.databaseBuilder(
        context,
        AppDatabase::class.java,
        "claude_terminal.db",
    )
        .fallbackToDestructiveMigration()
        .build()
```

Connection metadata (which servers a user connects to, usernames, timing patterns) is valuable reconnaissance data for an attacker.

**Remediation**: Use SQLCipher with Room (`net.zetetic:android-database-sqlcipher`) to encrypt the database at rest. The encryption key can be derived from the same Keystore-backed master key used for SSH key encryption.

---

## Medium Findings

### M-1: FLAG_SECURE Not Applied to Any Screen

**File**: `app/src/main/java/com/remoteclaude/app/MainActivity.kt`
**Severity**: MEDIUM

The security research document (section 7.3) recommends applying `FLAG_SECURE` to screens that display sensitive key material (key generation, key import, key details). None of the activities or screens apply this flag. This means:
- Screenshots can capture SSH public keys, fingerprints, and key metadata
- The app appears in the recent apps list with potentially sensitive information visible
- Screen recording apps can capture key management operations

**Remediation**: Apply `FLAG_SECURE` to `MainActivity` when navigating to key management screens. Since this is a single-activity Compose app, consider applying it globally or toggling it based on the current navigation destination.

### M-2: Key Metadata File Not Encrypted

**File**: `app/src/main/java/com/remoteclaude/app/security/KeyStorageManagerImpl.kt:266-267, 332-346`
**Severity**: MEDIUM

The `key_metadata.json` file stores key IDs, names, algorithms, fingerprints, and creation timestamps in plaintext JSON:

```kotlin
private fun metadataFile(): File =
    File(keysDir(), METADATA_FILE)
```

While the private key bytes themselves are encrypted, the metadata reveals:
- How many SSH keys exist
- What algorithms are in use (attack surface information)
- Key names that may contain identifying information
- Creation timestamps (behavioral patterns)

**Remediation**: Encrypt the metadata file using the same Tink AEAD instance used for key files, or store metadata in the encrypted Room database (if H-4 is addressed).

### M-3: Public Keys Stored in Plaintext

**File**: `app/src/main/java/com/remoteclaude/app/security/KeyStorageManagerImpl.kt:263-264, 274-276`
**Severity**: MEDIUM

Public key files (`$keyId.pub`) are stored as plaintext in the `ssh_keys/` directory:

```kotlin
private fun publicKeyFile(keyId: String): File =
    File(keysDir(), "$keyId.pub")

private fun savePublicKey(keyId: String, publicKeyOpenSsh: String) {
    publicKeyFile(keyId).writeText(publicKeyOpenSsh, Charsets.UTF_8)
}
```

Public keys are not secret, but their presence alongside encrypted private key files provides:
- Confirmation of which algorithms are in use
- The ability to test decrypted keys against known public keys during brute force
- Fingerprints that could be correlated with authorized_keys on target servers

**Remediation**: Consider encrypting the public key files as well, or storing them in the encrypted metadata. The overhead is minimal since public keys are only displayed in the UI and during export.

### M-4: DataStore Preferences Not Encrypted

**File**: `app/src/main/java/com/remoteclaude/app/di/AppModule.kt:32-34`
**Severity**: MEDIUM

The DataStore preferences file `claude_terminal_settings` stores user settings in plaintext. Currently it only stores non-sensitive settings (theme, font size, bell mode), but any future expansion could inadvertently store sensitive data here.

```kotlin
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = "claude_terminal_settings"
)
```

**Remediation**: If sensitive settings are added in the future, use an encrypted DataStore implementation (e.g., `encrypted-datastore` library referenced in the security research). For now, document that this store must NOT contain sensitive data.

### M-5: No Connection Timeout Configuration or Keepalive

**File**: `app/src/main/java/com/remoteclaude/app/ssh/SshManagerImpl.kt:23-24`
**Severity**: MEDIUM

Connection and key exchange timeouts are hardcoded at 30 seconds each, and there is no SSH keepalive configured:

```kotlin
private const val CONNECT_TIMEOUT_MS = 30_000
private const val KEY_EXCHANGE_TIMEOUT_MS = 30_000
```

Without SSH keepalive, a stale connection may not be detected until the user tries to send data, which could result in delayed error detection. There is also no idle session timeout, meaning a compromised device with an active SSH session grants indefinite access.

**Remediation**: Configure SSH keepalive (e.g., every 15 seconds) to detect stale connections promptly. Consider adding configurable idle session timeouts.

### M-6: Secure Deletion Only Overwrites Once

**File**: `app/src/main/java/com/remoteclaude/app/security/KeyStorageManagerImpl.kt:282-296`
**Severity**: MEDIUM

The secure deletion implementation overwrites with random data once:

```kotlin
private fun secureDelete(file: File) {
    // ...
    random.nextBytes(randomBytes)
    file.writeBytes(randomBytes)
    file.delete()
}
```

The code itself acknowledges that this has limited effectiveness on flash storage due to wear leveling. However, the `randomBytes` buffer is correctly zeroed in a `finally` block, which is good.

**Remediation**: The current approach is acceptable given flash storage limitations. The real protection is the encryption-at-rest design -- if the Keystore wrapping key is destroyed, encrypted remnants are useless. Document this explicitly.

---

## Low Findings

### L-1: Key IDs Are UUIDs Logged at INFO Level

**File**: `app/src/main/java/com/remoteclaude/app/security/KeyStorageManagerImpl.kt:106, 144, 180, 222, 235`
**Severity**: LOW

Key operations log the key ID at INFO level:

```kotlin
Log.i(TAG, "Generated Ed25519 key: $keyId ($name)")
Log.i(TAG, "Imported key: $keyId ($name, ${parsed.algorithm})")
Log.i(TAG, "Deleted key: $keyId")
```

While key IDs are not secret, they appear in the system logcat which other apps can read (on older Android versions) or which may be included in bug reports. The key name is also logged, which could contain identifying information.

**Remediation**: Reduce logging to DEBUG level in release builds, or strip key names from log output. Consider using a `BuildConfig.DEBUG` check to gate verbose logging.

### L-2: Host Key Fingerprint Logged at INFO Level

**File**: `app/src/main/java/com/remoteclaude/app/ssh/SshManagerImpl.kt:191-195`
**Severity**: LOW

The TOFU verifier logs the full host key fingerprint at INFO level:

```kotlin
Log.i(TAG, "Host key for $hostname:$port ($serverHostKeyAlgorithm): $fingerprint ...")
```

This reveals which servers the user connects to in the logcat.

**Remediation**: Log at DEBUG level only, or redact the hostname.

### L-3: No Input Length Limits on Connection Editor Fields

**File**: `app/src/main/java/com/remoteclaude/app/ui/connections/ConnectionEditorViewModel.kt:107-135`
**Severity**: LOW

The hostname, username, and nickname fields have no maximum length validation. While these are stored locally and not directly exploitable, extremely long values could cause UI rendering issues or database bloat.

**Remediation**: Add reasonable maximum length constraints (e.g., hostname: 253 chars per DNS spec, username: 32 chars, nickname: 64 chars).

### L-4: PEM Parsing Error Messages May Leak Information

**File**: `app/src/main/java/com/remoteclaude/app/security/KeyStorageManagerImpl.kt:394, 418-420, 447-449`
**Severity**: LOW

PEM parsing error messages include the class name of the unsupported PEM object:

```kotlin
else -> throw IllegalArgumentException(
    "Unsupported PEM key format: ${pemObject.javaClass.simpleName}"
)
```

This is fine for user-facing error messages but should not be exposed in production logs without sanitization.

**Remediation**: Acceptable as-is for a local app. Ensure these messages are shown to the user in a user-friendly way rather than as raw exception text.

### L-5: TerminalBridgeImpl CoroutineScope Never Cancelled

**File**: `app/src/main/java/com/remoteclaude/app/terminal/TerminalBridgeImpl.kt:43`
**Severity**: LOW

The `TerminalBridgeImpl` creates its own `CoroutineScope` that is never explicitly cancelled:

```kotlin
private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
```

When `detach()` is called, only the `readJob` is cancelled, but the scope itself lives on. This could lead to leaked coroutines if `resize()` is called after detach but before garbage collection.

**Remediation**: Cancel the scope's job in `stop()` and create a new scope in `start()`, or check for active state before launching in `resize()`.

---

## Passed Controls

### P-1: Private Keys Encrypted at Rest with Tink AEAD

**File**: `app/src/main/java/com/remoteclaude/app/security/KeyStorageManagerImpl.kt:243-251, 269-272`
**Status**: PASSED

SSH private keys are encrypted using Google Tink `AES256_GCM` with a master key stored in Android Keystore (`android-keystore://ssh_master_key`). The associated data (keyId) binds each ciphertext to its specific key, preventing ciphertext substitution attacks.

### P-2: Private Key Bytes Zeroed After Use

**Files**:
- `KeyStorageManagerImpl.kt:108-110` (Ed25519 generation)
- `KeyStorageManagerImpl.kt:148-150` (RSA generation)
- `KeyStorageManagerImpl.kt:183-186` (import)
- `SshManagerImpl.kt:98-100` (connection)
**Status**: PASSED

All code paths that handle plaintext private key bytes use `finally` blocks to zero the byte arrays:

```kotlin
try {
    // ... use privateKeyBytes ...
} finally {
    privateKeyBytes.fill(0)
}
```

The `importKey()` function also zeroes the passphrase `CharArray` and the intermediate `rawKeyBytes`. The JVM caveat about GC copies is acknowledged in the security research and is an acceptable residual risk.

### P-3: Passphrase Handled as CharArray, Zeroed After Use

**File**: `app/src/main/java/com/remoteclaude/app/security/KeyStorageManagerImpl.kt:185`
**Status**: PASSED

Passphrases are declared as `CharArray` (not `String`) and zeroed in finally blocks:

```kotlin
passphrase?.fill('\u0000')
```

This follows the security research guidance to avoid immutable `String` objects for sensitive data.

### P-4: Backup Exclusions Correctly Configured

**Files**:
- `AndroidManifest.xml:12` (`android:allowBackup="false"`)
- `res/xml/backup_rules.xml` (excludes `ssh_keys/` and keyset prefs)
- `res/xml/data_extraction_rules.xml` (excludes from both cloud backup and device transfer)
**Status**: PASSED

The `allowBackup="false"` flag prevents any backup of app data. The backup rules provide defense in depth by explicitly excluding SSH key files and the Tink keyset preferences even if `allowBackup` were changed.

### P-5: Cleartext Traffic Blocked

**File**: `res/xml/network_security_config.xml:3`
**Status**: PASSED

```xml
<base-config cleartextTrafficPermitted="false">
```

All HTTP traffic is blocked at the platform level. This prevents accidental cleartext requests (though the app uses SSH, not HTTP, for its primary communication).

### P-6: Only Required Permissions Declared

**File**: `AndroidManifest.xml:4-8`
**Status**: PASSED

The app declares only necessary permissions:
- `INTERNET` -- required for SSH connections
- `ACCESS_NETWORK_STATE` -- required to check connectivity
- `USE_BIOMETRIC` -- required for biometric authentication
- `VIBRATE` -- required for terminal bell
- `FOREGROUND_SERVICE` -- required for background SSH sessions

No dangerous permissions (storage, camera, contacts, location) are requested.

### P-7: No Exported Components Beyond Main Activity

**File**: `AndroidManifest.xml:21-30`
**Status**: PASSED

Only `MainActivity` is exported (with `android:exported="true"`), and it has only the standard `MAIN/LAUNCHER` intent filter. There are no exported services, broadcast receivers, or content providers that could be exploited by other apps.

### P-8: tmux Session Name Validation (in TmuxManagerImpl)

**File**: `app/src/main/java/com/remoteclaude/app/session/TmuxManagerImpl.kt:25, 62-63, 70-71`
**Status**: PASSED

`TmuxManagerImpl` validates session names against a strict allowlist regex before constructing shell commands:

```kotlin
private val SESSION_NAME_REGEX = Regex("^[a-zA-Z0-9._-]+$")

override fun getAttachCommand(sessionName: String): String {
    require(SESSION_NAME_REGEX.matches(sessionName)) { ... }
    return "tmux new-session -A -s $sessionName"
}
```

This correctly prevents shell injection through session names -- but only when `TmuxManager` is used (see H-1 for the bypass in `TerminalViewModel`).

### P-9: SSH Connection Parameters Validated

**File**: `app/src/main/java/com/remoteclaude/app/ui/connections/ConnectionEditorViewModel.kt:37-56`
**Status**: PASSED

The connection editor validates:
- Hostname against DNS hostname and IPv4 address regexes
- Port range (1-65535)
- Required fields (nickname, hostname, username)

### P-10: Ed25519 and RSA Key Generation Using SecureRandom

**File**: `app/src/main/java/com/remoteclaude/app/security/KeyStorageManagerImpl.kt:83, 117-119`
**Status**: PASSED

Both Ed25519 and RSA key generation use `java.security.SecureRandom`, which on Android is backed by `/dev/urandom` and provides cryptographically secure randomness.

### P-11: Room Database Does Not Store Sensitive Key Material

**File**: `app/src/main/java/com/remoteclaude/app/data/db/ConnectionProfileEntity.kt`
**Status**: PASSED

The Room database stores only connection metadata (hostname, port, username, key ID reference). SSH private key bytes are never stored in the database. The `sshKeyId` field is a UUID reference, not key material.

### P-12: Minification and Shrinking Enabled for Release

**File**: `app/build.gradle.kts:24-31`
**Status**: PASSED

Release builds enable `isMinifyEnabled = true` and `isShrinkResources = true`, which:
- Removes unused code (reduces attack surface)
- Obfuscates class/method names (mild reverse-engineering deterrent)
- Strips unused resources

---

## Summary Table

| ID | Severity | Finding | File | Status |
|----|----------|---------|------|--------|
| C-1 | CRITICAL | Host key verification disabled (auto-accepts all) | `SshManagerImpl.kt:184-199` | Open |
| H-1 | HIGH | Command injection via unvalidated session names in TerminalViewModel | `TerminalViewModel.kt:204-224` | Open |
| H-2 | HIGH | Biometric authentication not enforced for key access | `KeyStorageManagerImpl.kt:191-197` | Open |
| H-3 | HIGH | Empty ByteArray used when SSH key missing | `TerminalViewModel.kt:77-81` | Open |
| H-4 | HIGH | Room database not encrypted | `AppModule.kt:42-49` | Open |
| M-1 | MEDIUM | FLAG_SECURE not applied to any screen | `MainActivity.kt` | Open |
| M-2 | MEDIUM | Key metadata file not encrypted | `KeyStorageManagerImpl.kt:266-267` | Open |
| M-3 | MEDIUM | Public keys stored in plaintext | `KeyStorageManagerImpl.kt:263-264` | Open |
| M-4 | MEDIUM | DataStore preferences not encrypted | `AppModule.kt:32-34` | Open |
| M-5 | MEDIUM | No SSH keepalive or idle timeout | `SshManagerImpl.kt:23-24` | Open |
| M-6 | MEDIUM | Secure deletion single overwrite | `KeyStorageManagerImpl.kt:282-296` | Acceptable |
| L-1 | LOW | Key IDs and names logged at INFO level | `KeyStorageManagerImpl.kt:106` | Open |
| L-2 | LOW | Host key fingerprint logged at INFO | `SshManagerImpl.kt:191-195` | Open |
| L-3 | LOW | No input length limits on editor fields | `ConnectionEditorViewModel.kt:107-135` | Open |
| L-4 | LOW | PEM error messages include class names | `KeyStorageManagerImpl.kt:418-420` | Acceptable |
| L-5 | LOW | CoroutineScope never cancelled | `TerminalBridgeImpl.kt:43` | Open |
| P-1 | PASSED | Private keys encrypted at rest with Tink AEAD | `KeyStorageManagerImpl.kt` | -- |
| P-2 | PASSED | Private key bytes zeroed in finally blocks | Multiple files | -- |
| P-3 | PASSED | Passphrase handled as CharArray and zeroed | `KeyStorageManagerImpl.kt:185` | -- |
| P-4 | PASSED | Backup exclusions correctly configured | XML config files | -- |
| P-5 | PASSED | Cleartext traffic blocked | `network_security_config.xml` | -- |
| P-6 | PASSED | Only required permissions declared | `AndroidManifest.xml` | -- |
| P-7 | PASSED | No exported components beyond launcher activity | `AndroidManifest.xml` | -- |
| P-8 | PASSED | tmux session name validation in TmuxManagerImpl | `TmuxManagerImpl.kt:25` | -- |
| P-9 | PASSED | SSH connection parameters validated | `ConnectionEditorViewModel.kt` | -- |
| P-10 | PASSED | Key generation uses SecureRandom | `KeyStorageManagerImpl.kt` | -- |
| P-11 | PASSED | Room database does not store key material | `ConnectionProfileEntity.kt` | -- |
| P-12 | PASSED | Minification and shrinking enabled for release | `build.gradle.kts` | -- |

## Recommended Remediation Priority

1. **C-1** (Host key verification) -- This is the single most critical security gap. Implement TOFU known_hosts before any release.
2. **H-1** (Command injection) -- Route all tmux operations through `TmuxManager` to leverage existing validation. Quick fix.
3. **H-2** (Biometric auth) -- Wire `BiometricHelper` into the key retrieval flow. The infrastructure exists; it just needs to be connected.
4. **H-3** (Empty key fallback) -- Add null checks and user-facing errors. Quick fix.
5. **H-4** (Database encryption) -- Add SQLCipher. Moderate effort.
6. **M-1** through **M-5** -- Defense-in-depth improvements for a v1.0 release.
