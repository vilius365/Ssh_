# SSH Key Security Research for Android SSH Terminal App

> **Target**: Android 10+ (API 29+), Kotlin/Jetpack Compose
> **Date**: 2026-02-11

---

## Table of Contents

1. [Threat Model](#1-threat-model)
2. [Android Keystore System](#2-android-keystore-system)
3. [Recommended Key Storage Architecture](#3-recommended-key-storage-architecture)
4. [SSH Key Generation](#4-ssh-key-generation)
5. [Key Import Flow](#5-key-import-flow)
6. [Biometric Authentication Gating](#6-biometric-authentication-gating)
7. [Key Lifecycle Security](#7-key-lifecycle-security)
8. [Host Key Verification (TOFU)](#8-host-key-verification-tofu)
9. [Network Security](#9-network-security)
10. [Recommendations Summary](#10-recommendations-summary)

---

## 1. Threat Model

### What We're Protecting

- **SSH private keys** stored on the device
- **Connection credentials** (hostnames, usernames, ports)
- **Known hosts database** (integrity, not confidentiality)
- **Session data** in transit

### Threat Actors (Ranked by Likelihood)

| Threat | Likelihood | Impact | Mitigation |
|--------|-----------|--------|------------|
| Device theft (locked) | High | Medium | Encryption at rest + biometric gating |
| Malware/rogue app | Medium | High | App sandbox + Keystore-backed encryption |
| Shoulder surfing | Medium | Low | FLAG_SECURE on key screens |
| Device theft (unlocked) | Low | Critical | Biometric gating per-use, session timeouts |
| Targeted forensic analysis | Very Low | Critical | Hardware-backed keys where possible |
| Network MITM | Low | High | Host key verification, no password auth |

### Realistic Security Posture

A mobile SSH client operates under a fundamental constraint: **the device itself is the trust boundary**. If the device is fully compromised (rooted + attacker has physical access + device unlocked), no amount of application-level security will protect the keys.

Our goal is defense-in-depth:
1. Make key extraction require significantly more effort than alternatives
2. Protect against opportunistic theft and casual snooping
3. Prevent key leakage through backups, screenshots, and clipboard
4. Provide biometric gating as a meaningful speed bump for unlocked device scenarios

**What we are NOT doing** (security theater):
- Custom encryption algorithms
- Obfuscation of key storage locations (security through obscurity)
- Multiple layers of password prompts that add friction without security
- Storing keys in "hidden" directories

---

## 2. Android Keystore System

### Can It Store SSH Keys Directly?

**No.** The Android Keystore is designed for Android cryptographic operations (AES, RSA for signing/encryption, ECDSA). It does not support:
- Exporting private key material (by design)
- SSH-specific key formats (OpenSSH, PEM)
- Ed25519 in StrongBox (TEE support added in Android 13+ via KeyMint v2, but StrongBox support varies by device)

Since SSH libraries (SSHJ, Apache MINA SSHD) need raw key material to perform SSH handshakes, we **cannot** store SSH keys directly in Android Keystore.

### What It CAN Do

The Keystore is ideal for generating a **wrapping key** -- an AES-256-GCM key that encrypts the SSH private key at rest. This wrapping key:
- Never leaves the TEE/StrongBox
- Can require biometric authentication to use
- Is hardware-backed on all modern devices (API 29+)
- Survives app updates but not app uninstalls (keys are scoped to app UID)

### Hardware-Backed vs Software-Backed

| Feature | TEE (Trusted Execution Environment) | StrongBox (Secure Element) |
|---------|--------------------------------------|---------------------------|
| Availability | All devices API 29+ | Flagships, newer mid-range |
| Key types | AES, RSA, ECDSA, ECDH, Ed25519 (API 33+) | AES, RSA, ECDSA (subset) |
| Performance | Fast | 35-55x slower than TEE |
| Tamper resistance | Software isolation | Physical tamper resistance |
| Our use | Wrapping key for SSH keys | Optional upgrade path |

**Recommendation**: Use TEE-backed keys as default. Attempt StrongBox with fallback to TEE.

### Keystore Key Generation for Wrapping

```kotlin
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import javax.crypto.KeyGenerator

private const val WRAPPING_KEY_ALIAS = "ssh_key_wrapping_key"

fun getOrCreateWrappingKey(): SecretKey {
    val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    // Return existing key if present
    keyStore.getKey(WRAPPING_KEY_ALIAS, null)?.let { return it as SecretKey }

    // Generate new wrapping key
    val keyGen = KeyGenerator.getInstance(
        KeyProperties.KEY_ALGORITHM_AES,
        "AndroidKeyStore"
    )

    val spec = KeyGenParameterSpec.Builder(
        WRAPPING_KEY_ALIAS,
        KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
    )
        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
        .setKeySize(256)
        .setUserAuthenticationRequired(true)
        .setUserAuthenticationParameters(
            300, // 5 minutes validity after auth
            KeyProperties.AUTH_BIOMETRIC_STRONG or KeyProperties.AUTH_DEVICE_CREDENTIAL
        )
        // Attempt StrongBox, fall back to TEE
        .apply {
            try {
                setIsStrongBoxBacked(true)
            } catch (_: Exception) {
                // StrongBox not available, TEE is fine
            }
        }
        .build()

    keyGen.init(spec)
    return keyGen.generateKey()
}
```

---

## 3. Recommended Key Storage Architecture

### Overview

```
+--------------------------------------------------+
|  Android Keystore (TEE/StrongBox)                |
|  +--------------------------------------------+  |
|  |  AES-256-GCM Wrapping Key                  |  |
|  |  (never leaves secure hardware)            |  |
|  |  Requires biometric auth to decrypt        |  |
|  +--------------------------------------------+  |
+--------------------------------------------------+
         |  encrypts/decrypts
         v
+--------------------------------------------------+
|  App Internal Storage (encrypted file)           |
|  +--------------------------------------------+  |
|  |  IV (12 bytes) | Auth Tag | Encrypted SSH  |  |
|  |  Private Key (Ed25519 or RSA)              |  |
|  +--------------------------------------------+  |
+--------------------------------------------------+
```

### Why NOT EncryptedSharedPreferences / EncryptedFile

**EncryptedSharedPreferences has been officially deprecated** (androidx.security:security-crypto:1.1.0-alpha07). It suffered from:
- Keyset corruption on certain OEM devices
- Performance issues (strict mode violations on main thread)
- No clean way to upgrade encryption schemes
- Direct Android Keystore dependency with inconsistent behavior

### Recommended: Tink + App Internal Storage

Google Tink is the modern replacement. For our use case (encrypting SSH key files), we use Tink's `Aead` primitive with a Keystore-backed master key.

**Dependencies:**
```kotlin
// build.gradle.kts
implementation("com.google.crypto.tink:tink-android:1.15.0")
```

**Implementation:**

```kotlin
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.aead.PredefinedAeadParameters
import com.google.crypto.tink.integration.android.AndroidKeysetManager

object SshKeyEncryption {
    private const val KEYSET_NAME = "ssh_key_master_keyset"
    private const val PREF_FILE_NAME = "ssh_key_master_keyset_prefs"
    private const val MASTER_KEY_URI = "android-keystore://ssh_master_key"

    fun init() {
        AeadConfig.register()
    }

    fun getAead(context: Context): Aead {
        val keysetHandle = AndroidKeysetManager.Builder()
            .withSharedPref(context, KEYSET_NAME, PREF_FILE_NAME)
            .withKeyTemplate(PredefinedAeadParameters.AES256_GCM)
            .withMasterKeyUri(MASTER_KEY_URI)
            .build()
            .keysetHandle

        return keysetHandle.getPrimitive(Aead::class.java)
    }

    fun encryptKey(aead: Aead, privateKeyBytes: ByteArray, keyId: String): ByteArray {
        // Associated data ties the ciphertext to this specific key ID
        return aead.encrypt(privateKeyBytes, keyId.toByteArray())
    }

    fun decryptKey(aead: Aead, encryptedBytes: ByteArray, keyId: String): ByteArray {
        return aead.decrypt(encryptedBytes, keyId.toByteArray())
    }
}
```

### File Storage Location

Store encrypted key files in **app-internal storage** (`context.filesDir`):

```kotlin
private fun getKeyFile(context: Context, keyId: String): File {
    val keysDir = File(context.filesDir, "ssh_keys")
    keysDir.mkdirs()
    return File(keysDir, "$keyId.enc")
}

fun saveEncryptedKey(context: Context, keyId: String, encryptedBytes: ByteArray) {
    getKeyFile(context, keyId).writeBytes(encryptedBytes)
}

fun loadEncryptedKey(context: Context, keyId: String): ByteArray? {
    val file = getKeyFile(context, keyId)
    return if (file.exists()) file.readBytes() else null
}
```

This location:
- Is in the app sandbox (other apps cannot read it without root)
- Is automatically deleted on app uninstall
- Can be excluded from backups (see section 7)

---

## 4. SSH Key Generation

### Preferred Algorithm: Ed25519

Ed25519 is the recommended algorithm for new SSH keys:
- Fastest signature verification
- Smallest key size (32 bytes private, 32 bytes public)
- Resistant to side-channel attacks by design
- Default in modern OpenSSH server configurations
- Fixed key size eliminates "what bit length?" decisions

### Fallback: RSA-4096

RSA-4096 should be available as a fallback for legacy servers that don't support Ed25519.

### Implementation with BouncyCastle

BouncyCastle is the standard provider for Ed25519 on Android, since the platform's built-in providers have limited Ed25519 support.

**Dependencies:**
```kotlin
implementation("org.bouncycastle:bcprov-jdk18on:1.79")
implementation("org.bouncycastle:bcpkix-jdk18on:1.79")
```

**Key Generation:**

```kotlin
import org.bouncycastle.crypto.generators.Ed25519KeyPairGenerator
import org.bouncycastle.crypto.params.Ed25519KeyGenerationParameters
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.util.OpenSSHPublicKeyUtil
import java.security.SecureRandom

data class SshKeyPair(
    val privateKeyBytes: ByteArray,
    val publicKeyBytes: ByteArray,
    val publicKeyOpenSsh: String,
    val algorithm: String
)

fun generateEd25519Key(comment: String = ""): SshKeyPair {
    val generator = Ed25519KeyPairGenerator()
    generator.init(Ed25519KeyGenerationParameters(SecureRandom()))
    val keyPair = generator.generateKeyPair()

    val privateKey = keyPair.private as Ed25519PrivateKeyParameters
    val publicKey = keyPair.public as Ed25519PublicKeyParameters

    // Encode public key in OpenSSH format
    val publicKeyEncoded = OpenSSHPublicKeyUtil.encodePublicKey(publicKey)
    val publicKeyBase64 = Base64.encodeToString(publicKeyEncoded, Base64.NO_WRAP)
    val publicKeyOpenSsh = "ssh-ed25519 $publicKeyBase64${if (comment.isNotEmpty()) " $comment" else ""}"

    return SshKeyPair(
        privateKeyBytes = privateKey.encoded,
        publicKeyBytes = publicKey.encoded,
        publicKeyOpenSsh = publicKeyOpenSsh,
        algorithm = "ed25519"
    )
}

fun generateRsa4096Key(comment: String = ""): SshKeyPair {
    val keyPairGen = java.security.KeyPairGenerator.getInstance("RSA")
    keyPairGen.initialize(4096, SecureRandom())
    val keyPair = keyPairGen.generateKeyPair()

    val publicKeyEncoded = OpenSSHPublicKeyUtil.encodePublicKey(
        org.bouncycastle.crypto.params.RSAKeyParameters(
            false,
            (keyPair.public as java.security.interfaces.RSAPublicKey).modulus,
            (keyPair.public as java.security.interfaces.RSAPublicKey).publicExponent
        )
    )
    val publicKeyBase64 = Base64.encodeToString(publicKeyEncoded, Base64.NO_WRAP)
    val publicKeyOpenSsh = "ssh-rsa $publicKeyBase64${if (comment.isNotEmpty()) " $comment" else ""}"

    return SshKeyPair(
        privateKeyBytes = keyPair.private.encoded, // PKCS8 format
        publicKeyBytes = keyPair.public.encoded,
        publicKeyOpenSsh = publicKeyOpenSsh,
        algorithm = "rsa-4096"
    )
}
```

### Key Format Considerations

| Format | When Used | Notes |
|--------|-----------|-------|
| OpenSSH (new format) | Key export, display to user | Required for Ed25519 private keys |
| PEM / PKCS8 | Interoperability with tools | RSA keys commonly in this format |
| Raw bytes | Internal storage (encrypted) | Smallest, fastest to process |
| OpenSSH public key | Copying to servers | `ssh-ed25519 AAAA... comment` format |

For internal storage, store the **raw private key bytes** encrypted. Only convert to other formats on export.

---

## 5. Key Import Flow

### Supported Import Methods

#### 1. File Picker (Primary)

```kotlin
// Use ACTION_OPEN_DOCUMENT for SAF (Storage Access Framework)
val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
    addCategory(Intent.CATEGORY_OPENABLE)
    type = "*/*" // SSH keys have no standard MIME type
}
```

After file selection:
1. Read file bytes via ContentResolver
2. Detect key format (PEM header, OpenSSH header, raw)
3. If passphrase-protected, prompt for passphrase
4. Parse the private key using BouncyCastle/SSHJ PEM parser
5. Extract raw key material
6. Encrypt with Tink Aead and store
7. **Zero the plaintext key bytes immediately**

#### 2. Clipboard Import (Secondary)

```kotlin
fun importFromClipboard(context: Context): ByteArray? {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clipData = clipboard.primaryClip ?: return null
    val text = clipData.getItemAt(0)?.text?.toString() ?: return null

    // Parse the key
    val keyBytes = parseSshPrivateKey(text)

    // IMMEDIATELY clear the clipboard
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        clipboard.clearPrimaryClip()
    }

    return keyBytes
}
```

**Security warnings for clipboard import:**
- Show a clear warning that clipboard contents may be logged by other apps
- Clear clipboard immediately after reading
- On Android 12+, show a toast that clipboard was accessed (system handles this)
- Recommend file-based import instead

#### 3. Handling Passphrase-Protected Keys

```kotlin
import org.bouncycastle.openssl.PEMParser
import org.bouncycastle.openssl.jcajce.JcePEMDecryptorProviderBuilder
import org.bouncycastle.openssl.PEMEncryptedKeyPair

fun decryptPemKey(pemString: String, passphrase: CharArray): KeyPair {
    val parser = PEMParser(StringReader(pemString))
    val pemObject = parser.readObject()

    return when (pemObject) {
        is PEMEncryptedKeyPair -> {
            val decryptor = JcePEMDecryptorProviderBuilder()
                .build(passphrase)
            val keyPair = pemObject.decryptKeyPair(decryptor)
            // Convert to java.security.KeyPair
            JcaPEMKeyConverter().getKeyPair(keyPair)
        }
        // Handle other PEM types...
        else -> throw IllegalArgumentException("Unsupported key format")
    }.also {
        // Zero the passphrase
        passphrase.fill('\u0000')
    }
}
```

#### 4. QR Code Import (Nice to Have)

For QR code import, the key would need to be encoded (e.g., base64) and split across multiple QR codes for larger keys (RSA). Ed25519 private keys (32 bytes) fit in a single QR code. Use the ZXing library for scanning. This is lower priority and should be deferred to a later release.

---

## 6. Biometric Authentication Gating

### Architecture

Biometric auth gates access to the **Keystore wrapping key**, not the SSH key directly. When the user authenticates biometrically, the Keystore unlocks the wrapping key, which then decrypts the SSH private key.

### Implementation

```kotlin
import androidx.biometric.BiometricPrompt
import androidx.biometric.BiometricManager
import javax.crypto.Cipher

class BiometricKeyAccess(private val activity: FragmentActivity) {

    fun canUseBiometric(): Boolean {
        val biometricManager = BiometricManager.from(activity)
        return biometricManager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG
                    or BiometricManager.Authenticators.DEVICE_CREDENTIAL
        ) == BiometricManager.BIOMETRIC_SUCCESS
    }

    fun decryptWithBiometric(
        encryptedKeyBytes: ByteArray,
        keyId: String,
        onSuccess: (ByteArray) -> Unit,
        onError: (String) -> Unit
    ) {
        // Initialize cipher for decryption with the Keystore wrapping key
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val wrappingKey = keyStore.getKey(WRAPPING_KEY_ALIAS, null) as SecretKey

        // Extract IV from encrypted data (first 12 bytes for GCM)
        val iv = encryptedKeyBytes.copyOfRange(0, 12)
        val ciphertext = encryptedKeyBytes.copyOfRange(12, encryptedKeyBytes.size)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, wrappingKey, GCMParameterSpec(128, iv))

        val cryptoObject = BiometricPrompt.CryptoObject(cipher)

        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    val decryptedCipher = result.cryptoObject?.cipher ?: return
                    val privateKeyBytes = decryptedCipher.doFinal(ciphertext)
                    onSuccess(privateKeyBytes)
                    // Caller MUST zero privateKeyBytes after use
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    onError(errString.toString())
                }

                override fun onAuthenticationFailed() {
                    // Biometric didn't match; system allows retry automatically
                }
            }
        )

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock SSH Key")
            .setSubtitle("Authenticate to use your SSH key")
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG
                        or BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
            .build()

        prompt.authenticate(promptInfo, cryptoObject)
    }
}
```

### Authentication Validity Period

The wrapping key is configured with `setUserAuthenticationParameters(300, ...)` -- 5 minutes. This means:
- First connection in a session requires biometric auth
- Subsequent connections within 5 minutes do not
- Configurable per user preference (0 = require every time)

This balances security with the usability need to reconnect quickly.

---

## 7. Key Lifecycle Security

### 7.1 Zeroing Key Material from Memory

**Critical requirement.** After the SSH private key is decrypted and used for authentication, the plaintext bytes must be zeroed.

```kotlin
fun <T> useKeySecurely(keyBytes: ByteArray, block: (ByteArray) -> T): T {
    return try {
        block(keyBytes)
    } finally {
        // Zero the key material
        keyBytes.fill(0)
    }
}

// Usage:
useKeySecurely(decryptedKeyBytes) { keyBytes ->
    sshSession.addPublicKeyIdentity(loadKeyPair(keyBytes))
    sshSession.auth().verify(30, TimeUnit.SECONDS)
}
// keyBytes is zeroed here, regardless of success/failure
```

**Caveats:**
- JVM garbage collection may copy byte arrays during compaction -- we cannot fully prevent this
- `String` objects are immutable and cannot be zeroed -- never store key material as String
- Use `CharArray` for passphrases and zero them after use
- `SecretKey.destroy()` throws `DestroyFailedException` on most Android versions -- use manual zeroing instead

### 7.2 Preventing Key Leakage via Backups

In `AndroidManifest.xml`:

```xml
<application
    android:allowBackup="false"
    android:fullBackupContent="@xml/backup_rules"
    android:dataExtractionRules="@xml/data_extraction_rules">
```

`res/xml/backup_rules.xml` (for API < 31):
```xml
<?xml version="1.0" encoding="utf-8"?>
<full-backup-content>
    <exclude domain="file" path="ssh_keys/" />
    <exclude domain="sharedpref" path="ssh_key_master_keyset_prefs.xml" />
</full-backup-content>
```

`res/xml/data_extraction_rules.xml` (for API 31+):
```xml
<?xml version="1.0" encoding="utf-8"?>
<data-extraction-rules>
    <cloud-backup>
        <exclude domain="file" path="ssh_keys/" />
        <exclude domain="sharedpref" path="ssh_key_master_keyset_prefs.xml" />
    </cloud-backup>
    <device-transfer>
        <exclude domain="file" path="ssh_keys/" />
        <exclude domain="sharedpref" path="ssh_key_master_keyset_prefs.xml" />
    </device-transfer>
</data-extraction-rules>
```

**Recommendation**: Set `android:allowBackup="false"` entirely. An SSH terminal app's keys should never be in cloud backups. Users can re-import keys on a new device.

### 7.3 Screenshot Protection

Apply `FLAG_SECURE` to activities that display sensitive key material:

```kotlin
// In Activity.onCreate() or Fragment.onResume()
window.setFlags(
    WindowManager.LayoutParams.FLAG_SECURE,
    WindowManager.LayoutParams.FLAG_SECURE
)

// For Android 13+, also hide from recents
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
    setRecentsScreenshotEnabled(false)
}
```

**Where to apply:**
- Key generation screen (shows public key)
- Key import screen
- Key details/export screen
- **NOT** the terminal screen itself -- users may legitimately want to screenshot terminal output

### 7.4 Secure Deletion

When a user deletes a key:

```kotlin
fun deleteKey(context: Context, keyId: String) {
    val keyFile = getKeyFile(context, keyId)
    if (keyFile.exists()) {
        // Overwrite with random data before deleting
        // (Limited effectiveness on flash storage but better than nothing)
        val random = SecureRandom()
        val randomBytes = ByteArray(keyFile.length().toInt())
        random.nextBytes(randomBytes)
        keyFile.writeBytes(randomBytes)
        keyFile.delete()
    }

    // Remove metadata from preferences/database
    removeKeyMetadata(context, keyId)
}
```

**Honest caveat**: Secure deletion on flash storage (all Android devices) is unreliable because of wear leveling. The encryption-at-rest approach means that deleting the Keystore wrapping key effectively destroys access to all encrypted SSH keys, even if encrypted file remnants exist on flash.

---

## 8. Host Key Verification (TOFU)

### Implementation

TOFU (Trust On First Use) is the standard SSH model. On first connection to a new host:

1. Display the server's host key fingerprint prominently
2. Show the key algorithm and hash (SHA-256)
3. Ask the user to verify and accept
4. Store the accepted key in a local known_hosts database

On subsequent connections:
1. Compare the server's key against stored key
2. If match: connect silently
3. If mismatch: **block connection** and show a clear warning

### Known Hosts Storage

```kotlin
data class KnownHost(
    val hostname: String,
    val port: Int,
    val keyType: String,       // e.g., "ssh-ed25519"
    val publicKeyBase64: String,
    val firstSeen: Long,       // timestamp
    val lastSeen: Long         // timestamp
)
```

Store in an encrypted local database or encrypted file. The known hosts database needs **integrity** protection (prevent tampering) more than confidentiality, but encrypting it provides both.

### Host Key Change Warning

When a host key changes, show a **prominent, scary warning**:

```
WARNING: HOST KEY HAS CHANGED

The host key for server.example.com:22 has changed.
This could indicate a man-in-the-middle attack.

Previous key (ssh-ed25519):
  SHA256:abc123def456...

New key (ssh-ed25519):
  SHA256:xyz789ghi012...

If you trust this change, you can update the stored key.
If unexpected, do NOT connect and investigate.

[Update Key]  [Disconnect]
```

### Fingerprint Display

Show fingerprints in the same format as OpenSSH for easy comparison:

```kotlin
fun formatFingerprint(publicKey: ByteArray, algorithm: String): String {
    val digest = MessageDigest.getInstance("SHA-256")
    val hash = digest.digest(publicKey)
    val base64 = Base64.encodeToString(hash, Base64.NO_WRAP)
    return "$algorithm SHA256:$base64"
}
```

### Visual Host Key

Consider implementing ASCII art visualization (randomart) similar to OpenSSH's `VisualHostKey` option. This gives users a visual pattern to recognize, which is easier than comparing hex strings. This is a nice-to-have feature.

---

## 9. Network Security

### No Password Authentication

The app uses SSH key-based authentication only. This is a security feature, not a limitation. Password authentication is vulnerable to:
- Brute force attacks
- Credential reuse
- Keylogging
- Shoulder surfing

### SSH Protocol Security

When configuring the SSH client library:

```kotlin
// Prefer modern algorithms
val preferredKeyExchangeAlgorithms = listOf(
    "curve25519-sha256",
    "curve25519-sha256@libssh.org",
    "ecdh-sha2-nistp521",
    "ecdh-sha2-nistp384",
    "ecdh-sha2-nistp256",
    "diffie-hellman-group18-sha512",
    "diffie-hellman-group16-sha512"
)

val preferredCiphers = listOf(
    "chacha20-poly1305@openssh.com",
    "aes256-gcm@openssh.com",
    "aes128-gcm@openssh.com",
    "aes256-ctr",
    "aes192-ctr",
    "aes128-ctr"
)

val preferredMacs = listOf(
    "hmac-sha2-512-etm@openssh.com",
    "hmac-sha2-256-etm@openssh.com",
    "hmac-sha2-512",
    "hmac-sha2-256"
)

val preferredHostKeyAlgorithms = listOf(
    "ssh-ed25519",
    "ecdsa-sha2-nistp521",
    "ecdsa-sha2-nistp384",
    "ecdsa-sha2-nistp256",
    "rsa-sha2-512",
    "rsa-sha2-256"
)
```

### Certificate Pinning

Certificate pinning is not applicable to SSH (SSH uses its own host key verification, not TLS certificates). Do not confuse the two -- SSH host key verification (TOFU / known_hosts) is the equivalent concept.

---

## 10. Recommendations Summary

### Architecture Decision: Layered Encryption

```
Layer 1: App Sandbox (OS-level isolation)
Layer 2: Tink Aead encryption (AES-256-GCM, Keystore-backed master key)
Layer 3: Biometric gating (Keystore auth-bound wrapping key)
Layer 4: FLAG_SECURE + backup exclusion (leak prevention)
```

### Key Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Encryption library | Google Tink | Modern, maintained, replaces deprecated JetSec |
| Key generation | BouncyCastle | Best Ed25519 support on Android |
| Default key type | Ed25519 | Smaller, faster, more secure than RSA |
| SSH library integration | SSHJ or Apache MINA SSHD | Both support Ed25519 + modern algorithms |
| Storage location | App internal storage | Sandboxed, auto-deleted on uninstall |
| Biometric approach | Keystore auth-bound key + BiometricPrompt | Real cryptographic binding, not just UI gate |
| Backup policy | Disabled for key material | Keys should never leave the device via backup |
| Host key verification | TOFU with prominent warnings | Standard SSH model, good UX for mobile |

### Implementation Priority

1. **Must have (MVP)**:
   - Tink-encrypted key storage with Keystore-backed master key
   - Ed25519 and RSA-4096 key generation
   - File-based key import with passphrase support
   - TOFU host key verification
   - Backup exclusion
   - Memory zeroing of key material

2. **Should have (v1.0)**:
   - Biometric authentication gating
   - FLAG_SECURE on key management screens
   - Clipboard import with warnings
   - Host key change warnings with visual prominence
   - Modern algorithm preferences

3. **Nice to have (future)**:
   - QR code key import
   - Visual host key fingerprints (randomart)
   - StrongBox preference detection
   - Key export functionality (encrypted)

### Security Libraries Summary

| Library | Version | Purpose |
|---------|---------|---------|
| `com.google.crypto.tink:tink-android` | 1.15.0+ | Encryption at rest |
| `org.bouncycastle:bcprov-jdk18on` | 1.79+ | Ed25519 key generation |
| `org.bouncycastle:bcpkix-jdk18on` | 1.79+ | PEM parsing, key format conversion |
| `androidx.biometric:biometric` | 1.2.0+ | BiometricPrompt |
| `androidx.security:security-crypto` | **DEPRECATED** | Do NOT use |

---

## References

- [Android Keystore System](https://developer.android.com/privacy-and-security/keystore)
- [Hardware-backed Keystore](https://source.android.com/docs/security/features/keystore)
- [BiometricPrompt with CryptoObject](https://medium.com/androiddevelopers/using-biometricprompt-with-cryptoobject-how-and-why-aace500ccdb7)
- [Google Tink Streaming AEAD](https://developers.google.com/tink/streaming-aead)
- [Goodbye EncryptedSharedPreferences: A 2026 Migration Guide](https://www.droidcon.com/2025/12/16/goodbye-encryptedsharedpreferences-a-2026-migration-guide/)
- [Android Password Store SSH Key Generation](https://github.com/android-password-store/Android-Password-Store/wiki/Generate-SSH-Key)
- [Termius Biometric Keys](https://termius.com/documentation/android-biometric-keys)
- [Android FLAG_SECURE Documentation](https://developer.android.com/security/fraud-prevention/activities)
- [SSHJ Key Authentication Formats](https://exceptionfactory.com/posts/2023/04/06/sshj-key-authentication-formats/)
- [SSH Key Best Practices 2025 - Ed25519](https://www.brandonchecketts.com/archives/ssh-ed25519-key-best-practices-for-2025)
- [Android Keystore Pitfalls and Best Practices (Stytch)](https://stytch.com/blog/android-keystore-pitfalls-and-best-practices/)
- [encrypted-datastore (Tink + DataStore)](https://github.com/osipxd/encrypted-datastore)
- [Apache MINA SSHD Client Setup](https://github.com/apache/mina-sshd/blob/master/docs/client-setup.md)
