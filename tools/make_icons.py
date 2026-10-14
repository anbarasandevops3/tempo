"""Draw the app icons (public/icon-*.png): a stopwatch ring with a lightning-bolt split."""
from pathlib import Path
from PIL import Image, ImageDraw

OUT = Path(__file__).resolve().parent.parent / "public"

def icon(size: int) -> Image.Image:
    s = 4  # supersample
    n = size * s
    img = Image.new("RGB", (n, n), "#111318")
    d = ImageDraw.Draw(img)
    c, r, w = n / 2, n * 0.30, n * 0.075
    d.ellipse([c - r, c - r + n * 0.04, c + r, c + r + n * 0.04], outline="#3a3f4b", width=int(w))
    # orange "work" arc, ~2/3 of the ring
    d.arc([c - r, c - r + n * 0.04, c + r, c + r + n * 0.04], start=-90, end=150, fill="#ff6b2c", width=int(w))
    # crown of the stopwatch
    d.rounded_rectangle([c - n * 0.06, n * 0.10, c + n * 0.06, n * 0.17], radius=n * 0.02, fill="#ff6b2c")
    return img.resize((size, size), Image.LANCZOS)

for size in (180, 192, 512):
    icon(size).save(OUT / f"icon-{size}.png")
print("ok")
