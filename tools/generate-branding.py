#!/usr/bin/env python3
"""Build the approved Emrooz Android artwork directly from the user's PNG.

Requires Pillow. No model-generated/redrawn logo is used. Run from any directory.
"""
from pathlib import Path
from PIL import Image, ImageChops, ImageDraw
from io import BytesIO
from zipfile import ZipFile, ZIP_DEFLATED
import hashlib, json, math, os, tempfile

ROOT=Path(__file__).resolve().parents[1]
SRC=ROOT/'branding/source/F7F2EA.png'
RES=ROOT/'app/src/main/res'
OUT=ROOT/'out/branding'; OUT.mkdir(parents=True,exist_ok=True)
EXPECTED='baa6459571faaf06aa6c727f54b52330dea684fe39584c45c4ed1c7bc42b5ca3'
assert hashlib.sha256(SRC.read_bytes()).hexdigest()==EXPECTED, 'Unexpected source logo'
original=Image.open(SRC).convert('RGBA')
assert original.size==(1024,1536)
box=original.getchannel('A').point(lambda v:255 if v>1 else 0).getbbox()
cx=(box[0]+box[2])/2;cy=(box[1]+box[3])/2;visible_h=box[3]-box[1]
DENSITIES={'mdpi':48,'hdpi':72,'xhdpi':96,'xxhdpi':144,'xxxhdpi':192}
CREAM=(247,242,234,255);NAVY=(13,23,38,255)


def save(image,path,**options):
    path.parent.mkdir(parents=True,exist_ok=True)
    buffer=BytesIO();image.save(buffer,format=Image.registered_extensions()[path.suffix],**options)
    data=buffer.getvalue()
    with Image.open(BytesIO(data)) as check:check.verify()
    if path.exists() and path.read_bytes()==data:return
    with tempfile.NamedTemporaryFile(dir=OUT,delete=False) as tmp:
        tmp.write(data);tmp.flush();os.fsync(tmp.fileno());temp=Path(tmp.name)
    try:os.replace(temp,path)
    finally:temp.unlink(missing_ok=True)


def compose(art,size,logo_h,bg):
    scale=logo_h/visible_h
    dims=(round(art.width*scale),round(art.height*scale))
    assert abs(dims[0]/dims[1]-art.width/art.height)<=1/dims[1]
    result=Image.new('RGBA',size,bg)
    result.alpha_composite(art.resize(dims,Image.Resampling.LANCZOS),
                           (round(size[0]/2-cx*scale),round(size[1]/2-cy*scale)))
    return result

# Only the wordmark RGB changes in the approved night palette. Coordinates,
# alpha, original copper bookmark and copper dot stay exactly the same.
night=original.copy();pixels=night.load();changed=0
for y in range(930,1536):
    for x in range(1024):
        r,g,b,a=pixels[x,y]
        if not a or (180<=x<=306 and 945<=y<=1061):continue
        luminance=.2126*r+.7152*g+.0722*b
        factor=.89+.11*min(1,luminance/80)
        pixels[x,y]=(int(247*factor),int(242*factor),int(234*factor),a);changed+=1
assert night.getchannel('A').tobytes()==original.getchannel('A').tobytes()
assert night.crop((0,0,1024,930)).tobytes()==original.crop((0,0,1024,930)).tobytes()
assert night.crop((180,945,307,1062)).tobytes()==original.crop((180,945,307,1062)).tobytes()
archive=[];metrics={'source_sha256':EXPECTED,'source_size':original.size,'visible_bbox':box,
                    'launcher_sizes':DENSITIES,'splash_visible_height_fraction':.29,
                    'night_alpha_unchanged':True,'copper_unchanged':True,'wordmark_tinted_pixels':changed}
proof=Image.new('RGB',(1080,1220),'#E9EBEE');d=ImageDraw.Draw(proof)
for index,(theme,art,bg) in enumerate([('light',original,CREAM),('dark',night,NAVY)]):
    for density,n in DENSITIES.items():
        directory=RES/(f'mipmap-{density}' if theme=='light' else f'mipmap-night-{density}')
        icon=compose(art,(n,n),round(n*2/3),bg)
        save(icon.convert('RGB'),directory/'ic_launcher.png')
        mask=Image.new('L',(n*4,n*4));ImageDraw.Draw(mask).ellipse((0,0,n*4-1,n*4-1),fill=255)
        icon.putalpha(mask.resize((n,n),Image.Resampling.LANCZOS))
        save(icon,directory/'ic_launcher_round.png')
        for name in ['ic_launcher.png','ic_launcher_round.png']:
            archive.append((directory/name,f'{theme}/mipmap-{density}/{name}'))
    store=OUT/f'icon-{theme}-512.png';save(compose(art,(512,512),round(512*2/3),bg).convert('RGB'),store)
    archive.append((store,f'{theme}/icon-512.png'))
    directory=RES/('drawable-nodpi' if theme=='light' else 'drawable-night-nodpi')
    foreground=compose(art,(432,432),192,(0,0,0,0))
    fb=foreground.getchannel('A').point(lambda v:255 if v>2 else 0).getbbox()
    assert math.hypot(fb[2]-fb[0],fb[3]-fb[1])<=266
    save(foreground,directory/'ic_launcher_foreground_png.png')
    # Android12+ no-icon-background launch image:288dp canvas,192dp safe circle.
    h=math.floor(768/math.hypot((box[2]-box[0])/visible_h,1))
    save(compose(art,(1152,1152),h,(0,0,0,0)),directory/'splash_system_logo.png')
    splash=compose(art,(1080,1920),round(1920*.29),bg).convert('RGB')
    target=RES/'drawable-nodpi'/f'splash_emrooz_{theme}.webp'
    save(splash,target,lossless=True,method=6)
    assert ImageChops.difference(splash,Image.open(target).convert('RGB')).getbbox() is None
    save(splash,OUT/f'splash-{theme}-1080x1920.png')
    archive.append((OUT/f'splash-{theme}-1080x1920.png',f'{theme}/splash-1080x1920.png'))
    # Representative phone aspect ratios use precisely the app's Fit behavior.
    from PIL import ImageOps
    for j,viewport in enumerate([(360,640),(360,800)]):
        fitted=ImageOps.contain(splash,viewport,Image.Resampling.LANCZOS)
        screen=Image.new('RGB',viewport,bg[:3]);screen.paste(fitted,((viewport[0]-fitted.width)//2,(viewport[1]-fitted.height)//2))
        preview=screen.resize((270,viewport[1]*3//4),Image.Resampling.LANCZOS)
        proof.paste(preview,((index*2+j)*270,36))
        d.text(((index*2+j)*270+8,12),f'{theme} {viewport[0]}x{viewport[1]} / Fit',fill='#263137')
    proof.paste(compose(art,(230,230),round(230*2/3),bg).convert('RGB'),(70+index*540,710))
    for j,(density,n) in enumerate(list(DENSITIES.items())[:3]):
        path=RES/(f'mipmap-{density}' if theme=='light' else f'mipmap-night-{density}')/'ic_launcher.png'
        proof.paste(Image.open(path),(35+index*540+j*160,1030+(96-n)//2))
        d.text((35+index*540+j*160,1140),f'{n}x{n}',fill='#263137')
save(proof,OUT/'branding-proof.png')
(OUT/'validation.json').write_text(json.dumps(metrics,indent=2)+'\n')
with ZipFile(OUT/'emrooz-original-icons.zip','w',ZIP_DEFLATED) as z:
    z.write(SRC,'source/F7F2EA.png')
    for path,name in archive:z.write(path,name)
print(json.dumps(metrics,indent=2))
print('Source identity, unchanged alpha/copper, uniform scales, safe mask and lossless splash: PASS')
