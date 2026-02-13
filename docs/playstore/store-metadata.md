# PocketSSH - Play Store Metadata & Declarations

## App Identity

| Field | Value |
|---|---|
| **App name** | PocketSSH |
| **Package name** | com.pocketssh.app |
| **Category** | Tools |
| **Contact email** | vilius365@gmail.com |
| **Default language** | English (US) |

## Content Rating Questionnaire (IARC)

Answer these in Google Play Console under **Content rating > Start questionnaire**.

| Question | Answer |
|---|---|
| Does the app contain violence? | No |
| Does the app contain sexual content? | No |
| Does the app reference drugs? | No |
| Does the app contain crude humor? | No |
| Does the app enable user-to-user communication? | No |
| Does the app share user location? | No |
| Does the app allow purchases? | No |
| Does the app contain ads? | No |
| Does the app allow users to interact with each other? | No |

**Expected rating:** PEGI 3 / Everyone

## Target Audience & Content

| Field | Value |
|---|---|
| **Target age group** | 18 and over (select only the highest bracket) |
| **Appeals to children?** | No |
| **Is this a news app?** | No |

> Select "18 and over" only. Do NOT include any age groups under 13. This avoids Designed for Families requirements.

## Distribution

| Field | Value |
|---|---|
| **Countries** | All available countries |
| **Free or Paid** | Free |
| **Contains ads** | No |
| **In-app purchases** | No |

## Data Safety Section

Google Play Console > **App content > Data safety**. Answer the following:

### Overview Questions

| Question | Answer |
|---|---|
| Does your app collect or share any user data? | **No** |

Since the answer is "No", the remaining data safety form is minimal. Google will show "No data collected" on the store listing.

### If Google requires more detail (expanded form)

In case the form requires specifics even after answering "No":

| Data Type | Collected? | Shared? | Notes |
|---|---|---|---|
| Name | No | No | - |
| Email address | No | No | - |
| Phone number | No | No | - |
| Location | No | No | - |
| Financial info | No | No | - |
| Photos/videos | No | No | - |
| Files/docs | No | No | - |
| Contacts | No | No | - |
| App activity | No | No | - |
| Web browsing | No | No | - |
| Crash logs | No | No | - |
| Diagnostics | No | No | - |
| Device identifiers | No | No | - |

### Security Practices

| Question | Answer |
|---|---|
| Is data encrypted in transit? | **Yes** (SSH is encrypted by definition) |
| Do you provide a way for users to request data deletion? | **Yes** (users can delete connection profiles and keys from within the app) |

## App Access (If Requested)

If Google requests access instructions for app review:

> PocketSSH requires a remote SSH server to demonstrate full functionality. The app connects to user-provided servers only. To test: install the app, add a connection profile with any reachable SSH server, and connect. No account creation or special credentials are needed within the app itself.

## Declarations Checklist

- [x] App does not target children under 13
- [x] App contains no ads
- [x] App has no in-app purchases
- [x] App is not a government app
- [x] App does not use background location
- [x] Privacy policy URL provided (host privacy-policy.html and enter URL)
- [x] App does not use health-related permissions
- [x] App is not a COVID-19 app
- [x] App does not use VPN permissions
- [x] App does not use All Files Access

## Permissions Declaration

If prompted to justify permissions in Play Console:

| Permission | Justification |
|---|---|
| INTERNET | Core functionality: establishing SSH connections to user-configured remote servers |
| ACCESS_NETWORK_STATE | Checking network availability before attempting SSH connections |
| USE_BIOMETRIC | Optional feature: users can protect stored SSH keys with biometric authentication |
| VIBRATE | Haptic feedback when pressing terminal keyboard keys |
| FOREGROUND_SERVICE | Keeping SSH sessions alive when the app is in the background so sessions are not dropped |
