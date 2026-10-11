"""Generate self-contained, original pixel icons for the horror powers."""
from pathlib import Path
import zlib,struct,math
out=Path("src/main/resources/assets/deathwhisper/textures/item")
out.mkdir(parents=True,exist_ok=True)
def write(name,pixels):
    h=len(pixels);w=len(pixels[0])
    raw=b"".join(b"\x00"+b"".join(bytes(p) for p in row) for row in pixels)
    def part(key,data):
        return struct.pack(">I",len(data))+key+data+struct.pack(">I",zlib.crc32(key+data)&0xffffffff)
    png=b"\x89PNG\r\n\x1a\n"+part(b"IHDR",struct.pack(">IIBBBBB",w,h,8,6,0,0,0))
    png+=part(b"IDAT",zlib.compress(raw,9))+part(b"IEND",b"")
    (out/(name+".png")).write_bytes(png)
for name in ("chain_puzzle_box","blight_serum"):
    p=[[(0,0,0,0) for x in range(32)] for y in range(32)]
    def px(x,y,c):
        if 0<=x<32 and 0<=y<32:p[y][x]=c
    if name=="chain_puzzle_box":
        # Brass and onyx geometric puzzle box, crossed pinhead-inspired gold bands.
        for y in range(5,27):
            for x in range(5,27):
                edge=x in (5,6,25,26) or y in (5,6,25,26)
                px(x,y,(193,144,58,255) if edge else (24,27,41,255))
        for i in range(7,25):
            px(i,i,(233,202,91,255))
            px(31-i,i,(233,202,91,255))
        for y in range(9,23):
            px(15,y,(162,122,49,255))
            px(16,y,(162,122,49,255))
        for x in range(9,23):
            px(x,15,(162,122,49,255))
            px(x,16,(162,122,49,255))
        for x in range(13,19):
            for y in range(13,19):px(x,y,(89,33,115,255))
        for x,y in ((15,14),(16,14),(15,17),(16,17)):px(x,y,(239,178,253,255))
    else:
        # Bright, eerie violet serum inside an amber vial with a silver stopper.
        for y in range(3,7):
            for x in range(12,20):px(x,y,(179,188,199,255))
        for y in range(7,12):
            for x in range(11,21):px(x,y,(209,177,94,255) if x in (11,20) else (82,78,109,255))
        for y in range(12,27):
            left=8 if y<23 else 11;right=24 if y<23 else 21
            for x in range(left,right):
                edge=x in (left,right-1) or y==26
                px(x,y,(145,169,177,255) if edge else (150,29+3*(x%5),194,255))
        for x,y in ((12,13),(15,16),(19,19),(13,23),(20,14),(17,24)):
            px(x,y,(243,181,255,255))
    write(name,p)
print("Created power item icons.")
