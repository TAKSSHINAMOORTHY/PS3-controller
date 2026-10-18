import socket
import base64
import os
import time
import json

def make_ws_frame(data: str) -> bytes:
    payload = data.encode('utf-8')
    length = len(payload)
    assert length < 126, f"Frame length {length} too large for GamerPad!"
    mask = os.urandom(4)
    header = bytes([0x81, 0x80 | length])
    masked_payload = bytearray(length)
    for i in range(length):
        masked_payload[i] = payload[i] ^ mask[i % 4]
    return header + mask + masked_payload

def main():
    s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    s.connect(("127.0.0.1", 8080))
    key = base64.b64encode(os.urandom(16)).decode('ascii')
    req = (
        "GET /socket.io/?EIO=4&transport=websocket HTTP/1.1\r\n"
        "Host: localhost:8080\r\n"
        "Upgrade: websocket\r\n"
        "Connection: Upgrade\r\n"
        f"Sec-WebSocket-Key: {key}\r\n"
        "Sec-WebSocket-Version: 13\r\n\r\n"
    )
    s.sendall(req.encode('ascii'))
    s.recv(4096)
    
    s.settimeout(2.0)
    s.recv(4096) # open packet
    s.sendall(make_ws_frame("40")) # connect
    time.sleep(0.5)
    s.recv(4096) # server_info
    
    # Send controller_info (len = 80 bytes)
    cinfo = ["controller_info", {
        "name": "PS3",
        "guid": "0300",
        "sdl_mapping": {"A": 0, "B": 1}
    }]
    msg = "42" + json.dumps(cinfo)
    print(f"Sending controller_info (len={len(msg)})")
    s.sendall(make_ws_frame(msg))
    time.sleep(1.5)
    
    # 1. Press Button A
    p_msg = "42" + json.dumps(["press_button", {"id": "A"}])
    print(f">>> INJECTING: press_button 'A' (len={len(p_msg)})")
    s.sendall(make_ws_frame(p_msg))
    time.sleep(1.5)
    
    # 2. Release Button A
    r_msg = "42" + json.dumps(["release_button", {"id": "A"}])
    print(f">>> INJECTING: release_button 'A' (len={len(r_msg)})")
    s.sendall(make_ws_frame(r_msg))
    time.sleep(1.5)
    
    # 3. Move Left Joystick
    j_msg = "42" + json.dumps(["left_joystick", {"x": 1.0, "y": -1.0}])
    print(f">>> INJECTING: left_joystick x=1.0, y=-1.0 (len={len(j_msg)})")
    s.sendall(make_ws_frame(j_msg))
    time.sleep(1.5)
    
    # 4. Center Joystick
    jc_msg = "42" + json.dumps(["left_joystick", {"x": 0.0, "y": 0.0}])
    print(f">>> INJECTING: left_joystick center (len={len(jc_msg)})")
    s.sendall(make_ws_frame(jc_msg))
    time.sleep(1.5)
    
    print("Injection sequence complete. Disconnecting...")
    s.close()

if __name__ == "__main__":
    main()
