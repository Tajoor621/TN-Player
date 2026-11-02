"""Measure actual packaged decoder overhead, not AAR download size."""
from pathlib import Path
import json,zipfile
out=Path('app/build/reports');out.mkdir(parents=True,exist_ok=True)
report={}
for variant,path in [('debug','app/build/outputs/apk/debug/app-debug.apk'),('release','app/build/outputs/apk/release/app-release-unsigned.apk')]:
    p=Path(path)
    if not p.exists():continue
    with zipfile.ZipFile(p) as z:
        libs=[i for i in z.infolist() if i.filename.startswith('lib/') and i.filename.endswith('.so')]
        abi={}
        for i in libs:
            a=i.filename.split('/')[1];d=abi.setdefault(a,{'compressed_bytes':0,'uncompressed_bytes':0,'libraries':[]})
            d['compressed_bytes']+=i.compress_size;d['uncompressed_bytes']+=i.file_size;d['libraries'].append(i.filename.split('/')[-1])
        report[variant]={'apk_bytes':p.stat().st_size,'native_by_abi':abi}
        if variant=='debug':report[variant]['increase_over_2026_10_18_debug_bytes']=p.stat().st_size-11635069
(out/'codec-size-report.json').write_text(json.dumps(report,indent=2)+'\n')
print(json.dumps(report,indent=2))
