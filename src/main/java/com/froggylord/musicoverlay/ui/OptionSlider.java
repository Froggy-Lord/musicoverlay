package com.froggylord.musicoverlay.ui;

import com.froggylord.musicoverlay.config.ConfigManager;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;

import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;

/** A slider over a numeric range that writes straight back into the config. */
public class OptionSlider extends AbstractSliderButton {
    private final double min;
    private final double max;
    private final DoubleConsumer onApply;
    private final DoubleFunction<String> display;

    public OptionSlider(int x, int y, int w, int h, double min, double max, double current,
                        DoubleFunction<String> display, DoubleConsumer onApply) {
        super(x, y, w, h, Component.empty(), (current - min) / (max - min));
        this.min = min;
        this.max = max;
        this.display = display;
        this.onApply = onApply;
        updateMessage();
    }

    private double actual() {
        return min + this.value * (max - min);
    }

    @Override
    protected void updateMessage() {
        setMessage(Component.literal(display.apply(actual())));
    }

    @Override
    protected void applyValue() {
        onApply.accept(actual());
        ConfigManager.save();
    }
}
