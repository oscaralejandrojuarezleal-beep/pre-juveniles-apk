package com.example.apkpre

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Chequeo nocturno de novedades con la app cerrada.
 * Pide sólo el "manifiesto" (números de versión, unos pocos bytes) y, si algo cambió
 * desde lo último que vio el celular, muestra una notificación. No descarga contenido:
 * al abrir la app aparecen los botones para elegir qué bajar.
 */
object Novedades {

    private const val TRABAJO = "pre_chequeo_nocturno"

    fun programar(ctx: Context) {
        val ahora = Calendar.getInstance()
        val objetivo = (ahora.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, Config.HORA_CHEQUEO)
            set(Calendar.MINUTE, Config.MINUTO_CHEQUEO)
            set(Calendar.SECOND, 0)
            if (before(ahora)) add(Calendar.DAY_OF_MONTH, 1)
        }
        val espera = objetivo.timeInMillis - ahora.timeInMillis

        val pedido = PeriodicWorkRequestBuilder<ChequeoWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(espera, TimeUnit.MILLISECONDS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()

        WorkManager.getInstance(ctx)
            .enqueueUniquePeriodicWork(TRABAJO, ExistingPeriodicWorkPolicy.KEEP, pedido)
    }

    /**
     * Resume el manifiesto en un texto comparable. Ignora la parte personal ("|u…")
     * de capacitaciones/estudio para no avisar cuando la persona sólo marcó su propio avance.
     */
    fun huella(manifiestoJson: String?): String {
        if (manifiestoJson.isNullOrBlank()) return ""
        return try {
            val m = JSONObject(manifiestoJson)
            val v = m.optJSONObject("versiones") ?: JSONObject()
            fun base(clave: String) = v.optString(clave, "").substringBefore("|u")
            listOf(
                "app=" + m.optString("appBuild", ""),
                "biblia=" + base("catalogoBiblia"),
                "cap=" + base("capacitaciones"),
                "eb=" + base("estudioBiblia"),
                "cont=" + (m.optJSONObject("contenido")?.toString() ?: "")
            ).joinToString(";")
        } catch (e: Exception) {
            ""
        }
    }
}

class ChequeoWorker(ctx: Context, params: WorkerParameters) : Worker(ctx, params) {

    override fun doWork(): Result {
        val prefs = applicationContext.getSharedPreferences(Config.PREFS, Context.MODE_PRIVATE)
        val token = prefs.getString("token", "") ?: ""
        if (token.isBlank() || !Config.API_URL.contains("/exec")) return Result.success()

        return try {
            val cuerpo = JSONObject()
                .put("fn", "PRE_apiManifiestoApp")
                .put("args", JSONArray().put(token))
                .toString()
            val respuesta = JSONObject(Red.post(Config.API_URL, cuerpo))
            val r = respuesta.optJSONObject("r")
            if (!respuesta.optBoolean("ok") || r == null || r.optString("status") != "OK") {
                return Result.success()
            }
            val nueva = Novedades.huella(r.optJSONObject("data")?.toString())
            val vieja = prefs.getString("manifiesto", "") ?: ""
            if (nueva.isNotEmpty() && vieja.isNotEmpty() && nueva != vieja) {
                Notificaciones.mostrar(
                    applicationContext,
                    "Hay novedades 🔔",
                    "Abrí la app para ver qué hay de nuevo y elegir qué descargar."
                )
            }
            if (nueva.isNotEmpty()) prefs.edit().putString("manifiesto", nueva).apply()
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
