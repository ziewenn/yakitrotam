package com.yakitrotam.app.data.model

import kotlinx.serialization.Serializable

@Serializable
data class LatLng(
    val latitude: Double,
    val longitude: Double
) {
    fun toFormattedString(): String = "%.5f,%.5f".format(latitude, longitude)
}

/** Varışta depoda kalması istenen en az yakıt. [percent] null ise rezerv eşiği yeterlidir. */
@Serializable
enum class ArrivalFuel(val label: String, val percent: Double?) {
    RESERVE("Rezerv yeter", null),
    QUARTER("Çeyrek depo", 25.0),
    HALF("Yarım depo", 50.0)
}

/** Elektrikli aracın DC hızlı şarj soketi. Türkiye'de satılan araçların neredeyse tamamı CCS2'dir. */
@Serializable
enum class DcConnector(val label: String) {
    CCS2("CCS2"),
    CHADEMO("CHAdeMO")
}

/**
 * Araç profili. Elektrikli araçta ([FuelType.ELEKTRIK]) "depo" batarya, litre alanları kWh,
 * [currentLevelPercent] de şarj yüzdesi (SoC) olarak okunur; böylece menzil hesapları ortak kalır.
 */
@Serializable
data class VehicleProfile(
    val id: String = "default_car",
    val name: String = "Aracım",
    val fuelType: FuelType = FuelType.BENZIN,
    val consumptionPer100Km: Double = 6.8,    // 100 km'de tüketim (Litre)
    val tankCapacityLiters: Double = 50.0,    // Toplam depo kapasitesi (Litre)
    val currentLevelPercent: Double = 50.0,   // Başlangıç depo doluluk oranı (%)
    val reserveThresholdPercent: Double = 15.0, // Rezerv uyarı sınırı (%) örn. %15 kalınca yakıt alınmalı
    val arrivalFuel: ArrivalFuel = ArrivalFuel.RESERVE,
    /** Elektrikli araç: aracın kabul ettiği en yüksek DC şarj gücü (kW). */
    val maxChargeKw: Double = 100.0,
    /** Elektrikli araç: DC şarj soketi. */
    val connector: DcConnector = DcConnector.CCS2,
    /** Elektrikli araç: varış noktasında bataryada kalması istenen en az yüzde. */
    val evArrivalPercent: Double = 20.0
) {
    val isElectric: Boolean
        get() = fuelType.isElectric

    /** Varışta depoda kalması gereken en az yakıt; rezervin altına inemez. */
    val arrivalMinLiters: Double
        get() = maxOf(reserveLiters, tankCapacityLiters * ((arrivalFuel.percent ?: 0.0) / 100.0))

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

    companion object {
        /** Yakıt türü ilk kez "Elektrik" seçildiğinde başlanan makul bir elektrikli araç. */
        val EV_DEFAULT = VehicleProfile(
            name = "Elektrikli aracım",
            fuelType = FuelType.ELEKTRIK,
            consumptionPer100Km = 18.0,
            tankCapacityLiters = 60.0,
            currentLevelPercent = 80.0
        )
    }
}
