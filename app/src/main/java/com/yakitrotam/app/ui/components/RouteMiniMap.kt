package com.yakitrotam.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.yakitrotam.app.data.model.LatLng
import com.yakitrotam.app.data.model.TripPlanResult
import com.yakitrotam.app.ui.theme.AccentLime
import com.yakitrotam.app.ui.theme.DarkBackground
import com.yakitrotam.app.ui.theme.DarkBorder
import com.yakitrotam.app.ui.theme.DarkSurface
import com.yakitrotam.app.ui.theme.DarkSurfaceVariant
import com.yakitrotam.app.ui.theme.ReserveRed
import com.yakitrotam.app.ui.theme.SafeGreen
import com.yakitrotam.app.ui.theme.TextMuted
import com.yakitrotam.app.ui.theme.TextPrimary
import kotlin.math.cos

/**
 * Basit rota önizlemesi. Harita karosu indirmez (ücretli/ağ maliyetli olurdu);
 * sadece rota çizgisi ile durakların birbirine göre konumunu gösterir.
 */
@Composable
fun RouteMiniMap(
    tripResult: TripPlanResult,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(230.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.verticalGradient(listOf(DarkSurfaceVariant, DarkSurface))
            )
            .border(1.dp, DarkBorder, RoundedCornerShape(20.dp))
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 34.dp)) {
            val points = tripResult.routePoints
            if (points.size < 2) return@Canvas

            val allLats = points.map { it.latitude } + tripResult.stops.map { it.station.latitude }
            val allLngs = points.map { it.longitude } + tripResult.stops.map { it.station.longitude }

            val minLat = allLats.min()
            val maxLat = allLats.max()
            val minLng = allLngs.min()
            val maxLng = allLngs.max()

            // Boylamı enleme göre ölçekle, aksi halde Türkiye rotaları yatayda gerilmiş görünür.
            val lonScale = cos(Math.toRadians((minLat + maxLat) / 2.0))
            val spanX = ((maxLng - minLng) * lonScale).coerceAtLeast(1e-6)
            val spanY = (maxLat - minLat).coerceAtLeast(1e-6)

            // En-boy oranını koru, sığdır ve ortala.
            val scale = minOf(size.width / spanX, size.height / spanY)
            val offsetX = (size.width - spanX * scale) / 2.0
            val offsetY = (size.height - spanY * scale) / 2.0

            fun project(point: LatLng) = Offset(
                (offsetX + (point.longitude - minLng) * lonScale * scale).toFloat(),
                (offsetY + (maxLat - point.latitude) * scale).toFloat()
            )

            val routePath = Path().apply {
                val first = project(points.first())
                moveTo(first.x, first.y)
                for (i in 1 until points.size) {
                    val offset = project(points[i])
                    lineTo(offset.x, offset.y)
                }
            }

            drawPath(
                path = routePath,
                color = AccentLime.copy(alpha = 0.18f),
                style = Stroke(width = 14f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
            drawPath(
                path = routePath,
                color = AccentLime,
                style = Stroke(width = 4.5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )

            tripResult.stops.forEach { stop ->
                val center = project(stop.station.location)
                // Koyu marka renkleri (ör. Opet lacivert) koyu zeminde kaybolmasın diye
                // açık bir halka ile çevrelenir.
                drawCircle(color = Color.White, radius = 13f, center = center)
                drawCircle(color = Color(stop.station.brand.primaryColorHex), radius = 10f, center = center)
                drawCircle(color = Color.White, radius = 3.5f, center = center)
            }

            project(tripResult.origin.latLng).let {
                drawCircle(color = Color.White, radius = 12f, center = it)
                drawCircle(color = SafeGreen, radius = 9f, center = it)
            }
            project(tripResult.destination.latLng).let {
                drawCircle(color = Color.White, radius = 12f, center = it)
                drawCircle(color = ReserveRed, radius = 9f, center = it)
            }
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(12.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(DarkBackground.copy(alpha = 0.9f))
                .border(1.dp, DarkBorder, RoundedCornerShape(10.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            LegendDot(SafeGreen, "Kalkış")
            LegendDot(ReserveRed, "Varış")
            LegendDot(AccentLime, "${tripResult.stopsCount} durak")
        }

        Text(
            text = "© OpenStreetMap katkıcıları",
            style = MaterialTheme.typography.labelMedium,
            color = TextMuted,
            modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp)
        )
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(color))
        Text(label, style = MaterialTheme.typography.labelMedium, color = TextPrimary)
    }
}
