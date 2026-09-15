package com.yakitrotam.app.data.model

import kotlinx.serialization.Serializable

/**
 * Belirli bir il için o an geçerli pompa fiyatları.
 * [estimatedTypes] içindeki yakıt türlerinin fiyatı canlı servisten değil tahminden gelir.
 */
@Serializable
data class FuelPriceSnapshot(
    val pricesPerLiterTL: Map<FuelType, Double>,
    val sourceName: String,
    val sourceUrl: String,
    val regionName: String,
    val priceDate: String,
    val fetchedAtEpochMillis: Long,
    val estimatedTypes: Set<FuelType> = emptySet(),
    val isFallback: Boolean = false
) {
    fun priceFor(fuelType: FuelType): Double =
        pricesPerLiterTL[fuelType] ?: fuelType.fallbackPricePerLiterTL

    fun isEstimated(fuelType: FuelType): Boolean =
        isFallback || fuelType in estimatedTypes || fuelType !in pricesPerLiterTL

    companion object {
        /** Ağ erişimi yokken kullanılan, uygulamaya gömülü son bilinen fiyatlar. */
        fun fallback(): FuelPriceSnapshot = FuelPriceSnapshot(
            pricesPerLiterTL = FuelType.entries.associateWith { it.fallbackPricePerLiterTL },
            sourceName = "Uygulamaya gömülü son bilinen fiyatlar",
            sourceUrl = "",
            regionName = "Türkiye geneli",
            priceDate = "16.09.2026",
            fetchedAtEpochMillis = 0L,
            estimatedTypes = FuelType.entries.toSet(),
            isFallback = true
        )
    }
}
