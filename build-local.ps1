param(
    [Parameter(Mandatory=$true)][string]$AndroidBuildTools,
    [Parameter(Mandatory=$true)][string]$AndroidJar,
    [Parameter(Mandatory=$true)][string]$XposedJar,
    [Parameter(Mandatory=$true)][string]$JdkHome,
    [Parameter(Mandatory=$true)][string]$Keystore,
    [string]$KeyAlias = 'release',
    [string]$Version = '0.20'
)
$ErrorActionPreference = 'Stop'
$taskProbe = $PSScriptRoot
$taskBuildTools = $AndroidBuildTools
$taskAndroidJar = $AndroidJar
$taskXposedJar = $XposedJar
New-Item -ItemType Directory -Force -Path "$taskProbe\dist" | Out-Null
$taskOutput = Join-Path $taskProbe "dist\XiaoAiAllowList-$Version.apk"
function Check-Exit { if ($LASTEXITCODE -ne 0) { throw "Build command failed: $LASTEXITCODE" } }
New-Item -ItemType Directory -Force -Path "$taskProbe\classes","$taskProbe\dex" | Out-Null
& "$taskBuildTools\aapt2.exe" compile --dir "$taskProbe\res" -o "$taskProbe\compiled.zip"
Check-Exit
& "$taskBuildTools\aapt2.exe" link -o "$taskProbe\unsigned.apk" -I $taskAndroidJar --manifest "$taskProbe\AndroidManifest.xml" -A "$taskProbe\assets" "$taskProbe\compiled.zip"
Check-Exit
$taskSources = Get-ChildItem "$taskProbe\src" -Recurse -Filter '*.java' | ForEach-Object FullName
& "$JdkHome\bin\javac.exe" -encoding UTF-8 -d "$taskProbe\build-helpers" "$taskProbe\tools\CompileSources.java"
Check-Exit
& "$JdkHome\bin\java.exe" -cp "$taskProbe\build-helpers" CompileSources --release 8 -encoding UTF-8 -classpath "$taskAndroidJar;$taskXposedJar" -d "$taskProbe\classes" -- $taskSources
Check-Exit
$taskClasses = Get-ChildItem "$taskProbe\classes" -Recurse -Filter '*.class' | ForEach-Object FullName
$taskD8ArgsFile = Join-Path $taskProbe 'd8-args.txt'
$taskD8Args = @('-cp', ('"' + "$taskBuildTools/lib/d8.jar".Replace('\','/') + '"'), 'com.android.tools.r8.D8', '--lib', ('"' + $taskAndroidJar.Replace('\','/') + '"'), '--classpath', ('"' + $taskXposedJar.Replace('\','/') + '"'), '--min-api', '28', '--output', ('"' + "$taskProbe/dex".Replace('\','/') + '"'))
$taskD8Args += @($taskClasses | ForEach-Object { '"' + $_.Replace('\','/') + '"' })
[IO.File]::WriteAllLines($taskD8ArgsFile, $taskD8Args, [Text.UTF8Encoding]::new($false))
& "$JdkHome\bin\java.exe" "@$taskD8ArgsFile"
Check-Exit
& "$JdkHome\bin\jar.exe" --update --file "$taskProbe\unsigned.apk" -C "$taskProbe\dex" classes.dex
Check-Exit
& "$taskBuildTools\zipalign.exe" -f 4 "$taskProbe\unsigned.apk" "$taskProbe\aligned.apk"
Check-Exit
& "$taskBuildTools\apksigner.bat" sign --ks $Keystore --ks-key-alias $KeyAlias --out $taskOutput "$taskProbe\aligned.apk"
Check-Exit
& "$taskBuildTools\apksigner.bat" verify --verbose $taskOutput
Check-Exit
