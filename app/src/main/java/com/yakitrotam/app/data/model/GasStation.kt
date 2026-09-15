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
    val hasLpg: Boolean = true,
    val hasDiesel: Boolean = true,
    val hasGasoline: Boolean = true,
    val hasTasitTanima: Boolean = true,
    val hasMarket: Boolean = true,
    val hasRestaurant: Boolean = false,
    val is24Hours: Boolean = true
) {
    val location: LatLng
        get() = LatLng(latitude, longitude)

    fun supportsFuel(fuelType: FuelType): Boolean {
        return when (fuelType) {
            FuelType.BENZIN -> hasGasoline
            FuelType.DIZEL -> hasDiesel
            FuelType.LPG -> hasLpg
        }
    }
}
