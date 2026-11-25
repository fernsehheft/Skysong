# Skysong media helper for Windows.
#
# Prints the media session Windows knows about (the one shown in the volume flyout)
# as one JSON line every half second. Skysong starts this script and reads stdout.
# When Minecraft closes, the pipe breaks, the next write fails and the script ends.

$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = New-Object System.Text.UTF8Encoding $false

Add-Type -AssemblyName System.Runtime.WindowsRuntime
$null = [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager, Windows.Media.Control, ContentType = WindowsRuntime]
$null = [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties, Windows.Media.Control, ContentType = WindowsRuntime]

# PowerShell can't await WinRT operations, so we turn them into a Task and wait on that.
$asTask = [System.WindowsRuntimeSystemExtensions].GetMethods() |
    Where-Object {
        $_.Name -eq 'AsTask' -and
        $_.GetParameters().Count -eq 1 -and
        $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1'
    } |
    Select-Object -First 1

function Wait-WinRt($operation, [Type] $resultType) {
    $task = $asTask.MakeGenericMethod($resultType).Invoke($null, @($operation))
    $null = $task.Wait(-1)
    return $task.Result
}

$managerType = [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager]
$propertiesType = [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties]
$manager = Wait-WinRt ($managerType::RequestAsync()) $managerType

# Prefer something that is actually playing, Spotify first. If nothing plays,
# fall back to the session Windows considers current (usually the last one paused).
function Select-Session {
    $sessions = @($manager.GetSessions())
    $playing = @($sessions | Where-Object { $_.GetPlaybackInfo().PlaybackStatus -eq 'Playing' })
    $spotify = $playing | Where-Object { $_.SourceAppUserModelId -like '*Spotify*' } | Select-Object -First 1
    if ($spotify) { return $spotify }
    if ($playing.Count -gt 0) { return $playing[0] }
    return $manager.GetCurrentSession()
}

function Get-State {
    $session = Select-Session
    if ($null -eq $session) {
        return @{ active = $false }
    }

    $properties = Wait-WinRt ($session.TryGetMediaPropertiesAsync()) $propertiesType
    $timeline = $session.GetTimelineProperties()
    $playing = $session.GetPlaybackInfo().PlaybackStatus -eq 'Playing'

    # Many players (Spotify too) only refresh the timeline every few seconds,
    # so add the time that passed since they last did.
    $position = $timeline.Position.TotalMilliseconds
    if ($playing) {
        $position += ([DateTimeOffset]::Now - $timeline.LastUpdatedTime).TotalMilliseconds
    }

    return [ordered]@{
        active     = $true
        title      = $properties.Title
        artist     = $properties.Artist
        album      = $properties.AlbumTitle
        positionMs = [long][Math]::Max(0, $position)
        durationMs = [long]$timeline.EndTime.TotalMilliseconds
        playing    = $playing
        app        = $session.SourceAppUserModelId
    }
}

while ($true) {
    try {
        $state = Get-State
    } catch {
        $state = @{ active = $false; error = $_.Exception.Message }
    }
    # Deliberately outside the try: if Minecraft is gone this throws and ends the script.
    [Console]::Out.WriteLine(($state | ConvertTo-Json -Compress))
    [Console]::Out.Flush()
    Start-Sleep -Milliseconds 500
}
