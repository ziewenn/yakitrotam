package com.yakitrotam.app.data.model

import kotlinx.serialization.Serializable

@Serializable
data class GasStation(
    val id: String,
    val name: String,
    val brand: FuelBrand,
    val latitude: Double,
    val longitude: Double,
    val highway: String = "",
    val city: String = "",
    val hasLpg: Boolean? = null,
    val hasDiesel: Boolean? = null,
    val hasGasoline: Boolean? = null,
    val hasTasitTanima: Boolean = false,
    val hasMarket: Boolean? = null,
    val hasRestaurant: Boolean? = null,
    val is24Hours: Boolean? = null,
    val osmType: String = "node",
    val osmId: Long? = null,
    val openingHours: String? = null,
    val dataSource: String = "OpenStreetMap",
    /** Şarj istasyonu: CCS2 soketlerinin en yüksek gücü (kW); soket yoksa null. */
    val ccsKw: Double? = null,
    /** Şarj istasyonu: CHAdeMO soketlerinin en yüksek gücü (kW); soket yoksa null. */
    val chademoKw: Double? = null,
    /** Şarj istasyonu: kartta gösterilen soket özeti ("ZES · CCS 180 kW ×2 · AC 22 kW"). */
    val chargeInfo: String? = null,
    /** Şarj istasyonu: operatörün üyeliksiz DC liste fiyatı (TL/kWh); bilinmiyorsa null. */
    val kwhPriceTL: Double? = null,
    /** Şarj istasyonu: şarj ağının adı ("ZES", "Trugo"); şarj ağı tercihi bununla eşlenir. */
    val operatorName: String? = null
) {
    val location: LatLng
        get() = LatLng(latitude, longitude)

    /** OSM'de adı da markası da girilmemiş istasyon. */
    val isUnnamed: Boolean
        get() = name == UNNAMED

    fun fuelAvailability(fuelType: FuelType): Boolean? = when (fuelType) {
        FuelType.BENZIN -> hasGasoline
        FuelType.DIZEL -> hasDiesel
        FuelType.LPG -> hasLpg
        FuelType.ELEKTRIK -> ccsKw != null || chademoKw != null
    }

    /** Bu sokete uyan DC şarjın gücü (kW); istasyonda o soket yoksa null. */
    fun dcKwFor(connector: DcConnector): Double? = when (connector) {
        DcConnector.CCS2 -> ccsKw
        DcConnector.CHADEMO -> chademoKw
    }

    /** `false` açıkça desteklenmediğini, `null` ise OSM'de bilginin olmadığını belirtir. */
    fun supportsFuel(fuelType: FuelType): Boolean = fuelAvailability(fuelType) != false

    fun confirmsFuel(fuelType: FuelType): Boolean = fuelAvailability(fuelType) == true

    companion object {
        const val UNNAMED = "Markasız istasyon"
    }
}
