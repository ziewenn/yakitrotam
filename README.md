Türkçe | [English](README.en.md)

# YakıtRotam

İçten yanmalı araçlar için yakıt durağı planlayıcı. Deponuzun hacmini, ortalama
tüketiminizi ve anlık doluluk oranını girersiniz; uygulama güzergah üzerinde
rezerve düşmeden nerede yakıt almanız gerektiğini hesaplar ve rotayı duraklarla
birlikte Google Maps'e aktarır.

Elektrikli araçlardaki ABRP mantığının benzin, motorin ve LPG karşılığı.

## Nasıl çalışır

Rota OSRM'den çekilir. Ardından güzergah koridorundaki akaryakıt istasyonları
OpenStreetMap Overpass API'sinden indirilir; her istasyon rotaya izdüşürülerek
"başlangıçtan kaç km sonra" ve "rotadan kaç km sapma" değerleri hesaplanır.

Durak seçimi menzil kısıtlı bir açgözlü aramadır: yalnızca mevcut yakıtla
gerçekten ulaşılabilen istasyonlar aday olur, bunlar arasından mümkün olduğunca
ileride olan seçilir. Sapma mesafesi, marka tercihi ve OSM'de yakıt türünün
doğrulanmış olup olmaması puanlamaya girer. Menzil içinde hiç uygun istasyon
yoksa durak uydurulmaz, kullanıcı uyarılır.

Maliyet hesabı canlı pompa fiyatından yapılır. Son durakta depo tam
doldurulmaz; varışa yetecek miktar, rezerv ve %15 güvenlik payı kadar yakıt
alınır.

## Veri kaynakları

Uygulama hiçbir API anahtarı istemez. Kullanılan servislerin tamamı ücretsiz ve
anahtarsızdır.

| Veri | Kaynak |
| --- | --- |
| Akaryakıt istasyonları | OpenStreetMap Overpass API (ODbL) |
| Pompa fiyatları | Opet il bazlı fiyat servisi |
| Sürüş rotası | OSRM |
| Adres arama | Photon |
| Ters coğrafi kodlama | Nominatim |
| Cihaz konumu | FusedLocationProvider |

Google Places ve Maps SDK çağrı başına ücretlendirildiği için kullanılmıyor.
Google Maps yalnızca hazır rotayı açmak üzere Android intent'i ile çağrılıyor,
bu ücretsizdir.

İstasyon verisi uygulamaya gömülü değildir, her rota için yeniden çekilir.
Böylece gerçekte var olmayan bir konuma durak konmaz.

Opet servisi benzin ve motorini yayınlıyor, otogaz yayınlamıyor. LPG fiyatı
benzine oranlanarak tahmin ediliyor ve arayüzde "tahmini" olarak işaretleniyor.

## Özellikler

- Depo hacmi, L/100km tüketim, anlık doluluk ve rezerv eşiğine göre menzil
  simülasyonu.
- Benzin, motorin ve LPG. İstasyonun ilgili yakıtı sunup sunmadığı OSM
  etiketlerinden okunur.
- Marka tercihi (Shell, Opet, Petrol Ofisi, BP, TotalEnergies, Aytemiz,
  Türkiye Petrolleri, Alpet, Lukoil). Tercih katı bir filtre değil puanlama
  kriteridir; menzil içinde tercih edilen marka yoksa sürücü yolda bırakılmaz.
- Durak başına: varışta kalan yakıt yüzdesi ve litresi, alınacak litre, tahmini
  tutar, ana yoldan sapma mesafesi.
- Yolculuğun yakıt maliyeti, pompada ödenecek toplam ve 100 km başına maliyet
  dökümü.
- Rota ve durakların çizim önizlemesi.
- Tüm duraklar ara nokta olarak Google Maps'e aktarılır.
- Son girilen araç bilgileri (depo hacmi, tüketim, yakıt türü, doluluk), marka
  tercihi, kalkış ve varış noktası uygulama kapatılıp açılınca korunur. Adres
  aramasında son seçilen yerler en üstte gösterilir.

## Çalıştırma

Android Studio'da projeyi açıp Gradle senkronizasyonunu bekleyin, ardından bir
emülatör veya USB ile bağlı cihaz seçip `app` yapılandırmasını çalıştırın.

Terminalden:

```bash
./gradlew installDebug
```

API anahtarı veya `local.properties` yapılandırması gerekmez. Uygulamanın
internet erişimi olması yeterlidir.

## Testler

```bash
./gradlew test
```

| Test | Kapsam |
| --- | --- |
| `GeoUtilsTest` | Mesafe, rota izdüşümü ve interpolasyon formülleri |
| `FuelOptimizerEngineTest` | Menzil kısıtı, marka puanlaması, maliyet hesabı |
| `DemoRoutePlanTest` | Gerçek OSRM rotası ve 391 gerçek OSM istasyonuyla uçtan uca plan |
| `GoogleMapsIntentTest` | Üretilen Maps URL'sinin durak parametreleri |
| `PersistenceAndQueryTest` | Ayarların kaydedilip geri yüklenmesi, istasyon sorgusunun rotayı kapsaması |

`DemoRoutePlanTest` canlı servislerden alınmış İstanbul-Ankara verisini
`app/src/test/resources/demo/` altından okur. Ağa çıkmaz, ama gerçek veriyle
çalıştığı için seçilen durakların gerçekten var olan istasyonlar olduğunu ve
rotanın 6 km'lik koridorunda kaldığını doğrular.

## APK ve otomatik yayın

`main` dalına gönderilen her değişiklikte
[`.github/workflows/android-release.yml`](.github/workflows/android-release.yml)
testleri çalıştırır, sürüm numarasını pipeline numarasıyla artırır, aynı release
anahtarıyla imzalı APK üretir ve GitHub Releases'e yükler.

En güncel APK: https://github.com/ziewenn/yakitrotam/releases/latest

Güncelleme bildirimi almak için [Obtainium](https://github.com/ImranR98/Obtainium)
kullanılabilir; kaynak olarak bu reponun URL'sini eklemek yeterlidir. Android
güvenlik modeli nedeniyle mağaza dışı uygulamalar sessiz kurulum yapamaz,
güncellemede kurulum onayı istenir.

CI imza anahtarının yerel kurtarma kopyası `D:\YakitRotam-signing-backup`
klasöründedir. Anahtar kaybolursa daha önce yüklenen APK'nın üstüne güncelleme
kurulamaz; bu klasör güvenli bir harici konuma yedeklenmelidir.

## Reklamlar

Uygulama AdMob kullanır: planlama ve özet ekranlarının altında ince bir banner,
rota hesaplanırken de tam ekran geçiş reklamı gösterilir. Geçiş reklamları
arasında en az 3 dakika bırakılır. AB ve Birleşik Krallık kullanıcılarına reklam
yüklenmeden önce Google'ın onay formu (UMP) gösterilir.

Debug build her zaman Google'ın test reklam kimliklerini kullanır. Release
build'de gerçek reklam çıkması için repo ayarlarında şu Actions secret'ları
tanımlanmalıdır; tanımlı değilse release de test reklamlarıyla çıkar:

| Secret | Örnek biçim |
| --- | --- |
| `ADMOB_APP_ID` | `ca-app-pub-XXXXXXXXXXXXXXXX~YYYYYYYYYY` |
| `ADMOB_BANNER_ID` | `ca-app-pub-XXXXXXXXXXXXXXXX/ZZZZZZZZZZ` |
| `ADMOB_INTERSTITIAL_ID` | `ca-app-pub-XXXXXXXXXXXXXXXX/ZZZZZZZZZZ` |

Play Store'a yüklenecek paket (AAB) her build'de Actions artifact'ı olarak
üretilir; GitHub Release'e yalnızca APK eklenir.

## Play Store

Mağaza görselleri, mağaza metinleri ve adım adım yayın rehberi
[`store/PLAY_STORE.md`](store/PLAY_STORE.md) içinde. Gizlilik politikası:
https://ziewenn.github.io/yakitrotam/privacy/

## Teknik

Kotlin 2.3, Jetpack Compose ve Material 3, hedef SDK 36, tek Activity. Katmanlar ViewModel,
repository ve saf Kotlin hesaplama motoru olarak ayrılmış; durum StateFlow ile
taşınıyor. Ağ için OkHttp, serileştirme için kotlinx.serialization kullanılıyor.

```
app/src/main/java/com/yakitrotam/app/
├── MainActivity.kt
├── data/
│   ├── model/
│   │   ├── FuelBrand.kt            Markalar, renkler, OSM etiket eşlemesi
│   │   ├── FuelPriceSnapshot.kt    Pompa fiyatı anlık görüntüsü
│   │   ├── GasStation.kt           İstasyon ve OSM üzerinden yakıt bilgisi
│   │   ├── TripModels.kt           Duraklar ve seyahat özeti
│   │   └── VehicleProfile.kt       Depo, tüketim, menzil formülleri
│   └── repository/
│       ├── FuelPriceRepository.kt  Opet fiyat servisi, il eşlemesi, önbellek
│       ├── GasStationRepository.kt İstasyon önbelleği ve yakıt/marka filtresi
│       ├── LocationService.kt      Photon arama, Nominatim, cihaz konumu
│       ├── OverpassStationSource.kt Koridor sorgusu ve OSM etiket ayrıştırma
│       ├── RouteRepository.kt      OSRM ve çevrimdışı koridor yedeği
│       └── TripPreferences.kt      Son girilen ayarların saklanması
├── domain/
│   └── FuelOptimizerEngine.kt      Menzil simülasyonu ve durak seçimi
├── ui/
│   ├── components/                 Kadran, fiyat şeridi, zaman çizelgesi, harita
│   ├── screens/                    Planlama ve özet ekranları
│   ├── theme/
│   └── viewmodel/
└── util/
    ├── GeoUtils.kt                 Haversine, rota izdüşümü, interpolasyon
    └── GoogleMapsLauncher.kt       Maps intent'i
```

## Bilinen sınırlar

- LPG fiyatı tahminidir. Ücretsiz ve makine okunabilir bir otogaz fiyat kaynağı
  bulunamadı.
- İstasyon bilgisi OSM'nin kalitesine bağlıdır. Markası veya yakıt türü
  etiketlenmemiş istasyonlar "Diğer" olarak görünür ve puanlamada geri düşer.
- Ağ yoksa rota dahili otoyol koridoruna düşer, istasyon listesi ise boş kalır;
  bu durumda plan üretilmez.
- Süre tahmini sabit ortalama hıza dayanır, trafiği hesaba katmaz.
