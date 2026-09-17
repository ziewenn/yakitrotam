"""Play Store görsellerini ham ekran görüntülerinden üretir.

Kullanım:
    python store/make_store_assets.py <ham_goruntu_klasoru>

Ham klasörde şu dosyalar beklenir (1344x2992, reklamsız build: ./gradlew installDebug -PnoAds):
    planner.png, summary.png, stops.png, vehicle.png
Çıktılar bu betiğin bulunduğu klasöre yazılır.
"""
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageFont

RAW = Path(sys.argv[1])
OUT = Path(__file__).parent
FONTS = Path("C:/Windows/Fonts")
BG = (15, 17, 21)
LIME = (158, 255, 61)
WHITE = (242, 244, 248)
MUTED = (163, 171, 186)


def font(name, size):
    return ImageFont.truetype(str(FONTS / name), size)


def glow(size, center, radius, alpha):
    layer = Image.new("RGBA", size, (0, 0, 0, 0))
    cx, cy = center
    ImageDraw.Draw(layer).ellipse((cx - radius, cy - radius, cx + radius, cy + radius), fill=LIME + (alpha,))
    return layer.filter(ImageFilter.GaussianBlur(radius // 2))


def rounded(img, radius):
    mask = Image.new("L", img.size, 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, img.width - 1, img.height - 1), radius, fill=255)
    out = Image.new("RGBA", img.size, (0, 0, 0, 0))
    out.paste(img, (0, 0), mask)
    ImageDraw.Draw(out).rounded_rectangle((0, 0, img.width - 1, img.height - 1), radius, outline=(46, 51, 61), width=4)
    return out


def phone(name):
    img = Image.open(RAW / f"{name}.png").convert("RGB")
    return img.crop((0, 150, img.width, img.height - 70))  # durum ve gezinme çubukları


def fade(width, height, power):
    layer = Image.new("RGBA", (width, height), (0, 0, 0, 0))
    draw = ImageDraw.Draw(layer)
    for y in range(height):
        draw.line((0, y, width, y), fill=BG + (int(255 * (y / (height - 1)) ** power),))
    return layer


def screenshot(raw, title, subtitle, name):
    width, height = 1080, 1920
    canvas = Image.new("RGBA", (width, height), BG + (255,))
    canvas.alpha_composite(glow((width, height), (width // 2, 180), 420, 34))
    draw = ImageDraw.Draw(canvas)
    size = 70
    while draw.textlength(title, font=font("segoeuib.ttf", size)) > 940:
        size -= 2
    for text, fnt, fill, y in (
        (title, font("segoeuib.ttf", size), WHITE, 120 + (70 - size) // 2),
        (subtitle, font("segoeui.ttf", 38), MUTED, 222),
    ):
        draw.text(((width - draw.textlength(text, font=fnt)) / 2, y), text, font=fnt, fill=fill)
    shot = phone(raw)
    shot_width = 860
    shot = rounded(shot.resize((shot_width, int(shot.height * shot_width / shot.width)), Image.LANCZOS), 56)
    canvas.alpha_composite(shot, ((width - shot_width) // 2, 330))
    canvas.alpha_composite(fade(width, 260, 1.6), (0, height - 260))
    canvas.convert("RGB").save(OUT / f"{name}.png")


screenshot("planner", "Rotanı gir, deponu söyle", "Göstergeyi aracındaki gibi ayarla, gerisini bırak", "screenshot-1")
screenshot("summary", "Yolculuğun maliyeti hazır", "Mesafe, süre ve pompada ödeyeceğin tutar", "screenshot-2")
screenshot("stops", "Gerçekten yol üstündeki istasyonlar", "Otoyoldan çıkartan duraklar elenir", "screenshot-3")
screenshot("vehicle", "Aracını bir kez tanıt", "Tüketim, depo ve marka tercihin hatırlanır", "screenshot-4")

# Öne çıkan görsel (1024x500)
width, height = 1024, 500
feature = Image.new("RGBA", (width, height), BG + (255,))
feature.alpha_composite(glow((width, height), (230, 250), 300, 42))
draw = ImageDraw.Draw(feature)
icon = Image.open(OUT / "icon-512.png").convert("RGBA").resize((112, 112), Image.LANCZOS)
icon_mask = Image.new("L", icon.size, 0)
ImageDraw.Draw(icon_mask).rounded_rectangle((0, 0, 111, 111), 28, fill=255)
feature.paste(icon, (64, 110), icon_mask)
draw.text((64, 240), "YakıtRotam", font=font("segoeuib.ttf", 66), fill=WHITE)
draw.text((66, 330), "Yol boyunca doğru yerde,", font=font("seguisb.ttf", 32), fill=MUTED)
draw.text((66, 372), "doğru kadar yakıt.", font=font("seguisb.ttf", 32), fill=LIME)
shot = phone("summary").crop((0, 0, 1344, 1500))
shot_width = 400
shot = rounded(shot.resize((shot_width, int(shot.height * shot_width / shot.width)), Image.LANCZOS), 36)
feature.alpha_composite(shot, (width - shot_width - 64, 56))
feature.alpha_composite(fade(width, 120, 1.4), (0, height - 120))
feature.convert("RGB").save(OUT / "feature-graphic-1024x500.png")
print("ok")
