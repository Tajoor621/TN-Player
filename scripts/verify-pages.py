"""Fail deployment if the public site serves a stale build or missing/mismatched APK."""
import hashlib,json,sys,time,urllib.parse,urllib.request
base=sys.argv[1].rstrip('/')+'/'
expected=sys.argv[2]
last=None
for attempt in range(30):
    try:
        query=urllib.parse.urlencode({'revision':expected,'attempt':attempt})
        with urllib.request.urlopen(base+'build-info.json?'+query,timeout=30) as response:
            info=json.load(response)
        if info['commit']!=expected:
            raise ValueError('Public site still serves another commit')
        sha=hashlib.sha256();size=0
        with urllib.request.urlopen(base+'downloads/TN-Player-1.0.0-debug.apk?'+query,timeout=60) as response:
            while chunk:=response.read(1024*1024):
                sha.update(chunk);size+=len(chunk)
        if sha.hexdigest()!=info['apk_sha256'] or size!=info['apk_bytes']:
            raise ValueError('Public APK differs from the build manifest')
        print('Verified public site commit and full APK SHA-256:',expected,sha.hexdigest())
        break
    except (OSError,ValueError,KeyError) as error:
        last=error
        if attempt<29:time.sleep(5)
else:
    raise SystemExit('Public Pages verification failed: '+str(last))
