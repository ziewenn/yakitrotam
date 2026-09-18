package com.yakitrotam.app.notify

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.yakitrotam.app.MainActivity
import com.yakitrotam.app.R
import com.yakitrotam.app.data.model.FuelType
import com.yakitrotam.app.data.repository.FuelPriceRepository
import com.yakitrotam.app.data.repository.TripPreferences
import com.yakitrotam.app.util.Provinces
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.abs

data class PriceNotice(val title: String, val text: String)

/**
 * İki fiyat arasındaki fark bildirime değiyorsa metnini üretir. Kaynaklar arası birkaç kuruşluk
 * yuvarlama oynaması bildirim sayılmaz.
 */
fun priceChangeNotice(fuelType: FuelType, provinceName: String, oldPrice: Double, newPrice: Double): PriceNotice? {
    val difference = newPrice - oldPrice
    if (abs(difference) < 0.05) return null
    val turkish = Locale.forLanguageTag("tr")
    fun money(value: Double) = String.format(turkish, "%.2f ₺", value)
    val sign = if (difference > 0) "+" else "−"
    return PriceNotice(
        title = "${fuelType.shortName()} fiyatı ${if (difference > 0) "arttı" else "düştü"}",
        text = "$provinceName: ${money(oldPrice)} → ${money(newPrice)} ($sign${money(abs(difference))}/L)"
    )
}

/** Kullanıcının yakıt türünün kalkış ilindeki fiyatı değişince bildirim gönderen arka plan takibi. */
object PriceWatch {
    private const val WORK_NAME = "price-watch"
    private const val CHANNEL_ID = "price_changes"
    private const val PREFS = "price_watch"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_WATCHED = "watched"
    private const val KEY_PRICE = "price"
    private const val NOTIFICATION_ID = 1

    /** Sistem ayarlarından bildirim kapatıldıysa takip açık görünmez. */
    fun isEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ENABLED, false) &&
            NotificationManagerCompat.from(context).areNotificationsEnabled()

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
        val workManager = WorkManager.getInstance(context)
        if (!enabled) {
            workManager.cancelUniqueWork(WORK_NAME)
            return
        }
        // İlk çalışma hemen yapılır ve yalnızca karşılaştırma tabanını kaydeder.
        val request = PeriodicWorkRequestBuilder<PriceWatchWorker>(6, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        workManager.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    internal suspend fun check(context: Context): Boolean {
        val saved = TripPreferences(context).load()
        val fuelType = saved.vehicleProfile.fuelType
        val province = saved.origin?.latLng?.let(Provinces::nearest)
            ?: Provinces.all.first { it.name == "İstanbul Anadolu" }
        val table = FuelPriceRepository().getTable() ?: return false
        // Tahmini fiyat için bildirim gönderilmez; kaynak o an yoksa bir sonraki kontrole kalır.
        val price = table.pricesFor(province)?.livePrice(fuelType) ?: return true

        val prefs = prefs(context)
        val watched = "${province.key}:${fuelType.name}"
        if (prefs.getString(KEY_WATCHED, null) == watched) {
            priceChangeNotice(fuelType, province.name, prefs.getFloat(KEY_PRICE, 0f).toDouble(), price)
                ?.let { notify(context, it) }
        }
        prefs.edit().putString(KEY_WATCHED, watched).putFloat(KEY_PRICE, price.toFloat()).apply()
        return true
    }

    private fun notify(context: Context, notice: PriceNotice) {
        val notifications = NotificationManagerCompat.from(context)
        if (!notifications.areNotificationsEnabled()) return
        notifications.createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                .setName("Yakıt fiyatı değişimleri")
                .build()
        )
        val openApp = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_fuel)
            .setContentTitle(notice.title)
            .setContentText(notice.text)
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .build()
        try {
            notifications.notify(NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            // İzin, kontrol ile gönderim arasında geri alınmış; bir sonraki kontrolde yeniden denenir.
        }
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

class PriceWatchWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result =
        if (PriceWatch.check(applicationContext)) Result.success() else Result.retry()
}
