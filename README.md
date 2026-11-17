# VaultChat

A private Android app for photos, files, notes, chat and calls — everything is encrypted on the device, and nothing is uploaded to any server.

Built as a single dependency-free Android app (no AndroidX, no Gradle plugins, no third-party libraries), so the whole APK is about 50 KB and the entire codebase is auditable in one sitting.

## What it does

| Area | How it is protected |
|---|---|
| Photos, videos, files | Each file is encrypted with AES-256-GCM and stored inside the app's private directory. Filenames are random UUIDs; the index itself is encrypted too. |
| Notes | Stored in the same encrypted index. |
| Unlock | 6-digit PIN stretched with PBKDF2-HMAC-SHA256 (150,000 rounds, random 16-byte salt) to wrap a random 32-byte master key. Fingerprint unlock wraps the same master key with a hardware-backed Android Keystore key that requires user authentication. |
| Chat | Encrypted peer-to-peer over Wi-Fi/hotspot or Bluetooth. No server, no account, no cloud. |
| Voice call | 16 kHz mono PCM, one AES-GCM frame per 20 ms, inside the same encrypted session. |
| Video call | Camera frames (640×480 JPEG, ~10 fps) inside the same encrypted session. |

## Two ways to connect

| Mode | Needs | Works when |
|---|---|---|
| **Wi-Fi / hotspot** | Both phones on the same network | Same room, same house, or one phone's hotspot |
| **Bluetooth** | Bluetooth on, phones paired once in system settings | No Wi-Fi, no hotspot, no internet, no server. Range ~10 m |

The encryption layer is identical in both. Only the pipe changes.

There is deliberately no relay mode: it would require a server, and this app does not need one.

## Chat and call protocol

1. Both phones generate an **ephemeral ECDH key pair** (secp256r1) per session.
2. Public keys are exchanged over whichever transport is in use.
3. HKDF-SHA256 over the *sorted* pair of public keys produces **two independent AES-256-GCM keys**, one per direction, so the two sides never share a key.
4. Both phones display the same **safety number** (six groups of five digits, derived from both public keys). Comparing it on screen rules out a man in the middle.
5. Every frame is `[4-byte length][12-byte IV][AES-GCM(type || payload)]`. Tampered or replayed frames fail authentication and are dropped.

Keys are ephemeral, so nothing is reusable between sessions.

## Building

```bash
# needs JDK 17 and an Android SDK with build-tools 34+ and any platform (34 or newer)
export ANDROID_HOME=/path/to/Android/Sdk
bash build.sh
# -> build/VaultChat.apk
```

`build.sh` drives `aapt2`, `javac`, `d8`, `zipalign` and `apksigner` directly.

## Honest limits

- Both modes are **local**: the two phones must be within range of each other. Calling someone in another city is not supported and cannot be without a server.
- **Bluetooth** is short range and low bandwidth. Chat and voice are fine; video will be choppy. Both phones must be paired in the system settings first.
- The APK is **self-signed**, so Android shows the "install from unknown source" prompt. It is not on Play Store.
- Voice audio is narrowband (16 kHz) on purpose.
- The app compiles and its crypto is verified by `ProtocolTest.java`, but it has not been run on physical hardware in this build environment.
- Forgetting the PIN means the data is unrecoverable — that is the point of the design.

## Licence

MIT.
