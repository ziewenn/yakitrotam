package com.yakitrotam.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yakitrotam.app.data.model.LatLng
import com.yakitrotam.app.data.model.TripPlanResult
import com.yakitrotam.app.ui.theme.*

@Composable
fun RouteMiniMap(
    tripResult: TripPlanResult,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(200.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(DarkSurfaceVariant)
            .border(1.dp, DarkBorder, RoundedCornerShape(16.dp))
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding(24.dp)) {
            val points = tripResult.routePoints
            if (points.isEmpty()) return@Canvas

            // Bounding box hesapla
            var minLat = points.minOf { it.latitude }
            var maxLat = points.maxOf { it.latitude }
            var minLng = points.minOf { it.longitude }
            var maxLng = points.maxOf { it.longitude }

            // Durakları da sınırlara dahil et
            tripResult.stops.forEach { stop ->
                minLat = minOf(minLat, stop.station.latitude)
                maxLat = maxOf(maxLat, stop.station.latitude)
                minLng = minOf(minLng, stop.station.longitude)
                maxLng = maxOf(maxLng, stop.station.longitude)
            }

            val latRange = (maxLat - minLat).coerceAtLeast(0.0001)
            val lngRange = (maxLng - minLng).coerceAtLeast(0.0001)

            val w = size.width
            val h = size.height

            fun project(point: LatLng): Offset {
                // Enlem (Lat) yukarı doğru arttığı için Y eksenini ters çevir
                val x = ((point.longitude - minLng) / lngRange * w).toFloat()
                val y = ((maxLat - point.latitude) / latRange * h).toFloat()
                return Offset(x, y)
            }

            // 1. Rota Çizgisi (Polyline)
            val routePath = Path()
            val firstOffset = project(points.first())
            routePath.moveTo(firstOffset.x, firstOffset.y)

            for (i in 1 until points.size) {
                val offset = project(points[i])
                routePath.lineTo(offset.x, offset.y)
            }

            // Glow / Alt hat
            drawPath(
                path = routePath,
                color = PrimaryBlue.copy(alpha = 0.3f),
                style = Stroke(width = 10f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )

            // Ana hat
            drawPath(
                path = routePath,
                color = PrimaryBlue,
                style = Stroke(width = 4f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )

            // 2. Akaryakıt Durakları Noktaları
            tripResult.stops.forEach { stop ->
                val stopOffset = project(stop.station.location)
                val brandColor = Color(stop.station.brand.primaryColorHex)

                // Dış halka
                drawCircle(
                    color = Color.Black.copy(alpha = 0.6f),
                    radius = 12f,
                    center = stopOffset
                )
                // Marka rengi göbeği
                drawCircle(
                    color = brandColor,
                    radius = 9f,
                    center = stopOffset
                )
                // İç beyaz nokta
                drawCircle(
                    color = Color.White,
                    radius = 4f,
                    center = stopOffset
                )
            }

            // 3. Başlangıç Noktası (Yeşil Pin)
            val startOffset = project(tripResult.origin.latLng)
            drawCircle(color = SafeGreen, radius = 9f, center = startOffset)
            drawCircle(color = Color.White, radius = 4f, center = startOffset)

            // 4. Varış Noktası (Kırmızı / Bitiş Bayrağı Noktası)
            val endOffset = project(tripResult.destination.latLng)
            drawCircle(color = ReserveRed, radius = 9f, center = endOffset)
            drawCircle(color = Color.White, radius = 4f, center = endOffset)
        }

        // Bilgilendirme Rozeti
        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(12.dp)
                .background(DarkBackground.copy(alpha = 0.85f), RoundedCornerShape(8.dp))
                .border(0.5.dp, DarkBorder, RoundedCornerShape(8.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(SafeGreen)
            )
            Text(
                text = "${tripResult.origin.name.substringBefore(" ")} → ${tripResult.destination.name.substringBefore(" ")}",
                style = MaterialTheme.typography.labelLarge,
                color = TextPrimary,
                fontSize = 11.sp
            )
            Text(
                text = "• ${tripResult.stopsCount} Durak",
                style = MaterialTheme.typography.labelLarge,
                color = FuelAmber,
                fontSize = 11.sp
            )
        }
    }
}
