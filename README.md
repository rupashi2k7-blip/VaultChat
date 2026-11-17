# VaultChat

A private Android app for photos, files, notes, chat and calls — everything is encrypted on the device, and nothing is uploaded to any server.

Built as a single dependency-free Android app (no AndroidX, no Gradle plugins, no third-party libraries), so the whole APK is about 50 KB and the entire codebase is auditable in one sitting.

## What it does

| Area | How it is protected |
|---|---|
| Photos, videos, files | Each file is encrypted with AES-256-GCM and stored inside the app's private directory. Filenames are random UUIDs; the index itself is encrypted too. |
| Notes | Stored in the same encrypted index. |
| Unlock | 6-digit PIN stretched with PBKDF2-HMAC-SHA256 (150,000 rounds, random 16-byte salt) to wrap a random 32-byte master key. Fingerprint unlock wraps the same master key with a hardware-backed Android Keystore key that requires user authentication. |
| Chat | Encrypted peer-to-peer over one of three transports (below). |
| Voice call | 16 kHz mono PCM, one AES-GCM frame per 20 ms, inside the same encrypted session. |
| Video call | Camera frames (640×480 JPEG, ~10 fps) inside the same encrypted session. |

## Three ways to connect

| Mode | Needs | Works when |
|---|---|---|
| **Wi-Fi / hotspot** | Both phones on the same network | Same room or same house |
| **Bluetooth** | Bluetooth on, phones paired once in system settings | No Wi-Fi, no hotspot, no internet, no server. Range ~10 m |
| **Relay** | A relay server you host (see below), internet on both phones | Anywhere — different cities, mobile data |

The encryption layer is identical in all three. Only the pipe changes.

### Relay setup (for the "anywhere" mode)

The relay is a ~120 line Python script in `relay/relay.py`. It pairs two phones by room code and forwards bytes between them. It cannot read anything: the phones perform an ECDH handshake *through* the relay and encrypt every frame, so the relay only moves ciphertext.

You need a host that gives a **raw TCP port**. HTTP-only free tiers (for example a Render "web service") will not work.

```bash
# on any VPS, or a machine with port forwarding
PORT=9000 python3 relay/relay.py
```

Then in the app: **Secure chat → Relay**, enter `your-host:9000`, and share the generated room code with the other phone.

Deployment options: any small VPS (DigitalOcean, Hetzner, Lightsail — roughly ₹400–800/month), Fly.io with a TCP service, Railway, or a home machine with port forwarding. There is nothing to install beyond Python 3.8+.

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

- **Bluetooth** is short range and low bandwidth. Chat and voice are fine; video will be choppy. Both phones must be paired in the system settings first.
- **Relay mode is only as private as your relay** in the sense that whoever runs it sees connection metadata (two IPs, a room code, and how many bytes flowed). It never sees content. Always compare the safety number.
- Room codes are guessable if short. Use the generated 9-character code and share it privately. If an attacker joins your room first, the handshake still fails the safety-number check — but compare it.
- The APK is **self-signed**, so Android shows the "install from unknown source" prompt. It is not on Play Store.
- Voice audio is narrowband (16 kHz) on purpose.
- The app compiles and its crypto and relay have been verified with automated tests (see `ProtocolTest.java` and `relay/`), but it has not been run on physical hardware in this build environment.
- Forgetting the PIN means the data is unrecoverable — that is the point of the design.

## Licence

MIT.
