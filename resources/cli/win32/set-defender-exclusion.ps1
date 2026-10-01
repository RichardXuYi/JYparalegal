# set-defender-exclusion.ps1
#
# Adds (or removes) the Windows Defender exclusions that keep real-time scanning
# off the GrandPoem Studio install tree and its runtime data directories.
#
# Why this matters: the packaged app is ~50k files and ships without a code
# signing certificate. On a machine that has just installed it, the first launch
# is dominated by Defender inspecting every one of those files and by the cloud
# reputation lookup for an unknown unsigned binary — which is the difference
# between a few seconds and about a minute.
#
# Contract with the callers (scripts/installer.nsh) and
# electron/utils/defender-exclusion.ts:
#   -Action add | remove            required
#   -InstallDir <path>              required; the app's install directory
#   -ResultFile <path>              optional; receives a JSON status report
#   -NonElevated                    optional; do NOT prompt for UAC, exit 3 instead
#
# Exit codes:
#   0  applied and verified
#   2  unexpected error
#   3  not elevated and -NonElevated was requested
#   4  elevated, the command ran, but the exclusion is not present afterwards
#      (typically Tamper Protection or a policy block)
#
# The caller is responsible for surfacing exit code 4 to the user with manual
# instructions; the app never silently pretends the exclusion was applied.

[CmdletBinding()]
param(
  [Parameter(Mandatory = $true)]
  [ValidateSet('add', 'remove')]
  [string]$Action,

  [Parameter(Mandatory = $true)]
  [string]$InstallDir,

  [string]$ResultFile,

  [switch]$NonElevated
)

$ErrorActionPreference = 'Stop'

function Test-IsAdministrator {
  $identity = [Security.Principal.WindowsIdentity]::GetCurrent()
  $principal = New-Object Security.Principal.WindowsPrincipal($identity)
  return $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
}

function Write-Result {
  param(
    [bool]$Ok,
    [bool]$Verified,
    [string]$Message,
    [string]$Action,
    [string]$InstallDir,
    [string[]]$ExclusionPath,
    [string[]]$ExclusionProcess
  )

  if (-not $ResultFile) { return }

  $payload = [pscustomobject]@{
    ok               = $Ok
    action           = $Action
    installDir       = $InstallDir
    verified         = $Verified
    message          = $Message
    tamperProtected  = $null
    exclusionPath    = @($ExclusionPath)
    exclusionProcess = @($ExclusionProcess)
    checkedAt        = (Get-Date).ToUniversalTime().ToString('o')
  }

  try {
    $payload | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath $ResultFile -Encoding UTF8
  } catch {
    # Best effort: the exit code is the authoritative signal.
  }
}

if (-not (Test-IsAdministrator)) {
  if ($NonElevated) {
    Write-Result -Ok $false -Verified $false -Message 'not elevated' `
      -Action $Action -InstallDir $InstallDir -ExclusionPath @() -ExclusionProcess @()
    exit 3
  }

  # Re-launch elevated and wait for it, so the caller's exit code reflects the
  # real outcome. -NonElevated is intentionally NOT forwarded: the elevated run
  # must actually apply the change.
  $arguments = @(
    '-NoProfile', '-NonInteractive', '-ExecutionPolicy', 'Bypass',
    '-File', ('"{0}"' -f $PSCommandPath),
    '-Action', $Action,
    '-InstallDir', ('"{0}"' -f $InstallDir)
  )
  if ($ResultFile) {
    $arguments += @('-ResultFile', ('"{0}"' -f $ResultFile))
  }

  try {
    $process = Start-Process -FilePath 'powershell.exe' -Verb RunAs -ArgumentList $arguments -Wait -PassThru
    exit $process.ExitCode
  } catch {
    Write-Result -Ok $false -Verified $false -Message ("elevation failed: {0}" -f $_.Exception.Message) `
      -Action $Action -InstallDir $InstallDir -ExclusionPath @() -ExclusionProcess @()
    exit 4
  }
}

try {
  # Keep the exclusion list as narrow as possible: the install directory, the
  # per-user app data, and the OpenClaw state directory. Never the whole disk or
  # the whole user profile.
  $targets = @(
    $InstallDir,
    (Join-Path $env:APPDATA 'grandpoem-studio'),
    (Join-Path $env:USERPROFILE '.openclaw')
  ) | Where-Object { $_ -and (Test-Path -LiteralPath $_) }

  $processNames = @('GrandPoem Studio.exe', 'openclaw-gateway.exe')

  if ($Action -eq 'add') {
    foreach ($target in $targets) {
      Add-MpPreference -ExclusionPath $target -ErrorAction SilentlyContinue
    }
    foreach ($name in $processNames) {
      Add-MpPreference -ExclusionProcess $name -ErrorAction SilentlyContinue
    }
  } else {
    foreach ($target in $targets) {
      Remove-MpPreference -ExclusionPath $target -ErrorAction SilentlyContinue
    }
    foreach ($name in $processNames) {
      Remove-MpPreference -ExclusionProcess $name -ErrorAction SilentlyContinue
    }
  }

  $preference = Get-MpPreference
  $currentPaths = @($preference.ExclusionPath)
  $currentProcesses = @($preference.ExclusionProcess)

  $verified = if ($Action -eq 'add') {
    ($currentPaths -contains $InstallDir)
  } else {
    -not ($currentPaths -contains $InstallDir)
  }

  $tamperProtected = $null
  try {
    $tamperProtected = (Get-MpComputerStatus).IsTamperProtected
  } catch {
    # Get-MpComputerStatus can be unavailable on stripped-down systems.
  }

  Write-Result -Ok $verified -Verified $verified `
    -Message ($(if ($verified) { 'ok' } else { 'exclusion not present after running the command' })) `
    -Action $Action -InstallDir $InstallDir `
    -ExclusionPath $currentPaths -ExclusionProcess $currentProcesses

  if ($ResultFile -and $null -ne $tamperProtected) {
    # Fold the tamper-protection flag into the report without a second write path.
    try {
      $report = Get-Content -LiteralPath $ResultFile -Raw | ConvertFrom-Json
      $report.tamperProtected = [bool]$tamperProtected
      $report | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath $ResultFile -Encoding UTF8
    } catch {
      # ignore
    }
  }

  if ($Action -eq 'add' -and -not $verified) { exit 4 }
  exit 0
} catch {
  Write-Result -Ok $false -Verified $false -Message ("error: {0}" -f $_.Exception.Message) `
    -Action $Action -InstallDir $InstallDir -ExclusionPath @() -ExclusionProcess @()
  exit 2
}
