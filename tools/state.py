"""Print the latest decoded telemetry from a listen.py log: python3 state.py <log> [field…]"""
import sys

import frame
import regs

path, fields = sys.argv[1], sys.argv[2:]
last = None
with open(path) as f:
    for line in f:
        parts = line.split()
        if len(parts) == 5 and parts[1] == "rx" and parts[3] == "224":
            last = parts
d = regs.decode(frame.parse(bytes.fromhex(last[4]))["regs"])
print(last[0], " ".join(f"{k}={d[k]}" for k in (fields or d)))
