package io.github.fernsehheft.skysong;

import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

public final class Skysong {
    public static final String MOD_ID = "skysong";
    public static final Logger LOGGER = LoggerFactory.getLogger("Skysong");

    private Skysong() {
    }

    public static String version() {
        return FabricLoader.getInstance().getModContainer(MOD_ID)
                .map(mod -> mod.getMetadata().getVersion().getFriendlyString())
                .orElse("unknown");
    }

    /** {@code .minecraft/config/skysong}, where all our files live. */
    public static Path dataDirectory() {
        return FabricLoader.getInstance().getConfigDir().resolve(MOD_ID);
    }
}
