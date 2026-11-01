"""Türkiye'deki halka açık DC şarj istasyonlarını EPDK'nın açık servisinden alıp tek dosyaya yazar.

Kullanım:
    python tools/build_chargers.py <çıktı.json>                 EPDK'dan bir kez çeker
    python tools/build_chargers.py <çıktı.json> --raw <dosya>    kayıtlı EPDK cevabından üretir (çağrı yok)
    python tools/build_chargers.py --selftest

Kaynak: EPDK Serbest Erişim Platformu, https://apigateway.epdk.gov.tr/sarjIstasyonlari
(anahtarsız; GET isteği JSON gövdeyle; çok sıkı kota: saatte bir çağrıdan fazlası 429 döner).
Ham cevap çıktının yanına "<çıktı>.raw.json" olarak kaydedilir; fiyat değişince yeniden
çağırmadan --raw ile üretilebilir.

Çıktı, stations.json ile aynı yapıdadır ("elements"); uygulama ikisini aynı kodla okur.
Fiyatlar tools/ev_prices.json'dan operatör adına göre eklenir.
"""
import io
import json
import math
import os
import re
import sys
import urllib.error
import urllib.request
import zlib

API = "https://apigateway.epdk.gov.tr/sarjIstasyonlari"
BOX = (35.80, 25.60, 42.20, 44.90)  # Türkiye kutusu (build_stations.py ile aynı)
MIN_STATIONS = 3000  # bundan az kayıt yarım cevaptır; eldeki iyi dosyanın üzerine yazılmaz
PRICES = os.path.join(os.path.dirname(os.path.abspath(__file__)), "ev_prices.json")


def fetch():
    request = urllib.request.Request(
        API, data=b"{}", method="GET",
        headers={"Content-Type": "application/json; charset=utf-8", "Accept": "application/json",
                 "User-Agent": "YakitRotam/1.0 (station dataset build)"})
    try:
        return urllib.request.urlopen(request, timeout=300).read()
    except urllib.error.HTTPError as error:
        body = error.read()[:300]
        sys.exit(f"EPDK HTTP {error.code}: {body!r}\nKota dolmuş olabilir: en az 1 saat bekleyip tekrar dene.")


def number(value):
    """'180', '180,5', 180.0, None -> float ya da None."""
    if value is None:
        return None
    try:
        result = float(str(value).strip().replace(",", "."))
    except ValueError:
        return None
    return result if result > 0 else None


def numeric_id(value):
    """ŞRJ/1234 -> 1234; rakamsız kimlikler için kararlı bir sayı (uygulama kimliği sayı bekler)."""
    digits = re.sub(r"\D", "", str(value or ""))
    return int(digits) if digits else zlib.crc32(str(value).encode("utf-8"))


def socket_kind(socket):
    """Soketin türünü 'ccs', 'chademo', 'ac' ya da None olarak verir; alan adları değişse de bulur."""
    text = " ".join(str(v) for k, v in socket.items() if "tur" in k.lower() or "tip" in k.lower()).upper()
    if "CHADEMO" in text:
        return "chademo"
    if "CCS" in text or ("DC" in text and "GB" not in text):
        return "ccs"
    if "AC" in text or "TYPE" in text:
        return "ac"
    return None


def socket_power(socket):
    """Soket gücü: adında güç geçen alan (fiyat alanları atlanır); "180 kW" gibi metni de okur."""
    for key, value in socket.items():
        lower = key.lower()
        if "fiyat" in lower or "price" in lower or "ucret" in lower:
            continue
        if "guc" in lower or "güç" in lower or "kw" in lower or "power" in lower:
            power = number(re.sub(r"[^0-9.,]", "", str(value)) if value is not None else None)
            if power:
                return power
    return None


TR_LOWER = str.maketrans({"I": "ı", "İ": "i"})
TR_UPPER = str.maketrans({"i": "İ", "ı": "I"})
ASCII_FOLD = str.maketrans("çğıöşüâî", "cgiosuai")


def tr_lower(text):
    return text.translate(TR_LOWER).lower()


def fold(text):
    """Karşılaştırma anahtarı: Türkçe küçük harf, ASCII harf, yalnızca harf/rakam ve tek boşluk."""
    text = tr_lower(text or "").translate(ASCII_FOLD)
    return " ".join(re.sub(r"[^a-z0-9]+", " ", text).split())


def title_case(text):
    """Türkçe büyük/küçük harfle başlık: BAKIRKÖY -> Bakırköy, İZMİT -> İzmit."""
    lower = " ".join(tr_lower(text).split())
    # Kelime başları ve tire/eğik çizgi/parantez sonrası büyük: "K-ŞARJ" -> "K-Şarj".
    return re.sub(r"(^|[\s\-/(])(\w)", lambda m: m.group(1) + m.group(2).translate(TR_UPPER).upper(), lower)


PROVINCES = {fold(name) for name in (
    "Adana Adıyaman Afyonkarahisar Ağrı Aksaray Amasya Ankara Antalya Ardahan Artvin Aydın Balıkesir Bartın "
    "Batman Bayburt Bilecik Bingöl Bitlis Bolu Burdur Bursa Çanakkale Çankırı Çorum Denizli Diyarbakır Düzce "
    "Edirne Elazığ Erzincan Erzurum Eskişehir Gaziantep Giresun Gümüşhane Hakkari Hatay Iğdır Isparta İstanbul "
    "İzmir Kahramanmaraş Karabük Karaman Kars Kastamonu Kayseri Kilis Kırıkkale Kırklareli Kırşehir Kocaeli "
    "Konya Kütahya Malatya Manisa Mardin Mersin Muğla Muş Nevşehir Niğde Ordu Osmaniye Rize Sakarya Samsun "
    "Şanlıurfa Siirt Sinop Şırnak Sivas Tekirdağ Tokat Trabzon Tunceli Uşak Van Yalova Yozgat Zonguldak"
).split()}


# İl merkezleri (app/src/main/java/com/yakitrotam/app/util/Provinces.kt ile aynı; İstanbul iki yaka).
PROVINCE_CENTERS = [
    ("Adana", 37.0, 35.32),
    ("Adıyaman", 37.76, 38.28),
    ("Afyonkarahisar", 38.76, 30.54),
    ("Ağrı", 39.72, 43.05),
    ("Aksaray", 38.37, 34.03),
    ("Amasya", 40.65, 35.83),
    ("Ankara", 39.93, 32.85),
    ("Antalya", 36.89, 30.71),
    ("Ardahan", 41.11, 42.7),
    ("Artvin", 41.18, 41.82),
    ("Aydın", 37.85, 27.84),
    ("Balıkesir", 39.65, 27.88),
    ("Bartın", 41.64, 32.34),
    ("Batman", 37.88, 41.13),
    ("Bayburt", 40.26, 40.23),
    ("Bilecik", 40.14, 29.98),
    ("Bingöl", 38.88, 40.5),
    ("Bitlis", 38.4, 42.11),
    ("Bolu", 40.74, 31.61),
    ("Burdur", 37.72, 30.29),
    ("Bursa", 40.19, 29.06),
    ("Çanakkale", 40.15, 26.41),
    ("Çankırı", 40.6, 33.62),
    ("Çorum", 40.55, 34.95),
    ("Denizli", 37.78, 29.09),
    ("Diyarbakır", 37.91, 40.24),
    ("Düzce", 40.84, 31.16),
    ("Edirne", 41.68, 26.56),
    ("Elazığ", 38.67, 39.22),
    ("Erzincan", 39.75, 39.49),
    ("Erzurum", 39.9, 41.27),
    ("Eskişehir", 39.78, 30.52),
    ("Gaziantep", 37.07, 37.38),
    ("Giresun", 40.91, 38.39),
    ("Gümüşhane", 40.46, 39.48),
    ("Hakkari", 37.57, 43.74),
    ("Hatay", 36.2, 36.16),
    ("Iğdır", 39.92, 44.05),
    ("Isparta", 37.76, 30.55),
    ("İstanbul", 41.03, 28.8),
    ("İstanbul", 40.98, 29.25),
    ("İzmir", 38.42, 27.14),
    ("Kahramanmaraş", 37.58, 36.93),
    ("Karabük", 41.2, 32.62),
    ("Karaman", 37.18, 33.22),
    ("Kars", 40.6, 43.1),
    ("Kastamonu", 41.38, 33.78),
    ("Kayseri", 38.72, 35.49),
    ("Kilis", 36.72, 37.12),
    ("Kırıkkale", 39.85, 33.51),
    ("Kırklareli", 41.73, 27.22),
    ("Kırşehir", 39.15, 34.16),
    ("Kocaeli", 40.77, 29.94),
    ("Konya", 37.87, 32.48),
    ("Kütahya", 39.42, 29.98),
    ("Malatya", 38.35, 38.31),
    ("Manisa", 38.61, 27.43),
    ("Mardin", 37.31, 40.74),
    ("Mersin", 36.81, 34.64),
    ("Muğla", 37.22, 28.36),
    ("Muş", 38.73, 41.49),
    ("Nevşehir", 38.62, 34.71),
    ("Niğde", 37.97, 34.68),
    ("Ordu", 40.98, 37.88),
    ("Osmaniye", 37.07, 36.25),
    ("Rize", 41.02, 40.52),
    ("Sakarya", 40.77, 30.4),
    ("Samsun", 41.29, 36.33),
    ("Şanlıurfa", 37.16, 38.79),
    ("Siirt", 37.93, 41.94),
    ("Sinop", 42.03, 35.15),
    ("Şırnak", 37.52, 42.46),
    ("Sivas", 39.75, 37.02),
    ("Tekirdağ", 40.98, 27.51),
    ("Tokat", 40.31, 36.55),
    ("Trabzon", 41.0, 39.72),
    ("Tunceli", 39.11, 39.55),
    ("Uşak", 38.68, 29.41),
    ("Van", 38.49, 43.38),
    ("Yalova", 40.65, 29.27),
    ("Yozgat", 39.82, 34.81),
    ("Zonguldak", 41.45, 31.79)
]
MAX_KM_FROM_PROVINCE = 200  # en büyük ilin (Konya) merkezinden sınırına uzaklık ~180 km


def distance_km(lat1, lon1, lat2, lon2):
    p = math.pi / 180
    a = (math.sin((lat2 - lat1) * p / 2) ** 2 +
         math.cos(lat1 * p) * math.cos(lat2 * p) * math.sin((lon2 - lon1) * p / 2) ** 2)
    return 2 * 6371 * math.asin(math.sqrt(a))


def province_distance(address, lat, lon):
    """Adresin sonundaki ilin merkezine uzaklık (km); il okunamazsa None."""
    if "/" not in (address or ""):
        return None
    province = fold(address.rsplit("/", 1)[1])
    centers = [(la, lo) for name, la, lo in PROVINCE_CENTERS if fold(name) == province]
    return min(distance_km(lat, lon, la, lo) for la, lo in centers) if centers else None


def checked_coordinates(row, lat, lon):
    """EPDK'da birkaç kayıtta enlem/boylam yer değiştirmiş ya da başka ile düşmüş. Adresteki ille
    karşılaştırır: uzaksa ve ters hali tutuyorsa düzeltir, hiç tutmuyorsa None döner (kayıt atılır)."""
    near = province_distance(row.get("adres"), lat, lon)
    if near is None or near <= MAX_KM_FROM_PROVINCE:
        return lat, lon
    swapped = province_distance(row.get("adres"), lon, lat)
    if swapped is not None and swapped <= MAX_KM_FROM_PROVINCE:
        return lon, lat
    return None


def place_from_address(address):
    """'... No:5/1 Beykoz / İSTANBUL' -> 'Beykoz'. Son '/' sonrası il adı değilse ya da ilçe yerinde
    kapı numarası duruyorsa boş döner; uygulama o zaman en yakın il adını kullanır."""
    if "/" not in (address or ""):
        return ""
    before, after = address.rsplit("/", 1)
    if fold(after) not in PROVINCES:
        return ""
    words = before.split()
    district = words[-1] if words else ""
    if not district or re.search(r"[0-9:.]", district):
        return ""
    # "Merkez" tek başına yer söylemez: il merkezinde ilin adı yazılır.
    return title_case(after.strip() if fold(district) == "merkez" else district)


def load_prices():
    data = json.load(io.open(PRICES, encoding="utf-8"))
    return [(key, entry["dc"], [fold(m) for m in entry["match"]], entry.get("name", key))
            for key, entry in data["operators"].items()]


def match_price(operator, prices):
    """Operatör adını kelime sınırlarıyla eşler: "aksa" "Aksaray Belediyesi"ne, "zes" "Gazes"e uymaz."""
    text = " " + fold(operator) + " "
    for key, dc, needles, *_ in prices:
        if any(" " + needle + " " in text for needle in needles):
            return key, dc
    return None, None


def readable(text):
    """EPDK'da adların bir kısmı tamamen büyük ya da küçük harfle girilmiş: "BİLECİK KÖFTECİ YUSUF"
    -> "Bilecik Köfteci Yusuf". Karışık yazılmış adlara ("TŞOF Sivrihisar DC") dokunulmaz."""
    text = (text or "").strip()
    letters = [ch for ch in text if ch.isalpha()]
    if len(letters) > 3 and (all(ch.isupper() for ch in letters) or all(ch.islower() for ch in letters)):
        return title_case(text)
    return text


def display_operator(marka, price_key, prices):
    """Kartta görünen operatör adı: bilinen ağda düzgün yazımı, değilse okunur biçimi."""
    for key, _, _, name in prices:
        if key == price_key:
            return name
    return readable(marka)


def build(rows, prices):
    elements, unmatched, matched, kinds, services, swapped, misplaced = [], {}, {}, {}, {}, [], []
    for row in rows:
        service = str(row.get("hizmetSekli") or "")
        services[service] = services.get(service, 0) + 1
        if fold(service) != "halka acik":  # EPDK: HALKA_ACIK / OZEL
            continue
        lat, lon = number(row.get("enlem")), number(row.get("boylam"))
        if not lat or not lon:
            continue
        coordinates = checked_coordinates(row, lat, lon)
        if coordinates is None:
            misplaced.append(f"{row.get('sarjIstasyonuNo')} {row.get('sarjIstasyonuAdi')} ({lat}, {lon}) {row.get('adres')}")
            continue
        if coordinates != (lat, lon):
            swapped.append(str(row.get("sarjIstasyonuNo")))
        lat, lon = coordinates
        if not (BOX[0] <= lat <= BOX[2] and BOX[1] <= lon <= BOX[3]):
            continue
        sockets = row.get("soketler") or []
        if isinstance(sockets, str):
            try:
                sockets = json.loads(sockets)
            except ValueError:
                sockets = []
        best, counts = {}, {}
        for socket in sockets if isinstance(sockets, list) else []:
            if not isinstance(socket, dict):
                continue
            kind, power = socket_kind(socket), socket_power(socket)
            kinds[kind] = kinds.get(kind, 0) + 1
            if kind is None or power is None:
                continue
            counts[(kind, power)] = counts.get((kind, power), 0) + 1
            best[kind] = max(best.get(kind, 0), power)
        if "ccs" not in best and "chademo" not in best:
            continue  # yalnızca DC hızlı şarj: yol üstünde AC ile şarj saatler sürer
        operator = (row.get("marka") or row.get("sarjAgiIsletmecisiUnvan") or "").strip()
        price_key, price = match_price(operator + " " + str(row.get("sarjAgiIsletmecisiUnvan") or ""), prices)
        if price_key is None:
            unmatched[operator] = unmatched.get(operator, 0) + 1
        else:
            label = f"{operator} -> {price_key}"
            matched[label] = matched.get(label, 0) + 1
        shown = display_operator(operator, price_key, prices)
        parts = [shown] + [
            f"{'CCS' if k == 'ccs' else 'CHAdeMO' if k == 'chademo' else 'AC'} {p:g} kW" + (f" ×{n}" if n > 1 else "")
            for (k, p), n in sorted(counts.items(), key=lambda item: (item[0][0] != "ccs", -item[0][1]))
        ]
        if str(row.get("yesilSarjIstasyonuMu") or "").lower() in ("true", "evet", "1"):
            parts.append("Yeşil enerji")
        tags = {
            "name": readable(row.get("sarjIstasyonuAdi") or shown or "Şarj istasyonu"),
            "operator": shown,
            "addr:city": place_from_address(row.get("adres")),
            "charge:info": " · ".join(p for p in parts if p),
        }
        if "ccs" in best:
            tags["charge:ccs_kw"] = f"{best['ccs']:g}"
        if "chademo" in best:
            tags["charge:chademo_kw"] = f"{best['chademo']:g}"
        if price:
            tags["charge:price"] = f"{price:g}"
        elements.append({"type": "epdk", "id": numeric_id(row.get("sarjIstasyonuNo")), "lat": lat, "lon": lon, "tags": tags})
    elements.sort(key=lambda element: element["id"])
    report = {
        "rows": len(rows), "dc_public": len(elements), "hizmetSekli": services, "socket_kinds": kinds,
        "swapped_coordinates": swapped,
        "dropped_misplaced": misplaced,
        "matched_operators": sorted(matched.items(), key=lambda item: -item[1])[:40],
        "unmatched_operators": sorted(unmatched.items(), key=lambda item: -item[1])[:20],
    }
    return elements, report


def rows_of(raw):
    data = json.loads(raw)
    if "fault" in data:
        sys.exit(f"EPDK hata döndü: {data['fault']}")
    rows = data.get("data") or data.get("result") or []
    columns = data.get("columnNames")
    if rows and isinstance(rows[0], list) and columns:
        rows = [dict(zip(columns, row)) for row in rows]
    return rows


def write(output, elements):
    previous = 0
    if os.path.exists(output):
        try:
            previous = len(json.load(io.open(output, encoding="utf-8"))["elements"])
        except (ValueError, KeyError):
            previous = 0
    if len(elements) < MIN_STATIONS or len(elements) < previous * 0.9:
        sys.exit(f"Yalnızca {len(elements)} istasyon (önceki {previous}); dosya yazılmadı.")
    with io.open(output, "w", encoding="utf-8", newline="\n") as handle:
        handle.write('{"elements":[\n')
        handle.write(",\n".join(json.dumps(e, ensure_ascii=False, separators=(",", ":")) for e in elements))
        handle.write("\n]}\n")


def selftest():
    prices = [("zes", 16.49, ["zes"], "ZES"), ("trugo", 14.98, ["trugo"], "Trugo")]
    rows = [
        {"sarjIstasyonuNo": "ŞRJ/1000", "sarjIstasyonuAdi": "Kerimaba Petrol", "hizmetSekli": "Halka Açık",
         "marka": "ZES", "enlem": "41,17774", "boylam": 28.748019, "adres": "Anadolu Mah. No:1 Arnavutköy / İSTANBUL",
         "yesilSarjIstasyonuMu": "true",
         "soketler": [{"soketTuru": "DC_CCS", "soketGucu": "180"}, {"soketTuru": "DC_CCS", "soketGucu": 180},
                      {"soketTuru": "AC_TYPE2", "soketGucu": "22"}]},
        {"sarjIstasyonuNo": "ŞRJ/1001", "hizmetSekli": "Özel", "marka": "ZES", "enlem": 41.0, "boylam": 29.0,
         "soketler": [{"soketTuru": "DC_CCS", "soketGucu": 60}]},
        {"sarjIstasyonuNo": "ŞRJ/1002", "hizmetSekli": "Halka Açık", "marka": "X", "enlem": 39.0, "boylam": 32.0,
         "soketler": [{"soketTuru": "AC_TYPE2", "soketGucu": 22}]},
        {"sarjIstasyonuNo": "ABC", "hizmetSekli": "Halka Açık", "marka": "Trugo", "enlem": 39.9, "boylam": 32.8,
         "soketler": json.dumps([{"soketTuru": "DC_CHADEMO", "soketGucu": "50"}])},
        {"sarjIstasyonuNo": "ŞRJ/1004", "hizmetSekli": "Halka Açık", "marka": "ZES", "enlem": 0, "boylam": 0,
         "soketler": [{"soketTuru": "DC_CCS", "soketGucu": 60}]},
    ]
    elements, report = build(rows, prices)
    assert [e["id"] for e in elements] == sorted([1000, zlib.crc32(b"ABC")]), elements
    first = next(e for e in elements if e["id"] == 1000)
    assert first["lat"] == 41.17774 and first["tags"]["charge:ccs_kw"] == "180", first
    assert first["tags"]["charge:price"] == "16.49" and first["tags"]["addr:city"] == "Arnavutköy", first
    assert "CCS 180 kW ×2" in first["tags"]["charge:info"] and "Yeşil enerji" in first["tags"]["charge:info"]
    assert first["tags"]["operator"] == "ZES" and first["tags"]["charge:info"].startswith("ZES · ")
    assert display_operator("tunçmatik", None, prices) == "Tunçmatik" and display_operator("K-ŞARJ", None, prices) == "K-Şarj"
    assert readable("BİLECİK BOZÜYÜK KÖFTECİ YUSUF") == "Bilecik Bozüyük Köfteci Yusuf"
    assert readable("TŞOF Sivrihisar DC") == "TŞOF Sivrihisar DC" and readable("KIRCI") == "Kırcı"
    second = next(e for e in elements if e["id"] != 1000)
    assert second["tags"]["charge:chademo_kw"] == "50" and "charge:ccs_kw" not in second["tags"], second
    assert number("12,5") == 12.5 and number(None) is None and number("x") is None
    real = [("aksa", 13.99, ["aksa"], "Aksa"), ("zes", 16.49, ["zes"], "ZES"), ("otopriz", 13.9, ["otopriz"], "Otopriz"),
            ("epower", 11.99, ["e power", "petrol ofisi"], "PO"), ("wat", 14.49, ["wat mobilite", "wat"], "WAT")]
    assert match_price("AKSARAY BELEDİYESİ", real) == (None, None)
    assert match_price("Gazes Enerji", real) == (None, None)
    assert match_price("Swat Elektrik", real) == (None, None)
    assert match_price("OTOPRİZ ŞARJ A.Ş.", real)[0] == "otopriz"
    assert match_price("PETROL OFİSİ A.Ş.", real)[0] == "epower"
    assert match_price("ZES DİJİTAL TİCARET A.Ş.", real)[0] == "zes"
    assert match_price("WAT MOBİLİTE", real)[0] == "wat"
    assert place_from_address("Yeni Mah. Bağdat Cd. No:5/1") == ""
    assert place_from_address("Merkez Mah. BAKIRKÖY / İSTANBUL") == "Bakırköy"
    assert place_from_address("Sanayi Cd. İZMİT / KOCAELİ") == "İzmit"
    assert place_from_address("Bağlar Mah. No:2 Merkez / AKSARAY") == "Aksaray"
    assert socket_power({"soketGucu": "180 kW", "kwFiyati": "16.49"}) == 180.0
    # Şanlıurfa adresli kayıt Sivas'a düşmüş: enlem/boylam ters -> düzeltilir.
    urfa = {"adres": "Haleplibahçe Mah. Haliliye / ŞANLIURFA"}
    assert checked_coordinates(urfa, 38.78, 37.15) == (37.15, 38.78)
    assert checked_coordinates(urfa, 37.16, 38.79) == (37.16, 38.79)
    assert checked_coordinates({"adres": "Merkez / SİVAS"}, 40.43, 29.10) is None
    assert checked_coordinates({"adres": "adres yok"}, 40.43, 29.10) == (40.43, 29.10)
    print("selftest ok", report)


def main(argv):
    sys.stdout.reconfigure(encoding="utf-8")  # Windows konsolunda Türkçe karakterler
    if argv[:1] == ["--selftest"]:
        selftest()
        return
    output = argv[0]
    if "--raw" in argv:
        raw = io.open(argv[argv.index("--raw") + 1], "rb").read()
    else:
        raw = fetch()
        io.open(output + ".raw.json", "wb").write(raw)
    elements, report = build(rows_of(raw), load_prices())
    print(json.dumps(report, ensure_ascii=False, indent=1))
    write(output, elements)
    print(len(elements), "şarj istasyonu yazıldı")


if __name__ == "__main__":
    main(sys.argv[1:])
