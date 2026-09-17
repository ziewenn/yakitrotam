# Play Store yayın rehberi

Bu klasördeki dosyalar ve Play Console'da doldurulacak alanlar.

| Dosya | Play Console'daki yeri |
| --- | --- |
| `icon-512.png` | Ana mağaza girişi > Uygulama simgesi |
| `feature-graphic-1024x500.png` | Ana mağaza girişi > Öne çıkan grafik |
| `screenshot-1.png` ... `screenshot-4.png` | Ana mağaza girişi > Telefon ekran görüntüleri |

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

- Uygulama adı: `YakıtRotam: Yakıt Planlayıcı`
- Varsayılan dil: Türkçe
- Uygulama mı oyun mu: Uygulama
- Ücretsiz mi ücretli mi: Ücretsiz
- Beyanları işaretle.

## Adım 5. "Uygulamanızı ayarlayın" görevleri

Kontrol panelindeki listeyi sırayla doldur.

### Gizlilik politikası
`https://ziewenn.github.io/yakitrotam/privacy/`

### Uygulama erişimi
"Tüm işlevler özel erişim gerektirmeden kullanılabilir".

### Reklamlar
"Evet, uygulamamda reklam var".

### İçerik derecelendirmesi
E-posta gir, kategori **Yardımcı Program, Üretkenlik, İletişim veya Diğer**. Tüm
sorulara (şiddet, cinsellik, küfür, madde, hakaret, kullanıcı etkileşimi, konum
paylaşımı başka kullanıcılarla, dijital satın alma, hazır oyun) **Hayır**. Sonuç
herkese uygun (PEGI 3 / 3+) çıkmalı.

### Hedef kitle
Yaş grubu olarak yalnızca **18 ve üzeri** seç (araç kullanan kitle). "Uygulama
çocuklara hitap ediyor mu" sorusuna **Hayır**.

### Haber uygulaması / COVID / Devlet uygulaması / Finansal özellikler / Sağlık
Hepsine **Hayır** veya "bu özelliklerin hiçbiri yok".

### Veri güvenliği (Data safety)

Genel sorular:

- Uygulama gerekli kullanıcı veri türlerini topluyor veya paylaşıyor mu? **Evet**
- Toplanan tüm veriler aktarım sırasında şifreleniyor mu? **Evet**
- Kullanıcıların verilerinin silinmesini isteyebileceği bir yol sağlıyor musunuz?
  **Hayır** (hesap yok; cihazdaki veriler uygulama kaldırılınca silinir)

Veri türleri:

| Veri türü | Toplanıyor | Paylaşılıyor | Geçici işleniyor | Zorunlu mu | Amaç |
| --- | --- | --- | --- | --- | --- |
| Konum > Yaklaşık konum | Evet | Evet | Hayır | Zorunlu | Uygulama işlevleri, Reklam veya pazarlama, Analiz |
| Konum > Kesin konum | Evet | Hayır | Evet | İsteğe bağlı | Uygulama işlevleri |
| Uygulama etkinliği > Uygulama etkileşimleri | Evet | Evet | Hayır | Zorunlu | Reklam veya pazarlama, Analiz |
| Uygulama bilgileri ve performansı > Kilitlenme günlükleri | Evet | Evet | Hayır | Zorunlu | Analiz |
| Uygulama bilgileri ve performansı > Teşhis | Evet | Evet | Hayır | Zorunlu | Analiz |
| Cihaz veya diğer kimlikler | Evet | Evet | Hayır | Zorunlu | Reklam veya pazarlama, Analiz, Sahtekarlığı önleme |

Açıklama: "Yaklaşık konum", "Uygulama etkinliği", "Uygulama bilgileri" ve "Cihaz
kimlikleri" satırları Google AdMob SDK'sının topladıklarıdır. "Kesin konum" yalnızca
kullanıcı "Konumum"a basınca kalkış noktası için kullanılır ve saklanmaz.

Google'ın güncel AdMob beyan listesiyle son kez karşılaştır:
https://developers.google.com/admob/android/privacy/play-data-disclosure

### Uygulama kategorisi ve iletişim bilgileri
- Kategori: **Harita ve Navigasyon**
- E-posta: Play'in göstereceği destek e-postası (zorunlu)
- Web sitesi: `https://ziewenn.github.io` (app-ads.txt doğrulaması bu alana bakar)

## Adım 6. Ana mağaza girişi

- Uygulama adı: `YakıtRotam: Yakıt Planlayıcı`
- Kısa açıklama (77/80):

```
Rotanda nerede ve ne kadar yakıt alacağını güncel pompa fiyatlarıyla hesapla.
```

- Tam açıklama:

```
YakıtRotam, benzinli, dizel ve LPG'li araçlar için yol boyunca yakıt molası planlar.

Kalkış ve varış noktanı seç, deponun hacmini, ortalama tüketimini ve şu anki doluluğunu gir. Uygulama rotanı çizer, güzergah üzerindeki akaryakıt istasyonlarını bulur ve rezerve düşmeden nerede durman gerektiğini hesaplar.

Her durak için:
- İstasyona vardığında depoda kalan yakıt
- Alman gereken litre ve tahmini tutar
- Yola eklediği gerçek mesafe ve süre; otoyoldan çıkartıp dolaştıran istasyonlar elenir

Yolculuk özeti:
- Toplam mesafe ve tahmini süre
- Pompada ödeyeceğin toplam tutar
- Yolculuk boyunca yakılan yakıt ve 100 km başına maliyet

Neden YakıtRotam?
- İstasyonlar OpenStreetMap'teki gerçek kayıtlardan her rota için yeniden alınır.
- Benzin ve motorin fiyatları il bazında günceldir. LPG fiyatı tahminidir ve öyle işaretlenir.
- Bir istasyona ancak mevcut yakıtınla gerçekten ulaşabiliyorsan durak olarak önerilir.
- Son durakta depoyu gereksiz yere doldurtmaz; varışa yetecek kadar yakıt alman önerilir.
- Shell, Opet, Petrol Ofisi, BP, TotalEnergies, Aytemiz, TP, Alpet ve Lukoil arasından tercih ettiğin markalar öne alınır.
- Araç bilgilerin ve son aradığın yerler hatırlanır, her seferinde yeniden girmezsin.
- Plan hazır olunca tek dokunuşla tüm duraklarıyla Google Haritalar'da açılır.

Hesap açman gerekmez. Değerler tahmindir; sürüş tarzı ve trafik sonucu değiştirebilir.

Harita ve istasyon verileri © OpenStreetMap katkıcıları.
```

- Uygulama simgesi: `icon-512.png`
- Öne çıkan grafik: `feature-graphic-1024x500.png`
- Telefon ekran görüntüleri: `screenshot-1.png` ... `screenshot-4.png`

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
