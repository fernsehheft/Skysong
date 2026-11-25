package io.github.fernsehheft.skysong.media;

import java.io.IOException;
import java.util.Optional;
import java.util.function.BooleanSupplier;

/**
 * Listens to the system's music player, or to the demo song while that's switched on.
 * Switching takes effect at the next poll, so the demo can be tried from the settings
 * without restarting the game.
 */
public final class SwitchableSource implements MediaSource {
    private final MediaSource system;
    private final DemoSource demo = new DemoSource();
    private final BooleanSupplier demoWanted;

    /**
     * @param system the music player of this OS, or null if we can't read it here
     */
    public SwitchableSource(MediaSource system, BooleanSupplier demoWanted) {
        this.system = system;
        this.demoWanted = demoWanted;
    }

    @Override
    public String name() {
        return demoWanted.getAsBoolean() || system == null ? demo.name() : system.name();
    }

    @Override
    public Optional<NowPlaying> poll() throws IOException {
        if (demoWanted.getAsBoolean()) {
            demo.restartIfStopped();
            return demo.poll();
        }
        demo.stop();
        return system == null ? Optional.empty() : system.poll();
    }

    @Override
    public void close() {
        if (system != null) {
            system.close();
        }
    }
}
