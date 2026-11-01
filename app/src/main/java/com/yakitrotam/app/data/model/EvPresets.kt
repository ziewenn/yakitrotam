package com.yakitrotam.app.data.model

/**
 * Türkiye'de satılan elektrikli araçlar için hazır değerler: kullanılabilir batarya (kWh),
 * yolda ortalama tüketim (kWh/100 km) ve en yüksek DC şarj gücü (kW).
 *
 * Kaynak: Open EV Data (https://github.com/KilowattApp/open-ev-data, MIT lisansı) ve üreticilerin
 * Türkiye sayfaları. Yalnızca WLTP'den gelen tüketimler 1,2 ile çarpıldı (yolda gerçek tüketime
 * yaklaşsın, şarj durağı geç kalmasın diye). Kullanıcı tüm değerleri değiştirebilir.
 */
data class EvPreset(
    val brand: String,
    val model: String,
    val batteryKwh: Double,
    val consumptionPer100Km: Double,
    val maxDcKw: Double
) {
    val label: String
        get() = "$brand $model"
}

object EvPresets {
    val all: List<EvPreset> = listOf(
        EvPreset("BMW", "i4 eDrive35", 67.0, 16.8, 180.0),
        EvPreset("BMW", "i4 xDrive40", 81.3, 16.6, 207.0),
        EvPreset("BMW", "iX1 eDrive20", 64.7, 16.6, 128.0),
        EvPreset("BMW", "iX1 xDrive30", 64.7, 18.2, 130.0),
        EvPreset("BMW", "iX2 eDrive20", 64.7, 16.4, 128.0),
        EvPreset("BYD", "Atto 2 45.1 kWh Boost/Comfort", 45.1, 19.2, 65.0),
        EvPreset("BYD", "Atto 3 60.5 kWh", 60.5, 18.6, 89.0),
        EvPreset("BYD", "Dolphin 44.9 kWh Boost", 44.9, 17.3, 60.0),
        EvPreset("BYD", "Dolphin 60.4 kWh", 60.5, 17.3, 88.0),
        EvPreset("BYD", "Seal 82.5 kWh AWD Excellence", 82.5, 16.8, 150.0),
        EvPreset("BYD", "Seal 82.5 kWh RWD Design", 82.5, 16.5, 150.0),
        EvPreset("BYD", "Seal U EV 71.8 kWh Comfort", 71.8, 19.9, 115.0),
        EvPreset("BYD", "Seal U EV 87 kWh Design", 87.0, 20.5, 140.0),
        EvPreset("BYD", "Sealion 7 160 kW Design RWD", 71.8, 22.4, 150.0),
        EvPreset("BYD", "Sealion 7 390 kW AWD Excellence", 91.3, 22.4, 230.0),
        EvPreset("Citroen", "e-C3 Aircross 44 kWh", 44.0, 17.6, 100.0),
        EvPreset("Citroen", "e-C4 54 kWh", 50.8, 15.2, 100.0),
        EvPreset("Citroen", "e-C4 X 54 kWh", 50.8, 14.9, 100.0),
        EvPreset("Fiat", "500e 42 kWh", 37.3, 15.9, 85.0),
        EvPreset("Fiat", "600e", 50.8, 16.4, 100.0),
        EvPreset("Fiat", "Grande Panda", 43.8, 16.8, 100.0),
        EvPreset("Ford", "Capri Extended Range RWD", 77.0, 16.9, 135.0),
        EvPreset("Ford", "Explorer Extended Range RWD", 77.0, 17.7, 135.0),
        EvPreset("Ford", "Explorer Standard Range RWD", 52.0, 17.0, 145.0),
        EvPreset("Ford", "Puma Gen-E", 43.6, 15.9, 100.0),
        EvPreset("Hyundai", "Inster Long Range", 46.0, 15.6, 85.0),
        EvPreset("Hyundai", "Inster Standard Range", 39.0, 15.6, 73.0),
        EvPreset("Hyundai", "Kona Elektrik 48.4 kWh", 48.4, 16.4, 75.0),
        EvPreset("Hyundai", "Kona Elektrik 65.4 kWh", 65.4, 16.8, 105.0),
        EvPreset("KGM", "Torres EVX", 72.0, 19.5, 120.0),
        EvPreset("Kia", "EV3 Long Range 81.4 kWh", 81.4, 18.6, 128.0),
        EvPreset("Kia", "EV3 Standard Range 58.3 kWh", 58.3, 18.1, 101.0),
        EvPreset("Kia", "EV6 Long Range AWD (2024)", 80.0, 17.8, 263.0),
        EvPreset("Kia", "EV9 99.8 kWh AWD GT-Line", 96.0, 22.6, 209.0),
        EvPreset("Kia", "Niro EV", 68.4, 17.1, 80.0),
        EvPreset("Leapmotor", "T03", 37.3, 13.6, 48.0),
        EvPreset("Mercedes-Benz", "EQA 250+", 70.5, 16.8, 102.0),
        EvPreset("Mercedes-Benz", "EQB 250+", 70.5, 17.0, 102.0),
        EvPreset("MG", "MG4 64 kWh", 64.0, 17.0, 142.0),
        EvPreset("MG", "MG4 XPOWER", 61.7, 19.3, 142.0),
        EvPreset("MINI", "Countryman E", 64.6, 17.0, 128.0),
        EvPreset("MINI", "Countryman SE ALL4", 64.6, 17.7, 128.0),
        EvPreset("Opel", "Frontera Elektrik 44 kWh", 44.0, 22.2, 100.0),
        EvPreset("Peugeot", "e-2008 54 kWh", 50.8, 16.9, 100.0),
        EvPreset("Peugeot", "e-208 51 kWh", 48.1, 15.5, 100.0),
        EvPreset("Peugeot", "e-3008 73 kWh", 73.0, 19.2, 160.0),
        EvPreset("Peugeot", "e-5008 73 kWh", 73.0, 19.7, 160.0),
        EvPreset("Renault", "Megane E-Tech EV60 220 hp", 60.0, 15.8, 129.0),
        EvPreset("Renault", "R5 E-Tech 40 kWh 120 hp", 40.0, 15.7, 80.0),
        EvPreset("Renault", "R5 E-Tech 52 kWh 150 hp", 52.0, 16.3, 100.0),
        EvPreset("Renault", "Scenic E-Tech EV87 220 hp", 87.0, 18.1, 150.0),
        EvPreset("Skoda", "Elroq 50", 52.0, 16.5, 145.0),
        EvPreset("Skoda", "Elroq 60", 59.0, 17.1, 165.0),
        EvPreset("Skoda", "Elroq 85", 77.0, 17.1, 175.0),
        EvPreset("Skoda", "Enyaq 60", 59.0, 16.6, 165.0),
        EvPreset("Skoda", "Enyaq 85", 77.0, 17.1, 135.0),
        EvPreset("Skoda", "Enyaq Coupe 85", 77.0, 16.0, 135.0),
        EvPreset("Tesla", "Model Y Premium Long Range AWD", 75.0, 16.7, 250.0),
        EvPreset("Togg", "T10F V2 4More", 88.5, 20.3, 180.0),
        EvPreset("Togg", "T10F V2 RWD Uzun Menzil", 88.5, 17.0, 180.0),
        EvPreset("Togg", "T10X V1 RWD Standart Menzil", 52.4, 23.4, 180.0),
        EvPreset("Togg", "T10X V1/V2 RWD Uzun Menzil", 88.5, 22.9, 180.0),
        EvPreset("Volkswagen", "ID.4 Pro 4MOTION", 77.0, 18.1, 135.0),
        EvPreset("Volkswagen", "ID.7 Pro", 77.0, 16.2, 175.0),
        EvPreset("Volkswagen", "ID.7 Pro S", 86.0, 16.5, 200.0),
        EvPreset("Volvo", "EX30 Single Motor", 49.0, 17.5, 148.0),
        EvPreset("Volvo", "EX30 Single Motor Extended Range", 64.0, 17.5, 153.0),
        EvPreset("Volvo", "EX30 Twin Motor Performance", 64.0, 19.1, 158.0)
    )
}
