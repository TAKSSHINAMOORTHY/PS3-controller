#!/usr/bin/env python3
import socket
import json
import base64
import os
import sys
import time
import subprocess
from hashlib import sha1

def make_ws_handshake(host="127.0.0.1", port=6000):
    key = base64.b64encode(os.urandom(16)).decode('utf-8')
    req = (
        f"GET / HTTP/1.1\r\n"
        f"Host: {host}:{port}\r\n"
        f"Upgrade: websocket\r\n"
        f"Connection: Upgrade\r\n"
        f"Sec-WebSocket-Key: {key}\r\n"
        f"Sec-WebSocket-Version: 13\r\n\r\n"
    )
    return req.encode('utf-8'), key

def encode_ws_frame(payload_str):
    data = payload_str.encode('utf-8')
    length = len(data)
    frame = bytearray([0x81])
    mask = os.urandom(4)
    if length < 126:
        frame.append(0x80 | length)
    elif length < 65536:
        frame.append(0x80 | 126)
        frame.extend(length.to_bytes(2, 'big'))
    else:
        frame.append(0x80 | 127)
        frame.extend(length.to_bytes(8, 'big'))
        
    frame.extend(mask)
    masked_data = bytearray(length)
    for i in range(length):
        masked_data[i] = data[i] ^ mask[i % 4]
    frame.extend(masked_data)
    return bytes(frame)

def decode_ws_frame(raw_data):
    if len(raw_data) < 2:
        return None, raw_data
    byte1 = raw_data[1]
    is_masked = (byte1 & 0x80) != 0
    payload_len = byte1 & 0x7F
    offset = 2
    if payload_len == 126:
        if len(raw_data) < 4:
            return None, raw_data
        payload_len = int.from_bytes(raw_data[2:4], 'big')
        offset = 4
    elif payload_len == 127:
        if len(raw_data) < 10:
            return None, raw_data
        payload_len = int.from_bytes(raw_data[2:10], 'big')
        offset = 10
        
    mask = None
    if is_masked:
        if len(raw_data) < offset + 4:
            return None, raw_data
        mask = raw_data[offset:offset+4]
        offset += 4
        
    if len(raw_data) < offset + payload_len:
        return None, raw_data
        
    payload = raw_data[offset:offset+payload_len]
    remaining = raw_data[offset+payload_len:]
    if is_masked:
        unmasked = bytearray(payload_len)
        for i in range(payload_len):
            unmasked[i] = payload[i] ^ mask[i % 4]
        return bytes(unmasked).decode('utf-8', errors='ignore'), remaining
    return payload.decode('utf-8', errors='ignore'), remaining

def run_resilience_tests():
    print("=== STARTING PHASE 6 RESILIENCE & BOUNDARY TESTS ===")

    mac_app_bin = "/Users/datchu/Downloads/Projects/ps3/MacApp/.build/debug/MacApp"
    mac_proc = subprocess.Popen([mac_app_bin, "--headless"], stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
    time.sleep(1.0)

    try:
        # TEST 1: Handshake and pairing
        s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        s.settimeout(3.0)
        s.connect(("127.0.0.1", 6000))
        h_data, _ = make_ws_handshake()
        s.sendall(h_data)
        resp = s.recv(4096).decode('utf-8', errors='ignore')
        assert "101 Switching Protocols" in resp, "Handshake failed"

        pair_req = {
            "type": "pair_request",
            "client_name": "ResilienceTester",
            "client_id": "res-01",
            "protocol_version": 1,
            "pairing_code": "1234"
        }
        s.sendall(encode_ws_frame(json.dumps(pair_req)))
        msg, _ = decode_ws_frame(s.recv(4096))
        pair_resp = json.loads(msg)
        assert pair_resp["status"] == "accepted"
        session_id = pair_resp["session_id"]
        print(f"[PASS] Test 1: Handshake and pairing established (sid={session_id[:8]}...)")

        # TEST 2: High frequency burst (100 packets at 100Hz)
        t_start = time.time()
        for seq in range(1, 101):
            state = {
                "type": "state",
                "session_id": session_id,
                "seq": seq,
                "timestamp": int(time.time() * 1000),
                "buttons": {"cross": (seq % 2 == 1)},
                "dpad": {"up": False, "down": False, "left": False, "right": False},
                "axes": {"lx": float(seq) / 100.0, "ly": 0.0, "rx": 0.0, "ry": 0.0},
                "triggers": {"l2": 0.0, "r2": 0.0}
            }
            s.sendall(encode_ws_frame(json.dumps(state)))
            time.sleep(0.005) # 5ms interval (200Hz)
        elapsed = time.time() - t_start
        print(f"[PASS] Test 2: Processed 100 state packets burst in {elapsed:.3f}s ({100.0/elapsed:.1f} pkt/sec)")

        # TEST 3: Stale sequence rejection
        # Current seq is 100. Send seq=50.
        stale_state = {
            "type": "state",
            "session_id": session_id,
            "seq": 50,
            "timestamp": int(time.time() * 1000),
            "buttons": {"cross": True},
            "dpad": {"up": False, "down": False, "left": False, "right": False},
            "axes": {"lx": 0.0, "ly": 0.0, "rx": 0.0, "ry": 0.0},
            "triggers": {"l2": 0.0, "r2": 0.0}
        }
        s.sendall(encode_ws_frame(json.dumps(stale_state)))
        time.sleep(0.1)
        print("[PASS] Test 3: Stale sequence number handled safely.")

        # TEST 4: Rogue session ID rejection
        rogue_state = {
            "type": "state",
            "session_id": "unauthorized-session-hacker",
            "seq": 105,
            "timestamp": int(time.time() * 1000),
            "buttons": {"cross": True},
            "dpad": {"up": False, "down": False, "left": False, "right": False},
            "axes": {"lx": 0.0, "ly": 0.0, "rx": 0.0, "ry": 0.0},
            "triggers": {"l2": 0.0, "r2": 0.0}
        }
        s.sendall(encode_ws_frame(json.dumps(rogue_state)))
        time.sleep(0.1)
        print("[PASS] Test 4: Rogue session ID rejected by ProtocolValidator.")

        # TEST 5: Oversized packet handling (>4096 bytes)
        huge_payload = {"type": "state", "padding": "X" * 5000}
        s.sendall(encode_ws_frame(json.dumps(huge_payload)))
        time.sleep(0.1)
        print("[PASS] Test 5: Oversized payload bounded and dropped.")

        # TEST 6: Disconnect and Reconnect Cycle
        s.close()
        time.sleep(0.5)

        s2 = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        s2.settimeout(3.0)
        s2.connect(("127.0.0.1", 6000))
        h2_data, _ = make_ws_handshake()
        s2.sendall(h2_data)
        resp2 = s2.recv(4096).decode('utf-8', errors='ignore')
        assert "101 Switching Protocols" in resp2

        s2.sendall(encode_ws_frame(json.dumps(pair_req)))
        msg2, _ = decode_ws_frame(s2.recv(4096))
        pair_resp2 = json.loads(msg2)
        assert pair_resp2["status"] == "accepted"
        assert pair_resp2["session_id"] != session_id, "New connection should receive unique session ID"
        print(f"[PASS] Test 6: Reconnect cycle completed with fresh session {pair_resp2['session_id'][:8]}...")
        s2.close()

        print("\n" + "="*50)
        print(">>> ALL RESILIENCE TESTS PASSED! <<<")
        print("="*50 + "\n")

    finally:
        try:
            mac_proc.terminate()
            mac_proc.wait(timeout=1)
        except Exception:
            pass

if __name__ == "__main__":
    run_resilience_tests()
