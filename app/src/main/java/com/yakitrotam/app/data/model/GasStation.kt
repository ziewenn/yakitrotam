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
    val dataSource: String = "OpenStreetMap"
) {
    val location: LatLng
        get() = LatLng(latitude, longitude)

    fun fuelAvailability(fuelType: FuelType): Boolean? = when (fuelType) {
        FuelType.BENZIN -> hasGasoline
        FuelType.DIZEL -> hasDiesel
        FuelType.LPG -> hasLpg
    }

    /** `false` açıkça desteklenmediğini, `null` ise OSM'de bilginin olmadığını belirtir. */
    fun supportsFuel(fuelType: FuelType): Boolean = fuelAvailability(fuelType) != false

    fun confirmsFuel(fuelType: FuelType): Boolean = fuelAvailability(fuelType) == true
}
