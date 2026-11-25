package io.github.fernsehheft.skysong.media;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import io.github.fernsehheft.skysong.Skysong;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Windows: reads the system media session (whatever shows up in the volume flyout).
 *
 * <p>There is no Java API for that, so we run a small PowerShell script that keeps
 * printing the state as JSON lines. Starting PowerShell is slow, which is why the
 * script stays alive instead of being started for every poll.
 */
public final class WindowsMediaSource implements MediaSource {
    private static final String SCRIPT_RESOURCE = "/skysong/windows-media.ps1";
    /** If the helper hasn't said anything for this long, we stop trusting the last line. */
    private static final long STALE_AFTER_NANOS = TimeUnit.SECONDS.toNanos(3);
    /** A helper that dies sooner than this after starting counts as a failed start. */
    private static final long EARLY_EXIT_NANOS = TimeUnit.SECONDS.toNanos(10);
    private static final int MAX_EARLY_EXITS = 3;

    private Path script;
    private Process helper;
    private long helperStartedAt;
    private int earlyExits;
    private boolean gaveUp;

    private volatile NowPlaying latest;
    private volatile long latestAt;

    @Override
    public String name() {
        return "Windows media";
    }

    @Override
    public synchronized Optional<NowPlaying> poll() throws IOException {
        ensureHelperRunning();
        if (System.nanoTime() - latestAt > STALE_AFTER_NANOS) {
            return Optional.empty();
        }
        return Optional.ofNullable(latest);
    }

    private void ensureHelperRunning() throws IOException {
        if (gaveUp) {
            throw new IOException("The media helper keeps getting stopped, maybe by an antivirus. Not trying again.");
        }
        if (helper != null && helper.isAlive()) {
            return;
        }
        if (helper != null) {
            boolean early = System.nanoTime() - helperStartedAt < EARLY_EXIT_NANOS;
            earlyExits = early ? earlyExits + 1 : 0;
            helper = null;
            if (earlyExits >= MAX_EARLY_EXITS) {
                gaveUp = true;
                throw new IOException("The media helper stopped " + earlyExits + " times in a row right after starting");
            }
            Skysong.LOGGER.warn("Media helper exited, starting it again");
        }
        startHelper();
    }

    private void startHelper() throws IOException {
        if (script == null) {
            script = extractScript();
        }
        Process process = new ProcessBuilder(
                "powershell.exe", "-NoLogo", "-NoProfile", "-NonInteractive",
                "-ExecutionPolicy", "Bypass", "-File", script.toString())
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start();
        helper = process;
        helperStartedAt = System.nanoTime();

        Thread reader = new Thread(() -> readOutput(process), "Skysong media helper");
        reader.setDaemon(true);
        reader.start();
    }

    private void readOutput(Process process) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                long now = System.nanoTime();
                try {
                    latest = parseLine(line, now).orElse(null);
                    latestAt = now;
                } catch (JsonParseException | IllegalStateException e) {
                    Skysong.LOGGER.debug("Ignoring odd line from the media helper: {}", line);
                }
            }
        } catch (IOException e) {
            // The helper went away; poll() notices and restarts it.
        }
    }

    static Optional<NowPlaying> parseLine(String line, long sampledAtNanos) {
        JsonObject json = JsonParser.parseString(line).getAsJsonObject();
        if (json.has("error")) {
            Skysong.LOGGER.debug("Media helper: {}", json.get("error").getAsString());
        }
        if (!json.has("active") || !json.get("active").getAsBoolean()) {
            return Optional.empty();
        }
        String title = string(json, "title");
        if (title.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(new NowPlaying(
                title,
                string(json, "artist"),
                string(json, "album"),
                json.has("durationMs") ? json.get("durationMs").getAsLong() : 0,
                json.has("positionMs") ? json.get("positionMs").getAsLong() : 0,
                json.has("playing") && json.get("playing").getAsBoolean(),
                appName(string(json, "app")),
                sampledAtNanos));
    }

    /** Turns an app id like {@code Spotify.exe} or {@code SpotifyAB.SpotifyMusic_zpdnekdrzrea0!Spotify} into "Spotify". */
    static String appName(String appId) {
        String name = appId;
        int bang = name.lastIndexOf('!');
        if (bang >= 0) {
            name = name.substring(bang + 1);
        }
        if (name.regionMatches(true, name.length() - 4, ".exe", 0, 4)) {
            name = name.substring(0, name.length() - 4);
        }
        return name;
    }

    private static String string(JsonObject json, String key) {
        return json.has(key) && !json.get(key).isJsonNull() ? json.get(key).getAsString() : "";
    }

    private static Path extractScript() throws IOException {
        try (InputStream in = WindowsMediaSource.class.getResourceAsStream(SCRIPT_RESOURCE)) {
            if (in == null) {
                throw new IOException("The media helper script is missing from the mod jar");
            }
            Path file = Files.createTempFile("skysong-media", ".ps1");
            Files.copy(in, file, StandardCopyOption.REPLACE_EXISTING);
            file.toFile().deleteOnExit();
            return file;
        }
    }

    @Override
    public synchronized void close() {
        if (helper != null) {
            helper.destroy();
            helper = null;
        }
    }
}
