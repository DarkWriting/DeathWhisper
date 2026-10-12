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
for name in ("chain_puzzle_box","lament_configuration","blight_serum","shrouded_mask","ink_feather","living_harpoon","slasher_relic","security_door_controller"):
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
    elif name=="lament_configuration":
        # A golden kinetic puzzle box; distinct from the caster's obsidian gateway device.
        for y in range(5,27):
            for x in range(5,27):
                px(x,y,(200,162,75,255) if x in (5,26) or y in (5,26)
                   else (101,60,51,255))
        for i in range(8,25):
            px(i,i,(245,216,113,255))
            px(31-i,i,(245,216,113,255))
        for y in range(8,24):
            for x in range(14,18):
                px(x,y,(161,117,53,255))
        for x in range(11,21):
            for y in range(13,19):
                px(x,y,(219,182,82,255))
        for x in range(14,18):
            for y in range(14,18):
                px(x,y,(42,31,51,255))
    elif name=="blight_serum":
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
    elif name=="shrouded_mask":
        # Original stitched shadow hood; white elongated mask with crimson eyes.
        for y in range(3,30):
            for x in range(5,27):
                if (x-15.5)**2/140+(y-15.0)**2/205<1:
                    px(x,y,(25,24,39,255) if x in (5,6,25,26) else (42,43,59,255))
        for y in range(7,27):
            for x in range(10,22):
                if (x-15.5)**2/39+(y-16)**2/115<1:
                    px(x,y,(213,213,208,255) if x>11 and x<20 else (126,126,146,255))
        for y in range(13,16):
            for x in (12,13,18,19): px(x,y,(168,21,48,255))
        for x in (15,16):
            for y in (18,19,20):px(x,y,(27,26,43,255))
    elif name=="ink_feather":
        # Ink-black feather and violet supernatural glow.
        for y in range(4,29):
            center=24-y*.42
            spread=max(1, int(7*(1-abs(y-16)/17)))
            for x in range(max(2,int(center-spread)),min(30,int(center+spread))):
                px(x,y,(31,28,55,255) if (x+y)%4 else (74,45,104,255))
            if 0<=int(center)<32:px(int(center),y,(171,126,209,255))
        for i in range(10,26):
            x=int(24-i*.42)
            px(x,i,(193,163,217,255))
        for x,y in ((6,8),(25,5),(9,24),(24,24),(18,3)):
            px(x,y,(206,89,243,255))
    elif name=="living_harpoon":
        for i in range(6,27):
            px(i,31-i,(190,199,211,255))
            px(i+1,31-i,(70,80,92,255))
        for t in range(7):px(21+t,6+t,(179,30,50,255))
    elif name=="slasher_relic":
        for y in range(4,29):
            for x in range(6,26):
                if (x-15.5)**2/127+(y-15)**2/170<1:px(x,y,(30,29,39,255))
        for y in range(7,27):
            for x in range(10,22):
                if (x-15.5)**2/42+(y-16)**2/100<1:px(x,y,(178,181,178,255))
        for x in (12,13,18,19):
            for y in (14,15):px(x,y,(15,15,20,255))
        for y in range(18,25):px(16,y,(157,17,34,255))
    elif name=="security_door_controller":
        for y in range(3,29):
            for x in range(5,27):px(x,y,(103,101,117,255) if x in (5,26) or y in (3,28) else (22,25,39,255))
        for y in range(7,20):
            for x in range(8,24):px(x,y,(38,79,98,255))
        for x in (10,15,20):
            for y in (23,25):px(x,y,(165,49,174,255))
    write(name,p)
print("Created power item icons.")
