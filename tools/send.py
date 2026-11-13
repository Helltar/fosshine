"""Write one register through a running listen.py: python3 send.py led_mode 1"""
import socket
import sys

import frame
import regs

name, val = sys.argv[1], int(sys.argv[2])
key = regs.NAMES[name] if name in regs.NAMES else int(name)
cmd = frame.write_cmd(key, val)
socket.socket(socket.AF_INET, socket.SOCK_DGRAM).sendto(cmd.hex().encode(), ("127.0.0.1", 8059))
print(f"{regs.REGS.get(key, (name,))[0]} = {val}: {cmd.hex()}")
