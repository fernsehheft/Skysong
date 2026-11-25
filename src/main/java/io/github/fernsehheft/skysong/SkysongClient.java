package io.github.fernsehheft.skysong;

import net.fabricmc.api.ClientModInitializer;

public class SkysongClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        Skysong.LOGGER.info("Skysong is starting");
    }
}
