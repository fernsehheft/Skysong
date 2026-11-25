package io.github.fernsehheft.skysong;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.fernsehheft.skysong.config.SkysongConfig;
import io.github.fernsehheft.skysong.gui.SkysongScreen;
import io.github.fernsehheft.skysong.hud.SkysongHud;
import io.github.fernsehheft.skysong.lyrics.LocalLyricsFolder;
import io.github.fernsehheft.skysong.lyrics.LrclibClient;
import io.github.fernsehheft.skysong.lyrics.LyricsCache;
import io.github.fernsehheft.skysong.lyrics.LyricsPlayback;
import io.github.fernsehheft.skysong.lyrics.LyricsService;
import io.github.fernsehheft.skysong.media.MediaSources;
import io.github.fernsehheft.skysong.media.NowPlayingTracker;
import io.github.fernsehheft.skysong.media.SwitchableSource;
import io.github.fernsehheft.skysong.render.WorldLyricsRenderer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.Screen;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Path;
import java.util.Optional;

public class SkysongClient implements ClientModInitializer {
    private static final String KEY_CATEGORY = "key.category.skysong";

    private static SkysongHud hud;
    private static SkysongConfig config;
    private static Path lyricsFolder;

    /** The HUD, for the mixin that draws it while the game HUD is hidden. Empty if Skysong didn't start. */
    public static Optional<SkysongHud> hud() {
        return Optional.ofNullable(hud);
    }

    /** A new settings screen that goes back to {@code parent}, or empty if Skysong didn't start. */
    public static Optional<Screen> settingsScreen(Screen parent) {
        return hud().map(started -> new SkysongScreen(parent, config, started, lyricsFolder));
    }

    @Override
    public void onInitializeClient() {
        config = SkysongConfig.load(Skysong.dataDirectory().resolve("settings.json"));
        lyricsFolder = Skysong.dataDirectory().resolve("lyrics");

        var systemPlayer = MediaSources.forThisSystem().orElse(null);
        if (systemPlayer == null) {
            Skysong.LOGGER.warn("No way to read the music player on {}, only the demo song will work",
                    System.getProperty("os.name"));
        }
        NowPlayingTracker tracker = new NowPlayingTracker(new SwitchableSource(systemPlayer, () -> config.demoSong));

        LocalLyricsFolder localLyrics = new LocalLyricsFolder(lyricsFolder);
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

        LyricsPlayback playback = new LyricsPlayback(tracker, lyrics, config);
        WorldLyricsRenderer worldLyrics = new WorldLyricsRenderer(playback, config);
        WorldRenderEvents.LAST.register(worldLyrics::render);

        hud = new SkysongHud(playback, config);
        HudRenderCallback.EVENT.register((graphics, tickDelta) -> hud.render(graphics));

        KeyMapping openSettings = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.skysong.settings", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K, KEY_CATEGORY));
        KeyMapping toggleLyrics = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.skysong.toggle_lyrics", InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), KEY_CATEGORY));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openSettings.consumeClick()) {
                client.setScreen(new SkysongScreen(null, config, hud, lyricsFolder));
            }
            while (toggleLyrics.consumeClick()) {
                config.worldLyrics = !config.worldLyrics;
                config.save();
            }
        });
    }
}
