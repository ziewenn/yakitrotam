package com.yakitrotam.app.ads

import android.app.Activity
import android.os.SystemClock
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import com.yakitrotam.app.BuildConfig
import java.util.concurrent.atomic.AtomicBoolean

/**
 * AdMob kurulumu, kullanıcı onayı ve geçiş reklamı.
 *
 * Reklam isteği ancak UMP onay akışı "reklam istenebilir" dedikten sonra yapılır.
 * AB/Birleşik Krallık kullanıcılarına Google'ın onay formu gösterilir; diğer
 * ülkelerde form çıkmaz ve reklamlar doğrudan yüklenir.
 */
object Ads {
    /** Banner'lar bu değer true olunca çizilir; onaydan önce reklam isteği atılmaz. */
    var isReady by mutableStateOf(false)
        private set

    private val initStarted = AtomicBoolean(false)
    private var interstitial: InterstitialAd? = null
    private var isLoadingInterstitial = false
    private var lastInterstitialShownAt = 0L

    /** Geçiş reklamları arasında en az bu kadar süre bırakılır (AdMob politikası). */
    private const val INTERSTITIAL_MIN_INTERVAL_MS = 3 * 60 * 1000L

    fun initialize(activity: Activity) {
        if (!BuildConfig.ADS_ENABLED) return
        val consentInformation = UserMessagingPlatform.getConsentInformation(activity)

        // Önceki oturumda onay alınmışsa formu beklemeden başla.
        if (consentInformation.canRequestAds()) startMobileAds(activity)

        consentInformation.requestConsentInfoUpdate(
            activity,
            ConsentRequestParameters.Builder().build(),
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) {
                    if (consentInformation.canRequestAds()) startMobileAds(activity)
                }
            },
            {
                // Onay servisine ulaşılamazsa, önceden alınmış onay varsa devam edilir.
                if (consentInformation.canRequestAds()) startMobileAds(activity)
            }
        )
    }

    private fun startMobileAds(activity: Activity) {
        if (!initStarted.compareAndSet(false, true)) return
        MobileAds.initialize(activity.applicationContext) {
            activity.runOnUiThread {
                isReady = true
                preloadInterstitial(activity)
            }
        }
    }

    private fun preloadInterstitial(activity: Activity) {
        if (!isReady || interstitial != null || isLoadingInterstitial) return
        isLoadingInterstitial = true
        InterstitialAd.load(
            activity,
            BuildConfig.ADMOB_INTERSTITIAL_ID,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitial = ad
                    isLoadingInterstitial = false
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    interstitial = null
                    isLoadingInterstitial = false
                }
            }
        )
    }

    /**
     * Rota hesabı başlarken çağrılır. Reklam hazırsa ve son gösterimden beri yeterli
     * süre geçtiyse tam ekran reklam açılır; hesaplama arkada sürer, kullanıcı reklamı
     * kapatınca sonuç ekranını görür. Aksi halde hiçbir şey yapmaz.
     */
    fun showInterstitialIfDue(activity: Activity) {
        val ad = interstitial
        val now = SystemClock.elapsedRealtime()
        if (ad == null) {
            preloadInterstitial(activity)
            return
        }
        if (lastInterstitialShownAt != 0L && now - lastInterstitialShownAt < INTERSTITIAL_MIN_INTERVAL_MS) return

        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                interstitial = null
                preloadInterstitial(activity)
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                interstitial = null
                preloadInterstitial(activity)
            }
        }
        lastInterstitialShownAt = now
        ad.show(activity)
    }
}

/** İnce bant: ekranın büyük kısmını kaplayan "large" boyut yerine en fazla 60 dp. */
private const val BANNER_MAX_HEIGHT_DP = 60

/** Ekranın altına yerleşen, genişliğe uyarlanan banner reklam. */
@Composable
fun AdBanner(modifier: Modifier = Modifier) {
    if (!Ads.isReady) return

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val widthDp = maxWidth.value.toInt()
        val adSize = remember(widthDp) {
            AdSize.getInlineAdaptiveBannerAdSize(widthDp, BANNER_MAX_HEIGHT_DP)
        }
        AndroidView(
            modifier = Modifier.fillMaxWidth().height(adSize.height.dp),
            factory = { ctx ->
                AdView(ctx).apply {
                    setAdSize(adSize)
                    adUnitId = BuildConfig.ADMOB_BANNER_ID
                    loadAd(AdRequest.Builder().build())
                }
            },
            onRelease = { it.destroy() }
        )
    }
}
