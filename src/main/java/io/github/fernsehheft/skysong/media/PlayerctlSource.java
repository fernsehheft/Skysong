package io.github.fernsehheft.skysong.media;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * Linux (and BSD) players through MPRIS, using the {@code playerctl} command line tool.
 * Spotify is preferred when it's open, otherwise whichever player playerctl finds first.
 */
public final class PlayerctlSource implements MediaSource {
    private static final String FORMAT = String.join("\t",
            "{{status}}", "{{xesam:title}}", "{{xesam:artist}}", "{{xesam:album}}",
            "{{mpris:length}}", "{{position}}", "{{playerName}}");
    private static final List<String> COMMAND = List.of(
            "playerctl", "--player=spotify,%any", "metadata", "--format", FORMAT);

    @Override
    public String name() {
        return "playerctl";
    }

    @Override
    public Optional<NowPlaying> poll() throws IOException {
        ProcessOutput output;
        try {
            output = ProcessOutput.run(2000, COMMAND);
        } catch (IOException e) {
            // Java can't start a program that doesn't exist; the exact wording varies between versions.
            if (e.getMessage() != null && e.getMessage().startsWith("Cannot run program")) {
                throw new IOException("playerctl is not installed (e.g. 'sudo apt install playerctl')", e);
            }
            throw e;
        }
        // playerctl exits with 1 when no player is running, which is perfectly normal.
        if (!output.succeeded()) {
            return Optional.empty();
        }
        return parse(output.stdout(), System.nanoTime());
    }

    static Optional<NowPlaying> parse(String output, long sampledAtNanos) {
        String[] fields = output.strip().split("\t", -1);
        if (fields.length < 7 || fields[0].equals("Stopped") || fields[1].isBlank()) {
            return Optional.empty();
        }
        return Optional.of(new NowPlaying(
                fields[1],
                fields[2],
                fields[3],
                microsToMillis(fields[4]),
                microsToMillis(fields[5]),
                fields[0].equals("Playing"),
                fields[6],
                sampledAtNanos));
    }

    private static long microsToMillis(String value) {
        try {
            return Long.parseLong(value.strip()) / 1000L;
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
