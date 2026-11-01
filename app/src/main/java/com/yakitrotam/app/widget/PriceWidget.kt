package com.yakitrotam.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.widget.RemoteViews
import com.yakitrotam.app.MainActivity
import com.yakitrotam.app.R
import com.yakitrotam.app.data.model.FuelType
import com.yakitrotam.app.data.repository.FuelPriceRepository
import com.yakitrotam.app.data.repository.TripPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Ana ekran widget'ı: kalkış ilinin benzin, motorin ve otogaz fiyatı. Kullanıcının yakıtı
 * kalın yazılır. Dokununca uygulama açılır.
 */
class PriceWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val views = buildViews(context)
                ids.forEach { manager.updateAppWidget(it, views) }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        /** Uygulama fiyatları yenileyince widget da güncellensin. */
        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, PriceWidget::class.java))
            if (ids.isEmpty()) return
            context.sendBroadcast(
                Intent(context, PriceWidget::class.java)
                    .setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            )
        }

        private suspend fun buildViews(context: Context): RemoteViews {
            val saved = TripPreferences(context).load()
            val mine = saved.vehicleProfile.fuelType.takeUnless { it.isElectric }
                ?: saved.otherVehicle?.fuelType
            val snapshot = FuelPriceRepository().getPrices(saved.origin?.latLng)
            val turkish = Locale.forLanguageTag("tr")

            fun label(type: FuelType, name: String): CharSequence {
                val estimated = if (snapshot.isEstimated(type)) "~" else ""
                val text = SpannableString("$name\n$estimated${String.format(turkish, "%.2f ₺", snapshot.priceFor(type))}")
                if (type == mine) {
                    text.setSpan(StyleSpan(Typeface.BOLD), 0, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    text.setSpan(ForegroundColorSpan(0xFF0B7A6C.toInt()), 0, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                return text
            }

            val open = PendingIntent.getActivity(
                context, 0, Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            return RemoteViews(context.packageName, R.layout.widget_prices).apply {
                setTextViewText(
                    R.id.widget_region,
                    if (snapshot.isFallback) "YakıtRotam · fiyatlar alınamadı" else "YakıtRotam · ${snapshot.regionName}"
                )
                setTextViewText(R.id.widget_benzin, label(FuelType.BENZIN, "Benzin"))
                setTextViewText(R.id.widget_dizel, label(FuelType.DIZEL, "Motorin"))
                setTextViewText(R.id.widget_lpg, label(FuelType.LPG, "LPG"))
                setOnClickPendingIntent(R.id.widget_root, open)
            }
        }
    }
}
