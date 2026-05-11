# ============================================================================
# diag-cluster-split.ps1
# ----------------------------------------------------------------------------
# Diagnostic du split cluster WiFi : capture parallele 2 telephones, parse
# les logs cluster/election, et conclut H1 (DB stale) / H2 (SSID indispo) /
# WG1 (split confirme) sans rien modifier dans le code.
#
# Hypotheses testees (cf. RAPPORT diag du 2026-05-11) :
#   H1 : NodeSettingsRepositoryImpl.getSettings() renvoie un clusterId stale
#        d'une session precedente, jamais re-verifie contre le SSID actuel.
#   H2 : WifiNetworkRepositoryImpl.getCurrentSsid() renvoie null pendant le
#        boot -> refresh ne corrige rien.
#   WG1 confirme : ProcessIncomingElectionEventUseCase rejette le COORDINATOR
#        de l'autre tel via le garde WG1, prouvant le split.
#
# USAGE
#   .\scripts\diag-cluster-split.ps1
#   .\scripts\diag-cluster-split.ps1 -CaptureSec 90
# ============================================================================

param(
    [string]$LogDir = "logs\cluster-split-diag",
    [int]$CaptureSec = 60
)

$ErrorActionPreference = "Stop"

# ---------------------------------------------------------------------------
# 0a. Resolution de adb.exe (pas suppose dans PATH)
# ---------------------------------------------------------------------------
function Resolve-Adb {
    $cmd = Get-Command adb -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }
    $candidates = @(
        "$env:ANDROID_HOME\platform-tools\adb.exe",
        "$env:ANDROID_SDK_ROOT\platform-tools\adb.exe",
        "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe",
        "$env:USERPROFILE\AppData\Local\Android\Sdk\platform-tools\adb.exe",
        "C:\Android\Sdk\platform-tools\adb.exe",
        "C:\Program Files\Android\Sdk\platform-tools\adb.exe",
        "C:\Program Files (x86)\Android\android-sdk\platform-tools\adb.exe"
    )
    foreach ($p in $candidates) {
        if ($p -and (Test-Path $p)) { return $p }
    }
    return $null
}

$adb = Resolve-Adb
if (-not $adb) {
    Write-Host "ERREUR : adb.exe introuvable. Installe Android Platform Tools" -ForegroundColor Red
    Write-Host "        ou ajoute son repertoire au PATH, puis relance." -ForegroundColor Red
    exit 1
}

Write-Host ""
Write-Host "===============================================================" -ForegroundColor Cyan
Write-Host "  MobiCloud -- Diagnostic split cluster WiFi" -ForegroundColor Cyan
Write-Host "===============================================================" -ForegroundColor Cyan
Write-Host "  adb : $adb" -ForegroundColor DarkGray
Write-Host ""

# ---------------------------------------------------------------------------
# 0. Detection des devices
# ---------------------------------------------------------------------------
$adbDevices = (& $adb devices) -split "`n" |
    Where-Object { $_ -match "^\S+\s+device$" } |
    ForEach-Object { ($_ -split "\s+")[0] }

if ($adbDevices.Count -lt 2) {
    Write-Host "ERREUR : il faut 2 devices ADB connectes ; $($adbDevices.Count) detecte(s)." -ForegroundColor Red
    exit 1
}

Write-Host "Devices detectes :" -ForegroundColor Green
$idx = 1
$indexed = @{}
foreach ($d in $adbDevices) {
    $model = (& $adb -s $d shell getprop ro.product.model 2>$null).Trim()
    Write-Host "  [$idx] $d  ($model)"
    $indexed[$idx.ToString()] = $d
    $idx++
}
Write-Host ""

$choiceA = Read-Host "Numero du telephone A (1-$($adbDevices.Count))"
$choiceB = Read-Host "Numero du telephone B (1-$($adbDevices.Count))"
$serialA = $indexed[$choiceA]
$serialB = $indexed[$choiceB]

if ($serialA -eq $serialB) {
    Write-Host "ERREUR : A et B doivent etre differents." -ForegroundColor Red
    exit 1
}

Write-Host ""
Write-Host "  A = $serialA" -ForegroundColor Yellow
Write-Host "  B = $serialB" -ForegroundColor Yellow
Write-Host ""

# ---------------------------------------------------------------------------
# 1. Preparation logs
# ---------------------------------------------------------------------------
$timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$runDir = Join-Path $LogDir $timestamp
New-Item -ItemType Directory -Force -Path $runDir | Out-Null

$logA = Join-Path $runDir "A.log"
$logB = Join-Path $runDir "B.log"
$merged = Join-Path $runDir "merged.log"

Write-Host "Logs : $runDir" -ForegroundColor Gray
Write-Host ""

# ---------------------------------------------------------------------------
# 2. Vidage buffers + verif SSID courant
# ---------------------------------------------------------------------------
Write-Host "Vidage buffers logcat..." -ForegroundColor Gray
& $adb -s $serialA logcat -c 2>$null | Out-Null
& $adb -s $serialB logcat -c 2>$null | Out-Null

Write-Host "SSID actuel (lecture OS, hors app) :" -ForegroundColor Gray
$ssidA = (& $adb -s $serialA shell dumpsys wifi 2>$null | Select-String -Pattern 'mWifiInfo SSID:\s*"?([^",]+)' | Select-Object -First 1)
$ssidB = (& $adb -s $serialB shell dumpsys wifi 2>$null | Select-String -Pattern 'mWifiInfo SSID:\s*"?([^",]+)' | Select-Object -First 1)
if ($ssidA) { Write-Host "  A : $($ssidA.Matches[0].Groups[1].Value)" -ForegroundColor Gray } else { Write-Host "  A : (non lisible)" -ForegroundColor DarkYellow }
if ($ssidB) { Write-Host "  B : $($ssidB.Matches[0].Groups[1].Value)" -ForegroundColor Gray } else { Write-Host "  B : (non lisible)" -ForegroundColor DarkYellow }
Write-Host ""

# ---------------------------------------------------------------------------
# 3. Tuer l'app si lancee (pour garantir un boot frais)
# ---------------------------------------------------------------------------
Write-Host "Force-stop MobiCloud sur les deux telephones..." -ForegroundColor Gray
& $adb -s $serialA shell am force-stop com.mobicloud 2>$null | Out-Null
& $adb -s $serialB shell am force-stop com.mobicloud 2>$null | Out-Null
Start-Sleep -Seconds 1

# ---------------------------------------------------------------------------
# 4. Lancement captures parallele via Start-Process (background)
# ---------------------------------------------------------------------------
Write-Host "Demarrage capture logcat parallele ($CaptureSec s)..." -ForegroundColor Gray

$filter = "MobicloudP2PService:I SignalingRepo:D RegisterSuperPeerUC:I ProcessIncomingElectionEventUC:* RunBullyElectionUC:* *:S"

$procA = Start-Process -FilePath $adb `
    -ArgumentList "-s",$serialA,"logcat","-v","time",$filter `
    -RedirectStandardOutput $logA `
    -NoNewWindow -PassThru
$procB = Start-Process -FilePath $adb `
    -ArgumentList "-s",$serialB,"logcat","-v","time",$filter `
    -RedirectStandardOutput $logB `
    -NoNewWindow -PassThru

Start-Sleep -Seconds 1

# ---------------------------------------------------------------------------
# 5. Countdown demarrage simultane app
# ---------------------------------------------------------------------------
Write-Host ""
Write-Host "===============================================================" -ForegroundColor Cyan
Write-Host "  Prepare-toi a lancer MobiCloud sur LES DEUX telephones" -ForegroundColor Cyan
Write-Host "  EN MEME TEMPS au compte de 0." -ForegroundColor Cyan
Write-Host "===============================================================" -ForegroundColor Cyan
for ($i = 5; $i -ge 1; $i--) {
    Write-Host "  T-$i..." -ForegroundColor Yellow
    Start-Sleep -Seconds 1
}
Write-Host "  >>> LANCE L'APP MAINTENANT SUR A ET B <<<" -ForegroundColor Green -BackgroundColor Black
Write-Host ""

# ---------------------------------------------------------------------------
# 6. Attente capture
# ---------------------------------------------------------------------------
Write-Host "Capture en cours pendant $CaptureSec s..." -ForegroundColor Gray
$remaining = $CaptureSec
while ($remaining -gt 0) {
    Write-Host "`r  $remaining s restantes..." -NoNewline -ForegroundColor Gray
    Start-Sleep -Seconds 5
    $remaining -= 5
}
Write-Host ""

# ---------------------------------------------------------------------------
# 7. Stop captures
# ---------------------------------------------------------------------------
Write-Host "Arret captures..." -ForegroundColor Gray
Stop-Process -Id $procA.Id -Force -ErrorAction SilentlyContinue
Stop-Process -Id $procB.Id -Force -ErrorAction SilentlyContinue
Start-Sleep -Seconds 1

# ---------------------------------------------------------------------------
# 8. Merge taggue [A] / [B] avec timestamp pour correlation
# ---------------------------------------------------------------------------
$linesA = if (Test-Path $logA) { Get-Content $logA | ForEach-Object { "[A] $_" } } else { @() }
$linesB = if (Test-Path $logB) { Get-Content $logB | ForEach-Object { "[B] $_" } } else { @() }
$all = @($linesA) + @($linesB) | Sort-Object {
    if ($_ -match '\[\w\] (\d{2}-\d{2} \d{2}:\d{2}:\d{2}\.\d{3})') { $matches[1] } else { "" }
}
$all | Set-Content $merged
Write-Host "Merged log : $merged" -ForegroundColor Gray
Write-Host ""

# ---------------------------------------------------------------------------
# 9. Analyse et verdict
# ---------------------------------------------------------------------------
Write-Host "===============================================================" -ForegroundColor Cyan
Write-Host "  Analyse" -ForegroundColor Cyan
Write-Host "===============================================================" -ForegroundColor Cyan
Write-Host ""

function Extract-ClusterId {
    param([string]$file, [string]$pattern)
    if (-not (Test-Path $file)) { return $null }
    $line = Get-Content $file | Select-String -Pattern $pattern | Select-Object -First 1
    if ($line) { return $line.Line } else { return $null }
}

foreach ($pair in @(@{Tag='A'; File=$logA}, @{Tag='B'; File=$logB})) {
    $tag = $pair.Tag
    $file = $pair.File
    Write-Host "----- Telephone $tag -----" -ForegroundColor Yellow

    $refreshInit = Extract-ClusterId $file '\[CLUSTER\] refresh initial: clusterId='
    $refreshAfter = Extract-ClusterId $file '\[CLUSTER\] refresh apres WiFi available: clusterId='
    $registerPeer = Get-Content $file -ErrorAction SilentlyContinue | Select-String -Pattern 'REGISTER_PEER envoye.*clusterId=' | Select-Object -First 3
    $electionWon = Extract-ClusterId $file 'Election remportee'
    $coordRejected = Get-Content $file -ErrorAction SilentlyContinue | Select-String -Pattern 'COORDINATOR from cluster.*rejected'

    if ($refreshInit) {
        Write-Host "  [refresh initial] $($refreshInit.Substring([Math]::Max(0, $refreshInit.IndexOf('[CLUSTER]'))))" -ForegroundColor Gray
    } else {
        Write-Host "  [refresh initial] MANQUANT (service P2P pas demarre ou tag filtre)" -ForegroundColor Red
    }
    if ($refreshAfter) {
        Write-Host "  [refresh apres WiFi] $($refreshAfter.Substring([Math]::Max(0, $refreshAfter.IndexOf('[CLUSTER]'))))" -ForegroundColor Gray
    } else {
        Write-Host "  [refresh apres WiFi] non emis (callback WiFi pas declenche pendant capture)" -ForegroundColor DarkGray
    }
    if ($registerPeer) {
        $registerPeer | ForEach-Object { Write-Host "  [REGISTER_PEER] $($_.Line.Substring([Math]::Max(0, $_.Line.IndexOf('REGISTER_PEER'))))" -ForegroundColor Gray }
    }
    if ($electionWon) {
        Write-Host "  [Bully fire] $($electionWon.Substring([Math]::Max(0, $electionWon.IndexOf('Election remportee'))))" -ForegroundColor Green
    }
    if ($coordRejected) {
        $coordRejected | ForEach-Object { Write-Host "  [WG1 rejet] $($_.Line.Substring([Math]::Max(0, $_.Line.IndexOf('COORDINATOR'))))" -ForegroundColor Magenta }
    }
    Write-Host ""
}

# ---------------------------------------------------------------------------
# 10. Verdict synthetique
# ---------------------------------------------------------------------------
Write-Host "===============================================================" -ForegroundColor Cyan
Write-Host "  Verdict" -ForegroundColor Cyan
Write-Host "===============================================================" -ForegroundColor Cyan

function Get-ClusterIdValue {
    param([string]$file, [string]$pattern)
    if (-not (Test-Path $file)) { return $null }
    $line = Get-Content $file | Select-String -Pattern "$pattern([0-9a-f]{8}|\(vide)" | Select-Object -First 1
    if ($line -and $line.Matches.Count -gt 0) {
        return $line.Matches[0].Groups[1].Value
    }
    return $null
}

$initA = Get-ClusterIdValue $logA '\[CLUSTER\] refresh initial: clusterId='
$afterA = Get-ClusterIdValue $logA '\[CLUSTER\] refresh apres WiFi available: clusterId='
$initB = Get-ClusterIdValue $logB '\[CLUSTER\] refresh initial: clusterId='
$afterB = Get-ClusterIdValue $logB '\[CLUSTER\] refresh apres WiFi available: clusterId='

$bullyA = (Get-Content $logA -ErrorAction SilentlyContinue | Select-String -Pattern 'Election remportee').Count -gt 0
$bullyB = (Get-Content $logB -ErrorAction SilentlyContinue | Select-String -Pattern 'Election remportee').Count -gt 0
$rejectA = (Get-Content $logA -ErrorAction SilentlyContinue | Select-String -Pattern 'COORDINATOR from cluster.*rejected').Count -gt 0
$rejectB = (Get-Content $logB -ErrorAction SilentlyContinue | Select-String -Pattern 'COORDINATOR from cluster.*rejected').Count -gt 0

Write-Host ""
# H2 : SSID indispo au boot
$h2A = ($initA -eq "(vide")
$h2B = ($initB -eq "(vide")
if ($h2A -or $h2B) {
    Write-Host "[H2 CONFIRMEE] SSID indisponible au refresh initial sur :" -ForegroundColor Red
    if ($h2A) { Write-Host "  - A (initial = vide)" -ForegroundColor Red }
    if ($h2B) { Write-Host "  - B (initial = vide)" -ForegroundColor Red }
    Write-Host "  -> WifiNetworkRepositoryImpl.getCurrentSsid() retourne null au boot." -ForegroundColor Gray
} else {
    Write-Host "[H2 ECARTEE] SSID lu correctement des le refresh initial." -ForegroundColor Green
}

# H1 : DB stale (initial != after)
$h1A = ($initA -and $afterA -and $initA -ne $afterA -and $initA -ne "(vide")
$h1B = ($initB -and $afterB -and $initB -ne $afterB -and $initB -ne "(vide")
if ($h1A -or $h1B) {
    Write-Host ""
    Write-Host "[H1 CONFIRMEE] clusterId stale en DB :" -ForegroundColor Red
    if ($h1A) { Write-Host "  - A : initial=$initA, after-WiFi=$afterA" -ForegroundColor Red }
    if ($h1B) { Write-Host "  - B : initial=$initB, after-WiFi=$afterB" -ForegroundColor Red }
    Write-Host "  -> NodeSettingsRepositoryImpl.getSettings() ne revalide pas le clusterId" -ForegroundColor Gray
    Write-Host "     contre le SSID courant." -ForegroundColor Gray
} else {
    Write-Host ""
    Write-Host "[H1 NON OBSERVEE] pas de divergence initial vs after-WiFi sur cette session." -ForegroundColor Green
}

# Split confirme
if ($bullyA -and $bullyB) {
    Write-Host ""
    Write-Host "[RACE BULLY] Les DEUX telephones ont remporte une election." -ForegroundColor Red
}
if ($rejectA -or $rejectB) {
    Write-Host ""
    Write-Host "[WG1 ACTIVE] Garde anti-split a vire COORDINATOR cross-cluster :" -ForegroundColor Red
    if ($rejectA) { Write-Host "  - A a rejete le COORDINATOR de B" -ForegroundColor Red }
    if ($rejectB) { Write-Host "  - B a rejete le COORDINATOR de A" -ForegroundColor Red }
    Write-Host "  -> Split cluster IRL confirme." -ForegroundColor Gray
}

# clusterIds finaux symetriques ?
if ($initA -and $initB) {
    if ($initA -eq $initB -and $initA -ne "(vide") {
        Write-Host ""
        Write-Host "[OK] Les deux clusterIds initiaux sont IDENTIQUES ($initA) -- pas de split au boot." -ForegroundColor Green
    } elseif ($initA -ne "(vide" -and $initB -ne "(vide" -and $initA -ne $initB) {
        Write-Host ""
        Write-Host "[!] clusterIds initiaux DIFFERENTS : A=$initA  B=$initB" -ForegroundColor Red
        Write-Host "    Cause directe du split. Verifie historique WiFi de chaque telephone." -ForegroundColor Gray
    }
}

Write-Host ""
Write-Host "===============================================================" -ForegroundColor Cyan
Write-Host "  Merged log complet : $merged" -ForegroundColor Cyan
Write-Host "===============================================================" -ForegroundColor Cyan
Write-Host ""
