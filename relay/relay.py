#!/usr/bin/env python3
"""
VaultChat relay.

Pairs two phones by room code and forwards bytes between them. It never sees
plaintext: VaultChat performs an ECDH handshake through this relay and encrypts
every frame with AES-256-GCM, so the relay only moves ciphertext.

Protocol
  client -> relay : "VC1 <roomcode>\n"
  relay  -> client : "ROLE 0\n"   (first peer, acts as the responder)
                     "ROLE 1\n"   (second peer, acts as the initiator)
  then the relay copies bytes in both directions until either side disconnects.

Run:
  PORT=9000 python3 relay.py

Needs a host that gives you a raw TCP port (a VPS, Fly.io, Railway, or your own
machine with port forwarding). HTTP-only free tiers will not work, because this
is a raw TCP service, not a web service.
"""

import asyncio
import os
import sys

ROOMS = {}
MAX_ROOM_LEN = 64
IDLE_TIMEOUT = 300


async def read_preamble(reader):
    try:
        line = await asyncio.wait_for(reader.readline(), timeout=30)
    except asyncio.TimeoutError:
        return None
    if not line or len(line) > 128:
        return None
    parts = line.decode("utf-8", "ignore").strip().split()
    if len(parts) != 2 or parts[0] != "VC1":
        return None
    room = parts[1][:MAX_ROOM_LEN]
    if not room.isalnum():
        return None
    return room


async def pump(src, dst, done):
    try:
        while True:
            data = await src["reader"].read(65536)
            if not data:
                break
            dst["writer"].write(data)
            await dst["writer"].drain()
    except Exception:
        pass
    finally:
        if not done.done():
            done.set_result(None)


async def handle(reader, writer):
    peer = None
    room = None
    try:
        room = await read_preamble(reader)
        if room is None:
            writer.write(b"ERR\n")
            await writer.drain()
            return

        peers = ROOMS.setdefault(room, [])
        if len(peers) >= 2:
            writer.write(b"ERR\n")
            await writer.drain()
            return

        done = asyncio.get_event_loop().create_future()
        peer = {"reader": reader, "writer": writer, "done": done}
        peers.append(peer)
        role = len(peers) - 1

        writer.write(f"ROLE {role}\n".encode())
        await writer.drain()
        print(f"[relay] room {room}: peer {role} joined ({len(peers)}/2)", flush=True)

        if len(peers) == 2:
            a, b = peers[0], peers[1]
            asyncio.ensure_future(asyncio.gather(pump(a, b, done), pump(b, a, done)))

        await asyncio.wait_for(done, timeout=IDLE_TIMEOUT)
    except asyncio.TimeoutError:
        pass
    except Exception as exc:
        print(f"[relay] error: {exc}", flush=True)
    finally:
        try:
            writer.close()
        except Exception:
            pass
        if room and room in ROOMS:
            peers = ROOMS[room]
            if peer in peers:
                peers.remove(peer)
            if not peers:
                ROOMS.pop(room, None)
        print(f"[relay] room {room}: peer left", flush=True)


async def main():
    port = int(os.environ.get("PORT", "9000"))
    server = await asyncio.start_server(handle, "0.0.0.0", port)
    addrs = ", ".join(str(s.getsockname()) for s in server.sockets)
    print(f"[relay] listening on {addrs}", flush=True)
    async with server:
        await server.serve_forever()


if __name__ == "__main__":
    try:
        asyncio.run(main())
    except KeyboardInterrupt:
        sys.exit(0)
