"""Generate original ghost, motion sensor and cursed investigation equipment sprites."""
from pathlib import Path
import struct,zlib,math,random
root=Path("src/main/resources/assets/deathwhisper")
def png(path, pixels):
    path.parent.mkdir(parents=True,exist_ok=True)
    h,w=len(pixels),len(pixels[0])
    raw=b''.join(b'\x00'+b''.join(bytes(p) for p in row) for row in pixels)
    def chunk(name,value):
        return struct.pack(">I",len(value))+name+value+struct.pack(">I",zlib.crc32(name+value)&0xffffffff)
    (path).write_bytes(b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack(">IIBBBBB",w,h,8,6,0,0,0))
        +chunk(b'IDAT',zlib.compress(raw,9))+chunk(b'IEND',b''))

# A faded, almost transparent humanoid with bright spectral eyes.
r=random.Random(713)
ghost=[]
for y in range(64):
    line=[]
    for x in range(64):
        shade=r.randrange(-12,14)
        alpha=132
        if 8<=x<16 and 8<=y<16:
            shade+=28
            if x in (10,13) and y in (11,12):line.append((107,249,245,235));continue
        line.append((max(0,108+shade),max(0,148+shade),max(0,170+shade),alpha))
    ghost.append(line)
png(root/"textures/entity/ghost_apparition.png",ghost)

for activated in (False,True):
    image=[]
    for y in range(16):
        line=[]
        for x in range(16):
            edge=x in (0,1,14,15) or y in (0,1,14,15)
            if edge:color=(77,83,103,255)
            elif x in (2,13) or y in (2,13):color=(18,26,38,255)
            elif 5<=x<=10 and 5<=y<=10:
                color=(79,232,142,255) if activated else (199,56,86,255)
            else:color=(31,42,58,255)
            line.append(color)
        image.append(line)
    png(root/("textures/block/motion_sensor_on.png" if activated else "textures/block/motion_sensor_off.png"),image)

colors={
  "cursed_recorder":(105,85,120,255),
  "cursed_tape":(159,65,109,255),
  "ghost_hunt_kit":(46,112,115,255)
}
for name,base in colors.items():
    image=[[(0,0,0,0) for x in range(32)] for y in range(32)]
    def box(x0,y0,x1,y1,c):
        for y in range(y0,y1):
            for x in range(x0,x1):image[y][x]=c
    dark=(25,24,38,255);silver=(177,190,204,255);light=(215,223,216,255)
    if name=="cursed_recorder":
        box(5,7,27,25,base);box(7,10,25,23,dark)
        for x in (11,20):
            box(x-3,12,x+3,19,silver)
            box(x-1,14,x+1,17,dark)
        box(12,22,20,24,(171,115,170,255))
    elif name=="cursed_tape":
        box(5,7,27,25,dark);box(6,8,26,24,base)
        box(9,11,23,18,light)
        box(11,13,21,16,(58,43,62,255))
        for x in (11,21):
            box(x-3,18,x+3,22,silver)
            box(x-1,19,x+1,21,dark)
    else:
        box(6,3,26,29,base)
        box(7,5,25,27,dark)
        box(9,7,23,19,(31,82,89,255))
        box(11,9,21,16,(62,199,187,255))
        for x in (10,14,19):
            box(x,22,x+2,25,silver)
    png(root/("textures/item/"+name+".png"),image)
print("Ghost, sensor and recording graphics generated")
