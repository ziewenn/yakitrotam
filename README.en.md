[Türkçe](README.md) | English

# YakıtRotam

A refuelling stop planner for combustion vehicles. You enter your tank size,
average consumption and current fuel level; the app works out where along the
route you need to stop before dipping into your reserve, then hands the route
and its stops over to Google Maps.

What ABRP does for electric cars, for petrol, diesel and LPG.

## How it works

The driving route comes from OSRM. Fuel stations inside the route corridor are
then pulled from the OpenStreetMap Overpass API, and each one is projected onto
the polyline to get two numbers: how far along the route it sits, and how far it
deviates from it.

Stop selection is a range-constrained greedy search. Only stations that are
genuinely reachable on the fuel currently in the tank become candidates, and the
furthest reachable one wins. Detour distance, brand preference and whether OSM
actually confirms the fuel type feed into the score. If nothing suitable is in
range the app does not invent a stop, it warns you instead.

Costs are computed from live pump prices. The final stop does not fill the tank
to the brim; it takes only what is needed to reach the destination, plus the
reserve and a 15 percent margin.

## Data sources

The app needs no API keys. Every service it talks to is free and keyless.

| Data | Source |
| --- | --- |
| Fuel stations | OpenStreetMap Overpass API (ODbL) |
| Pump prices | Opet province-level price service |
| Driving route | OSRM |
| Address search | Photon |
| Reverse geocoding | Nominatim |
| Device location | FusedLocationProvider |

Google Places and the Maps SDK are billed per call, so they are not used. Google
Maps is only invoked through an Android intent to open the finished route, which
costs nothing.

Station data is not bundled with the app; it is fetched fresh for every route.
That is what keeps stops from landing on locations where no station exists.

Opet publishes petrol and diesel but not LPG. The LPG price is estimated as a
ratio of the petrol price and is labelled as an estimate in the UI.

## Features

- Range simulation from tank size, L/100km consumption, current level and
  reserve threshold.
- Petrol, diesel and LPG. Whether a station sells a given fuel is read from its
  OSM tags.
- Brand preference (Shell, Opet, Petrol Ofisi, BP, TotalEnergies, Aytemiz,
  Türkiye Petrolleri, Alpet, Lukoil). Preference is a scoring criterion rather
  than a hard filter, so you are never stranded because your brand was absent.
- Per stop: fuel remaining on arrival in percent and litres, litres to add,
  estimated cost, detour from the main road.
- Cost breakdown: fuel burned for the trip, total paid at the pump, cost per
  100 km.
- Vector preview of the route and its stops.
- All stops are passed to Google Maps as waypoints.
- The last vehicle settings (tank size, consumption, fuel type, fill level),
  brand preference, origin and destination survive an app restart. Recently
  picked places are listed first in the address search.

## Running it

Open the project in Android Studio, wait for the Gradle sync, then pick an
emulator or a connected device and run the `app` configuration.

From a terminal:

```bash
./gradlew installDebug
```

No API key and no `local.properties` entries are required. The app only needs
internet access.

## Tests

```bash
./gradlew test
```

| Test | Covers |
| --- | --- |
| `GeoUtilsTest` | Distance, route projection and interpolation maths |
| `FuelOptimizerEngineTest` | Range constraint, brand scoring, cost calculation |
| `DemoRoutePlanTest` | End-to-end plan over a real OSRM route and 391 real OSM stations |
| `GoogleMapsIntentTest` | Waypoint parameters in the generated Maps URL |
| `PersistenceAndQueryTest` | Saving and restoring settings, station query covering the whole route |

`DemoRoutePlanTest` reads Istanbul-Ankara data captured from the live services
under `app/src/test/resources/demo/`. It makes no network calls, but because the
data is real it verifies that the chosen stops are stations that actually exist
and that they stay within a 6 km corridor of the route.

## APK and automated releases

Every push to `main` runs
[`.github/workflows/android-release.yml`](.github/workflows/android-release.yml),
which executes the tests, bumps the version from the pipeline number, builds an
APK signed with the same release key and uploads it to GitHub Releases.

Latest APK: https://github.com/ziewenn/yakitrotam/releases/latest

[Obtainium](https://github.com/ImranR98/Obtainium) can watch this repository and
notify you about updates; adding the repo URL as a source is enough. Android's
security model does not allow sideloaded apps to install silently, so you will
be asked to confirm each update.

A local recovery copy of the CI signing key lives in
`D:\YakitRotam-signing-backup`. If that key is lost, updates can no longer be
installed over an already installed APK, so the folder should be backed up
somewhere safe.

## Technical notes

Kotlin 1.9.23, Jetpack Compose with Material 3, single Activity. The layers are
split into ViewModel, repositories and a pure Kotlin calculation engine, with
state carried over StateFlow. OkHttp handles networking and kotlinx.serialization
handles models.

```
app/src/main/java/com/yakitrotam/app/
├── MainActivity.kt
├── data/
│   ├── model/
│   │   ├── FuelBrand.kt            Brands, colours, OSM tag mapping
│   │   ├── FuelPriceSnapshot.kt    Pump price snapshot
│   │   ├── GasStation.kt           Station and OSM-derived fuel availability
│   │   ├── TripModels.kt           Stops and trip summary
│   │   └── VehicleProfile.kt       Tank, consumption, range formulas
│   └── repository/
│       ├── FuelPriceRepository.kt  Opet price service, province lookup, cache
│       ├── GasStationRepository.kt Station cache, fuel and brand filtering
│       ├── LocationService.kt      Photon search, Nominatim, device location
│       ├── OverpassStationSource.kt Corridor query and OSM tag parsing
│       ├── RouteRepository.kt      OSRM with an offline corridor fallback
│       └── TripPreferences.kt      Persists the last entered settings
├── domain/
│   └── FuelOptimizerEngine.kt      Range simulation and stop selection
├── ui/
│   ├── components/                 Gauge, price strip, timeline, mini map
│   ├── screens/                    Planner and summary screens
│   ├── theme/
│   └── viewmodel/
└── util/
    ├── GeoUtils.kt                 Haversine, route projection, interpolation
    └── GoogleMapsLauncher.kt       Maps intent
```

## Known limitations

- The LPG price is an estimate. No free, machine-readable autogas price feed was
  found.
- Station quality depends on OSM. Stations without a brand or fuel tags show up
  as "Diğer" and score lower.
- Without a network connection the route falls back to a built-in motorway
  corridor, but the station list stays empty, so no plan is produced.
- Travel time assumes a fixed average speed and ignores traffic.
