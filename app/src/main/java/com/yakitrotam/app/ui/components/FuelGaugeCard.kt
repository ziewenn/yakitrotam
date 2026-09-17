package com.yakitrotam.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yakitrotam.app.data.model.VehicleProfile
import com.yakitrotam.app.ui.theme.DarkSurfaceVariant
import com.yakitrotam.app.ui.theme.FuelAmber
import com.yakitrotam.app.ui.theme.ReserveRed
import com.yakitrotam.app.ui.theme.SafeGreen
import com.yakitrotam.app.ui.theme.TextMuted
import com.yakitrotam.app.ui.theme.TextPrimary
import com.yakitrotam.app.ui.theme.TextSecondary
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private const val GAUGE_START_ANGLE = 150f
private const val GAUGE_SWEEP = 240f
private const val MIN_LEVEL = 5f

/** Planlama ekranının merkezi: "depoda ne kadar yakıt var?" sorusu ve sonucu olan menzil. */
@Composable
fun FuelLevelCard(
    vehicleProfile: VehicleProfile,
    onLevelChange: (Double) -> Unit,
    modifier: Modifier = Modifier
) {
    val percent = vehicleProfile.currentLevelPercent.toFloat()
    val levelColor = when {
        percent <= vehicleProfile.reserveThresholdPercent -> ReserveRed
        percent <= 30f -> FuelAmber
        else -> SafeGreen
    }

    SurfaceCard(modifier = modifier, spacing = 4.dp) {
        Text("Depoda ne kadar yakıt var?", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
        Text(
            "Göstergeyi aracındaki gibi ayarla",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
        )

        FuelGauge(
            percent = percent,
            reservePercent = vehicleProfile.reserveThresholdPercent.toFloat(),
            color = levelColor,
            onPercentChange = { onLevelChange(it.toDouble()) },
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(top = 8.dp)
                .size(width = 250.dp, height = 190.dp)
        ) {
            Text("%${percent.roundToInt()}", style = MaterialTheme.typography.displaySmall, color = levelColor)
            Text(
                "${formatDecimal(vehicleProfile.currentFuelLiters)} L",
                style = MaterialTheme.typography.bodyLarge,
                color = TextSecondary
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            StepButton(Icons.Default.Remove, "Azalt") { onLevelChange((percent - 5f).coerceAtLeast(MIN_LEVEL).toDouble()) }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "~${vehicleProfile.calculateCurrentSafeRangeKm().toInt()} km",
                    style = MaterialTheme.typography.headlineMedium,
                    color = TextPrimary
                )
                Text(
                    "rezerve girmeden menzil",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMuted
                )
            }
            StepButton(Icons.Default.Add, "Artır") { onLevelChange((percent + 5f).coerceAtMost(100f).toDouble()) }
        }
    }
}

@Composable
private fun StepButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(44.dp).clip(CircleShape).background(DarkSurfaceVariant)
    ) {
        Icon(icon, contentDescription = label, tint = TextPrimary)
    }
}

/**
 * Araç göstergesi biçiminde 240° yakıt kadranı. Kadrana dokunarak ya da tutamağı
 * sürükleyerek seviye ayarlanır. Rezerv bölgesi kırmızı taralıdır; E ve F uçları ile
 * çeyrek çentikleri gerçek gösterge panelindeki okumayı kolaylaştırır.
 */
@Composable
fun FuelGauge(
    percent: Float,
    reservePercent: Float,
    color: Color,
    onPercentChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    center: @Composable () -> Unit
) {
    val animated by animateFloatAsState(percent.coerceIn(0f, 100f), tween(180), label = "fuelLevel")
    val currentOnChange by rememberUpdatedState(onPercentChange)
    val textMeasurer = rememberTextMeasurer()
    val endLabelStyle = TextStyle(color = TextMuted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)

    Box(
        modifier = modifier
            .semantics {
                contentDescription = "Yakıt seviyesi"
                progressBarRangeInfo = ProgressBarRangeInfo(percent, 0f..100f)
            }
            .pointerInput(Unit) {
                // Dokunma ve sürükleme aynı hesabı kullanır: parmağın kadran merkezine göre açısı.
                fun update(position: Offset) {
                    val radius = (size.width - 22.dp.toPx()) / 2f
                    val dx = position.x - size.width / 2f
                    val dy = position.y - (11.dp.toPx() + radius)
                    val angle = (Math.toDegrees(atan2(dy, dx).toDouble()).toFloat() + 360f) % 360f
                    val relative = (angle - GAUGE_START_ANGLE + 360f) % 360f
                    // Kadranın altındaki boşlukta kalan dokunuş en yakın uca yapışır.
                    val clamped = if (relative > GAUGE_SWEEP) {
                        if (relative > GAUGE_SWEEP + (360f - GAUGE_SWEEP) / 2f) 0f else GAUGE_SWEEP
                    } else relative
                    currentOnChange((clamped / GAUGE_SWEEP * 100f).roundToInt().toFloat().coerceIn(MIN_LEVEL, 100f))
                }
                awaitEachGesture {
                    val down = awaitFirstDown()
                    update(down.position)
                    drag(down.id) { change ->
                        update(change.position)
                        change.consume()
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = 16.dp.toPx()
            val inset = 11.dp.toPx()
            val diameter = size.width - inset * 2
            val radius = diameter / 2f
            val arcTopLeft = Offset(inset, inset)
            val arcSize = Size(diameter, diameter)
            val arcCenter = Offset(size.width / 2f, inset + radius)

            fun pointAt(levelPercent: Float, distance: Float): Offset {
                val radians = Math.toRadians((GAUGE_START_ANGLE + GAUGE_SWEEP * levelPercent / 100f).toDouble())
                return Offset(
                    arcCenter.x + distance * cos(radians).toFloat(),
                    arcCenter.y + distance * sin(radians).toFloat()
                )
            }

            drawArc(DarkSurfaceVariant, GAUGE_START_ANGLE, GAUGE_SWEEP, false, arcTopLeft, arcSize,
                style = Stroke(stroke, cap = StrokeCap.Round))
            drawArc(color, GAUGE_START_ANGLE, GAUGE_SWEEP * animated / 100f, false, arcTopLeft, arcSize,
                style = Stroke(stroke, cap = StrokeCap.Round))

            // Rezerv bölgesi: gerçek göstergelerdeki kırmızı alan gibi, seviye ne olursa olsun görünür.
            val reserveInset = inset + stroke / 2f + 4.dp.toPx()
            drawArc(
                color = ReserveRed,
                startAngle = GAUGE_START_ANGLE,
                sweepAngle = GAUGE_SWEEP * reservePercent / 100f,
                useCenter = false,
                topLeft = Offset(reserveInset, reserveInset),
                size = Size(size.width - reserveInset * 2, size.width - reserveInset * 2),
                style = Stroke(3.dp.toPx(), cap = StrokeCap.Round)
            )

            // Çeyrek çentikleri kadranın iç tarafında.
            listOf(0f, 25f, 50f, 75f, 100f).forEach { tick ->
                val long = tick == 0f || tick == 50f || tick == 100f
                drawLine(
                    color = TextMuted,
                    start = pointAt(tick, radius - stroke / 2f - 5.dp.toPx()),
                    end = pointAt(tick, radius - stroke / 2f - (if (long) 14 else 10).dp.toPx()),
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
            listOf(0f to "E", 100f to "F").forEach { (tick, label) ->
                val layout = textMeasurer.measure(label, endLabelStyle)
                val position = pointAt(tick, radius - stroke / 2f - 28.dp.toPx())
                drawText(layout, topLeft = Offset(position.x - layout.size.width / 2f, position.y - layout.size.height / 2f))
            }

            // Sürüklenebilir tutamak.
            val handle = pointAt(animated, radius)
            drawCircle(Color.Black.copy(alpha = 0.35f), radius = 14.dp.toPx(), center = handle)
            drawCircle(Color.White, radius = 11.dp.toPx(), center = handle)
            drawCircle(color, radius = 6.dp.toPx(), center = handle)
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(top = 18.dp)
        ) { center() }
    }
}
