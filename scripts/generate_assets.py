"""Generate tiny original Minecraft skin and whistle; no third-party dependencies."""
import math, pathlib, struct, wave, zlib
root=pathlib.Path("src/main/resources/assets/deathwhisper")
tex=root/"textures/entity/watcher.png"
tex.parent.mkdir(parents=True,exist_ok=True)
w=h=64
pixels=[[(26,27,32,255) for x in range(w)] for y in range(h)]
# Vanilla biped UVs: dark cloak-like skin and subtle red eyes on the head face
for y in range(8,16):
    for x in range(8,16): pixels[y][x]=(17,18,23,255)
for x in (10,13):
    pixels[12][x]=(240,28,34,255)
for y in range(20,32):
    for x in range(20,28): pixels[y][x]=(12,13,18,255)
raw=b"".join(b"\\x00"+b"".join(bytes(p) for p in line) for line in pixels)
def chunk(tag,data):
    return struct.pack(">I",len(data))+tag+data+struct.pack(">I",zlib.crc32(tag+data)&0xffffffff)
tex.write_bytes(b"\\x89PNG\\r\\n\\x1a\\n"+chunk(b"IHDR",struct.pack(">IIBBBBB",w,h,8,6,0,0,0))+chunk(b"IDAT",zlib.compress(raw,9))+chunk(b"IEND",b""))
rate=22050
samples=[]
for n in range(int(rate*2.1)):
    t=n/rate
    hz=775+65*math.sin(2*math.pi*0.7*t)-70*(t/2.1)
    envelope=min(1.0,t*9)*min(1.0,(2.1-t)*5)
    sample=0.29*envelope*(math.sin(2*math.pi*hz*t)+0.12*math.sin(2*math.pi*2*hz*t))
    samples.append(int(max(-1,min(1,sample))*32767))
pathlib.Path("build").mkdir(exist_ok=True)
with wave.open("build/whistle.wav","wb") as out:
    out.setnchannels(1);out.setsampwidth(2);out.setframerate(rate)
    out.writeframes(struct.pack("<"+"h"*len(samples),*samples))
(root/"sounds").mkdir(parents=True,exist_ok=True)
print("Generated",tex,"and build/whistle.wav")
