"""Generates the mod's textures procedurally.

Run from the repo root:  python3 tools/generate_textures.py [preview_dir]

The Spider-Man skin is a standard 64x64 player-skin layout rendered at 4x (256x256) so the web
pattern, eyes and emblem stay crisp in game. Writes straight into src/main/resources/assets.
"""
import math
import os
import random
import sys

from PIL import Image, ImageDraw, ImageFilter

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "spiderman")
S = 4  # skin upscale factor

RED = (190, 22, 34)
RED_DARK = (118, 8, 20)
BLUE = (30, 58, 150)
BLUE_DARK = (14, 28, 88)
LINE = (16, 6, 10)
EYE = (236, 242, 248)
EYE_SHADE = (180, 196, 214)
EYE_RIM = (10, 10, 14)

rng = random.Random(1962)


# --------------------------------------------------------------------------- helpers

def shade(color, factor):
    return tuple(max(0, min(255, int(c * factor))) for c in color)


def fabric(w, h, base, dark, light_dir=(0.0, -1.0)):
    """Base colour with soft vertical lighting, rim darkening and a fine knit texture."""
    img = Image.new("RGBA", (w, h))
    px = img.load()
    for y in range(h):
        for x in range(w):
            ny = y / max(1, h - 1)
            nx = x / max(1, w - 1)
            light = 1.06 - 0.16 * ny
            rim = 1.0 - 0.18 * (abs(nx - 0.5) * 2) ** 3
            knit = 1.0 + ((x + y) % 2) * 0.025 + rng.uniform(-0.025, 0.025)
            f = light * rim * knit
            c = tuple(int(dark[i] + (base[i] - dark[i]) * min(1.25, f)) for i in range(3))
            px[x, y] = (*[max(0, min(255, v)) for v in c], 255)
    return img


def radial_web(w, h, cx, cy, spokes=12, ring_step=None, rotation=0.0, sag=0.28):
    """Spider-web line art: radial spokes with sagging concentric rings, as an L mask."""
    mask = Image.new("L", (w, h), 0)
    d = ImageDraw.Draw(mask)
    cx, cy = cx * w, cy * h
    reach = math.hypot(max(cx, w - cx), max(cy, h - cy)) + 2
    ring_step = ring_step or max(w, h) / 5.2
    angles = [rotation + i * 2 * math.pi / spokes for i in range(spokes)]
    for a in angles:
        d.line([(cx, cy), (cx + math.cos(a) * reach, cy + math.sin(a) * reach)], fill=255, width=1)
    r = ring_step * 0.75
    while r < reach:
        for i in range(spokes):
            a0, a1 = angles[i], angles[(i + 1) % spokes] + (2 * math.pi if i == spokes - 1 else 0)
            pts = []
            for k in range(9):
                t = k / 8
                a = a0 + (a1 - a0) * t
                rr = r * (1 - sag * math.sin(t * math.pi) * 0.45)
                pts.append((cx + math.cos(a) * rr, cy + math.sin(a) * rr))
            d.line(pts, fill=255, width=1)
        r += ring_step
    return mask


def tube_web(w, h, columns=2, row_step=None, sag=3.0, offset=0.0):
    """Web pattern for limbs: vertical seams with hanging arcs between them."""
    mask = Image.new("L", (w, h), 0)
    d = ImageDraw.Draw(mask)
    xs = [w * (i + 0.5) / columns for i in range(columns)]
    edges = [-w * 0.5 / columns] + xs + [w + w * 0.5 / columns]
    for x in xs:
        d.line([(x, 0), (x, h)], fill=255, width=1)
    row_step = row_step or w / 1.6
    y = row_step * (0.6 + offset)
    while y < h + row_step:
        for i in range(len(edges) - 1):
            x0, x1 = edges[i], edges[i + 1]
            pts = []
            for k in range(9):
                t = k / 8
                pts.append((x0 + (x1 - x0) * t, y + math.sin(t * math.pi) * sag))
            d.line(pts, fill=255, width=1)
        y += row_step
    return mask


def apply_lines(img, line_mask, region_mask, color=LINE):
    lines = Image.new("RGBA", img.size, (*color, 255))
    m = Image.new("L", img.size, 0)
    m.paste(line_mask, (0, 0), region_mask)
    img.paste(lines, (0, 0), m)


def outline_regions(img, red_mask, color=LINE):
    """Thin dark seam where red meets blue."""
    edge = red_mask.filter(ImageFilter.FIND_EDGES).point(lambda v: 255 if v > 40 else 0)
    img.paste(Image.new("RGBA", img.size, (*color, 255)), (0, 0), edge)


def mask_from(w, h, fn):
    m = Image.new("L", (w, h), 0)
    px = m.load()
    for y in range(h):
        for x in range(w):
            if fn((x + 0.5) / w, (y + 0.5) / h):
                px[x, y] = 255
    return m


def two_tone(w, h, red_fn, web_mask):
    red = mask_from(w, h, red_fn)
    img = fabric(w, h, BLUE, BLUE_DARK)
    img.paste(fabric(w, h, RED, RED_DARK), (0, 0), red)
    apply_lines(img, web_mask, red)
    outline_regions(img, red)
    return img


# --------------------------------------------------------------------------- emblem & eyes

def draw_spider(img, cx, cy, size, color=LINE, width=1.6):
    """Stylised spider emblem, drawn supersampled so the thin legs stay smooth."""
    k = 6
    layer = Image.new("L", (img.width * k, img.height * k), 0)
    d = ImageDraw.Draw(layer)
    X = lambda v: (cx + v * size) * k
    Y = lambda v: (cy + v * size) * k
    d.ellipse([X(-0.065), Y(-0.21), X(0.065), Y(-0.06)], fill=255)
    d.ellipse([X(-0.09), Y(-0.07), X(0.09), Y(0.30)], fill=255)
    legs = [
        [(0.04, -0.15), (0.20, -0.30), (0.17, -0.50)],
        [(0.06, -0.09), (0.30, -0.16), (0.36, -0.38)],
        [(0.06, 0.00), (0.30, 0.10), (0.36, 0.33)],
        [(0.04, 0.06), (0.20, 0.25), (0.17, 0.47)],
    ]
    for leg in legs:
        for sign in (-1, 1):
            d.line([(X(sign * lx), Y(ly)) for lx, ly in leg], fill=255, width=int(width * k), joint="curve")
    mask = layer.resize(img.size, Image.LANCZOS)
    img.paste(Image.new("RGBA", img.size, (*color, 255)), (0, 0), mask)


def eye_polygon(left=True):
    pts = [(0.46, 0.66), (0.44, 0.48), (0.36, 0.33), (0.22, 0.25), (0.08, 0.26),
           (0.03, 0.36), (0.07, 0.50), (0.22, 0.60), (0.37, 0.67)]
    return pts if left else [(1 - x, y) for x, y in pts]


def draw_eyes(img):
    """Big classic eyes: thick black rims, bright lenses with a soft lower shade."""
    k = 6
    w, h = img.size
    rim = Image.new("L", (w * k, h * k), 0)
    lens = Image.new("L", (w * k, h * k), 0)
    lower = Image.new("L", (w * k, h * k), 0)
    for left in (True, False):
        poly = [(x * w * k, y * h * k) for x, y in eye_polygon(left)]
        cx = sum(p[0] for p in poly) / len(poly)
        cy = sum(p[1] for p in poly) / len(poly)
        ImageDraw.Draw(rim).polygon(poly, fill=255)
        inner = [(cx + (x - cx) * 0.74, cy + (y - cy) * 0.70) for x, y in poly]
        ImageDraw.Draw(lens).polygon(inner, fill=255)
        ImageDraw.Draw(lower).rectangle([0, cy + h * k * 0.03, w * k, h * k], fill=255)
    rim = rim.resize((w, h), Image.LANCZOS)
    lens = lens.resize((w, h), Image.LANCZOS)
    lower = Image.composite(lens, Image.new("L", (w, h), 0), lower.resize((w, h), Image.LANCZOS))
    out = img.copy()
    out.paste(Image.new("RGBA", (w, h), (*EYE_RIM, 255)), (0, 0), rim)
    out.paste(Image.new("RGBA", (w, h), (*EYE, 255)), (0, 0), lens)
    out.paste(Image.new("RGBA", (w, h), (*EYE_SHADE, 255)), (0, 0), lower.point(lambda v: int(v * 0.55)))
    return out


# --------------------------------------------------------------------------- skin parts

def head_face(kind):
    w = h = 8 * S
    if kind == "front":
        img = fabric(w, h, RED, RED_DARK)
        apply_lines(img, radial_web(w, h, 0.5, 0.62, spokes=12, ring_step=w / 4.6), Image.new("L", (w, h), 255))
        return draw_eyes(img)
    if kind == "top":
        img = fabric(w, h, RED, RED_DARK)
        apply_lines(img, radial_web(w, h, 0.5, 0.5, spokes=12, ring_step=w / 5.0), Image.new("L", (w, h), 255))
        return img
    if kind == "bottom":
        img = fabric(w, h, RED_DARK, shade(RED_DARK, 0.6))
        return img
    img = fabric(w, h, RED, RED_DARK)
    cy = 0.62 if kind == "back" else 0.55
    apply_lines(img, radial_web(w, h, 0.5, cy, spokes=10, ring_step=w / 5.0, rotation=0.3), Image.new("L", (w, h), 255))
    return img


def torso_red(x, y):
    if y < 0.40 or y > 0.88:
        return True
    t = (y - 0.40) / 0.48
    half = 0.5 - 0.30 * t
    return abs(x - 0.5) < half


def body_face(kind):
    if kind in ("front", "back"):
        w, h = 8 * S, 12 * S
        web = radial_web(w, h, 0.5, 0.27, spokes=14, ring_step=w / 5.0)
        belt = tube_web(w, h, columns=4, row_step=h / 9.0, sag=2)
        combined = Image.new("L", (w, h), 0)
        combined.paste(web, (0, 0), mask_from(w, h, lambda x, y: y < 0.86))
        combined.paste(belt, (0, 0), mask_from(w, h, lambda x, y: y >= 0.86))
        ey, ery = (0.25, 0.17) if kind == "front" else (0.34, 0.25)
        erx = 0.30 if kind == "front" else 0.42
        ImageDraw.Draw(combined).ellipse([w * (0.5 - erx), h * (ey - ery), w * (0.5 + erx), h * (ey + ery)], fill=0)
        img = two_tone(w, h, torso_red, combined)
        if kind == "front":
            draw_spider(img, w * 0.5, h * 0.25, w * 0.62, width=1.5)
        else:
            draw_spider(img, w * 0.5, h * 0.33, w * 0.9, width=1.8)
        return img
    if kind in ("right", "left"):
        w, h = 4 * S, 12 * S
        return two_tone(w, h, lambda x, y: y < 0.36 or y > 0.88, tube_web(w, h, columns=2))
    w, h = 8 * S, 4 * S
    if kind == "top":
        img = fabric(w, h, RED, RED_DARK)
        apply_lines(img, tube_web(w, h, columns=4, row_step=h / 1.6), Image.new("L", (w, h), 255))
        return img
    return fabric(w, h, BLUE_DARK, shade(BLUE_DARK, 0.7))


def arm_face(kind, side):
    """side: 'right' or 'left' character arm. Outer faces and gloves are red, the inner arm is blue."""
    w, h = 4 * S, 12 * S
    if kind in ("top", "bottom"):
        img = fabric(w, w, RED if kind == "top" else RED_DARK, RED_DARK)
        if kind == "top":
            apply_lines(img, radial_web(w, w, 0.5, 0.5, spokes=8, ring_step=w / 3), Image.new("L", (w, w), 255))
        return img
    outer = {"right": "right", "left": "left"}[side]
    inner = "left" if side == "right" else "right"
    web = tube_web(w, h, columns=2, row_step=w / 1.5)
    if kind == outer:
        return two_tone(w, h, lambda x, y: True, web)
    if kind == inner:
        return two_tone(w, h, lambda x, y: y < 0.16 or y > 0.76, web)
    # front/back: outer half red. Front faces have the outer edge on the viewer-left for the
    # right arm; back faces are mirrored.
    outer_is_left = (kind == "front") == (side == "right")
    return two_tone(w, h, lambda x, y: y < 0.16 or y > 0.76 or ((x < 0.55) if outer_is_left else (x > 0.45)), web)


def leg_face(kind):
    w, h = 4 * S, 12 * S
    if kind == "top":
        return fabric(w, w, BLUE, BLUE_DARK)
    if kind == "bottom":
        return fabric(w, w, shade(RED_DARK, 0.7), shade(RED_DARK, 0.5))
    web = tube_web(w, h, columns=2, row_step=w / 1.5, offset=0.3)
    if kind == "front":
        boot = lambda x, y: y > 0.56 + 0.10 * (1 - abs(x - 0.5) * 2)
    elif kind == "back":
        boot = lambda x, y: y > 0.60
    else:
        boot = lambda x, y: y > 0.58
    return two_tone(w, h, boot, web)


def build_skin():
    skin = Image.new("RGBA", (64 * S, 64 * S), (0, 0, 0, 0))

    def put(img, x, y):
        skin.paste(img, (x * S, y * S))

    # head
    put(head_face("top"), 8, 0)
    put(head_face("bottom"), 16, 0)
    put(head_face("right"), 0, 8)
    put(head_face("front"), 8, 8)
    put(head_face("left"), 16, 8)
    put(head_face("back"), 24, 8)
    # body
    put(body_face("top"), 20, 16)
    put(body_face("bottom"), 28, 16)
    put(body_face("right"), 16, 20)
    put(body_face("front"), 20, 20)
    put(body_face("left"), 28, 20)
    put(body_face("back"), 32, 20)
    # right arm
    put(arm_face("top", "right"), 44, 16)
    put(arm_face("bottom", "right"), 48, 16)
    put(arm_face("right", "right"), 40, 20)
    put(arm_face("front", "right"), 44, 20)
    put(arm_face("left", "right"), 48, 20)
    put(arm_face("back", "right"), 52, 20)
    # left arm
    put(arm_face("top", "left"), 36, 48)
    put(arm_face("bottom", "left"), 40, 48)
    put(arm_face("right", "left"), 32, 52)
    put(arm_face("front", "left"), 36, 52)
    put(arm_face("left", "left"), 40, 52)
    put(arm_face("back", "left"), 44, 52)
    # right leg
    put(leg_face("top"), 4, 16)
    put(leg_face("bottom"), 8, 16)
    put(leg_face("right"), 0, 20)
    put(leg_face("front"), 4, 20)
    put(leg_face("left"), 8, 20)
    put(leg_face("back"), 12, 20)
    # left leg
    put(leg_face("top"), 20, 48)
    put(leg_face("bottom"), 24, 48)
    put(leg_face("right"), 16, 52)
    put(leg_face("front"), 20, 52)
    put(leg_face("left"), 24, 52)
    put(leg_face("back"), 28, 52)
    return skin


def preview(skin, path):
    """Flat front/back 'paper doll' of the skin for eyeballing the design."""
    def crop(x, y, w, h):
        return skin.crop((x * S, y * S, (x + w) * S, (y + h) * S))

    doll = Image.new("RGBA", (40 * S, 34 * S), (235, 235, 235, 255))
    # front: viewer-left is the character's right
    doll.paste(crop(8, 8, 8, 8), (4 * S, 1 * S))
    doll.paste(crop(20, 20, 8, 12), (4 * S, 9 * S))
    doll.paste(crop(44, 20, 4, 12), (0 * S, 9 * S))
    doll.paste(crop(36, 52, 4, 12), (12 * S, 9 * S))
    doll.paste(crop(4, 20, 4, 12), (4 * S, 21 * S))
    doll.paste(crop(20, 52, 4, 12), (8 * S, 21 * S))
    # back
    ox = 22
    doll.paste(crop(24, 8, 8, 8), ((ox + 4) * S, 1 * S))
    doll.paste(crop(32, 20, 8, 12), ((ox + 4) * S, 9 * S))
    doll.paste(crop(44, 52, 4, 12), ((ox + 0) * S, 9 * S))
    doll.paste(crop(52, 20, 4, 12), ((ox + 12) * S, 9 * S))
    doll.paste(crop(28, 52, 4, 12), ((ox + 4) * S, 21 * S))
    doll.paste(crop(12, 20, 4, 12), ((ox + 8) * S, 21 * S))
    doll = doll.resize((doll.width * 3, doll.height * 3), Image.NEAREST)
    doll.save(path)


# --------------------------------------------------------------------------- other textures

def build_web_shooter():
    """16x16 icon: a steel wrist cuff seen at 3/4, red web-fluid band, nozzle, and a fresh strand of web."""
    W, H = 16, 16
    ax, ay = 5.0, 10.8   # near end (the wrist opening)
    bx, by = 9.6, 6.2    # far end (nozzle side)
    r = 4.1
    dx, dy = bx - ax, by - ay
    length = math.hypot(dx, dy)
    ux, uy = dx / length, dy / length
    px_, py_ = -uy, ux                 # perpendicular (points down-right of the axis)
    cap = 0.62                          # how squashed the end ellipses look

    def frame(x, y):
        rx, ry = x + 0.5 - ax, y + 0.5 - ay
        return (rx * ux + ry * uy) / length, rx * px_ + ry * py_

    def inside(x, y):
        u, v = frame(x, y)
        if abs(v) > r:
            return False
        along = u * length
        if 0 <= along <= length:
            return True
        k = math.sqrt(max(0.0, 1 - (v / r) ** 2)) * r * cap
        return -k <= along <= length + k

    def opening(x, y, scale):
        u, v = frame(x, y)
        along = u * length
        return (along / (r * cap * scale)) ** 2 + (v / (r * scale)) ** 2 <= 1

    steel_hi, steel, steel_dk = (230, 236, 242), (156, 166, 180), (84, 92, 108)
    red, red_dk, red_hi = (210, 24, 46), (128, 10, 28), (255, 96, 110)
    ink = (22, 22, 30)
    img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    shape = [[inside(x, y) for x in range(W)] for y in range(H)]
    for y in range(H):
        for x in range(W):
            if not shape[y][x]:
                continue
            edge = any(not (0 <= x + ox < W and 0 <= y + oy < H and shape[y + oy][x + ox])
                       for ox, oy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
            u, v = frame(x, y)
            if edge:
                c = ink
            elif opening(x, y, 0.66):
                c = (58, 62, 78) if v < -0.8 else (38, 40, 52)
            elif opening(x, y, 1.0) and u < 0.15:
                c = steel_hi if v < 0.6 else steel
            else:
                t = (v + r) / (2 * r)
                c = steel_hi if t < 0.28 else steel if t < 0.68 else steel_dk
                if 0.40 <= u <= 0.68:
                    c = red_hi if t < 0.28 else red if t < 0.68 else red_dk
            img.putpixel((x, y), (*c, 255))

    for x, y in [(12, 3), (11, 3), (12, 4)]:
        img.putpixel((x, y), (*ink, 255))
    img.putpixel((11, 4), (*steel_dk, 255))
    web, web_dk = (252, 252, 252, 255), (186, 196, 208, 255)
    for x, y in [(13, 2), (14, 1)]:
        img.putpixel((x, y), web)
    for x, y in [(15, 1), (14, 0), (13, 0), (15, 3), (12, 0), (15, 2)]:
        img.putpixel((x, y), web_dk if (x * 3 + y) % 2 else web)
    return img


def build_web_line():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y in range(16):
        edge = abs(y - 7.5) / 7.5
        for x in range(16):
            if edge > 0.86:
                continue
            fiber = 1.0 - 0.12 * ((x * 7 + y * 3) % 5 == 0)
            v = int((255 - 60 * edge ** 2) * fiber)
            img.putpixel((x, y), (v, v, min(255, v + 4), 255))
    return img


def build_icon(skin):
    face = skin.crop((8 * S, 8 * S, 16 * S, 16 * S)).resize((112, 112), Image.LANCZOS)
    icon = Image.new("RGBA", (128, 128), (0, 0, 0, 0))
    d = ImageDraw.Draw(icon)
    d.rounded_rectangle([0, 0, 127, 127], radius=22, fill=(20, 28, 60, 255))
    icon.paste(face, (8, 8))
    return icon


def main():
    preview_dir = sys.argv[1] if len(sys.argv) > 1 else None
    entity_dir = os.path.join(ASSETS, "textures", "entity")
    item_dir = os.path.join(ASSETS, "textures", "item")
    os.makedirs(entity_dir, exist_ok=True)
    os.makedirs(item_dir, exist_ok=True)

    skin = build_skin()
    skin.save(os.path.join(entity_dir, "spider_man.png"))
    build_web_line().save(os.path.join(entity_dir, "web_line.png"))
    shooter = build_web_shooter()
    shooter.save(os.path.join(item_dir, "web_shooter.png"))
    build_icon(skin).save(os.path.join(ASSETS, "icon.png"))

    if preview_dir:
        os.makedirs(preview_dir, exist_ok=True)
        preview(skin, os.path.join(preview_dir, "skin_doll.png"))
        skin.resize((512, 512), Image.NEAREST).save(os.path.join(preview_dir, "skin_atlas.png"))
        bg = Image.new("RGBA", (16, 16), (70, 78, 92, 255))
        bg.alpha_composite(shooter)
        bg.resize((256, 256), Image.NEAREST).save(os.path.join(preview_dir, "web_shooter_big.png"))
    print("textures written")


if __name__ == "__main__":
    main()
