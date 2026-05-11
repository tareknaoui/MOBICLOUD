# ============================================================================
# setup-emulators.ps1
# ----------------------------------------------------------------------------
# Lance N emulateurs Android, attend leur boot complet, installe MobiCloud
# et lance l'app sur chacun. Pre-accorde la permission Localisation.
#
# USAGE
# -----
# .\scripts\setup-emulators.ps1                       # 3 emulateurs par defaut
# .\scripts\setup-emulators.ps1 -Count 2 -AvdName Pixel_6_API_33
# ============================================================================

param(
    [int]$Count = 3,
    [string]$AvdName = "",
    [int]$BasePort = 5554
)

$ErrorActionPreference = "Stop"

# ---------------------------------------------------------------------------
# 1. Localiser emulator.exe
# ---------------------------------------------------------------------------
$candidates = @(
    "$env:LOCALAPPDATA\Android\Sdk\emulator\emulator.exe",
    "$env:USERPROFILE\AppData\Local\Android\Sdk\emulator\emulator.exe",
    "$env:ANDROID_HOME\emulator\emulator.exe",
    "$env:ANDROID_SDK_ROOT\emulator\emulator.exe"
)
$emulatorExe = $candidates | Where-Object { Test-Path $_ } | Select-Object -First 1
if (-not $emulatorExe) {
    Write-Host "ERREUR : emulator.exe introuvable. Definir ANDROID_HOME ou installer Android Studio." -ForegroundColor Red
    exit 1
}
Write-Host "Emulator binary : $emulatorExe" -ForegroundColor Gray

# ---------------------------------------------------------------------------
# 2. Lister les AVDs disponibles, choisir si non specifie
# ---------------------------------------------------------------------------
$availableAvds = & $emulatorExe -list-avds 2>$null | Where-Object { $_ -match "\S" }
if (-not $availableAvds) {
    Write-Host "ERREUR : aucun AVD trouve. Cree un AVD via Android Studio Device Manager d'abord." -ForegroundColor Red
    exit 1
}

if (-not $AvdName) {
    $AvdName = $availableAvds | Select-Object -First 1
    Write-Host "AVD selectionne automatiquement : $AvdName" -ForegroundColor Gray
} elseif ($AvdName -notin $availableAvds) {
    Write-Host "ERREUR : AVD '$AvdName' n'existe pas. AVDs disponibles :" -ForegroundColor Red
    $availableAvds | ForEach-Object { Write-Host "  - $_" }
    exit 1
}

# ---------------------------------------------------------------------------
# 3. Lancer N emulateurs sur ports differents
# ---------------------------------------------------------------------------
Write-Host ""
Write-Host "Lancement de $Count emulateur(s) AVD '$AvdName' a partir du port $BasePort..." -ForegroundColor Cyan
$serials = @()
for ($i = 0; $i -lt $Count; $i++) {
    $port = $BasePort + ($i * 2)
    $serial = "emulator-$port"
    $serials += $serial

    Write-Host "  $serial ..." -NoNewline
    Start-Process -FilePath $emulatorExe `
        -ArgumentList "-avd", $AvdName, "-port", $port, "-no-snapshot-save", "-no-boot-anim" `
        -WindowStyle Normal
    Write-Host " demarre" -ForegroundColor Green
    Start-Sleep -Seconds 5
}

# ---------------------------------------------------------------------------
# 4. Attendre le boot complet de chaque emulateur
# ---------------------------------------------------------------------------
Write-Host ""
Write-Host "Attente du boot complet (peut prendre 60-120s)..." -ForegroundColor Cyan
foreach ($serial in $serials) {
    & adb -s $serial wait-for-device | Out-Null
    $start = Get-Date
    $bootCompleted = "0"
    while ($bootCompleted -ne "1") {
        Start-Sleep -Seconds 2
        $bootCompleted = (& adb -s $serial shell getprop sys.boot_completed 2>$null).Trim()
        $elapsed = ((Get-Date) - $start).TotalSeconds
        if ($elapsed -gt 180) {
            Write-Host "  $serial : timeout 180s" -ForegroundColor Red
            break
        }
    }
    if ($bootCompleted -eq "1") {
        Write-Host "  $serial : boote ($([int]((Get-Date) - $start).TotalSeconds)s)" -ForegroundColor Green
    }
}

# ---------------------------------------------------------------------------
# 5. Installer MobiCloud
# ---------------------------------------------------------------------------
Write-Host ""
Write-Host "Installation de MobiCloud sur les $Count emulateur(s)..." -ForegroundColor Cyan
& ./gradlew :app:installDebug 2>&1 | Where-Object { $_ -match "Installing|Installed|FAILED" }

# ---------------------------------------------------------------------------
# 6. Pre-accorder la permission Localisation et activer la geoloc systeme
# ---------------------------------------------------------------------------
Write-Host ""
Write-Host "Configuration permissions..." -ForegroundColor Cyan
foreach ($serial in $serials) {
    & adb -s $serial shell pm grant com.mobicloud android.permission.ACCESS_FINE_LOCATION 2>$null | Out-Null
    & adb -s $serial shell pm grant com.mobicloud android.permission.ACCESS_COARSE_LOCATION 2>$null | Out-Null
    & adb -s $serial shell pm grant com.mobicloud android.permission.NEARBY_WIFI_DEVICES 2>$null | Out-Null
    & adb -s $serial shell pm grant com.mobicloud android.permission.POST_NOTIFICATIONS 2>$null | Out-Null
    & adb -s $serial shell settings put secure location_mode 3 2>$null | Out-Null
    Write-Host "  $serial : permissions accordees" -ForegroundColor Green
}

# ---------------------------------------------------------------------------
# 7. Lancer MobiCloud sur chaque emulateur
# ---------------------------------------------------------------------------
Write-Host ""
Write-Host "Lancement de MobiCloud..." -ForegroundColor Cyan
foreach ($serial in $serials) {
    & adb -s $serial shell am start -n com.mobicloud/com.mobicloud.MainActivity 2>$null | Out-Null
    Write-Host "  $serial : MobiCloud lance" -ForegroundColor Green
}

# ---------------------------------------------------------------------------
# 8. Recap
# ---------------------------------------------------------------------------
Write-Host ""
Write-Host "===============================================================" -ForegroundColor Cyan
Write-Host "  Setup termine" -ForegroundColor Cyan
Write-Host "===============================================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "Devices ADB connectes :" -ForegroundColor Green
& adb devices
Write-Host ""
Write-Host "Patiente 30 secondes que Bully converge, puis lance :" -ForegroundColor Yellow
Write-Host "  .\scripts\test-migration.ps1" -ForegroundColor White
Write-Host ""
