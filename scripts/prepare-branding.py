"""Package the approved master artwork; never invent or redraw the supplied original."""
from pathlib import Path
from PIL import Image
import argparse, hashlib, json, shutil
root=Path(__file__).resolve().parents[1]
p=argparse.ArgumentParser();p.add_argument('original',type=Path);p.add_argument('icon',type=Path);a=p.parse_args()
original=a.original.resolve(); icon=a.icon.resolve()
shutil.copyfile(original,root/'branding/TN-Player-logo-original.png')
shutil.copyfile(icon,root/'branding/TN-Player-icon-master.png')
# Resampling only: image-generation produced the separate emblem adaptation.
def png(source,path,size):
 im=Image.open(source).convert('RGB');im.thumbnail((size,size),Image.Resampling.LANCZOS)
 canvas=Image.new('RGB',(size,size),'black');canvas.paste(im,((size-im.width)//2,(size-im.height)//2))
 path.parent.mkdir(parents=True,exist_ok=True);canvas.save(path,optimize=True)
for path,size in [('branding/TN-Player-logo.png',512),('branding/TN-Player-wordmark.png',768),('app/src/main/res/drawable-nodpi/tn_logo.png',512),('app/src/main/res/drawable-nodpi/tn_wordmark.png',512),('assets/tn-player-icon.png',192),('assets/tn-player-brand.png',768)]:
 png(original if 'wordmark' in path or 'brand.png' in path else icon,root/path,size)
for density,size in [('mdpi',48),('hdpi',72),('xhdpi',96),('xxhdpi',144),('xxxhdpi',192)]:
 png(icon,root/f'app/src/main/res/mipmap-{density}/ic_launcher.png',size)
(root/'branding/source-manifest.json').write_text(json.dumps({'original_sha256':hashlib.sha256(original.read_bytes()).hexdigest(),'icon_master_sha256':hashlib.sha256(icon.read_bytes()).hexdigest(),'original':'TN-Player-logo-original.png','launcher':'TN-Player-logo.png','date':'2026-10-18','method':'User original preserved unchanged; built-in image edit for emblem adaptation; Pillow LANCZOS packaging'},indent=2)+'\n')
print('Original preserved; Android density icons, in-app marks and web assets packaged.')
