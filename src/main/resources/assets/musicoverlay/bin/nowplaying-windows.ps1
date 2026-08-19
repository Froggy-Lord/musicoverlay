# Music Overlay - Windows now-playing helper.
#
# Reads the System Media Transport Controls (SMTC) session - the same source
# behind the Windows media flyout - so it sees whatever app is playing: Spotify,
# a browser tab, Groove, foobar, anything that reports media. Writes a JSON
# snapshot the mod polls, saves the album thumbnail, and drains a command file so
# the in-game keybinds can toggle play/pause and skip.
#
# Pure PowerShell + WinRT, no install step.

$ErrorActionPreference = 'Stop'

$baseDir = $env:MC_NOWPLAYING_DIR
if ([string]::IsNullOrEmpty($baseDir)) { $baseDir = Join-Path $env:TEMP 'musicoverlay' }
$artDir = Join-Path $baseDir 'art'
$jsonPath = Join-Path $baseDir 'nowplaying.json'
$commandPath = Join-Path $baseDir 'command'
New-Item -ItemType Directory -Force -Path $artDir | Out-Null

Add-Type -AssemblyName System.Runtime.WindowsRuntime

# Bring the WinRT projections into the session.
[Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager, Windows.Media.Control, ContentType = WindowsRuntime] | Out-Null
[Windows.Storage.Streams.DataReader, Windows.Storage.Streams, ContentType = WindowsRuntime] | Out-Null

# --- async helpers: turn IAsyncOperation/IAsyncAction into blocking calls ---
$asTaskOp = ([System.WindowsRuntimeSystemExtensions].GetMethods() | Where-Object {
    $_.Name -eq 'AsTask' -and $_.GetParameters().Count -eq 1 -and
    $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1'
})[0]
$asTaskAction = ([System.WindowsRuntimeSystemExtensions].GetMethods() | Where-Object {
    $_.Name -eq 'AsTask' -and $_.GetParameters().Count -eq 1 -and
    $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncAction'
})[0]

function Await($op, $resultType) {
    $task = $asTaskOp.MakeGenericMethod($resultType).Invoke($null, @($op))
    $task.Wait(-1) | Out-Null
    $task.Result
}
function AwaitAction($op) {
    $task = $asTaskAction.Invoke($null, @($op))
    $task.Wait(-1) | Out-Null
}

$statusNames = @{ 0 = 'Stopped'; 1 = 'Stopped'; 2 = 'Stopped'; 3 = 'Stopped'; 4 = 'Playing'; 5 = 'Paused' }
$lastHash = ''
$lastArtPath = ''

function Write-Json($data) {
    $tmp = "$jsonPath.tmp"
    ($data | ConvertTo-Json -Compress) | Set-Content -Path $tmp -Encoding UTF8
    Move-Item -Path $tmp -Destination $jsonPath -Force
}

function Empty-Snapshot {
    [ordered]@{
        title = ''; artist = ''; album = ''; sourceApp = '';
        positionMs = 0; durationMs = 0; status = 'Stopped';
        coverPath = ''; coverHash = ''; canControl = $false; timestamp = 0
    }
}

function Save-Thumbnail($mediaProps, $hash) {
    if ($lastHash -eq $hash -and (Test-Path $lastArtPath)) { return $lastArtPath }
    if ($null -eq $mediaProps.Thumbnail) { return '' }
    try {
        $target = Join-Path $artDir $hash
        $stream = Await $mediaProps.Thumbnail.OpenReadAsync() ([Windows.Storage.Streams.IRandomAccessStreamWithContentType])
        $size = [uint32]$stream.Size
        if ($size -eq 0) { return '' }
        $reader = [Windows.Storage.Streams.DataReader]::new($stream)
        Await $reader.LoadAsync($size) ([uint32]) | Out-Null
        $bytes = New-Object byte[] $size
        $reader.ReadBytes($bytes)
        [System.IO.File]::WriteAllBytes($target, $bytes)
        $script:lastHash = $hash
        $script:lastArtPath = $target
        return $target
    } catch {
        return ''
    }
}

function Get-Session {
    $mgr = Await ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager]::RequestAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager])
    return $mgr.GetCurrentSession()
}

function Drain-Commands($session) {
    if (-not (Test-Path $commandPath)) { return }
    try {
        $lines = Get-Content -Path $commandPath -ErrorAction Stop
        Clear-Content -Path $commandPath -ErrorAction SilentlyContinue
    } catch { return }
    if ($null -eq $session) { return }
    foreach ($line in $lines) {
        switch ($line.Trim().ToLower()) {
            'playpause' { AwaitAction $session.TryTogglePlayPauseAsync() }
            'next'      { AwaitAction $session.TrySkipNextAsync() }
            'previous'  { AwaitAction $session.TrySkipPreviousAsync() }
        }
    }
}

Write-Output 'Music Overlay Windows helper running (SMTC / WinRT).'

while ($true) {
    try {
        $session = Get-Session
        Drain-Commands $session

        if ($null -eq $session) {
            Write-Json (Empty-Snapshot)
        } else {
            $media = Await $session.TryGetMediaPropertiesAsync() ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties])
            $timeline = $session.GetTimelineProperties()
            $playback = $session.GetPlaybackInfo()

            $title = if ($media.Title) { $media.Title } else { '' }
            $artist = if ($media.Artist) { $media.Artist } else { '' }
            $album = if ($media.AlbumTitle) { $media.AlbumTitle } else { '' }
            $statusCode = [int]$playback.PlaybackStatus
            $status = if ($statusNames.ContainsKey($statusCode)) { $statusNames[$statusCode] } else { 'Stopped' }

            $positionMs = [long]$timeline.Position.TotalMilliseconds
            $durationMs = [long]($timeline.EndTime.TotalMilliseconds - $timeline.StartTime.TotalMilliseconds)

            $hash = [System.BitConverter]::ToString(
                [System.Security.Cryptography.MD5]::Create().ComputeHash(
                    [System.Text.Encoding]::UTF8.GetBytes("$title|$artist|$album"))
            ).Replace('-', '').ToLower()
            $coverPath = Save-Thumbnail $media $hash

            Write-Json ([ordered]@{
                title = $title; artist = $artist; album = $album
                sourceApp = $session.SourceAppUserModelId
                positionMs = $positionMs; durationMs = $durationMs; status = $status
                coverPath = $coverPath; coverHash = $hash
                canControl = $true; timestamp = [long](([DateTimeOffset]::UtcNow).ToUnixTimeMilliseconds())
            })
        }
    } catch {
        try { Write-Json (Empty-Snapshot) } catch {}
    }
    Start-Sleep -Milliseconds 250
}
