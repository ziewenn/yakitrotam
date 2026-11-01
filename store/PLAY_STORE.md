# Play Store yayın rehberi

Bu klasördeki dosyalar ve Play Console'da doldurulacak alanlar.

| Dosya | Play Console'daki yeri |
| --- | --- |
| `icon-512.png` | Ana mağaza girişi > Uygulama simgesi |
| `feature-graphic-1024x500.png` | Ana mağaza girişi > Öne çıkan grafik |
| `screenshot-1.png` ... `screenshot-8.png` | Ana mağaza girişi > Telefon ekran görüntüleri |

Görsellerde reklam yok; ekran görüntüleri `./gradlew installDebug -PnoAds` ile
alınmış reklamsız build'den üretildi. Arayüz değişince ham görüntüleri yeniden
alıp `python store/make_store_assets.py <ham_klasör>` ile hepsi yeniden üretilir.

Önemli adresler:

- Gizlilik politikası: https://ziewenn.github.io/yakitrotam/privacy/
- Geliştirici web sitesi (app-ads.txt için): https://ziewenn.github.io
- app-ads.txt: https://ziewenn.github.io/app-ads.txt

---

## Adım 1. AdMob hazırlığı

1. https://admob.google.com adresine gir, **Uygulamalar > Uygulama ekle**.
2. "Uygulama bir mağazada yayınlandı mı?" sorusuna şimdilik **Hayır** de, platform
   Android, ad **YakıtRotam**.
3. Uygulama oluşunca **Reklam birimleri > Reklam birimi ekle**:
   - **Banner** türünde bir birim (ad: `alt-banner`).
   - **Geçiş reklamı (Interstitial)** türünde bir birim (ad: `rota-hesaplama`).
   - **Native advanced** türünde bir birim (ad: `ozet-native`).
4. Şu üç değeri not et:
   - Uygulama kimliği: `ca-app-pub-...~...` (tilda işaretli olan)
   - Banner birim kimliği: `ca-app-pub-.../...`
   - Geçiş reklamı birim kimliği: `ca-app-pub-.../...`
5. **Ayarlar > Hesap bilgileri** altındaki **Yayıncı kimliğini** (`pub-...`) not et.
   Bu, `app-ads.txt` için gerekiyor.
6. **Gizlilik ve mesajlaşma > GDPR** bölümünden bir onay mesajı oluşturup yayınla.
   Uygulamadaki onay formu buradan beslenir; mesaj yayınlanmazsa AB kullanıcılarına
   form çıkmaz.

## Adım 2. Kimlikleri GitHub'a gir

GitHub'da `ziewenn/yakitrotam` > **Settings > Secrets and variables > Actions >
New repository secret**:

| Ad | Değer |
| --- | --- |
| `ADMOB_APP_ID` | Uygulama kimliği (`~` işaretli) |
| `ADMOB_BANNER_ID` | Banner birim kimliği |
| `ADMOB_INTERSTITIAL_ID` | Geçiş reklamı birim kimliği |
| `ADMOB_NATIVE_ID` | Native advanced birim kimliği |

`app-ads.txt` için yayıncı kimliğini `ziewenn/ziewenn.github.io` reposundaki
dosyada `#` işaretini kaldırıp `pub-XXXXXXXXXXXXXXXX` yerine yazman yeterli.

Sonra `main`'e yapılan ilk push gerçek reklamlı APK ve AAB üretir.

## Adım 3. AAB dosyasını indir

GitHub > **Actions** > en son başarılı "Android APK" çalışması > sayfanın altındaki
**Artifacts** > `YakitRotam-v1.0.X` zip'ini indir. İçindeki `.aab` dosyası Play'e
yüklenecek paket.

## Adım 4. Play Console'da uygulamayı oluştur

**Tüm uygulamalar > Uygulama oluştur**:

- Uygulama adı (en fazla 30): `YakıtRotam: Yakıt & Şarj Planı`
- Kısa açıklama (75/80):

```
Yakıt ve şarj durağını, köprü-otoyol ücretini ve yol masrafını önceden gör.
```

- Tam açıklama (2308/4000):

```
YakıtRotam, benzinli, dizel, LPG'li ve elektrikli araçlar için yola çıkmadan önce yakıt ve şarj molalarını planlar. Nerede duracağını, ne kadar alacağını ve yolculuğun sana kaça mal olacağını önceden görürsün.

ELEKTRİKLİ ARAÇLAR İÇİN
- Togg dahil 20 markadan 68 hazır model; istersen batarya, tüketim ve şarj gücünü kendin gir
- Türkiye'deki halka açık DC hızlı şarj istasyonları, EPDK kayıtlarından her gün güncellenir
- Her durakta varış ve çıkış şarj yüzdesi, şarj süresi, istasyon gücü, eklenecek kWh ve tutar
- Aracının şarj eğrisi hesaba katılır; şarjın yavaşladığı yüzdelerde seni gereksiz bekletmez
- Üyeliğin olan şarj ağlarını seç; plan önce onların istasyonlarında durur
- Aracının soketine (CCS2 ya da CHAdeMO) uymayan istasyon önerilmez

YAKITLI ARAÇLAR İÇİN
- Depo göstergesini aracındaki gibi ayarla; rezerve düşmeden nerede durman gerektiğini gör
- Her durakta alınacak litre, tahmini tutar ve varışta depoda kalan yakıt
- Benzin, motorin ve LPG fiyatları il bazında güncel; her durak kendi ilinin fiyatıyla hesaplanır
- Tercih ettiğin akaryakıt markaları öne alınır; menzilde yoksa yolda kalmaman için başkası önerilir
- Otoyoldan çıkıp dolaştıran istasyonlar elenir; her durağın yola eklediği gerçek mesafe ve süre gösterilir

YOLCULUĞUN GERÇEK MALİYETİ
- Toplam mesafe, süre ve duraklarda ödeyeceğin tutar
- Köprü, tünel ve otoyol geçiş ücreti tahmini (KGM tarifeleri, otomobil)
- 100 km başına maliyet

HARİTA VE KAYITLI ROTALAR
- Yakınındaki akaryakıt ve hızlı şarj istasyonları, 150 kW üstü süzgeciyle
- Sık uğradığın istasyonları favorilere ekle
- Sık gittiğin rotaları kaydet, tek dokunuşla planla
- Plan hazır olunca tüm duraklarıyla Google Haritalar'da aç

YAKIT VE ŞARJ DEFTERİ
- Dolum ve şarjlarını kaydet; aracının gerçek tüketimini ve 100 km maliyetini öğren
- Ölçülen tüketimi tek dokunuşla planlarda kullan

FİYAT TAKİBİ
- Ana ekran widget'ı: ilinin benzin, motorin ve otogaz fiyatı bir bakışta
- İstersen yakıt fiyatı değişince bildirim al

Hesap açman gerekmez; araç bilgilerin, rotaların ve defterin yalnızca telefonunda tutulur. Değerler tahmindir; sürüş tarzı, hava ve trafik sonucu değiştirebilir.

Harita ve akaryakıt istasyonu verileri © OpenStreetMap katkıcıları. Şarj istasyonları: EPDK. Akaryakıt fiyatları: Opet ve Petrol Ofisi. Araç verileri: Open EV Data (MIT).
```

- Sürüm notları (tr-TR, 406/500):

```
<tr-TR>
Yenilikler:
- Elektrikli araç desteği: 68 hazır model, EPDK'daki DC hızlı şarj istasyonları, şarj süresi ve tutarıyla durak planı
- Şarj ağı tercihi: üyeliğin olan ağ öne alınır
- Köprü ve otoyol ücreti tahmini
- Harita: yakındaki istasyonlar ve favoriler
- Kayıtlı rotalar
- Yakıt ve şarj defteri: gerçek tüketimini öğren
- Ana ekran fiyat widget'ı
- Açık renkli yeni tasarım ve daha küçük uygulama boyutu
</tr-TR>
```

- Uygulama simgesi: `icon-512.png`
- Öne çıkan grafik: `feature-graphic-1024x500.png`
- Telefon ekran görüntüleri: `screenshot-1.png` ... `screenshot-8.png`

## Adım 7. Kapalı test (yeni kişisel hesaplar için zorunlu)

Kasım 2023'ten sonra açılan kişisel geliştirici hesaplarında üretime çıkmadan önce
**en az 12 test kullanıcısıyla, kesintisiz 14 gün süren kapalı test** şartı vardır.
Hesabın kurumsal (şirket) hesapsa bu adım zorunlu değildir, Adım 8'e geç.

1. **Test ve yayınla > Test > Kapalı test > Kanal oluştur**.
2. **Testçiler** sekmesinde bir e-posta listesi oluştur, en az 12 Google hesabı ekle.
3. **Yeni sürüm oluştur**:
   - "Play Uygulama İmzalama" sorulursa **Google'ın oluşturduğu anahtarı kullan**
     (varsayılan). Yüklediğin AAB bizim yükleme anahtarımızla imzalı; Google bunu
     yükleme anahtarı olarak kaydeder.
   - Adım 3'te indirdiğin `.aab` dosyasını yükle.
   - Sürüm notu: `İlk sürüm.`
4. **Sürümü incele > Kapalı teste sun**. Google incelemesi birkaç saat ile birkaç
   gün sürebilir.
5. Onaylanınca testçilere **Testçiler** sekmesindeki katılım bağlantısını gönder.
   Testçilerin bağlantıdan katılıp uygulamayı yüklemesi ve 14 gün boyunca katılımcı
   kalması gerekir.
6. 14 gün dolunca **Kontrol paneli > Üretim erişimi için başvur** formunu doldur.

## Adım 8. Üretime yayınla

1. **Test ve yayınla > Üretim > Ülkeler/bölgeler**: Türkiye (ve istediğin diğer ülkeler).
2. **Yeni sürüm oluştur**, en güncel `.aab` dosyasını yükle (her yeni yüklemenin
   sürüm kodu öncekinden büyük olmalı; CI bunu otomatik artırır).
3. **Sürümü incele > Üretime sun**.

## Adım 9. Yayından sonra

- AdMob'da uygulamayı Play Store kaydına bağla (**Uygulama ayarları > Mağazaya bağla**).
- app-ads.txt doğrulaması AdMob'da 24 saate kadar sürebilir.
- Güncellemelerde: kodu `main`'e push et, Actions'tan yeni `.aab`'yi indir, Play
  Console'da ilgili kanalda **Yeni sürüm oluştur** ile yükle.

Not: Telefonuna Obtainium/GitHub üzerinden kurduğun APK, bizim anahtarımızla
imzalı. Play'den kurulan sürüm Google'ın imza anahtarıyla imzalanır, bu yüzden
ikisi birbirinin üstüne güncellenemez. Play sürümüne geçerken GitHub sürümünü
kaldırıp Play'den yüklemen gerekir.
