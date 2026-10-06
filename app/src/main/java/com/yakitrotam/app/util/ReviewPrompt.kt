package com.yakitrotam.app.util

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.play.core.review.ReviewManagerFactory

/**
 * Google Play'in uygulama içi puanlama penceresi. Kullanıcı uygulamadan fayda gördükten sonra,
 * yani birkaç plan çıkardıktan sonra istenir. Pencerenin gerçekten gösterilip gösterilmeyeceğine
 * Play karar verir (kota, daha önce puan vermiş olma); uygulama sonucu öğrenemez.
 */
object ReviewPrompt {
    private const val PREFS = "review_prompt"
    private const val KEY_PLANS = "plans"
    private const val KEY_NEXT_ASK_AT = "next_ask_at"
    private const val FIRST_ASK_AFTER_PLANS = 2
    private const val PLANS_BETWEEN_ASKS = 5

    /** Başarıyla çıkarılan her plan için çağrılır. */
    fun recordPlan(context: Context) {
        val prefs = prefs(context)
        prefs.edit().putInt(KEY_PLANS, prefs.getInt(KEY_PLANS, 0) + 1).apply()
    }

    /** Kullanıcı bir işin ortasında değilken çağrılmalı (özet ekranından dönüş, açılış). */
    fun askIfDue(activity: Activity) {
        val prefs = prefs(activity)
        val plans = prefs.getInt(KEY_PLANS, 0)
        if (plans < prefs.getInt(KEY_NEXT_ASK_AT, FIRST_ASK_AFTER_PLANS)) return

        val manager = ReviewManagerFactory.create(activity)
        manager.requestReviewFlow().addOnCompleteListener { request ->
            if (!request.isSuccessful) {
                // Play Store yok ya da ulaşılamıyor; sayaç ilerlemediği için sonra yeniden denenir.
                Log.w("ReviewPrompt", "Puanlama akışı alınamadı", request.exception)
                return@addOnCompleteListener
            }
            if (activity.isFinishing || activity.isDestroyed) return@addOnCompleteListener
            prefs.edit().putInt(KEY_NEXT_ASK_AT, plans + PLANS_BETWEEN_ASKS).apply()
            manager.launchReviewFlow(activity, request.result)
        }
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
