#!/usr/bin/env python3
"""Create a synthetic PNG for picker/generation QA; no user photos required."""
import math, struct, zlib, sys
from pathlib import Path
w,h=480,360
rows=[]
for y in range(h):
    row=bytearray([0])
    for x in range(w):
        sun=(x-350)**2+(y-85)**2<45**2
        hill=y>245+30*math.sin(x/90)
        rgb=(238,196,111) if sun else ((61,93,82) if hill else (156-y//7,181-y//8,186-y//9))
        row.extend(rgb)
    rows.append(row)
def chunk(t,data): return struct.pack('>I',len(data))+t+data+struct.pack('>I',zlib.crc32(t+data)&0xffffffff)
p=Path(sys.argv[1] if len(sys.argv)>1 else '/tmp/lottery-qa-seed.png')
p.write_bytes(b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('>IIBBBBB',w,h,8,2,0,0,0))+chunk(b'IDAT',zlib.compress(b''.join(rows)))+chunk(b'IEND',b''))
print(p)
