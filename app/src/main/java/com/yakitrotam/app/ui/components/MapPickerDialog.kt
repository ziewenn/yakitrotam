package com.yakitrotam.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.yakitrotam.app.data.model.CityLocation
import com.yakitrotam.app.data.model.LatLng
import com.yakitrotam.app.ui.theme.AccentLime
import com.yakitrotam.app.ui.theme.DarkBackground
import com.yakitrotam.app.ui.theme.DarkBorder
import com.yakitrotam.app.ui.theme.DarkSurface
import com.yakitrotam.app.ui.theme.TextMuted
import com.yakitrotam.app.ui.theme.TextPrimary
import com.yakitrotam.app.ui.theme.TextSecondary
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Tam ekran harita; kullanıcı haritayı kaydırıp ortadaki işareti istediği noktaya getirir.
 * Adres, sunucuyu gereksiz yormamak için yalnızca onaylanınca çözülür.
 */
@Composable
fun MapPickerDialog(
    title: String,
    initialCenter: LatLng?,
    onDescribePoint: suspend (LatLng) -> CityLocation,
    onPicked: (CityLocation) -> Unit,
    onDismiss: () -> Unit
) {
    var center by remember { mutableStateOf(initialCenter) }
    var isResolving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Box(modifier = Modifier.fillMaxSize().background(DarkBackground)) {
            PickerMap(
                initialCenter = initialCenter,
                onCenterChanged = { center = it },
                modifier = Modifier.fillMaxSize()
            )

            // İşaretin ucu tam ekranın ortasına, yani seçilen noktaya denk gelir.
            Icon(
                Icons.Default.Place,
                contentDescription = null,
                tint = AccentLime,
                modifier = Modifier.align(Alignment.Center).size(48.dp).offset(y = (-22).dp)
            )
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(Color.Black)
                    .border(1.5.dp, AccentLime, CircleShape)
            )

            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .background(DarkBackground.copy(alpha = 0.88f))
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Geri", tint = TextPrimary)
                }
                Column {
                    Text(title, style = MaterialTheme.typography.titleLarge, color = TextPrimary)
                    Text(
                        "Haritayı kaydırıp işareti konuma getir",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(16.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(DarkSurface)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column {
                    Text("Seçilen nokta", style = MaterialTheme.typography.labelMedium, color = TextMuted)
                    Text(
                        text = center?.let {
                            String.format(Locale.US, "%.5f, %.5f", it.latitude, it.longitude)
                        } ?: "Harita yükleniyor...",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary
                    )
                }
                Button(
                    onClick = {
                        val point = center ?: return@Button
                        isResolving = true
                        scope.launch {
                            val place = onDescribePoint(point)
                            isResolving = false
                            onPicked(place)
                        }
                    },
                    enabled = center != null && !isResolving,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(15.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentLime, contentColor = Color.Black)
                ) {
                    if (isResolving) {
                        CircularProgressIndicator(Modifier.size(18.dp), color = Color.Black, strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                        Text("Adres bulunuyor...", style = MaterialTheme.typography.titleMedium)
                    } else {
                        Text("Bu konumu kullan", style = MaterialTheme.typography.titleLarge)
                    }
                }
                Text(
                    MAP_ATTRIBUTION,
                    style = MaterialTheme.typography.labelMedium,
                    color = TextMuted,
                    modifier = Modifier.align(Alignment.End)
                )
            }
        }
    }
}
