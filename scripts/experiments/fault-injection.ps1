# ============================================================================
# fault-injection.ps1
# ----------------------------------------------------------------------------
# Evaluation du circuit-breaker par injection de pannes reseau (reponse a R1-5).
#
# CE QUE LE SCRIPT FAIT
# 1. Mesure le decalage d'horloge entre le PC et chaque telephone
# 2. Coupe puis retablit le reseau des telephones "victimes", -Cycles fois :
#    hors ligne pendant -OfflineS secondes, en ligne pendant -OnlineS secondes
# 3. Note l'heure exacte de chaque coupure et retablissement (injections.csv)
# 4. Recupere le journal events.csv de chaque telephone
# 5. Lance analyze-faults.py (delai d'ouverture, reparations retenues, fermeture)
#
# SCENARIOS CONSEILLES (7 telephones, circuit ouvert si churn > 30 %)
# - churn massif transitoire : 3 victimes, -OfflineS 60  -> le circuit doit s'ouvrir
#   et retenir les reparations au lieu de saturer le reseau
# - depart isole : 1 victime, -OfflineS 300 -> le circuit doit rester ferme et la
#   reparation partir normalement
#
# CONTRAINTES
# - Telephones relies en USB : couper le WiFi couperait une session adb sans fil.
# - Uploader quelques fichiers avant, sinon il n'y a rien a reparer.
#
# USAGE
#   .\scripts\experiments\fault-injection.ps1 -Victims SERIAL1,SERIAL2,SERIAL3 -Cycles 5 -OfflineS 60 -OnlineS 240
#   .\scripts\experiments\fault-injection.ps1 -Victims SERIAL1 -Cycles 3 -OfflineS 300 -OnlineS 300 -CutMobileData
# ============================================================================

param(
    [Parameter(Mandatory = $true)][string[]]$Victims,
    [string[]]$Observers = @(),
    [int]$Cycles = 5,
    [int]$OfflineS = 60,
    [int]$OnlineS = 240,
    [switch]$CutMobileData,
    [string]$OutDir = "measurements\faults",
    [string]$Package = "com.mobicloud"
)

$ErrorActionPreference = "Stop"
$RemoteEvents = "/sdcard/Android/data/$Package/files/measurements/events.csv"

function Get-HostEpochMs { [long]([DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()) }

function Set-Network {
    param([string]$Serial, [bool]$Enabled)
    $state = if ($Enabled) { "enabled" } else { "disabled" }
    $svc = if ($Enabled) { "enable" } else { "disable" }
    & adb -s $Serial shell cmd wifi set-wifi-enabled $state 2>$null | Out-Null
    if ($LASTEXITCODE -ne 0) { & adb -s $Serial shell svc wifi $svc | Out-Null }
    if ($CutMobileData) { & adb -s $Serial shell svc data $svc | Out-Null }
}

# --- Telephones --------------------------------------------------------------
$connected = @(& adb devices | Select-String -Pattern "\tdevice$" | ForEach-Object { ($_ -split "\t")[0] })
foreach ($v in $Victims) { if ($connected -notcontains $v) { throw "Victime $v non connectee (adb devices)." } }
if ($Observers.Count -eq 0) { $Observers = @($connected | Where-Object { $Victims -notcontains $_ }) }
$all = @($Victims + $Observers | Select-Object -Unique)
Write-Host "Victimes : $($Victims -join ', ')"
Write-Host "Observateurs : $($Observers -join ', ')"
Write-Host "Scenario : $Cycles cycles, $OfflineS s hors ligne / $OnlineS s en ligne (duree ~ $([Math]::Round($Cycles * ($OfflineS + $OnlineS) / 60)) min)"

$runId = Get-Date -Format "yyyyMMdd-HHmmss"
$dir = Join-Path $OutDir $runId
New-Item -ItemType Directory -Force -Path $dir | Out-Null

# --- Horloges (resolution 1 s) ----------------------------------------------
$offsets = @("serial,offset_ms")
foreach ($s in $all) {
    $before = Get-HostEpochMs
    $deviceS = [long]((& adb -s $s shell date +%s).Trim())
    $after = Get-HostEpochMs
    $offsets += "$s,$($deviceS * 1000 - [long](($before + $after) / 2))"
}
$offsets | Set-Content -Encoding utf8 (Join-Path $dir "offsets.csv")

# --- Injection ---------------------------------------------------------------
$injections = @("cycle,serial,action,host_epoch_ms")
try {
    for ($c = 1; $c -le $Cycles; $c++) {
        foreach ($v in $Victims) { Set-Network $v $false; $injections += "$c,$v,cut,$(Get-HostEpochMs)" }
        Write-Host "[cycle $c] coupure ($(Get-Date -Format HH:mm:ss))"
        Start-Sleep -Seconds $OfflineS
        foreach ($v in $Victims) { Set-Network $v $true; $injections += "$c,$v,restore,$(Get-HostEpochMs)" }
        Write-Host "[cycle $c] retablissement ($(Get-Date -Format HH:mm:ss))"
        Start-Sleep -Seconds $OnlineS
    }
} finally {
    # Quoi qu'il arrive (Ctrl+C compris), le reseau des victimes est retabli.
    foreach ($v in $Victims) { Set-Network $v $true }
    $injections | Set-Content -Encoding utf8 (Join-Path $dir "injections.csv")
}

# --- Journaux ----------------------------------------------------------------
foreach ($s in $all) {
    $safe = $s -replace '[^A-Za-z0-9]', '_'
    & adb -s $s pull $RemoteEvents (Join-Path $dir "events_$safe.csv") 2>$null | Out-Null
    if ($LASTEXITCODE -ne 0) { Write-Host "Pas de journal sur $s (application pas a jour ?)" -ForegroundColor Yellow }
}
Write-Host "Resultats : $dir"
python scripts\experiments\analyze-faults.py $dir
