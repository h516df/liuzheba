"""Generate the geometric test fixture; no source photograph or external library."""
from pathlib import Path
import struct,zlib

def chunk(kind,data):
    return struct.pack('!I',len(data))+kind+data+struct.pack('!I',zlib.crc32(kind+data)&0xffffffff)

width,height=960,1280
colors=((42,76,117),(225,172,73),(190,216,224),(59,105,100))
pixels=bytearray()
for y in range(height):
    pixels.append(0)
    for x in range(width):pixels.extend(colors[(x//240+y//320)%4])
png=b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('!2I5B',width,height,8,2,0,0,0))+chunk(b'IDAT',zlib.compress(bytes(pixels),9))+chunk(b'IEND',b'')
target=Path(__file__).resolve().parents[1]/'app/src/androidTest/assets/v03-demo.png'
target.parent.mkdir(parents=True,exist_ok=True)
target.write_bytes(png)
print(target.relative_to(Path(__file__).resolve().parents[1]))
