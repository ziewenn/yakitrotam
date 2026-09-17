package com.yakitrotam.app.ads

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.nativead.MediaView
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdOptions
import com.google.android.gms.ads.nativead.NativeAdView
import com.yakitrotam.app.BuildConfig

/**
 * Uygulamanın kart tasarımına uyan native reklam.
 *
 * Reklam içerikle karıştırılmasın diye (AdMob politikası) üstte belirgin bir "Reklam"
 * etiketi ve farklı bir kenarlık rengi vardır. Reklam gelmezse hiç yer kaplamaz.
 * MediaView en az 120 dp tutulur; daha küçüğü AdMob tarafından geçersiz sayılabiliyor.
 */
@Composable
fun NativeAdCard(modifier: Modifier = Modifier) {
    if (!Ads.isReady || BuildConfig.ADMOB_NATIVE_ID.isBlank()) return
    val context = LocalContext.current
    var nativeAd by remember { mutableStateOf<NativeAd?>(null) }

    DisposableEffect(Unit) {
        var disposed = false
        AdLoader.Builder(context, BuildConfig.ADMOB_NATIVE_ID)
            .forNativeAd { ad ->
                if (disposed) {
                    ad.destroy()
                } else {
                    nativeAd?.destroy()
                    nativeAd = ad
                }
            }
            .withAdListener(object : AdListener() {
                override fun onAdFailedToLoad(error: LoadAdError) {
                    Log.w("YakitRotamAds", "Native reklam yüklenemedi: ${error.code} ${error.message}")
                }
            })
            .withNativeAdOptions(
                NativeAdOptions.Builder()
                    .setAdChoicesPlacement(NativeAdOptions.ADCHOICES_TOP_RIGHT)
                    .build()
            )
            .build()
            .loadAd(AdRequest.Builder().build())

        onDispose {
            disposed = true
            nativeAd?.destroy()
            nativeAd = null
        }
    }

    val ad = nativeAd ?: return
    AndroidView(
        modifier = modifier.fillMaxWidth(),
        factory = { ctx -> NativeAdViews(ctx).root },
        update = { view -> NativeAdViews.bind(view, ad) }
    )
}

/** Native reklam görünümünü Android View'larıyla kurar; Compose teması renkleriyle aynı. */
private class NativeAdViews(context: Context) {
    val root = NativeAdView(context)

    private fun dp(value: Int) = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), root.resources.displayMetrics
    ).toInt()

    init {
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(14))
            background = GradientDrawable().apply {
                cornerRadius = dp(20).toFloat()
                setColor(SURFACE)
                setStroke(dp(1), AMBER_BORDER)
            }
        }

        val badge = TextView(context).apply {
            text = "Reklam"
            setTextColor(Color.BLACK)
            setTypeface(typeface, Typeface.BOLD)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
            setPadding(dp(7), dp(2), dp(7), dp(2))
            background = GradientDrawable().apply { cornerRadius = dp(6).toFloat(); setColor(AMBER) }
        }
        val advertiser = TextView(context).apply {
            setTextColor(MUTED)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setPadding(dp(8), 0, dp(24), 0)
        }
        card.addView(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(badge)
            addView(advertiser)
        })

        val media = MediaView(context)
        val headline = TextView(context).apply {
            setTextColor(TEXT)
            setTypeface(typeface, Typeface.BOLD)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
        }
        val body = TextView(context).apply {
            setTextColor(SECONDARY)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            maxLines = 3
            ellipsize = TextUtils.TruncateAt.END
            setPadding(0, dp(4), 0, dp(8))
        }
        val cta = Button(context).apply {
            isAllCaps = false
            setTextColor(Color.BLACK)
            setTypeface(typeface, Typeface.BOLD)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            minHeight = 0
            minimumHeight = 0
            setPadding(dp(14), dp(8), dp(14), dp(8))
            stateListAnimator = null
            background = GradientDrawable().apply { cornerRadius = dp(12).toFloat(); setColor(LIME) }
        }
        val texts = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, 0, 0)
            addView(headline)
            addView(body)
            addView(cta, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ))
        }
        card.addView(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(10), 0, 0)
            addView(media, LinearLayout.LayoutParams(dp(120), dp(120)))
            addView(texts, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        })

        root.addView(card)
        root.mediaView = media
        root.headlineView = headline
        root.bodyView = body
        root.callToActionView = cta
        root.advertiserView = advertiser
    }

    companion object {

        private val SURFACE = Color.parseColor("#14161D")
        private val AMBER = Color.parseColor("#FFB020")
        private val AMBER_BORDER = Color.parseColor("#66FFB020")
        private val LIME = Color.parseColor("#9EFF3D")
        private val TEXT = Color.parseColor("#F4F6FA")
        private val SECONDARY = Color.parseColor("#9AA3B2")
        private val MUTED = Color.parseColor("#636B7A")

        fun bind(view: NativeAdView, ad: NativeAd) {
            (view.headlineView as TextView).text = ad.headline
            (view.bodyView as TextView).apply {
                text = ad.body
                visibility = if (ad.body.isNullOrBlank()) View.GONE else View.VISIBLE
            }
            (view.callToActionView as Button).apply {
                text = ad.callToAction
                visibility = if (ad.callToAction.isNullOrBlank()) View.GONE else View.VISIBLE
            }
            (view.advertiserView as TextView).text = ad.advertiser.orEmpty()
            view.mediaView?.mediaContent = ad.mediaContent
            view.setNativeAd(ad)
        }
    }
}
