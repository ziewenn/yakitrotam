"""Play Store görsellerini ham ekran görüntülerinden üretir.

Kullanım:
    python store/make_store_assets.py <ham_goruntu_klasoru>

Ham klasörde şu dosyalar beklenir (1344x2992, reklamsız build: ./gradlew installDebug -PnoAds):
    planner.png, summary.png, cost.png, ev.png, map.png, defter.png, widget.png, vehicle.png
Çıktılar bu betiğin bulunduğu klasöre yazılır.
"""
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageFont

RAW = Path(sys.argv[1])
OUT = Path(__file__).parent
FONTS = Path("C:/Windows/Fonts")
BG = (243, 248, 246)
LIME = (11, 122, 108)    # vurgu: camgöbeği
WHITE = (19, 35, 32)     # başlık metni (açık zeminde koyu)
MUTED = (75, 93, 89)


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
    ImageDraw.Draw(out).rounded_rectangle((0, 0, img.width - 1, img.height - 1), radius, outline=(211, 227, 221), width=4)
    return out


def phone(name, top=150):
    """Durum ve gezinme çubukları kırpılır; top büyütülürse ekranın alt kısmı gösterilir."""
    img = Image.open(RAW / f"{name}.png").convert("RGB")
    return img.crop((0, top, img.width, img.height - 70))


def fade(width, height, power):
    layer = Image.new("RGBA", (width, height), (0, 0, 0, 0))
    draw = ImageDraw.Draw(layer)
    for y in range(height):
        draw.line((0, y, width, y), fill=BG + (int(255 * (y / (height - 1)) ** power),))
    return layer


def screenshot(raw, title, subtitle, name, top=150, bottom=None, shot_width=800):
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
    shot = phone(raw, top)
    # Telefon kartı tuvalin içinde biter; alt köşeler görünür, düğmeler soluklaştırılmaz.
    fit = int((height - 330 - 50) * shot.width / shot_width)
    shot = shot.crop((0, 0, shot.width, min(shot.height, fit, (bottom - top) if bottom else fit)))
    shot = rounded(shot.resize((shot_width, int(shot.height * shot_width / shot.width)), Image.LANCZOS), 56)
    canvas.alpha_composite(shot, ((width - shot_width) // 2, 330))
    canvas.convert("RGB").save(OUT / f"{name}.png")


SHOTS = [
    ("planner", "Rotanı gir, deponu söyle", "Sık gittiğin rotalar tek dokunuşta", 150, 2665),
    ("summary", "Planın hazır: nerede, ne kadar", "Mesafe, süre ve duraklarda ödeyeceğin tutar", 150, 2580),
    ("cost", "Köprü ve otoyol ücreti de hesaplanır", "Osmangazi, 1915 Çanakkale, Avrasya ve otoyollar", 1000),
    ("ev", "Elektrikli araçla şarj planı", "Üye olduğun şarj ağı öne alınır", 150, 2635),
    ("map", "Yakındaki istasyonlar haritada", "Akaryakıt ve hızlı şarj; favorilerin yıldızlı", 150, 2680),
    ("defter", "Gerçek tüketimini öğren", "Dolumları gir, planlar senin aracına göre olsun", 150, 2665),
    ("widget", "Pompa fiyatları ana ekranında", "Benzin, motorin ve otogaz bir bakışta", 150, 2380),
    ("vehicle", "Aracını bir kez tanıt", "68 elektrikli model hazır; yakıtlı araç da kayıtlı kalır", 150),
]
for index, (raw, title, subtitle, *crop) in enumerate(SHOTS, start=1):
    screenshot(raw, title, subtitle, f"screenshot-{index}", *crop)

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
draw.text((66, 372), "yetecek kadar yakıt ve şarj.", font=font("seguisb.ttf", 32), fill=LIME)
shot = phone("summary").crop((0, 0, 1344, 1500))
shot_width = 400
shot = rounded(shot.resize((shot_width, int(shot.height * shot_width / shot.width)), Image.LANCZOS), 36)
feature.alpha_composite(shot, (width - shot_width - 64, 56))
feature.alpha_composite(fade(width, 120, 1.4), (0, height - 120))
feature.convert("RGB").save(OUT / "feature-graphic-1024x500.png")
print("ok")
