package io.github.fernsehheft.skysong.media;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * macOS: asks Spotify or Apple Music through AppleScript.
 *
 * <p>We check with {@code pgrep} first which app is open, because telling an app that
 * isn't running to do anything would launch it.
 */
public final class AppleScriptSource implements MediaSource {
    private static final List<String> APPS = List.of("Spotify", "Music");

    // Both apps report the position in seconds. Spotify reports the duration in
    // milliseconds and Music in seconds, so Spotify's is divided here to match.
    private static final String SCRIPT = """
            tell application "%s"
                set playerState to player state as string
                if playerState is "stopped" then return ""
                set t to current track
                return playerState & tab & (name of t) & tab & (artist of t) & tab & (album of t) & tab & ((duration of t) %s) & tab & player position
            end tell
            """;

    @Override
    public String name() {
        return "AppleScript";
    }

    @Override
    public Optional<NowPlaying> poll() throws IOException {
        for (String app : APPS) {
            if (!isRunning(app)) {
                continue;
            }
            String script = SCRIPT.formatted(app, app.equals("Spotify") ? "/ 1000" : "");
            ProcessOutput output = ProcessOutput.run(2000, List.of("osascript", "-e", script));
            if (output.succeeded()) {
                Optional<NowPlaying> track = parse(output.stdout(), app, System.nanoTime());
                if (track.isPresent()) {
                    return track;
                }
            }
        }
        return Optional.empty();
    }

    private static boolean isRunning(String app) throws IOException {
        return ProcessOutput.run(1000, List.of("pgrep", "-xq", app)).succeeded();
    }

    static Optional<NowPlaying> parse(String output, String app, long sampledAtNanos) {
        String[] fields = output.strip().split("\t", -1);
        if (fields.length < 6 || fields[1].isBlank()) {
            return Optional.empty();
        }
        return Optional.of(new NowPlaying(
                fields[1],
                fields[2],
                fields[3],
                secondsToMillis(fields[4]),
                secondsToMillis(fields[5]),
                fields[0].equals("playing"),
                app,
                sampledAtNanos));
    }

    private static long secondsToMillis(String value) {
        try {
            // AppleScript may write reals with a decimal comma depending on the system language.
            return Math.round(Double.parseDouble(value.strip().replace(',', '.')) * 1000.0);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
