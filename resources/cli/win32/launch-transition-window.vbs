' launch-transition-window.vbs
'
' Hidden relay that starts transition-window.ps1 without any console flash.
'
' Why a VBS relay instead of `cmd /c start powershell ...`:
'   - `cmd /c start` allocates a console (conhost), so a black terminal window
'     flashes before the splash appears; windowsHide only hides the directly
'     spawned cmd.exe, not the console `start` creates internally.
'   - Spawning powershell.exe detached strips its console and PowerShell can exit
'     before running the script; not detaching means the window may not outlive
'     the parent.
'   - wscript.exe is a GUI-subsystem host (no console at all), and
'     WshShell.Run(cmd, 0, False) launches PowerShell with a hidden window and
'     returns immediately, so the splash survives independently.
'
' Adapted from the same pattern WorkBuddy uses for its silent updates.
'
' Usage (spawned by the shortcut created in scripts/installer.nsh, or manually):
'   wscript.exe launch-transition-window.vbs <ps1Path> [powershell args...]
' Arg 0 is the .ps1 path; the remaining args are forwarded verbatim to
' powershell.exe -File <ps1Path> <args...>.

Option Explicit

Dim shell, args, i, ps1Path, forwarded, cmd

If WScript.Arguments.Count < 1 Then
    ' The splash is a non-critical UX enhancement: do nothing rather than error.
    WScript.Quit 0
End If

Set shell = CreateObject("WScript.Shell")
Set args = WScript.Arguments

ps1Path = args(0)

forwarded = ""
For i = 1 To args.Count - 1
    forwarded = forwarded & " " & Quote(args(i))
Next

cmd = "powershell.exe -NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File " _
    & Quote(ps1Path) & forwarded

' 0 = hidden window, False = do not wait (PowerShell outlives this relay).
shell.Run cmd, 0, False

WScript.Quit 0

' Wrap a value in double quotes, doubling any embedded double quotes
' (VBScript / command-line convention).
Function Quote(value)
    Quote = """" & Replace(value, """", """""") & """"
End Function
