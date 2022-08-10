/*
 * Copyright (c) 2021-2022 See AUTHORS file.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.tommyettinger.textra;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.scenes.scene2d.utils.TransformDrawable;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.FloatArray;
import com.badlogic.gdx.utils.LongArray;
import com.badlogic.gdx.utils.NumberUtils;
import com.badlogic.gdx.utils.ObjectMap;
import com.badlogic.gdx.utils.ObjectMap.Entry;
import com.badlogic.gdx.utils.reflect.ClassReflection;

import java.lang.StringBuilder;
import java.util.Arrays;
import java.util.Map;

/**
 * An extension of {@link Label} that progressively shows the text as if it was being typed in real time, and allows the
 * use of tokens in the following format: <tt>{TOKEN=PARAMETER}</tt>.
 */
public class TypingLabel extends TextraLabel {
    ///////////////////////
    /// --- Members --- ///
    ///////////////////////

    // Collections
    private final ObjectMap<String, String> variables = new ObjectMap<>();
    protected final Array<TokenEntry> tokenEntries = new Array<>();

    // Config
    private final Color clearColor = new Color(TypingConfig.DEFAULT_CLEAR_COLOR);
    private TypingListener listener = null;

    // Internal state
    private final StringBuilder originalText = new StringBuilder();
    private final StringBuilder intermediateText = new StringBuilder();
    protected final Layout workingLayout = Layout.POOL.obtain();
    /**
     * Contains two floats per glyph; even items are x offsets, odd items are y offsets.
     */
    public final FloatArray offsets = new FloatArray();
    /**
     * Contains two floats per glyph, as size multipliers; even items apply to x, odd items apply to y.
     */
    public final FloatArray sizing = new FloatArray();
    /**
     * Contains one float per glyph; each is a rotation in degrees to apply to that glyph (around its center).
     */
    public final FloatArray rotations = new FloatArray();
    /**
     * If true, this will attempt to track which glyph the user's mouse or other pointer is over (see {@link #overIndex}
     * and {@link #lastTouchedIndex}).
     */
    public boolean trackingInput = false;
    /**
     * The global glyph index (as used by {@link #setInWorkingLayout(int, long)}) of the last glyph touched by the user.
     * If nothing in this TypingLabel was touched during the last call to {@link #draw(Batch, float)}, then this will be
     * -1 . This only changes when a click, tap, or other touch was just issued.
     */
    public int lastTouchedIndex = -1;
    /**
     * The global glyph index (as used by {@link #setInWorkingLayout(int, long)}) of the last glyph hovered or dragged
     * over by the user (including a click and mouse movement without a click). If nothing in this TypingLabel was moved
     * over during the last call to {@link #draw(Batch, float)}, then this will be -1 . This changes whenever the mouse
     * or a pointer is over a glyph in this.
     */
    public int overIndex = -1;
    protected final Array<Effect> activeEffects = new Array<>(Effect.class);
    private float textSpeed = TypingConfig.DEFAULT_SPEED_PER_CHAR;
    private float charCooldown = textSpeed;
    private int rawCharIndex = -2; // All chars, including color codes
    private int glyphCharIndex = -1; // Only renderable chars, excludes color codes
    private int glyphCharCompensation = 0;
    private boolean parsed = false;
    private boolean paused = false;
    private boolean ended = false;
    private boolean skipping = false;
    private boolean ignoringEvents = false;
    private boolean ignoringEffects = false;
    private String defaultToken = "";

    ////////////////////////////
    /// --- Constructors --- ///
    ////////////////////////////

    public TypingLabel() {
        super();
        workingLayout.font(super.font);
        saveOriginalText("");
    }

    public TypingLabel(String text, Skin skin) {
        this(text, skin.get(Label.LabelStyle.class));
    }

    public TypingLabel(String text, Skin skin, Font replacementFont) {
        this(text, skin.get(Label.LabelStyle.class), replacementFont);
    }

    public TypingLabel(String text, Skin skin, String styleName) {
        this(text, skin.get(styleName, Label.LabelStyle.class));
    }

    public TypingLabel(String text, Skin skin, String styleName, Font replacementFont) {
        this(text, skin.get(styleName, Label.LabelStyle.class), replacementFont);
    }

    public TypingLabel(String text, Label.LabelStyle style) {
        super(text = Parser.preprocess(text), style);
        workingLayout.font(super.font);
        workingLayout.setBaseColor(layout.baseColor);
        saveOriginalText(text);
    }

    public TypingLabel(String text, Label.LabelStyle style, Font replacementFont) {
        super(text = Parser.preprocess(text), style, replacementFont);
        workingLayout.font(super.font);
        workingLayout.setBaseColor(layout.baseColor);
        saveOriginalText(text);
    }

    public TypingLabel(String text, Font font) {
        super(text = Parser.preprocess(text), font);
        workingLayout.font(font);
        saveOriginalText(text);
    }

    public TypingLabel(String text, Font font, Color color) {
        super(text = Parser.preprocess(text), font, color);
        workingLayout.font(font);
        workingLayout.setBaseColor(layout.baseColor);
        saveOriginalText(text);

    }

    /////////////////////////////
    /// --- Text Handling --- ///
    /////////////////////////////

    /**
     * Modifies the text of this label. If the char progression is already running, it's highly recommended to use
     * {@link #restart(String)} instead.
     */
    @Override
    public void setText(String newText) {
        this.setText(newText, true);
    }

    /**
     * Sets the text of this label.
     *
     * @param modifyOriginalText Flag determining if the original text should be modified as well. If {@code false},
     *                           only the display text is changed while the original text is untouched. If {@code true},
     *                           then this runs {@link Parser#preprocess(CharSequence)} on the text, which should only
     *                           generally be run once per original text.
     * @see #restart(String)
     */
    protected void setText(String newText, boolean modifyOriginalText) {
        if (modifyOriginalText) newText = Parser.preprocess(newText);
        setText(newText, modifyOriginalText, true);
    }

    /**
     * Sets the text of this label.
     *
     * @param modifyOriginalText Flag determining if the original text should be modified as well. If {@code false},
     *                           only the display text is changed while the original text is untouched.
     * @param restart            Whether this label should restart. Defaults to true.
     * @see #restart(String)
     */
    protected void setText(String newText, boolean modifyOriginalText, boolean restart) {
        final boolean hasEnded = this.hasEnded();
        font.markup(newText, layout.clear());
        float actualWidth = layout.getWidth();
        workingLayout.setTargetWidth(actualWidth);
        font.markup(newText, workingLayout.clear());

        setWidth(actualWidth + (style != null && style.background != null ?
                style.background.getLeftWidth() + style.background.getRightWidth() : 0.0f));

        if (modifyOriginalText) saveOriginalText(newText);
        if (restart) {
            this.restart();
        }
        if (hasEnded) {
            this.skipToTheEnd(true, false);
        }
    }

    /**
     * Similar to {@link Layout#toString()}, but returns the original text with all the tokens unchanged.
     */
    public StringBuilder getOriginalText() {
        return originalText;
    }

    /**
     * Copies the content of {@link #getOriginalText()} to the {@link StringBuilder} containing the original
     * text with all tokens unchanged.
     */
    protected void saveOriginalText(CharSequence text) {
        if (text != originalText) {
            originalText.setLength(0);
            originalText.append(text);
        }
        originalText.trimToSize();
    }

    /**
     * Restores the original text with all tokens unchanged to this label. Make sure to call {@link #parseTokens()} to
     * parse the tokens again.
     */
    protected void restoreOriginalText() {
//        float actualWidth = layout.getTargetWidth();
//        layout.setTargetWidth(Float.MAX_VALUE);
        super.setText(originalText.toString());
//        layout.setTargetWidth(actualWidth);
        this.parsed = false;
    }

    ////////////////////////////
    /// --- External API --- ///
    ////////////////////////////

    /**
     * Returns the {@link TypingListener} associated with this label. May be {@code null}.
     */
    public TypingListener getTypingListener() {
        return listener;
    }

    /**
     * Sets the {@link TypingListener} associated with this label, or {@code null} to remove the current one.
     */
    public void setTypingListener(TypingListener listener) {
        this.listener = listener;
    }

    /**
     * Returns a {@link Color} instance with the color to be used on {@code CLEARCOLOR} tokens. Modify this instance to
     * change the token color. Default value is specified by {@link TypingConfig}.
     *
     * @see TypingConfig#DEFAULT_CLEAR_COLOR
     */
    public Color getClearColor() {
        return clearColor;
    }

    /**
     * Returns the default token being used in this label. Defaults to empty string.
     */
    public String getDefaultToken() {
        return defaultToken;
    }

    /**
     * Sets the default token being used in this label. This token will be used before the label's text, and after each
     * {RESET} call. Useful if you want a certain token to be active at all times without having to type it all the
     * time.
     */
    public void setDefaultToken(String defaultToken) {
        this.defaultToken = defaultToken == null ? "" : defaultToken;
        this.parsed = false;
    }

    /**
     * Parses all tokens of this label. Use this after setting the text and any variables that should be replaced.
     */
    public void parseTokens() {
        this.setText(Parser.preprocess("{NORMAL}" + getDefaultToken() + originalText), false, false);
        Parser.parseTokens(this);
        parsed = true;
//        setSize(actualWidth, workingLayout.getHeight());
    }

    /**
     * Skips the char progression to the end, showing the entire label. Useful for when users don't want to wait for too
     * long. Ignores all subsequent events by default.
     */
    @Override
    public void skipToTheEnd() {
        skipToTheEnd(true);
    }

    /**
     * Skips the char progression to the end, showing the entire label. Useful for when users don't want to wait for too
     * long.
     *
     * @param ignoreEvents If {@code true}, skipped events won't be reported to the listener.
     */
    public void skipToTheEnd(boolean ignoreEvents) {
        skipToTheEnd(ignoreEvents, false);
    }

    /**
     * Skips the char progression to the end, showing the entire label. Useful for when users don't want to wait for too
     * long.
     *
     * @param ignoreEvents  If {@code true}, skipped events won't be reported to the listener.
     * @param ignoreEffects If {@code true}, all text effects will be instantly cancelled.
     */
    public void skipToTheEnd(boolean ignoreEvents, boolean ignoreEffects) {
        skipping = true;
        ignoringEvents = ignoreEvents;
        ignoringEffects = ignoreEffects;
    }

    /**
     * Cancels calls to {@link #skipToTheEnd()}. Useful if you need to restore the label's normal behavior at some event
     * after skipping.
     */
    public void cancelSkipping() {
        if (skipping) {
            skipping = false;
            ignoringEvents = false;
            ignoringEffects = false;
        }
    }

    /**
     * Returns whether this label is currently skipping its typing progression all the way to the end. This is
     * only true if skipToTheEnd is called.
     */
    public boolean isSkipping() {
        return skipping;
    }

    /**
     * Returns whether this label is paused.
     */
    public boolean isPaused() {
        return paused;
    }

    /**
     * Pauses this label's character progression.
     */
    public void pause() {
        paused = true;
    }

    /**
     * Resumes this label's character progression.
     */
    public void resume() {
        paused = false;
    }

    /**
     * Returns whether this label's char progression has ended.
     */
    public boolean hasEnded() {
        return ended;
    }

    /**
     * Restarts this label with the original text and starts the char progression right away. All tokens are
     * automatically parsed.
     */
    public void restart() {
        restart(getOriginalText().toString());
    }

    /**
     * Restarts this label with the given text and starts the char progression right away. All tokens are automatically
     * parsed.
     */
    public void restart(String newText) {
        // Reset cache collections
        workingLayout.baseColor = Color.WHITE_FLOAT_BITS;
        workingLayout.maxLines = Integer.MAX_VALUE;
        workingLayout.atLimit = false;
        workingLayout.ellipsis = null;
        Line.POOL.freeAll(workingLayout.lines);
        workingLayout.lines.clear();
        workingLayout.lines.add(Line.POOL.obtain());

        offsets.clear();
        sizing.clear();
        rotations.clear();
        activeEffects.clear();

        // Reset state
        textSpeed = TypingConfig.DEFAULT_SPEED_PER_CHAR;
        charCooldown = textSpeed;
        rawCharIndex = -2;
        glyphCharIndex = -1;
        glyphCharCompensation = 0;
        parsed = false;
        paused = false;
        ended = false;
        skipping = false;
        ignoringEvents = false;
        ignoringEffects = false;

        // Set new text
        invalidate();
        saveOriginalText(newText);

        // Parse tokens
        tokenEntries.clear();
        parseTokens();
    }

    /**
     * Returns an {@link ObjectMap} with all the variable names and their respective replacement values.
     */
    public ObjectMap<String, String> getVariables() {
        return variables;
    }

    /**
     * Registers a variable and its respective replacement value to this label.
     */
    public void setVariable(String var, String value) {
        variables.put(var.toUpperCase(), value);
    }

    /**
     * Registers a set of variables and their respective replacement values to this label.
     */
    public void setVariables(ObjectMap<String, String> variableMap) {
        this.variables.clear();
        for (Entry<String, String> entry : variableMap.entries()) {
            this.variables.put(entry.key.toUpperCase(), entry.value);
        }
    }

    /**
     * Registers a set of variables and their respective replacement values to this label.
     */
    public void setVariables(Map<String, String> variableMap) {
        this.variables.clear();
        for (Map.Entry<String, String> entry : variableMap.entrySet()) {
            this.variables.put(entry.getKey().toUpperCase(), entry.getValue());
        }
    }

    /**
     * Removes all variables from this label.
     */
    public void clearVariables() {
        this.variables.clear();
    }

    //////////////////////////////////
    /// --- Core Functionality --- ///
    //////////////////////////////////

    @Override
    public void act(float delta) {
        super.act(delta);

        // Force token parsing
        if (!parsed) {
            parseTokens();
        }

        // Update cooldown and process char progression
        if (skipping || (!ended && !paused)) {
            if (skipping || (charCooldown -= delta) < 0.0f) {
                processCharProgression();
            }
        }
        font.calculateSize(workingLayout);
        int glyphCount = getLayoutSize(layout);
        offsets.setSize(glyphCount + glyphCount);
        Arrays.fill(offsets.items, 0, glyphCount + glyphCount, 0f);
        sizing.setSize(glyphCount + glyphCount);
        Arrays.fill(sizing.items, 0, glyphCount + glyphCount, 1f);
        rotations.setSize(glyphCount);
        Arrays.fill(rotations.items, 0, glyphCount, 0f);

        // Apply effects
        if (!ignoringEffects) {
            int workingLayoutSize = getLayoutSize(workingLayout);

            for (int i = activeEffects.size - 1; i >= 0; i--) {
                Effect effect = activeEffects.get(i);
                effect.update(delta);
                int start = effect.indexStart;
                int end = effect.indexEnd >= 0 ? effect.indexEnd : glyphCharIndex;

                // If effect is finished, remove it
                if (effect.isFinished()) {
                    activeEffects.removeIndex(i);
                    continue;
                }

                // Apply effect to glyph
                for (int j = Math.max(0, start); j <= glyphCharIndex && j <= end && j < workingLayoutSize; j++) {
                    long glyph = getInLayout(workingLayout, j);
                    if (glyph == 0xFFFFFFL) break; // invalid char
                    effect.apply(glyph, j, delta);
                }
            }
        }
    }

    /**
     * Returns a seeded random float between -2.4f and -0.4f. This is meant to be used to randomize the typing
     * speed-ups and slow-downs for natural typing, when the NATURAL tag is used. It returns a negative value because
     * when textSpeed is negative, that indicates natural typing should be used, and so we multiply negative textSpeed
     * by a negative random value to get a normal positive textSpeed.
     * @param seed any int; should be the same if a value should be replicable
     * @return a random float between -2.4f and -0.4f
     */
    private float randomize(int seed) {
        return NumberUtils.intBitsToFloat((int) ((seed ^ 0x9E3779B97F4A7C15L) * 0xD1B54A32D192ED03L >>> 41) | 0x40000000) - 4.4f;
    }

    /**
     * Proccess char progression according to current cooldown and process all tokens in the current index.
     */
    private void processCharProgression() {
        // Keep a counter of how many chars we're processing in this tick.
        int charCounter = 0;
        // Process chars while there's room for it
        while (skipping || charCooldown < 0.0f) {
            // Apply compensation to glyph index, if any
            if (glyphCharCompensation != 0) {
                if (glyphCharCompensation > 0) {
                    glyphCharIndex++;
                    glyphCharCompensation--;
                } else {
                    glyphCharIndex--;
                    glyphCharCompensation++;
                }

                // Increment cooldown and wait for it
                if(textSpeed < 0f) {
                    charCooldown += textSpeed * randomize(glyphCharIndex);
                }
                else
                    charCooldown += textSpeed;
                continue;
            }

            // Increase raw char index
            rawCharIndex++;

            // Get next character and calculate cooldown increment

            int layoutSize = getLayoutSize(layout);

            // If char progression is finished, or if text is empty, notify listener and abort routine
            if (layoutSize == 0 || glyphCharIndex >= layoutSize) {
                if (!ended) {
                    ended = true;
                    skipping = false;
                    if (listener != null) listener.end();
                }
                break;
            }

            // Process tokens according to the current index
            if (tokenEntries.size > 0 && tokenEntries.peek().index == rawCharIndex) {
                TokenEntry entry = tokenEntries.pop();
                String token = entry.token;
                TokenCategory category = entry.category;
                rawCharIndex = entry.endIndex - 1;
//                glyphCharIndex--;
                // Process tokens
                switch (category) {
                    case SPEED: {
                        textSpeed = entry.floatValue;
                        continue;
                    }
                    case WAIT: {
//                        glyphCharIndex--;
//                        rawCharIndex--;
//                        glyphCharCompensation++;
                        charCooldown += entry.floatValue;
                        continue;
                    }
//                    case SKIP: {
//                        if (entry.stringValue != null) {
//                            rawCharIndex += entry.stringValue.length();
//                        }
//                        break;
//                    }
                    case EVENT: {
                        triggerEvent(entry.stringValue, false);
                        continue;
                    }
                    case EFFECT_START:
                    case EFFECT_END: {
                        // Get effect class
                        boolean isStart = category == TokenCategory.EFFECT_START;
                        Class<? extends Effect> effectClass = isStart ? TypingConfig.EFFECT_START_TOKENS.get(token) : TypingConfig.EFFECT_END_TOKENS.get(token);

                        // End all effects of the same type
                        for (int i = 0; i < activeEffects.size; i++) {
                            Effect effect = activeEffects.get(i);
                            if (effect.indexEnd < 0) {
                                if (ClassReflection.isAssignableFrom(effectClass, effect.getClass())) {
                                    effect.indexEnd = glyphCharIndex;
                                }
                            }
                        }

                        // Create new effect if necessary
                        if (isStart) {
                            entry.effect.indexStart = glyphCharIndex + 1;
                            activeEffects.add(entry.effect);
                        }
                        continue;
                    }
                }
                break;
            }
            int safeIndex = MathUtils.clamp(glyphCharIndex + 1, 0, layoutSize - 1);
            long baseChar; // Null character by default
            if (layoutSize > 0) {
                baseChar = getInLayout(layout, safeIndex);
                float intervalMultiplier = TypingConfig.INTERVAL_MULTIPLIERS_BY_CHAR.get((char) baseChar, 1);
                if(textSpeed < 0f) {
                    charCooldown += textSpeed * randomize(glyphCharIndex) * intervalMultiplier;
                }
                else
                    charCooldown += textSpeed * intervalMultiplier;
            }


            // Increment char counter
            charCounter++;

            // Increase glyph char index for all characters
            if (rawCharIndex > 0) {
                glyphCharIndex++;
            }

            // Notify listener about char progression
            if (glyphCharIndex >= 0 && glyphCharIndex < layoutSize && rawCharIndex >= 0 && listener != null) {
                listener.onChar(getInLayout(layout, glyphCharIndex));
            }

            // Break loop if this was our first glyph to prevent glyph issues.
            if (glyphCharIndex == 0) {
//                // Notify listener about char progression
//                if (glyphCharIndex < layoutSize && rawCharIndex >= 0 && listener != null) {
//                    listener.onChar(getInLayout(layout, glyphCharIndex));
//                }
//                glyphCharIndex++;

                charCooldown = Math.abs(textSpeed);
                break;
            }

            // Break loop if enough chars were processed
            charCounter++;
            int charLimit = TypingConfig.CHAR_LIMIT_PER_FRAME;
            if (!skipping && charLimit > 0 && charCounter > charLimit) {
                charCooldown = Math.max(charCooldown, Math.abs(textSpeed));
                break;
            }
        }
        if (wrap) {
//            font.regenerateLayout(workingLayout);
//            parseTokens();
            this.setText(intermediateText.toString(), false, false);

        } else {
            font.calculateSize(workingLayout);
        }

        invalidateHierarchy();
    }

    private int getLayoutSize(Layout layout) {
        int layoutSize = 0;
        for (int i = 0, n = layout.lines(); i < n; i++) {
            layoutSize += layout.getLine(i).glyphs.size;
        }
        return layoutSize;
    }

    @Override
    public boolean remove() {
        Layout.POOL.free(workingLayout);
        Layout.POOL.free(layout);
        return super.remove();
    }

    @Override
    public void setSize(float width, float height) {
        super.setSize(width, height);
        if (wrap) {
            workingLayout.setTargetWidth(width);
        }
    }

//    @Override
//    public float getPrefWidth() {
//        return wrap ? 0f : (workingLayout.getWidth() + (style != null && style.background != null ?
//                style.background.getLeftWidth() + style.background.getRightWidth() : 0.0f));
//    }
//
//    @Override
//    public float getPrefHeight() {
//        return workingLayout.getHeight() + (style != null && style.background != null ?
//                style.background.getBottomHeight() + style.background.getTopHeight() : 0.0f);
//    }

    @Override
    public void layout() {
        super.layout();

        if (wrap && (workingLayout.getTargetWidth() != getWidth())) {
            font.regenerateLayout(workingLayout);
        }
    }

    /**
     * If your font uses {@link com.github.tommyettinger.textra.Font.DistanceFieldType#SDF} or {@link com.github.tommyettinger.textra.Font.DistanceFieldType#MSDF},
     * then this has to do some extra work to use the appropriate shader.
     * If {@link Font#enableShader(Batch)} was called before rendering a group of TypingLabels, then they will try to
     * share one Batch; otherwise this will change the shader to render SDF or MSDF, then change it back at the end of
     * each draw() call.
     *
     * @param batch
     * @param parentAlpha
     */
    @Override
    public void draw(Batch batch, float parentAlpha) {
        super.validate();

        final float rot = getRotation();
        final float originX = getOriginX();
        final float originY = getOriginY();
        final float sn = MathUtils.sinDeg(rot);
        final float cs = MathUtils.cosDeg(rot);

        batch.getColor().set(getColor()).a *= parentAlpha;
        batch.setColor(batch.getColor());
        final int lines = workingLayout.lines();
        float baseX = getX(), baseY = getY();

        float height = workingLayout.getHeight();
        if (Align.isBottom(align)) {
            baseX -= sn * height;
            baseY += cs * height;
        } else if (Align.isCenterVertical(align)) {
            baseX -= sn * height * 0.5f;
            baseY += cs * height * 0.5f;
        }
        float width = getWidth();
        height = getHeight();
        if (Align.isRight(align)) {
            baseX += cs * width;
            baseY += sn * width;
        } else if (Align.isCenterHorizontal(align)) {
            baseX += cs * width * 0.5f;
            baseY += sn * width * 0.5f;
        }

        if (Align.isTop(align)) {
            baseX -= sn * height;
            baseY += cs * height;
        } else if (Align.isCenterVertical(align)) {
            baseX -= sn * height * 0.5f;
            baseY += cs * height * 0.5f;
        }
        if (style != null && style.background != null) {
            Drawable background = style.background;
            if (Align.isLeft(align)) {
                baseX += cs * background.getLeftWidth();
                baseY += sn * background.getLeftWidth();
            } else if (Align.isRight(align)) {
                baseX -= cs * background.getRightWidth();
                baseY -= sn * background.getRightWidth();
            } else {
                baseX += cs * (background.getLeftWidth() - background.getRightWidth()) * 0.5f;
                baseY += sn * (background.getLeftWidth() - background.getRightWidth()) * 0.5f;
            }
            if (Align.isBottom(align)) {
                baseX -= sn * background.getBottomHeight();
                baseY += cs * background.getBottomHeight();
            } else if (Align.isTop(align)) {
                baseX += sn * background.getTopHeight();
                baseY -= cs * background.getTopHeight();
            } else {
                baseX -= sn * (background.getBottomHeight() - background.getTopHeight()) * 0.5f;
                baseY += cs * (background.getBottomHeight() - background.getTopHeight()) * 0.5f;
            }
            ((TransformDrawable) background).draw(batch,
                    getX(), getY(),             // position
                    originX, originY,           // origin
                    getWidth(), getHeight(),    // size
                    1f, 1f,                     // scale
                    rot);                       // rotation
        }

        if (layout.lines.isEmpty()) return;

//        baseY += workingLayout.lines.first().height * 0.25f;

        int o = 0, s = 0, r = 0, gi = 0;
        boolean resetShader = font.distanceField != Font.DistanceFieldType.STANDARD && batch.getShader() != font.shader;
        if (resetShader)
            font.enableShader(batch);

        baseX -= 0.5f * font.cellWidth;
        baseY -= 0.5f * font.cellHeight;

        baseX += cs * 0.5f * font.cellWidth;
        baseY += sn * 0.5f * font.cellWidth;
        baseX -= sn * 0.5f * font.cellHeight;
        baseY += cs * 0.5f * font.cellHeight;

        int globalIndex = -1;
        lastTouchedIndex = -1;
        overIndex = -1;

        EACH_LINE:
        for (int ln = 0; ln < lines; ln++) {
            Line glyphs = workingLayout.getLine(ln);

            baseX += sn * glyphs.height;
            baseY -= cs * glyphs.height;
            if(glyphs.glyphs.size == 0)
                continue;

            float x = baseX, y = baseY;

            final float worldOriginX = x + originX;
            final float worldOriginY = y + originY;
            float fx = -originX;
            float fy = -originY;
            x = cs * fx - sn * fy + worldOriginX;
            y = sn * fx + cs * fy + worldOriginY;


            float single, xChange = 0, yChange = 0;

            if (Align.isCenterHorizontal(align)) {
                x -= cs * (glyphs.width * 0.5f);
                y -= sn * (glyphs.width * 0.5f);
            } else if (Align.isRight(align)) {
                x -= cs * glyphs.width;
                y -= sn * glyphs.width;
            }

            Font f = null;
            int kern = -1;
            for (int i = 0, n = glyphs.glyphs.size, end = glyphCharIndex,
                 lim = Math.min(Math.min(rotations.size, offsets.size >> 1), sizing.size >> 1);
                 i < n && r < lim; i++, gi++) {
                if (gi > end) break EACH_LINE;
                long glyph = glyphs.glyphs.get(i);
                if (font.family != null) f = font.family.connected[(int) (glyph >>> 16 & 15)];
                if (f == null) f = font;
                if (f.kerning != null) {
                    kern = kern << 16 | (int) ((glyph = glyphs.glyphs.get(i)) & 0xFFFF);
                    float amt = f.kerning.get(kern, 0) * f.scaleX * ((glyph + 0x300000L >>> 20 & 15) + 1) * 0.25f;
                    xChange += cs * amt;
                    yChange += sn * amt;
                } else {
                    kern = -1;
                }
                if(i == 0) {
                    Font.GlyphRegion reg = font.mapping.get((char) glyph);
                    if (reg != null && reg.offsetX < 0) {
                        float ox = reg.offsetX * f.scaleX * ((glyph + 0x300000L >>> 20 & 15) + 1) * 0.25f;
                        xChange -= cs * ox;
                        yChange -= sn * ox;
                    }
                }
                ++globalIndex;
                single = f.drawGlyph(batch, glyph, x + xChange + offsets.get(o++), y + yChange + offsets.get(o++), rotations.get(r++) + rot, sizing.get(s++), sizing.get(s++));
                if(trackingInput){
                    int inX = Gdx.input.getX();
                    int inY = Gdx.graphics.getBackBufferHeight() - Gdx.input.getY();//
                    float xx = x + xChange + offsets.get(o-2), yy = y + yChange + offsets.get(o-1);
                    if(xx <= inX && inX <= xx + single && yy <= inY && inY <= yy + glyphs.height){
                        overIndex = globalIndex;
                        if(Gdx.input.justTouched() && isTouchable())
                            lastTouchedIndex = globalIndex;
                    }
                }
                xChange += cs * single;
                yChange += sn * single;
            }

        }
        invalidateHierarchy();
//        addMissingGlyphs();
        if (resetShader)
            batch.setShader(null);
    }

    @Override
    public String toString() {
        return workingLayout.toString();
    }

    public void setIntermediateText(CharSequence text, boolean modifyOriginalText, boolean restart) {
        final boolean hasEnded = this.hasEnded();
        if (text != intermediateText) {
            intermediateText.setLength(0);
            intermediateText.append(text);
        }
        intermediateText.trimToSize();
        if (modifyOriginalText) saveOriginalText(text);
        if (restart) {
            this.restart();
        }
        if (hasEnded) {
            this.skipToTheEnd(true, false);
        }
    }

    public StringBuilder getIntermediateText() {
        return intermediateText;
    }

    public long getInLayout(Layout layout, int index) {
        for (int i = 0, n = layout.lines(); i < n && index >= 0; i++) {
            LongArray glyphs = layout.getLine(i).glyphs;
            if (index < glyphs.size)
                return glyphs.get(index);
            else
                index -= glyphs.size;
        }
        return 0xFFFFFFL;
    }

    public Line getLineInLayout(Layout layout, int index) {
        for (int i = 0, n = layout.lines(); i < n && index >= 0; i++) {
            LongArray glyphs = layout.getLine(i).glyphs;
            if (index < glyphs.size)
                return layout.getLine(i);
            else
                index -= glyphs.size;
        }
        return null;
    }

    public float getLineHeight(int index) {
        for (int i = 0, n = workingLayout.lines(); i < n && index >= 0; i++) {
            LongArray glyphs = workingLayout.getLine(i).glyphs;
            if (index < glyphs.size)
                return workingLayout.getLine(i).height;
            else
                index -= glyphs.size;
        }
        return font.cellHeight;
    }

    public long getFromIntermediate(int index) {
        if (index >= 0 && intermediateText.length() > index) return intermediateText.charAt(index);
        else return 0xFFFFFFL;
    }

    public void setInLayout(Layout layout, int index, long newGlyph) {
        for (int i = 0, n = layout.lines(); i < n && index >= 0; i++) {
            LongArray glyphs = layout.getLine(i).glyphs;
            if (index < glyphs.size) {
                glyphs.set(index, newGlyph);
                return;
            } else
                index -= glyphs.size;
        }
    }

    public void setInWorkingLayout(int index, long newGlyph) {
        for (int i = 0, n = layout.lines(); i < n && index >= 0; i++) {
            LongArray glyphs = workingLayout.getLine(i).glyphs;
            if (i < workingLayout.lines() && index < glyphs.size) {
                glyphs.set(index, newGlyph);
                return;
//            LongArray glyphs = layout.getLine(i).glyphs;
//            if(index < glyphs.size) {
//                char old = (char) glyphs.get(index);
//                glyphs.set(index, newGlyph);
//                if(i < workingLayout.lines() && index < workingLayout.getLine(i).glyphs.size) {
//                    char work;
//                    if((work = (char)workingLayout.getLine(i).glyphs.get(index)) != old)
//                        System.out.println("Different: " + old + " ! => " + work + " at index " + index);
//                    workingLayout.getLine(i).glyphs.set(index, newGlyph);
//                }
//                return;
            } else
                index -= glyphs.size;
        }
    }

    /**
     * Triggers an event with the given String name. If {@code always} is true, this will trigger the event even if the
     * typing animation has already ended. This requires a {@link TypingListener} to be set.
     * @param event the event name to trigger
     * @param always if true, the event will be triggered even if the animation has finished.
     */
    public void triggerEvent(String event, boolean always) {
        if (this.listener != null && (always || !ignoringEvents)) {
            listener.event(event);
        }
    }
}
