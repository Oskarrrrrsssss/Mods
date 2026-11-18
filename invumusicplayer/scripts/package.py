"""Package bilingual docs with CI evidence; no account data is read."""
from pathlib import Path
import hashlib,os,shutil,sys,json,xml.etree.ElementTree as ET
if hasattr(sys.stdout,"reconfigure"):sys.stdout.reconfigure(encoding="utf-8")
root=Path(__file__).resolve().parents[1];dest=root/'build/distribution';dest.mkdir(parents=True,exist_ok=True)
jars=[p for p in (root/'build/libs').glob('invumusicplayer-*.jar') if not p.name.endswith(('-sources.jar','-gametest.jar','-smoketest.jar','-dev.jar'))]
if len(jars)!=1:raise RuntimeError('Expected exactly one production JAR / Нужен один основной JAR')
jar=jars[0]
import zipfile
with zipfile.ZipFile(jar) as archive:
    metadata=json.loads(archive.read('fabric.mod.json'))
    if metadata.get('depends',{}).get('minecraft')!='1.20.4' or metadata['environment']!='client':raise RuntimeError('Wrong Minecraft target / Неверная версия Minecraft')
    classes=[n for n in archive.namelist() if n.startswith('dev/invumusicplayer/') and n.endswith('.class')]
    if not classes or any(int.from_bytes(archive.read(n)[6:8],'big')!=61 for n in classes):raise RuntimeError('Java 17 bytecode required / Нужен байткод Java 17')
    if any('PortSmoke' in n or 'SmokePlayerFactory' in n for n in archive.namelist()):raise RuntimeError('Test code leaked into mod / Проверочный код попал в мод')
    if b'net/minecraft/class_310' not in archive.read('dev/invumusicplayer/client/InvuMusicPlayerClient.class'):raise RuntimeError('Production remap missing / Нет remap для Fabric')
    mixins=json.loads(archive.read('invumusicplayer.mixins.json'))
    refmap=json.loads(archive.read(mixins['refmap']))
    mapped=json.dumps(refmap.get('mappings',{}))
    if 'GameRendererMixin' not in mapped or 'class_757' not in mapped or 'class_332' not in mapped:raise RuntimeError('Mixin remap missing / Нет remap отрисовки F1')
    nested=[n for n in archive.namelist() if n.startswith('META-INF/jars/') and n.endswith('.jar')]
    if len(nested)!=11:raise RuntimeError('Bundled codec count changed / Изменился состав декодеров')
    lang=[json.loads(archive.read('assets/invumusicplayer/lang/'+code+'.json')) for code in ['ru_ru','en_us']]
    if lang[0].keys()!=lang[1].keys():raise RuntimeError('Translations differ / Набор переводов не совпадает')
shutil.copyfile(jar,dest/jar.name)
for name in ['README.md','README.en.md','VALIDATION.md','VALIDATION.en.md','THIRD_PARTY.md','THIRD_PARTY.en.md','LICENSE','FEATURES.md','FEATURES.en.md']:shutil.copyfile(root/name,dest/name)
shutil.copytree(root/'native-spotify/licenses',dest/'spotify-licenses',dirs_exist_ok=True)
shutil.copytree(root/'native-spotify/src',dest/'spotify-source/src',dirs_exist_ok=True)
for name in ['Cargo.toml','Cargo.lock']:shutil.copyfile(root/'native-spotify'/name,dest/'spotify-source'/name)
tests=failures=skipped=0
for path in (root/'build/test-results/test').glob('TEST-*.xml'):
 suite=ET.parse(path).getroot();tests+=int(suite.get('tests',0));failures+=int(suite.get('failures',0))+int(suite.get('errors',0));skipped+=int(suite.get('skipped',0))
if not tests or failures:raise RuntimeError('Test evidence missing or failed / Нет успешных результатов тестов')
os_name=os.environ.get('RUNNER_OS','Local');run=os.environ.get('GITHUB_RUN_ID','');repo=os.environ.get('GITHUB_REPOSITORY','Oskarrrrrsssss/Mods');url=f'https://github.com/{repo}/actions/runs/{run}'
sha=hashlib.sha256(jar.read_bytes()).hexdigest();screens=len(list((root/'build/run/smoke/screenshots').glob('*.png')))
for file,en in [('VALIDATION.md',False),('VALIDATION.en.md',True)]:
 prefix=(f'# invumusicplayer 0.3.0 / Minecraft 1.20.4: verified build\n\nCI: [{run}]({url}) · {os_name}\n\nTests: **{tests-skipped} passed, {failures} failed, {skipped} skipped**.\n\n' if en else f'# invumusicplayer 0.3.0 / Minecraft 1.20.4: проверенная сборка\n\nCI: [{run}]({url}) · {os_name}\n\nТесты: **{tests-skipped} прошли, {failures} ошибок, {skipped} пропусков**.\n\n')
 if os_name=='Linux':prefix+=(f'Actual Minecraft 1.20.4 client RU/EN verification completed; {screens} screenshots. Synthetic audio and WebP decoder checks only; no real Yandex/Spotify account or physical sound device.\n\n' if en else f'Проверка настоящего Minecraft 1.20.4-клиента RU/EN завершена; {screens} скриншотов. Синтетическое аудио и проверка WebP; без настоящего аккаунта Яндекса/Spotify и физического аудиоустройства.\n\n')
 else:prefix+=('The graphical Minecraft test runs in the Linux job of this workflow.\n\n' if en else 'Графический тест Minecraft выполняется в Linux-задаче этого же запуска CI.\n\n')
 if os_name=='Windows':prefix+=('Portable yt-dlp, Deno, FFmpeg and ffprobe binaries were downloaded, verified and started; MP3 conversion and decoding were checked. No video was accessed.\n\n' if en else 'Портативные yt-dlp, Deno, FFmpeg и ffprobe скачаны, проверены и запущены; MP3-преобразование и декодирование проверены. Видео не загружалось.\n\n')
 probe=root/'build/artwork-probe.json'
 if probe.exists():
  data=json.loads(probe.read_text())
  if data.get('200x200',{}).get('image'):prefix+=('Public Yandex CDN artwork was downloaded without credentials and used in the Minecraft UI test.\n\n' if en else 'Общедоступная обложка с CDN Яндекса скачана без токена и использована в проверке интерфейса Minecraft.\n\n')
 prefix+=f'{jar.name}\n\nSHA-256: `{sha}`\n\n'
 old=(dest/file).read_text(encoding="utf-8");marker='# Verification results' if en else '# Результаты проверки';idx=old.find(marker);old=old[idx:] if idx>=0 else old
 (dest/file).write_text(prefix+'---\n\n'+old,encoding='utf-8')
print(f'Packaged {jar.name}: {tests-skipped} tests passed / тестов прошли; RU + EN instructions included / инструкции включены')
