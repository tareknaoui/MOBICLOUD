# ============================================================================
# test-migration.ps1
# ----------------------------------------------------------------------------
# Test automatise du mecanisme de migration proactive (M06) de MobiCloud.
#
# CE QUE LE SCRIPT FAIT
# ---------------------
# 1. Detecte les devices ADB connectes (>= 3 requis : 1 super-peer + 2 pairs)
# 2. Demarre une capture de logs en parallele sur les 3 devices
# 3. Te demande de faire l'upload manuellement depuis le device de ton choix
# 4. Une fois pret, desactive le WiFi sur le device "partant" (declenche
#    le NetworkChangeObserver.onLost qui declenche le DEPARTURE_NOTICE)
# 5. Surveille les logs pendant 30s pour detecter la sequence de migration
# 6. Reactive le WiFi
# 7. Analyse les logs et te dit ce qui s'est passe
#
# CE QUE LE SCRIPT NE FAIT PAS
# ----------------------------
# - L'upload du fichier (tu cliques toi-meme dans l'UI -- impossible
#   d'automatiser de facon portable entre devices)
# - L'identification du super-peer (le script t'aide en montrant les nodeIds,
#   mais c'est toi qui sais qui est qui)
#
# USAGE
# -----
# .\scripts\test-migration.ps1
# .\scripts\test-migration.ps1 -LogDir custom\path
# ============================================================================

param(
    [string]$Package = "com.mobicloud",
    [string]$LogDir = "logs\migration-test",
    [int]$ObservationWindowSec = 30
)

$ErrorActionPreference = "Stop"

# ---------------------------------------------------------------------------
# 0. Verifications prealables
# ---------------------------------------------------------------------------
Write-Host ""
Write-Host "===============================================================" -ForegroundColor Cyan
Write-Host "  MobiCloud -- Test de migration proactive (M06)" -ForegroundColor Cyan
Write-Host "===============================================================" -ForegroundColor Cyan
Write-Host ""

$adbDevices = (adb devices) -split "`n" |
    Where-Object { $_ -match "^\S+\s+device$" } |
    ForEach-Object { ($_ -split "\s+")[0] }

if ($adbDevices.Count -lt 3) {
    Write-Host "ERREUR : il faut au moins 3 devices ADB connectes ; $($adbDevices.Count) detecte(s)." -ForegroundColor Red
    Write-Host "Branche tes 3 telephones, active le debogage USB, accepte 'Toujours autoriser', puis relance." -ForegroundColor Yellow
    exit 1
}

Write-Host "Devices detectes ($($adbDevices.Count)) :" -ForegroundColor Green
$idx = 1
$deviceTypes = @{}   # serial -> "emulator" ou "physical"
foreach ($d in $adbDevices) {
    $model = (adb -s $d shell getprop ro.product.model 2>$null).Trim()
    $type = if ($d -match "^emulator-") { "EMULATOR" } else { "PHYSICAL" }
    $deviceTypes[$d] = $type
    $colorTag = if ($type -eq "EMULATOR") { "[EMU]" } else { "[PHY]" }
    Write-Host "  [$idx] $colorTag $d  ($model)"
    $idx++
}
Write-Host ""

# ---------------------------------------------------------------------------
# 1. Preparation des dossiers de log
# ---------------------------------------------------------------------------
$timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$runDir = Join-Path $LogDir $timestamp
New-Item -ItemType Directory -Force -Path $runDir | Out-Null
Write-Host "Logs sauvegardes dans : $runDir" -ForegroundColor Gray
Write-Host ""

# ---------------------------------------------------------------------------
# 2. Vider les logs et identifier le nodeId local de chaque device
# ---------------------------------------------------------------------------
Write-Host "Vidage des buffers logcat sur tous les devices..." -ForegroundColor Gray
foreach ($d in $adbDevices) {
    adb -s $d logcat -c 2>$null | Out-Null
}

Write-Host ""
Write-Host "===============================================================" -ForegroundColor Cyan
Write-Host "  ETAPE 1 : Identification des nodeIds" -ForegroundColor Cyan
Write-Host "===============================================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "Lance MobiCloud sur les 3 devices, puis attends ~10s que les" -ForegroundColor Yellow
Write-Host "boucles SCORE-LOOP loggent les nodeIds locaux." -ForegroundColor Yellow
Write-Host ""
Read-Host "Appuie sur Entree quand l'app tourne sur les 3 devices"

Start-Sleep -Seconds 5

$deviceNodeIds = @{}
foreach ($d in $adbDevices) {
    $line = adb -s $d logcat -d -s MobicloudP2PService:I 2>$null |
        Select-String -Pattern "SCORE-LOOP.*pour ([0-9a-f]{8})" |
        Select-Object -First 1
    if ($line -and $line.Matches.Count -gt 0) {
        $nodeId = $line.Matches[0].Groups[1].Value
        $deviceNodeIds[$d] = $nodeId
        Write-Host "  $d  -> nodeId $nodeId" -ForegroundColor Green
    } else {
        Write-Host "  $d  -> nodeId INCONNU (pas de log SCORE-LOOP encore)" -ForegroundColor Yellow
        $deviceNodeIds[$d] = "??"
    }
}

# ---------------------------------------------------------------------------
# 3. Identification du super-peer
# ---------------------------------------------------------------------------
Write-Host ""
Write-Host "===============================================================" -ForegroundColor Cyan
Write-Host "  ETAPE 2 : Selection des roles" -ForegroundColor Cyan
Write-Host "===============================================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "Sur quel device fais-tu l'upload ? (= source du fichier)" -ForegroundColor Yellow
Write-Host "Idealement le SUPER-PEER -- les fragments iront sur les 2 autres," -ForegroundColor Gray
Write-Host "donc tu pourras tester la migration depuis B ou C." -ForegroundColor Gray
Write-Host ""
$idx = 1
$indexedDevices = @{}
foreach ($d in $adbDevices) {
    $indexedDevices[$idx.ToString()] = $d
    Write-Host "  [$idx] $d  (nodeId $($deviceNodeIds[$d]))"
    $idx++
}
$uploadChoice = Read-Host "Numero du device source (1-$($adbDevices.Count))"
$uploaderDevice = $indexedDevices[$uploadChoice]

Write-Host ""
Write-Host "Sur quel device veux-tu simuler le DEPART (= leaver) ?" -ForegroundColor Yellow
Write-Host "Choisis un device DIFFERENT du source." -ForegroundColor Gray
$leaverChoice = Read-Host "Numero du device leaver (1-$($adbDevices.Count))"
$leaverDevice = $indexedDevices[$leaverChoice]

if ($leaverDevice -eq $uploaderDevice) {
    Write-Host "ERREUR : le leaver doit etre different du source." -ForegroundColor Red
    exit 1
}

Write-Host ""
Write-Host "Source upload : $uploaderDevice (nodeId $($deviceNodeIds[$uploaderDevice]))" -ForegroundColor Green
Write-Host "Leaver        : $leaverDevice (nodeId $($deviceNodeIds[$leaverDevice]))" -ForegroundColor Green

# ---------------------------------------------------------------------------
# 4. Demarrage de la capture en arriere-plan
# ---------------------------------------------------------------------------
Write-Host ""
Write-Host "===============================================================" -ForegroundColor Cyan
Write-Host "  ETAPE 3 : Capture des logs (en arriere-plan)" -ForegroundColor Cyan
Write-Host "===============================================================" -ForegroundColor Cyan
Write-Host ""

$logTags = "MobiCloud:Distribute:* MobicloudP2PService:* SignalingRepo:*"
$jobs = @()

foreach ($d in $adbDevices) {
    $logFile = Join-Path $runDir "$d.log"
    Write-Host "  Capture demarree pour $d -> $logFile" -ForegroundColor Gray
    adb -s $d logcat -c | Out-Null
    $job = Start-Job -ScriptBlock {
        param($dev, $tags, $file)
        # On utilise -v threadtime pour avoir des timestamps precis
        & adb -s $dev logcat -v threadtime $tags.Split(' ') 2>&1 | Out-File -FilePath $file -Encoding utf8
    } -ArgumentList $d, $logTags, $logFile
    $jobs += @{ Job = $job; Device = $d; LogFile = $logFile }
}

# ---------------------------------------------------------------------------
# 5. Pause pour que l'utilisateur fasse l'upload
# ---------------------------------------------------------------------------
Write-Host ""
Write-Host "===============================================================" -ForegroundColor Cyan
Write-Host "  ETAPE 4 : UPLOAD MANUEL" -ForegroundColor Cyan
Write-Host "===============================================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "Sur le device $uploaderDevice :" -ForegroundColor Yellow
Write-Host "  1. Ouvre MobiCloud" -ForegroundColor White
Write-Host "  2. Va dans l'explorateur" -ForegroundColor White
Write-Host "  3. Choisis un fichier (PDF, image, ~Ko a quelques Mo)" -ForegroundColor White
Write-Host "  4. Lance l'upload" -ForegroundColor White
Write-Host "  5. Attends que les 3 fragments soient verts (succes)" -ForegroundColor White
Write-Host ""
Read-Host "Appuie sur Entree QUAND l'upload est complet"

# ---------------------------------------------------------------------------
# 6. Declenchement de la migration : couper le WiFi sur le leaver
# ---------------------------------------------------------------------------
Write-Host ""
Write-Host "===============================================================" -ForegroundColor Cyan
Write-Host "  ETAPE 5 : DECLENCHEMENT DE LA MIGRATION" -ForegroundColor Cyan
Write-Host "===============================================================" -ForegroundColor Cyan
Write-Host ""
$leaverType = $deviceTypes[$leaverDevice]
Write-Host "Coupure reseau sur $leaverDevice ($leaverType) -- declenche onLost..." -ForegroundColor Yellow

$tDepart = Get-Date

# Methode universelle (phones ET emulateurs) : activer le mode avion via settings + broadcast.
# `svc wifi disable` ne suffit pas sur emulateurs car le WiFi virtuel reste "fake-up".
# Le mode avion coupe TOUTES les transports reseau et declenche systematiquement
# ConnectivityManager.onLost dans l'app.
adb -s $leaverDevice shell "settings put global airplane_mode_on 1" 2>$null | Out-Null
adb -s $leaverDevice shell "cmd connectivity airplane-mode enable" 2>$null | Out-Null
# Fallback pour les Android plus anciens (avant API 30) : broadcast manuel
adb -s $leaverDevice shell "am broadcast -a android.intent.action.AIRPLANE_MODE --ez state true" 2>$null | Out-Null
# Et en bretelle, on coupe wifi/data explicitement aussi
adb -s $leaverDevice shell "svc wifi disable" 2>$null | Out-Null
adb -s $leaverDevice shell "svc data disable" 2>$null | Out-Null

Write-Host "Reseau coupe a $($tDepart.ToString('HH:mm:ss.fff'))" -ForegroundColor Green

Write-Host ""
Write-Host "Surveillance des logs pendant $ObservationWindowSec secondes..." -ForegroundColor Gray
Write-Host "(le DEPARTURE_NOTICE doit partir dans la 1ere seconde et la migration tient en 5s)" -ForegroundColor Gray
Write-Host ""

# Affichage live d'un compte a rebours
for ($i = $ObservationWindowSec; $i -gt 0; $i--) {
    Write-Host "  $i s..." -NoNewline
    Start-Sleep -Seconds 1
    Write-Host "`r" -NoNewline
}
Write-Host "  Fin de la fenetre d'observation.            " -ForegroundColor Gray
Write-Host ""

# ---------------------------------------------------------------------------
# 7. Reactivation du reseau pour que le leaver puisse re-rejoindre
# ---------------------------------------------------------------------------
Write-Host "Reactivation du reseau sur $leaverDevice..." -ForegroundColor Gray
adb -s $leaverDevice shell "settings put global airplane_mode_on 0" 2>$null | Out-Null
adb -s $leaverDevice shell "cmd connectivity airplane-mode disable" 2>$null | Out-Null
adb -s $leaverDevice shell "am broadcast -a android.intent.action.AIRPLANE_MODE --ez state false" 2>$null | Out-Null
adb -s $leaverDevice shell "svc wifi enable" 2>$null | Out-Null
adb -s $leaverDevice shell "svc data enable" 2>$null | Out-Null

# ---------------------------------------------------------------------------
# 8. Stop des jobs de capture
# ---------------------------------------------------------------------------
Start-Sleep -Seconds 2  # laisser les derniers logs s'ecrire
Write-Host "Arret des captures de log..." -ForegroundColor Gray
foreach ($entry in $jobs) {
    Stop-Job -Job $entry.Job -ErrorAction SilentlyContinue
    Remove-Job -Job $entry.Job -Force -ErrorAction SilentlyContinue
}

# ---------------------------------------------------------------------------
# 9. Analyse des logs
# ---------------------------------------------------------------------------
Write-Host ""
Write-Host "===============================================================" -ForegroundColor Cyan
Write-Host "  ETAPE 6 : ANALYSE DES LOGS" -ForegroundColor Cyan
Write-Host "===============================================================" -ForegroundColor Cyan
Write-Host ""

$results = @()

foreach ($entry in $jobs) {
    $d = $entry.Device
    $file = $entry.LogFile
    $isLeaver = $d -eq $leaverDevice
    $role = if ($isLeaver) { "LEAVER" } else { "OBSERVER/SUPER-PEER" }

    if (-not (Test-Path $file)) {
        Write-Host "[$d] ($role) : aucun log capture (fichier absent)" -ForegroundColor Red
        continue
    }
    $logContent = Get-Content $file -ErrorAction SilentlyContinue

    Write-Host "[$d] ($role) -- nodeId $($deviceNodeIds[$d])" -ForegroundColor Cyan

    # Patterns clefs pour M06
    $sentNotice    = $logContent | Select-String -Pattern "DEPART.*DEPARTURE_NOTICE envoye"
    $planSent      = $logContent | Select-String -Pattern "MIGRATION.*Plan envoye a"
    $planReceived  = $logContent | Select-String -Pattern "MIGRATION.*Plan recu"
    $blocMigrated  = $logContent | Select-String -Pattern "MIGRATION.*Bloc.*migre vers"
    $migrationFail = $logContent | Select-String -Pattern "MIGRATION.*echec|MIGRATION.*Timeout|MIGRATION.*ignore|MIGRATION.*invalide|MIGRATION.*annule"
    $windowExpire  = $logContent | Select-String -Pattern "Fenetre de migration expiree"

    # Synthese ligne par ligne
    if ($sentNotice)    { Write-Host "  [OK] DEPARTURE_NOTICE envoye        ($($sentNotice.Count)x)" -ForegroundColor Green }
    if ($planSent)      { Write-Host "  [OK] Plan de migration envoye        ($($planSent.Count)x)" -ForegroundColor Green }
    if ($planReceived)  { Write-Host "  [OK] Plan de migration recu          ($($planReceived.Count)x)" -ForegroundColor Green }
    if ($blocMigrated)  { Write-Host "  [OK] Bloc(s) migre(s)                ($($blocMigrated.Count)x)" -ForegroundColor Green }
    if ($windowExpire)  { Write-Host "  [INFO] Fenetre 5s expiree            ($($windowExpire.Count)x)" -ForegroundColor Gray }
    if ($migrationFail) {
        Write-Host "  [ERREUR] Echecs migration detectes :" -ForegroundColor Red
        $migrationFail | ForEach-Object { Write-Host "        $($_.Line)" -ForegroundColor Red }
    }

    if (-not ($sentNotice -or $planSent -or $planReceived -or $blocMigrated -or $migrationFail)) {
        Write-Host "  Aucun evenement de migration detecte sur ce device." -ForegroundColor Yellow
    }

    Write-Host ""

    $results += [PSCustomObject]@{
        Device         = $d
        Role           = $role
        SentNotice     = if ($sentNotice) { $sentNotice.Count } else { 0 }
        PlanSent       = if ($planSent) { $planSent.Count } else { 0 }
        PlanReceived   = if ($planReceived) { $planReceived.Count } else { 0 }
        BlocsMigrated  = if ($blocMigrated) { $blocMigrated.Count } else { 0 }
        Failures       = if ($migrationFail) { $migrationFail.Count } else { 0 }
    }
}

# ---------------------------------------------------------------------------
# 10. Verdict
# ---------------------------------------------------------------------------
Write-Host "===============================================================" -ForegroundColor Cyan
Write-Host "  VERDICT GLOBAL" -ForegroundColor Cyan
Write-Host "===============================================================" -ForegroundColor Cyan

$leaverResult = $results | Where-Object { $_.Role -eq "LEAVER" }
$peerResults  = $results | Where-Object { $_.Role -ne "LEAVER" }
$totalPlanSent = ($peerResults | Measure-Object -Property PlanSent -Sum).Sum
$totalBlocsMigrated = ($leaverResult | Measure-Object -Property BlocsMigrated -Sum).Sum
$totalFailures = ($results | Measure-Object -Property Failures -Sum).Sum

if ($leaverResult.SentNotice -gt 0 -and $totalPlanSent -gt 0 -and $totalBlocsMigrated -gt 0 -and $totalFailures -eq 0) {
    Write-Host ""
    Write-Host "  MIGRATION REUSSIE -- TOUS LES SIGNAUX VERTS" -ForegroundColor Green
    Write-Host "  - DEPARTURE_NOTICE envoye par le leaver" -ForegroundColor Green
    Write-Host "  - Plan de migration emis par le super-peer" -ForegroundColor Green
    Write-Host "  - $totalBlocsMigrated bloc(s) migre(s) vers d'autres pairs" -ForegroundColor Green
} elseif ($leaverResult.SentNotice -eq 0) {
    Write-Host ""
    Write-Host "  ECHEC -- Le leaver n'a pas envoye de DEPARTURE_NOTICE." -ForegroundColor Red
    Write-Host "  Causes possibles :" -ForegroundColor Yellow
    Write-Host "    - NetworkChangeObserver pas branche (verifier MobicloudP2PService)" -ForegroundColor Yellow
    Write-Host "    - Le leaver n'avait aucun bloc heberge (rien a migrer = pas envoye)" -ForegroundColor Yellow
    Write-Host "    - svc wifi disable n'a pas declenche onLost (selon version Android)" -ForegroundColor Yellow
} elseif ($totalPlanSent -eq 0) {
    Write-Host ""
    Write-Host "  ECHEC -- Le super-peer n'a pas envoye de plan." -ForegroundColor Red
    Write-Host "  Causes possibles :" -ForegroundColor Yellow
    Write-Host "    - Le notice n'est pas arrive au super-peer (TCP rate)" -ForegroundColor Yellow
    Write-Host "    - Pas de candidats destination disponibles" -ForegroundColor Yellow
    Write-Host "    - Signature du notice rejetee (pubkey mal propagee)" -ForegroundColor Yellow
} elseif ($totalBlocsMigrated -eq 0) {
    Write-Host ""
    Write-Host "  ECHEC PARTIEL -- Plan emis mais aucun bloc migre." -ForegroundColor Yellow
    Write-Host "  Causes possibles :" -ForegroundColor Yellow
    Write-Host "    - Le leaver a ferme son TCP server avant de pouvoir transferer" -ForegroundColor Yellow
    Write-Host "    - Les destinataires n'etaient plus joignables" -ForegroundColor Yellow
    Write-Host "    - Verifier les 'Failures' ci-dessus" -ForegroundColor Yellow
} else {
    Write-Host ""
    Write-Host "  ECHECS DETECTES (voir details ci-dessus)" -ForegroundColor Red
}

Write-Host ""
Write-Host "Logs complets dans : $runDir" -ForegroundColor Gray
Write-Host ""
