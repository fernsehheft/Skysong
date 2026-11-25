package io.github.fernsehheft.skysong;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.fernsehheft.skysong.hud.NowPlayingOverlay;
import io.github.fernsehheft.skysong.lyrics.LocalLyricsFolder;
import io.github.fernsehheft.skysong.lyrics.LrclibClient;
import io.github.fernsehheft.skysong.lyrics.LyricsCache;
import io.github.fernsehheft.skysong.lyrics.LyricsService;
import io.github.fernsehheft.skysong.media.MediaSources;
import io.github.fernsehheft.skysong.media.NowPlayingTracker;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

public class SkysongClient implements ClientModInitializer {
    private static final String KEY_CATEGORY = "key.category.skysong";

    @Override
    public void onInitializeClient() {
        NowPlayingTracker tracker = MediaSources.forThisSystem()
                .map(NowPlayingTracker::new)
                .orElse(null);
        if (tracker == null) {
            Skysong.LOGGER.warn("No way to read the music player on {}, Skysong won't know what's playing",
                    System.getProperty("os.name"));
            return;
        }

        LocalLyricsFolder localLyrics = new LocalLyricsFolder(Skysong.dataDirectory().resolve("lyrics"));
        localLyrics.prepare();
        LyricsService lyrics = new LyricsService(
                localLyrics,
                new LyricsCache(Skysong.dataDirectory().resolve("cache")),
                new LrclibClient("Skysong/" + Skysong.version() + " (https://github.com/fernsehheft/Skysong)"));
        tracker.onTrackChange(lyrics::onTrackChanged);

        tracker.start();
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            tracker.stop();
            lyrics.stop();
        });

        NowPlayingOverlay overlay = new NowPlayingOverlay(tracker, lyrics);
        HudRenderCallback.EVENT.register((graphics, tickDelta) -> overlay.render(graphics));

        KeyMapping toggleOverlay = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.skysong.toggle_overlay", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K, KEY_CATEGORY));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (toggleOverlay.consumeClick()) {
                overlay.toggle();
            }
        });
    }
}
