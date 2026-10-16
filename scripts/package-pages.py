"""Create a self-contained Pages artifact using the APK from this exact build."""
from pathlib import Path
import hashlib,json,os,shutil
root=Path(__file__).resolve().parents[1]
out=root/'_site'
if out.exists():shutil.rmtree(out)
out.mkdir();(out/'downloads').mkdir()
shutil.copyfile(root/'index.html',out/'index.html')
shutil.copytree(root/'assets',out/'assets')
apk=root/'app/build/outputs/apk/debug/app-debug.apk'
if not apk.is_file():raise SystemExit('Build the debug APK before packaging Pages')
name='TN-Player-1.0.0-debug.apk'
shutil.copyfile(apk,out/'downloads'/name)
sha=hashlib.sha256(apk.read_bytes()).hexdigest()
(out/'downloads/SHA256SUMS.txt').write_text(f'{sha}  {name}\n')
(out/'build-info.json').write_text(json.dumps({'app':'TN-Player','version':'1.0.0','commit':os.environ.get('GITHUB_SHA','local'),'apk_sha256':sha,'apk_bytes':apk.stat().st_size},indent=2)+'\n')
(out/'.nojekyll').touch()
print('Pages artifact assembled with current APK and SHA-256 checksum.')
