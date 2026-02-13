# PocketSSH Privacy Policy

**Effective Date:** February 12, 2026

PocketSSH is a free Android SSH terminal emulator. This privacy policy explains how the app handles your data.

**The short version:** PocketSSH does not collect, transmit, or share any personal data. Everything stays on your device.

## Data Storage

PocketSSH stores the following data **locally on your device only**:

- **SSH connection profiles** (hostnames, ports, usernames) in a local database
- **SSH private keys**, encrypted with AES-256-GCM using the Android Keystore system
- **App settings and preferences** in local Android DataStore

None of this data is sent to PocketSSH developers, third-party servers, or any external service.

## Data Not Collected

PocketSSH does **not**:

- Collect analytics or usage statistics
- Include advertising or ad tracking SDKs
- Require user accounts or registration
- Transmit telemetry or crash reports
- Phone home to any server
- Share data with any third party

## Network Connections

PocketSSH connects **only** to SSH servers that you explicitly configure. The app does not make any other network connections. Your SSH credentials (passwords and private keys) are sent only to the servers you choose to connect to.

## Permissions

PocketSSH requests the following Android permissions:

| Permission | Why It's Needed |
|---|---|
| **INTERNET** | To establish SSH connections to your servers |
| **ACCESS_NETWORK_STATE** | To check network availability before connecting |
| **USE_BIOMETRIC** | To optionally protect SSH keys with fingerprint/face authentication |
| **VIBRATE** | To provide haptic feedback on terminal key presses |
| **FOREGROUND_SERVICE** | To keep SSH sessions alive while the app is in the background |

## Data Security

- SSH private keys are encrypted using AES-256-GCM with keys managed by the Android Keystore hardware-backed security system
- Biometric authentication can be enabled as an additional layer of protection for stored keys
- All sensitive data remains on-device and is protected by Android's application sandboxing

## Children's Privacy

PocketSSH is a technical tool intended for users who manage remote servers. It is not directed at children under 13. The app does not knowingly collect any data from anyone, including children.

## Changes to This Policy

If this privacy policy is updated, the new version will be posted here with a revised effective date. Since the app collects no data, significant changes are unlikely.

## Contact

If you have questions about this privacy policy, contact:

**PocketSSH Developer**
Email: vilius365@gmail.com

## Open Source

PocketSSH is open source. You can review the complete source code to verify these privacy claims.
