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
import io.github.fernsehheft.skysong.lyrics.SongOffsets;
import io.github.fernsehheft.skysong.media.MediaSources;
import io.github.fernsehheft.skysong.media.NowPlayingTracker;
import io.github.fernsehheft.skysong.media.SwitchableSource;
import io.github.fernsehheft.skysong.spotify.MusicControls;
import io.github.fernsehheft.skysong.spotify.Spotify;
import io.github.fernsehheft.skysong.render.WorldLyricsRenderer;
import io.github.fernsehheft.skysong.showcase.Showcase;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public class SkysongClient implements ClientModInitializer {
    private static final String KEY_CATEGORY = "key.category.skysong";
    public static final long SONG_OFFSET_STEP_MS = 250;

    private static SkysongHud hud;
    private static SkysongConfig config;
    private static Path lyricsFolder;
    private static Spotify spotify;

    /** The HUD, for the mixin that draws it while the game HUD is hidden. Empty if Skysong didn't start. */
    public static Optional<SkysongHud> hud() {
        return Optional.ofNullable(hud);
    }

    /** A new settings screen that goes back to {@code parent}, or empty if Skysong didn't start. */
    public static Optional<Screen> settingsScreen(Screen parent) {
        return hud().map(started -> new SkysongScreen(parent, config, started, lyricsFolder, spotify));
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
        spotify = new Spotify(Skysong.dataDirectory().resolve("spotify.json"));
        SwitchableSource source = new SwitchableSource(systemPlayer, spotify, config);
        NowPlayingTracker tracker = new NowPlayingTracker(source);
        MusicControls controls = new MusicControls(spotify,
                () -> source.active() == SwitchableSource.Active.SPOTIFY,
                SkysongClient::showControlResult);

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
            controls.stop();
            spotify.cancelLogin();
        });

        LyricsPlayback playback = new LyricsPlayback(tracker, lyrics, config,
                SongOffsets.load(Skysong.dataDirectory().resolve("song-offsets.json")));
        WorldLyricsRenderer worldLyrics = new WorldLyricsRenderer(playback, config);
        WorldRenderEvents.LAST.register(worldLyrics::render);

        hud = new SkysongHud(playback, config);
        Showcase.get().setPlayback(playback);
        HudRenderCallback.EVENT.register((graphics, tickDelta) -> hud.render(graphics));

        KeyMapping openSettings = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.skysong.settings", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K, KEY_CATEGORY));
        KeyMapping toggleLyrics = unboundKey("key.skysong.toggle_lyrics");
        KeyMapping showcase = unboundKey("key.skysong.showcase");
        KeyMapping lyricsEarlier = unboundKey("key.skysong.lyrics_earlier");
        KeyMapping lyricsLater = unboundKey("key.skysong.lyrics_later");
        Map<KeyMapping, MusicControls.Action> controlKeys = Map.of(
                unboundKey("key.skysong.play_pause"), MusicControls.Action.PLAY_PAUSE,
                unboundKey("key.skysong.next"), MusicControls.Action.NEXT,
                unboundKey("key.skysong.previous"), MusicControls.Action.PREVIOUS,
                unboundKey("key.skysong.volume_up"), MusicControls.Action.VOLUME_UP,
                unboundKey("key.skysong.volume_down"), MusicControls.Action.VOLUME_DOWN);
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openSettings.consumeClick()) {
                settingsScreen(null).ifPresent(client::setScreen);
            }
            while (toggleLyrics.consumeClick()) {
                config.worldLyrics = !config.worldLyrics;
                config.save();
            }
            Showcase.get().tick();
            while (showcase.consumeClick()) {
                Showcase.get().toggle();
            }
            while (lyricsEarlier.consumeClick()) {
                nudgeLyrics(playback, SONG_OFFSET_STEP_MS);
            }
            while (lyricsLater.consumeClick()) {
                nudgeLyrics(playback, -SONG_OFFSET_STEP_MS);
            }
            controlKeys.forEach((key, action) -> {
                while (key.consumeClick()) {
                    if (source.active() == SwitchableSource.Active.DEMO && action == MusicControls.Action.PLAY_PAUSE) {
                        // The demo song can be paused too, to try how the lyrics react.
                        source.demo().togglePause();
                        showControlResult(source.demo().paused()
                                ? MusicControls.Result.PAUSED : MusicControls.Result.PLAYING, "");
                    } else {
                        controls.run(action);
                    }
                }
            });
        });
    }

    /** Moves the lyrics of the current song earlier (positive) or later, and says so above the hotbar. */
    private static void nudgeLyrics(LyricsPlayback playback, long changeMs) {
        playback.now().ifPresent(moment -> {
            long offset = playback.songOffsets().adjust(moment.track(), changeMs);
            Minecraft.getInstance().gui.setOverlayMessage(describeSongOffset(offset), false);
        });
    }

    /** "Lyrics 0.50 s earlier for this song", or that they're on the song's own timing. */
    public static Component describeSongOffset(long offsetMs) {
        if (offsetMs == 0) {
            return Component.translatable("skysong.song_offset.none");
        }
        String seconds = String.format(Locale.ROOT, "%.2f", Math.abs(offsetMs) / 1000.0);
        return Component.translatable(offsetMs > 0 ? "skysong.song_offset.earlier" : "skysong.song_offset.later", seconds);
    }

    /** A key for something most players won't need; they can bind it in Controls. */
    private static KeyMapping unboundKey(String name) {
        return KeyBindingHelper.registerKeyBinding(new KeyMapping(
                name, InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), KEY_CATEGORY));
    }

    /** Tells the player above the hotbar how a music control went. */
    private static void showControlResult(MusicControls.Result result, String detail) {
        Component message = Component.translatable(
                "skysong.controls." + result.name().toLowerCase(Locale.ROOT), detail);
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.execute(() -> minecraft.gui.setOverlayMessage(message, false));
    }
}
