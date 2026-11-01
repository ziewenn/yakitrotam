package com.yakitrotam.app.ui.theme

import androidx.compose.ui.graphics.Color

// Açık nane/camgöbeği tema. Uygulama yola çıkmadan önce kullanılıp Haritalar'a devrettiği
// için gündüz okunaklığı öncelikli. Kontrast oranları WCAG formülüyle beyaz/zemin üzerinde
// hesaplandı; hepsi metin için en az 4.5:1.

// --- Yüzeyler -------------------------------------------------------------
val AppBackground = Color(0xFFF3F8F6)
val CardSurface = Color(0xFFFFFFFF)
val SurfaceMuted = Color(0xFFE3F2EE)   // seçili çip, ikincil zemin
val BorderColor = Color(0xFFD3E3DD)    // yalnızca ince ayırıcı çizgiler için
val ElevatedSurface = Color(0xFFEAF3F0)

// --- Vurgu ----------------------------------------------------------------
// Tek vurgu rengi; ana eylem, seçili durum ve rota çizgisi. Üzerindeki yazı beyaz (5.2:1).
val Accent = Color(0xFF0B7A6C)
val AccentDim = Color(0xFF0A6458)
val AccentSoft = Color(0xFFCFEDE6)
val OnAccent = Color(0xFFFFFFFF)
val AccentViolet = Color(0xFF5B43D6)

// --- Durum renkleri (yakıt/batarya seviyesi) ------------------------------
val FuelAmber = Color(0xFFB25E00)
val ReserveRed = Color(0xFFC62828)
val SafeGreen = Color(0xFF13803F)

// --- Metin ------------------------------------------------------------------
val TextPrimary = Color(0xFF132320)
val TextSecondary = Color(0xFF4B5D59)
val TextMuted = Color(0xFF5E6F69)
