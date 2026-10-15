[CmdletBinding()]
param(
    [string]$JavaHome,
    [string]$SdkRoot = "$env:LOCALAPPDATA\Android\Sdk",
    [string]$AuthAssets = "$env:USERPROFILE\DiPlay-runtime-assets",
    [string]$Proxy,
    [switch]$Offline,
    [switch]$Check,
    [switch]$Install,
    [string]$Serial = '2064eb6d'
)
$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path $PSScriptRoot -Parent
if (-not $JavaHome) {
    $supportRoot = Join-Path (Split-Path $repoRoot -Parent) 'DiPlay-local-support'
    $localJdk = Get-ChildItem (Join-Path $supportRoot 'tools') -Directory -Filter 'jdk-25*' -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($localJdk) { $JavaHome = $localJdk.FullName } else { $JavaHome = $env:JAVA_HOME }
}
if (-not $JavaHome -or -not (Test-Path "$JavaHome\bin\java.exe")) { throw 'Specify -JavaHome with a JDK 25 directory.' }
foreach ($authName in @('identity.pk8', 'certificate.p7b')) {
    $authPath = Join-Path $AuthAssets "offline-mfi\$authName"
    if (-not (Test-Path $authPath) -or (Get-Item $authPath).Length -eq 0) { throw "Missing runtime asset: $authPath" }
}
$savedEnvironment = @{}
foreach ($envName in @('JAVA_HOME', 'ANDROID_HOME', 'DIPLAY_AUTH_ASSETS_DIR', 'JAVA_OPTS')) {
    $savedEnvironment[$envName] = [Environment]::GetEnvironmentVariable($envName, 'Process')
}
Push-Location $repoRoot
try {
    $env:JAVA_HOME = $JavaHome
    $env:ANDROID_HOME = $SdkRoot
    $env:DIPLAY_AUTH_ASSETS_DIR = $AuthAssets
    $gradleArgs = @('--no-daemon', '--max-workers=4', "-Porg.gradle.java.installations.paths=$JavaHome")
    if ($Proxy) {
        $proxyUri = [Uri]$Proxy
        if ($proxyUri.Scheme -ne 'http' -or $proxyUri.UserInfo) { throw 'Use an HTTP proxy URL without credentials.' }
        $proxyOptions = "-Dhttps.proxyHost=$($proxyUri.Host) -Dhttps.proxyPort=$($proxyUri.Port) -Dhttp.proxyHost=$($proxyUri.Host) -Dhttp.proxyPort=$($proxyUri.Port)"
        $env:JAVA_OPTS = "$env:JAVA_OPTS $proxyOptions"
        $gradleArgs += $proxyOptions.Split(' ')
    }
    if ($Offline) { $gradleArgs += '--offline' }
    if ($Check) { $gradleArgs += @(':shared:testDebugUnitTest', ':common:testDebugUnitTest', ':mobile:lintDebug') }
    $gradleArgs += ':mobile:assembleStandaloneDebug'
    & .\gradlew.bat @gradleArgs
    if ($LASTEXITCODE -ne 0) { throw "Gradle failed: exit $LASTEXITCODE" }
    $apkPath = Join-Path $repoRoot 'mobile\build\outputs\apk\debug\mobile-debug.apk'
    if (-not (Test-Path $apkPath)) { throw 'APK output is missing.' }
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $apkZip = [System.IO.Compression.ZipFile]::OpenRead($apkPath)
    try {
        foreach ($authName in @('identity.pk8', 'certificate.p7b')) {
            $entry = $apkZip.GetEntry("assets/offline-mfi/$authName")
            if (-not $entry -or $entry.Length -eq 0) { throw "APK has no $authName" }
            $entryStream = $entry.Open()
            $sha = [System.Security.Cryptography.SHA256]::Create()
            try { $actualHash = [BitConverter]::ToString($sha.ComputeHash($entryStream)).Replace('-', '') }
            finally { $sha.Dispose(); $entryStream.Dispose() }
            $expectedHash = (Get-FileHash (Join-Path $AuthAssets "offline-mfi\$authName") -Algorithm SHA256).Hash
            if ($actualHash -ne $expectedHash) { throw "Packaged asset mismatch: $authName" }
        }
    } finally { $apkZip.Dispose() }
    Write-Host "APK and authentication assets verified: $apkPath"
    if ($Install) {
        $adbPath = Join-Path $SdkRoot 'platform-tools\adb.exe'
        $deviceState = & $adbPath -s $Serial get-state
        if ($LASTEXITCODE -ne 0 -or $deviceState.Trim() -ne 'device') { throw "Device $Serial is not available." }
        & $adbPath -s $Serial install -r $apkPath
        if ($LASTEXITCODE -ne 0) { throw 'Installation failed. Check device authorization and APK signing.' }
        & $adbPath -s $Serial shell am start --user 0 -n com.shihab.diplay.hudtest/com.shilapi.xcertplay.DiPlayActivity
        if ($LASTEXITCODE -ne 0) { throw 'Application launch failed.' }
    }
} finally {
    Pop-Location
    foreach ($envName in $savedEnvironment.Keys) {
        [Environment]::SetEnvironmentVariable($envName, $savedEnvironment[$envName], 'Process')
    }
}
