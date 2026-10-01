; GrandPoem Studio Custom NSIS Installer/Uninstaller Script
;
; Install: enables long paths, adds resources\cli to user PATH for openclaw CLI.
; Uninstall: removes the PATH entry and optionally deletes user data.

!include "LogicLib.nsh"

!ifndef nsProcess::FindProcess
  !include "nsProcess.nsh"
!endif

Var /GLOBAL grandpoem_studioRollbackDir

!macro customHeader
  ; Show install details by default so users can see what stage is running.
  ShowInstDetails show
  ShowUninstDetails show
!macroend

!ifndef BUILD_UNINSTALLER
Function GrandPoemStudioMoveLegacyInstallDir
  Exch $R6

  ${if} $R6 == ""
    Goto _grandpoem-studio_legacy_move_done
  ${endIf}
  ${if} $R6 == $INSTDIR
    Goto _grandpoem-studio_legacy_move_done
  ${endIf}

  IfFileExists "$R6\" 0 _grandpoem-studio_legacy_move_done
    DetailPrint "Moving previous GrandPoem Studio installation at $R6 out of the way..."
    SetOutPath $TEMP
    nsExec::ExecToStack `"$SYSDIR\WindowsPowerShell\v1.0\powershell.exe" -NoProfile -NonInteractive -ExecutionPolicy Bypass -Command "Get-CimInstance -ClassName Win32_Process | Where-Object { $$_.ExecutablePath -and $$_.ExecutablePath.StartsWith('$R6', [System.StringComparison]::OrdinalIgnoreCase) } | ForEach-Object { Stop-Process -Id $$_.ProcessId -Force -ErrorAction SilentlyContinue }"`
    Pop $0
    Pop $1
    Sleep 1000
    StrCpy $R8 0

  _grandpoem-studio_legacy_find_free_stale:
    IfFileExists "$R6._stale_$R8\" 0 _grandpoem-studio_legacy_found_free_stale
    IntOp $R8 $R8 + 1
    Goto _grandpoem-studio_legacy_find_free_stale

  _grandpoem-studio_legacy_found_free_stale:
    ClearErrors
    Rename "$R6" "$R6._stale_$R8"
    IfErrors 0 _grandpoem-studio_legacy_stale_moved
      DetailPrint "Waiting for file locks at $R6 to clear..."
      nsExec::ExecToStack `"$SYSDIR\WindowsPowerShell\v1.0\powershell.exe" -NoProfile -NonInteractive -ExecutionPolicy Bypass -Command "Get-CimInstance -ClassName Win32_Process | Where-Object { $$_.ExecutablePath -and $$_.ExecutablePath.StartsWith('$R6', [System.StringComparison]::OrdinalIgnoreCase) } | ForEach-Object { Stop-Process -Id $$_.ProcessId -Force -ErrorAction SilentlyContinue }"`
      Pop $0
      Pop $1
      Sleep 2000
      ClearErrors
      Rename "$R6" "$R6._stale_$R8"
      IfErrors 0 _grandpoem-studio_legacy_stale_moved
      DetailPrint "Removing previous GrandPoem Studio installation at $R6..."
      nsExec::ExecToStack 'cmd.exe /c rd /s /q "$R6"'
      Pop $0
      Pop $1
      Goto _grandpoem-studio_legacy_move_done
  _grandpoem-studio_legacy_stale_moved:
    ExecShell "" "cmd.exe" `/c ping -n 61 127.0.0.1 >nul & rd /s /q "$R6._stale_$R8"` SW_HIDE

  _grandpoem-studio_legacy_move_done:
    ClearErrors
    Pop $R6
FunctionEnd

!macro grandpoem-studioMoveLegacyInstallDir ROOT_KEY
  ReadRegStr $R6 ${ROOT_KEY} "${INSTALL_REGISTRY_KEY}" InstallLocation
  Push $R6
  Call GrandPoemStudioMoveLegacyInstallDir
!macroend
!endif


!macro customCheckAppRunning
  ; Make stage logs visible on assisted installers (defaults to hidden).
  SetDetailsPrint both
  DetailPrint "Preparing installation..."
  DetailPrint "Extracting GrandPoem Studio runtime files. This can take a few minutes on slower disks or while antivirus scanning is active."

  ${nsProcess::FindProcess} "${APP_EXECUTABLE_FILENAME}" $R0

  ${if} $R0 == 0
    ${if} ${isUpdated}
      # Auto-update: the app is already shutting down (quitAndInstall was called).
      # The before-quit handler needs up to 8s to gracefully stop the Gateway
      # process tree (5s timeout + force-terminate + re-quit).  Wait for the
      # app to exit on its own before resorting to force-kill.
      DetailPrint `Waiting for "${PRODUCT_NAME}" to finish shutting down...`
      Sleep 8000
      ${nsProcess::FindProcess} "${APP_EXECUTABLE_FILENAME}" $R0
      ${if} $R0 != 0
        # App exited cleanly. Still kill long-lived child processes (gateway,
        # uv, python) which may not have followed the app's graceful exit.
        nsExec::ExecToStack 'taskkill /F /IM openclaw-gateway.exe'
        Pop $0
        Pop $1
        Goto done_killing
      ${endIf}
      # App didn't exit in time; fall through to force-kill
    ${endIf}

    doStopProcess:
    DetailPrint `Closing running "${PRODUCT_NAME}"...`

    # Kill ALL processes whose executable lives inside $INSTDIR.
    # This covers GrandPoem Studio.exe (multiple Electron processes), openclaw-gateway.exe,
    # python.exe (skills runtime), uv.exe (package manager), and any other
    # child process that might hold file locks in the installation directory.
    #
    # Use PowerShell Get-CimInstance for path-based matching (most reliable),
    # with taskkill name-based fallback for restricted environments.
    # Note: Using backticks ` ` for the NSIS string allows us to use single quotes inside.
    nsExec::ExecToStack `"$SYSDIR\WindowsPowerShell\v1.0\powershell.exe" -NoProfile -NonInteractive -ExecutionPolicy Bypass -Command "Get-CimInstance -ClassName Win32_Process | Where-Object { $$_.ExecutablePath -and $$_.ExecutablePath.StartsWith('$INSTDIR', [System.StringComparison]::OrdinalIgnoreCase) } | ForEach-Object { Stop-Process -Id $$_.ProcessId -Force -ErrorAction SilentlyContinue }"`
    Pop $0
    Pop $1

    ${if} $0 != 0
      # PowerShell failed (policy restriction, etc.) 鈥?fall back to name-based kill
      nsExec::ExecToStack 'taskkill /F /T /IM "${APP_EXECUTABLE_FILENAME}"'
      Pop $0
      Pop $1
    ${endIf}

    # Also kill well-known child processes that may have detached from the
    # Electron process tree or run from outside $INSTDIR (e.g. system python).
    nsExec::ExecToStack 'taskkill /F /IM openclaw-gateway.exe'
    Pop $0
    Pop $1

    # Wait for Windows to fully release file handles after process termination.
    # 5 seconds accommodates slow antivirus scanners and filesystem flush delays.
    Sleep 5000
    DetailPrint "Processes terminated. Continuing installation..."

    done_killing:
      ${nsProcess::Unload}
  ${endIf}

  ; Even if GrandPoem Studio.exe was not detected as running, orphan child processes
  ; (python.exe, openclaw-gateway.exe, uv.exe, etc.) from a previous crash
  ; or unclean shutdown may still hold file locks inside $INSTDIR.
  ; Unconditionally kill any process whose executable lives in the install dir.
  nsExec::ExecToStack `"$SYSDIR\WindowsPowerShell\v1.0\powershell.exe" -NoProfile -NonInteractive -ExecutionPolicy Bypass -Command "Get-CimInstance -ClassName Win32_Process | Where-Object { $$_.ExecutablePath -and $$_.ExecutablePath.StartsWith('$INSTDIR', [System.StringComparison]::OrdinalIgnoreCase) } | ForEach-Object { Stop-Process -Id $$_.ProcessId -Force -ErrorAction SilentlyContinue }"`
  Pop $0
  Pop $1

  ; Always kill known process names as a belt-and-suspenders approach.
  ; PowerShell path-based kill may miss processes if the old GrandPoem Studio was installed
  ; in a different directory than $INSTDIR (e.g., per-machine -> per-user migration).
  ; taskkill is name-based and catches processes regardless of their install location.
  nsExec::ExecToStack 'taskkill /F /T /IM "${APP_EXECUTABLE_FILENAME}"'
  Pop $0
  Pop $1
  nsExec::ExecToStack 'taskkill /F /IM openclaw-gateway.exe'
  Pop $0
  Pop $1
  ; Note: we intentionally do NOT kill uv.exe globally 鈥?it is a popular
  ; Python package manager and other users/CI jobs may have uv running.
  ; The PowerShell path-based kill above already handles uv inside $INSTDIR.

  ; Brief wait for handle release (main wait was already done above if app was running)
  Sleep 2000

  ; Do not continue while the old UI process is still alive. Continuing in that
  ; state can leave the running old process/window in place, making the user see
  ; the old version after an otherwise successful extract.  Use process-list
  ; commands instead of nsProcess here: field diagnostics showed GrandPoem Studio.exe can
  ; remain alive while the old installer still reports success; this check must
  ; fail closed even when taskkill or the nsProcess plugin misses/elevates poorly.
  StrCpy $R7 0
  _grandpoem-studio_verify_closed:
    nsExec::ExecToStack `"$SYSDIR\WindowsPowerShell\v1.0\powershell.exe" -NoProfile -NonInteractive -ExecutionPolicy Bypass -Command "if (Get-CimInstance -ClassName Win32_Process | Where-Object { $$_.Name -ieq '${APP_EXECUTABLE_FILENAME}' }) { exit 0 } else { exit 1 }"`
    Pop $R0
    Pop $R1
    ${if} $R0 != 0
      nsExec::ExecToStack 'cmd.exe /c tasklist /FI "IMAGENAME eq ${APP_EXECUTABLE_FILENAME}" | find /I "${APP_EXECUTABLE_FILENAME}" >nul'
      Pop $R0
      Pop $R1
    ${endIf}
    ${if} $R0 == 0
      IntOp $R7 $R7 + 1
      DetailPrint `Waiting for "${PRODUCT_NAME}" to close (attempt $R7)...`
      nsExec::ExecToStack 'taskkill /F /T /IM "${APP_EXECUTABLE_FILENAME}"'
      Pop $0
      Pop $1
      nsExec::ExecToStack `cmd.exe /c wmic process where "name='${APP_EXECUTABLE_FILENAME}'" call terminate`
      Pop $0
      Pop $1
      Sleep 2000
      ${if} $R7 < 5
        Goto _grandpoem-studio_verify_closed
      ${endIf}
      MessageBox MB_RETRYCANCEL|MB_ICONEXCLAMATION "GrandPoem Studio is still running and cannot be replaced safely. Please close GrandPoem Studio and retry installation." /SD IDCANCEL IDRETRY _grandpoem-studio_verify_closed
      SetErrorLevel 2
      Quit
    ${endIf}

  !ifndef BUILD_UNINSTALLER
    StrCpy $grandpoem_studioRollbackDir ""

    ; Release NSIS's CWD on $INSTDIR BEFORE the rename check.
    ; NSIS sets CWD to $INSTDIR in .onInit; Windows refuses to rename a directory
    ; that any process (including NSIS itself) has as its CWD.
    SetOutPath $TEMP

    ; Move legacy installs discovered in both registry hives before handling the
    ; current $INSTDIR.  This covers per-user <-> per-machine migrations and
    ; custom install directories where the new $INSTDIR is not the old location.
    !insertmacro grandpoem-studioMoveLegacyInstallDir HKCU
    !insertmacro grandpoem-studioMoveLegacyInstallDir HKLM

    ; Pre-emptively clear the old installation directory so that the 7z
  ; extraction `CopyFiles` step in extractAppPackage.nsh won't fail on
  ; locked files.  electron-builder's extractUsing7za macro extracts to a
  ; temp folder first, then uses `CopyFiles /SILENT` to copy into $INSTDIR.
  ; If ANY file in $INSTDIR is still locked, CopyFiles fails and triggers a
  ; "Can't modify GrandPoem Studio's files" retry loop -> "GrandPoem Studio 鏃犳硶鍏抽棴" dialog.
  ;
  ; Strategy: rename (move) the old $INSTDIR out of the way.  Rename works
  ; even when AV/indexer have files open for reading (they use
  ; FILE_SHARE_DELETE sharing mode), whereas CopyFiles fails because it
  ; needs write/overwrite access which some AV products deny.
  ; Check if a previous installation exists ($INSTDIR is a directory).
  ; Use trailing backslash 鈥?the correct NSIS idiom for directory existence.
  ; (IfFileExists "$INSTDIR\*.*" only matches files containing a dot and
  ;  would fail for extensionless files or pure-subdirectory layouts.)
  IfFileExists "$INSTDIR\" 0 _instdir_clean
    DetailPrint "Moving previous installation out of the way..."
    ; Find the first available stale directory name (e.g. $INSTDIR._stale_0)
    ; This ensures we NEVER have to synchronously delete old leftovers before
    ; renaming the current $INSTDIR. We just move it out of the way instantly.
    StrCpy $R8 0
  _find_free_stale:
    IfFileExists "$INSTDIR._stale_$R8\" 0 _found_free_stale
    IntOp $R8 $R8 + 1
    Goto _find_free_stale

  _found_free_stale:
    ClearErrors
    Rename "$INSTDIR" "$INSTDIR._stale_$R8"
    IfErrors 0 _stale_moved
      ; Rename still failed 鈥?retry process termination, then delete synchronously.
      ; Large openclaw bundles (#1026+) can make rd /s /q take several minutes.
      DetailPrint "Waiting for file locks to clear, then removing old files..."
      nsExec::ExecToStack 'taskkill /F /T /IM "${APP_EXECUTABLE_FILENAME}"'
      Pop $0
      Pop $1
      nsExec::ExecToStack 'taskkill /F /IM openclaw-gateway.exe'
      Pop $0
      Pop $1
      Sleep 3000
      nsExec::ExecToStack 'cmd.exe /c rd /s /q "$INSTDIR"'
      Pop $0
      Pop $1
      Sleep 2000
      RMDir "$INSTDIR"
      IfFileExists "$INSTDIR\" 0 _recreate_clean_instdir
        DetailPrint "Failed to remove previous installation directory; aborting to avoid leaving the old version installed."
        MessageBox MB_OK|MB_ICONEXCLAMATION "Unable to replace the previous GrandPoem Studio installation because files are still locked. Please close GrandPoem Studio and retry installation." /SD IDOK
        SetErrorLevel 2
        Quit
      _recreate_clean_instdir:
      CreateDirectory "$INSTDIR"
      Goto _instdir_clean
  _stale_moved:
    StrCpy $grandpoem_studioRollbackDir "$INSTDIR._stale_$R8"
    CreateDirectory "$INSTDIR"
  _instdir_clean:

  ; During overwrite installs, stale files can still survive if the old
  ; installation directory was only partially removed after a locked-file
  ; fallback. Explicitly remove the bundled skills subtree so old skills
  ; (apple-notes, discord, etc.) do not remain under resources\openclaw\skills.
  IfFileExists "$INSTDIR\resources\openclaw\skills\" 0 _openclaw_skills_clean
    DetailPrint "Removing stale bundled OpenClaw skills from previous install..."
    RMDir /r "$INSTDIR\resources\openclaw\skills"
    IfFileExists "$INSTDIR\resources\openclaw\skills\" 0 _openclaw_skills_clean
      nsExec::ExecToStack 'cmd.exe /c rd /s /q "$INSTDIR\resources\openclaw\skills"'
      Pop $0
      Pop $1
  _openclaw_skills_clean:

  ; Opposite-hive registry cleanup is intentionally done in customInstall after
  ; successful extraction, so a failed update can still roll back to the old app
  ; with its existing uninstall entries intact.
  !endif
!macroend

; Override electron-builder's handleUninstallResult to prevent the
; "GrandPoem Studio 鏃犳硶鍏抽棴" retry dialog when the old uninstaller fails.
;
; During upgrades, electron-builder copies the old uninstaller to a temp dir
; and runs it silently.  The old uninstaller uses atomicRMDir to rename every
; file out of $INSTDIR.  If ANY file is still locked (antivirus scanner,
; Windows Search indexer, delayed kernel handle release after taskkill), it
; aborts with a non-zero exit code.  The default handler retries 5脳 then shows
; a blocking MessageBox.
;
; This macro clears the error and lets the new installer proceed 鈥?it will
; simply overwrite / extract new files on top of the (partially cleaned) old
; installation directory.  This is safe because:
;   1. Processes have already been force-killed in customCheckAppRunning.
;   2. The new installer extracts a complete, self-contained file tree.
;   3. Any leftover old files that weren't removed are harmless.
!macro customUnInstallCheck
  ${if} $R0 != 0
    DetailPrint "Old uninstaller exited with code $R0. Continuing with overwrite install..."
  ${endIf}
  ClearErrors
!macroend

; Same safety net for the HKEY_CURRENT_USER uninstall path.
; Without this, handleUninstallResult would show a fatal error and Quit.
!macro customUnInstallCheckCurrentUser
  ${if} $R0 != 0
    DetailPrint "Old uninstaller (current user) exited with code $R0. Continuing..."
  ${endIf}
  ClearErrors
!macroend

!macro customInstall
  ; Now that the new files and current-hive registry entries have been written,
  ; remove stale entries from the opposite hive so Windows Apps & Features does
  ; not continue showing the old version after cross-hive upgrades.
  DetailPrint "Clearing stale GrandPoem Studio registry entries from the opposite install scope..."
  ${if} $installMode == "all"
    DeleteRegKey HKCU "${UNINSTALL_REGISTRY_KEY}"
    DeleteRegKey HKCU "${INSTALL_REGISTRY_KEY}"
    !ifdef UNINSTALL_REGISTRY_KEY_2
      DeleteRegKey HKCU "${UNINSTALL_REGISTRY_KEY_2}"
    !endif
  ${else}
    DeleteRegKey HKLM "${UNINSTALL_REGISTRY_KEY}"
    DeleteRegKey HKLM "${INSTALL_REGISTRY_KEY}"
    !ifdef UNINSTALL_REGISTRY_KEY_2
      DeleteRegKey HKLM "${UNINSTALL_REGISTRY_KEY_2}"
    !endif
  ${endIf}
  ClearErrors

  ; Async cleanup of old dirs left by the rename loop in customCheckAppRunning.
  ; Wait 60s before starting deletion to avoid I/O contention with GrandPoem Studio's
  ; first launch (Windows Defender scan, ASAR mapping, etc.).
  ; ExecShell SW_HIDE is completely detached from NSIS and avoids pipe blocking.
  IfFileExists "$INSTDIR._stale_0\" 0 _ci_stale_cleaned
    ; Use PowerShell to extract the basename of $INSTDIR so the glob works
    ; even when the user picked a custom install folder name.
    ; E.g. $INSTDIR = D:\Apps\MyClaw 鈫?glob = MyClaw._stale_*
    ExecShell "" "cmd.exe" `/c ping -n 61 127.0.0.1 >nul & cd /d "$INSTDIR\.." & for /d %D in ("$INSTDIR._stale_*") do rd /s /q "%D"` SW_HIDE
  _ci_stale_cleaned:
  DetailPrint "Core files extracted. Finalizing system integration..."

  ; Enable Windows long path support (Windows 10 1607+ / Windows 11).
  ; pnpm virtual store paths can exceed the default MAX_PATH limit of 260 chars.
  ; Writing to HKLM requires admin privileges; on per-user installs without
  ; elevation this call silently fails 鈥?no crash, just no key written.
  DetailPrint "Enabling long-path support (if permissions allow)..."
  WriteRegDWORD HKLM "SYSTEM\CurrentControlSet\Control\FileSystem" "LongPathsEnabled" 1

  ; Add the install directory (plus the app's runtime data directories) to the
  ; Windows Defender exclusion list.
  ;
  ; Why: the packaged app is ~50k files and unsigned, so on a freshly installed
  ; machine the first launch is dominated by Defender inspecting every new file
  ; and by the cloud reputation lookup for an unknown binary.
  ;
  ; The helper script needs administrator rights. A per-user install runs
  ; unelevated, so we call it with -NonElevated: instead of prompting it exits
  ; with code 3, and the user can opt in from the finish page or later from
  ; Settings -> Developer. See docs/startup-performance-plan.md (scheme 1).
  DetailPrint "Configuring Windows Defender exclusion (optional)..."
  InitPluginsDir
  ClearErrors
  File "/oname=$PLUGINSDIR\set-defender-exclusion.ps1" "${PROJECT_DIR}\resources\cli\win32\set-defender-exclusion.ps1"
  nsExec::ExecToStack '"$SYSDIR\WindowsPowerShell\v1.0\powershell.exe" -NoProfile -NonInteractive -ExecutionPolicy Bypass -File "$PLUGINSDIR\set-defender-exclusion.ps1" -Action add -InstallDir "$INSTDIR" -NonElevated'
  Pop $0
  Pop $1
  ${if} $0 == "0"
    DetailPrint "Windows Defender exclusion added."
  ${else}
    DetailPrint "Windows Defender exclusion skipped (administrator approval required)."
  ${endIf}

  ; Use PowerShell to update the current user's PATH.
  ; This avoids NSIS string-buffer limits and preserves long PATH values.
  DetailPrint "Updating user PATH for the OpenClaw CLI..."
  InitPluginsDir
  ClearErrors
  File "/oname=$PLUGINSDIR\update-user-path.ps1" "${PROJECT_DIR}\resources\cli\win32\update-user-path.ps1"
  nsExec::ExecToStack '"$SYSDIR\WindowsPowerShell\v1.0\powershell.exe" -NoProfile -NonInteractive -ExecutionPolicy Bypass -File "$PLUGINSDIR\update-user-path.ps1" -Action add -CliDir "$INSTDIR\resources\cli"'
  Pop $0
  Pop $1
  StrCmp $0 "error" 0 +2
    DetailPrint "Warning: Failed to launch PowerShell while updating PATH."
  StrCmp $0 "timeout" 0 +2
    DetailPrint "Warning: PowerShell PATH update timed out."
  StrCmp $0 "0" 0 +2
    Goto _ci_done
  DetailPrint "Warning: PowerShell PATH update exited with code $0."

  _ci_done:

  ; Repoint the shortcuts at a splash bootstrap when one is available.
  ;
  ; Two implementations exist, in order of preference:
  ;   1. resources\bin\grandpoem-launcher.exe — a ~430 KB compiled WinForms
  ;      splash (scripts/build-launcher.mjs; needs the in-box C# compiler)
  ;   2. a wscript relay + PowerShell WinForms window (the pattern WorkBuddy
  ;      ships), used when the compiler was unavailable at build time
  ; Both only make the wait visible; neither changes how long startup takes.
  ; See docs/startup-performance-plan.md (scheme 3b).
  StrCpy $R5 ""
  StrCpy $R4 ""
  StrCpy $R3 "$INSTDIR\${APP_EXECUTABLE_FILENAME}"

  ${if} ${FileExists} "$INSTDIR\resources\bin\grandpoem-launcher.exe"
    StrCpy $R5 "$INSTDIR\resources\bin\grandpoem-launcher.exe"
    DetailPrint "Pointing shortcuts at the splash launcher..."
  ${elseIf} ${FileExists} "$INSTDIR\cli\launch-transition-window.vbs"
    StrCpy $R5 "$SYSDIR\wscript.exe"
    StrCpy $R4 '"$INSTDIR\cli\launch-transition-window.vbs" "$INSTDIR\cli\transition-window.ps1" "$INSTDIR\${APP_EXECUTABLE_FILENAME}"'
    DetailPrint "Pointing shortcuts at the PowerShell transition window..."
  ${endIf}

  ${if} $R5 != ""
    ${if} ${FileExists} "$DESKTOP\${SHORTCUT_NAME}.lnk"
      CreateShortCut "$DESKTOP\${SHORTCUT_NAME}.lnk" "$R5" "$R4" "$R3" 0
    ${endIf}
    !ifdef MENU_FILENAME
      ${if} ${FileExists} "$SMPROGRAMS\${MENU_FILENAME}\${SHORTCUT_NAME}.lnk"
        CreateShortCut "$SMPROGRAMS\${MENU_FILENAME}\${SHORTCUT_NAME}.lnk" "$R5" "$R4" "$R3" 0
      ${endIf}
    !endif
    ${if} ${FileExists} "$SMPROGRAMS\${SHORTCUT_NAME}.lnk"
      CreateShortCut "$SMPROGRAMS\${SHORTCUT_NAME}.lnk" "$R5" "$R4" "$R3" 0
    ${endIf}
  ${endIf}

  ; Pre-warm the OpenClaw runtime in the background.
  ;
  ; The first Gateway boot compiles ~10k loose modules unless
  ; NODE_COMPILE_CACHE is already populated (see electron/gateway/
  ; process-launcher.ts), and Defender still has to make its first pass over
  ; the whole runtime tree. Paying that here - while the installer window is
  ; still open - removes it from the user's first click.
  ;
  ; Deliberately a detached process: it must not delay the installer, and the
  ; app's own pre-warm mode never takes the single-instance lock, so a launch
  ; during or after it is unaffected. See docs/startup-performance-plan.md
  ; (scheme 5).
  IfFileExists "$INSTDIR\resources\openclaw\openclaw.mjs" 0 _ci_prewarm_done
  IfFileExists "$INSTDIR\resources\bin\node.exe" 0 _ci_prewarm_done
    DetailPrint "Pre-warming the OpenClaw runtime cache in the background..."
    ExecShell "" "cmd.exe" `/c set "NODE_COMPILE_CACHE=$APPDATA\grandpoem-studio\openclaw-compile-cache" & "$INSTDIR\resources\bin\node.exe" "$INSTDIR\resources\openclaw\openclaw.mjs" doctor --json >nul 2>&1` SW_HIDE
  _ci_prewarm_done:

  ; --- OpenClaw runtime detection -------------------------------------------
  ; The runtime is either bundled in the package (classic build) or downloaded on
  ; demand. Only ask the user when this build pins a runtime version and the
  ; machine has no matching copy.
  ;
  ; The download itself deliberately happens in the app on first launch: there it
  ; can show progress, resume an interrupted transfer and verify a checksum.
  ; Here we only detect and record the user's consent.
  ; See docs/startup-performance-plan.md.
  StrCpy $R8 ""
  ClearErrors
  FileOpen $R9 "$INSTDIR\resources\runtime-required.txt" r
  IfErrors _ci_runtime_checked
    FileRead $R9 $R8
    FileClose $R9
    ; The file holds the version plus a single trailing LF; drop it so the
    ; string can be used as a directory name.
    StrCpy $R8 $R8 -1
  _ci_runtime_checked:

  ${if} $R8 != ""
    DetailPrint "Required OpenClaw runtime version: $R8"
    StrCpy $R7 "missing"
    ${if} ${FileExists} "$INSTDIR\resources\openclaw\package.json"
      StrCpy $R7 "bundled"
    ${elseIf} ${FileExists} "$LOCALAPPDATA\grandpoem-studio\runtime\$R8\.grandpoem-runtime.json"
      StrCpy $R7 "installed"
    ${endIf}

    ${if} $R7 == "missing"
      MessageBox MB_YESNO|MB_ICONQUESTION "The OpenClaw runtime for version $R8 is not installed (about 255 MB).$\r$\n$\r$\nDownload it automatically the first time GrandPoem Studio starts?" /SD IDYES IDYES _ci_runtime_consent_yes IDNO _ci_runtime_consent_no

      _ci_runtime_consent_yes:
        CreateDirectory "$LOCALAPPDATA\grandpoem-studio"
        ClearErrors
        FileOpen $R9 "$LOCALAPPDATA\grandpoem-studio\runtime-consent.json" w
        IfErrors _ci_runtime_consent_yes_done
          FileWrite $R9 '{"consent":true,"desiredVersion":"$R8","source":"installer"}'
          FileClose $R9
        _ci_runtime_consent_yes_done:
        DetailPrint "Runtime download approved; it will start on first launch."
        Goto _ci_runtime_consent_done

      _ci_runtime_consent_no:
        CreateDirectory "$LOCALAPPDATA\grandpoem-studio"
        ClearErrors
        FileOpen $R9 "$LOCALAPPDATA\grandpoem-studio\runtime-consent.json" w
        IfErrors _ci_runtime_consent_no_done
          FileWrite $R9 '{"consent":false,"desiredVersion":"$R8","source":"installer"}'
          FileClose $R9
        _ci_runtime_consent_no_done:
        DetailPrint "Runtime download declined; it can be started later from Settings."
        Goto _ci_runtime_consent_done

      _ci_runtime_consent_done:
    ${else}
      DetailPrint "OpenClaw runtime already satisfied ($R7)."
    ${endIf}
  ${endIf}

  DetailPrint "Installation steps complete."
!macroend

; Finish page.
;
; electron-builder's assisted installer adds a "run the app" checkbox only when
; `customFinishPage` is NOT defined (see app-builder-lib/templates/nsis/
; assistedInstaller.nsh), so redefining the page means re-declaring
; MUI_FINISHPAGE_RUN ourselves.
;
; The readme checkbox slot is reused as an opt-in for the Defender exclusion:
; this is the one moment where an extra UAC prompt is expected and welcome, and
; it is the only way for a per-user (unelevated) install to register the
; exclusion.
!macro customFinishPage
  Function StartApp
    ${if} ${isUpdated}
      StrCpy $1 "--updated"
    ${else}
      StrCpy $1 ""
    ${endif}
    ${StdUtils.ExecShellAsUser} $0 "$launchLink" "open" "$1"
  FunctionEnd

  Function GrandPoemStudioAddDefenderExclusion
    IfFileExists "$PLUGINSDIR\set-defender-exclusion.ps1" 0 _gp_defender_missing
      DetailPrint "Adding the Windows Defender exclusion (administrator approval required)..."
      nsExec::ExecToStack '"$SYSDIR\WindowsPowerShell\v1.0\powershell.exe" -NoProfile -NonInteractive -ExecutionPolicy Bypass -File "$PLUGINSDIR\set-defender-exclusion.ps1" -Action add -InstallDir "$INSTDIR"'
      Pop $0
      Pop $1
      ${if} $0 == "0"
        DetailPrint "Windows Defender exclusion added."
      ${else}
        DetailPrint "Windows Defender exclusion was not applied (exit code $0). It can be added later from Settings -> Developer."
      ${endIf}
      Return
    _gp_defender_missing:
      DetailPrint "Defender helper script missing; skipping."
  FunctionEnd

  !define MUI_FINISHPAGE_RUN
  !define MUI_FINISHPAGE_RUN_FUNCTION "StartApp"
  ; The checkbox itself is narrow (195u in MUI2's finish page), so keep the label
  ; short and put the explanation in the page body text.
  !define MUI_FINISHPAGE_TEXT "Setup has finished installing GrandPoem Studio.$\r$\n$\r$\nOptional: tick the box below to add the installation folder to the Windows Defender exclusion list. Real-time scanning of a freshly installed app is what makes the very first launch slow. This requires administrator rights, and you can also enable or remove it later in Settings > Developer."
  !define MUI_FINISHPAGE_SHOWREADME ""
  !define MUI_FINISHPAGE_SHOWREADME_TEXT "Faster startup (Defender exclusion)"
  !define MUI_FINISHPAGE_SHOWREADME_FUNCTION GrandPoemStudioAddDefenderExclusion
  !insertmacro MUI_PAGE_FINISH
!macroend

!macro customUnInstall
  ; Remove the Windows Defender exclusion(s) added at install time.
  ; Uses the shared helper so only the three known paths are removed — a user's
  ; pre-existing exclusions are never touched.
  InitPluginsDir
  ClearErrors
  File "/oname=$PLUGINSDIR\set-defender-exclusion.ps1" "${PROJECT_DIR}\resources\cli\win32\set-defender-exclusion.ps1"
  IfFileExists "$PLUGINSDIR\set-defender-exclusion.ps1" 0 _cu_defender_done
    nsExec::ExecToStack '"$SYSDIR\WindowsPowerShell\v1.0\powershell.exe" -NoProfile -NonInteractive -ExecutionPolicy Bypass -File "$PLUGINSDIR\set-defender-exclusion.ps1" -Action remove -InstallDir "$INSTDIR"'
    Pop $0
    Pop $1
  _cu_defender_done:

  ; Remove resources\cli from user PATH via PowerShell so long PATH values are handled safely
  InitPluginsDir
  ClearErrors
  File "/oname=$PLUGINSDIR\update-user-path.ps1" "${PROJECT_DIR}\resources\cli\win32\update-user-path.ps1"
  nsExec::ExecToStack '"$SYSDIR\WindowsPowerShell\v1.0\powershell.exe" -NoProfile -NonInteractive -ExecutionPolicy Bypass -File "$PLUGINSDIR\update-user-path.ps1" -Action remove -CliDir "$INSTDIR\resources\cli"'
  Pop $0
  Pop $1
  StrCmp $0 "error" 0 +2
    DetailPrint "Warning: Failed to launch PowerShell while removing PATH entry."
  StrCmp $0 "timeout" 0 +2
    DetailPrint "Warning: PowerShell PATH removal timed out."
  StrCmp $0 "0" 0 +2
    Goto _cu_pathDone
  DetailPrint "Warning: PowerShell PATH removal exited with code $0."

  _cu_pathDone:

  ; Ask user if they want to remove AppData (preserves .openclaw)
  MessageBox MB_YESNO|MB_ICONQUESTION \
    "Do you want to remove GrandPoem Studio application data?$\r$\n$\r$\nThis will delete:$\r$\n  鈥?AppData\Local\grandpoem-studio (local app data)$\r$\n  鈥?AppData\Roaming\grandpoem-studio (roaming app data)$\r$\n$\r$\nYour .openclaw folder (configuration & skills) will be preserved.$\r$\nSelect 'No' to keep all data for future reinstallation." \
    /SD IDNO IDYES _cu_removeData IDNO _cu_skipRemove

  _cu_removeData:
    ; Kill any lingering GrandPoem Studio processes (and their child process trees) to
    ; release file locks on electron-store JSON files, Gateway sockets, etc.
    ${nsProcess::FindProcess} "${APP_EXECUTABLE_FILENAME}" $R0
    ${if} $R0 == 0
      nsExec::ExecToStack 'taskkill /F /T /IM "${APP_EXECUTABLE_FILENAME}"'
      Pop $0
      Pop $1
    ${endIf}
    ${nsProcess::Unload}

    ; Wait for processes to fully exit and release file handles
    Sleep 2000

    ; --- Always remove current user's AppData first ---
    ; NOTE: .openclaw directory is intentionally preserved (user configuration & skills)
    RMDir /r "$LOCALAPPDATA\grandpoem-studio"
    RMDir /r "$APPDATA\grandpoem-studio"

    ; --- Retry: if directories still exist (locked files), wait and try again ---

    ; Check AppData\Local\grandpoem-studio
    IfFileExists "$LOCALAPPDATA\grandpoem-studio\*.*" 0 _cu_localDone
      Sleep 3000
      RMDir /r "$LOCALAPPDATA\grandpoem-studio"
      IfFileExists "$LOCALAPPDATA\grandpoem-studio\*.*" 0 _cu_localDone
        nsExec::ExecToStack 'cmd.exe /c rd /s /q "$LOCALAPPDATA\grandpoem-studio"'
        Pop $0
        Pop $1
    _cu_localDone:

    ; Check AppData\Roaming\grandpoem-studio
    IfFileExists "$APPDATA\grandpoem-studio\*.*" 0 _cu_roamingDone
      Sleep 3000
      RMDir /r "$APPDATA\grandpoem-studio"
      IfFileExists "$APPDATA\grandpoem-studio\*.*" 0 _cu_roamingDone
        nsExec::ExecToStack 'cmd.exe /c rd /s /q "$APPDATA\grandpoem-studio"'
        Pop $0
        Pop $1
    _cu_roamingDone:

    ; --- Final check: warn user if any directories could not be removed ---
    StrCpy $R3 ""
    IfFileExists "$LOCALAPPDATA\grandpoem-studio\*.*" 0 +2
      StrCpy $R3 "$R3$\r$\n  鈥?$LOCALAPPDATA\grandpoem-studio"
    IfFileExists "$APPDATA\grandpoem-studio\*.*" 0 +2
      StrCpy $R3 "$R3$\r$\n  鈥?$APPDATA\grandpoem-studio"
    StrCmp $R3 "" _cu_cleanupOk
      MessageBox MB_OK|MB_ICONEXCLAMATION \
        "Some data directories could not be removed (files may be in use):$\r$\n$R3$\r$\n$\r$\nPlease delete them manually after restarting your computer."
    _cu_cleanupOk:

    ; --- For per-machine (all users) installs, enumerate all user profiles ---
    StrCpy $R0 0

  _cu_enumLoop:
    EnumRegKey $R1 HKLM "SOFTWARE\Microsoft\Windows NT\CurrentVersion\ProfileList" $R0
    StrCmp $R1 "" _cu_enumDone

    ReadRegStr $R2 HKLM "SOFTWARE\Microsoft\Windows NT\CurrentVersion\ProfileList\$R1" "ProfileImagePath"
    StrCmp $R2 "" _cu_enumNext

    ; ExpandEnvStrings requires distinct src and dest registers
    ExpandEnvStrings $R3 $R2
    StrCmp $R3 $PROFILE _cu_enumNext

    ; NOTE: .openclaw directory is intentionally preserved for all users
    RMDir /r "$R3\AppData\Local\grandpoem-studio"
    RMDir /r "$R3\AppData\Roaming\grandpoem-studio"

  _cu_enumNext:
    IntOp $R0 $R0 + 1
    Goto _cu_enumLoop

  _cu_enumDone:
  _cu_skipRemove:
!macroend
