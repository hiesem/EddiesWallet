# EddiesWallet design source

The files in [`source/`](source/) are a preserved copy of `Eddie's Wallet overview.zip`, supplied as the visual authority for the Android v1 implementation. The `.dc.html` files are design canvases and `android-frame.jsx` is the reference Android frame. They are documentation only: the Android app does not load, bundle, or depend on the HTML runtime or its CDN font links.

The selected launcher direction is **1e — Big friendly E**: an Eddie-coral tile, cream dotted background, and a centered tilted E. The same mark is used for the Android adaptive icon and the child avatar treatment.

## Android token mapping

Source values are preserved as OKLCH in the canvas. The hex values below are the sRGB equivalents used by `app/src/main/kotlin/com/eddieswallet/ui/theme/Color.kt`; the source expression is included to make the mapping auditable.

| Token | Source | Android |
| --- | --- | --- |
| Warm cream background | `oklch(0.955 0.016 80)` | `#F6EFE4` |
| Surface | `oklch(0.99 0.004 85)` | `#FDFCF9` |
| Ink | `oklch(0.28 0.02 60)` | `#30271F` |
| Secondary ink | `oklch(0.53 0.02 70)` | `#746A60` |
| Line | `oklch(0.89 0.012 80)` | `#DFDAD2` |
| Parent primary | `oklch(0.68 0.15 55)` | `#DD7B2B` |
| Eddie coral / icon tile | `oklch(0.68 0.17 30)` | `#EF6856` |
| Teal / Save Jar | `oklch(0.68 0.15 175)` | `#00B593` |
| Yellow / Spending Jar | `oklch(0.72 0.16 75)` | `#DE9300` |
| Purple / owed | `oklch(0.62 0.16 300)` | `#966CD7` |
| Child dotted cream | `oklch(0.975 0.04 95)` | `#FFF7D9` |
| Child surface | `oklch(0.99 0.02 95)` | `#FFFCED` |

The canvas establishes 14/18/20/22/26/30dp card families, 999px pills, 8–18px content spacing, and low offset shadows (typically 1–6px). Compose tokens centralize those values as `EddieShapes`, `EddieSpacing`, and `EddieElevation`. Nunito is the display direction (friendly heavy numbers/headings); IBM Plex Sans is the body/UI direction. Their variable font files are packaged locally under `app/src/main/res/font`; Android can fall back to a system sans if a font cannot load, still without a network dependency.

## Source provenance

Copied from `/home/killerhis/Projects/kunchen/firstmate/projects/EddiesWallet/Eddie's Wallet overview.zip` on 2026-09-01. No backend, credentials, production data, or external service is included here.
