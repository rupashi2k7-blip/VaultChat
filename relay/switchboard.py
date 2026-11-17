#!/usr/bin/env python3
"""
VaultChat switchboard.

A tiny server that lets two phones find each other by name or phone number and
then bridges them. It never sees plaintext: the phones run an ECDH handshake
through this server and encrypt every frame with AES-256-GCM, so the server only
moves ciphertext. It does learn who is registered and who called whom.

Protocol
  client -> server : "VC1 <myId>\n"          register a name / phone number
  server -> client : "OK\n"                  or "ERR busy\n"
  client -> server : "WAIT\n"                "put me in a call when someone calls me"
                     "CALL <theirId>\n"      "connect me to this id"
  server -> caller : "PEER 1 <theirId>\n"
  server -> callee : "PEER 0 <callerId>\n"
  then the server copies bytes in both directions until either side disconnects.

One connection is used per call attempt, so the app reconnects for each call.

Run:
  PORT=9000 python3 switchboard.py

Needs a host with a raw TCP port: a small VPS, Fly.io with a TCP service, or a
home machine with port forwarding. HTTP-only free tiers will not work.
"""

import asyncio
import os
import sys

PEERS = {}
IDLE_TIMEOUT = 900
MAX_ID_LEN = 32


def valid_id(s):
    if not (1 <= len(s) <= MAX_ID_LEN):
        return False
    return all(c.isalnum() or c in "-_." for c in s)


async def read_line(reader, timeout=30):
    try:
        line = await asyncio.wait_for(reader.readline(), timeout)
    except asyncio.TimeoutError:
        return None
    if not line or len(line) > 128:
        return None
    return line.decode("utf-8", "ignore").strip()


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


def drop(peer_id, conn):
    if peer_id and PEERS.get(peer_id) is conn:
        PEERS.pop(peer_id, None)


async def handle(reader, writer):
    peer_id = None
    conn = None
    try:
        line = await read_line(reader)
        if not line:
            return
        parts = line.split()
        if len(parts) != 2 or parts[0] != "VC1" or not valid_id(parts[1]):
            writer.write(b"ERR badid\n")
            await writer.drain()
            return

        peer_id = parts[1]
        if peer_id in PEERS:
            writer.write(b"ERR busy\n")
            await writer.drain()
            return

        conn = {
            "reader": reader,
            "writer": writer,
            "id": peer_id,
            "available": False,
            "done": asyncio.get_event_loop().create_future(),
        }
        PEERS[peer_id] = conn
        writer.write(b"OK\n")
        await writer.drain()
        print(f"[switch] {peer_id} registered", flush=True)

        cmd = await read_line(reader, timeout=60)
        if cmd is None:
            return
        parts = cmd.split()
        if not parts:
            return

        if parts[0] == "WAIT" and len(parts) == 1:
            conn["available"] = True
            print(f"[switch] {peer_id} waiting for a call", flush=True)
            await asyncio.wait_for(conn["done"], timeout=IDLE_TIMEOUT)

        elif parts[0] == "CALL" and len(parts) == 2:
            target_id = parts[1]
            target = PEERS.get(target_id)
            if target is None or not target.get("available"):
                writer.write(b"ERR offline\n")
                await writer.drain()
                print(f"[switch] {peer_id} called {target_id}: not available", flush=True)
                return

            target["available"] = False
            writer.write(f"PEER 1 {target_id}\n".encode())
            await writer.drain()
            target["writer"].write(f"PEER 0 {peer_id}\n".encode())
            await target["writer"].drain()
            print(f"[switch] bridging {peer_id} <-> {target_id}", flush=True)

            done = asyncio.get_event_loop().create_future()
            tasks = [
                asyncio.ensure_future(pump(conn, target, done)),
                asyncio.ensure_future(pump(target, conn, done)),
            ]
            # as soon as either direction ends, tear the whole call down
            await asyncio.wait(tasks, return_when=asyncio.FIRST_COMPLETED)
            for t in tasks:
                t.cancel()
            if not target["done"].done():
                target["done"].set_result(None)
            print(f"[switch] call ended {peer_id} <-> {target_id}", flush=True)

        else:
            writer.write(b"ERR badcmd\n")
            await writer.drain()

    except asyncio.TimeoutError:
        pass
    except Exception as exc:
        print(f"[switch] error: {exc}", flush=True)
    finally:
        drop(peer_id, conn)
        try:
            writer.close()
        except Exception:
            pass
        if peer_id:
            print(f"[switch] {peer_id} left", flush=True)


async def main():
    port = int(os.environ.get("PORT", "9000"))
    server = await asyncio.start_server(handle, "0.0.0.0", port)
    addrs = ", ".join(str(s.getsockname()) for s in server.sockets)
    print(f"[switch] listening on {addrs}", flush=True)
    async with server:
        await server.serve_forever()


if __name__ == "__main__":
    try:
        asyncio.run(main())
    except KeyboardInterrupt:
        sys.exit(0)
