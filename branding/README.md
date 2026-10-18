# TN-Player supplied gold/cyan identity — 18 October 2026

`TN-Player-logo-original.png` is the **unchanged user upload**, including the TN-Player and TN-MuzZify wordmarks. `source-manifest.json` records its SHA-256. The former JPEG identity is retained only as historical source; active branding uses the new artwork.

`TN-Player-icon-master.png` is the emblem adaptation made with the built-in image editing tool: gold headphones, cyan/blue play triangle, gold TN letters and musical note, with the lower text removed for launcher legibility. The supplied original remains the full brand image in Settings/About and the website hero.

Packaged consumers:

- Android legacy icons: 48/72/96/144/192 px for mdpi–xxxhdpi.
- Android 8+ adaptive icon: near-black background, emblem inset for system masks.
- Android 13+ monochrome icon: simplified native vector for themed launcher tinting.
- Toolbar, settings, widget and shortcuts: shared Android icon resources.
- Website favicon, Apple touch icon, header, full hero artwork and icon download.
- README: shared emblem artwork.

Run `python3 scripts/prepare-branding.py ORIGINAL_PNG ICON_MASTER_PNG` with Pillow installed to reproduce the deterministic resize/package step. Use the supplied source files as inputs from another directory, since the script copies masters into this folder. Packaging only resamples approved assets; it does not generate a new design.

## Adaptation prompt

Keep the central gold headphone arch, cyan/blue play triangle with gold TN letters, gold/cyan musical note and cyan equalizer bars. Preserve shapes, metallic finish, proportions and colors. Remove the lower TN-Player/TN-MuzZify text and decorative rules. Center the emblem on a near-black square with generous adaptive-mask margins. No new symbols, words, borders or watermark.
