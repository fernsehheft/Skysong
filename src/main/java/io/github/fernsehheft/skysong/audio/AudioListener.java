package io.github.fernsehheft.skysong.audio;

import io.github.fernsehheft.skysong.Skysong;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Listens to what the computer plays, so lyrics and the showcase can move with the beat.
 * The sound comes from a small helper program as a stream of mono 16-bit samples at
 * {@value BeatAnalyzer#SAMPLE_RATE} Hz: on Windows a PowerShell script recording the
 * speakers (WASAPI loopback), on Linux {@code parec} reading the speakers' monitor. The
 * sound is only analysed on the spot, never saved or sent anywhere.
 */
public final class AudioListener {
    private static final String WINDOWS_SCRIPT = "/skysong/windows-audio.ps1";
    /** A helper that stops sooner than this after starting counts as a failed start. */
    private static final long EARLY_EXIT_NANOS = TimeUnit.SECONDS.toNanos(10);
    private static final int MAX_EARLY_EXITS = 3;

    private final BeatAnalyzer analyzer = new BeatAnalyzer();
    // Read by the render thread in state(), so it has to be visible across threads.
    private volatile Process helper;
    private long helperStartedAt;
    private int earlyExits;
    private boolean gaveUp;
    private Path windowsScript;
    /** The tempo last written to the log, to note when it changes. */
    private float loggedBpm;

    /** What the music sounds like now; {@link AudioState#NONE} while not listening. */
    public AudioState state() {
        return helper != null && helper.isAlive() ? analyzer.state() : AudioState.NONE;
    }

    /** Whether listening can work on this computer at all. */
    public static boolean supported() {
        return isWindows() || isLinux();
    }

    /**
     * Starts or stops listening as wanted, and restarts the helper if it stopped. Cheap
     * enough to call every tick.
     */
    public synchronized void update(boolean wanted) {
        if (!wanted) {
            stop();
            return;
        }
        if (helper != null && helper.isAlive()) {
            noteTempo();
            return;
        }
        if (gaveUp) {
            return;
        }
        if (helper != null) {
            boolean early = System.nanoTime() - helperStartedAt < EARLY_EXIT_NANOS;
            earlyExits = early ? earlyExits + 1 : 0;
            helper = null;
            if (earlyExits >= MAX_EARLY_EXITS) {
                gaveUp = true;
                Skysong.LOGGER.warn("The audio helper stopped {} times right after starting; not listening to the music this session", earlyExits);
                return;
            }
        }
        start();
    }

    private void noteTempo() {
        AudioState state = analyzer.state();
        if (!state.silent() && state.bpm() > 0 && Math.abs(state.bpm() - loggedBpm) >= 4) {
            loggedBpm = state.bpm();
            Skysong.LOGGER.info("Tempo: {} BPM", Math.round(state.bpm()));
        }
    }

    public synchronized void stop() {
        if (helper != null) {
            helper.destroy();
            helper = null;
        }
    }

    private void start() {
        try {
            if (!supported()) {
                gaveUp = true;
                return;
            }
            if (windowsScript == null && isWindows()) {
                windowsScript = extract(WINDOWS_SCRIPT);
            }
            List<String> command = command(windowsScript);
            Process process = new ProcessBuilder(command)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
            helper = process;
            helperStartedAt = System.nanoTime();
            Thread reader = new Thread(() -> read(process), "Skysong audio");
            reader.setDaemon(true);
            reader.start();
            Skysong.LOGGER.info("Listening to the music to find the beat");
        } catch (IOException e) {
            // Most likely parec isn't installed; there's no point in trying again.
            gaveUp = true;
            Skysong.LOGGER.warn("Couldn't start listening to the music: {}", e.getMessage());
        }
    }

    private void read(Process process) {
        byte[] bytes = new byte[2048];
        float[] samples = new float[bytes.length / 2];
        int carry = -1;
        try (InputStream in = process.getInputStream()) {
            int read;
            while ((read = in.read(bytes)) > 0) {
                int count = 0;
                int i = 0;
                if (carry >= 0) {
                    // The second byte of a sample split across two reads.
                    samples[count++] = (short) ((bytes[0] << 8) | carry) / 32768f;
                    carry = -1;
                    i = 1;
                }
                for (; i + 1 < read; i += 2) {
                    samples[count++] = (short) ((bytes[i + 1] << 8) | (bytes[i] & 0xFF)) / 32768f;
                }
                if (i < read) {
                    carry = bytes[i] & 0xFF;
                }
                analyzer.accept(samples, count, System.nanoTime());
            }
        } catch (IOException e) {
            // The helper went away; update() starts it again.
        }
    }

    /** The helper to run; only called where {@link #supported()} is true. */
    private static List<String> command(Path windowsScript) {
        if (isWindows()) {
            return List.of("powershell.exe", "-NoLogo", "-NoProfile", "-NonInteractive",
                    "-ExecutionPolicy", "Bypass", "-File", windowsScript.toString());
        }
        // PulseAudio and PipeWire both offer the speakers' sound as a "monitor".
        return List.of("parec", "--device=@DEFAULT_MONITOR@", "--raw", "--format=s16le",
                "--rate=" + BeatAnalyzer.SAMPLE_RATE, "--channels=1", "--latency-msec=30");
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows");
    }

    /** macOS can't record the speakers without extra software, so it's left out. */
    private static boolean isLinux() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("linux");
    }

    private static Path extract(String resource) throws IOException {
        try (InputStream in = AudioListener.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IOException("The audio helper is missing from the mod jar");
            }
            Path file = Files.createTempFile("skysong-audio", ".ps1");
            Files.copy(in, file, StandardCopyOption.REPLACE_EXISTING);
            file.toFile().deleteOnExit();
            return file;
        }
    }
}
