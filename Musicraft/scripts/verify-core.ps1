param([string]$JdkHome = $env:JAVA_HOME)
$ErrorActionPreference = 'Stop'
$project = Split-Path -Parent $PSScriptRoot
$cache = Join-Path $project '.core-check'
$classes = Join-Path $cache 'classes'
New-Item -ItemType Directory -Force $classes | Out-Null
$java = if ($JdkHome) { Join-Path $JdkHome 'bin\java.exe' } else { 'java' }
$javac = if ($JdkHome) { Join-Path $JdkHome 'bin\javac.exe' } else { 'javac' }
$artifacts = @(
    @{ Name = 'gson'; Path = 'com/google/code/gson/gson/2.13.2/gson-2.13.2.jar'; Sha256 = 'dd0ce1b55a3ed2080cb70f9c655850cda86c206862310009dcb5e5c95265a5e0' }
    @{ Name = 'jna'; Path = 'net/java/dev/jna/jna/5.18.1/jna-5.18.1.jar'; Sha256 = '260c4b1e22b1db9e110ee441c4f13ce115f841fa48c41d78750986214b395557' }
    @{ Name = 'jna-platform'; Path = 'net/java/dev/jna/jna-platform/5.18.1/jna-platform-5.18.1.jar'; Sha256 = 'ad14c1b1ec4f43d396231219dfa635ebf828f738eac9f890ea1bc07795892d9a' }
    @{ Name = 'junit'; Path = 'org/junit/platform/junit-platform-console-standalone/1.13.4/junit-platform-console-standalone-1.13.4.jar'; Sha256 = '3fdfc37e29744a9a67dd5365e81467e26fbde0b7aa204e6f8bbe79eeaa7ae892' }
    @{ Name = 'javasound-mp3'; Path = 'com/tianscar/javasound/javasound-mp3/1.9.8/javasound-mp3-1.9.8.jar'; Sha256 = '45bb2331c5baa9f9206b34a75d25a299835c5b5cf5d55ecf33339b4adda365e7' }
    @{ Name = 'javasound-vorbis'; Path = 'com/tianscar/javasound/javasound-vorbis/2.1.0/javasound-vorbis-2.1.0.jar'; Sha256 = 'd5f3b6509f8b5b1cbde21b6ba21a5c1ad1dbaef52b580df0f0c0c969cc7bf86e' }
    @{ Name = 'tritonus-share'; Path = 'com/googlecode/soundlibs/tritonus-share/0.3.7.4/tritonus-share-0.3.7.4.jar'; Sha256 = '6f626b41dc72074656059de1a25bc6f1c5abb202b57d37c538f5714f54efdab4' }
)
$jars = @()
foreach ($artifact in $artifacts) {
    $target = Join-Path $cache ($artifact.Name + '.jar')
    if (!(Test-Path $target)) {
        Invoke-WebRequest -UseBasicParsing -Uri ('https://repo.maven.apache.org/maven2/' + $artifact.Path) -OutFile $target
    }
    if ((Get-FileHash -Algorithm SHA256 $target).Hash.ToLowerInvariant() -ne $artifact.Sha256) {
        throw ('Dependency checksum mismatch: ' + $artifact.Name)
    }
    $jars += $target
}
$classpath = $jars -join [IO.Path]::PathSeparator
$sources = @(Get-ChildItem (Join-Path $project 'src\main\java') -Recurse -Filter '*.java' | ForEach-Object FullName)
$sources += @(Get-ChildItem (Join-Path $project 'src\test\java') -Recurse -Filter '*.java' | ForEach-Object FullName)
& $javac -encoding UTF-8 -cp $classpath -d $classes @sources
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
$testClasspath = $classes + [IO.Path]::PathSeparator + (Join-Path $project 'src\test\resources') + [IO.Path]::PathSeparator + $classpath
& $java -cp $testClasspath org.junit.platform.console.ConsoleLauncher execute --scan-class-path --disable-banner --details=summary
exit $LASTEXITCODE
