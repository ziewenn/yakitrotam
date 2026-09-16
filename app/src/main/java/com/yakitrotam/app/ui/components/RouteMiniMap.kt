package com.yakitrotam.app.ui.components

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.yakitrotam.app.data.model.TripPlanResult
import com.yakitrotam.app.ui.theme.AccentLime
import com.yakitrotam.app.ui.theme.DarkBackground
import com.yakitrotam.app.ui.theme.DarkBorder
import com.yakitrotam.app.ui.theme.DarkSurfaceVariant
import com.yakitrotam.app.ui.theme.ReserveRed
import com.yakitrotam.app.ui.theme.SafeGreen
import com.yakitrotam.app.ui.theme.TextPrimary
import com.yakitrotam.app.ui.theme.TextSecondary

/** Özet ekranındaki harita kartı: gerçek harita üzerinde rota ve numaralı duraklar. */
@Composable
fun RouteMiniMap(
    tripResult: TripPlanResult,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(280.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(DarkSurfaceVariant)
            .border(1.dp, DarkBorder, RoundedCornerShape(20.dp))
    ) {
        RouteMap(tripResult = tripResult, modifier = Modifier.fillMaxSize())

        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(DarkBackground.copy(alpha = 0.85f))
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
            text = MAP_ATTRIBUTION,
            style = MaterialTheme.typography.labelMedium,
            color = TextSecondary,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .background(DarkBackground.copy(alpha = 0.7f), RoundedCornerShape(topStart = 8.dp))
                .padding(horizontal = 8.dp, vertical = 3.dp)
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
