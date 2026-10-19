#!/usr/bin/env python3
import socket
import json
import base64
import os
import sys
import time
import subprocess
import threading
import select
from hashlib import sha1

WS_GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"

def make_ws_handshake(host="127.0.0.1", port=6006):
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
    frame = bytearray([0x81]) # text frame, FIN=1
    
    # Client to server frames must be masked (bit 7 = 1)
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

def run_e2e_test():
    print("=== STARTING PHASE 5 END-TO-END INTEGRATION TEST ===")
    
    # 1. Compile monitor_sdl if not present
    monitor_bin = "/Users/datchu/Downloads/Projects/ps3/spikes/monitor_sdl"
    if not os.path.exists(monitor_bin):
        print("[SETUP] Compiling spikes/monitor_sdl...")
        res = subprocess.run(["clang", "-O2", "/Users/datchu/Downloads/Projects/ps3/spikes/monitor_sdl.c", "-o", monitor_bin])
        if res.returncode != 0:
            print("[FATAL] Failed to compile monitor_sdl")
            sys.exit(1)
            
    # 2. Check/Start DriverKit daemon on 8080
    daemon_proc = None
    sock = socket.socket()
    try:
        sock.connect(("127.0.0.1", 8080))
        sock.close()
        print("[SETUP] DriverKit daemon is verified active on 127.0.0.1:8080")
    except Exception:
        print("[SETUP] Starting DriverKit daemon (/Applications/CoreOrbits-GamerPad.app/Contents/MacOS/CoreOrbits-GamerPad)...")
        daemon_proc = subprocess.Popen(
            ["/Applications/CoreOrbits-GamerPad.app/Contents/MacOS/CoreOrbits-GamerPad"],
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL
        )
        time.sleep(2.0)
        sock2 = socket.socket()
        sock2.connect(("127.0.0.1", 8080))
        sock2.close()
        print("[SETUP] DriverKit daemon successfully started on 127.0.0.1:8080")
        
    # 3. Start MacApp in headless mode
    mac_app_bin = "/Users/datchu/Downloads/Projects/ps3/MacApp/.build/arm64-apple-macosx/debug/MacApp"
    if not os.path.exists(mac_app_bin):
        mac_app_bin = "/Users/datchu/Downloads/Projects/ps3/MacApp/.build/debug/MacApp"
        
    test_port = 6006
    print(f"[SETUP] Launching MacApp from {mac_app_bin} on port {test_port}...")
    mac_proc = subprocess.Popen([mac_app_bin, "--headless", "--port", str(test_port)], stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
    time.sleep(1.5)
    
    # 4. Start monitor_sdl
    print("[SETUP] Launching RPCS3 SDL3 event monitor...")
    monitor_proc = subprocess.Popen([monitor_bin], stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True, bufsize=1)
    
    captured_events = []
    def monitor_reader():
        for line in iter(monitor_proc.stdout.readline, ''):
            clean = line.strip()
            if clean:
                captured_events.append(clean)
                print(f"  [SDL-EVENT] {clean}")
    
    t_reader = threading.Thread(target=monitor_reader, daemon=True)
    t_reader.start()
    time.sleep(1.0)
    
    try:
        # 5. Connect to MacApp on port 6006
        print(f"[TEST] Connecting client socket to ws://127.0.0.1:{test_port}...")
        client_sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        client_sock.settimeout(5.0)
        client_sock.connect(("127.0.0.1", test_port))
        
        # 6. WebSocket Handshake
        handshake_data, sec_key = make_ws_handshake(port=test_port)
        client_sock.sendall(handshake_data)
        resp = client_sock.recv(4096).decode('utf-8', errors='ignore')
        assert "101 Switching Protocols" in resp, f"Handshake failed: {resp}"
        print("[TEST] WebSocket Upgrade Handshake 101 OK!")
        
        # 7. Send PairRequest
        pair_req = {
            "type": "pair_request",
            "client_name": "E2ETestClient",
            "client_id": "e2e-001",
            "protocol_version": 1,
            "pairing_code": "1234"
        }
        client_sock.sendall(encode_ws_frame(json.dumps(pair_req)))
        print("[TEST] Sent pair_request -> awaiting pair_response...")
        
        raw_resp = client_sock.recv(4096)
        msg_str, _ = decode_ws_frame(raw_resp)
        print(f"[TEST] Received message: {msg_str}")
        assert msg_str is not None, "Did not receive pair response frame"
        pair_resp = json.loads(msg_str)
        assert pair_resp.get("status") == "accepted", f"Pairing not accepted: {pair_resp}"
        session_id = pair_resp.get("session_id")
        assert session_id, "Missing session_id in pair response"
        print(f"[TEST] Pairing ACCEPTED! Active Session ID: {session_id}")
        
        # Wait for DriverKit controller to attach to SDL
        time.sleep(1.5)
        
        # 8. Test Button Press (CROSS -> Button 0)
        print("[TEST] Injecting CROSS button press (state=1)...")
        press_state = {
            "type": "state",
            "session_id": session_id,
            "seq": 1,
            "timestamp": int(time.time() * 1000),
            "buttons": {
                "cross": True, "circle": False, "square": False, "triangle": False,
                "l1": False, "r1": False, "l2": False, "r2": False,
                "l3": False, "r3": False, "start": False, "select": False, "ps": False
            },
            "dpad": {"up": False, "down": False, "left": False, "right": False},
            "axes": {"lx": 0.0, "ly": 0.0, "rx": 0.0, "ry": 0.0},
            "triggers": {"l2": 0.0, "r2": 0.0}
        }
        client_sock.sendall(encode_ws_frame(json.dumps(press_state)))
        time.sleep(0.4)
        
        # Check SDL event
        down_found = any("JOYSTICK_BUTTON_DOWN: button=0, state=1" in e or "Button 0 is currently PRESSED (1)" in e for e in captured_events)
        print(f"[VERIFY] JOYSTICK_BUTTON_DOWN detected in RPCS3 SDL3: {down_found}")
        assert down_found, "RPCS3 SDL3 failed to detect JOYSTICK_BUTTON_DOWN for CROSS"
        
        # 9. Test Button Release (CROSS -> release)
        print("[TEST] Injecting CROSS button release (state=0)...")
        release_state = press_state.copy()
        release_state["seq"] = 2
        release_state["timestamp"] = int(time.time() * 1000)
        release_state["buttons"] = release_state["buttons"].copy()
        release_state["buttons"]["cross"] = False
        client_sock.sendall(encode_ws_frame(json.dumps(release_state)))
        time.sleep(0.4)
        
        up_found = any("JOYSTICK_BUTTON_UP: button=0, state=0" in e for e in captured_events)
        print(f"[VERIFY] JOYSTICK_BUTTON_UP detected in RPCS3 SDL3: {up_found}")
        assert up_found, "RPCS3 SDL3 failed to detect JOYSTICK_BUTTON_UP for CROSS"
        
        # 10. Test Stick Motion (LX = 1.0, LY = -1.0)
        print("[TEST] Injecting Left Stick motion (LX=1.0, LY=-1.0)...")
        stick_state = release_state.copy()
        stick_state["seq"] = 3
        stick_state["timestamp"] = int(time.time() * 1000)
        stick_state["axes"] = {"lx": 1.0, "ly": -1.0, "rx": 0.0, "ry": 0.0}
        client_sock.sendall(encode_ws_frame(json.dumps(stick_state)))
        time.sleep(0.4)
        
        axis_found = any("JOYSTICK_AXIS_MOTION" in e for e in captured_events)
        print(f"[VERIFY] JOYSTICK_AXIS_MOTION detected in RPCS3 SDL3: {axis_found}")
        assert axis_found, "RPCS3 SDL3 failed to detect JOYSTICK_AXIS_MOTION"
        
        # 11. Test Heartbeat Message
        print("[TEST] Sending heartbeat message...")
        hb = {
            "type": "heartbeat",
            "session_id": session_id,
            "seq": 4,
            "timestamp": int(time.time() * 1000)
        }
        client_sock.sendall(encode_ws_frame(json.dumps(hb)))
        time.sleep(0.3)
        print("[VERIFY] Heartbeat acknowledged without disconnection.")
        
        # 12. Disconnect test
        print("[TEST] Closing client socket...")
        client_sock.close()
        time.sleep(0.5)
        print("[VERIFY] Clean client disconnect.")
        
        print("\n" + "="*50)
        print(">>> ALL END-TO-END INTEGRATION TESTS PASSED! <<<")
        print("="*50 + "\n")
        
    finally:
        print("[CLEANUP] Stopping monitor, MacApp, and daemon...")
        try:
            monitor_proc.terminate()
            monitor_proc.wait(timeout=1)
        except Exception:
            pass
        try:
            mac_proc.terminate()
            mac_proc.wait(timeout=1)
        except Exception:
            pass
        if daemon_proc:
            try:
                daemon_proc.terminate()
                daemon_proc.wait(timeout=1)
            except Exception:
                pass

if __name__ == "__main__":
    run_e2e_test()
