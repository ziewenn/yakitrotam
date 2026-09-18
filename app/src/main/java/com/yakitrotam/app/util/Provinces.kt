package com.yakitrotam.app.util

import com.yakitrotam.app.data.model.LatLng
import java.util.Locale

/** Pompa fiyatı bölgesi olarak il. İstanbul'un iki yakası ayrı fiyatlandırıldığı için iki kayıttır. */
data class Province(val name: String, val latitude: Double, val longitude: Double) {
    /** Fiyat kaynaklarındaki yazımlarla eşleştirme anahtarı. */
    val key: String = provinceKey(name)

    /** Kullanıcıya gösterilen ad; İstanbul'un yakası belirtilmez. */
    val displayName: String
        get() = name.removeSuffix(" Avrupa").removeSuffix(" Anadolu")
}

/** "İSTANBUL (ANADOLU)", "İstanbul Anadolu" ve "ISTANBUL ANADOLU" aynı anahtara iner. */
fun provinceKey(name: String): String = name
    .lowercase(Locale.forLanguageTag("tr"))
    .map { TURKISH_TO_ASCII[it] ?: it }
    .filter { it in 'a'..'z' }
    .joinToString("")

private val TURKISH_TO_ASCII = mapOf('ç' to 'c', 'ğ' to 'g', 'ı' to 'i', 'ö' to 'o', 'ş' to 's', 'ü' to 'u', 'â' to 'a', 'î' to 'i')

object Provinces {
    private const val ISTANBUL_EUROPE = "İstanbul Avrupa"
    private const val ISTANBUL_ASIA = "İstanbul Anadolu"

    val all: List<Province> = listOf(
        Province("Adana", 37.00, 35.32), Province("Adıyaman", 37.76, 38.28),
        Province("Afyonkarahisar", 38.76, 30.54), Province("Ağrı", 39.72, 43.05),
        Province("Aksaray", 38.37, 34.03), Province("Amasya", 40.65, 35.83),
        Province("Ankara", 39.93, 32.85), Province("Antalya", 36.89, 30.71),
        Province("Ardahan", 41.11, 42.70), Province("Artvin", 41.18, 41.82),
        Province("Aydın", 37.85, 27.84), Province("Balıkesir", 39.65, 27.88),
        Province("Bartın", 41.64, 32.34), Province("Batman", 37.88, 41.13),
        Province("Bayburt", 40.26, 40.23), Province("Bilecik", 40.14, 29.98),
        Province("Bingöl", 38.88, 40.50), Province("Bitlis", 38.40, 42.11),
        Province("Bolu", 40.74, 31.61), Province("Burdur", 37.72, 30.29),
        Province("Bursa", 40.19, 29.06), Province("Çanakkale", 40.15, 26.41),
        Province("Çankırı", 40.60, 33.62), Province("Çorum", 40.55, 34.95),
        Province("Denizli", 37.78, 29.09), Province("Diyarbakır", 37.91, 40.24),
        Province("Düzce", 40.84, 31.16), Province("Edirne", 41.68, 26.56),
        Province("Elazığ", 38.67, 39.22), Province("Erzincan", 39.75, 39.49),
        Province("Erzurum", 39.90, 41.27), Province("Eskişehir", 39.78, 30.52),
        Province("Gaziantep", 37.07, 37.38), Province("Giresun", 40.91, 38.39),
        Province("Gümüşhane", 40.46, 39.48), Province("Hakkari", 37.57, 43.74),
        Province("Hatay", 36.20, 36.16), Province("Iğdır", 39.92, 44.05),
        Province("Isparta", 37.76, 30.55), Province(ISTANBUL_EUROPE, 41.03, 28.80),
        Province(ISTANBUL_ASIA, 40.98, 29.25), Province("İzmir", 38.42, 27.14),
        Province("Kahramanmaraş", 37.58, 36.93), Province("Karabük", 41.20, 32.62),
        Province("Karaman", 37.18, 33.22), Province("Kars", 40.60, 43.10),
        Province("Kastamonu", 41.38, 33.78), Province("Kayseri", 38.72, 35.49),
        Province("Kilis", 36.72, 37.12), Province("Kırıkkale", 39.85, 33.51),
        Province("Kırklareli", 41.73, 27.22), Province("Kırşehir", 39.15, 34.16),
        Province("Kocaeli", 40.77, 29.94), Province("Konya", 37.87, 32.48),
        Province("Kütahya", 39.42, 29.98), Province("Malatya", 38.35, 38.31),
        Province("Manisa", 38.61, 27.43), Province("Mardin", 37.31, 40.74),
        Province("Mersin", 36.81, 34.64), Province("Muğla", 37.22, 28.36),
        Province("Muş", 38.73, 41.49), Province("Nevşehir", 38.62, 34.71),
        Province("Niğde", 37.97, 34.68), Province("Ordu", 40.98, 37.88),
        Province("Osmaniye", 37.07, 36.25), Province("Rize", 41.02, 40.52),
        Province("Sakarya", 40.77, 30.40), Province("Samsun", 41.29, 36.33),
        Province("Şanlıurfa", 37.16, 38.79), Province("Siirt", 37.93, 41.94),
        Province("Sinop", 42.03, 35.15), Province("Şırnak", 37.52, 42.46),
        Province("Sivas", 39.75, 37.02), Province("Tekirdağ", 40.98, 27.51),
        Province("Tokat", 40.31, 36.55), Province("Trabzon", 41.00, 39.72),
        Province("Tunceli", 39.11, 39.55), Province("Uşak", 38.68, 29.41),
        Province("Van", 38.49, 43.38), Province("Yalova", 40.65, 29.27),
        Province("Yozgat", 39.82, 34.81), Province("Zonguldak", 41.45, 31.79)
    )

    /**
     * Noktanın fiyat bölgesi: en yakın il merkezi.
     * ponytail: il sınırı değil merkez yakınlığı kullanılır, sınıra yakın noktalarda komşu il
     * dönebilir. Komşu iller arasındaki pompa farkı birkaç kuruş olduğu için yeterli; kesin il
     * gerekirse il sınırı poligonlarına geçilmeli.
     */
    fun nearest(point: LatLng): Province {
        val closest = all.minBy { GeoUtils.distanceKm(point, LatLng(it.latitude, it.longitude)) }
        if (closest.name != ISTANBUL_EUROPE && closest.name != ISTANBUL_ASIA) return closest
        // Boğaz kabaca kuzeydoğuya uzanır: 41.0°K'da 29.00°D, 41.2°K'da 29.11°D.
        val bosphorusLongitude = 29.0 + (point.latitude - 41.0) * 0.55
        val side = if (point.longitude < bosphorusLongitude) ISTANBUL_EUROPE else ISTANBUL_ASIA
        return all.first { it.name == side }
    }

    /** Kaynaktaki il adını ("AFYON", "İSTANBUL (AVRUPA)") tablodaki ile eşler. */
    fun byName(name: String): Province? {
        val key = provinceKey(name)
        all.firstOrNull { it.key == key }?.let { return it }
        // Kaynaklar kısaltabiliyor ("AFYON"); tek harflik hücreler yanlış eşleşmesin.
        if (key.length < 3) return null
        return all.firstOrNull { it.key.startsWith(key) || key.startsWith(it.key) }
    }
}
