# Security notes

## What is protected

- **At rest.** Photos, files and notes are AES-256-GCM encrypted in the app's private storage. The 32-byte master key exists only in memory while the app is unlocked. It is stored wrapped by a key derived from the PIN (PBKDF2-HMAC-SHA256, 150,000 rounds, 16-byte random salt) and optionally by a hardware-backed Keystore key that requires fingerprint authentication.
- **In transit.** Chat, voice and video frames are encrypted per frame with AES-256-GCM under per-session, per-direction keys derived from an ephemeral ECDH exchange. This holds for both transports: Wi-Fi and Bluetooth.
- **No server anywhere.** There is no backend, no account, no telemetry, no relay, and no network use other than the direct peer-to-peer link.

## What is not protected

- A rooted device or a compromised OS can read memory while the app is unlocked.
- Screenshots are not blocked.
- **Metadata.** Peers can see each other's device on the local link (Wi-Fi network or Bluetooth pairing). That two devices connected, and roughly how much data moved, is visible on the link.
- Messages are not persisted; when the connection drops the session keys are discarded.
- Sessions are ephemeral but there is no double ratchet and no post-compromise security within a session. For a threat model that needs that, use Signal.
- Because both modes are local, there is no protection against someone physically near the link — the safety number is your check.

## Verified by automated tests

`ProtocolTest.java` runs the handshake on a desktop JVM and checks: both directions derive matching keys, the keys differ per direction, safety numbers agree on both sides, frames decrypt, tampered frames are rejected, and a wrong PIN fails to unwrap the master key.

## Reporting

This is a personal project. If you find a flaw, open an issue with reproduction steps.
