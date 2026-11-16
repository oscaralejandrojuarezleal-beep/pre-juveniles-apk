package com.example.apkpre

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Avisos con la app cerrada (sin Firebase: los arma el propio celular).
 *  - A la mañana (7:30): devocional del día y cumpleaños de hoy.
 *  - A la noche (21:30): capacitaciones nuevas.
 * En los dos horarios también se pone al día el calendario "PRE Juveniles" (si la persona lo activó).
 * Android puede correr la hora unos minutos para ahorrar batería.
 */
object Novedades {

    private const val MANANA = "pre_avisos_manana"
    private const val NOCHE = "pre_avisos_noche"

    fun programar(ctx: Context) {
        val wm = WorkManager.getInstance(ctx)
        wm.cancelUniqueWork("pre_chequeo_nocturno") // el aviso genérico de antes
        programarUno(ctx, MANANA, Config.HORA_MANANA, Config.MINUTO_MANANA, "manana")
        programarUno(ctx, NOCHE, Config.HORA_CHEQUEO, Config.MINUTO_CHEQUEO, "noche")
    }

    private fun programarUno(ctx: Context, nombre: String, hora: Int, minuto: Int, momento: String) {
        val ahora = Calendar.getInstance()
        val objetivo = (ahora.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, hora)
            set(Calendar.MINUTE, minuto)
            set(Calendar.SECOND, 0)
            if (before(ahora)) add(Calendar.DAY_OF_MONTH, 1)
        }
        val pedido = PeriodicWorkRequestBuilder<AvisosWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(objetivo.timeInMillis - ahora.timeInMillis, TimeUnit.MILLISECONDS)
            .setInputData(workDataOf("momento" to momento))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(nombre, ExistingPeriodicWorkPolicy.UPDATE, pedido)
    }

    /** Pide al servidor actividades, cumpleaños, devocionales y capacitaciones de esta persona. */
    fun pedirDatos(ctx: Context): JSONObject? {
        val prefs = ctx.getSharedPreferences(Config.PREFS, Context.MODE_PRIVATE)
        val token = prefs.getString("token", "") ?: ""
        if (token.isBlank()) return null
        val cuerpo = JSONObject().put("fn", "PRE_apiAgendaAvisos").put("args", JSONArray().put(token)).toString()
        val respuesta = JSONObject(Red.post(Config.API_URL, cuerpo))
        val r = respuesta.optJSONObject("r") ?: return null
        if (!respuesta.optBoolean("ok") || !r.optBoolean("ok")) return null
        return r
    }

    fun hoy(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    /** Aviso de la mañana: devocional en curso y cumpleaños de hoy (una vez por día). */
    fun avisosManana(ctx: Context, datos: JSONObject) {
        val prefs = ctx.getSharedPreferences(Config.PREFS, Context.MODE_PRIVATE)
        val hoy = hoy()
        if (prefs.getString("aviso_manana_dia", "") == hoy) return
        val devs = datos.optJSONArray("devocionales") ?: JSONArray()
        if (devs.length() > 0) {
            val d = devs.getJSONObject(0)
            val total = d.optInt("total")
            val hechos = d.optInt("hechos")
            val extra = if (devs.length() > 1) " · y " + (devs.length() - 1) + " más" else ""
            Notificaciones.mostrar(
                ctx,
                "🙏 Devocional de hoy",
                d.optString("titulo") + (if (total > 0) " (día " + (hechos + 1) + " de " + total + ")" else "") + extra,
                2001,
                "capacitaciones"
            )
        }
        val cal = Calendar.getInstance()
        val mes = cal.get(Calendar.MONTH) + 1
        val dia = cal.get(Calendar.DAY_OF_MONTH)
        val cumplen = mutableListOf<String>()
        val cums = datos.optJSONArray("cumpleanios") ?: JSONArray()
        for (i in 0 until cums.length()) {
            val c = cums.optJSONObject(i) ?: continue
            if (c.optInt("mes") == mes && c.optInt("dia") == dia) cumplen.add(c.optString("nombre"))
        }
        if (cumplen.isNotEmpty()) {
            Notificaciones.mostrar(
                ctx,
                if (cumplen.size == 1) "🎂 Hoy cumple " + cumplen[0] else "🎂 Hoy cumplen " + cumplen.size + " personas",
                if (cumplen.size == 1) "¡No te olvides de saludar!" else cumplen.joinToString(", "),
                2002,
                "inicio"
            )
        }
        prefs.edit().putString("aviso_manana_dia", hoy).apply()
    }

    /** Aviso de la noche: capacitaciones que todavía no se avisaron. */
    fun avisosNoche(ctx: Context, datos: JSONObject) {
        val prefs = ctx.getSharedPreferences(Config.PREFS, Context.MODE_PRIVATE)
        val avisadas = (prefs.getString("caps_avisadas", "") ?: "").split("|").filter { it.isNotBlank() }.toMutableSet()
        val caps = datos.optJSONArray("capacitaciones") ?: JSONArray()
        val nuevas = mutableListOf<String>()
        val vigentes = mutableSetOf<String>()
        for (i in 0 until caps.length()) {
            val c = caps.optJSONObject(i) ?: continue
            val id = c.optString("id")
            vigentes.add(id)
            if (id.isNotBlank() && id !in avisadas) nuevas.add(c.optString("titulo"))
        }
        if (nuevas.isNotEmpty()) {
            Notificaciones.mostrar(
                ctx,
                if (nuevas.size == 1) "📚 Nueva capacitación" else "📚 " + nuevas.size + " capacitaciones nuevas",
                nuevas.joinToString(" · "),
                2003,
                "capacitaciones"
            )
        }
        // Se recuerdan sólo las vigentes, así la lista no crece para siempre.
        prefs.edit().putString("caps_avisadas", (vigentes + avisadas.intersect(vigentes)).joinToString("|")).apply()
    }

    /** Lo que la app ya vio (usado por PuenteJs). Se mantiene por compatibilidad. */
    fun huella(manifiestoJson: String?): String {
        if (manifiestoJson.isNullOrBlank()) return ""
        return try {
            val m = JSONObject(manifiestoJson)
            val v = m.optJSONObject("versiones") ?: JSONObject()
            fun base(clave: String) = v.optString(clave, "").substringBefore("|u")
            listOf("app=" + m.optString("appBuild", ""), "cap=" + base("capacitaciones")).joinToString(";")
        } catch (e: Exception) {
            ""
        }
    }
}

class AvisosWorker(ctx: Context, params: WorkerParameters) : Worker(ctx, params) {
    override fun doWork(): Result {
        return try {
            val datos = Novedades.pedirDatos(applicationContext) ?: return Result.success()
            if (Calendario.activo(applicationContext)) {
                try {
                    Calendario.sincronizar(applicationContext, datos)
                } catch (e: Exception) {
                }
            }
            if (inputData.getString("momento") == "manana") Novedades.avisosManana(applicationContext, datos)
            else Novedades.avisosNoche(applicationContext, datos)
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
