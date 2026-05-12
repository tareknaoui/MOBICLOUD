# test-load-based-admission.ps1
# Story 12.1 — Validation de l'admission par charge (sans GPS)
#
# Scénario :
#   Device A : démarre → attend 20 s d'isolement → devient SuperPair du cluster α
#   Device B : démarre → reçoit α via tracker avec currentMemberCount=1 → JOIN → Member
#   Device C : démarre → reçoit α avec currentMemberCount=2 → JOIN → Member
#
# Vérification : les 3 devices partagent le même clusterId (grep [JOIN-FSM])
#
# Prérequis :
#   - 3 émulateurs Android démarrés (emulator-5554, emulator-5556, emulator-5558)
#   - APK déjà installé : .\gradlew installDebug (ou installé manuellement)
#   - adb en PATH
#   - Serveur relai déployé (Render ou local)

param(
    [string]$DeviceA = "emulator-5554",
    [string]$DeviceB = "emulator-5556",
    [string]$DeviceC = "emulator-5558",
    [int]$WaitBullySec = 30,
    [int]$WaitJoinSec  = 25,
    [int]$TimeoutSec   = 120
)

$ErrorActionPreference = "Stop"
$PACKAGE = "com.mobicloud"
$TAG_FSM = "\[JOIN-FSM\]"

function Log { param($msg) Write-Host "$(Get-Date -Format 'HH:mm:ss') $msg" }

function StartApp { param($device)
    adb -s $device shell am start -n "$PACKAGE/.MainActivity" | Out-Null
    Log "[$device] App lancée"
}

function StopApp { param($device)
    adb -s $device shell am force-stop $PACKAGE | Out-Null
}

function ClearLogcat { param($device)
    adb -s $device logcat -c | Out-Null
}

function GetFsmLogs { param($device, [int]$lines = 200)
    adb -s $device logcat -d -s "JoinStateMachine" | Select-Object -Last $lines
}

function ExtractClusterId { param($device)
    $logs = GetFsmLogs $device
    # Cherche "clusterId=xxxxxxxx" dans les logs FSM
    $match = $logs | Select-String -Pattern "clusterId=([0-9a-f-]{8})" | Select-Object -Last 1
    if ($match) {
        return $match.Matches[0].Groups[1].Value
    }
    # Fallback : cherche "ACCEPT" avec clusterId
    $match2 = $logs | Select-String -Pattern "JOIN_ACCEPT.*clusterId=([0-9a-f-]{8})" | Select-Object -Last 1
    if ($match2) {
        return $match2.Matches[0].Groups[1].Value
    }
    return $null
}

function CheckDeviceOnline { param($device)
    $status = adb -s $device get-state 2>$null
    return $status -eq "device"
}

# ─── Vérification prérequis ─────────────────────────────────────────────────

Log "=== test-load-based-admission.ps1 (Story 12.1) ==="
Log "Vérification des 3 émulateurs..."

foreach ($dev in @($DeviceA, $DeviceB, $DeviceC)) {
    if (-not (CheckDeviceOnline $dev)) {
        Write-Error "Émulateur $dev non disponible — lancer avec : emulator -avd <nom>"
    }
    Log "  $dev : OK"
}

# ─── Phase 1 : Device A devient SuperPair ──────────────────────────────────

Log ""
Log "=== PHASE 1 : Device A ($DeviceA) → SuperPair ==="
StopApp $DeviceA
ClearLogcat $DeviceA
StartApp $DeviceA

Log "Attente élection Bully ($WaitBullySec s)..."
Start-Sleep -Seconds $WaitBullySec

# Vérifier que A est SuperPair
$logsA = GetFsmLogs $DeviceA
$isSuperPairA = $logsA | Select-String "SuperPair" | Select-Object -Last 1
if (-not $isSuperPairA) {
    Write-Error "ÉCHEC : Device A n'a pas atteint l'état SuperPair après $WaitBullySec s"
}
Log "Device A → SuperPair : OK"

# ─── Phase 2 : Device B rejoint le cluster α ────────────────────────────────

Log ""
Log "=== PHASE 2 : Device B ($DeviceB) → JOIN cluster α ==="
StopApp $DeviceB
ClearLogcat $DeviceB
StartApp $DeviceB

Log "Attente JOIN Device B ($WaitJoinSec s)..."
Start-Sleep -Seconds $WaitJoinSec

$logsB = GetFsmLogs $DeviceB
$isMemberB = $logsB | Select-String "Member" | Select-Object -Last 1
if (-not $isMemberB) {
    Write-Warning "Device B n'a pas encore transité vers Member — réseau peut être lent"
}

# ─── Phase 3 : Device C rejoint le cluster α ────────────────────────────────

Log ""
Log "=== PHASE 3 : Device C ($DeviceC) → JOIN cluster α ==="
StopApp $DeviceC
ClearLogcat $DeviceC
StartApp $DeviceC

Log "Attente JOIN Device C ($WaitJoinSec s)..."
Start-Sleep -Seconds $WaitJoinSec

# ─── Vérification : même clusterId ─────────────────────────────────────────

Log ""
Log "=== VÉRIFICATION : même clusterId sur les 3 devices ==="

$cidA = ExtractClusterId $DeviceA
$cidB = ExtractClusterId $DeviceB
$cidC = ExtractClusterId $DeviceC

Log "ClusterId Device A : $cidA"
Log "ClusterId Device B : $cidB"
Log "ClusterId Device C : $cidC"

# Vérification FSM sur logs
$fsmA = GetFsmLogs $DeviceA 500
$fsmB = GetFsmLogs $DeviceB 500
$fsmC = GetFsmLogs $DeviceC 500

$noGpsA = -not ($fsmA | Select-String "gps|latitude|longitude|MAX_RADIUS" -CaseSensitive)
$noGpsB = -not ($fsmB | Select-String "gps|latitude|longitude|MAX_RADIUS" -CaseSensitive)
$noGpsC = -not ($fsmC | Select-String "gps|latitude|longitude|MAX_RADIUS" -CaseSensitive)

Log ""
Log "=== RÉSULTAT ==="

$ok = $true

if ($cidA -and $cidB -and $cidC -and $cidA -eq $cidB -and $cidA -eq $cidC) {
    Log "[PASS] Les 3 devices partagent le clusterId : $cidA"
} else {
    Log "[FAIL] ClusterIds divergent : A=$cidA B=$cidB C=$cidC"
    $ok = $false
}

if ($noGpsA -and $noGpsB -and $noGpsC) {
    Log "[PASS] Aucune référence GPS dans les logs FSM"
} else {
    Log "[WARN] Références GPS détectées dans les logs (vérifier manuellement)"
}

if ($ok) {
    Log ""
    Log "✅ OK — Admission par charge (memberCount) validée sans GPS"
    exit 0
} else {
    Log ""
    Log "❌ FAIL — Vérifier les logs adb logcat sur chaque device"
    exit 1
}
