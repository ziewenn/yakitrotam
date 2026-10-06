"""Türkiye'deki akaryakıt istasyonlarını OpenStreetMap'ten indirip tek dosyaya yazar.

Kullanım:
    python tools/build_stations.py <çıktı.json>

Uygulama bu dosyayı GitHub Pages'ten (ziewenn.github.io/yakitrotam/stations.json) okur;
böylece her rota için herkese açık Overpass sunucularına bağımlı kalmaz. Dosya, Overpass
yanıtıyla aynı yapıdadır ("elements"), yalnızca uygulamanın okuduğu etiketler bırakılır.

Veri © OpenStreetMap katkıcıları, ODbL.
"""
import json
import sys
import time
import urllib.parse
import urllib.request

# Türkiye'yi kapsayan kutu; komşu ülkelerden gelen fazlalık zararsızdır (rota dışında kalır).
QUERY = '[out:json][timeout:180];nwr["amenity"="fuel"](35.80,25.60,42.20,44.90);out center tags;'
ENDPOINTS = [
    "https://overpass-api.de/api/interpreter",
    "https://overpass.openstreetmap.fr/api/interpreter",
]
# OverpassStationSource.parseElements'in okuduğu etiketler.
TAGS = [
    "brand", "operator", "name", "opening_hours", "addr:street", "addr:city", "addr:district",
    "fuel:lpg", "fuel:diesel", "fuel:octane_95", "fuel:octane_91", "fuel:octane_98",
    "fuel:octane_100", "fuel:e10", "shop", "fuel:shop", "restaurant",
]
# Bundan az kayıt gelirse sorgu yarım kalmıştır; eldeki iyi dosyanın üzerine yazılmaz.
MIN_STATIONS = 10000


def fetch():
    for attempt in range(6):
        for endpoint in ENDPOINTS:
            try:
                request = urllib.request.Request(
                    endpoint,
                    data=urllib.parse.urlencode({"data": QUERY}).encode(),
                    headers={"User-Agent": "YakitRotam/1.0 (station dataset build)"},
                )
                elements = json.loads(urllib.request.urlopen(request, timeout=240).read())["elements"]
                if len(elements) >= MIN_STATIONS:
                    return elements
                print(f"{endpoint}: yalnızca {len(elements)} kayıt", file=sys.stderr)
            except Exception as error:  # sunucu meşgul olabilir; sıradakini dene
                print(f"{endpoint}: {error!r}", file=sys.stderr)
        time.sleep(20)
    sys.exit("İstasyon verisi alınamadı")


def main():
    stations = []
    for element in fetch():
        center = element.get("center", {})
        lat, lon = element.get("lat", center.get("lat")), element.get("lon", center.get("lon"))
        if lat is None or lon is None:
            continue
        tags = {key: value for key, value in element.get("tags", {}).items() if key in TAGS}
        stations.append({"type": element["type"], "id": element["id"], "lat": lat, "lon": lon, "tags": tags})
    stations.sort(key=lambda station: (station["type"], station["id"]))  # farklar okunabilir kalsın
    with open(sys.argv[1], "w", encoding="utf-8", newline="\n") as output:
        json.dump({"elements": stations}, output, ensure_ascii=False, separators=(",", ":"))
    print(len(stations), "istasyon yazıldı")


if __name__ == "__main__":
    main()
