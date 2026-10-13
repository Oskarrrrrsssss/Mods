"""Stage the decoder built on this platform; credentials and audio are never packaged."""
from pathlib import Path
import hashlib,platform,shutil,sys
if hasattr(sys.stdout,"reconfigure"):sys.stdout.reconfigure(encoding="utf-8")
root=Path(__file__).resolve().parents[1]
windows=platform.system()=='Windows'
name='invumusicplayer-spotify'+('.exe' if windows else '')
source=root/'native-spotify/target/release'/name
if not source.is_file():raise RuntimeError('Build native-spotify first / Сначала соберите native-spotify')
dest=root/'build/generated/spotify-native'/('windows-x64' if windows else 'linux-x64')
dest.mkdir(parents=True,exist_ok=True)
shutil.copyfile(source,dest/name)
(dest/'sha256.txt').write_text(hashlib.sha256(source.read_bytes()).hexdigest()+'\n',encoding='ascii')
print('Spotify native decoder staged / Встроенный декодер Spotify добавлен:',name)
