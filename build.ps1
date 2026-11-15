<#
.SYNOPSIS
    Baut den Speedreader ueber apk-builder: native Bruecke (Dateiauswahl,
    Zwischenablage) und Empfang von Text per "Teilen" aus anderen Apps.
.DESCRIPTION
    Aufbau wie gym-coach\build.ps1. apps\Speedreader unter apk-builder wird
    bei jedem Lauf per -Force neu aus dem Template erzeugt, die Patches laufen
    deshalb jedes Mal erneut. Throw-Guards: Aendert sich das Template, bricht
    der Build laut ab, statt still eine APK ohne Bruecke zu bauen.

    - AndroidManifest: zweiter intent-filter (ACTION_SEND, text/plain), damit
      die App im Teilen-Menue anderer Apps erscheint.
    - android-src\FileBridge.java wird mit dem echten Paketnamen kopiert.
    - MainActivity: Bruecke "SrAndroid", onActivityResult, onNewIntent.

    Mit -Release landet die signierte APK zusaetzlich als
    dist\speedreader-<Version>.apk (aeltere dist-APKs werden entfernt).
.EXAMPLE
    .\build.ps1 -VersionName 1.1 -VersionCode 2 -Release
#>
[CmdletBinding()]
param(
    [string]$VersionName = '1.0',
    [int]$VersionCode = 1,
    [switch]$Release,
    [switch]$Install
)

$ErrorActionPreference = 'Stop'

# javac lehnt eine UTF-8-BOM ab; PS 5.1 schreibt mit -Encoding utf8 immer eine.
$Utf8NoBom = New-Object System.Text.UTF8Encoding($false)
function Write-TextNoBom {
    param([string]$Path, [string]$Content)
    [System.IO.File]::WriteAllText($Path, $Content, $Utf8NoBom)
}
function Replace-Once {
    param([string]$Text, [string]$Needle, [string]$Replacement, [string]$What)
    $idx = $Text.IndexOf($Needle)
    if ($idx -lt 0) {
        throw "Patchziel '$What' nicht gefunden - hat sich das apk-builder-Template geaendert?"
    }
    return $Text.Substring(0, $idx) + $Replacement + $Text.Substring($idx + $Needle.Length)
}

$ProjectRoot = $PSScriptRoot
$ApkBuilder  = "D:\claude code projects\apk-builder"
$PackageId   = "com.daniel.speedreader"
$AppName     = "Speedreader"
$appDir      = Join-Path $ApkBuilder "apps\$AppName"

& "$ApkBuilder\new-app.ps1" -Name $AppName -PackageId $PackageId `
    -WebRoot (Join-Path $ProjectRoot 'web') `
    -Icon (Join-Path $ProjectRoot 'icon.xml') `
    -IconBackground "#12171E" -Portrait -KeepScreenOn `
    -VersionName $VersionName -VersionCode $VersionCode -Force

$mainDir = Join-Path $appDir 'app\src\main'
$javaDir = Join-Path $mainDir ("java\" + $PackageId.Replace('.', '\'))

# --- AndroidManifest.xml: Text per "Teilen" empfangen ------------------------
$manifestPath = Join-Path $mainDir 'AndroidManifest.xml'
$manifest = [System.IO.File]::ReadAllText($manifestPath)
$manifest = Replace-Once $manifest '</intent-filter>' @'
</intent-filter>
            <!-- Markierten Text aus anderen Apps per "Teilen" annehmen. -->
            <intent-filter>
                <action android:name="android.intent.action.SEND" />
                <category android:name="android.intent.category.DEFAULT" />
                <data android:mimeType="text/plain" />
            </intent-filter>
'@.TrimEnd() 'intent-filter'
Write-TextNoBom -Path $manifestPath -Content $manifest

# --- FileBridge.java -------------------------------------------------------
$bridge = [System.IO.File]::ReadAllText((Join-Path $ProjectRoot 'android-src\FileBridge.java'))
$bridge = Replace-Once $bridge 'package __PACKAGE__;' "package $PackageId;" 'package-Platzhalter'
Write-TextNoBom -Path (Join-Path $javaDir 'FileBridge.java') -Content $bridge

# --- MainActivity.java -----------------------------------------------------
$mainActivityPath = Join-Path $javaDir 'MainActivity.java'
$java = [System.IO.File]::ReadAllText($mainActivityPath)

$java = Replace-Once $java 'webView.setWebViewClient(new LocalWebViewClient());' @'
webView.setWebViewClient(new LocalWebViewClient());

        // Native Bruecke: siehe speed-reader\android-src\FileBridge.java.
        fileBridge = new FileBridge(this, webView);
        webView.addJavascriptInterface(fileBridge, "SrAndroid");
        fileBridge.takeIntent(getIntent());
'@.TrimEnd() 'setWebViewClient'

$java = Replace-Once $java '    /** Haelt lokale Seiten im WebView' @'
    // ---- Native Bruecke (eingefuegt von speed-reader\build.ps1) --------
    private FileBridge fileBridge;

    @SuppressWarnings("deprecation")
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (fileBridge != null) fileBridge.onResult(requestCode, resultCode, data);
    }

    // launchMode singleTop: Laeuft die App schon, kommt "Teilen" hier an.
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (fileBridge != null) fileBridge.takeIntent(intent);
    }

    /** Haelt lokale Seiten im WebView
'@ 'LocalWebViewClient-Kommentar'

Write-TextNoBom -Path $mainActivityPath -Content $java
Write-Host "  [ok] Bruecke und Teilen-Empfang eingebaut" -ForegroundColor Green

$buildArgs = @{ App = $AppName }
if ($Release) { $buildArgs.Release = $true }
if ($Install) { $buildArgs.Install = $true }
& "$ApkBuilder\build-apk.ps1" @buildArgs

if ($Release) {
    $latest = Get-ChildItem (Join-Path $ApkBuilder 'out') -Filter "$AppName-release-*.apk" |
        Sort-Object LastWriteTime -Descending | Select-Object -First 1
    if (-not $latest) { throw "Keine Release-APK in apk-builder\out gefunden" }
    $dist = Join-Path $ProjectRoot 'dist'
    if (-not (Test-Path $dist)) { New-Item -ItemType Directory -Path $dist | Out-Null }
    Get-ChildItem $dist -Filter 'speedreader-*.apk' | Remove-Item -Force
    $target = Join-Path $dist "speedreader-$VersionName.apk"
    Copy-Item $latest.FullName $target -Force
    Write-Host "  [ok] $target" -ForegroundColor Green
}
