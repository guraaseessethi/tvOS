"""Painted tile frames with a rounded window: real app icons (from ECP /query/icon) show through."""
import os
from PIL import Image, ImageDraw
SS=4; W,H,R=288,192,34; HW,HH,HR=208,156,20
OUT='pkg/lumen/images/tiles'
def rr(w,h,r,box=None,size=None):
    m=Image.new('L',((size or (w,h))[0]*SS,(size or (w,h))[1]*SS),0)
    x,y=box or (0,0)
    ImageDraw.Draw(m).rounded_rectangle([x*SS,y*SS,(x+w)*SS-1,(y+h)*SS-1],r*SS,fill=255)
    return m.resize(size or (w,h),Image.LANCZOS)
for f in sorted(os.listdir('tiles')):
    name=f[5:-4]
    t=Image.open('tiles/'+f).convert('RGBA').resize((W,H),Image.LANCZOS)
    wash=Image.new('RGBA',(W,H),(255,255,255,0)); d=ImageDraw.Draw(wash)
    for y in range(H): d.line([(0,y),(W,y)],fill=(255,255,255,int(255*(.38-.26*y/H))))
    t=Image.alpha_composite(t,wash)
    hx,hy=(W-HW)//2,(H-HH)//2
    outer=rr(W,H,R); hole=rr(HW,HH,HR,(hx,hy),(W,H))
    a=Image.eval(Image.composite(Image.new('L',(W,H),0),outer,hole),lambda v:v)
    t.putalpha(a)
    # thin cream lip around the window
    lip=Image.new('RGBA',(W*SS,H*SS),(0,0,0,0))
    ImageDraw.Draw(lip).rounded_rectangle([(hx-3)*SS,(hy-3)*SS,(hx+HW+3)*SS,(hy+HH+3)*SS],(HR+3)*SS,outline=(251,247,236,255),width=3*SS)
    lip=lip.resize((W,H),Image.LANCZOS)
    t=Image.alpha_composite(t,lip)
    t.save(f'{OUT}/frame_{name}.png')
print('frames ok')
