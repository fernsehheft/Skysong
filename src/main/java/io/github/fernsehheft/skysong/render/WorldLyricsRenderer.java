package io.github.fernsehheft.skysong.render;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.fernsehheft.skysong.config.SkysongConfig;
import io.github.fernsehheft.skysong.gui.PreviewArea;
import io.github.fernsehheft.skysong.lyrics.LyricLine;
import io.github.fernsehheft.skysong.lyrics.Lyrics;
import io.github.fernsehheft.skysong.lyrics.LyricsPlayback;
import io.github.fernsehheft.skysong.lyrics.TimedWord;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;
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

    private static final long APPEAR_MS = 300;
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

    /** A line that is on screen, with where it was put. */
    private static final class ShownLine {
        final LyricLine line;
        final List<LineLayout.PlacedWord> words;
        final Vec3 position;
        final float pixelSize;
        final long visibleUntil;
        long leftAt;

        ShownLine(LyricLine line, List<LineLayout.PlacedWord> words, Vec3 position, float pixelSize, long visibleUntil) {
            this.line = line;
            this.words = words;
            this.position = position;
            this.pixelSize = pixelSize;
            this.visibleUntil = visibleUntil;
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

        Lyrics song = moment.lyrics();
        long time = moment.timeMs();
        int index = moment.lineIndex();
        if (song != shownLyrics || index != shownIndex) {
            switchLine(song, index, moment.lineEndMs(), time, context);
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
        buffers.endBatch();
    }

    private void switchLine(Lyrics song, int index, long end, long time, WorldRenderContext context) {
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
        List<TimedWord> words = TimedWord.of(line, end);
        if (words.isEmpty()) {
            return;
        }
        long lastWord = words.get(words.size() - 1).startMs();

        Font font = Minecraft.getInstance().font;
        List<LineLayout.PlacedWord> placed = LineLayout.layout(
                words, font::width, font.width(" "), MAX_ROW_WIDTH, font.lineHeight + ROW_GAP);

        Vec3 eye = context.camera().getPosition();
        Vec3 position = placeLine(context.camera(), context.world());
        float pixelSize = (float) (PIXEL_SIZE * config.size * eye.distanceTo(position) / REFERENCE_DISTANCE);
        current = new ShownLine(line, placed, position, pixelSize, Math.min(end, lastWord + LINGER_MS));
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

    private void draw(PoseStack pose, MultiBufferSource buffers, Font font, Camera camera, ShownLine shown, long time) {
        float appear = Easing.easeOut(Easing.progress(time - shown.line.startMs(), APPEAR_MS));
        float leave = shown == leaving ? Easing.easeOut(Easing.progress(time - shown.leftAt, LEAVE_MS)) : 0;
        float lineAlpha = appear * (1 - leave);
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

        for (LineLayout.PlacedWord placed : shown.words) {
            long sinceSung = time - placed.word().startMs();
            float alpha = lineAlpha;
            float scale = 1;
            float offsetY = lineOffsetY;
            int color;
            switch (config.animation) {
                case KARAOKE -> {
                    float sung = Easing.progress(sinceSung, 120);
                    color = Easing.lerpColor(config.textRgb(), config.highlightRgb(), sung);
                    alpha *= UNSUNG_ALPHA + (1 - UNSUNG_ALPHA) * sung;
                    if (sinceSung >= 0) {
                        scale += 0.15f * Easing.bump(Easing.progress(sinceSung, 220));
                    }
                }
                case FADE -> {
                    float in = Easing.easeOut(Easing.progress(sinceSung, 250));
                    alpha *= in;
                    offsetY += (1 - in) * 4;
                    // Flash in the highlight colour, then settle on the normal one.
                    color = Easing.lerpColor(config.highlightRgb(), config.textRgb(), Easing.progress(sinceSung - 200, 500));
                }
                case RISE -> color = Easing.lerpColor(config.textRgb(), config.highlightRgb(), Easing.progress(sinceSung, 150));
                default -> throw new IllegalStateException("Unknown animation " + config.animation);
            }
            drawWord(pose, buffers, font, placed, color, alpha, scale, offsetY);
        }
        pose.popPose();
    }

    private static void drawWord(PoseStack pose, MultiBufferSource buffers, Font font, LineLayout.PlacedWord placed,
                                 int color, float alpha, float scale, float offsetY) {
        // Minecraft draws text with an alpha below 4 as fully opaque, so skip those.
        int alphaByte = Math.round(alpha * 255);
        if (alphaByte < 5) {
            return;
        }
        float halfWidth = placed.width() / 2f;
        float halfHeight = font.lineHeight / 2f;
        pose.pushPose();
        pose.translate(placed.x() + halfWidth, placed.y() + halfHeight + offsetY, 0);
        pose.scale(scale, scale, 1);
        font.drawInBatch(placed.word().text(), -halfWidth, -halfHeight, (alphaByte << 24) | color, true,
                pose.last().pose(), buffers, Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);
        pose.popPose();
    }
}
