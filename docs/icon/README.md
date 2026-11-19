# Launcher icon review

Variant **A** is wired provisionally. All artwork is original geometric vector
art, under the app's GPL-3.0-only license. No font or external artwork is used.

| Variant | Idea | Stable | Beta |
| --- | --- | --- | --- |
| A | Crossed blue/red sticks over a snare-head rim | ![A](variants/a-circle.png) | ![A beta](variants/a-beta-circle.png) |
| B | A flam: small blue grace note leading a red main note | ![B](variants/b-circle.png) | ![B beta](variants/b-beta-circle.png) |
| C | An R built from a blue stick and a red bowl/diagonal | ![C](variants/c-circle.png) | ![C beta](variants/c-beta-circle.png) |
| D | Alternating hand notes descending to a hit line | ![D](variants/d-circle.png) | ![D beta](variants/d-beta-circle.png) |

Each candidate directory contains a 108 dp Android vector foreground, a white
monochrome vector and stable/beta background drawables. Foreground artwork fits
inside the central 66 dp safe circle (radius 33 about 54,54). Stable backgrounds
are slate blue; beta backgrounds are warm brown. Beta changes only the background;
Android's themed icons deliberately use the same monochrome silhouette.

## Reproduce previews

Run `bash scripts/render-icon-variants.sh` with `rsvg-convert` (librsvg) installed.
It translates the Android paths directly to SVG, preserving even-odd fills, and
produces sixteen 512×512 PNGs: full-bleed and circle-masked stable/beta previews
for each candidate. These show the entire 108 dp layer; a real launcher's viewport,
mask and motion can change the apparent scale. They are artwork previews, not
store screenshots or an Android launcher capture.

## Select a candidate

Copy the chosen candidate's `foreground.xml`, `monochrome.xml`, and
`background.xml` to `app/src/main/res/drawable/ic_launcher_{foreground,monochrome,background}.xml`
respectively. Copy `beta-background.xml` to
`app/src/beta/res/drawable/ic_launcher_background.xml`. The shared adaptive icon
already references all three layers. Other candidate layers are review-only and
are not packaged in the APK. Remove rejected candidates/previews after selection.
