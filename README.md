# VaultChat

A private Android app for photos, files, notes, chat and calls — everything is encrypted on the device.

Built as a single dependency-free Android app (no AndroidX, no Gradle plugins, no third-party libraries), so the whole APK is about 50 KB and the entire codebase is auditable in one sitting.

## What it does

| Area | How it is protected |
|---|---|
| Photos, videos, files | Each file is encrypted with AES-256-GCM and stored inside the app's private directory. Filenames are random UUIDs; the index itself is encrypted too. |
| Notes | Stored in the same encrypted index. |
| Unlock | 6-digit PIN stretched with PBKDF2-HMAC-SHA256 (150,000 rounds, random 16-byte salt) to wrap a random 32-byte master key. Fingerprint unlock wraps the same master key with a hardware-backed Android Keystore key that requires user authentication. |
| Chat | Encrypted peer-to-peer, over Wi-Fi, Bluetooth or the internet. |
| Voice call | 16 kHz mono PCM, one AES-GCM frame per 20 ms, inside the same encrypted session. |
| Video call | Camera frames (640×480 JPEG, ~10 fps) inside the same encrypted session. |

## Three ways to connect

| Mode | Needs | Works when |
|---|---|---|
| **Wi-Fi / hotspot** | Both phones on the same network | Same room or same house |
| **Bluetooth** | Bluetooth on, phones paired once in system settings | No Wi-Fi, no hotspot, no internet. Range ~10 m |
| **Internet** | A switchboard server you host, data on both phones | Anywhere — you dial the other person by name or phone number |

The encryption layer is identical in all three. Only the pipe changes.

### Internet mode

You type the other person's **name or phone number**, not an IP address. Both phones register that ID with your switchboard, and the server bridges whichever two want to talk.

> Important: this is **not** dialling a real phone number over the telephone network. Both people must have VaultChat installed. The number is only used as an ID on your own server.

**Setup, once per phone:** open *Secure chat → Internet*, enter the server address (`host:port`) and your own name or number, save.

**To call:** one phone presses *Wait for a call* and stays on that screen; the other types the first phone's ID and presses *Call*.

**Hosting the switchboard** (`relay/switchboard.py`, ~180 lines, Python 3.8+, nothing to install):

```bash
PORT=9000 python3 relay/switchboard.py
```

It needs a host with a **raw TCP port**: a small VPS (DigitalOcean, Hetzner, Lightsail — roughly ₹400–800/month), Fly.io with a TCP service, or a home machine with port forwarding. HTTP-only free tiers will not work. A `Dockerfile` is included.

## Chat and call protocol

1. Both phones generate an **ephemeral ECDH key pair** (secp256r1) per session.
2. Public keys are exchanged over whichever transport is in use.
3. HKDF-SHA256 over the *sorted* pair of public keys produces **two independent AES-256-GCM keys**, one per direction, so the two sides never share a key.
4. Both phones display the same **safety number** (six groups of five digits, derived from both public keys). Comparing it on screen rules out a man in the middle — including the switchboard itself.
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

- **Internet mode needs a server that you run.** There is no way around this: two phones on mobile data cannot find each other without something in between. The server never sees plaintext, but it does see who registered and who called whom.
- **Internet mode only reaches people who also have VaultChat.** It cannot call a normal phone.
- The waiting phone must keep the app open on the Internet screen — there is no push notification or background service yet.
- **Bluetooth** is short range and low bandwidth. Chat and voice are fine; video will be choppy.
- The APK is **self-signed**, so Android shows the "install from unknown source" prompt. It is not on Play Store.
- Voice audio is narrowband (16 kHz) on purpose.
- The app compiles and its crypto, the switchboard, and the full client call flow are covered by automated tests, but it has not been run on physical hardware in this build environment.
- Forgetting the PIN means the data is unrecoverable — that is the point of the design.

## Licence

MIT.
