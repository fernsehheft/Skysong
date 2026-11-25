package io.github.fernsehheft.skysong;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.fernsehheft.skysong.config.SkysongConfig;
import io.github.fernsehheft.skysong.hud.HudEditScreen;
import io.github.fernsehheft.skysong.hud.SkysongHud;
import io.github.fernsehheft.skysong.lyrics.LocalLyricsFolder;
import io.github.fernsehheft.skysong.lyrics.LrclibClient;
import io.github.fernsehheft.skysong.lyrics.LyricsCache;
import io.github.fernsehheft.skysong.lyrics.LyricsPlayback;
import io.github.fernsehheft.skysong.lyrics.LyricsService;
import io.github.fernsehheft.skysong.media.DemoSource;
import io.github.fernsehheft.skysong.media.MediaSource;
import io.github.fernsehheft.skysong.media.MediaSources;
import io.github.fernsehheft.skysong.media.NowPlayingTracker;
import io.github.fernsehheft.skysong.render.WorldLyricsRenderer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Path;
import java.util.Optional;

public class SkysongClient implements ClientModInitializer {
    private static final String KEY_CATEGORY = "key.category.skysong";

    private static SkysongHud hud;

    /** The HUD, for the mixin that draws it while the game HUD is hidden. Empty if Skysong didn't start. */
    public static Optional<SkysongHud> hud() {
        return Optional.ofNullable(hud);
    }

    @Override
    public void onInitializeClient() {
        Path settingsFile = Skysong.dataDirectory().resolve("settings.json");
        SkysongConfig config = SkysongConfig.load(settingsFile);

        Optional<MediaSource> source = config.demoSong
                ? Optional.of(new DemoSource())
                : MediaSources.forThisSystem();
        NowPlayingTracker tracker = source.map(NowPlayingTracker::new).orElse(null);
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

        LyricsPlayback playback = new LyricsPlayback(tracker, lyrics, config);
        WorldLyricsRenderer worldLyrics = new WorldLyricsRenderer(playback, config);
        WorldRenderEvents.LAST.register(worldLyrics::render);

        hud = new SkysongHud(playback, config);
        HudRenderCallback.EVENT.register((graphics, tickDelta) -> hud.render(graphics));

        KeyMapping editHud = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.skysong.edit_hud", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K, KEY_CATEGORY));
        KeyMapping toggleLyrics = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.skysong.toggle_lyrics", InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), KEY_CATEGORY));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (editHud.consumeClick()) {
                client.setScreen(new HudEditScreen(hud, config, settingsFile));
            }
            while (toggleLyrics.consumeClick()) {
                config.worldLyrics = !config.worldLyrics;
                config.save(settingsFile);
            }
        });
    }
}
