# transition-window.ps1
#
# Lightweight WinForms splash shown while GrandPoem Studio's main executable is
# being loaded (large unsigned PE + antivirus first-look). Adapted from the same
# pattern WorkBuddy ships for its silent updates: a borderless window that starts
# instantly and closes as soon as the real app owns a visible window.
#
# Launched through launch-transition-window.vbs (a wscript relay), because
# spawning `cmd /c start powershell` flashes a console window, and a detached
# powershell.exe can exit before the script runs.
#
# This is the FALLBACK path. When the build machine has the in-box .NET
# compiler, scripts/build-launcher.mjs produces grandpoem-launcher.exe, which
# starts faster and shows the Chinese branding; installer.nsh prefers it.
# PowerShell 5.1 reads .ps1 files as ANSI without a BOM, so all strings here are
# deliberately ASCII.
#
# Close strategy (whichever fires first):
#   1) the launched app process has a visible main window
#   2) the app process exited (crash / single-instance handoff)
#   3) hard timeout
param(
    [Parameter(Mandatory = $true)][string]$AppPath,
    [string]$AppProcessName = 'GrandPoem Studio',
    [int]$TimeoutSeconds = 60,
    [int]$MinimumVisibleMs = 1200
)

Add-Type -AssemblyName System.Windows.Forms
Add-Type -AssemblyName System.Drawing

$script:started = Get-Date
$script:startedProcess = $null
$script:elapsedMs = 0

try {
    $script:startedProcess = Start-Process -FilePath $AppPath -PassThru
} catch {
    # Nothing to cover: let the shell surface the failure.
    exit 1
}

$form = New-Object System.Windows.Forms.Form
$form.FormBorderStyle = 'None'
$form.StartPosition = 'CenterScreen'
$form.ClientSize = New-Object System.Drawing.Size(420, 220)
$form.ShowInTaskbar = $true
$form.TopMost = $true
$form.Text = 'GrandPoem Studio'
$form.BackColor = [System.Drawing.Color]::FromArgb(248, 250, 252)
$form.DoubleBuffered = $true

$script:dotPhase = 0

$form.Add_Paint({
    param($sender, $e)
    $g = $e.Graphics
    $g.SmoothingMode = 'AntiAlias'
    $g.TextRenderingHint = 'ClearTypeGridFit'

    $rect = New-Object System.Drawing.Rectangle(0, 0, $sender.ClientSize.Width, $sender.ClientSize.Height)
    $bg = New-Object System.Drawing.Drawing2D.LinearGradientBrush($rect, [System.Drawing.Color]::FromArgb(248, 250, 252), [System.Drawing.Color]::FromArgb(239, 246, 255), 45)
    $g.FillRectangle($bg, $rect)

    $logoRect = New-Object System.Drawing.Rectangle(36, 46, 64, 64)
    $logoBrush = New-Object System.Drawing.Drawing2D.LinearGradientBrush($logoRect, [System.Drawing.Color]::FromArgb(37, 99, 235), [System.Drawing.Color]::FromArgb(147, 51, 234), 45)
    $g.FillEllipse($logoBrush, $logoRect)

    $logoFont = New-Object System.Drawing.Font('Segoe UI', 20, [System.Drawing.FontStyle]::Bold)
    $titleFont = New-Object System.Drawing.Font('Segoe UI', 15, [System.Drawing.FontStyle]::Bold)
    $textFont = New-Object System.Drawing.Font('Segoe UI', 9.5)
    $titleBrush = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(30, 41, 59))
    $textBrush = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(100, 116, 139))
    $white = [System.Drawing.Brushes]::White

    $centered = New-Object System.Drawing.StringFormat
    $centered.Alignment = 'Center'
    $centered.LineAlignment = 'Center'
    $g.DrawString('JY', $logoFont, $white, $logoRect, $centered)

    $g.DrawString('JYparalegal', $titleFont, $titleBrush, 120, 52)
    $g.DrawString('AI contract workspace for Chinese legal professionals', $textFont, $textBrush, 120, 84)

    $dots = @('', '.', '..', '...')[$script:dotPhase]
    $g.DrawString(("Starting" + $dots), $textFont, $textBrush, 36, 144)

    $logoFont.Dispose(); $titleFont.Dispose(); $textFont.Dispose()
    $titleBrush.Dispose(); $textBrush.Dispose(); $bg.Dispose(); $logoBrush.Dispose(); $centered.Dispose()
})

$timer = New-Object System.Windows.Forms.Timer
$timer.Interval = 200
$timer.Add_Tick({
    $script:elapsedMs = ((Get-Date) - $script:started).TotalMilliseconds
    $script:dotPhase = ($script:dotPhase + 1) % 4
    $form.Invalidate()

    $app = $script:startedProcess
    if ($null -eq $app) { $form.Close(); return }

    $app.Refresh()
    if ($app.HasExited) { $form.Close(); return }

    if ($script:elapsedMs -lt $MinimumVisibleMs) { return }

    # MainWindowHandle becomes non-zero once the app owns a visible top-level
    # window. WaitForInputIdle is deliberately NOT used: Electron creates
    # message-only windows within milliseconds, which would hand over far too
    # early.
    if ($app.MainWindowHandle -ne [IntPtr]::Zero) { $form.Close(); return }
    if ($script:elapsedMs -ge ($TimeoutSeconds * 1000)) { $form.Close() }
})
$timer.Start()

[void]$form.ShowDialog()

$timer.Stop()
$timer.Dispose()
$form.Dispose()
exit 0
