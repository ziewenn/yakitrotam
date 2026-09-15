# ⛽ YakıtRotam - Akıllı Akaryakıt & Rota Planlayıcı (Android)

> **Elektrikli araçlar için ABRP (A Better Routeplanner) neyse, içten yanmalı araçlar (Benzin, Motorin, LPG) için YakıtRotam odur!**

YakıtRotam; Türkiye'deki araç sürücülerinin depo kapasitesi, ortalama yakıt tüketimi (L/100km), anlık depo seviyesi ve marka tercihlerini (Shell, Opet, Petrol Ofisi, BP, TotalEnergies vb.) hesaba katarak en uygun yakıt duraklarını otomatik hesaplayan ve tek tıkla **Google Maps** uygulamasına aktaran modern bir Android uygulamasıdır.

---

## 🌟 Öne Çıkan Özellikler

1. **Akıllı Tüketim & Depo Simülasyonu:**
   * 100 km'de kaç litre yaktığınızı, deponuzun kaç litre olduğunu ve anlık doluluk oranını girin.
   * Algoritma, yakıtınız rezerve (%15) düşmeden önce otoyol ve ana arterler üzerindeki en uygun noktayı hesaplar.
2. **Türk Akaryakıt Markaları Filtreleme:**
   * **Shell, Opet, Petrol Ofisi, BP, TotalEnergies, Aytemiz, TP (Türkiye Petrolleri)** ve diğerleri.
   * Şirket aracı / Taşıt Tanıma (Shell Taşıt Tanıma, Opet Otobil vb.) veya sadakat kartınıza göre sadece istediğiniz markalarda durak planlar.
3. **Yakıt Türü Desteği:**
   * **Benzin (95 Oktan)**
   * **Motorin (Dizel)**
   * **LPG (Otogaz):** Özellikle Türkiye'de küçük depolu LPG araçlarının sık dolum ihtiyacına ve sadece LPG bulunan istasyonlara özel filtreleme.
4. **Google Maps Entegrasyonu (Tek Tıkla Navigasyon):**
   * Hesaplanan optimum duraklar, Android Universal Intent ile resmi Google Maps uygulamasına aktarılır.
   * Tüm istasyonlar ara durak (waypoint) olarak hazır gelir; tek tıkla sesli navigasyon başlar.
5. **Uygulama İçi İnteraktif Rota & Durak Zaman Çizelgesi:**
   * Her durak için: Varış anındaki kalan yakıt yüzdesi (%), depoyu fullemek için gereken litre, tahmini maliyet (₺) ve ana yoldan sapma mesafesi (km).
   * İnteraktif rota harita önizlemesi.
6. **Canlı Veri (anahtarsız, ücretsiz):**
   * İstasyonlar her rota için OpenStreetMap Overpass API'sinden çekilir; gömülü/statik istasyon listesi yoktur, bu yüzden gerçekte var olmayan bir konuma durak konmaz.
   * Pompa fiyatları Opet'in herkese açık il bazlı fiyat servisinden alınır. Otogaz (LPG) yayınlanmadığı için benzine oranlanarak tahmin edilir ve arayüzde "tahmini" olarak işaretlenir.
   * Rota OSRM'den gelir; ağ yoksa dahili otoyol koridoru devreye girer.
   * Google Maps/Places API kullanılmaz (ücretli). Konum araması Photon, ters kodlama Nominatim üzerindendir.

---

## 🛠️ Teknoloji Yığını

* **Dil:** Kotlin 1.9.23 / Modern Android
* **Arayüz (UI):** Jetpack Compose + Material 3 (Otomotiv odaklı koyu tema)
* **Mimari:** Clean Architecture + MVVM (ViewModel, StateFlow, Repository, Domain Engine)
* **Konum Arama:** Photon (OpenStreetMap tabanlı autocomplete) + Nominatim ters kodlama — API anahtarı gerekmez
* **Ağ:** OkHttp + Kotlinx Serialization
* **Veri:** OpenStreetMap Overpass (istasyonlar, ODbL) + Opet fiyat servisi (pompa fiyatları) + OSRM (rota)

---

## 📂 Proje Yapısı

```
appidea/
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/yakitrotam/app/
│   │   │   │   ├── MainActivity.kt             # Ana Activity ve ekran yönlendirici
│   │   │   │   ├── data/
│   │   │   │   │   ├── model/
│   │   │   │   │   │   ├── FuelBrand.kt        # Markalar, renkler, kimlikler
│   │   │   │   │   │   ├── FuelType.kt         # Benzin, Dizel, LPG ve yedek fiyatlar
│   │   │   │   │   │   ├── FuelPriceSnapshot.kt # Canlı pompa fiyatı anlık görüntüsü
│   │   │   │   │   │   ├── VehicleProfile.kt   # Depo, tüketim ve menzil formülleri
│   │   │   │   │   │   ├── GasStation.kt       # İstasyon özellikleri ve koordinatlar
│   │   │   │   │   │   └── TripModels.kt       # Duraklar, hesaplanan seyahat özeti
│   │   │   │   │   └── repository/
│   │   │   │   │       ├── GasStationRepository.kt # Marka & koridor arama
│   │   │   │   │       └── RouteRepository.kt      # OSRM + yedek rota koridorları
│   │   │   │   ├── domain/
│   │   │   │   │   └── FuelOptimizerEngine.kt  # Depo simülasyonu & durak optimizasyon algoritması
│   │   │   │   ├── ui/
│   │   │   │   │   ├── theme/                  # Renkler, tipografi ve tema
│   │   │   │   │   ├── components/             # FuelGaugeCard, BrandFilterBar, FuelStopTimelineCard, RouteMiniMap
│   │   │   │   │   ├── screens/                # TripPlannerScreen, RouteSummaryScreen
│   │   │   │   │   └── viewmodel/              # TripViewModel (StateFlow)
│   │   │   │   └── util/
│   │   │   │       ├── GeoUtils.kt             # Haversine, cross-track, interpolasyon
│   │   │   │       └── GoogleMapsLauncher.kt   # Google Maps intent başlatıcı
│   │   │   └── res/                            # String ve tema kaynakları
│   │   └── test/java/com/yakitrotam/app/       # Birim testleri (GeoUtils, FuelOptimizer, MapsIntent)
│   ├── build.gradle.kts
│   └── proguard-rules.pro
├── gradle/
│   ├── libs.versions.toml                      # Version catalog
│   └── wrapper/gradle-wrapper.properties
├── build.gradle.kts
└── settings.gradle.kts
```

---

## 🚀 Projeyi Çalıştırma (Android Studio)

1. **Android Studio**'yu açın.
2. `Open` seçeneği ile `d:\appidea` klasörünü seçin.
3. Android Studio Gradle senkronizasyonunu otomatik tamamlayacaktır.
4. Bir Android emülatör veya USB ile bağlı gerçek Android cihaz seçip **Run ('app')** butonuna basın.

> Hiçbir API anahtarı gerekmez. İstasyon, fiyat, rota ve adres araması için kullanılan servislerin tamamı ücretsiz ve anahtarsızdır; uygulamanın internet erişimi olması yeterlidir.

---

## 📦 APK ve otomatik yayın

`main` dalına gönderilen her değişiklikte `.github/workflows/android-release.yml` otomatik olarak:

1. Birim testlerini çalıştırır.
2. Sürüm numarasını pipeline numarasıyla artırır.
3. Aynı release anahtarıyla imzalı APK üretir.
4. APK'yı **Actions Artifacts** ve **GitHub Releases** alanına yükler.

En güncel APK şu sayfadan indirilebilir:

```text
https://github.com/ziewenn/yakitrotam/releases/latest
```

Telefonda GitHub sürümlerini takip ederek güncelleme bildirimi almak için [Obtainium](https://github.com/ImranR98/Obtainium) kullanılabilir. Kaynak adresi olarak bu reponun URL'sini eklemek yeterlidir. Android güvenlik modeli nedeniyle mağaza dışı normal uygulamalar sessiz kurulum yapamaz; güncellemede Android'in kurulum onayı gösterilir.

CI imza anahtarının yerel kurtarma kopyası `D:\\YakitRotam-signing-backup` klasöründedir. Bu klasör gizli tutulmalı ve güvenli bir harici konuma yedeklenmelidir. Anahtar kaybolursa daha önce yüklenen APK'nın üstüne güncelleme kurulamaz.

---

## 🧪 Birim Testleri Çalıştırma

Terminalden veya Android Studio içinden:
```bash
./gradlew test
```
* `GeoUtilsTest`: Mesafe ve rota kesişim formüllerini doğrular.
* `FuelOptimizerEngineTest`: Depo tüketim modelini, marka filtrelemesini ve durak üretimini test eder.
* `GoogleMapsIntentTest`: Üretilen Google Maps URL formatının durak parametrelerini doğru taşıdığını doğrular.
