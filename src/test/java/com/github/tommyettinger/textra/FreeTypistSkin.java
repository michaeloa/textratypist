/*
 * Copyright (c) 2024 See AUTHORS file.
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
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.SerializationException;

/**
 * A sublass of {@link Skin} that includes a serializer for FreeType fonts from JSON. These JSON's are typically exported by
 * Skin Composer. This can also load Font and BitmapFont objects from .fnt files or .json files made by FontWriter. See the
 * <a href="https://github.com/raeleus/skin-composer/wiki/Creating-FreeType-Fonts#using-a-custom-serializer">Skin Composer documentation</a>.
 * If you are using Asset Manager, use {@link FreeTypistSkinLoader}
 */
public class FreeTypistSkin extends Skin {
    /** Creates an empty skin. */
    public FreeTypistSkin() {
    }
    
    /** Creates a skin containing the resources in the specified skin JSON file. If a file in the same directory with a ".atlas"
     * extension exists, it is loaded as a {@link TextureAtlas} and the texture regions added to the skin. The atlas is
     * automatically disposed when the skin is disposed.
     * @param  skinFile The JSON file to be read.
     */
    public FreeTypistSkin(FileHandle skinFile) {
        super(skinFile);
        
    }
    
    /** Creates a skin containing the resources in the specified skin JSON file and the texture regions from the specified atlas.
     * The atlas is automatically disposed when the skin is disposed.
     * @param skinFile The JSON file to be read.
     * @param atlas The texture atlas to be associated with the {@link Skin}.
     */
    public FreeTypistSkin(FileHandle skinFile, TextureAtlas atlas) {
        super(skinFile, atlas);
    }
    
    /** Creates a skin containing the texture regions from the specified atlas. The atlas is automatically disposed when the skin
     * is disposed.
     * @param atlas The texture atlas to be associated with the {@link Skin}.
     */
    public FreeTypistSkin(TextureAtlas atlas) {
        super(atlas);
    }
    
    /**
     * Overrides the default JSON loader to process FreeType fonts from a Skin JSON.
     * @param skinFile The JSON file to be processed.
     * @return The {@link Json} used to read the file.
     */
    @Override
    protected Json getJsonLoader(final FileHandle skinFile) {
        Json json = super.getJsonLoader(skinFile);
        final Skin skin = this;

        json.setSerializer(Font.class, new Json.ReadOnlySerializer<Font>() {
            @Override
            public Font read(Json json, JsonValue jsonData, Class type) {
                String path = json.readValue("file", String.class, jsonData);

                FileHandle fontFile = skinFile.sibling(path);
                if (!fontFile.exists()) fontFile = Gdx.files.internal(path);
                if (!fontFile.exists()) throw new SerializationException("Font file not found: " + fontFile);

                path = fontFile.path();

                boolean fw = path.endsWith(".json");
                float scaledSize = json.readValue("scaledSize", float.class, -1f, jsonData);
                float xAdjust = json.readValue("xAdjust", float.class, 0f, jsonData);
                float yAdjust = json.readValue("yAdjust", float.class, 0f, jsonData);
                float widthAdjust = json.readValue("widthAdjust", float.class, 0f, jsonData);
                float heightAdjust = json.readValue("heightAdjust", float.class, 0f, jsonData);
                Boolean useIntegerPositions = json.readValue("useIntegerPositions", Boolean.class, false, jsonData);
                Boolean makeGridGlyphs = json.readValue("makeGridGlyphs", Boolean.class, true, jsonData);


                // Use a region with the same name as the font, else use a PNG file in the same directory as the FNT file.
                String regionName = path.substring(Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'))+1, path.lastIndexOf('.'));
                try {
                    Font font;
                    Array<TextureRegion> regions = skin.getRegions(regionName);
                    if (regions != null && regions.notEmpty()) {
                        if(fw)
                            font = new Font(path, regions.first(), xAdjust, yAdjust, widthAdjust, heightAdjust, makeGridGlyphs, true);
                        else
                            font = new Font(path, regions, Font.DistanceFieldType.STANDARD, xAdjust, yAdjust, widthAdjust, heightAdjust, makeGridGlyphs);
                    } else {
                        TextureRegion region = skin.optional(regionName, TextureRegion.class);
                        if (region != null)
                        {
                            if(fw)
                                font = new Font(path, region, xAdjust, yAdjust, widthAdjust, heightAdjust, true, true);
                            else
                                font = new Font(path, region, Font.DistanceFieldType.STANDARD, xAdjust, yAdjust, widthAdjust, heightAdjust, makeGridGlyphs);
                        }
                        else {
                            FileHandle imageFile = Gdx.files.internal(path).sibling(regionName + ".png");
                            if (imageFile.exists()) {
                                if(fw)
                                    font = new Font(path, new TextureRegion(new Texture(imageFile)), xAdjust, yAdjust, widthAdjust, heightAdjust, makeGridGlyphs, true);
                                else
                                    font = new Font(path, new TextureRegion(new Texture(imageFile)), Font.DistanceFieldType.STANDARD, xAdjust, yAdjust, widthAdjust, heightAdjust, makeGridGlyphs);
                            } else {
                                if(fw)
                                    throw new RuntimeException("Missing image file or TextureRegion.");
                                else
                                    font = new Font(path);
                            }
                        }
                    }
                    font.useIntegerPositions(useIntegerPositions);
                    // Scaled size is the desired cap height to scale the font to.
                    if (scaledSize != -1) font.scaleTo(font.originalCellWidth * scaledSize / font.originalCellHeight, scaledSize);
                    return font;
                } catch (RuntimeException ex) {
                    throw new SerializationException("Error loading bitmap font: " + path, ex);
                }
            }
        });

        json.setSerializer(BitmapFont.class, new Json.ReadOnlySerializer<BitmapFont>() {
            public BitmapFont read (Json json, JsonValue jsonData, Class type) {
                String path = json.readValue("file", String.class, jsonData);

                FileHandle fontFile = skinFile.sibling(path);
                if (!fontFile.exists()) fontFile = Gdx.files.internal(path);
                if (!fontFile.exists()) throw new SerializationException("Font file not found: " + fontFile);

                boolean fw = "json".equals(fontFile.extension());

                float scaledSize = json.readValue("scaledSize", float.class, -1f, jsonData);
                Boolean flip = json.readValue("flip", Boolean.class, false, jsonData);
                Boolean markupEnabled = json.readValue("markupEnabled", Boolean.class, false, jsonData);
                // This defaults to true if loading from .fnt, or false if loading from .json :
                Boolean useIntegerPositions = json.readValue("useIntegerPositions", Boolean.class, !fw, jsonData);

                // Use a region with the same name as the font, else use a PNG file in the same directory as the FNT file.
                String regionName = fontFile.nameWithoutExtension();
                try {
                    BitmapFont font;
                    Array<TextureRegion> regions = skin.getRegions(regionName);
                    if (regions != null && regions.notEmpty()) {
                        if(fw)
                            font = BitmapFontSupport.loadStructuredJson(fontFile, regions.first(), flip);
                        else
                            font = new BitmapFont(new BitmapFont.BitmapFontData(fontFile, flip), regions, true);
                    } else {
                        TextureRegion region = skin.optional(regionName, TextureRegion.class);
                        if (region != null)
                        {
                            if(fw)
                                font = BitmapFontSupport.loadStructuredJson(fontFile, region, flip);
                            else
                                font = new BitmapFont(fontFile, region, flip);
                        }
                        else {
                            FileHandle imageFile = fontFile.sibling(regionName + ".png");
                            if (imageFile.exists()) {
                                if(fw)
                                    font = BitmapFontSupport.loadStructuredJson(fontFile,
                                            new TextureRegion(new Texture(imageFile)), flip);
                                else
                                    font = new BitmapFont(fontFile, imageFile, flip);
                            } else {
                                if(fw)
                                    font = BitmapFontSupport.loadStructuredJson(fontFile, "", flip);
                                else
                                    font = new BitmapFont(fontFile, flip);
                            }
                        }
                    }
                    font.getData().markupEnabled = markupEnabled;
                    font.setUseIntegerPositions(useIntegerPositions);
                    // Scaled size is the desired cap height to scale the font to.
                    if (scaledSize != -1) font.getData().setScale(scaledSize / font.getCapHeight());
                    return font;
                } catch (RuntimeException ex) {
                    throw new SerializationException("Error loading bitmap font: " + fontFile, ex);
                }
            }
        });

        json.setSerializer(FreeTypeFontGenerator.class, new Json.ReadOnlySerializer<FreeTypeFontGenerator>() {
            @Override
            public FreeTypeFontGenerator read(Json json,
                                              JsonValue jsonData, Class type) {
                String path = json.readValue("font", String.class, jsonData);
                jsonData.remove("font");

                FreeTypeFontGenerator.Hinting hinting = FreeTypeFontGenerator.Hinting.valueOf(json.readValue("hinting",
                        String.class, "Medium", jsonData));
                jsonData.remove("hinting");

                Texture.TextureFilter minFilter = Texture.TextureFilter.valueOf(
                        json.readValue("minFilter", String.class, "Nearest", jsonData));
                jsonData.remove("minFilter");

                Texture.TextureFilter magFilter = Texture.TextureFilter.valueOf(
                        json.readValue("magFilter", String.class, "Linear", jsonData));
                jsonData.remove("magFilter");

                FreeTypeFontGenerator.FreeTypeFontParameter parameter = json.readValue(FreeTypeFontGenerator.FreeTypeFontParameter.class, jsonData);
                parameter.hinting = hinting;
                parameter.minFilter = minFilter;
                parameter.magFilter = magFilter;
                FreeTypeFontGenerator generator = new FreeTypeFontGenerator(skinFile.sibling(path));
                FreeTypeFontGenerator.setMaxTextureSize(FreeTypeFontGenerator.NO_MAXIMUM);
                BitmapFont font = generator.generateFont(parameter);
                skin.add(jsonData.name, font);
                skin.add(jsonData.name, new Font(font));
                if (parameter.incremental) {
                    generator.dispose();
                    return null;
                } else {
                    return generator;
                }
            }
        });

        return json;
    }
}
