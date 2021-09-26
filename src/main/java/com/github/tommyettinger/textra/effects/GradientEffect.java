
package com.github.tommyettinger.textra.effects;

import com.badlogic.gdx.graphics.Color;
import com.github.tommyettinger.textra.Effect;
import com.github.tommyettinger.textra.TypingLabel;
import com.github.tommyettinger.textra.utils.ColorUtils;

/** Tints the text in a gradient pattern. */
public class GradientEffect extends Effect {
    private static final float DEFAULT_DISTANCE  = 0.975f;
    private static final float DEFAULT_FREQUENCY = 2f;

    private Color color1    = null; // First color of the gradient.
    private Color color2    = null; // Second color of the gradient.
    private float distance  = 1; // How extensive the rainbow effect should be.
    private float frequency = 1; // How frequently the color pattern should move through the text.

    public GradientEffect(TypingLabel label, String[] params) {
        super(label);

        // Color 1
        if(params.length > 0) {
            this.color1 = paramAsColor(params[0]);
        }

        // Color 2
        if(params.length > 1) {
            this.color2 = paramAsColor(params[1]);
        }

        // Distance
        if(params.length > 2) {
            this.distance = paramAsFloat(params[2], 1);
        }

        // Frequency
        if(params.length > 3) {
            this.frequency = paramAsFloat(params[3], 1);
        }

        // Validate parameters
        if(this.color1 == null) this.color1 = new Color(Color.WHITE);
        if(this.color2 == null) this.color2 = new Color(Color.WHITE);
    }

    @Override
    protected void onApply(long glyph, int localIndex, int globalIndex, float delta) {
        // Calculate progress
        float distanceMod = (1f / distance) * (1f - DEFAULT_DISTANCE);
        float frequencyMod = (1f / frequency) * DEFAULT_FREQUENCY;
        float progress = calculateProgress(frequencyMod, distanceMod * localIndex, true);

        // Calculate color
        label.setInLayout(label.layout, globalIndex,
                (glyph & 0xFFFFFFFFL) | (long) ColorUtils.lerpColors(Color.rgba8888(this.color1), Color.rgba8888(this.color2), progress) << 32);
    }

}
