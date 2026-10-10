# Steampunk Dragon watch face

Clockwork brass and silver, with a red dragon coiled around the bezel. Made for Ren Faire.
Sized for the Galaxy Watch 8 Classic (round screen, 438×438).

## The app (moving version)
`app/dist/ClockworkDragon.apk` is a real Wear OS watch face (Watch Face Format, no code).
- The gears and the hairspring turn, the second hand sweeps smoothly, the dragon's eye glows and pulses,
  and smoke curls from its nostrils.
- **Always On:** the whole face stays visible at about 40% brightness. The second hand and smoke hide to save battery.

### Installing over wireless debugging
1. Watch: Settings → About watch → Software → tap *Software version* 5 times to turn on Developer options.
2. Watch: Settings → Developer options → turn on **ADB debugging** and **Wireless debugging**.
3. Phone: open your ADB app (Bugjaeger, Wear Installer 2, etc.), pair with the code the watch shows, then install
   `ClockworkDragon.apk`. From a computer: `adb install ClockworkDragon.apk`.
4. Watch: long-press the current face → swipe to **+ Add** → pick **Clockwork Dragon**
   (or Galaxy Wearable app → Watch faces → Downloaded).
5. For the always-on look: Settings → Display → **Always On Display** → on.

Rebuild with `ANDROID_JAR=/path/to/android.jar python3 app/build_app.py`
(needs aapt, zipalign, apksigner, ImageMagick).

## Files
- `png/preview_*.png`: the full face with hands, for showing it off
- `png/dial_450.png`: the face with no hands. Use this as a photo watch face.
- `png/hand_hour_*.png`, `hand_minute_*`, `hand_second_*`, `center_cap_*`: separate hand layers, each
  pointing at 12 and pivoting on the image center. Use these to get moving hands in Facer.
- `build.py` rebuilds everything (`python3 build.py`); `svg/` holds the vector sources.

## Putting it on the watch
**Quickest (about 2 minutes):** save `dial_450.png` to your phone, then open Galaxy Wearable →
Watch faces → **My Photo+** (or **Photos**) → add the image. The watch draws the time over it.

**Moving brass hands:** in the Facer Creator (facer.io/creator, free), make a round face, set
`dial_1000.png` as the background, then add `hand_hour_1000.png`, `hand_minute_1000.png` and
`hand_second_1000.png` as full-size images and give each one the matching rotation
(hour, minute or second). Put `center_cap_1000.png` on top. Sync it to the watch with the Facer app.

Fonts: Cinzel and UnifrakturMaguntia (SIL Open Font License).
