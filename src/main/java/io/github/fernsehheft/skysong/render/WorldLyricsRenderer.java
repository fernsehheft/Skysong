package io.github.fernsehheft.skysong.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import io.github.fernsehheft.skysong.config.ColorMode;
import io.github.fernsehheft.skysong.config.SkysongConfig;
import io.github.fernsehheft.skysong.gui.PreviewArea;
import io.github.fernsehheft.skysong.lyrics.LyricLine;
import io.github.fernsehheft.skysong.lyrics.Lyrics;
import io.github.fernsehheft.skysong.lyrics.LyricsPlayback;
import io.github.fernsehheft.skysong.lyrics.SongStructure;
import io.github.fernsehheft.skysong.lyrics.TrackQuery;
import io.github.fernsehheft.skysong.lyrics.TimedWord;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Draws the line that is being sung as text floating in the world, and lets the
 * previous line fade away.
 */
public final class WorldLyricsRenderer {
    /** Size of one font pixel in blocks, for lyrics 4 blocks away at size 1.0. */
    private static final float PIXEL_SIZE = 0.032f;
    private static final float REFERENCE_DISTANCE = 4.0f;
    private static final int MAX_ROW_WIDTH = 180;
    private static final int ROW_GAP = 3;
    private static final double MAX_SCATTER_DEGREES = 30;
    /**
     * Lines in the sky are written up where the clouds are, far away, like skywriting. They're
     * at least this many degrees above the horizon; looking further up takes them along.
     */
    private static final double SKY_MIN_ELEVATION = 20;
    private static final double SKY_MAX_ELEVATION = 70;
    /** Even near the cloud layer (or above it), lines stay at least this far overhead. */
    private static final double SKY_MIN_HEIGHT = 40;
    /** Keeps lines well inside the view distance when the clouds are far above. */
    private static final double SKY_MAX_HORIZONTAL = 300;
    /** Where there are no clouds (Nether, End), lines go this far above the player. */
    private static final double SKY_HEIGHT_WITHOUT_CLOUDS = 60;
    /** Their size grows with the distance; at this, a long line still fits the view. */
    private static final float SKY_SIZE = 0.8f;

    private static final long MS_PER_TYPED_LETTER = 40;
    private static final float CHORUS_SIZE = 1.15f;
    private static final float TITLE_SCALE = 1.25f;
    private static final float TITLE_JUMP = 0.3f;
    private static final long TITLE_JUMP_MS = 450;
    private static final float BACKING_SCALE = 0.8f;
    private static final float BACKING_ALPHA = 0.6f;
    private static final float HELD_GROWTH = 0.18f;
    private static final long GLITCH_MS = 180;
    private static final String GLITCH_CHARACTERS = "#@%&*?!<>/\\=~";
    private static final int DEPTH_LAYERS = 4;
    private static final float DEPTH_STEP = 0.35f;
    private static final float GLOW_CORE_WHITENESS = 0.6f;
    /** Rim thickness and how far it sits behind the word, in font pixels; positive z is away from the camera. */
    private static final float GLOW_WIDTH = 0.7f;
    private static final float GLOW_BEHIND = 0.15f;
    private static final int GLOW_MIN_ALPHA = 240;
    /** Sparks only for words sung within this long, not for ones that were over before the line showed up. */
    private static final long SPARKLE_WINDOW_MS = 300;

    private static final long APPEAR_MS = 300;
    private static final float PAUSE_FADE_MS = 400;
    private static final long LEAVE_MS = 450;
    /** A line disappears this long after its last word, if the next one takes a while. */
    private static final long LINGER_MS = 4000;
    /** Unsung words in karaoke mode are this see-through. */
    private static final float UNSUNG_ALPHA = 0.55f;

    private final LyricsPlayback playback;
    private final SkysongConfig config;
    private final Random random = new Random();

    private Lyrics shownLyrics;
    private int shownIndex = -1;
    private ShownLine current;
    private ShownLine leaving;

    /**
     * Fades everything out while the music is paused and back in when it plays again. It
     * runs on the real clock, as the song's clock stands still during a pause.
     */
    private float playingVisibility = 1;
    /** Which lines of the current song come back, worked out once per song. */
    private Lyrics repeatedLinesOf;
    private boolean[] repeatedLines;
    private long lastFrameNanos = System.nanoTime();
    private boolean hiddenByPause;

    /** A line that is on screen, with where it was put. */
    private static final class ShownLine {
        final LyricLine line;
        final List<LineLayout.PlacedWord> words;
        final Vec3 position;
        final float pixelSize;
        final long visibleUntil;
        /** Per word: part of the song's title, background vocals, how long it's held (null without word timing). */
        final boolean[] title;
        final boolean[] backing;
        final long[] holdMs;
        long leftAt;
        /** How many of the words have already had their sparks. */
        int sparkedWords;

        ShownLine(LyricLine line, List<LineLayout.PlacedWord> words, Vec3 position, float pixelSize, long visibleUntil,
                  boolean[] title, boolean[] backing, long[] holdMs) {
            this.line = line;
            this.words = words;
            this.position = position;
            this.pixelSize = pixelSize;
            this.visibleUntil = visibleUntil;
            this.title = title;
            this.backing = backing;
            this.holdMs = holdMs;
        }
    }

    public WorldLyricsRenderer(LyricsPlayback playback, SkysongConfig config) {
        this.playback = playback;
        this.config = config;
    }

    public void render(WorldRenderContext context) {
        LyricsPlayback.Moment moment = playback.now().orElse(null);
        if (moment == null || moment.lyrics() == null || !config.worldLyrics) {
            clear();
            return;
        }

        if (!followPlayState(moment.track().playing())) {
            return;
        }

        Lyrics song = moment.lyrics();
        long time = moment.timeMs();
        int index = moment.lineIndex();
        if (song != shownLyrics || index != shownIndex) {
            switchLine(song, index, moment.lineEndMs(), time, moment.track().title(), context);
        }
        if (current != null && time >= current.visibleUntil) {
            startLeaving(current, current.visibleUntil);
            current = null;
        }
        if (leaving != null && time - leaving.leftAt > LEAVE_MS) {
            leaving = null;
        }
        if (current == null && leaving == null) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        PoseStack pose = context.matrixStack();
        Camera camera = context.camera();
        if (leaving != null) {
            draw(pose, buffers, minecraft.font, camera, leaving, time);
        }
        if (current != null) {
            draw(pose, buffers, minecraft.font, camera, current, time);
        }
        if (config.placement == Placement.SKY) {
            // Minecraft's see-through text doesn't switch the depth test off, it expects it to
            // be off already. Here it's still on, so the sky writing would hide behind trees.
            RenderSystem.disableDepthTest();
            buffers.endBatch();
            RenderSystem.enableDepthTest();
        } else {
            buffers.endBatch();
        }
    }

    /**
     * Fades the lyrics out on pause and in again when the music plays on.
     *
     * @return false while there's nothing to draw because of a pause
     */
    private boolean followPlayState(boolean playing) {
        long now = System.nanoTime();
        float step = (now - lastFrameNanos) / 1_000_000f / PAUSE_FADE_MS;
        lastFrameNanos = now;
        playingVisibility = playing ? Math.min(1, playingVisibility + step) : Math.max(0, playingVisibility - step);

        if (!playing && playingVisibility == 0) {
            hiddenByPause = true;
            return false;
        }
        if (playing && hiddenByPause) {
            // After a pause the player may be anywhere: start over with the line that's
            // playing now, put in front of where they're looking now.
            hiddenByPause = false;
            clear();
        }
        return true;
    }

    private void switchLine(Lyrics song, int index, long end, long time, String trackTitle, WorldRenderContext context) {
        if (current != null) {
            startLeaving(current, time);
        }
        shownLyrics = song;
        shownIndex = index;
        current = null;
        if (index < 0) {
            return;
        }

        LyricLine line = song.lines().get(index);
        if (line.isBreak()) {
            return;
        }
        List<TimedWord> words = TimedWord.of(line, end, config.estimateWordTiming);
        if (words.isEmpty()) {
            return;
        }
        long lastWord = words.get(words.size() - 1).startMs();

        int count = words.size();
        boolean react = config.reactToSong;
        List<String> texts = words.stream().map(TimedWord::text).toList();
        boolean[] title = react
                ? SongStructure.titleWords(texts, new TrackQuery(trackTitle, "", "", 0).cleanTitle())
                : new boolean[count];
        boolean[] backing = react ? SongStructure.backingWords(texts) : new boolean[count];
        long[] holdMs = react && song.wordTimed() ? SongStructure.wordLengths(words, end) : null;
        if (react && song != repeatedLinesOf) {
            repeatedLinesOf = song;
            repeatedLines = SongStructure.repeatedLines(song);
        }
        boolean chorus = react && repeatedLines[index];

        // Words that will be drawn bigger or smaller get more or less room in the line.
        Font font = Minecraft.getInstance().font;
        Map<TimedWord, Integer> indexOf = new IdentityHashMap<>();
        for (int i = 0; i < count; i++) {
            indexOf.put(words.get(i), i);
        }
        List<LineLayout.PlacedWord> placed = LineLayout.layoutWithWidths(words, word -> {
            int i = indexOf.get(word);
            return Math.round(config.font.width(font, word.text())
                    * roomFor(title[i], backing[i], holdMs != null && holdMs[i] > SongStructure.HELD_NOTE_MS));
        }, config.font.width(font, " "), MAX_ROW_WIDTH, font.lineHeight + ROW_GAP);

        Vec3 eye = context.camera().getPosition();
        Vec3 position = placeLine(context.camera(), context.world());
        float pixelSize = (float) (PIXEL_SIZE * config.size * eye.distanceTo(position) / REFERENCE_DISTANCE);
        if (config.placement == Placement.SKY) {
            pixelSize *= SKY_SIZE;
        }
        if (chorus) {
            pixelSize *= CHORUS_SIZE;
        }
        // Only for a line that starts now, not when joining in the middle of one.
        if (config.beatZoom && time - line.startMs() < APPEAR_MS) {
            BeatZoom.kick();
        }
        current = new ShownLine(line, placed, position, pixelSize, Math.min(end, lastWord + LINGER_MS),
                title, backing, holdMs);
    }

    private void startLeaving(ShownLine line, long time) {
        line.leftAt = time;
        leaving = line;
    }

    private void clear() {
        shownLyrics = null;
        shownIndex = -1;
        current = null;
        leaving = null;
    }

    private Vec3 placeLine(Camera camera, ClientLevel level) {
        if (config.placement == Placement.SKY) {
            return skyPlace(camera, level);
        }
        double yaw = camera.getYRot();
        double distance = config.distance;
        double height = 0.2;
        if (config.placement == Placement.SCATTERED) {
            // Stay well inside the default field of view, so no line ends up half off screen.
            yaw += (random.nextDouble() * 2 - 1) * MAX_SCATTER_DEGREES;
            distance *= 0.85 + random.nextDouble() * 0.4;
            height = -0.3 + random.nextDouble() * 1.1;
        }

        yaw += previewYawShift();
        double radians = Math.toRadians(yaw);
        Vec3 direction = new Vec3(-Math.sin(radians), 0, Math.cos(radians));
        Vec3 eye = camera.getPosition();
        Vec3 target = eye.add(direction.scale(distance)).add(0, height, 0);
        return inFrontOfWalls(level, eye, target);
    }

    /**
     * High up in the direction the player faces. Nothing is pulled out of the way here: the
     * sky is far beyond any wall, and a tree in between doesn't make the lyrics any less "up there".
     */
    private static Vec3 skyPlace(Camera camera, ClientLevel level) {
        Vec3 eye = camera.getPosition();
        double cloudHeight = level == null ? Double.NaN : level.effects().getCloudHeight();
        double above = Double.isNaN(cloudHeight)
                ? SKY_HEIGHT_WITHOUT_CLOUDS
                : Math.max(SKY_MIN_HEIGHT, cloudHeight - eye.y);
        // Minecraft's pitch is negative when looking up.
        double elevation = Math.toRadians(Math.max(SKY_MIN_ELEVATION, Math.min(SKY_MAX_ELEVATION, -camera.getXRot())));
        double horizontal = Math.min(SKY_MAX_HORIZONTAL, above / Math.tan(elevation));
        double yaw = Math.toRadians(camera.getYRot() + previewYawShift());
        return eye.add(-Math.sin(yaw) * horizontal, above, Math.cos(yaw) * horizontal);
    }

    /**
     * While the settings cover the left of the screen, new lines are turned to the right
     * by the angle at which the middle of the still visible part is seen.
     */
    private static double previewYawShift() {
        Minecraft minecraft = Minecraft.getInstance();
        float shift = PreviewArea.centerShift(minecraft.getWindow().getGuiScaledWidth());
        if (shift == 0) {
            return 0;
        }
        double aspect = (double) minecraft.getWindow().getWidth() / Math.max(1, minecraft.getWindow().getHeight());
        double halfVerticalFov = Math.toRadians(minecraft.options.fov().get() / 2.0);
        return Math.toDegrees(Math.atan(shift * Math.tan(halfVerticalFov) * aspect));
    }

    /** Pulls the spot towards the player if a wall is in the way, so the text isn't hidden in it. */
    private static Vec3 inFrontOfWalls(ClientLevel level, Vec3 eye, Vec3 target) {
        Entity viewer = Minecraft.getInstance().getCameraEntity();
        if (level == null || viewer == null) {
            return target;
        }
        HitResult hit = level.clip(new ClipContext(eye, target, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, viewer));
        if (hit.getType() == HitResult.Type.MISS) {
            return target;
        }
        double toWall = eye.distanceTo(hit.getLocation());
        Vec3 direction = target.subtract(eye).normalize();
        return eye.add(direction.scale(Math.max(0.6, toWall - 0.3)));
    }

    /** How one word looks in one frame. */
    private static final class WordLook {
        String text;
        int color;
        float alpha;
        float scale = 1;
        float offsetX;
        float offsetY;
    }

    private void draw(PoseStack pose, MultiBufferSource buffers, Font font, Camera camera, ShownLine shown, long time) {
        float appear = Easing.easeOut(Easing.progress(time - shown.line.startMs(), APPEAR_MS));
        float leave = shown == leaving ? Easing.easeOut(Easing.progress(time - shown.leftAt, LEAVE_MS)) : 0;
        float lineAlpha = appear * (1 - leave) * playingVisibility;
        if (lineAlpha <= 0.02f) {
            return;
        }

        // In text space y points down, so a positive offset is below the line's spot.
        // A leaving line always drifts up, so it clears the way for the next one,
        // which in the AHEAD placement shows up at the very same spot.
        float lineOffsetY = -leave * 12;
        if (config.animation == Animation.RISE) {
            lineOffsetY = (1 - appear) * 12 - leave * 14;
        }

        Vec3 camPos = camera.getPosition();
        pose.pushPose();
        pose.translate(shown.position.x - camPos.x, shown.position.y - camPos.y, shown.position.z - camPos.z);
        pose.mulPose(camera.rotation());
        pose.scale(-shown.pixelSize, -shown.pixelSize, shown.pixelSize);

        WordLook look = new WordLook();
        for (int i = 0; i < shown.words.size(); i++) {
            LineLayout.PlacedWord placed = shown.words.get(i);
            long sinceSung = time - placed.word().startMs();
            look.text = placed.word().text();
            look.alpha = lineAlpha;
            look.scale = 1;
            look.offsetX = 0;
            look.offsetY = lineOffsetY;
            int base = LyricColors.base(config, i, shown.words.size(), System.currentTimeMillis());
            if (animate(look, i, base, sinceSung, time)) {
                react(look, shown, i, sinceSung);
                drawWord(pose, buffers, font, placed, look);
            }
        }
        pose.popPose();

        if (shown != leaving && config.sparkles) {
            sparkle(camera, shown, time);
        }
    }

    /**
     * Works out how a word looks for the chosen animation.
     *
     * @param text the word's colour when it isn't lit up, from the colour mode
     * @return false if the word isn't visible at all right now
     */
    private boolean animate(WordLook look, int index, int text, long sinceSung, long time) {
        int highlight = config.highlightRgb();
        switch (config.animation) {
            case KARAOKE -> {
                float sung = Easing.progress(sinceSung, 120);
                look.color = Easing.lerpColor(text, highlight, sung);
                look.alpha *= UNSUNG_ALPHA + (1 - UNSUNG_ALPHA) * sung;
                if (sinceSung >= 0) {
                    look.scale += 0.15f * Easing.bump(Easing.progress(sinceSung, 220));
                }
            }
            case FADE -> {
                float in = Easing.easeOut(Easing.progress(sinceSung, 250));
                look.alpha *= in;
                look.offsetY += (1 - in) * 4;
                // Flash in the highlight colour, then settle on the normal one.
                look.color = Easing.lerpColor(highlight, text, Easing.progress(sinceSung - 200, 500));
            }
            case RISE -> look.color = Easing.lerpColor(text, highlight, Easing.progress(sinceSung, 150));
            case WAVE -> {
                look.color = Easing.lerpColor(text, highlight, Easing.progress(sinceSung, 150));
                look.offsetY += (float) Math.sin(time / 1000.0 * 4 + index * 0.8) * 1.5f;
            }
            case TYPEWRITER -> {
                if (sinceSung < 0) {
                    return false;
                }
                int letters = look.text.length();
                int shownLetters = (int) Math.ceil(letters * Easing.progress(sinceSung, letters * MS_PER_TYPED_LETTER));
                look.text = look.text.substring(0, Math.max(1, shownLetters));
                look.color = Easing.lerpColor(highlight, text, Easing.progress(sinceSung - letters * MS_PER_TYPED_LETTER, 600));
            }
            case GLITCH -> {
                if (sinceSung < 0) {
                    return false;
                }
                if (sinceSung < GLITCH_MS) {
                    // A different jumble every few frames, the same within them.
                    long seed = time / 40 * 31 + index;
                    look.text = scramble(look.text, seed);
                    look.color = (seed & 1) == 0 ? highlight : text;
                    look.offsetX = (hash(seed) - 0.5f) * 3;
                } else {
                    look.color = Easing.lerpColor(highlight, text, Easing.progress(sinceSung - GLITCH_MS, 600));
                }
            }
            case DROP -> {
                if (sinceSung < 0) {
                    return false;
                }
                float fall = Easing.progress(sinceSung, 420);
                look.offsetY -= (1 - Easing.bounceOut(fall)) * 18;
                look.alpha *= Math.min(1, fall * 3);
                look.color = Easing.lerpColor(highlight, text, Easing.progress(sinceSung - 400, 600));
            }
            case ZOOM -> {
                if (sinceSung < 0) {
                    return false;
                }
                float in = Easing.easeOut(Easing.progress(sinceSung, 260));
                look.scale = 1 + (1 - in) * 1.4f;
                look.alpha *= in;
                look.color = Easing.lerpColor(highlight, text, Easing.progress(sinceSung - 200, 600));
            }
            default -> throw new IllegalStateException("Unknown animation " + config.animation);
        }
        boolean glitching = config.animation == Animation.GLITCH && sinceSung >= 0 && sinceSung < GLITCH_MS;
        if (config.colorMode != ColorMode.SINGLE && !glitching) {
            look.color = LyricColors.afterSinging(text, highlight, sinceSung);
        }
        return true;
    }

    /** Room a word gets in the line, matching how big {@link #react} draws it at most. */
    private static float roomFor(boolean title, boolean backing, boolean held) {
        float room = 1;
        if (title) {
            room *= TITLE_SCALE;
        }
        if (backing) {
            room *= BACKING_SCALE;
        }
        if (held) {
            room *= 1 + HELD_GROWTH;
        }
        return room;
    }

    /**
     * Makes the words react to the song: the title stands out and jumps when it's sung,
     * background vocals step back, and a held note grows for as long as it's held.
     */
    private void react(WordLook look, ShownLine shown, int index, long sinceSung) {
        if (shown.title[index]) {
            look.scale *= TITLE_SCALE;
            if (sinceSung >= 0) {
                look.scale *= 1 + TITLE_JUMP * Easing.bump(Easing.progress(sinceSung, TITLE_JUMP_MS));
                look.color = config.highlightRgb();
            }
        }
        if (shown.backing[index]) {
            look.scale *= BACKING_SCALE;
            look.alpha *= BACKING_ALPHA;
        }
        if (shown.holdMs != null && shown.holdMs[index] > SongStructure.HELD_NOTE_MS && sinceSung >= 0) {
            float held = Easing.progress(sinceSung, shown.holdMs[index]);
            // Grows while the note is held and settles back once the next word comes.
            look.scale *= 1 + HELD_GROWTH * (sinceSung <= shown.holdMs[index] ? Easing.easeOut(held) : 0);
        }
    }

    private void drawWord(PoseStack pose, MultiBufferSource buffers, Font font, LineLayout.PlacedWord placed, WordLook look) {
        // Minecraft draws text with an alpha below 4 as fully opaque, so skip those.
        int alphaByte = Math.round(look.alpha * 255);
        if (alphaByte < 5) {
            return;
        }
        // The word is centred in its room, which can be wider than the word itself. Measured on
        // the whole word, so a word being typed out grows from its first letter.
        float halfWidth = config.font.width(font, placed.word().text()) / 2f;
        float halfHeight = font.lineHeight / 2f;
        pose.pushPose();
        pose.translate(placed.x() + placed.width() / 2f + look.offsetX, placed.y() + halfHeight + look.offsetY, 0);
        pose.scale(look.scale, look.scale, 1);

        if (config.depth3d) {
            // Darker copies stacked away from the viewer give the letters some thickness.
            // After the flip in draw(), positive z in text space points away from the camera.
            int side = withAlpha(darken(look.color, 0.45f), alphaByte);
            for (int layer = DEPTH_LAYERS; layer >= 1; layer--) {
                pose.pushPose();
                pose.translate(layer * DEPTH_STEP, layer * DEPTH_STEP, layer * DEPTH_STEP);
                text(font, look.text, -halfWidth, -halfHeight, side, false, pose, buffers);
                pose.popPose();
            }
        }
        if (config.glow && alphaByte >= GLOW_MIN_ALPHA) {
            // Like a neon tube: a bright core with a rim in the word's colour around it. The rim
            // is eight solid copies set a little behind the word, so it can't cover the core.
            // (Minecraft's own glowing sign outline ends up in front of the text in this view.)
            int rim = withAlpha(config.colorMode == ColorMode.SINGLE ? config.highlightRgb() : look.color, alphaByte);
            int core = Easing.lerpColor(look.color, 0xFFFFFF, GLOW_CORE_WHITENESS);
            pose.pushPose();
            pose.translate(0, 0, GLOW_BEHIND);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    if (dx != 0 || dy != 0) {
                        text(font, look.text, -halfWidth + dx * GLOW_WIDTH, -halfHeight + dy * GLOW_WIDTH, rim, false,
                                pose, buffers);
                    }
                }
            }
            pose.popPose();
            text(font, look.text, -halfWidth, -halfHeight, withAlpha(core, alphaByte), false, pose, buffers);
        } else {
            // Also while a glowing word fades in or out: the outline can't be see-through.
            text(font, look.text, -halfWidth, -halfHeight, withAlpha(look.color, alphaByte), !config.depth3d, pose, buffers);
        }
        pose.popPose();
    }

    private void text(Font font, String text, float x, float y, int argb, boolean shadow,
                      PoseStack pose, MultiBufferSource buffers) {
        // Writing in the sky stays in front of trees and hills, like a nametag seen through a wall.
        Font.DisplayMode mode = config.placement == Placement.SKY ? Font.DisplayMode.SEE_THROUGH : Font.DisplayMode.NORMAL;
        font.drawInBatch(config.font.text(text), x, y, argb, shadow, pose.last().pose(), buffers, mode, 0,
                LightTexture.FULL_BRIGHT);
    }

    /** Sends a few sparks off every word at the moment it's sung. */
    private void sparkle(Camera camera, ShownLine shown, long time) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        Font font = Minecraft.getInstance().font;
        Vec3 camPos = camera.getPosition();
        while (shown.sparkedWords < shown.words.size()) {
            LineLayout.PlacedWord placed = shown.words.get(shown.sparkedWords);
            long sinceSung = time - placed.word().startMs();
            if (sinceSung < 0) {
                break;
            }
            shown.sparkedWords++;
            // Words that were sung before the line showed up (after seeking) stay quiet.
            if (sinceSung > SPARKLE_WINDOW_MS) {
                continue;
            }

            // The same transformation the text is drawn with, but starting from world space.
            Vector3f center = new Matrix4f()
                    .translation((float) (shown.position.x - camPos.x), (float) (shown.position.y - camPos.y),
                            (float) (shown.position.z - camPos.z))
                    .rotate(camera.rotation())
                    .scale(-shown.pixelSize, -shown.pixelSize, shown.pixelSize)
                    .transformPosition(new Vector3f(placed.x() + placed.width() / 2f, placed.y() + font.lineHeight / 2f, 0));
            double x = camPos.x + center.x();
            double y = camPos.y + center.y();
            double z = camPos.z + center.z();

            int rgb = config.highlightRgb();
            DustParticleOptions dust = new DustParticleOptions(
                    new Vector3f(((rgb >> 16) & 0xFF) / 255f, ((rgb >> 8) & 0xFF) / 255f, (rgb & 0xFF) / 255f), 0.7f);
            double spread = placed.width() * shown.pixelSize / 2;
            for (int i = 0; i < 4; i++) {
                level.addParticle(dust, x + (random.nextDouble() - 0.5) * spread * 2,
                        y + (random.nextDouble() - 0.3) * 0.25, z + (random.nextDouble() - 0.5) * spread, 0, 0, 0);
            }
            level.addParticle(ParticleTypes.END_ROD, x, y, z,
                    (random.nextDouble() - 0.5) * 0.04, 0.02 + random.nextDouble() * 0.03, (random.nextDouble() - 0.5) * 0.04);
        }
    }

    private static String scramble(String word, long seed) {
        StringBuilder jumbled = new StringBuilder(word.length());
        for (int i = 0; i < word.length(); i++) {
            int pick = (int) (hash(seed * 7 + i) * GLITCH_CHARACTERS.length());
            jumbled.append(GLITCH_CHARACTERS.charAt(Math.min(pick, GLITCH_CHARACTERS.length() - 1)));
        }
        return jumbled.toString();
    }

    /** A number between 0 and 1 that looks random but is always the same for the same seed. */
    private static float hash(long seed) {
        long mixed = seed * 0x9E3779B97F4A7C15L;
        mixed ^= mixed >>> 29;
        return (mixed >>> 40) / (float) (1L << 24);
    }

    private static int withAlpha(int rgb, int alphaByte) {
        return (alphaByte << 24) | (rgb & 0xFFFFFF);
    }

    private static int darken(int rgb, float factor) {
        int r = Math.round(((rgb >> 16) & 0xFF) * factor);
        int g = Math.round(((rgb >> 8) & 0xFF) * factor);
        int b = Math.round((rgb & 0xFF) * factor);
        return (r << 16) | (g << 8) | b;
    }
}
