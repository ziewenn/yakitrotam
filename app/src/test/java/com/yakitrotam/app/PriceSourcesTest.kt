package com.yakitrotam.app

import com.yakitrotam.app.data.model.FuelType
import com.yakitrotam.app.data.model.LatLng
import com.yakitrotam.app.data.repository.Endpoints
import com.yakitrotam.app.data.repository.PriceParsers
import com.yakitrotam.app.data.repository.PriceTable
import com.yakitrotam.app.notify.priceChangeNotice
import com.yakitrotam.app.util.Provinces
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PriceSourcesTest {

    private val opetJson = """
        [{"provinceCode":34,"provinceName":"İSTANBUL ANADOLU","districtName":"ANADOLU_Y_MERKEZ","prices":[
            {"productName":"Kurşunsuz Benzin 95","amount":80.16,"productCode":"A100"},
            {"productName":"Gazyağı","amount":96.75,"productCode":"A110"},
            {"productName":"Motorin UltraForce","amount":100.19,"productCode":"A121"},
            {"productName":"Motorin EcoForce","amount":100.10,"productCode":"A128"}]},
         {"provinceCode":16,"provinceName":"BURSA","districtName":"MERKEZ","prices":[
            {"productName":"Kurşunsuz Benzin 95","amount":81.02,"productCode":"A100"},
            {"productName":"Motorin UltraForce","amount":101.05,"productCode":"A121"}]},
         {"provinceCode":99,"provinceName":"ATLANTİS","districtName":"MERKEZ","prices":[]}]
    """.trimIndent()

    /** Petrol Ofisi sayfasındaki tablonun yapısı (sütun sırası ve KDV'li/KDV'siz çift değerler). */
    private val petrolOfisiHtml = """
        <table><thead><tr><th>Şehir</th><th>V/Max Kurşunsuz 95</th><th>V/Max Diesel</th>
        <th>Gazyağı</th><th>Kalorifer Yakıtı</th><th>Fuel Oil</th><th>PO/gaz Otogaz</th></tr></thead><tbody>
        <tr class="price-row district-03400" data-disctrict-name="ISTANBUL (ANADOLU)"> <td>ISTANBUL (ANADOLU)</td>
          <td> <span class="with-tax">80.20</span> <span class="without-tax">66.84</span> TL/LT </td>
          <td> <span class="with-tax">100.20</span> <span class="without-tax">83.50</span> TL/LT </td>
          <td> <span class="with-tax">98.65</span> <span class="without-tax">82.21</span> TL/LT </td>
          <td> <span class="with-tax">69.05</span> <span class="without-tax">57.55</span> TL/KG </td>
          <td> <span class="with-tax">49.83</span> <span class="without-tax">41.53</span> TL/KG </td>
          <td> <span class="with-tax">34.39</span> <span class="without-tax">28.66</span> TL/LT </td> </tr>
        <tr class="price-row"> <td>AFYON</td>
          <td><span class="with-tax">82.40</span></td><td><span class="with-tax">102.70</span></td>
          <td><span class="with-tax">99.00</span></td><td><span class="with-tax">70.00</span></td>
          <td><span class="with-tax">50.00</span></td><td><span class="with-tax">35,10</span></td> </tr>
        </tbody></table>
    """.trimIndent()

    @Test
    fun `opet yaniti il anahtarina gore okunur ve bilinmeyen il atlanir`() {
        val prices = PriceParsers.parseOpet(opetJson)

        assertEquals(setOf("istanbulanadolu", "bursa"), prices.keys)
        assertEquals(80.16, prices.getValue("istanbulanadolu").benzin, 1e-9)
        assertEquals("İlk motorin ürünü esas alınır", 100.19, prices.getValue("istanbulanadolu").dizel, 1e-9)
        assertNull("Opet otogaz yayınlamıyor", prices.getValue("bursa").lpg)
    }

    @Test
    fun `petrol ofisi tablosundan otogaz sutunu okunur`() {
        val prices = PriceParsers.parsePetrolOfisi(petrolOfisiHtml)

        assertEquals(34.39, prices.getValue("istanbulanadolu").lpg!!, 1e-9)
        assertEquals("AFYON kısaltması Afyonkarahisar'a eşlenmeli", 35.10, prices.getValue("afyonkarahisar").lpg!!, 1e-9)
    }

    @Test
    fun `otogaz sutunu kayarsa yanlis deger otogaz sanilmaz`() {
        // Son sütun benzinle aynı seviyede bir değer taşıyor: otogaz olamaz.
        val shifted = petrolOfisiHtml.replace("34.39", "79.90")

        assertNull(PriceParsers.parsePetrolOfisi(shifted).getValue("istanbulanadolu").lpg)
    }

    @Test
    fun `birlesik tabloda benzin opetten otogaz petrol ofisinden gelir`() {
        val merged = PriceParsers.merge(PriceParsers.parseOpet(opetJson), PriceParsers.parsePetrolOfisi(petrolOfisiHtml))
        val table = PriceTable(merged, fetchedAtEpochMillis = 0L)
        val kadikoy = LatLng(40.99, 29.03)

        assertEquals(80.16, table.pricePerLiter(kadikoy, FuelType.BENZIN)!!, 1e-9)
        assertEquals(34.39, table.pricePerLiter(kadikoy, FuelType.LPG)!!, 1e-9)

        val snapshot = table.snapshotFor(Provinces.nearest(kadikoy))!!
        assertFalse("Otogaz gerçek kaynaktan geldi, tahmini işaretlenmemeli", snapshot.isEstimated(FuelType.LPG))

        // Bursa'da otogaz kaynağı yok: benzine oranlı tahmin ve "tahmini" işareti.
        val bursa = table.snapshotFor(Provinces.byName("Bursa")!!)!!
        assertTrue(bursa.isEstimated(FuelType.LPG))
        assertEquals(81.02, bursa.priceFor(FuelType.BENZIN), 1e-9)
    }

    @Test
    fun `iki kaynagin il adlari tablodaki 82 bolgeyle eslesir`() {
        val opetNames = listOf(
            "ADANA", "ADIYAMAN", "AFYONKARAHİSAR", "AĞRI", "AKSARAY", "AMASYA", "ANKARA", "ANTALYA", "ARDAHAN",
            "ARTVİN", "AYDIN", "BALIKESİR", "BARTIN", "BATMAN", "BAYBURT", "BİLECİK", "BİNGÖL", "BİTLİS", "BOLU",
            "BURDUR", "BURSA", "ÇANAKKALE", "ÇANKIRI", "ÇORUM", "DENİZLİ", "DİYARBAKIR", "DÜZCE", "EDİRNE",
            "ELAZIĞ", "ERZİNCAN", "ERZURUM", "ESKİŞEHİR", "GAZİANTEP", "GİRESUN", "GÜMÜŞHANE", "HAKKARİ", "HATAY",
            "IĞDIR", "ISPARTA", "İSTANBUL ANADOLU", "İSTANBUL AVRUPA", "İZMİR", "KAHRAMANMARAŞ", "KARABÜK",
            "KARAMAN", "KARS", "KASTAMONU", "KAYSERİ", "KIRIKKALE", "KIRKLARELİ", "KIRŞEHİR", "KİLİS", "KOCAELİ",
            "KONYA", "KÜTAHYA", "MALATYA", "MANİSA", "MARDİN", "MERSİN", "MUĞLA", "MUŞ", "NEVŞEHİR", "NİĞDE",
            "ORDU", "OSMANİYE", "RİZE", "SAKARYA", "SAMSUN", "SİİRT", "SİNOP", "SİVAS", "ŞANLIURFA", "ŞIRNAK",
            "TEKİRDAĞ", "TOKAT", "TRABZON", "TUNCELİ", "UŞAK", "VAN", "YALOVA", "YOZGAT", "ZONGULDAK"
        )
        val petrolOfisiNames = listOf(
            "ISTANBUL (AVRUPA)", "ISTANBUL (ANADOLU)", "ANKARA", "IZMIR", "ADANA", "ADIYAMAN", "AFYON", "AGRI",
            "AKSARAY", "AMASYA", "ANTALYA", "ARDAHAN", "ARTVIN", "AYDIN", "BALIKESIR", "BARTIN", "BATMAN",
            "BAYBURT", "BILECIK", "BINGOL", "BITLIS", "BOLU", "BURDUR", "BURSA", "CANAKKALE", "CANKIRI", "CORUM",
            "DENIZLI", "DIYARBAKIR", "DUZCE", "EDIRNE", "ELAZIG", "ERZINCAN", "ERZURUM", "ESKISEHIR", "GAZIANTEP",
            "GIRESUN", "GUMUSHANE", "HAKKARI", "HATAY", "IGDIR", "ISPARTA", "KAHRAMANMARAS", "KARABUK", "KARAMAN",
            "KARS", "KASTAMONU", "KAYSERI", "KILIS", "KIRIKKALE", "KIRKLARELI", "KIRSEHIR", "KOCAELI", "KONYA",
            "KUTAHYA", "MALATYA", "MANISA", "MARDIN", "MERSIN", "MUGLA", "MUS", "NEVSEHIR", "NIGDE", "ORDU",
            "OSMANIYE", "RIZE", "SAKARYA", "SAMSUN", "SANLIURFA", "SIIRT", "SINOP", "SIRNAK", "SIVAS", "TEKIRDAG",
            "TOKAT", "TRABZON", "TUNCELI", "USAK", "VAN", "YALOVA", "YOZGAT", "ZONGULDAK"
        )

        assertEquals(82, Provinces.all.size)
        for (names in listOf(opetNames, petrolOfisiNames)) {
            val matched = names.map { name -> assertNotNull("Eşleşmeyen il: $name", Provinces.byName(name)); Provinces.byName(name)!! }
            assertEquals("Her ad farklı bir bölgeye eşlenmeli", 82, matched.toSet().size)
        }
    }

    @Test
    fun `nokta en yakin ile ve istanbulda dogru yakaya duser`() {
        assertEquals("İstanbul Avrupa", Provinces.nearest(LatLng(41.043, 29.007)).name)   // Beşiktaş
        assertEquals("İstanbul Anadolu", Provinces.nearest(LatLng(40.990, 29.027)).name)  // Kadıköy
        assertEquals("İstanbul Avrupa", Provinces.nearest(LatLng(41.167, 29.057)).name)   // Sarıyer
        assertEquals("Bursa", Provinces.nearest(LatLng(40.20, 29.20)).name)
        assertEquals("İstanbul", Provinces.nearest(LatLng(40.99, 29.03)).displayName)
    }

    @Test
    fun `fiyat degisimi bildirimi yalnizca anlamli farkta uretilir`() {
        assertNull(priceChangeNotice(FuelType.BENZIN, "Bursa", 80.16, 80.18))

        val increase = priceChangeNotice(FuelType.BENZIN, "İstanbul Anadolu", 80.16, 81.40)!!
        assertEquals("Benzin fiyatı arttı", increase.title)
        assertEquals("İstanbul Anadolu: 80,16 ₺ → 81,40 ₺ (+1,24 ₺/L)", increase.text)

        val decrease = priceChangeNotice(FuelType.LPG, "Konya", 35.00, 34.10)!!
        assertEquals("LPG fiyatı düştü", decrease.title)
        assertTrue(decrease.text.endsWith("(−0,90 ₺/L)"))
    }

    @Test
    fun `uzak ayar yalnizca gecerli https adreslerini uygular`() {
        val originalOsrm = Endpoints.osrm
        val originalOverpass = Endpoints.overpass

        assertFalse("Bozuk JSON reddedilmeli", Endpoints.apply("<html>404</html>"))
        assertEquals(originalOsrm, Endpoints.osrm)

        assertTrue(Endpoints.apply("""{"osrm":"http://evil.example","overpass":["ftp://x"],"photon":42}"""))
        assertEquals("https olmayan adres yok sayılmalı", originalOsrm, Endpoints.osrm)
        assertEquals(originalOverpass, Endpoints.overpass)

        assertTrue(Endpoints.apply("""{"osrm":"https://osrm.example.com/","overpass":["https://a.example/api","http://b"]}"""))
        assertEquals("https://osrm.example.com", Endpoints.osrm)
        assertEquals(listOf("https://a.example/api"), Endpoints.overpass)

        // Diğer testleri etkilemesin.
        Endpoints.apply("""{"osrm":"$originalOsrm","overpass":${originalOverpass.joinToString(prefix = "[\"", separator = "\",\"", postfix = "\"]")}}""")
    }
}
