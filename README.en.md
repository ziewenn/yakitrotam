[Türkçe](README.md) | English

# YakıtRotam

A refuelling and charging stop planner. For petrol, diesel and LPG cars you enter
the tank size, average consumption and current fuel level; for electric cars the
battery, consumption and state of charge. The app works out where along the route
you need to stop, then hands the route and its stops over to Google Maps.

## How it works

The driving route comes from OSRM. Fuel stations inside the route corridor are
then pulled from the OpenStreetMap Overpass API, and each one is projected onto
the polyline to get two numbers: how far along the route it sits, and how far it
deviates from it.

Stop selection is a range-constrained greedy search. Only stations that are
genuinely reachable on the fuel currently in the tank become candidates, and the
furthest reachable one wins. Brand preference and whether OSM actually confirms
the fuel type feed into the score. If nothing suitable is in range the app does
not invent a stop, it warns you instead.

Detours are not measured as the crow flies. A station 200 metres from a
motorway often means leaving at the next junction and driving 20-30 km to get
back. Candidates are therefore sent to the OSRM table service, which gives the
real extra distance and time of leaving the route, visiting the station and
rejoining. If every candidate in the window needs a long detour, an earlier
station that is actually on the road is picked instead. If the service does not
answer, the straight-line estimate is used as a fallback.

Costs are computed from live pump prices, and each stop is priced with the
province it is in. The final stop does not fill the tank to the brim; it takes
only what is needed to reach the destination, plus the fuel you want left on
arrival (the reserve by default) and a 15 percent margin.

### Electric vehicles

Charging stops are chosen by a separate engine (`EvChargePlanner`) and shown on
the same screens. Only DC fast charging is used; charging on AC along the way
would take hours.

- You reach every charger with at least the reserve margin left (15% by default).
- Stops are scored in minutes: detour time, charging time, stopping early for no
  reason, and any extra stop added to the trip.
- It charges only what is needed, normally up to 80%. If 80% does not reach the
  next charger, the previous stop is extended up to 100%.
- Charging time uses one curve (Open EV Data's default: full power up to 50%,
  half at 80%, a tenth at 100%). Power is the lower of the charger and the car;
  chargers without the car's connector (CCS2 / CHAdeMO) are never picked.
- Cost uses the operator's non-member DC list price, counting ~5% more energy
  drawn from the grid than goes into the battery.
- Elevation and cold weather are not modelled; consumption and the reserve margin
  are set by the user.

## Data sources

The app needs no API keys. Every service it talks to is free and keyless.

| Data | Source |
| --- | --- |
| Fuel stations | OpenStreetMap (ODbL): prebuilt Turkey list, Overpass API as fallback |
| Charging stations | EPDK open access platform (`apigateway.epdk.gov.tr/sarjIstasyonlari`) |
| Charging prices | Each operator's own tariff page (`tools/ev_prices.json`) |
| EV models | Open EV Data (MIT) and manufacturer pages |
| Petrol and diesel prices | Opet price service (all provinces in one request) |
| Autogas (LPG) price | Petrol Ofisi price page |
| Driving route | OSRM |
| Address search | Photon |
| Reverse geocoding | Nominatim |
| Map display | OpenFreeMap (via MapLibre) |
| Device location | FusedLocationProvider |

Google Places and the Maps SDK are billed per call, so they are not used. Google
Maps is only invoked through an Android intent to open the finished route, which
costs nothing.

Station data is not bundled with the app. The public Overpass servers are often
busy or down, so the app does not query them for every route; it downloads the list
of all stations in Turkey (built from OSM by `tools/build_stations.py`, about 15,000
entries) from `ziewenn.github.io/yakitrotam/stations.json`, caches it for three days
and filters the route corridor on the device. If the list is unreachable it falls
back to a live Overpass query. To refresh the list, run the script and push its
output to the site repository.

Opet publishes petrol and diesel but not LPG, so the autogas price is read from
the table on Petrol Ofisi's price page. If the page is unreachable or its layout
changes, the LPG price falls back to a ratio of the petrol price and is labelled
as an estimate in the UI.

None of these servers comes with a service guarantee. Their addresses are built
into the app but are also read at startup from
`ziewenn.github.io/yakitrotam/config.json`, so a dead server can be swapped
without waiting for a store update. Only `https` addresses are accepted.

## Features

- Range simulation from tank size, L/100km consumption, current level and
  reserve threshold.
- Petrol, diesel and LPG. Whether a station sells a given fuel is read from its
  OSM tags.
- Brand preference (Shell, Opet, Petrol Ofisi, BP, TotalEnergies, Aytemiz,
  Türkiye Petrolleri, Alpet, Lukoil). Preference is a scoring criterion rather
  than a hard filter, so you are never stranded because your brand was absent.
  The preferred brand is looked for across the whole reachable range: as long as
  it does not add a stop to the trip, an earlier station on the road beats another
  brand at the end of the range, or the same brand behind a detour.
- Per stop: fuel remaining on arrival in percent and litres, litres to add,
  estimated cost, real extra distance and time (or "on the way").
- Cost breakdown: fuel burned for the trip, total paid at the pump, cost per
  100 km.
- Route and numbered stops drawn on a real map.
- Up to 3 alternative stations per stop, in the same area and reachable on the
  current fuel; picking one recalculates the following stops.
- Pick a location on the map for places without an address.
- Share the plan as plain text (WhatsApp, SMS, email).
- All stops are passed to Google Maps as waypoints.
- Map tab: nearby fuel or charging stations (150+ kW filter); tapping one shows
  power, price, directions and "plan a route". With location permission the map
  opens around the driver.
- After a few plans the app requests Google Play's in-app review prompt.
- Optional price alerts: a notification when the price of your fuel type changes
  in your origin province. The comparison runs on the device every 6 hours via
  WorkManager; there is no server.
- Advanced settings (collapsed by default in the vehicle sheet): reserve margin
  and the minimum fuel to have left on arrival (reserve, quarter or half tank).
- Electric vehicles: 68 searchable model presets or manual entry, current and
  desired arrival state of charge, and per stop the arrival/departure percentage,
  charging time, kW, kWh added and cost. The fuel car and the EV are stored
  separately; switching the fuel type brings the other car back.
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
| `EvChargePlannerTest` | Charging curve, stop choice, extending to 100%, connector match, warnings |
| `EvDataTest` | EV profile persistence, charger file parsing, remote config, share text |

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

## Ads

The app uses AdMob: a thin banner at the bottom of the planner screen, a native
ad styled like the app's cards after the first stop on the summary screen,
and a full-screen interstitial while a route is being calculated. If no ad is
returned, the banner and the native card take no space.
Interstitials are at least 3 minutes apart. Users in the EEA and UK see Google's
consent form (UMP) before any ad is requested.

Debug builds always use Google's test ad unit IDs. For real ads in release
builds, define these Actions secrets in the repository settings; without them
release builds also ship with test ads:

| Secret | Format |
| --- | --- |
| `ADMOB_APP_ID` | `ca-app-pub-XXXXXXXXXXXXXXXX~YYYYYYYYYY` |
| `ADMOB_BANNER_ID` | `ca-app-pub-XXXXXXXXXXXXXXXX/ZZZZZZZZZZ` |
| `ADMOB_INTERSTITIAL_ID` | `ca-app-pub-XXXXXXXXXXXXXXXX/ZZZZZZZZZZ` |
| `ADMOB_NATIVE_ID` | `ca-app-pub-XXXXXXXXXXXXXXXX/ZZZZZZZZZZ` |

Without `ADMOB_NATIVE_ID`, release builds do not show the native ad card at all
(no test ads).

The Play Store bundle (AAB) is produced on every build as an Actions artifact;
only the APK is attached to GitHub Releases.

## Play Store

Store graphics, listing texts and a step-by-step release guide (in Turkish) are
in [`store/PLAY_STORE.md`](store/PLAY_STORE.md). Privacy policy:
https://ziewenn.github.io/yakitrotam/privacy/

## Technical notes

Kotlin 2.3, Jetpack Compose with Material 3, target SDK 36, single Activity. The layers are
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
│   │   ├── EvPresets.kt            Bundled EV model presets
│   │   ├── TripModels.kt           Stops and trip summary
│   │   └── VehicleProfile.kt       Tank, consumption, range formulas
│   └── repository/
│       ├── Endpoints.kt            Server addresses and the remote settings file
│       ├── FuelPriceRepository.kt  Opet and Petrol Ofisi prices, province table, cache
│       ├── GasStationRepository.kt Station cache, fuel and brand filtering
│       ├── LocationService.kt      Photon search, Nominatim, device location
│       ├── OverpassStationSource.kt Corridor query and OSM tag parsing
│       ├── RouteRepository.kt      OSRM with an offline corridor fallback
│       └── TripPreferences.kt      Persists the last entered settings
├── domain/
│   ├── EvChargePlanner.kt          Charging stop plan for electric cars
│   └── FuelOptimizerEngine.kt      Range simulation and stop selection
├── notify/
│   └── PriceWatch.kt               Price change notification (WorkManager)
├── ui/
│   ├── components/                 Gauge, price strip, timeline, MapLibre maps
│   ├── screens/                    Planner and summary screens
│   ├── theme/
│   └── viewmodel/
└── util/
    ├── GeoUtils.kt                 Haversine, route projection, interpolation
    ├── Provinces.kt                Province centres, price region lookup for a point
    ├── TripShareText.kt            Shared plan text
    └── GoogleMapsLauncher.kt       Maps intent
```

## Refreshing the station lists

A GitHub Actions workflow in the site repository
(`ziewenn.github.io/.github/workflows/yakitrotam-stations.yml`) refreshes the
lists automatically: charging stations daily (EPDK allows about one call per
hour), fuel stations every Monday. If a script fails or the new list is shorter
than 90% of the old one, the old list stays published. To run it by hand:

```bash
python tools/build_chargers.py chargers.json
```

Push the output to the site repository as `yakitrotam/chargers.json`. The raw
response is saved as `chargers.json.raw.json`; when only prices change
(`tools/ev_prices.json`) the file can be rebuilt without calling the service:

```bash
python tools/build_chargers.py chargers.json --raw chargers.json.raw.json
```

## Known limitations

- Charging stations have no occupancy, fault or opening-hours data (EPDK does not
  publish it). Prices are per operator, not per station; membership discounts are
  not applied.

- The autogas price is parsed from a web page; if its layout changes the app
  falls back to an estimate until the next release.
- A stop's province is taken from the nearest province centre. Stations close to
  a border may get the neighbouring province's price; the difference is a few
  kuruş.
- Station quality depends on OSM. Stations without a brand or fuel tags show up
  as "Diğer" and score lower.
- Without a network connection the route falls back to a built-in motorway
  corridor, but the station list stays empty, so no plan is produced.
- Travel time is OSRM's driving time plus a break per stop; traffic is ignored.
