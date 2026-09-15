package com.yakitrotam.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.yakitrotam.app.data.model.CityLocation
import com.yakitrotam.app.data.model.PlaceSource
import com.yakitrotam.app.data.model.PlaceSuggestion
import com.yakitrotam.app.ui.theme.DarkBackground
import com.yakitrotam.app.ui.theme.DarkBorder
import com.yakitrotam.app.ui.theme.DarkSurface
import com.yakitrotam.app.ui.theme.DarkSurfaceVariant
import com.yakitrotam.app.ui.theme.PrimaryBlue
import com.yakitrotam.app.ui.theme.TextMuted
import com.yakitrotam.app.ui.theme.TextPrimary
import com.yakitrotam.app.ui.theme.TextSecondary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun PlaceSearchDialog(
    title: String,
    isOriginSearch: Boolean,
    onSelectPlace: (CityLocation) -> Unit,
    onUseCurrentLocation: (() -> Unit)? = null,
    onSearchQueryChanged: suspend (String) -> List<PlaceSuggestion>,
    onResolvePlace: suspend (PlaceSuggestion) -> CityLocation?,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<PlaceSuggestion>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var resolvingId by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(searchQuery) {
        isSearching = true
        errorMessage = null
        if (searchQuery.isNotBlank()) delay(300)
        searchResults = onSearchQueryChanged(searchQuery)
        isSearching = false
    }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = DarkBackground) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(top = 8.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Geri", tint = TextPrimary)
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(title, style = MaterialTheme.typography.titleLarge, color = TextPrimary)
                        Text(
                            "Google Maps konumlarında ara",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    }
                }

                TextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                    placeholder = { Text("Adres, işletme veya konum ara", color = TextMuted) },
                    leadingIcon = { Icon(Icons.Default.Search, null, tint = PrimaryBlue) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Close, "Aramayı temizle", tint = TextSecondary)
                            }
                        }
                    },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = DarkSurfaceVariant,
                        unfocusedContainerColor = DarkSurfaceVariant,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        cursorColor = PrimaryBlue
                    ),
                    shape = RoundedCornerShape(18.dp),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { keyboardController?.hide() })
                )

                if (isSearching) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        color = PrimaryBlue,
                        trackColor = Color.Transparent
                    )
                } else {
                    Spacer(Modifier.height(4.dp))
                }

                if (isOriginSearch && onUseCurrentLocation != null) {
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp).clickable {
                            onUseCurrentLocation()
                            onDismiss()
                        },
                        color = PrimaryBlue.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier.size(40.dp).clip(CircleShape)
                                    .background(PrimaryBlue.copy(alpha = 0.18f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.MyLocation, null, tint = PrimaryBlue)
                            }
                            Column {
                                Text("Mevcut konumum", color = TextPrimary, fontWeight = FontWeight.SemiBold)
                                Text("GPS ile başlangıç noktası seç", color = TextSecondary, fontSize = 12.sp)
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (searchQuery.isBlank()) "ÖNERİLEN KONUMLAR" else "ARAMA SONUÇLARI",
                        style = MaterialTheme.typography.labelLarge,
                        color = TextMuted,
                        letterSpacing = 0.8.sp
                    )
                    if (searchResults.any { it.source == PlaceSource.GOOGLE }) {
                        Text("Powered by Google", color = TextSecondary, fontSize = 11.sp)
                    }
                }

                errorMessage?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(bottom = 8.dp))
                }

                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(searchResults, key = { it.id }) { place ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable(enabled = resolvingId == null) {
                                resolvingId = place.id
                                errorMessage = null
                                keyboardController?.hide()
                                scope.launch {
                                    val resolved = onResolvePlace(place)
                                    resolvingId = null
                                    if (resolved != null) {
                                        onSelectPlace(resolved)
                                        onDismiss()
                                    } else {
                                        errorMessage = "Konum ayrıntıları alınamadı. Lütfen tekrar deneyin."
                                    }
                                }
                            }.padding(vertical = 13.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Box(
                                modifier = Modifier.size(42.dp).clip(CircleShape).background(DarkSurfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                if (resolvingId == place.id) {
                                    CircularProgressIndicator(Modifier.size(20.dp), color = PrimaryBlue, strokeWidth = 2.dp)
                                } else {
                                    Icon(
                                        if (place.source == PlaceSource.LOCAL) Icons.Default.History else Icons.Default.LocationOn,
                                        null,
                                        tint = PrimaryBlue,
                                        modifier = Modifier.size(21.dp)
                                    )
                                }
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    place.title,
                                    color = TextPrimary,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (place.subtitle.isNotBlank()) {
                                    Text(
                                        place.subtitle,
                                        color = TextSecondary,
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                        HorizontalDivider(color = DarkBorder.copy(alpha = 0.55f))
                    }

                    if (!isSearching && searchResults.isEmpty()) {
                        item {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(Icons.Default.Search, null, tint = TextMuted, modifier = Modifier.size(36.dp))
                                Spacer(Modifier.height(12.dp))
                                Text("Eşleşen konum bulunamadı", color = TextSecondary)
                                Text("Daha farklı bir arama deneyin", color = TextMuted, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}
