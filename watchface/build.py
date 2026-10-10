#!/usr/bin/env python3
"""Steampunk dragon watch face generator.

Writes SVG layers (dial, hands, preview) into ./svg and renders PNGs into ./png
using the pre-installed headless Chromium. Canvas is 1000x1000; exports are made
at 450px (Galaxy Watch 8 Classic screen is 438x438) and at 1000px.
"""
import math
import os
import subprocess

HERE = os.path.dirname(os.path.abspath(__file__))
SVG_DIR = os.path.join(HERE, "svg")
PNG_DIR = os.path.join(HERE, "png")
CHROME = "/opt/pw-browsers/chromium_headless_shell-1194/chrome-linux/headless_shell"

C = 500  # center


def pol(clock_deg, r):
    """Clock angle (0 = 12 o'clock, clockwise) and radius -> x, y."""
    a = math.radians(clock_deg)
    return C + r * math.sin(a), C - r * math.cos(a)


def f(v):
    return f"{v:.1f}"


def pts(points):
    return " ".join(f"{f(x)},{f(y)}" for x, y in points)


# --------------------------------------------------------------------------- defs
DEFS = """
<defs>
  <radialGradient id="bg" cx="50%" cy="45%" r="60%">
    <stop offset="0" stop-color="#2b1a0e"/>
    <stop offset="0.7" stop-color="#120a05"/>
    <stop offset="1" stop-color="#050302"/>
  </radialGradient>
  <linearGradient id="brass" x1="0" y1="0" x2="1" y2="1">
    <stop offset="0" stop-color="#fbe3a0"/>
    <stop offset="0.25" stop-color="#d9a945"/>
    <stop offset="0.5" stop-color="#8a5f1c"/>
    <stop offset="0.75" stop-color="#d4a548"/>
    <stop offset="1" stop-color="#6b4512"/>
  </linearGradient>
  <linearGradient id="brassDark" x1="0" y1="0" x2="1" y2="1">
    <stop offset="0" stop-color="#b58a3a"/>
    <stop offset="0.5" stop-color="#5e3f12"/>
    <stop offset="1" stop-color="#a07428"/>
  </linearGradient>
  <linearGradient id="silver" x1="0" y1="0" x2="1" y2="1">
    <stop offset="0" stop-color="#ffffff"/>
    <stop offset="0.3" stop-color="#c9ccd1"/>
    <stop offset="0.55" stop-color="#6d7178"/>
    <stop offset="0.8" stop-color="#dfe2e6"/>
    <stop offset="1" stop-color="#8a8e95"/>
  </linearGradient>
  <linearGradient id="copper" x1="0" y1="0" x2="1" y2="1">
    <stop offset="0" stop-color="#f2b285"/>
    <stop offset="0.5" stop-color="#8e4a22"/>
    <stop offset="1" stop-color="#c97a48"/>
  </linearGradient>
  <radialGradient id="parchment" cx="50%" cy="50%" r="50%">
    <stop offset="0.80" stop-color="#cfb486"/>
    <stop offset="0.88" stop-color="#e3cfa4"/>
    <stop offset="0.96" stop-color="#c4a574"/>
    <stop offset="1" stop-color="#9c7c4c"/>
  </radialGradient>
  <radialGradient id="rivet" cx="35%" cy="35%" r="70%">
    <stop offset="0" stop-color="#ffffff"/>
    <stop offset="0.35" stop-color="#c8cbd0"/>
    <stop offset="1" stop-color="#3d3f44"/>
  </radialGradient>
  <radialGradient id="ruby" cx="38%" cy="35%" r="70%">
    <stop offset="0" stop-color="#ffd0d0"/>
    <stop offset="0.25" stop-color="#e2232f"/>
    <stop offset="0.7" stop-color="#7a0610"/>
    <stop offset="1" stop-color="#2d0004"/>
  </radialGradient>
  <radialGradient id="eye" cx="50%" cy="50%" r="50%">
    <stop offset="0" stop-color="#fff6a8"/>
    <stop offset="0.5" stop-color="#ffb300"/>
    <stop offset="1" stop-color="#d14600"/>
  </radialGradient>
  <linearGradient id="scaleRed" x1="0" y1="0" x2="1" y2="1">
    <stop offset="0" stop-color="#c62a22"/>
    <stop offset="0.5" stop-color="#8c1212"/>
    <stop offset="1" stop-color="#5a0808"/>
  </linearGradient>
  <linearGradient id="wing" x1="0" y1="0" x2="1" y2="1">
    <stop offset="0" stop-color="#a3161a"/>
    <stop offset="1" stop-color="#4a0508"/>
  </linearGradient>
  <filter id="shadow" x="-20%" y="-20%" width="140%" height="140%">
    <feDropShadow dx="4" dy="6" stdDeviation="5" flood-color="#000" flood-opacity="0.65"/>
  </filter>
  <filter id="softShadow" x="-20%" y="-20%" width="140%" height="140%">
    <feDropShadow dx="2" dy="3" stdDeviation="3" flood-color="#000" flood-opacity="0.55"/>
  </filter>
  <filter id="glow" x="-100%" y="-100%" width="300%" height="300%">
    <feGaussianBlur stdDeviation="4" result="b"/>
    <feMerge><feMergeNode in="b"/><feMergeNode in="SourceGraphic"/></feMerge>
  </filter>
  <filter id="emboss" x="-5%" y="-5%" width="110%" height="110%">
    <feDropShadow dx="0" dy="1.5" stdDeviation="0.6" flood-color="#fff3c4" flood-opacity="0.5"/>
  </filter>
  <clipPath id="dialClip"><circle cx="500" cy="500" r="330"/></clipPath>
  <clipPath id="screen"><circle cx="500" cy="500" r="500"/></clipPath>
  <path id="bezelText" d="M {a} A 452 452 0 0 1 {b}"/>
</defs>
"""


def fonts_css():
    # Fonts are embedded because SVGs rendered as images can't load external files.
    import base64

    def face(family, fname):
        with open(os.path.join(HERE, "fonts", fname), "rb") as fh:
            data = base64.b64encode(fh.read()).decode()
        return f"@font-face {{ font-family: '{family}'; src: url(data:font/ttf;base64,{data}); }}"
    return "<style>" + face("Cinzel", "Cinzel.ttf") + face("Fraktur", "UnifrakturMaguntia.ttf") + "</style>"


# --------------------------------------------------------------------------- gears
def gear(cx, cy, r, teeth, fill, spokes=5, depth=None, hub=None, rot=0.0):
    depth = depth or max(8, r * 0.12)
    root = r - depth
    step = 2 * math.pi / teeth
    outline = []
    for i in range(teeth):
        a0 = i * step + math.radians(rot)
        for frac, rad in ((0.0, root), (0.18, r), (0.5, r), (0.68, root)):
            a = a0 + frac * step
            outline.append((cx + rad * math.cos(a), cy + rad * math.sin(a)))
    d = "M " + " L ".join(f"{f(x)},{f(y)}" for x, y in outline) + " Z"
    # spoke windows (sector cut-outs)
    inner = root - max(10, r * 0.14)
    hub = hub or max(14, r * 0.22)
    gap = math.radians(14 if spokes > 4 else 20)
    sec = 2 * math.pi / spokes
    for k in range(spokes):
        a1 = k * sec + gap / 2 + math.radians(rot)
        a2 = (k + 1) * sec - gap / 2 + math.radians(rot)
        h1 = hub + 6
        p = [
            (cx + h1 * math.cos(a1 + 0.15), cy + h1 * math.sin(a1 + 0.15)),
            (cx + inner * math.cos(a1), cy + inner * math.sin(a1)),
        ]
        d += f" M {f(p[0][0])},{f(p[0][1])} L {f(p[1][0])},{f(p[1][1])}"
        d += f" A {f(inner)} {f(inner)} 0 0 1 {f(cx + inner * math.cos(a2))},{f(cy + inner * math.sin(a2))}"
        d += f" L {f(cx + h1 * math.cos(a2 - 0.15))},{f(cy + h1 * math.sin(a2 - 0.15))}"
        d += f" A {f(h1)} {f(h1)} 0 0 0 {f(p[0][0])},{f(p[0][1])} Z"
    hole = hub * 0.35
    d += f" M {f(cx + hole)},{f(cy)} A {f(hole)} {f(hole)} 0 1 0 {f(cx - hole)},{f(cy)} A {f(hole)} {f(hole)} 0 1 0 {f(cx + hole)},{f(cy)} Z"
    return (
        f'<g filter="url(#shadow)">'
        f'<path d="{d}" fill="{fill}" fill-rule="evenodd" stroke="#2a1a08" stroke-width="2"/>'
        f'<circle cx="{f(cx)}" cy="{f(cy)}" r="{f(inner)}" fill="none" stroke="#000" stroke-opacity="0.35" stroke-width="2"/>'
        f'<circle cx="{f(cx)}" cy="{f(cy)}" r="{f(hub)}" fill="none" stroke="#fff6d8" stroke-opacity="0.35" stroke-width="2"/>'
        f"</g>"
    )


def gears():
    g = [
        gear(360, 610, 215, 28, "url(#brass)", spokes=6, rot=4),
        gear(668, 372, 150, 22, "url(#silver)", spokes=5, rot=10),
        gear(660, 668, 96, 16, "url(#copper)", spokes=4, rot=3),
        gear(338, 330, 78, 13, "url(#brass)", spokes=4, rot=8),
        gear(520, 250, 52, 10, "url(#silver)", spokes=3, rot=0),
    ]
    # screws and a pivot jewel or two
    extras = ""
    for x, y in ((360, 610), (668, 372), (660, 668), (338, 330), (520, 250)):
        extras += f'<circle cx="{x}" cy="{y}" r="9" fill="url(#ruby)" stroke="#3a2408" stroke-width="2"/>'
    # a little coiled spring / balance wheel
    spiral = []
    for i in range(260):
        t = i / 259 * 4 * 2 * math.pi
        rr = 6 + t * 3.0
        spiral.append((560 + rr * math.cos(t), 520 + rr * math.sin(t)))
    extras += f'<polyline points="{pts(spiral)}" fill="none" stroke="#e7c873" stroke-width="2.2" opacity="0.9"/>'
    return f'<g clip-path="url(#dialClip)">{"".join(g)}{extras}</g>'


# --------------------------------------------------------------------------- dial
ROMAN = ["XII", "I", "II", "III", "IIII", "V", "VI", "VII", "VIII", "IX", "X", "XI"]


def chapter_ring():
    s = []
    # parchment annulus
    s.append(
        '<path fill="url(#parchment)" fill-rule="evenodd" filter="url(#softShadow)" '
        'd="M 910,500 A 410 410 0 1 0 90,500 A 410 410 0 1 0 910,500 Z '
        'M 830,500 A 330 330 0 1 0 170,500 A 330 330 0 1 0 830,500 Z"/>'
    )
    # minute track
    s.append('<circle cx="500" cy="500" r="402" fill="none" stroke="#2a1608" stroke-width="1.5"/>')
    s.append('<circle cx="500" cy="500" r="388" fill="none" stroke="#2a1608" stroke-width="1"/>')
    for m in range(60):
        a = m * 6
        if m % 5 == 0:
            x1, y1 = pol(a, 386)
            x2, y2 = pol(a, 404)
            s.append(f'<line x1="{f(x1)}" y1="{f(y1)}" x2="{f(x2)}" y2="{f(y2)}" stroke="#1a0c04" stroke-width="4"/>')
        else:
            x1, y1 = pol(a, 390)
            x2, y2 = pol(a, 401)
            s.append(f'<line x1="{f(x1)}" y1="{f(y1)}" x2="{f(x2)}" y2="{f(y2)}" stroke="#2a1608" stroke-width="1.6"/>')
    # numerals, radially oriented
    for h, num in enumerate(ROMAN):
        a = h * 30
        x, y = pol(a, 356)
        rot = a if 90 < a < 270 else a
        flip = 180 if 90 < a < 270 else 0
        size = 44 if len(num) < 4 else 38
        colour = "#7d0b0f" if h in (0, 3, 6, 9) else "#1a0c04"
        s.append(
            f'<text x="{f(x)}" y="{f(y)}" transform="rotate({rot + flip} {f(x)} {f(y)})" '
            f'font-family="Cinzel" font-weight="700" font-size="{size}" fill="{colour}" '
            f'text-anchor="middle" dominant-baseline="central" filter="url(#emboss)">{num}</text>'
        )
    # inner silver ring and outer brass ring
    s.append('<circle cx="500" cy="500" r="331" fill="none" stroke="url(#silver)" stroke-width="9"/>')
    s.append('<circle cx="500" cy="500" r="331" fill="none" stroke="#1a1008" stroke-width="1" opacity="0.6"/>')
    return "".join(s)


def bezel():
    s = []
    s.append(
        '<path fill="url(#brass)" fill-rule="evenodd" '
        'd="M 998,500 A 498 498 0 1 0 2,500 A 498 498 0 1 0 998,500 Z '
        'M 910,500 A 410 410 0 1 0 90,500 A 410 410 0 1 0 910,500 Z"/>'
    )
    # knurled outer edge
    for i in range(180):
        x1, y1 = pol(i * 2, 488)
        x2, y2 = pol(i * 2, 499)
        s.append(f'<line x1="{f(x1)}" y1="{f(y1)}" x2="{f(x2)}" y2="{f(y2)}" stroke="#3b2608" stroke-width="2" opacity="0.7"/>')
    s.append('<circle cx="500" cy="500" r="487" fill="none" stroke="#fff0c0" stroke-width="1.5" opacity="0.6"/>')
    s.append('<circle cx="500" cy="500" r="413" fill="none" stroke="url(#silver)" stroke-width="7"/>')
    s.append('<circle cx="500" cy="500" r="409" fill="none" stroke="#1a1008" stroke-width="1.5"/>')
    # engraved motto on the open (upper-right) side of the bezel
    s.append(
        '<text font-family="Cinzel" font-weight="700" font-size="30" fill="#3d2608" letter-spacing="3" '
        'filter="url(#emboss)"><textPath href="#bezelText" startOffset="50%" text-anchor="middle">'
        "TEMPUS FUGIT • DRACO VIGILAT</textPath></text>"
    )
    # rivets
    for a in (8, 112, 125, 140, 155, 170):
        x, y = pol(a, 452)
        s.append(f'<circle cx="{f(x)}" cy="{f(y)}" r="10" fill="url(#rivet)" stroke="#222" stroke-width="1.2" filter="url(#softShadow)"/>')
    return "".join(s)


def banner():
    """Scroll at the bottom of the open dial: 'Here be Dragons'."""
    return (
        '<g filter="url(#shadow)">'
        '<path d="M 330,640 L 300,628 L 316,660 L 300,692 L 345,680 Z" fill="#7d0b0f" stroke="#2a0505" stroke-width="2"/>'
        '<path d="M 670,640 L 700,628 L 684,660 L 700,692 L 655,680 Z" fill="#7d0b0f" stroke="#2a0505" stroke-width="2"/>'
        '<path d="M 335,630 Q 500,600 665,630 L 665,684 Q 500,654 335,684 Z" fill="#e3cfa4" stroke="#5a3a14" stroke-width="2.5"/>'
        '<path id="bannerLine" d="M 350,668 Q 500,640 650,668" fill="none"/>'
        '<text font-family="Fraktur" font-size="40" fill="#1a0c04"><textPath href="#bannerLine" '
        'startOffset="50%" text-anchor="middle">Here be Dragons</textPath></text>'
        "</g>"
    )


# --------------------------------------------------------------------------- dragon
def lerp(a, b, t):
    return a + (b - a) * t


def smooth(t):
    t = max(0.0, min(1.0, t))
    return t * t * (3 - 2 * t)


A_TAIL, A_HEAD = 112, 322


def body_frame(u):
    a = lerp(A_TAIL, A_HEAD, u)
    r = 452 + 6 * math.sin(u * math.pi * 6)
    x, y = pol(a, r)
    return a, r, x, y


def body_width(u):
    if u < 0.4:
        return lerp(4, 60, smooth(u / 0.4))
    if u < 0.82:
        return 60
    return lerp(60, 42, smooth((u - 0.82) / 0.18))


def dragon():
    N = 220
    cl, nrm, tan, w = [], [], [], []
    for i in range(N + 1):
        u = i / N
        a, r, x, y = body_frame(u)
        _, _, x2, y2 = body_frame(min(1, u + 0.002))
        _, _, x0, y0 = body_frame(max(0, u - 0.002))
        tx, ty = x2 - x0, y2 - y0
        L = math.hypot(tx, ty)
        tx, ty = tx / L, ty / L
        nx, ny = (x - C) / math.hypot(x - C, y - C), (y - C) / math.hypot(x - C, y - C)
        cl.append((x, y)); tan.append((tx, ty)); nrm.append((nx, ny)); w.append(body_width(u))

    outer = [(x + nx * ww / 2, y + ny * ww / 2) for (x, y), (nx, ny), ww in zip(cl, nrm, w)]
    inner = [(x - nx * ww / 2, y - ny * ww / 2) for (x, y), (nx, ny), ww in zip(cl, nrm, w)]
    s = []

    # --- wing (behind body): bat-style wing on brass ribs, fanned inward over the dial
    iw = int(0.62 * N)
    S = (cl[iw][0] - nrm[iw][0] * 12, cl[iw][1] - nrm[iw][1] * 12)
    n_in = (-nrm[iw][0], -nrm[iw][1])
    fw = tan[iw]

    def ray(phi, length):
        c, sn = math.cos(math.radians(phi)), math.sin(math.radians(phi))
        return (S[0] + (c * n_in[0] + sn * fw[0]) * length, S[1] + (c * n_in[1] + sn * fw[1]) * length)

    ribs = [(64, 280), (38, 262), (12, 228), (-14, 186), (-40, 140)]
    tips = [ray(phi, L) for phi, L in ribs]
    tail_pt = ray(-78, 70)

    def scallop(p, q, k=0.42):
        mx, my = (p[0] + q[0]) / 2, (p[1] + q[1]) / 2
        cx, cy = mx + (S[0] - mx) * k, my + (S[1] - my) * k
        return f"Q {f(cx)},{f(cy)} {f(q[0])},{f(q[1])}"

    knee = ray(70, 160)
    wing_d = f"M {f(S[0])},{f(S[1])} Q {f(knee[0])},{f(knee[1])} {f(tips[0][0])},{f(tips[0][1])} "
    for a_, b_ in zip(tips, tips[1:]):
        wing_d += scallop(a_, b_) + " "
    wing_d += scallop(tips[-1], tail_pt, 0.25) + f" L {f(S[0])},{f(S[1])} Z"
    s.append(f'<path d="{wing_d}" fill="url(#wing)" fill-opacity="0.8" stroke="#2a0303" stroke-width="2.5" filter="url(#shadow)"/>')
    for k, (tip, (phi, L)) in enumerate(zip(tips, ribs)):
        mid = ray(phi + 6, L * 0.55)
        if k == 0:
            rib = f"M {f(S[0])},{f(S[1])} Q {f(knee[0])},{f(knee[1])} {f(tip[0])},{f(tip[1])}"
        else:
            rib = f"M {f(S[0])},{f(S[1])} Q {f(mid[0])},{f(mid[1])} {f(tip[0])},{f(tip[1])}"
        width = 9 if k == 0 else 5
        s.append(f'<path d="{rib}" fill="none" stroke="#e7c873" stroke-width="{width}" stroke-linecap="round"/>')
        s.append(f'<path d="{rib}" fill="none" stroke="#6b4512" stroke-width="1.6" stroke-linecap="round"/>')
        s.append(f'<circle cx="{f(tip[0])}" cy="{f(tip[1])}" r="{5 if k else 7}" fill="url(#rivet)" stroke="#222" stroke-width="1"/>')
    # thumb claw at the leading tip
    t0 = tips[0]
    claw_end = ray(73, 306)
    s.append(
        f'<path d="M {f(t0[0])},{f(t0[1])} Q {f(claw_end[0] + 10)},{f(claw_end[1])} {f(claw_end[0])},{f(claw_end[1])} '
        f'Q {f(t0[0] + 4)},{f(t0[1] - 4)} {f(t0[0] - 6)},{f(t0[1] + 4)} Z" fill="#efe3c6" stroke="#2a1a08" stroke-width="1.5"/>'
    )
    s.append(f'<circle cx="{f(S[0])}" cy="{f(S[1])}" r="12" fill="url(#brass)" stroke="#3b2408" stroke-width="2"/>')
    s.append(f'<circle cx="{f(S[0])}" cy="{f(S[1])}" r="5" fill="url(#ruby)"/>')

    # --- spines on the outer edge
    spines = []
    for i in range(14, N - 10, 7):
        u = i / N
        (ox, oy), (nx, ny), (tx, ty) = outer[i], nrm[i], tan[i]
        h = 10 + 12 * min(1, w[i] / 60)
        b0 = outer[i - 3]
        b1 = outer[min(N, i + 3)]
        apex = (ox + nx * h - tx * h * 0.6, oy + ny * h - ty * h * 0.6)
        spines.append(f'<polygon points="{pts([b0, apex, b1])}" fill="#2b1608" stroke="#c99a3c" stroke-width="1.4"/>')
    s.append(f'<g filter="url(#softShadow)">{"".join(spines)}</g>')

    # --- tail spade
    (tx, ty), (x0, y0) = tan[0], cl[0]
    nx, ny = nrm[0]
    tip = (x0 - tx * 46, y0 - ty * 46)
    l1 = (x0 - tx * 14 + nx * 20, y0 - ty * 14 + ny * 20)
    l2 = (x0 - tx * 14 - nx * 20, y0 - ty * 14 - ny * 20)
    s.append(
        f'<path d="M {f(x0)},{f(y0)} Q {f(l1[0])},{f(l1[1])} {f(tip[0])},{f(tip[1])} '
        f'Q {f(l2[0])},{f(l2[1])} {f(x0)},{f(y0)} Z" fill="#8c1212" stroke="#2a0505" stroke-width="2.5" filter="url(#softShadow)"/>'
    )

    # --- legs gripping the chapter ring
    for u in (0.3, 0.66):
        i = int(u * N)
        (ix, iy), (nx, ny), (tx, ty) = inner[i], nrm[i], tan[i]
        hip = (ix + nx * 8, iy + ny * 8)
        foot = (ix - nx * 18 - tx * 10, iy - ny * 18 - ty * 10)
        s.append(f'<path d="M {f(hip[0])},{f(hip[1])} L {f(foot[0])},{f(foot[1])}" stroke="#5a0808" stroke-width="22" stroke-linecap="round"/>')
        s.append(f'<path d="M {f(hip[0])},{f(hip[1])} L {f(foot[0])},{f(foot[1])}" stroke="#a51c18" stroke-width="15" stroke-linecap="round"/>')
        for k in (-1, 0, 1):
            dx, dy = tx * k * 10, ty * k * 10
            base = (foot[0] + dx, foot[1] + dy)
            tipc = (base[0] - nx * 22 + tx * k * 4, base[1] - ny * 22 + ty * k * 4)
            ctrl = (base[0] - nx * 6 + tx * 10, base[1] - ny * 6 + ty * 10)
            s.append(
                f'<path d="M {f(base[0] - tx * 4)},{f(base[1] - ty * 4)} Q {f(ctrl[0])},{f(ctrl[1])} {f(tipc[0])},{f(tipc[1])} '
                f'L {f(base[0] + tx * 4)},{f(base[1] + ty * 4)} Z" fill="#f1e6c8" stroke="#2a1a08" stroke-width="1.5"/>'
            )

    # --- body
    body = pts(outer + inner[::-1])
    s.append(f'<polygon points="{body}" fill="url(#scaleRed)" stroke="#250303" stroke-width="3.5" filter="url(#shadow)"/>')
    # belly plates along inner edge
    belly_in = inner
    belly_out = [(x - nx * ww * 0.18, y - ny * ww * 0.18) for (x, y), (nx, ny), ww in zip(cl, nrm, w)]
    s.append(f'<polygon points="{pts(belly_out + belly_in[::-1])}" fill="#d8b56a" stroke="#4a2a08" stroke-width="1.5"/>')
    for i in range(6, N, 4):
        s.append(
            f'<line x1="{f(belly_in[i][0])}" y1="{f(belly_in[i][1])}" x2="{f(belly_out[i][0])}" '
            f'y2="{f(belly_out[i][1])}" stroke="#6b4512" stroke-width="1.6"/>'
        )
    # dorsal highlight
    hl_a = [(x + nx * ww * 0.08, y + ny * ww * 0.08) for (x, y), (nx, ny), ww in zip(cl, nrm, w)]
    hl_b = [(x + nx * ww * 0.30, y + ny * ww * 0.30) for (x, y), (nx, ny), ww in zip(cl, nrm, w)]
    s.append(f'<polygon points="{pts(hl_a + hl_b[::-1])}" fill="#e2483a" opacity="0.35"/>')
    # scales
    sc = []
    for i in range(8, N - 4, 3):
        ww = w[i]
        if ww < 14:
            continue
        (x, y), (nx, ny), (tx, ty) = cl[i], nrm[i], tan[i]
        size = ww * 0.11
        row = (i // 3) % 2
        for off in ([-0.05, 0.17, 0.39] if row == 0 else [0.06, 0.28]):
            qx, qy = x + nx * ww * off, y + ny * ww * off
            p1 = (qx - nx * size, qy - ny * size)
            p2 = (qx + nx * size, qy + ny * size)
            ctl = (qx + tx * size * 1.9, qy + ty * size * 1.9)
            sc.append(f"M {f(p1[0])},{f(p1[1])} Q {f(ctl[0])},{f(ctl[1])} {f(p2[0])},{f(p2[1])}")
    s.append(f'<path d="{" ".join(sc)}" fill="none" stroke="#3d0505" stroke-width="1.6" opacity="0.85"/>')
    # brass harness band across the body (steampunk touch)
    for u in (0.48, 0.86):
        i = int(u * N)
        s.append(
            f'<line x1="{f(outer[i][0])}" y1="{f(outer[i][1])}" x2="{f(inner[i][0])}" y2="{f(inner[i][1])}" '
            'stroke="url(#brass)" stroke-width="11"/>'
        )
        for t in (0.3, 0.7):
            px = lerp(outer[i][0], inner[i][0], t)
            py = lerp(outer[i][1], inner[i][1], t)
            s.append(f'<circle cx="{f(px)}" cy="{f(py)}" r="3" fill="url(#rivet)"/>')

    # --- head
    (hx, hy), (tx, ty) = cl[-1], tan[-1]
    ang = math.degrees(math.atan2(ty, tx)) + 22
    head = f"""
    <g transform="translate({f(hx)},{f(hy)}) rotate({f(ang)}) scale(1.6)" filter="url(#shadow)">
      <path d="M 26,-24 C 12,-44 -12,-56 -40,-54 C -16,-46 2,-36 14,-20 Z" fill="#efe3c6" stroke="#2a1a08" stroke-width="1.6"/>
      <path d="M 40,-23 C 33,-42 18,-58 -2,-66 C 16,-52 26,-38 30,-21 Z" fill="#e2d2ac" stroke="#2a1a08" stroke-width="1.6"/>
      <path d="M 2,20 L -24,32 L 8,10 Z M -2,6 L -28,10 L 2,-4 Z" fill="#2b1608" stroke="#c99a3c" stroke-width="1.2"/>
      <path d="M -6,-20 C 10,-27 30,-30 45,-24 L 62,-16 C 72,-14 84,-12 92,-8 C 98,-6 99,0 94,2 L 70,4 L 66,7 L 88,12
               C 90,15 86,18 80,18 C 60,20 40,24 25,26 C 12,28 0,26 -6,22 Z"
            fill="url(#scaleRed)" stroke="#250303" stroke-width="2.4"/>
      <path d="M 94,2 L 70,4 L 66,7 L 88,12 Z" fill="#2a0303"/>
      <path d="M 74,3.5 L 76.5,9 L 79,3.2 Z M 83,2.8 L 85,7.5 L 87,2.6 Z M 78,10 L 80,6.5 L 82,10.8 Z" fill="#fff8e6"/>
      <path d="M 25,26 C 40,22 60,19 80,18" fill="none" stroke="#d8b56a" stroke-width="3"/>
      <path d="M 44,-23 C 52,-21 58,-18 64,-15" fill="none" stroke="#2b1608" stroke-width="3"/>
      <ellipse cx="56" cy="-11" rx="7.5" ry="4.2" fill="url(#eye)" filter="url(#glow)"/>
      <ellipse cx="56" cy="-11" rx="1.3" ry="3.6" fill="#120200"/>
      <ellipse cx="90" cy="-4" rx="2" ry="1.4" fill="#250303"/>
      <path d="M 10,-6 Q 20,-2 30,-8 M 14,6 Q 24,10 34,4" fill="none" stroke="#3d0505" stroke-width="1.4"/>
      <circle cx="36" cy="-26" r="3" fill="url(#rivet)"/>
    </g>
    """
    # wisps of smoke from the nostrils
    smoke = f"""
    <g transform="translate({f(hx)},{f(hy)}) rotate({f(ang)}) scale(1.6)" opacity="0.55">
      <path d="M 96,-6 C 108,-14 104,-24 116,-30 C 128,-36 124,-46 136,-50" fill="none" stroke="#e9e2d6" stroke-width="3" stroke-linecap="round"/>
      <path d="M 98,-2 C 114,-4 116,-14 128,-16" fill="none" stroke="#e9e2d6" stroke-width="2" stroke-linecap="round"/>
    </g>
    """
    s.append(head)
    s.append(smoke)
    return "".join(s)


# --------------------------------------------------------------------------- hands
def mirror(half):
    """half: list of (x_offset, radius) from tip side; returns closed polygon in canvas coords (pointing up)."""
    right = [(C + dx, C - r) for dx, r in half]
    left = [(C - dx, C - r) for dx, r in reversed(half)]
    return right + left


def hour_hand():
    half = [(0, 238), (14, 206), (6, 196), (10, 186), (19, 168), (10, 150), (8, 60), (12, 0), (10, -40), (4, -52)]
    poly = mirror(half)
    return (
        f'<g filter="url(#shadow)">'
        f'<polygon points="{pts(poly)}" fill="url(#brass)" stroke="#3b2408" stroke-width="2.5"/>'
        f'<circle cx="500" cy="{C - 168}" r="11" fill="#1a0c04" stroke="#3b2408" stroke-width="1"/>'
        f'<circle cx="500" cy="{C - 168}" r="6" fill="url(#ruby)"/>'
        f'<line x1="500" y1="{C - 140}" x2="500" y2="{C - 30}" stroke="#6b4512" stroke-width="2"/>'
        f"</g>"
    )


def minute_hand():
    half = [(0, 335), (8, 312), (4, 300), (12, 282), (4, 262), (5, 80), (9, 0), (8, -55), (3, -66)]
    poly = mirror(half)
    return (
        f'<g filter="url(#shadow)">'
        f'<polygon points="{pts(poly)}" fill="url(#silver)" stroke="#2a2c30" stroke-width="2.2"/>'
        f'<line x1="500" y1="{C - 255}" x2="500" y2="{C - 30}" stroke="#4a4d52" stroke-width="1.6"/>'
        f'<polygon points="500,{C - 300} 506,{C - 283} 500,{C - 266} 494,{C - 283}" fill="#7d0b0f"/>'
        f"</g>"
    )


def second_hand():
    tail = gear(500, C + 72, 18, 9, "url(#brass)", spokes=3, depth=4, hub=6)
    return (
        f'<g filter="url(#softShadow)">'
        f'<polygon points="{pts(mirror([(0, 345), (2.2, 330), (1.6, 0), (3, -60)]))}" fill="#b3121a"/>'
        f'<polygon points="{pts([(500, C - 345), (507, C - 318), (500, C - 324), (493, C - 318)])}" fill="#b3121a"/>'
        f"{tail}"
        f"</g>"
    )


def center_cap():
    return (
        '<g filter="url(#softShadow)">'
        '<circle cx="500" cy="500" r="24" fill="url(#brass)" stroke="#3b2408" stroke-width="2"/>'
        '<circle cx="500" cy="500" r="13" fill="url(#ruby)" stroke="#2a0505" stroke-width="1.5"/>'
        '<circle cx="495" cy="495" r="3.5" fill="#fff" opacity="0.8"/>'
        "</g>"
    )


def rotated(content, deg):
    return f'<g transform="rotate({deg} 500 500)">{content}</g>'


# --------------------------------------------------------------------------- assembly
def svg(content):
    a = "{:.1f},{:.1f}".format(*pol(12, 452))
    b = "{:.1f},{:.1f}".format(*pol(108, 452))
    return (
        '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1000 1000" width="1000" height="1000">'
        + fonts_css()
        + DEFS.replace("{a}", a).replace("{b}", b)
        + f'<g clip-path="url(#screen)">{content}</g></svg>'
    )


def dial():
    return (
        '<circle cx="500" cy="500" r="500" fill="url(#bg)"/>'
        + gears()
        + banner()
        + chapter_ring()
        + bezel()
        + dragon()
    )


def layers():
    hh, mm, ss = 10, 9, 36
    h_deg = (hh % 12) * 30 + mm * 0.5
    m_deg = mm * 6 + ss * 0.1
    s_deg = ss * 6
    return {
        "dial": svg(dial()),
        "hand_hour": svg(hour_hand()),
        "hand_minute": svg(minute_hand()),
        "hand_second": svg(second_hand()),
        "center_cap": svg(center_cap()),
        "preview": svg(
            dial()
            + rotated(hour_hand(), h_deg)
            + rotated(minute_hand(), m_deg)
            + rotated(second_hand(), s_deg)
            + center_cap()
        ),
    }


def render(name, size):
    src = os.path.join(SVG_DIR, f"{name}.svg")
    html = os.path.join(SVG_DIR, f"_{name}.html")
    with open(html, "w") as fh:
        fh.write(
            "<!doctype html><html><head><style>html,body{margin:0;background:transparent}"
            f"img{{width:{size}px;height:{size}px;display:block}}</style></head>"
            f'<body><img src="{name}.svg"></body></html>'
        )
    out = os.path.join(PNG_DIR, f"{name}_{size}.png")
    subprocess.run(
        [CHROME, "--no-sandbox", "--disable-gpu", "--hide-scrollbars",
         "--allow-file-access-from-files", "--default-background-color=00000000",
         f"--window-size={size},{size}", "--virtual-time-budget=3000", f"--screenshot={out}",
         f"file://{html}"],
        check=True, capture_output=True,
    )
    os.remove(html)
    return out


def main():
    os.makedirs(SVG_DIR, exist_ok=True)
    os.makedirs(PNG_DIR, exist_ok=True)
    for name, content in layers().items():
        with open(os.path.join(SVG_DIR, f"{name}.svg"), "w") as fh:
            fh.write(content)
    for name in ("dial", "hand_hour", "hand_minute", "hand_second", "center_cap", "preview"):
        for size in (450, 1000):
            print(render(name, size))


if __name__ == "__main__":
    main()
