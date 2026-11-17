# Security notes

## What is protected

- **At rest.** Photos, files and notes are AES-256-GCM encrypted in the app's private storage. The 32-byte master key exists only in memory while the app is unlocked. It is stored wrapped by a key derived from the PIN (PBKDF2-HMAC-SHA256, 150,000 rounds, 16-byte random salt) and optionally by a hardware-backed Keystore key that requires fingerprint authentication.
- **In transit.** Chat, voice and video frames are encrypted per frame with AES-256-GCM under per-session, per-direction keys derived from an ephemeral ECDH exchange. This holds for all three transports: Wi-Fi, Bluetooth and the internet switchboard.
- **The switchboard cannot read content.** It registers IDs and bridges two sockets. It never holds a key.

## What the switchboard learns

In Internet mode the server operator can see:

- which IDs registered (your chosen name or phone number),
- which ID called which, and when,
- both IP addresses,
- how many bytes flowed (not what they were).

That is metadata, not content. If that matters to you, use Wi-Fi or Bluetooth mode, which involve no third party at all.

## What is not protected

- A rooted device or a compromised OS can read memory while the app is unlocked.
- Screenshots are not blocked.
- **A malicious switchboard can attempt a man in the middle**, because it controls both sockets. The defence is the safety number: it is derived from both sides' ephemeral public keys, so an interceptor produces a different number on each phone. **Compare the six-group number on both screens before sharing anything private.**
- ID squatting: whoever registers your ID first on that server holds it. A second registration gets "already online". Use an ID you do not mind being public.
- Messages are not persisted; when the connection drops the session keys are discarded.
- Sessions are ephemeral but there is no double ratchet and no post-compromise security within a session. For a threat model that needs that, use Signal.

## Verified by automated tests

- `ProtocolTest.java` — ECDH handshake on a desktop JVM: both directions derive matching keys, keys differ per direction, safety numbers agree, frames decrypt, tampered frames are rejected, wrong PIN fails to unwrap the master key.
- Switchboard test — registration, WAIT/CALL pairing, `PEER` role assignment, bidirectional forwarding, 40 KB frames, offline/busy/bad-ID rejection, and teardown when either side hangs up.
- Cross-language test (`SwitchTest.java` + `relay/switchboard.py`) — the real Java client flow: register by phone number, wait, call by number, matching safety numbers, 50 KB payload intact.

## Reporting

This is a personal project. If you find a flaw, open an issue with reproduction steps.
