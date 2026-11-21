# ============================================================================
# run-experiment.ps1
# ----------------------------------------------------------------------------
# Lance une campagne de mesures MobiCloud sur un telephone et recupere le CSV
# (reponse aux reviewers R2-8 energie et R2-11 mesures physiques).
#
# CE QUE LE SCRIPT FAIT
# 1. Verifie le telephone (adb) et refuse de mesurer l'energie s'il charge
# 2. Envoie la commande au recepteur ExperimentCommandReceiver (adb seulement)
# 3. Affiche la progression (logcat MobiCloud:EXP) jusqu'a la fin
# 4. Recupere measurements.csv dans le dossier -OutDir
#
# AVANT DE LANCER
# - Le cluster doit etre forme (le telephone est membre ou super-peer).
# - Pour l'energie : debrancher le cable et passer par le debogage sans fil
#   (Parametres > Options developpeur > Debogage sans fil, puis "adb connect").
#   Branche en USB, le courant mesure est celui de la charge.
# - Ecran allume a luminosite fixe, autres applications fermees.
#
# USAGE
#   .\scripts\experiments\run-experiment.ps1 -Label wifi_10mb -SizeMb 10 -Reps 30
#   .\scripts\experiments\run-experiment.ps1 -Serial 192.168.1.20:5555 -Label lte_1mb -SizeKb 1024 -Reps 30
#   .\scripts\experiments\run-experiment.ps1 -Label upload_only -SizeMb 5 -Reps 30 -UploadOnly
#   .\scripts\experiments\run-experiment.ps1 -Stop
# ============================================================================

param(
    [string]$Serial = "",
    [string]$Label = "",
    [int]$SizeMb = 0,
    [int]$SizeKb = 0,
    [int]$Reps = 30,
    [int]$K = 2,
    [int]$CooldownS = 5,
    [switch]$UploadOnly,
    [switch]$AllowCharging,
    [switch]$Stop,
    [string]$OutDir = "measurements",
    [string]$Package = "com.mobicloud",
    [int]$TimeoutMin = 120
)

$ErrorActionPreference = "Stop"
$Receiver = "$Package/com.mobicloud.data.instrumentation.ExperimentCommandReceiver"
$RemoteCsv = "/sdcard/Android/data/$Package/files/measurements/measurements.csv"

function Invoke-Adb {
    param([string[]]$AdbArgs)
    if ($Serial -ne "") { $AdbArgs = @("-s", $Serial) + $AdbArgs }
    & adb @AdbArgs
}

# --- Telephone ---------------------------------------------------------------
if (-not (Get-Command adb -ErrorAction SilentlyContinue)) { throw "adb introuvable dans le PATH." }
$devices = @(& adb devices | Select-String -Pattern "\tdevice$" | ForEach-Object { ($_ -split "\t")[0] })
if ($devices.Count -eq 0) { throw "Aucun telephone connecte (adb devices)." }
if ($Serial -eq "") {
    if ($devices.Count -gt 1) { throw "Plusieurs telephones connectes : preciser -Serial parmi $($devices -join ', ')." }
    $Serial = $devices[0]
}
Write-Host "Telephone : $Serial"

if ($Stop) {
    Invoke-Adb @("shell", "am", "broadcast", "-n", $Receiver, "-a", "com.mobicloud.action.STOP_EXPERIMENT") | Out-Null
    Write-Host "Arret demande."
    exit 0
}

if ($Label -notmatch '^[A-Za-z0-9_\-]+$') { throw "-Label obligatoire (lettres, chiffres, _ ou -), ex. wifi_10mb." }
if ($SizeKb -le 0) { $SizeKb = [Math]::Max($SizeMb, 0) * 1024 }
if ($SizeKb -le 0) { throw "Preciser -SizeMb ou -SizeKb." }

# --- Charge ------------------------------------------------------------------
$battery = (Invoke-Adb @("shell", "dumpsys", "battery")) -join "`n"
$charging = $battery -match "(AC|USB|Wireless) powered: true"
if ($charging -and -not $AllowCharging) {
    Write-Host "Le telephone est en charge : l'energie mesuree serait fausse." -ForegroundColor Yellow
    Write-Host "Debrancher le cable et utiliser le debogage sans fil (adb connect <ip>:<port>)," -ForegroundColor Yellow
    Write-Host "ou relancer avec -AllowCharging pour ne mesurer que les durees et le debit." -ForegroundColor Yellow
    exit 1
}

# --- Lancement ---------------------------------------------------------------
$runId = Get-Date -Format "yyyyMMdd-HHmmss"
$roundTrip = if ($UploadOnly) { "false" } else { "true" }
Invoke-Adb @("logcat", "-c") | Out-Null
$result = (Invoke-Adb @(
    "shell", "am", "broadcast", "-n", $Receiver, "-a", "com.mobicloud.action.RUN_EXPERIMENT",
    "--es", "run_id", $runId, "--es", "label", $Label, "--ei", "size_kb", "$SizeKb",
    "--ei", "reps", "$Reps", "--ez", "round_trip", $roundTrip, "--ei", "k", "$K",
    "--ei", "cooldown_s", "$CooldownS"
)) -join " "
if ($result -notmatch "Broadcast completed") { throw "Commande non delivree : $result" }
Write-Host "Campagne $Label ($runId) : $Reps x $SizeKb Ko, aller-retour=$roundTrip, k=$K"

# --- Suivi -------------------------------------------------------------------
$printed = 0
$deadline = (Get-Date).AddMinutes($TimeoutMin)
$finished = $false
while (-not $finished -and (Get-Date) -lt $deadline) {
    Start-Sleep -Seconds 5
    $lines = @(Invoke-Adb @("logcat", "-d", "-s", "MobiCloud:EXP:*", "-v", "time"))
    $lines = @($lines | Where-Object { $_ -match "MobiCloud:EXP" })
    for ($i = $printed; $i -lt $lines.Count; $i++) {
        Write-Host $lines[$i]
        if ($lines[$i] -match "EXPERIMENT (DONE|ERROR|STOPPED|BUSY)") { $finished = $true }
    }
    $printed = $lines.Count
}
if (-not $finished) { Write-Host "Delai depasse ($TimeoutMin min) : la campagne continue peut-etre sur le telephone." -ForegroundColor Yellow }

# --- Recuperation ------------------------------------------------------------
New-Item -ItemType Directory -Force -Path $OutDir | Out-Null
$safeSerial = $Serial -replace '[^A-Za-z0-9]', '_'
$localCsv = Join-Path $OutDir "$safeSerial`_measurements.csv"
Invoke-Adb @("pull", $RemoteCsv, $localCsv) | Out-Null
Write-Host "CSV : $localCsv"
Write-Host "Resume : python scripts\experiments\summarize.py $localCsv --label $Label"
