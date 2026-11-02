"""Ship exact corresponding sources, patches, build flags and relink material with binaries."""
from pathlib import Path
import hashlib,json,tarfile,shutil
root=Path(__file__).resolve().parents[1]
work=root/'.native';dist=work/'dist';notices=dist/'notices'
lock=json.loads((root/'software-codecs/native-lock.json').read_text())
for name in ['ffmpeg','libvpx','libgav1','cpu_features']:
    src=work/'sources'/name
    for p in src.iterdir():
        if p.is_file() and (p.name.startswith(('COPYING','LICENSE','NOTICE','PATENTS'))):
            shutil.copyfile(p,notices/f'{name}-{p.name}')
ndk_source=(work/'sources/ndk_cpu_features/cpu-features.c').read_text()
(notices/'NDK-cpu-features-LICENSE.txt').write_text(ndk_source.split('*/',1)[0]+'*/\n')
shutil.copyfile(root/'software-codecs/licenses/Media3-ffmpeg-APACHE-2.0.txt',notices/'Media3-APACHE-2.0.txt')
manifest={'lock':lock,'libraries':{str(p.relative_to(dist)):{'bytes':p.stat().st_size,'sha256':hashlib.sha256(p.read_bytes()).hexdigest()} for p in sorted((dist/'jniLibs').rglob('*.so'))}}
(dist/'native-build.json').write_text(json.dumps(manifest,indent=2)+'\n')
shutil.copyfile(dist/'native-build.json',notices/'native-build.json')
with tarfile.open(dist/'TN-Player-native-sources.tar.xz','w:xz') as tar:
    def keep(info):
        return None if '.git' in Path(info.name).parts else info
    tar.add(work/'sources',arcname='.native/sources',filter=keep)
    tar.add(work/'config',arcname='.native/config')
    tar.add(root/'software-codecs',arcname='software-codecs',filter=lambda info: None if 'jniLibs' in Path(info.name).parts or 'build' in Path(info.name).parts else info)
    for name in ['build-native-codecs.sh','install-native-codecs.sh','package-native-sources.py','generate-codec-fixtures.sh']:
        tar.add(root/'scripts'/name,arcname='scripts/'+name)
print('Native libraries, per-ABI sizes, licenses and corresponding source archive prepared.')
