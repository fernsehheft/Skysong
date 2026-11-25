package io.github.fernsehheft.skysong.hud;

import io.github.fernsehheft.skysong.config.ColorMode;
import io.github.fernsehheft.skysong.config.SkysongConfig;
import io.github.fernsehheft.skysong.lyrics.LyricLine;
import io.github.fernsehheft.skysong.lyrics.LyricsPlayback;
import io.github.fernsehheft.skysong.lyrics.TimedWord;
import io.github.fernsehheft.skysong.render.Easing;
import io.github.fernsehheft.skysong.render.LineLayout;
import io.github.fernsehheft.skysong.render.LyricColors;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.List;

/** The line being sung as a subtitle, with the words lighting up, and the next line below it. */
final class KaraokeBar implements MovableElement {
    private static final int PADDING_X = 8;
    private static final int PADDING_Y = 5;
    private static final int MAX_ROW_WIDTH = 260;
    private static final int ROW_HEIGHT = 11;
    private static final float NEXT_LINE_SCALE = 0.75f;
    private static final int NEXT_LINE_COLOR = 0x9A9A9A;
    private static final int BACKGROUND = 0x000000;
    private static final float BACKGROUND_ALPHA = 0.6f;
    private static final long FADE_IN_MS = 180;
    private static final List<TimedWord> PREVIEW_WORDS = List.of(
            new TimedWord("Your", -1000), new TimedWord("lyrics", -1000), new TimedWord("show", Long.MAX_VALUE),
            new TimedWord("up", Long.MAX_VALUE), new TimedWord("here", Long.MAX_VALUE));

    private final SkysongConfig config;
    /** Below 1 while fading out for a pause; set by the HUD every frame. */
    float visibility = 1;

    KaraokeBar(SkysongConfig config) {
        this.config = config;
    }

    /** What the bar shows at one moment, laid out in unscaled pixels. */
    private record Content(List<LineLayout.PlacedWord> words, int rows, int textWidth, String nextLine,
                           long lineStartMs, long timeMs) {
    }

    @Override
    public Component name() {
        return Component.translatable("skysong.hud.karaoke_bar");
    }

    @Override
    public boolean enabled() {
        return config.karaokeBar;
    }

    private Content content(Font font, LyricsPlayback.Moment moment, boolean preview) {
        List<TimedWord> words = null;
        long lineStart = 0;
        long time = 0;
        String next = null;
        if (moment != null && moment.lyrics() != null) {
            LyricLine line = moment.line().filter(l -> !l.isBreak()).orElse(null);
            time = moment.timeMs();
            if (line != null) {
                words = TimedWord.of(line, moment.lineEndMs(), config.estimateWordTiming);
                lineStart = line.startMs();
            } else {
                // Between lines: a quiet note until the singing starts again.
                words = List.of(new TimedWord("♪", Long.MAX_VALUE));
                lineStart = time - FADE_IN_MS;
            }
            next = moment.nextLine().map(LyricLine::text).orElse(null);
        } else if (preview) {
            words = PREVIEW_WORDS;
            lineStart = -FADE_IN_MS;
            next = "and the next line here";
        }
        if (words == null || words.isEmpty()) {
            return null;
        }

        List<LineLayout.PlacedWord> placed = LineLayout.layout(words, font::width, font.width(" "), MAX_ROW_WIDTH, ROW_HEIGHT);
        int textWidth = 0;
        int rows = 0;
        float lastY = Float.NaN;
        for (LineLayout.PlacedWord word : placed) {
            textWidth = Math.max(textWidth, Math.round(Math.abs(word.x()) * 2));
            textWidth = Math.max(textWidth, Math.round(Math.abs(word.x() + word.width()) * 2));
            if (word.y() != lastY) {
                rows++;
                lastY = word.y();
            }
        }
        if (config.karaokeStyle == SkysongConfig.KaraokeStyle.MINIMAL) {
            next = null;
        }
        if (next != null) {
            next = HudDraw.fit(font, next, Math.round(MAX_ROW_WIDTH / NEXT_LINE_SCALE));
            textWidth = Math.max(textWidth, Math.round(font.width(next) * NEXT_LINE_SCALE));
        }
        return new Content(placed, rows, textWidth, next, lineStart, time);
    }

    private static int width(Content content) {
        return content.textWidth() + PADDING_X * 2;
    }

    private static int height(Content content, Font font) {
        int height = content.rows() * ROW_HEIGHT + PADDING_Y * 2;
        if (content.nextLine() != null) {
            height += Math.round(font.lineHeight * NEXT_LINE_SCALE) + 2;
        }
        return height;
    }

    @Override
    public HudBox box(Font font, int screenWidth, int screenHeight, LyricsPlayback.Moment moment, boolean preview) {
        Content content = content(font, moment, preview);
        if (content == null) {
            return null;
        }
        return HudBox.centeredAt(config.karaokeX, config.karaokeY, width(content), height(content, font),
                (float) config.karaokeScale, screenWidth, screenHeight);
    }

    @Override
    public void render(GuiGraphics graphics, Font font, int screenWidth, int screenHeight,
                       LyricsPlayback.Moment moment, boolean preview) {
        Content content = content(font, moment, preview);
        if (content == null) {
            return;
        }
        int width = width(content);
        int height = height(content, font);
        HudBox box = HudBox.centeredAt(config.karaokeX, config.karaokeY, width, height,
                (float) config.karaokeScale, screenWidth, screenHeight);
        float alpha = Easing.easeOut(Easing.progress(content.timeMs() - content.lineStartMs(), FADE_IN_MS));
        // The editor always shows the bar, paused or not.
        float shown = preview ? 1 : visibility;
        if (shown <= 0.01f) {
            return;
        }
        alpha *= shown;
        boolean pill = config.karaokeStyle == SkysongConfig.KaraokeStyle.PILL;

        graphics.pose().pushPose();
        graphics.pose().translate(box.x(), box.y(), 0);
        graphics.pose().scale(box.scale(), box.scale(), 1);
        if (pill) {
            HudDraw.roundedRect(graphics, 0, 0, width, height, 6, HudDraw.withAlpha(BACKGROUND, BACKGROUND_ALPHA * shown));
        }

        float centerX = width / 2f;
        float rowsTop = PADDING_Y + content.rows() * ROW_HEIGHT / 2f;
        long clock = System.currentTimeMillis();
        for (int i = 0; i < content.words().size(); i++) {
            LineLayout.PlacedWord placed = content.words().get(i);
            long sinceSung = content.timeMs() - placed.word().startMs();
            float sung = Easing.progress(sinceSung, 120);
            int base = LyricColors.base(config, i, content.words().size(), clock);
            int color = config.colorMode == ColorMode.SINGLE
                    ? Easing.lerpColor(base, config.highlightRgb(), sung)
                    : LyricColors.afterSinging(base, config.highlightRgb(), sinceSung);
            float wordAlpha = alpha * (0.6f + 0.4f * sung);
            HudDraw.text(graphics, font, placed.word().text(), centerX + placed.x(), rowsTop + placed.y(),
                    HudDraw.withAlpha(color, wordAlpha), !pill);
        }

        if (content.nextLine() != null) {
            graphics.pose().pushPose();
            graphics.pose().translate(centerX, PADDING_Y + content.rows() * ROW_HEIGHT + 2, 0);
            graphics.pose().scale(NEXT_LINE_SCALE, NEXT_LINE_SCALE, 1);
            HudDraw.text(graphics, font, content.nextLine(), -font.width(content.nextLine()) / 2f, 0,
                    HudDraw.withAlpha(NEXT_LINE_COLOR, shown), false);
            graphics.pose().popPose();
        }
        graphics.pose().popPose();
    }

    @Override
    public void moveTo(double fractionX, double fractionY) {
        config.karaokeX = fractionX;
        config.karaokeY = fractionY;
    }

    @Override
    public void resize(double scaleChange) {
        config.karaokeScale = clampScale(config.karaokeScale + scaleChange);
    }

    @Override
    public void reset() {
        config.karaokeX = SkysongConfig.DEFAULT_KARAOKE_X;
        config.karaokeY = SkysongConfig.DEFAULT_KARAOKE_Y;
        config.karaokeScale = 1.0;
    }

    static double clampScale(double scale) {
        return Math.max(SkysongConfig.MIN_HUD_SCALE, Math.min(SkysongConfig.MAX_HUD_SCALE, scale));
    }
}
