import socket
import base64
import os
import time
import json

def make_ws_frame(data: str) -> bytes:
    payload = data.encode('utf-8')
    length = len(payload)
    mask = os.urandom(4)
    if length < 126:
        header = bytes([0x81, 0x80 | length])
    else:
        header = bytes([0x81, 0x80 | 126, (length >> 8) & 0xff, length & 0xff])
    
    masked_payload = bytearray(length)
    for i in range(length):
        masked_payload[i] = payload[i] ^ mask[i % 4]
    return header + mask + masked_payload

def main():
    print("Connecting to GamerPad WebSocket...")
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
    resp = s.recv(4096)
    
    s.settimeout(2.0)
    open_packet = s.recv(4096)
    
    # Connect
    s.sendall(make_ws_frame("40"))
    time.sleep(0.5)
    server_info = s.recv(4096)
    print("Received server_info")
    
    # Init Controller
    cinfo = ["controller_info", {
        "name": "PS3 Controller",
        "guid": "030000004c050000",
        "sdl_mapping": {"cross": 0, "circle": 1}
    }]
    msg = "42" + json.dumps(cinfo)
    s.sendall(make_ws_frame(msg))
    print("Sent controller_info")
    time.sleep(2)
    
    # 1. Press button 'cross' (maps to button 0)
    print(">>> SENDING: press_button 'cross'")
    press_msg = "42" + json.dumps(["press_button", {"button": "cross"}])
    s.sendall(make_ws_frame(press_msg))
    time.sleep(2)
    
    # 2. Release button 'cross'
    print(">>> SENDING: release_button 'cross'")
    rel_msg = "42" + json.dumps(["release_button", {"button": "cross"}])
    s.sendall(make_ws_frame(rel_msg))
    time.sleep(2)
    
    # 3. Move left joystick to (0.75, -0.75)
    print(">>> SENDING: left_joystick x=0.75, y=-0.75")
    joy_msg = "42" + json.dumps(["left_joystick", {"x": 0.75, "y": -0.75}])
    s.sendall(make_ws_frame(joy_msg))
    time.sleep(2)
    
    # 4. Center joystick
    print(">>> SENDING: left_joystick x=0.0, y=0.0 (center)")
    joy_center = "42" + json.dumps(["left_joystick", {"x": 0.0, "y": 0.0}])
    s.sendall(make_ws_frame(joy_center))
    time.sleep(2)
    
    print("Test sequence complete. Closing connection...")
    s.close()

if __name__ == "__main__":
    main()
