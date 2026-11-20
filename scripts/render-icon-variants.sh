#!/usr/bin/env bash
# Render the selected icon from its Android vector paths. No Android SDK needed.
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."
command -v rsvg-convert >/dev/null
scratch=$(mktemp -d)
trap 'rm -rf "$scratch"' EXIT

for variant in a; do
    dir="docs/icon/variants/$variant"
    for channel in stable beta; do
        background="$dir/background.xml"
        suffix=""
        if [[ "$channel" == beta ]]; then
            background="$dir/beta-background.xml"
            suffix="-beta"
        fi
        color=$(sed -n 's/.*android:color="\([^"]*\)".*/\1/p' "$background")
        for mask in full circle; do
            output="docs/icon/variants/$variant$suffix"
            [[ "$mask" == circle ]] && output="$output-circle"
            {
                echo '<svg xmlns="http://www.w3.org/2000/svg" width="512" height="512" viewBox="0 0 108 108">'
                if [[ "$mask" == circle ]]; then
                    echo '<defs><clipPath id="mask"><circle cx="54" cy="54" r="54"/></clipPath></defs><g clip-path="url(#mask)">'
                else
                    echo '<g>'
                fi
                echo "<path fill=\"$color\" d=\"M0,0H108V108H0Z\"/>"
                # Android and SVG share path syntax. Preserve even-odd holes.
                sed -n '/<path /{s/android:fillColor=/fill=/g;s/android:pathData=/d=/g;s/android:fillType=/fill-rule=/g;s/evenOdd/evenodd/g;p;}' "$dir/foreground.xml"
                echo '</g></svg>'
            } > "$scratch/icon.svg"
            rsvg-convert "$scratch/icon.svg" -o "$output.png"
        done
    done
done
