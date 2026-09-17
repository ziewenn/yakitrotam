package com.yakitrotam.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.yakitrotam.app.data.model.TripPlanResult
import com.yakitrotam.app.ui.theme.DarkBackground
import com.yakitrotam.app.ui.theme.DarkSurfaceVariant
import com.yakitrotam.app.ui.theme.TextSecondary

/** Özet ekranındaki harita: gerçek harita üzerinde rota ve numaralı duraklar. */
@Composable
fun RouteMiniMap(
    tripResult: TripPlanResult,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(250.dp)
            .clip(CardShape)
            .background(DarkSurfaceVariant)
    ) {
        RouteMap(tripResult = tripResult, modifier = Modifier.fillMaxSize())

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
