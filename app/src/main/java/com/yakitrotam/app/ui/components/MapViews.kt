package com.yakitrotam.app.ui.components

import android.annotation.SuppressLint
import android.view.MotionEvent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import org.maplibre.android.style.sources.GeoJsonOptions
import com.yakitrotam.app.data.model.GasStation
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.doOnLayout
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.yakitrotam.app.data.model.LatLng
import com.yakitrotam.app.data.repository.Endpoints
import com.yakitrotam.app.data.model.TripPlanResult
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import org.maplibre.android.geometry.LatLng as MapLatLng

/**
 * OpenFreeMap: OpenStreetMap tabanlı, anahtarsız ve ticari kullanıma açık ücretsiz
 * harita karoları. Google Maps SDK ücretli olduğu için kullanılmıyor.
 */

private val TURKEY_CENTER = MapLatLng(39.0, 35.0)

/**
 * MapView'i Compose yaşam döngüsüne bağlar. Liste ve diyalog içinde düzgün çizilsin
 * diye SurfaceView yerine TextureView kullanılır.
 */
@Composable
private fun rememberMapView(): MapView {
    val context = LocalContext.current
    val mapView = remember {
        MapLibre.getInstance(context)
        MapView(context, MapLibreMapOptions.createFromAttributes(context).textureMode(true))
            .apply { onCreate(null) }
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, mapView) {
        var started = false
        var resumed = false
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> { mapView.onStart(); started = true }
                Lifecycle.Event.ON_RESUME -> { mapView.onResume(); resumed = true }
                Lifecycle.Event.ON_PAUSE -> { mapView.onPause(); resumed = false }
                Lifecycle.Event.ON_STOP -> { mapView.onStop(); started = false }
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            if (resumed) mapView.onPause()
            if (started) mapView.onStop()
            mapView.onDestroy()
        }
    }
    return mapView
}

/** Harita kaydırılırken üstteki kaydırılabilir liste dokunuşu çalmasın. */
@SuppressLint("ClickableViewAccessibility")
private fun MapView.keepGesturesInside() {
    setOnTouchListener { view, event ->
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            view.parent?.requestDisallowInterceptTouchEvent(true)
        }
        false
    }
}

private fun MapLibreMap.basicSettings() {
    uiSettings.isRotateGesturesEnabled = false
    uiSettings.isTiltGesturesEnabled = false
    uiSettings.isCompassEnabled = false
    // Kaynak gösterimi haritanın üzerinde metin olarak yapılıyor (MapAttribution).
    uiSettings.isLogoEnabled = false
    uiSettings.isAttributionEnabled = false
}

private fun LatLng.toPoint(): Point = Point.fromLngLat(longitude, latitude)

private fun colorHex(argb: Long): String = String.format("#%06X", argb and 0xFFFFFF)

/** Rota çizgisi, kalkış/varış ve numaralı yakıt durakları gerçek harita üzerinde. */
@Composable
fun RouteMap(
    tripResult: TripPlanResult,
    modifier: Modifier = Modifier
) {
    val mapView = rememberMapView()

    AndroidView(
        modifier = modifier,
        factory = {
            mapView.keepGesturesInside()
            mapView.getMapAsync { map ->
                map.basicSettings()
                map.setStyle(Style.Builder().fromUri(Endpoints.mapStyleLight))
            }
            mapView
        },
        // Stil bir kez yüklenir; plan değişince (ör. alternatif istasyon seçilince)
        // yalnızca rota ve durak katmanları yeniden çizilir.
        update = { view ->
            val density = view.resources.displayMetrics.density
            view.getMapAsync { map -> map.getStyle { style -> drawTrip(view, map, style, tripResult, density) } }
        }
    )
}

private fun drawTrip(view: MapView, map: MapLibreMap, style: Style, trip: TripPlanResult, density: Float) {
    listOf("stop-labels", "points", "route").forEach { style.getLayer(it)?.let(style::removeLayer) }
    listOf("points", "route").forEach { style.getSource(it)?.let(style::removeSource) }

    if (trip.routePoints.size >= 2) {
        style.addSource(
            GeoJsonSource("route", LineString.fromLngLats(trip.routePoints.map { it.toPoint() }))
        )
        style.addLayer(
            LineLayer("route", "route").withProperties(
                PropertyFactory.lineColor("#0B7A6C"),
                PropertyFactory.lineWidth(5f),
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND)
            )
        )
    }

    val features = buildList {
        add(pointFeature(trip.origin.latLng, "#13803F", ""))
        add(pointFeature(trip.destination.latLng, "#C62828", ""))
        trip.stops.forEach { stop ->
            add(
                pointFeature(
                    stop.station.location,
                    colorHex(stop.station.brand.primaryColorHex),
                    stop.stopIndex.toString()
                )
            )
        }
    }
    style.addSource(GeoJsonSource("points", FeatureCollection.fromFeatures(features)))
    style.addLayer(
        CircleLayer("points", "points").withProperties(
            PropertyFactory.circleColor(Expression.get("color")),
            PropertyFactory.circleRadius(
                Expression.switchCase(
                    Expression.eq(Expression.get("label"), Expression.literal("")),
                    Expression.literal(7f),
                    Expression.literal(11f)
                )
            ),
            PropertyFactory.circleStrokeColor("#FFFFFF"),
            PropertyFactory.circleStrokeWidth(2.5f)
        )
    )
    style.addLayer(
        SymbolLayer("stop-labels", "points").withProperties(
            PropertyFactory.textField(Expression.get("label")),
            PropertyFactory.textFont(arrayOf("Noto Sans Bold")),
            PropertyFactory.textSize(12f),
            PropertyFactory.textColor("#FFFFFF"),
            PropertyFactory.textHaloColor("#000000"),
            PropertyFactory.textHaloWidth(1.2f),
            PropertyFactory.textAllowOverlap(true),
            PropertyFactory.textIgnorePlacement(true)
        )
    )

    val allPoints = trip.routePoints + trip.stops.map { it.station.location }
    if (allPoints.size >= 2) {
        val bounds = LatLngBounds.Builder()
            .includes(allPoints.map { MapLatLng(it.latitude, it.longitude) })
            .build()
        // Altta kaynak yazısı var; işaretler onun altında kalmasın.
        fun dp(value: Int) = (value * density).toInt()
        // Stil önbellekten hızlı yüklenirse buraya harita ölçülmeden gelinir; sıfır boyutla
        // hesaplanan kamera rotayı alakasız bir bölgeye taşır. Yerleşim bitince uygulanır.
        view.doOnLayout {
            map.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, dp(32), dp(32), dp(32), dp(40)))
        }
    }
}

private fun pointFeature(location: LatLng, color: String, label: String): Feature =
    Feature.fromGeometry(location.toPoint()).apply {
        addStringProperty("color", color)
        addStringProperty("label", label)
    }

/**
 * Haritayı kaydırarak nokta seçme. Seçilen nokta ekranın ortasındaki imleçtir;
 * [onCenterChanged] harita her durduğunda merkez koordinatıyla çağrılır.
 */
@Composable
fun PickerMap(
    initialCenter: LatLng?,
    onCenterChanged: (LatLng) -> Unit,
    modifier: Modifier = Modifier
) {
    val mapView = rememberMapView()

    AndroidView(
        modifier = modifier,
        factory = {
            mapView.getMapAsync { map ->
                map.basicSettings()
                map.cameraPosition = CameraPosition.Builder()
                    .target(initialCenter?.let { MapLatLng(it.latitude, it.longitude) } ?: TURKEY_CENTER)
                    .zoom(if (initialCenter != null) 13.0 else 5.2)
                    .build()
                map.setStyle(Style.Builder().fromUri(Endpoints.mapStyleLight))
                map.addOnCameraIdleListener {
                    map.cameraPosition.target?.let { onCenterChanged(LatLng(it.latitude, it.longitude)) }
                }
            }
            mapView
        }
    )
}

/**
 * Harita ekranı: istasyonlar kümelenmiş noktalar olarak (binlerce istasyon tek katmanda,
 * MapLibre kendisi kümeler). Noktaya dokununca [onStationClick], kümeye dokununca yakınlaşma.
 * [focus] değişince kamera oraya kayar; [onCenterChanged] harita her durduğunda çağrılır.
 */
@Composable
fun StationsMap(
    stations: List<GasStation>,
    electric: Boolean,
    focus: LatLng,
    focusZoom: Double,
    selectedId: String?,
    onCenterChanged: (LatLng) -> Unit,
    onStationClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val mapView = rememberMapView()
    val latestCenter by rememberUpdatedState(onCenterChanged)
    val latestClick by rememberUpdatedState(onStationClick)
    // Aynı listeyi ve aynı odağı tekrar tekrar haritaya göndermemek için son uygulananlar.
    val applied = remember { arrayOfNulls<Any>(3) }

    AndroidView(
        modifier = modifier,
        factory = {
            mapView.keepGesturesInside()
            mapView.getMapAsync { map ->
                map.basicSettings()
                map.cameraPosition = CameraPosition.Builder()
                    .target(MapLatLng(focus.latitude, focus.longitude)).zoom(focusZoom).build()
                applied[1] = focus
                map.setStyle(Style.Builder().fromUri(Endpoints.mapStyleLight)) { style -> addStationLayers(style) }
                map.addOnCameraIdleListener {
                    map.cameraPosition.target?.let { latestCenter(LatLng(it.latitude, it.longitude)) }
                }
                map.addOnMapClickListener { point ->
                    val screen = map.projection.toScreenLocation(point)
                    val station = map.queryRenderedFeatures(screen, "station-points").firstOrNull()
                    val cluster = map.queryRenderedFeatures(screen, "station-clusters").firstOrNull()
                    when {
                        station != null -> { latestClick(station.getStringProperty("id")); true }
                        cluster != null -> {
                            map.animateCamera(CameraUpdateFactory.newLatLngZoom(point, map.cameraPosition.zoom + 2))
                            true
                        }
                        else -> false
                    }
                }
            }
            mapView
        },
        update = { view ->
            view.getMapAsync { map ->
                map.getStyle { style ->
                    if (applied[0] !== stations || applied[2] != selectedId) {
                        applied[0] = stations
                        applied[2] = selectedId
                        (style.getSource("stations") as? GeoJsonSource)?.setGeoJson(
                            FeatureCollection.fromFeatures(stations.map { stationFeature(it, electric, it.id == selectedId) })
                        )
                    }
                }
                if (applied[1] != focus) {
                    applied[1] = focus
                    map.animateCamera(
                        CameraUpdateFactory.newLatLngZoom(MapLatLng(focus.latitude, focus.longitude), focusZoom), 600
                    )
                }
            }
        }
    )
}

private fun stationFeature(station: GasStation, electric: Boolean, selected: Boolean): Feature =
    Feature.fromGeometry(Point.fromLngLat(station.longitude, station.latitude)).apply {
        addStringProperty("id", station.id)
        val color = when {
            selected -> "#C62828"
            !electric -> colorHex(station.brand.primaryColorHex)
            (station.ccsKw ?: station.chademoKw ?: 0.0) >= 150.0 -> "#0A6458"
            else -> "#3E9C8F"
        }
        addStringProperty("color", color)
        addNumberProperty("size", if (selected) 9 else 6)
    }

private fun addStationLayers(style: Style) {
    style.addSource(
        GeoJsonSource(
            "stations",
            FeatureCollection.fromFeatures(emptyList<Feature>()),
            GeoJsonOptions().withCluster(true).withClusterRadius(46).withClusterMaxZoom(11)
        )
    )
    style.addLayer(
        CircleLayer("station-clusters", "stations").withProperties(
            PropertyFactory.circleColor("#0B7A6C"),
            PropertyFactory.circleOpacity(0.85f),
            PropertyFactory.circleRadius(
                Expression.step(
                    Expression.get("point_count"), Expression.literal(14f),
                    Expression.stop(20, 18f), Expression.stop(100, 23f)
                )
            ),
            PropertyFactory.circleStrokeColor("#FFFFFF"),
            PropertyFactory.circleStrokeWidth(2f)
        ).withFilter(Expression.has("point_count"))
    )
    style.addLayer(
        SymbolLayer("station-cluster-count", "stations").withProperties(
            PropertyFactory.textField(Expression.toString(Expression.get("point_count_abbreviated"))),
            PropertyFactory.textFont(arrayOf("Noto Sans Bold")),
            PropertyFactory.textSize(12f),
            PropertyFactory.textColor("#FFFFFF"),
            PropertyFactory.textAllowOverlap(true),
            PropertyFactory.textIgnorePlacement(true)
        ).withFilter(Expression.has("point_count"))
    )
    style.addLayer(
        CircleLayer("station-points", "stations").withProperties(
            PropertyFactory.circleColor(Expression.get("color")),
            PropertyFactory.circleRadius(Expression.toNumber(Expression.get("size"))),
            PropertyFactory.circleStrokeColor("#FFFFFF"),
            PropertyFactory.circleStrokeWidth(2f)
        ).withFilter(Expression.not(Expression.has("point_count")))
    )
}

/** OpenFreeMap ve OpenStreetMap lisanslarının istediği görünür kaynak gösterimi. */
const val MAP_ATTRIBUTION = "© OpenFreeMap © OpenMapTiles © OpenStreetMap"
