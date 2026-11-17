# VaultChat

A private Android app for photos, files, notes, chat and calls — everything is encrypted on the device, and nothing is uploaded to any server.

Built as a single dependency-free Android app (no AndroidX, no Gradle plugins, no third-party libraries), so the whole APK is about 50 KB and the entire codebase is auditable in one sitting.

## What it does

| Area | How it is protected |
|---|---|
| Photos, videos, files | Each file is encrypted with AES-256-GCM and stored inside the app's private directory. Filenames are randomised UUIDs; the index itself is encrypted too. |
| Notes | Stored in the same encrypted index. |
| Unlock | 6-digit PIN stretched with PBKDF2-HMAC-SHA256 (150,000 rounds, random 16-byte salt) to wrap a random 32-byte master key. Fingerprint unlock wraps the same master key with a hardware-backed Android Keystore key that requires user authentication. |
| Chat | Peer-to-peer over the local network (Wi-Fi or hotspot). No internet, no relay, no server. |
| Voice call | 16 kHz mono PCM, one AES-GCM frame per 20 ms, inside the same encrypted session. |
| Video call | Camera frames (640×480 JPEG, ~10 fps) inside the same encrypted session. |

## Chat and call protocol

1. Both phones generate an **ephemeral ECDH key pair** (secp256r1) per session.
2. Public keys are exchanged, a shared secret is derived with ECDH.
3. HKDF-SHA256 over the sorted pair of public keys produces **two independent AES-256-GCM keys**, one per direction, so the two sides never share a key.
4. Both phones display the same **safety number** (six groups of five digits, derived from both public keys). Comparing it on screen rules out a man in the middle.
5. Every frame is `[4-byte length][12-byte IV][AES-GCM(type || payload)]`. Tampered or replayed frames fail authentication and are dropped.

Ephemeral keys mean the session keys are never reused between connections (no forward secrecy across a session, but nothing is stored on disk either).

## Building

```bash
# needs JDK 17 and Android build-tools 34 + platform android-35
bash build.sh
# -> build/VaultChat.apk
```

`build.sh` drives `aapt2`, `javac`, `d8`, `zipalign` and `apksigner` directly.

## Honest limits

- Calls and chat only work when **both phones are on the same Wi-Fi or hotspot**. There is no internet relay, so no calling across different networks. Adding that requires a TURN/relay server, which this app deliberately does not include.
- The APK is **self-signed** with a local keystore, so Android shows the "install from unknown source" prompt. It is not on Play Store.
- Voice/video quality depends on the local network. Audio is narrowband (16 kHz) on purpose.
- The app has been compiled and its crypto verified with an automated test, but it has not been run on physical hardware in this build environment.
- Forgetting the PIN means the data is unrecoverable — that is the point of the design.

## Licence

MIT.
