"""Generate original mirrored-stalker fallback texture and six atmospheric OGG inputs."""
import math, pathlib, random, struct, wave, zlib
root=pathlib.Path("src/main/resources/assets/deathwhisper")
tex=root/"textures/entity/watcher.png"
tex.parent.mkdir(parents=True,exist_ok=True)
size=64
rng=random.Random(20261011)
pixels=[]
for y in range(size):
    row=[]
    for x in range(size):
        noise=rng.randrange(-12,13)
        color=(max(0,min(255,185+noise)),max(0,min(255,184+noise)),max(0,min(255,208+noise)),255)
        if 8<=y<16 and 8<=x<16:color=(232,225,216,255)
        if y in (11,12) and x in (10,13):color=(235,10,15,255)
        row.append(color)
    pixels.append(row)
raw=b"".join(bytes([0])+b"".join(bytes(p) for p in row) for row in pixels)
def chunk(name,data):
    return struct.pack(">I",len(data))+name+data+struct.pack(">I",zlib.crc32(name+data)&0xFFFFFFFF)
tex.write_bytes(bytes([137,80,78,71,13,10,26,10])+chunk(b"IHDR",struct.pack(">IIBBBBB",64,64,8,6,0,0,0))+chunk(b"IDAT",zlib.compress(raw,9))+chunk(b"IEND",b""))
pathlib.Path("build").mkdir(exist_ok=True)
rate=22050
sounds={
"whistle":(2.1,"whistle",1),
"whistle_alt":(2.3,"whistle",2),
"whistle_low":(2.5,"whistle",3),
"footsteps":(.9,"footsteps",4),
"laughter":(2.4,"laughter",5),
"ambience":(4.0,"ambience",6),
}
for name,(duration,kind,seed) in sounds.items():
    randomizer=random.Random(seed)
    phase=0.0
    samples=[]
    for n in range(int(duration*rate)):
        t=n/rate
        fade=min(1.0,t*8,(duration-t)*8)
        if kind=="whistle":
            frequency=(730 if seed==1 else 870 if seed==2 else 510)+95*math.sin(t*1.8+seed)
            phase+=2*math.pi*frequency/rate
            signal=(math.sin(phase)+.19*math.sin(2*phase))*.28
        elif kind=="footsteps":
            pulse=max(0,1-((t*4)%1)*7)
            signal=(randomizer.random()*2-1)*pulse*.21
        elif kind=="laughter":
            phase+=2*math.pi*(170+40*math.sin(t*13))/rate
            pulse=.5+.5*math.sin(t*16)
            signal=(math.sin(phase)*.18+(randomizer.random()*2-1)*.045)*pulse
        else:
            phase+=2*math.pi*(64+8*math.sin(t*.7))/rate
            signal=.15*math.sin(phase)+.015*(randomizer.random()*2-1)
        samples.append(int(max(-1,min(1,signal*fade))*32767))
    with wave.open("build/"+name+".wav","wb") as out:
        out.setnchannels(1)
        out.setsampwidth(2)
        out.setframerate(rate)
        out.writeframes(struct.pack("<"+"h"*len(samples),*samples))
print("Original assets generated; target-player skin inversion occurs dynamically on the client")
