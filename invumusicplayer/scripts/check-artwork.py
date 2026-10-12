"""Probe a public artwork URI from the upstream protocol test; no credentials or audio."""
from pathlib import Path
import json,urllib.request,urllib.error
root=Path(__file__).resolve().parents[1]
dest=root/'build/run/clientGameTest';dest.mkdir(parents=True,exist_ok=True)
base='https://avatars.yandex.net/get-music-content/95061/89c14a7d.a.5239478-1/'
results={}
for size in ['200x200','256x256']:
 try:
  request=urllib.request.Request(base+size,headers={'User-Agent':'InvuMusicPlayer/0.2.1'})
  with urllib.request.urlopen(request,timeout=30) as response:
   data=response.read(8_000_001);content_type=response.headers.get('Content-Type','')
   good=len(data)<=8_000_000 and (data.startswith(b'\xff\xd8') or data.startswith(b'\x89PNG') or data[:4]==b'RIFF')
   results[size]={'http':response.status,'image':good,'bytes':len(data),'content_type':content_type}
   if size=='200x200' and good:(dest/'reference-cover.jpg').write_bytes(data)
 except urllib.error.HTTPError as e:results[size]={'http':e.code,'image':False}
 except Exception as e:results[size]={'error':type(e).__name__,'image':False}
(root/'build/artwork-probe.json').write_text(json.dumps(results,indent=2))
print('Public artwork probe / Проверка общедоступной обложки:',json.dumps(results))
