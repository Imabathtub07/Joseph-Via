#!/usr/bin/env python3
"""Builds the Clockwork Dragon watch face as a Wear OS app (Watch Face Format, no code).

Steps: render each moving part to its own PNG, write the Watch Face Format XML,
then package, align and sign an APK with aapt / zipalign / apksigner.

Needs: headless Chromium (see ../build.py), ImageMagick, aapt, zipalign, apksigner,
keytool, and an android.jar (API 33+) passed via ANDROID_JAR.
"""
import os
import shutil
import subprocess
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.dirname(HERE))
import build as art  # noqa: E402

SIZE = 450  # watch face canvas; the watch scales it to its 438px screen
K = SIZE / 1000  # art is drawn on a 1000-unit canvas
RES = os.path.join(HERE, "res")
DRAW = os.path.join(RES, "drawable-nodpi")
WORK = os.path.join(HERE, "build")
DIST = os.path.join(HERE, "dist")
APK_NAME = "ClockworkDragon.apk"

# Always-on display: a black veil fades in over the whole face so it shows at about
# 40% brightness. 0 = full brightness, 255 = black.
AMBIENT_DIM = 155


def render_layer(name, content, crop=True):
    """Render one layer at SIZE px; optionally trim to its bounding box. Returns (x, y, w, h)."""
    svg_path = os.path.join(WORK, f"{name}.svg")
    with open(svg_path, "w") as fh:
        fh.write(art.svg(content))
    html = os.path.join(WORK, f"{name}.html")
    with open(html, "w") as fh:
        fh.write(
            "<!doctype html><html><head><style>html,body{margin:0;background:transparent}"
            f"img{{width:{SIZE}px;height:{SIZE}px;display:block}}</style></head>"
            f'<body><img src="{name}.svg"></body></html>'
        )
    out = os.path.join(DRAW, f"{name}.png")
    subprocess.run(
        [art.CHROME, "--no-sandbox", "--disable-gpu", "--hide-scrollbars",
         "--allow-file-access-from-files", "--default-background-color=00000000",
         f"--window-size={SIZE},{SIZE}", "--virtual-time-budget=3000", f"--screenshot={out}",
         f"file://{html}"],
        check=True, capture_output=True,
    )
    if not crop:
        return 0, 0, SIZE, SIZE
    geom = subprocess.run(["convert", out, "-format", "%@", "info:"], check=True, capture_output=True, text=True).stdout
    wh, x, y = geom.split("+")
    w, h = (int(v) for v in wh.split("x"))
    x, y = int(x), int(y)
    subprocess.run(["convert", out, "-crop", f"{w}x{h}+{x}+{y}", "+repage", out], check=True)
    return x, y, w, h


def part(name, box, pivot=None, transforms=(), ambient_alpha=None, alpha=None):
    x, y, w, h = box
    attrs = f'x="{x}" y="{y}" width="{w}" height="{h}"'
    if alpha is not None:
        attrs += f' alpha="{alpha}"'
    if pivot:
        px, py = pivot[0] * K, pivot[1] * K
        attrs += f' pivotX="{(px - x) / w:.4f}" pivotY="{(py - y) / h:.4f}"'
    body = f'      <Image resource="{name}"/>\n'
    for target, value in transforms:
        body += f'      <Transform target="{target}" value="{value}"/>\n'
    if ambient_alpha is not None:
        body += f'      <Variant mode="AMBIENT" target="alpha" value="{ambient_alpha}"/>\n'
    return f'    <PartImage {attrs}>\n{body}    </PartImage>\n'


def main():
    for d in (DRAW, WORK, DIST):
        shutil.rmtree(d, ignore_errors=True)
        os.makedirs(d)
    os.makedirs(os.path.join(RES, "raw"), exist_ok=True)
    os.makedirs(os.path.join(RES, "xml"), exist_ok=True)
    os.makedirs(os.path.join(RES, "values"), exist_ok=True)

    t = "([MINUTE] * 60 + [SECOND_MILLISECOND])"  # seconds into the hour, smooth
    parts = []

    # 1. dark backplate
    parts.append(part("backplate", render_layer("backplate", '<circle cx="500" cy="500" r="500" fill="url(#bg)"/>', crop=False)))

    # 2. spinning gears (drawn unclipped; the opaque chapter ring above hides the overhang)
    for i, (cx, cy, *_rest, speed) in enumerate(art.GEARS):
        name = f"gear_{i}"
        box = render_layer(name, art.one_gear(i, shadow="spinShadow"))
        parts.append(part(name, box, pivot=(cx, cy), transforms=[("angle", f"{t} * {speed}")]))
    box = render_layer("hairspring", art.spiral())
    parts.append(part("hairspring", box, pivot=art.SPIRAL_CENTER, transforms=[("angle", f"{t} * -30")]))

    # 3. everything static above the gears: scroll, chapter ring, bezel, dragon
    top = art.banner() + art.chapter_ring() + art.bezel() + art.dragon(smoke=False)
    parts.append(part("dial_top", render_layer("dial_top", top, crop=False)))

    # 4. dragon's glowing eye (pulses) and nostril smoke (rises and fades)
    ex, ey = art.EYE["x"], art.EYE["y"]
    glow = (
        f'<ellipse cx="{ex:.1f}" cy="{ey:.1f}" rx="20" ry="13" fill="url(#eye)" opacity="0.9" '
        f'transform="rotate({art.EYE["ang"]:.1f} {ex:.1f} {ey:.1f})" filter="url(#glow)"/>'
    )
    box = render_layer("eye_glow", glow)
    parts.append(part("eye_glow", box, transforms=[("alpha", "150 + 105 * sin([SECOND_MILLISECOND] * 2.4)")]))
    sx, sy, sw, sh = render_layer("smoke", art.EYE["smoke"])
    parts.append(part("smoke", (sx, sy, sw, sh), transforms=[
        ("alpha", "230 - ([SECOND_MILLISECOND] % 3) * 76"),
        ("y", f"{sy} - ([SECOND_MILLISECOND] % 3) * 6"),
    ], ambient_alpha=0))

    # 5. hands
    hands = [
        ("hand_hour", art.hour_hand(), "([HOUR_0_11] + [MINUTE] / 60) * 30", None),
        ("hand_minute", art.minute_hand(), "([MINUTE] + [SECOND] / 60) * 6", None),
        ("hand_second", art.second_hand(), "[SECOND_MILLISECOND] * 6", 0),
    ]
    for name, content, angle, amb in hands:
        content = content.replace('filter="url(#shadow)"', 'filter="url(#spinShadow)"')
        box = render_layer(name, content)
        parts.append(part(name, box, pivot=(500, 500), transforms=[("angle", angle)], ambient_alpha=amb))
    parts.append(part("center_cap", render_layer("center_cap", art.center_cap())))

    # 6. always-on dimmer: invisible normally, darkens everything evenly in ambient mode
    veil = render_layer("ambient_veil", '<circle cx="500" cy="500" r="500" fill="#000"/>', crop=False)
    parts.append(part("ambient_veil", veil, alpha=0, ambient_alpha=AMBIENT_DIM))

    # preview for the watch face picker
    shutil.copy(os.path.join(os.path.dirname(HERE), "png", "preview_450.png"), os.path.join(DRAW, "preview.png"))

    watchface = (
        '<?xml version="1.0" encoding="utf-8"?>\n'
        f'<WatchFace width="{SIZE}" height="{SIZE}">\n'
        '  <Metadata key="CLOCK_TYPE" value="ANALOG"/>\n'
        '  <Metadata key="PREVIEW_TIME" value="10:08:36"/>\n'
        '  <Scene backgroundColor="#ff000000">\n'
        f'   <Group name="face" x="0" y="0" width="{SIZE}" height="{SIZE}">\n'
        + "".join(parts)
        + "   </Group>\n  </Scene>\n</WatchFace>\n"
    )
    with open(os.path.join(RES, "raw", "watchface.xml"), "w") as fh:
        fh.write(watchface)

    # 7. package, align, sign
    android_jar = os.environ.get("ANDROID_JAR")
    if not android_jar:
        sys.exit("Set ANDROID_JAR to an android.jar (API 33 or newer) to package the APK.")
    unsigned = os.path.join(WORK, "unsigned.apk")
    aligned = os.path.join(WORK, "aligned.apk")
    subprocess.run(
        ["aapt", "package", "-f", "--no-crunch", "-0", "arsc", "-M", os.path.join(HERE, "AndroidManifest.xml"),
         "-S", RES, "-I", android_jar, "-F", unsigned],
        check=True,
    )
    subprocess.run(["zipalign", "-f", "-p", "4", unsigned, aligned], check=True)
    keystore = os.path.join(HERE, "debug.keystore")
    if not os.path.exists(keystore):
        subprocess.run(
            ["keytool", "-genkeypair", "-keystore", keystore, "-storepass", "android", "-keypass", "android",
             "-alias", "androiddebugkey", "-keyalg", "RSA", "-keysize", "2048", "-validity", "10000",
             "-dname", "CN=Android Debug,O=Android,C=US"],
            check=True, capture_output=True,
        )
    apk = os.path.join(DIST, APK_NAME)
    subprocess.run(
        ["apksigner", "sign", "--ks", keystore, "--ks-pass", "pass:android", "--key-pass", "pass:android",
         "--out", apk, aligned],
        check=True,
    )
    subprocess.run(["apksigner", "verify", "--print-certs", apk], check=True, capture_output=True)
    print(apk)


if __name__ == "__main__":
    main()
