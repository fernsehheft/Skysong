package io.github.fernsehheft.skysong.media;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Runs a short-lived command and collects what it printed. */
record ProcessOutput(int exitCode, String stdout) {

    static ProcessOutput run(long timeoutMs, List<String> command) throws IOException {
        Process process = new ProcessBuilder(command)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start();
        try {
            if (!process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                throw new IOException(command.get(0) + " didn't answer in time");
            }
        } catch (InterruptedException e) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while waiting for " + command.get(0), e);
        }
        try (InputStream in = process.getInputStream()) {
            return new ProcessOutput(process.exitValue(), new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    boolean succeeded() {
        return exitCode == 0;
    }
}
