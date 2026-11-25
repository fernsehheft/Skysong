package io.github.fernsehheft.skysong.gui;

import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;

import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;

/** A slider for a number setting, snapping to steps and showing "Label: value". */
final class SettingSlider extends AbstractSliderButton {
    private final Component label;
    private final double min;
    private final double max;
    private final double step;
    private final DoubleConsumer setter;
    private final DoubleFunction<Component> format;

    SettingSlider(int width, Component label, double min, double max, double step, double current,
                  DoubleConsumer setter, DoubleFunction<Component> format) {
        super(0, 0, width, 20, Component.empty(), (current - min) / (max - min));
        this.label = label;
        this.min = min;
        this.max = max;
        this.step = step;
        this.setter = setter;
        this.format = format;
        updateMessage();
    }

    private double settingValue() {
        double raw = min + value * (max - min);
        return Math.max(min, Math.min(max, Math.round(raw / step) * step));
    }

    @Override
    protected void updateMessage() {
        setMessage(Component.translatable("skysong.settings.value", label, format.apply(settingValue())));
    }

    @Override
    protected void applyValue() {
        setter.accept(settingValue());
    }
}
