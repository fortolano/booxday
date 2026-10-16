<#
.SYNOPSIS
    Puts your OpenRouter API key into BooxDay on a connected BOOX tablet, from an environment variable.

.DESCRIPTION
    BooxDay keeps the key in encrypted storage that only the app itself can write. On every start the
    app also looks in its old plain preferences file (api_key_prefs) and, if it finds a key there, moves
    it into encrypted storage and deletes the plain copy (security/UserApiKeyStore.getOpenRouterKey).
    This script writes the key into that plain file and starts the app, which then secures it.

    - Works with debug builds only (needs "adb run-as"); the tablets run debug builds.
    - Replaces any key already saved in the app.
    - Stops the app first (unsaved work in an open dialog is lost; drawings are saved as you draw).
    - The key is sent to the tablet over adb's standard input: it never appears on a command line,
      in a file on the PC or in this script's output.

.PARAMETER Serial
    adb serial of the tablet (Go 10.3 = 496ba931, Go 6 = 6d029feb). Optional when one device is connected.

.PARAMETER KeyVariable
    Name of the environment variable holding the key. Default: OPENROUTER_API_KEY.

.EXAMPLE
    .\tools\install-openrouter-key.ps1
.EXAMPLE
    .\tools\install-openrouter-key.ps1 -Serial 496ba931
#>
param(
    [string]$Serial,
    [string]$KeyVariable = 'OPENROUTER_API_KEY',
    [string]$Package = 'com.boox.day'
)

# Not 'Stop': in Windows PowerShell 5.1 that turns any adb stderr line (monkey always writes one)
# into a fatal error. Failures are checked explicitly below.
$ErrorActionPreference = 'Continue'

function Fail([string]$message) {
    Write-Host "ERROR: $message" -ForegroundColor Red
    exit 1
}

# --- The key -----------------------------------------------------------------------------------
$key = [Environment]::GetEnvironmentVariable($KeyVariable)
if ([string]::IsNullOrWhiteSpace($key)) {
    Fail "Environment variable $KeyVariable is not set (or is empty). Set it, open a new terminal, and run this again."
}
$key = $key.Trim()
if ($key -notmatch '^sk-or-[A-Za-z0-9_-]+$') {
    Fail "$KeyVariable does not look like an OpenRouter key (expected sk-or-... with letters, digits, '-' or '_')."
}

# --- The tablet ----------------------------------------------------------------------------------
$adb = (Get-Command adb -ErrorAction SilentlyContinue).Source
if (-not $adb) { Fail "adb not found on PATH (Android SDK platform-tools)." }

$devices = @(& $adb devices | Select-String -Pattern '^(\S+)\s+device$' | ForEach-Object { $_.Matches[0].Groups[1].Value })
if (-not $Serial) {
    if ($devices.Count -eq 0) { Fail "No device connected (adb devices shows none in 'device' state)." }
    if ($devices.Count -gt 1) { Fail "More than one device connected ($($devices -join ', ')). Pick one with -Serial." }
    $Serial = $devices[0]
} elseif ($devices -notcontains $Serial) {
    Fail "Device $Serial is not connected (connected: $(if ($devices) { $devices -join ', ' } else { 'none' }))."
}
Write-Host "Device: $Serial"

if (-not (& $adb -s $Serial shell pm path $Package)) { Fail "$Package is not installed on $Serial." }
$runAsCheck = (& $adb -s $Serial shell run-as $Package id 2>&1 | Out-String)
if ($runAsCheck -notmatch 'uid=') {
    Fail "Can't use run-as for $Package (a release build?). This script needs a debug build. adb said: $($runAsCheck.Trim())"
}

# --- Write the key ---------------------------------------------------------------------------------
Write-Host "Stopping BooxDay..."
& $adb -s $Serial shell am force-stop $Package | Out-Null

$xml = "<?xml version='1.0' encoding='utf-8' standalone='yes' ?>`n<map>`n" +
       "    <string name=`"openrouter_api_key`">$([Security.SecurityElement]::Escape($key))</string>`n</map>`n"

# How many lines of the plain prefs file hold the key ("" when the file doesn't exist).
# The whole command goes to the device shell as one string, so 2>/dev/null runs there.
function KeyLinesInPlainPrefs {
    (& $adb -s $Serial shell "run-as $Package grep -c openrouter_api_key shared_prefs/api_key_prefs.xml 2>/dev/null" | Out-String).Trim()
}

& $adb -s $Serial shell "run-as $Package mkdir -p shared_prefs" | Out-Null

# exec-in runs the command without a shell (so no "sh -c '...'", no "> file"): tee writes stdin to
# the file. tee also echoes it back; that comes only into this process and is discarded unprinted.
$psi = New-Object System.Diagnostics.ProcessStartInfo
$psi.FileName = $adb
$psi.Arguments = "-s $Serial exec-in run-as $Package tee shared_prefs/api_key_prefs.xml"
$psi.UseShellExecute = $false
$psi.RedirectStandardInput = $true
$psi.RedirectStandardOutput = $true
$psi.RedirectStandardError = $true
# Plain UTF-8: otherwise .NET writes a byte-order mark first (Windows PowerShell 5.1 takes the
# encoding from the console), and the prefs file would not start with "<?xml".
$utf8NoBom = New-Object System.Text.UTF8Encoding($false)
$restoreConsoleEncoding = $null
if ($psi.PSObject.Properties.Name -contains 'StandardInputEncoding') {
    $psi.StandardInputEncoding = $utf8NoBom
} else {
    $restoreConsoleEncoding = [Console]::InputEncoding
    [Console]::InputEncoding = $utf8NoBom
}
try {
    $process = [System.Diagnostics.Process]::Start($psi)
} finally {
    if ($restoreConsoleEncoding) { [Console]::InputEncoding = $restoreConsoleEncoding }
}
$stdoutTask = $process.StandardOutput.ReadToEndAsync()
$stderrTask = $process.StandardError.ReadToEndAsync()
$bytes = (New-Object System.Text.UTF8Encoding($false)).GetBytes($xml)
$process.StandardInput.BaseStream.Write($bytes, 0, $bytes.Length)
$process.StandardInput.Close()
$process.WaitForExit()
$stderr = $stderrTask.Result
$null = $stdoutTask.Result
$xml = $null; $bytes = $null; $key = $null; $stdoutTask = $null
if ($process.ExitCode -ne 0) { Fail "Writing the key failed (adb exit $($process.ExitCode)) $stderr. The USB link may have dropped: check 'adb devices' and run this again." }

$written = KeyLinesInPlainPrefs
if ($written -ne '1') { Fail "The key did not land in the app's preferences file (found $(if ($written) { $written } else { 'no file' }))." }
$start = (& $adb -s $Serial shell "run-as $Package head -c 5 shared_prefs/api_key_prefs.xml 2>/dev/null" | Out-String).Trim()
if ($start -ne '<?xml') {
    & $adb -s $Serial shell "run-as $Package rm -f shared_prefs/api_key_prefs.xml" | Out-Null
    Fail "The preferences file was written in the wrong format (doesn't start with '<?xml'); removed it, nothing changed in the app."
}

# --- Let the app move it into encrypted storage -------------------------------------------------
Write-Host "Starting BooxDay so it can move the key into encrypted storage..."
& $adb -s $Serial shell "monkey -p $Package -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1" | Out-Null

$secured = $false
for ($i = 0; $i -lt 20; $i++) {
    Start-Sleep -Seconds 1
    if ((KeyLinesInPlainPrefs) -eq '0') { $secured = $true; break }
}

if ($secured) {
    Write-Host "Done: the key is saved in BooxDay's encrypted storage and the plain copy is gone." -ForegroundColor Green
} else {
    Write-Host "The key is saved, but still in the plain preferences file after 20 s." -ForegroundColor Yellow
    Write-Host "Either the app hasn't finished starting (open it and check AI settings), or encrypted storage is"
    Write-Host "unavailable on this device, in which case BooxDay keeps using the plain file (by design)."
}
