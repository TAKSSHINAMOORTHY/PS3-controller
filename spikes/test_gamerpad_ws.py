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

def ws_handshake():
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
    
    s.sendall(make_ws_frame("40"))
    time.sleep(0.5)
    server_info = s.recv(4096)
    
    # 87 bytes total
    cinfo = ["controller_info", {
        "name": "PS3",
        "guid": "030000004c050000",
        "sdl_mapping": {"a": 0}
    }]
    msg = "42" + json.dumps(cinfo)
    print("Sending msg (len", len(msg), "):", msg)
    s.sendall(make_ws_frame(msg))
    
    time.sleep(1)
    try:
        reply = s.recv(4096)
        print("Reply:", reply)
    except Exception as e:
        print("No reply:", e)
        
    print("Holding for 10 seconds...")
    time.sleep(10)
    s.close()

if __name__ == "__main__":
    ws_handshake()
