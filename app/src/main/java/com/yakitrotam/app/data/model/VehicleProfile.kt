package com.yakitrotam.app.data.model

import kotlinx.serialization.Serializable

@Serializable
data class LatLng(
    val latitude: Double,
    val longitude: Double
) {
    fun toFormattedString(): String = "%.5f,%.5f".format(latitude, longitude)
}

@Serializable
data class VehicleProfile(
    val id: String = "default_car",
    val name: String = "Aracım",
    val fuelType: FuelType = FuelType.BENZIN,
    val consumptionPer100Km: Double = 6.8,    // 100 km'de tüketim (Litre)
    val tankCapacityLiters: Double = 50.0,    // Toplam depo kapasitesi (Litre)
    val currentLevelPercent: Double = 50.0,   // Başlangıç depo doluluk oranı (%)
    val reserveThresholdPercent: Double = 15.0 // Rezerv uyarı sınırı (%) örn. %15 kalınca yakıt alınmalı
) {
    val currentFuelLiters: Double
        get() = tankCapacityLiters * (currentLevelPercent / 100.0)

    val reserveLiters: Double
        get() = tankCapacityLiters * (reserveThresholdPercent / 100.0)

    val safeUsableCurrentFuelLiters: Double
        get() = (currentFuelLiters - reserveLiters).coerceAtLeast(0.0)

    /**
     * Mevcut yakıtla rezerve düşmeden gidilebilecek güvenli menzil (km)
     */
    fun calculateCurrentSafeRangeKm(): Double {
        if (consumptionPer100Km <= 0) return 0.0
        return (safeUsableCurrentFuelLiters / consumptionPer100Km) * 100.0
    }

    /**
     * Tam dolu depoyla rezerve kadar gidilebilecek güvenli menzil (km)
     */
    fun calculateFullTankSafeRangeKm(): Double {
        if (consumptionPer100Km <= 0) return 0.0
        val usableLiters = (tankCapacityLiters - reserveLiters).coerceAtLeast(0.0)
        return (usableLiters / consumptionPer100Km) * 100.0
    }
}
