# PocketSSH - Release Build & Play Store Checklist

## 1. App Signing

### Google Play App Signing (Mandatory for New Apps)

Google Play manages the **app signing key** (the key used to sign the APK delivered to users). You provide an **upload key** to sign the AAB you upload to the Play Console. Google then re-signs with the app signing key.

**Key distinction:**
- **App signing key** -- Managed by Google. Used to sign the final APK. Cannot be lost since Google holds it.
- **Upload key** -- You control this. Used to sign AABs before upload. Can be reset via Play Console support if lost.

### Generate an Upload Keystore

If no upload keystore exists yet, generate one:

```bash
keytool -genkeypair \
  -alias pocketssh-upload \
  -keyalg RSA \
  -keysize 2048 \
  -validity 25000 \
  -keystore pocketssh-upload.jks \
  -storepass <STORE_PASSWORD> \
  -keypass <KEY_PASSWORD> \
  -dname "CN=PocketSSH,O=<YOUR_ORG>,L=<CITY>,ST=<STATE>,C=<COUNTRY>"
```

**Store the keystore file and passwords securely.** Losing the upload key requires contacting Google Play support to reset it. Never commit the keystore or passwords to version control.

### Configure Signing in build.gradle.kts

Add a `signingConfigs` block to `app/build.gradle.kts`. Load credentials from environment variables or a local `keystore.properties` file (gitignored):

```kotlin
// Read from keystore.properties (do NOT commit this file)
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = java.util.Properties()
if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(keystorePropertiesFile.inputStream())
}

android {
    signingConfigs {
        create("release") {
            storeFile = file(keystoreProperties["storeFile"] as? String ?: "")
            storePassword = keystoreProperties["storePassword"] as? String ?: ""
            keyAlias = keystoreProperties["keyAlias"] as? String ?: ""
            keyPassword = keystoreProperties["keyPassword"] as? String ?: ""
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            // ... existing config
        }
    }
}
```

Example `keystore.properties` (add to `.gitignore`):

```properties
storeFile=../pocketssh-upload.jks
storePassword=your_store_password
keyAlias=pocketssh-upload
keyPassword=your_key_password
```

---

## 2. Build Configuration Review

### Current State (app/build.gradle.kts)

| Setting | Value | Status |
|---------|-------|--------|
| applicationId | `com.pocketssh.app` | OK |
| minSdk | 29 (Android 10) | OK |
| targetSdk | 35 (Android 15) | OK -- meets Play Store requirement (target API 35 required for new apps as of Aug 2025) |
| compileSdk | 35 | OK |
| versionCode | 1 | OK for initial release |
| versionName | 1.0.0 | OK |
| isMinifyEnabled (release) | true | OK |
| isShrinkResources (release) | true | OK |
| debug applicationIdSuffix | `.debug` | OK -- debug and release can coexist on same device |

### Items Requiring Attention

1. **No release signingConfig** -- The release build type does not have a `signingConfig`. Without this, `bundleRelease` produces an unsigned AAB. See Section 1 above.

2. **settings.gradle.kts rootProject.name** -- Currently set to `"ClaudeTerminal"` (old name). Update to `"PocketSSH"` for consistency. This affects Gradle project naming but not the published app.

3. **FOREGROUND_SERVICE permission declared but unused** -- `AndroidManifest.xml` declares `android.permission.FOREGROUND_SERVICE` but no Service class or `startForeground()` call exists in the codebase. On Android 14+ (API 34), foreground services also require `FOREGROUND_SERVICE_*` type permissions. **Recommendation:** Remove the `FOREGROUND_SERVICE` permission before release unless a foreground service is planned. Unused permissions can cause Play Store review questions.

4. **VIBRATE permission** -- Declared in manifest. Verify it is used (e.g., for haptic feedback on key press). If unused, remove it.

5. **No crash reporting** -- No crash reporting library (Firebase Crashlytics, Sentry, etc.) is included. Consider adding one before launch to diagnose production crashes. This is optional but strongly recommended.

---

## 3. AAB Build Instructions

Google Play requires **Android App Bundle (.aab)** format for all new apps (APK uploads are no longer accepted for new apps).

### Build the Release AAB

```bash
# Ensure signing is configured first (see Section 1)
./gradlew clean bundleRelease
```

Output location:
```
app/build/outputs/bundle/release/app-release.aab
```

### Test the AAB Locally

Use `bundletool` to generate APKs from the AAB for local testing:

```bash
# Download bundletool from https://github.com/google/bundletool/releases
java -jar bundletool.jar build-apks \
  --bundle=app/build/outputs/bundle/release/app-release.aab \
  --output=pocketssh.apks \
  --local-testing

java -jar bundletool.jar install-apks --apks=pocketssh.apks
```

### AAB Benefits
- Smaller downloads -- Google Play generates optimized APKs per device configuration
- Dynamic delivery support for future modularization
- Required format for Play Store submission

---

## 4. ProGuard / R8 Rules Review

Current rules in `app/proguard-rules.pro`:

| Library | Rule | Status |
|---------|------|--------|
| ConnectBot sshlib (trilead-ssh2) | `-keep class com.trilead.ssh2.** { *; }` | OK -- trilead uses reflection for crypto provider loading |
| Termux terminal-emulator | `-keep class com.termux.terminal.** { *; }` | OK -- accessed via JNI-style calls |
| BouncyCastle | `-keep class org.bouncycastle.** { *; }` | OK -- crypto providers are loaded by name via reflection |
| Google Tink | `-keep class com.google.crypto.tink.** { *; }` | NOTE: Tink is not in dependencies. This rule is harmless but unnecessary. Consider removing for cleanliness. |
| Room | `-keep class * extends androidx.room.RoomDatabase` | OK -- Room uses code generation but database class must survive |
| Hilt / Dagger | `-keep class dagger.hilt.** { *; }` and javax.inject | OK -- DI framework needs reflection access |

### Recommendations

- **Remove Tink rule** if Google Tink is not a dependency (it is not in `libs.versions.toml`).
- The existing rules are comprehensive. The broad `-keep` on trilead-ssh2 and BouncyCastle is intentionally conservative -- these crypto libraries are highly sensitive to R8 stripping.
- **Test the release build thoroughly** after any ProGuard rule changes. Crypto and SSH handshake failures are the most common R8-related issues.

---

## 5. Pre-Launch Checklist

### Code Quality

- [ ] **No debug logging**: Confirmed -- no `android.util.Log` calls found in source (good).
- [ ] **No BuildConfig.DEBUG guards**: Confirmed -- no debug-conditional code found.
- [ ] **No hardcoded IPs or test credentials**: Confirmed -- no IP addresses or hardcoded secrets in source.
- [ ] **Network security config**: `cleartextTrafficPermitted="false"` is set. Good -- SSH connections use their own socket, not HTTP.
- [ ] **Backup disabled**: `android:allowBackup="false"` is set. Appropriate for an app storing SSH credentials.

### Permissions Audit

| Permission | Justification | Action |
|------------|--------------|--------|
| `INTERNET` | Required for SSH connections | Keep |
| `ACCESS_NETWORK_STATE` | Check connectivity before SSH connect | Keep |
| `USE_BIOMETRIC` | Biometric auth for SSH key access | Keep |
| `VIBRATE` | Haptic feedback | Verify usage or remove |
| `FOREGROUND_SERVICE` | Not used | **Remove before release** |

### Build Verification

- [ ] Run `./gradlew clean bundleRelease` successfully
- [ ] Install release APK (via bundletool) on a physical device
- [ ] Test SSH connection, terminal rendering, and key management in release build
- [ ] Verify R8 has not broken crypto operations (SSH handshake succeeds)
- [ ] Verify terminal emulator renders correctly (Termux library intact)
- [ ] Verify Room database operations work (create/edit/delete connections)
- [ ] Test biometric prompt appears and works correctly

### Play Store Readiness

- [ ] App icon meets specifications (512x512 PNG for store listing, adaptive icon in app)
- [ ] Feature graphic prepared (1024x500)
- [ ] Screenshots for required device types (phone, optionally tablet)
- [ ] Short description (80 char max) and full description written
- [ ] Privacy policy URL ready (required for apps requesting INTERNET permission)
- [ ] Content rating questionnaire completed in Play Console
- [ ] Target audience and content settings configured
- [ ] App category selected (Tools or Productivity)
- [ ] Contact email set in Play Console

### Post-Upload

- [ ] Upload AAB to internal testing track first
- [ ] Run pre-launch report (automated testing by Google)
- [ ] Review pre-launch report for crashes or warnings
- [ ] Promote to closed testing (invite beta testers)
- [ ] After validation, promote to production

---

## 6. Action Items Summary

| Priority | Item | Effort |
|----------|------|--------|
| **Required** | Add release signing config to build.gradle.kts | Low |
| **Required** | Generate upload keystore | Low |
| **Required** | Add `keystore.properties` to `.gitignore` | Trivial |
| **Recommended** | Remove unused `FOREGROUND_SERVICE` permission | Trivial |
| **Recommended** | Verify `VIBRATE` permission is used or remove | Trivial |
| **Recommended** | Update rootProject.name in settings.gradle.kts to "PocketSSH" | Trivial |
| **Recommended** | Remove unused Tink ProGuard rule | Trivial |
| **Optional** | Add crash reporting (Crashlytics/Sentry) | Medium |
| **Optional** | Add Play Store pre-launch opt-in for automated device testing | Low |
