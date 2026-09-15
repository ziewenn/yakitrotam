package com.yakitrotam.app.data.model

import kotlinx.serialization.Serializable

@Serializable
enum class FuelBrand(
    val displayName: String,
    val primaryColorHex: Long,
    val accentColorHex: Long
) {
    SHELL("Shell", 0xFFFFD700, 0xFFDD1D21),       // Sarı & Kırmızı
    OPET("Opet", 0xFF003399, 0xFF00B0FF),        // Mavi & Açık Mavi
    PETROL_OFISI("Petrol Ofisi", 0xFFE30613, 0xFFFFFFFF), // Kırmızı & Beyaz
    BP("BP", 0xFF008837, 0xFFE3DC00),           // Yeşil & Sarı
    TOTAL("TotalEnergies", 0xFFEE1D23, 0xFF1C63B7), // Kırmızı & Mavi
    AYTEMIZ("Aytemiz", 0xFFD32F2F, 0xFFFFA000), // Kırmızı & Turuncu
    TP("Türkiye Petrolleri", 0xFFC62828, 0xFFFFFFFF), // Kırmızı
    LUKOIL("Lukoil", 0xFFD50000, 0xFFFFFFFF),    // Kırmızı
    DIGER("Diğer", 0xFF607D8B, 0xFF90A4AE);      // Nötr

    companion object {
        fun fromString(value: String): FuelBrand {
            return entries.firstOrNull { 
                it.name.equals(value, ignoreCase = true) || 
                it.displayName.equals(value, ignoreCase = true) 
            } ?: DIGER
        }
    }
}

@Serializable
enum class FuelType(
    val displayName: String,
    val averagePricePerLiterTL: Double // Güncel Türkiye ortalama pompa fiyatı (TL/L)
) {
    BENZIN("Benzin (95 Oktan)", 43.50),
    DIZEL("Motorin (Dizel)", 44.20),
    LPG("Otogaz (LPG)", 22.80);

    companion object {
        fun fromString(value: String): FuelType {
            return entries.firstOrNull { 
                it.name.equals(value, ignoreCase = true) || 
                it.displayName.contains(value, ignoreCase = true)
            } ?: BENZIN
        }
    }
}
