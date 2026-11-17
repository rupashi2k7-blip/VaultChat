# Security notes

## What is protected

- **At rest.** Photos, files and notes are AES-256-GCM encrypted in the app's private storage. The 32-byte master key exists only in memory while the app is unlocked. It is stored wrapped by a key derived from the PIN (PBKDF2-HMAC-SHA256, 150,000 rounds, 16-byte random salt) and optionally by a hardware-backed Keystore key that requires fingerprint authentication.
- **In transit.** Chat, voice and video frames are encrypted per frame with AES-256-GCM under per-session, per-direction keys derived from an ephemeral ECDH exchange. This is true for all three transports: Wi-Fi, Bluetooth and the relay.
- **The relay cannot read content.** It forwards opaque frames between two peers in the same room. It learns: two IP addresses, a room code, connection timing, and byte counts.

## What is not protected

- A rooted device or a compromised OS can read memory while the app is unlocked.
- Screenshots are not blocked.
- **Metadata.** On Wi-Fi and Bluetooth, peers see each other's device on the local link. In relay mode, the relay operator sees both IP addresses and the room code. Room codes travel in the clear from app to relay; everything after that is ciphertext.
- **Room squatting.** Anyone who guesses a room code can occupy the second slot. They cannot complete the handshake convincingly — the safety number will not match — but they can cause a failed connection. Compare the safety number, always.
- Messages are not persisted; when the connection drops the session keys are discarded.
- Sessions are ephemeral but there is no double ratchet and no post-compromise security within a session. For a threat model that needs that, use Signal.

## Verified by automated tests

- `ProtocolTest.java` — ECDH handshake on a desktop JVM: both directions derive matching keys, keys differ per direction, safety numbers agree on both sides, frames decrypt, tampered frames are rejected, wrong PIN fails to unwrap the master key.
- Relay test — role assignment, bidirectional forwarding, 40 KB frames, third-peer rejection, room isolation, EOF propagation.
- Cross-language test — the real Java client flow (preamble, role mapping, handshake, 50 KB payload) through the Python relay.

## Reporting

This is a personal project. If you find a flaw, open an issue with reproduction steps.
