package io.github.fernsehheft.skysong.hud;

import io.github.fernsehheft.skysong.config.SkysongConfig;
import io.github.fernsehheft.skysong.lyrics.LyricsPlayback;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Path;

/** Lets the player drag the HUD elements around and resize them with the mouse wheel. */
public final class HudEditScreen extends Screen {
    private static final int DIM = 0x66000000;
    private static final int OUTLINE = 0x88FFFFFF;
    private static final int OUTLINE_HOVERED = 0xFFFFFFFF;
    private static final int HINT_COLOR = 0xFFDDDDDD;
    private static final double SCALE_STEP = 0.1;

    private final SkysongHud hud;
    private final SkysongConfig config;
    private final Path settingsFile;

    private MovableElement dragged;
    private double grabOffsetX;
    private double grabOffsetY;

    public HudEditScreen(SkysongHud hud, SkysongConfig config, Path settingsFile) {
        super(Component.literal("Skysong HUD"));
        this.hud = hud;
        this.config = config;
        this.settingsFile = settingsFile;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, DIM);
        LyricsPlayback.Moment moment = hud.playback().now().orElse(null);
        MovableElement hovered = dragged != null ? dragged : elementAt(mouseX, mouseY, moment);

        boolean any = false;
        for (MovableElement element : hud.movableElements()) {
            HudBox box = element.enabled() ? element.box(font, width, height, moment, true) : null;
            if (box == null) {
                continue;
            }
            any = true;
            element.render(graphics, font, width, height, moment, true);
            graphics.renderOutline(box.x() - 2, box.y() - 2, box.width() + 4, box.height() + 4,
                    element == hovered ? OUTLINE_HOVERED : OUTLINE);
            int labelY = box.y() >= 14 ? box.y() - 12 : box.y() + box.height() + 4;
            graphics.drawString(font, element.name(), box.x(), labelY, HINT_COLOR);
        }

        String hint = any
                ? "Drag to move  ·  Scroll to resize  ·  R resets  ·  Esc when you're done"
                : "The karaoke bar and the now playing card are both turned off";
        graphics.drawCenteredString(font, hint, width / 2, height - 20, HINT_COLOR);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private MovableElement elementAt(double mouseX, double mouseY, LyricsPlayback.Moment moment) {
        // Last drawn is on top, so search backwards.
        var elements = hud.movableElements();
        for (int i = elements.size() - 1; i >= 0; i--) {
            MovableElement element = elements.get(i);
            HudBox box = element.enabled() ? element.box(font, width, height, moment, true) : null;
            if (box != null && box.contains(mouseX, mouseY)) {
                return element;
            }
        }
        return null;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            LyricsPlayback.Moment moment = hud.playback().now().orElse(null);
            dragged = elementAt(mouseX, mouseY, moment);
            if (dragged != null) {
                HudBox box = dragged.box(font, width, height, moment, true);
                grabOffsetX = mouseX - (box.x() + box.width() / 2.0);
                grabOffsetY = mouseY - (box.y() + box.height() / 2.0);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (dragged == null) {
            return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
        }
        double x = (mouseX - grabOffsetX) / width;
        double y = (mouseY - grabOffsetY) / height;
        dragged.moveTo(Math.max(0, Math.min(1, x)), Math.max(0, Math.min(1, y)));
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        dragged = null;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        MovableElement element = elementAt(mouseX, mouseY, hud.playback().now().orElse(null));
        if (element == null) {
            return false;
        }
        element.resize(Math.signum(delta) * SCALE_STEP);
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_R) {
            MovableElement element = elementAt(mouseX(), mouseY(), hud.playback().now().orElse(null));
            if (element != null) {
                element.reset();
            } else {
                hud.movableElements().forEach(MovableElement::reset);
            }
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private double mouseX() {
        return minecraft.mouseHandler.xpos() * width / minecraft.getWindow().getScreenWidth();
    }

    private double mouseY() {
        return minecraft.mouseHandler.ypos() * height / minecraft.getWindow().getScreenHeight();
    }

    @Override
    public void removed() {
        config.save(settingsFile);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
