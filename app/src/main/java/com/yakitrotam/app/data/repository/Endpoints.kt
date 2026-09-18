package com.yakitrotam.app.data.repository

import android.content.Context
import android.util.Log
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * Uygulamanın kullandığı ücretsiz sunucuların adresleri. Bu sunucuların hizmet garantisi yok;
 * biri kapanır ya da uygulamayı kısıtlarsa mağaza güncellemesi beklemeden değiştirilebilsin diye
 * adresler [CONFIG_URL] adresindeki JSON'dan okunur. Dosyaya ulaşılamazsa son indirilen,
 * o da yoksa aşağıdaki gömülü değerler geçerlidir.
 */
object Endpoints {
    private const val CONFIG_URL = "https://ziewenn.github.io/yakitrotam/config.json"
    private const val PREFS = "endpoints"
    private const val KEY_CONFIG = "config_json"

    @Volatile var osrm: String = "https://router.project-osrm.org"
        private set
    @Volatile var overpass: List<String> = listOf(
        "https://overpass-api.de/api/interpreter",
        "https://overpass.kumi.systems/api/interpreter",
        "https://maps.mail.ru/osm/tools/overpass/api/interpreter"
    )
        private set
    @Volatile var nominatim: String = "https://nominatim.openstreetmap.org"
        private set
    @Volatile var photon: String = "https://photon.komoot.io"
        private set
    @Volatile var mapStyle: String = "https://tiles.openfreemap.org/styles/dark"
        private set

    /** Açılışta çağrılır: önce son indirilen ayar uygulanır, güncel olanı arka planda çekilir. */
    fun init(context: Context) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.getString(KEY_CONFIG, null)?.let(::apply)

        thread(isDaemon = true, name = "endpoints-config") {
            try {
                val client = OkHttpClient.Builder()
                    .connectTimeout(8, TimeUnit.SECONDS)
                    .readTimeout(8, TimeUnit.SECONDS)
                    .build()
                client.newCall(Request.Builder().url(CONFIG_URL).build()).execute().use { response ->
                    val body = response.body?.string()
                    if (response.isSuccessful && body != null && apply(body)) {
                        prefs.edit().putString(KEY_CONFIG, body).apply()
                    }
                }
            } catch (e: Exception) {
                Log.w("Endpoints", "Uzak ayar alınamadı, mevcut adreslerle devam ediliyor", e)
            }
        }
    }

    /**
     * [raw] içindeki geçerli adresleri uygular. Yalnızca https adresleri kabul edilir; eksik ya da
     * geçersiz alanlar mevcut değeri korur. JSON okunamıyorsa false döner ve hiçbir şey değişmez.
     */
    internal fun apply(raw: String): Boolean {
        val config = runCatching { Json.parseToJsonElement(raw).jsonObject }.getOrNull() ?: return false
        config["osrm"].url()?.let { osrm = it }
        config["nominatim"].url()?.let { nominatim = it }
        config["photon"].url()?.let { photon = it }
        config["mapStyle"].url()?.let { mapStyle = it }
        runCatching { config["overpass"]?.jsonArray?.mapNotNull { it.url() } }.getOrNull()
            ?.takeIf { it.isNotEmpty() }
            ?.let { overpass = it }
        return true
    }

    private fun JsonElement?.url(): String? =
        runCatching { this?.jsonPrimitive?.contentOrNull }.getOrNull()
            ?.trim()?.trimEnd('/')
            ?.takeIf { it.startsWith("https://") && it.length > "https://".length }
}
