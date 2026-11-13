"""Stand in for the Fossibot+ app's offline mode: a TCP server on :8058 that the station connects to.

The app answers every message with the same fixed frame (utils/tcp.js, sendDefaultResponse), so this does too.
Everything received and sent is logged as hex with a timestamp, one line per read or write.

To send a frame to the connected station, put its hex in a UDP datagram to 127.0.0.1:8059
(tools/send.py does that).

With --cloud it stands in for the vendor's cloud on :5555 instead: it opens with the cloud's handshake, the
frames copied from a relayed session, and answers the station's serial before acking telemetry.
"""
import socket
import sys
import threading
import time

ACK = bytes.fromhex("0a001f00040004001f00")
# the cloud's opening prompt ("You need to authenticate:") and its reply to the station's serial (kind 0x51)
AUTH_PROMPT = bytes.fromhex("1f0050001b00") + b"You need to authenticate:"
AUTH_OK = bytes.fromhex("0c0052000600000000000000")
KIND_AUTH = 0x51
CONTROL_PORT = 8059

lock = threading.Lock()
current = None


def log(out, line):
    stamp = time.strftime("%H:%M:%S")
    with lock:
        print(f"{stamp} {line}", flush=True)
        out.write(f"{stamp} {line}\n")
        out.flush()


def serve(conn, addr, out, ack, cloud):
    global current
    log(out, f"connect {addr}")
    current = conn
    with conn:
        try:
            if cloud:
                conn.sendall(AUTH_PROMPT)
                log(out, f"tx {len(AUTH_PROMPT)} {AUTH_PROMPT.hex()}")
            while data := conn.recv(4096):
                log(out, f"rx {addr[0]} {len(data)} {data.hex()}")
                if cloud and len(data) >= 4 and int.from_bytes(data[2:4], "little") == KIND_AUTH:
                    conn.sendall(AUTH_OK)
                    log(out, f"tx {len(AUTH_OK)} {AUTH_OK.hex()}")
                elif ack:
                    conn.sendall(ACK)
        except OSError as e:
            log(out, f"error {addr} {e}")
    if current is conn:
        current = None
    log(out, f"close {addr}")


def control(out):
    udp = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    udp.bind(("127.0.0.1", CONTROL_PORT))
    while True:
        msg, _ = udp.recvfrom(4096)
        try:
            frame = bytes.fromhex(msg.decode().strip())
        except ValueError:
            log(out, f"bad control message {msg!r}")
            continue
        conn = current
        if conn is None:
            log(out, f"no station connected, dropped {frame.hex()}")
            continue
        conn.sendall(frame)
        log(out, f"tx {len(frame)} {frame.hex()}")


def main():
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 8058
    path = sys.argv[2] if len(sys.argv) > 2 else "capture.log"
    ack = "--no-ack" not in sys.argv
    cloud = "--cloud" in sys.argv
    srv = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    srv.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    srv.bind(("0.0.0.0", port))
    srv.listen()
    with open(path, "a") as out:
        log(out, f"listening on :{port} ack={ack} cloud={cloud}, control on 127.0.0.1:{CONTROL_PORT}")
        threading.Thread(target=control, args=(out,), daemon=True).start()
        while True:
            conn, addr = srv.accept()
            threading.Thread(target=serve, args=(conn, addr, out, ack, cloud), daemon=True).start()


if __name__ == "__main__":
    main()
