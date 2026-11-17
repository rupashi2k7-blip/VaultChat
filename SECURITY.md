# Security notes

## What is protected

- **At rest.** Photos, files and notes are AES-256-GCM encrypted in the app's private storage. The 32-byte master key exists only in memory while the app is unlocked. It is stored wrapped by a key derived from the PIN (PBKDF2-HMAC-SHA256, 150,000 rounds, 16-byte random salt) and optionally by a hardware-backed Keystore key that requires fingerprint authentication.
- **In transit.** Chat, voice and video frames are encrypted per frame with AES-256-GCM under per-session, per-direction keys derived from an ephemeral ECDH exchange.
- **No server.** There is no backend, no account, no telemetry, and no network use other than the direct peer-to-peer socket.

## What is not protected

- A rooted device or a compromised OS can read memory while the app is unlocked.
- Screenshots are not blocked.
- Metadata (that two devices connected, and roughly how much data was sent) is visible on the local network.
- Messages are not persisted; when the connection drops the session keys are discarded.
- Sessions are ephemeral but not forward-secret across a single session, and there is no double ratchet. For a threat model that needs that, use Signal.

## Verified

`ProtocolTest.java` checks the handshake end to end on a desktop JVM: both directions derive matching keys, the keys differ per direction, safety numbers agree on both sides, frames decrypt, tampered frames are rejected, and a wrong PIN fails to unwrap the master key.

## Reporting

This is a personal project. If you find a flaw, open an issue with reproduction steps.
