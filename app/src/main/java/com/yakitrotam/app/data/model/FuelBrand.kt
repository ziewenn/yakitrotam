package com.yakitrotam.app.data.model

import kotlinx.serialization.Serializable

@Serializable
enum class FuelBrand(
    val displayName: String,
    val primaryColorHex: Long,
    val accentColorHex: Long,
    /** OpenStreetMap `brand` / `operator` / `name` etiketlerinde geçen yazımlar. */
    val aliases: List<String> = emptyList()
) {
    SHELL("Shell", 0xFFFFD700, 0xFFDD1D21, listOf("shell")),
    OPET("Opet", 0xFF003399, 0xFF00B0FF, listOf("opet", "sunpet")),
    PETROL_OFISI("Petrol Ofisi", 0xFFE30613, 0xFFFFFFFF, listOf("petrol ofisi", "petrol ofisi̇", "po", "petrolofisi")),
    BP("BP", 0xFF008837, 0xFFE3DC00, listOf("bp", "british petroleum", "castrol")),
    TOTAL("TotalEnergies", 0xFFEE1D23, 0xFF1C63B7, listOf("total", "totalenergies")),
    AYTEMIZ("Aytemiz", 0xFFD32F2F, 0xFFFFA000, listOf("aytemiz")),
    TP("Türkiye Petrolleri", 0xFFC62828, 0xFFFFFFFF, listOf("türkiye petrolleri", "turkiye petrolleri", "tp", "tpao", "tppd")),
    ALPET("Alpet", 0xFF0B5FA5, 0xFFF57C00, listOf("alpet")),
    LUKOIL("Lukoil", 0xFFD50000, 0xFFFFFFFF, listOf("lukoil", "lukoıl")),
    DIGER("Diğer", 0xFF607D8B, 0xFF90A4AE);

    companion object {
        /**
         * OSM'den gelen serbest metni markaya eşler. Etiketler "Shell Kurtköy",
         * "OPET Petrolcülük A.Ş." gibi ek kelimeler içerdiği için parça eşleşmesi yapılır.
         */
        fun fromString(value: String): FuelBrand {
            val normalized = value.trim().lowercase(java.util.Locale.forLanguageTag("tr"))
            if (normalized.isBlank()) return DIGER

            entries.firstOrNull { it.name.equals(value, ignoreCase = true) }?.let { return it }
            // Uzun takma adlar önce denenir: "petrol ofisi" içeren metin "po" ile de eşleşir.
            return entries
                .flatMap { brand -> brand.aliases.map { brand to it } }
                .sortedByDescending { it.second.length }
                .firstOrNull { (_, alias) -> normalized.containsWord(alias) }
                ?.first
                ?: DIGER
        }

        /** Kelime sınırlarına saygılı içerme: "topet" metni "opet" markasına düşmesin. */
        private fun String.containsWord(needle: String): Boolean {
            var index = indexOf(needle)
            while (index >= 0) {
                val beforeOk = index == 0 || !this[index - 1].isLetterOrDigit()
                val endIndex = index + needle.length
                val afterOk = endIndex == length || !this[endIndex].isLetterOrDigit()
                if (beforeOk && afterOk) return true
                index = indexOf(needle, index + 1)
            }
            return false
        }
    }
}

@Serializable
enum class FuelType(
    val displayName: String,
    /**
     * Canlı fiyat servisi (Opet) ulaşılamadığında kullanılan son bilinen pompa fiyatı (TL/L).
     * 16 Eylül 2026 itibarıyla güncellenmiştir; gerçek değer her zaman [FuelPriceSnapshot]'tan gelir.
     */
    val fallbackPricePerLiterTL: Double
) {
    BENZIN("Benzin (95 Oktan)", 80.16),
    DIZEL("Motorin (Dizel)", 100.19),
    LPG("Otogaz (LPG)", 34.39);

    fun shortName(): String = when (this) {
        BENZIN -> "Benzin"
        DIZEL -> "Motorin"
        LPG -> "LPG"
    }

    companion object {
        fun fromString(value: String): FuelType {
            return entries.firstOrNull {
                it.name.equals(value, ignoreCase = true) ||
                    it.displayName.contains(value, ignoreCase = true)
            } ?: BENZIN
        }
    }
}
