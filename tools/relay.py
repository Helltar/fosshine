"""Logging TCP relay between the station and the Fossibot cloud: python3 relay.py <log> [listen_port] [upstream_host] [upstream_port]

Every chunk is logged as hex with its direction: "up" station → cloud, "down" cloud → station.
"""
import socket
import sys
import threading
import time

lock = threading.Lock()


def log(out, line):
    stamp = time.strftime("%H:%M:%S")
    with lock:
        print(f"{stamp} {line}", flush=True)
        out.write(f"{stamp} {line}\n")
        out.flush()


def pump(src, dst, direction, out):
    try:
        while data := src.recv(4096):
            log(out, f"{direction} {len(data)} {data.hex()}")
            dst.sendall(data)
    except OSError as e:
        log(out, f"{direction} error {e}")
    finally:
        for s in (src, dst):
            try:
                s.shutdown(socket.SHUT_RDWR)
            except OSError:
                pass


def handle(client, addr, upstream, out):
    log(out, f"connect {addr}")
    try:
        server = socket.create_connection(upstream, timeout=10)
        server.settimeout(None)
    except OSError as e:
        log(out, f"upstream failed {e}")
        client.close()
        return
    log(out, f"upstream {server.getpeername()}")
    t = threading.Thread(target=pump, args=(server, client, "down", out), daemon=True)
    t.start()
    pump(client, server, "up", out)
    t.join()
    client.close()
    server.close()
    log(out, f"close {addr}")


def main():
    path = sys.argv[1]
    port = int(sys.argv[2]) if len(sys.argv) > 2 else 5555
    upstream = (sys.argv[3] if len(sys.argv) > 3 else "8.211.40.49", int(sys.argv[4]) if len(sys.argv) > 4 else 5555)
    srv = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    srv.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    srv.bind(("0.0.0.0", port))
    srv.listen()
    with open(path, "a") as out:
        log(out, f"relay :{port} -> {upstream[0]}:{upstream[1]}")
        while True:
            client, addr = srv.accept()
            threading.Thread(target=handle, args=(client, addr, upstream, out), daemon=True).start()


if __name__ == "__main__":
    main()
