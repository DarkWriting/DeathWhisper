"""Generate five original 32px investigation instrument textures, zero external art."""
import pathlib, struct, zlib
root=pathlib.Path("src/main/resources/assets/deathwhisper/textures/item")
root.mkdir(parents=True,exist_ok=True)

def write(name, palette):
    image=[[(0,0,0,0) for x in range(32)] for y in range(32)]
    def box(x0,y0,x1,y1,fill):
        for y in range(max(0,y0),min(32,y1)):
            for x in range(max(0,x0),min(32,x1)):image[y][x]=fill
    dark=(32,30,44,255);metal=(148,158,177,255);light=(209,205,188,255)
    cyan=(55,192,190,255);violet=(130,77,169,255);red=(179,53,78,255)
    if name=="seance_chalk":
        box(5,22,27,27,light);box(8,18,24,23,(226,220,209,255))
        box(10,12,22,19,(245,238,231,255))
        box(11,11,21,13,metal)
        for x in range(9,24,3):box(x,23,x+1,25,violet)
        box(12,6,20,11,light)
    elif name=="field_journal":
        box(6,4,25,29,(114,65,89,255))
        box(9,6,25,26,(189,160,133,255))
        box(9,9,25,25,(226,209,184,255))
        for y in (12,16,20,24):box(12,y,22,y+1,(128,100,119,255))
        box(5,5,9,28,dark)
        box(6,7,8,24,(200,151,87,255))
        box(17,5,21,12,red)
    elif name=="emf_reader":
        box(9,4,23,29,dark)
        box(11,6,21,19,metal)
        box(12,7,20,18,(31,68,49,255))
        for i,colour in enumerate([(58,177,62,255),(93,206,64,255),(201,208,50,255),(244,153,51,255),red]):
            box(13,16-i*2,19,17-i*2,colour)
        box(12,21,20,25,metal)
        box(15,2,17,5,light)
    elif name=="spirit_radio":
        box(5,8,27,26,dark)
        box(7,10,25,24,(74,71,95,255))
        box(10,12,22,17,(21,27,44,255))
        for x in range(11,22,2):
            box(x,14,x+1,15,cyan)
        for x in range(9,24,3):box(x,19,x+1,23,metal)
        box(8,5,10,9,light)
        box(9,3,11,6,metal)
        box(23,19,26,22,red)
    else: # ghost_thermometer
        box(14,4,19,23,metal)
        box(15,5,18,24,light)
        box(16,8,17,24,cyan)
        for y in (10,13,16,19):box(11,y,14,y+1,dark)
        for y in range(20,29):
            for x in range(10,23):
                if (x-16)**2+(y-22)**2<32:image[y][x]=metal
                if (x-16)**2+(y-22)**2<17:image[y][x]=cyan
    def chunk(name,data):
        return struct.pack(">I",len(data))+name+data+struct.pack(">I",zlib.crc32(name+data)&0xffffffff)
    raw=b"".join(b"\x00"+b"".join(bytes(pixel) for pixel in row) for row in image)
    png=b"\x89PNG\r\n\x1a\n"+chunk(b"IHDR",struct.pack(">IIBBBBB",32,32,8,6,0,0,0))
    png+=chunk(b"IDAT",zlib.compress(raw,9))+chunk(b"IEND",b"")
    (root/(name+".png")).write_bytes(png)
for name in ["seance_chalk","field_journal","emf_reader","spirit_radio","ghost_thermometer"]:
    write(name,None)
print("Investigation tool textures generated.")
