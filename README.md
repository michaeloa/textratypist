# TextraTypist
![Animated preview](images/logo_animated.gif)

A text-display library centered around a label that prints over time, with both effects and styles.

In other words, this brings more features to text rendering in libGDX.

What does this look like? A little something like this...

![Still preview](https://i.imgur.com/DOZ222M.png)

Or perhaps like this...

![Animated preview](images/preview.gif)

## It's got labels!

There's a "normal" label here in the form of TextraLabel, which acts almost exactly like Label in scene2d.ui, but
allows the styles covered below. A lot of usage may prefer TypingLabel, though!

TypingLabel is a fairly normal scene2d.ui widget, and extends TextraLabel. However, it puts letters up on
the screen one at a time, unless it is told to skip ahead. This is a nostalgic effect found in many older text-heavy
games, and it looks like a typewriter is putting up each letter at some slower-than-instantaneous rate.

## It's got effects!

Yes, it has more than the typewriter mode! Text can hang above and then drop into place. It can jump up and down in a
long wave. It can waver and shudder, as if it is sick. It can blink in different colors, move in a gradient smoothly
between two colors, or go across a whole rainbow. Lots of options; lots of fun. Effects are almost the same as in
typing-label, so you should consult [its documentation](https://github.com/rafaskb/typing-label/wiki/Examples) for now.

The one new effect so far is `{JOLT}`, which randomly chooses glyphs over time to get "shocked," shaking them as with
the `{SHAKE}` effect briefly and optionally changing their color while shocked. It takes distance (often 1), intensity
(often 1), duration (often "inf" to make it last forever), likelihood (a low float between 0 and 1, with higher values
making more glyphs get shocked), base color and jolting color.

I'll probably clone the typing-label wiki at some point and update the relevant parts for the way this library works,
but I haven't done this yet.

## And now, it's got style!

This library extends what the original typing-label can do -- it allows styles to be applied to text, such as bold,
underline, oblique, superscript, etc. Related to styles are scale changes, which can shrink or enlarge text without
changing your font, and the "font family" feature. A font can be assigned a "family" of other fonts and names to use to
refer to them; this acts like a normal style, but actually changes what Font is used to draw. The full list of styles is
long, but isn't as detailed as the effect tokens. You can enable styles with something like libGDX color markup, in
square brackets like `[*]`, or you can use `{STYLE=BOLD}` to do the same thing. The full list of styles:

- `[*]` toggles bold mode. Can use style names `*`, `B`, `BOLD`, `STRONG`.
- `[/]` toggles oblique mode (like italics). Can use style names `/`, `I`, `OBLIQUE`, `ITALIC`.
- `[^]` toggles superscript mode (and turns off subscript or midscript mode). Can use style names `^`, `SUPER`, `SUPERSCRIPT`.
- `[=]` toggles midscript mode (and turns off superscript or subscript mode). Can use style names `=`, `MID`, `MIDSCRIPT`.
- `[.]` toggles subscript mode (and turns off superscript or midscript mode). Can use style names `.`, `SUB`, `SUBSCRIPT`.
- `[_]` toggles underline mode. Can use style names `_`, `U`, `UNDER`, `UNDERLINE`.
- `[~]` toggles strikethrough mode. Can use style names `~`, `STRIKE`, `STRIKETHROUGH`.
- `[!]` toggles all upper case mode (replacing any other case mode). Can use style names `!`, `UP`, `UPPER`.
- `[,]` toggles all lower case mode (replacing any other case mode). Can use style names `,`, `LOW`, `LOWER`.
- `[;]` toggles capitalize each word mode (replacing any other case mode). Can use style names `;`, `EACH`, `TITLE`.
- `[%DDD]`, where DDD is a percentage from 0 to 375, scales text to that multiple. Can be used with `{SIZE=150%}`, `{SIZE=%25}`, or similarly `{STYLE=200%}`.
- `[%]` on its own sets text to the default 100% scale.
- `[@Name]`, where Name is a key/name in this Font's `family` variable, switches the current typeface to the named one.
- `[@]` on its own resets the typeface to this Font, ignoring its family.
- `[#HHHHHHHH]`, where HHHHHHHH is a hex RGB888 or RGBA8888 int color, changes the color. This is a normal `{COLOR=#HHHHHHHH}` tag.
- `[COLORNAME]`, where COLORNAME is a typically-upper-case color name that will be looked up externally, changes the color.
  - By default, this looks up COLORNAME in libGDX's `Colors` class, but it can be configured to create colors differently.
    - You can use `Font`'s `setColorLookup()` method with your own `ColorLookup` implementation to do what you want here.
    - `TypingLabel` does still try to look up color names in `Colors`, but will fall back to using whatever `ColorLookup`
         a `Font` uses. You can clear the known names with `Colors.getColors().clear()`, which will force a `ColorLookup` to be used.
  - The name can optionally be preceded by `|`, which allows looking up colors with names that contain punctuation.
      For example, `[|;_;]` would look up a color called `;_;`, "the color of sadness," and would not act like `[;]`.
  - This also can be used with a color tag, such as `{COLOR=SKY}` (which Colors can handle right away) or
    `{COLOR=Lighter Orange-Red}` (which would need that color to be defined).

## But wait, there's fonts!

Textratypist makes heavy use of its new `Font` class, which is a full overhaul of libGDX's BitmapFont that shares
essentially no code with its ancestor. A Font has various qualities that give it more power than BitmapFont, mostly
derived from how it stores (and makes available) the glyph images as TextureRegions in a map. There's nothing strictly
preventing you from adding your own images to the `mapping` of a Font, as long as they have the requisite information to
be used as a textual glyph, and then placing those images in with your text. Textratypist supports standard bitmap
fonts and also distance field fonts, using SDF or MSDF. `TypingLabel` will automatically enable the ShaderProgram that
the appropriate distance field type needs (if it needs one) and disable it after rendering itself. You can change this
behavior by manually calling the `Font.enableShader(Batch)` method on your Font, and changing the Batch back to your
other ShaderProgram of choice with its `Batch.setShader()` method (often, you just pass null here to reset the shader).

There are several preconfigured font settings in `KnownFonts`; the documentation for each font getter says what files
are needed to use that font. This is meant to save some hassle getting the xAdjust, yAdjust, widthAdjust, 
and heightAdjust parameters just right, though you're still free to change them however you wish. The license files for
each font are included in the same folder, in `knownFonts` here. All fonts provided here were checked to ensure their
licenses permit commercial use without fees, and all do. Most require attribution; check the licenses for details. The
variety of font types isn't amazing, but it should be a good starting point.

## Act now and get these features, free of charge!

You can rotate individual glyphs (if you draw them individually) or rotate whole blocks of text as a Layout, using an
optional overload of `Font.drawGlyph()` or `Font.drawGlyphs()`. You can also, for some fonts, have box-drawing
characters and block elements be automatically generated. This needs a solid white block character (of any size,
typically 1x1) present in the font at id 0 (used here because most fonts don't use it) or 9608 (the Unicode full block
index). This also enables a better guarantee of underline and strikethrough characters connecting properly, and without
smudging where two underscores or hyphens overlap each other. `Font` attempts to enable this by default, but if it fails
then it falls back to using underscores for underline and hyphens for strikethrough. All of the fonts in `KnownFonts`
either are configured to use a solid block or to specifically avoid it because that font renders better without it.

These two features are new in 0.3.0, and are expected to see more attention in future releases (such as more
configuration for rotation origin).

## Hold the phone, there's widgets!

Starting in the 0.4.0 release (in development), there are various widgets that replace their
scene2d.ui counterparts and swap out `Label` for `TextraLabel`, allowing you to use markup in them.
The widgets are `ImageTextraButton`, `TextraButton`, `TextraCheckBox`, `TextraDialog`, `TextraLabel`, `TextraTooltip`, 
and `TextraWindow`, at least, so far.

Future additions to these widgets should permit setting the `TextraLabel` to a `TypingLabel` of your choice.
While `TextArea` is not yet supported, `TextraLabel` defaults to supporting multiple lines, and may be able to stand-in
for some usage. A counterpart to `TextArea` is planned.

## How do I get it?

You probably want to get this with Gradle! The dependency for a libGDX project's core module looks like:

```groovy
implementation "com.github.tommyettinger:textratypist:0.3.0"
```

This assumes you already depend on libGDX; TextraTypist depends on version 1.10.0 or higher, and should have no problems
updating to 1.10.1-SNAPSHOT or 1.10.1 when it is released. This is different from typing-label, which unfortunately had
the rug pulled out from under it by changes in libGDX's font rendering code during the 1.10.1-SNAPSHOT period, and needs
a different version for 1.10.1 (-SNAPSHOT) and higher vs. 1.10.0 .

If you use GWT, this should be compatible. It needs these dependencies in the html module:

```groovy
implementation "com.github.tommyettinger:textratypist:0.3.0:sources"
implementation "com.github.tommyettinger:regexodus:0.1.13:sources"
```

GWT also needs this in the GdxDefinition.gwt.xml file:
```xml
<inherits name="regexodus" />
<inherits name="textratypist" />
```

RegExodus is the GWT-compatible regular-expression library this uses to match some complex patterns internally. Other
than libGDX itself, RegExodus is the only dependency this project has.

There is at least one release in the [Releases](https://github.com/tommyettinger/textratypist/releases) section of this
repo, but you're still encouraged to use Gradle to handle this library and its dependencies.

## License

This is based very closely on [typing-label](https://github.com/rafaskb/typing-label), by Rafa Skoberg.
Typing-label is MIT-licensed according to its repo `LICENSE` file, but (almost certainly unintentionally) does not
include any license headers in any files. Since the only requirement of the MIT license is to leave any license text
as-is, this Apache-licensed project is fully compliant with MIT. The full MIT license text is in the file
`typing-label.LICENSE`, and the Apache 2 license for this project is in the file `LICENSE`. Apache license headers are
also present in all library source files here. The Apache license does not typically apply to non-code resources in the
`src/test/resources` folder; individual fonts have their own licenses stored in that directory.

The logo was made by Raymond "raeleus" Buckley and contributed to this project. It can be used freely for any purpose,
but I request that it only be used to refer to this project unless substantially modified.
