package com.example.apkpre

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import java.util.TimeZone

/**
 * Calendario "PRE Juveniles" dentro del calendario del celular (Google Calendar lo muestra igual).
 * - Actividades del año, con aviso 1 hora antes.
 * - Cumpleaños, todos los años (sin año de nacimiento). El aviso del día lo da la app a la mañana.
 * Es un calendario aparte: se puede ocultar o borrar sin tocar los eventos propios de cada uno.
 * Se mantiene solo: lo que cambia o se cancela en la app se actualiza o se borra acá.
 */
object Calendario {

    private const val CUENTA = "PRE Juveniles"
    private const val PREF_ACTIVO = "calendario_activo"

    fun permisoOk(ctx: Context): Boolean =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    fun activo(ctx: Context): Boolean =
        ctx.getSharedPreferences(Config.PREFS, Context.MODE_PRIVATE).getBoolean(PREF_ACTIVO, false)

    fun marcarActivo(ctx: Context, si: Boolean) {
        ctx.getSharedPreferences(Config.PREFS, Context.MODE_PRIVATE).edit().putBoolean(PREF_ACTIVO, si).apply()
    }

    private fun comoAdaptador(uri: Uri): Uri = uri.buildUpon()
        .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
        .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_NAME, CUENTA)
        .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
        .build()

    private fun idCalendario(ctx: Context, crear: Boolean): Long? {
        val cr = ctx.contentResolver
        cr.query(
            CalendarContract.Calendars.CONTENT_URI,
            arrayOf(CalendarContract.Calendars._ID),
            "${CalendarContract.Calendars.ACCOUNT_NAME}=? AND ${CalendarContract.Calendars.ACCOUNT_TYPE}=?",
            arrayOf(CUENTA, CalendarContract.ACCOUNT_TYPE_LOCAL),
            null
        )?.use { c -> if (c.moveToFirst()) return c.getLong(0) }
        if (!crear) return null
        val v = ContentValues().apply {
            put(CalendarContract.Calendars.ACCOUNT_NAME, CUENTA)
            put(CalendarContract.Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
            put(CalendarContract.Calendars.NAME, CUENTA)
            put(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, CUENTA)
            put(CalendarContract.Calendars.CALENDAR_COLOR, Color.parseColor("#3158CF"))
            put(CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL, CalendarContract.Calendars.CAL_ACCESS_OWNER)
            put(CalendarContract.Calendars.OWNER_ACCOUNT, CUENTA)
            put(CalendarContract.Calendars.VISIBLE, 1)
            put(CalendarContract.Calendars.SYNC_EVENTS, 1)
            put(CalendarContract.Calendars.CALENDAR_TIME_ZONE, TimeZone.getDefault().id)
        }
        val uri = cr.insert(comoAdaptador(CalendarContract.Calendars.CONTENT_URI), v) ?: return null
        return uri.lastPathSegment?.toLongOrNull()
    }

    /** Borra el calendario "PRE Juveniles" con todos sus eventos. */
    fun quitar(ctx: Context) {
        marcarActivo(ctx, false)
        if (!permisoOk(ctx)) return
        val id = idCalendario(ctx, false) ?: return
        ctx.contentResolver.delete(
            comoAdaptador(CalendarContract.Calendars.CONTENT_URI),
            "${CalendarContract.Calendars._ID}=?",
            arrayOf(id.toString())
        )
    }

    private fun milis(fecha: String, hora: String, zona: TimeZone): Long? {
        return try {
            val f = fecha.split("-").map { it.toInt() }
            val h = if (hora.length >= 4) hora.split(":").map { it.toInt() } else listOf(0, 0)
            Calendar.getInstance(zona).apply {
                clear()
                set(f[0], f[1] - 1, f[2], h[0], h.getOrElse(1) { 0 }, 0)
            }.timeInMillis
        } catch (e: Exception) {
            null
        }
    }

    private class Evento(val uid: String, val valores: ContentValues, val avisoMin: Int?)

    private fun armarEventos(datos: JSONObject, cal: Long): List<Evento> {
        val zona = TimeZone.getDefault()
        val utc = TimeZone.getTimeZone("UTC")
        val lista = mutableListOf<Evento>()
        val acts = datos.optJSONArray("actividades") ?: JSONArray()
        for (i in 0 until acts.length()) {
            val a = acts.optJSONObject(i) ?: continue
            val fecha = a.optString("fecha")
            val inicio = a.optString("inicio")
            val fin = a.optString("fin")
            val conHora = inicio.length >= 4
            val v = ContentValues().apply {
                put(CalendarContract.Events.CALENDAR_ID, cal)
                put(CalendarContract.Events.TITLE, a.optString("titulo", "Actividad"))
                put(CalendarContract.Events.EVENT_LOCATION, a.optString("lugar"))
                put(CalendarContract.Events.DESCRIPTION, listOf(a.optString("nota"), "PRE Juveniles").filter { it.isNotBlank() }.joinToString("\n\n"))
                put(CalendarContract.Events.SYNC_DATA1, a.optString("id"))
                if (conHora) {
                    val ini = milis(fecha, inicio, zona) ?: return@apply
                    var finM = if (fin.length >= 4) milis(fecha, fin, zona) else null
                    if (finM == null || finM <= ini) finM = ini + 2 * 60 * 60 * 1000L
                    put(CalendarContract.Events.DTSTART, ini)
                    put(CalendarContract.Events.DTEND, finM)
                    put(CalendarContract.Events.EVENT_TIMEZONE, zona.id)
                    put(CalendarContract.Events.ALL_DAY, 0)
                } else {
                    val ini = milis(fecha, "", utc) ?: return@apply
                    put(CalendarContract.Events.DTSTART, ini)
                    put(CalendarContract.Events.DTEND, ini + 24 * 60 * 60 * 1000L)
                    put(CalendarContract.Events.EVENT_TIMEZONE, "UTC")
                    put(CalendarContract.Events.ALL_DAY, 1)
                }
                put(CalendarContract.Events.HAS_ALARM, if (conHora) 1 else 0)
            }
            if (v.containsKey(CalendarContract.Events.DTSTART)) lista.add(Evento(a.optString("id"), v, if (conHora) 60 else null))
        }
        val anio = Calendar.getInstance().get(Calendar.YEAR)
        val cums = datos.optJSONArray("cumpleanios") ?: JSONArray()
        for (i in 0 until cums.length()) {
            val c = cums.optJSONObject(i) ?: continue
            val mes = c.optInt("mes")
            val dia = c.optInt("dia")
            if (mes !in 1..12 || dia !in 1..31) continue
            val ini = Calendar.getInstance(utc).apply { clear(); set(anio, mes - 1, dia, 0, 0, 0) }.timeInMillis
            val v = ContentValues().apply {
                put(CalendarContract.Events.CALENDAR_ID, cal)
                put(CalendarContract.Events.TITLE, "🎂 Cumple de " + c.optString("nombre"))
                put(CalendarContract.Events.DESCRIPTION, "PRE Juveniles")
                put(CalendarContract.Events.SYNC_DATA1, c.optString("id"))
                put(CalendarContract.Events.DTSTART, ini)
                put(CalendarContract.Events.DURATION, "P1D")
                put(CalendarContract.Events.RRULE, "FREQ=YEARLY")
                put(CalendarContract.Events.EVENT_TIMEZONE, "UTC")
                put(CalendarContract.Events.ALL_DAY, 1)
                put(CalendarContract.Events.HAS_ALARM, 0)
            }
            lista.add(Evento(c.optString("id"), v, null))
        }
        return lista
    }

    /**
     * Deja el calendario igual a lo que dice la app: agrega lo nuevo, actualiza lo cambiado
     * y borra lo que ya no está (por ejemplo, una actividad cancelada).
     * Devuelve cuántos eventos quedaron.
     */
    fun sincronizar(ctx: Context, datos: JSONObject): Int {
        if (!permisoOk(ctx)) return -1
        val cal = idCalendario(ctx, true) ?: return -1
        val cr = ctx.contentResolver
        val existentes = HashMap<String, Long>()
        cr.query(
            CalendarContract.Events.CONTENT_URI,
            arrayOf(CalendarContract.Events._ID, CalendarContract.Events.SYNC_DATA1),
            "${CalendarContract.Events.CALENDAR_ID}=?",
            arrayOf(cal.toString()),
            null
        )?.use { c ->
            while (c.moveToNext()) {
                val uid = c.getString(1) ?: ""
                if (uid.isNotBlank()) existentes[uid] = c.getLong(0)
            }
        }
        val nuevos = armarEventos(datos, cal)
        val vistos = HashSet<String>()
        val uriEventos = comoAdaptador(CalendarContract.Events.CONTENT_URI)
        for (e in nuevos) {
            if (e.uid.isBlank() || !vistos.add(e.uid)) continue
            val id = existentes[e.uid]
            val eventoId: Long? = if (id != null) {
                // Para pasar de "con hora" a "todo el día" (o al revés) hay que limpiar el campo que no corresponde.
                if (e.valores.containsKey(CalendarContract.Events.DURATION)) e.valores.putNull(CalendarContract.Events.DTEND)
                else e.valores.putNull(CalendarContract.Events.DURATION)
                cr.update(uriEventos, e.valores, "${CalendarContract.Events._ID}=?", arrayOf(id.toString()))
                id
            } else {
                cr.insert(uriEventos, e.valores)?.lastPathSegment?.toLongOrNull()
            }
            if (eventoId != null) {
                cr.delete(
                    CalendarContract.Reminders.CONTENT_URI,
                    "${CalendarContract.Reminders.EVENT_ID}=?",
                    arrayOf(eventoId.toString())
                )
                if (e.avisoMin != null) {
                    cr.insert(CalendarContract.Reminders.CONTENT_URI, ContentValues().apply {
                        put(CalendarContract.Reminders.EVENT_ID, eventoId)
                        put(CalendarContract.Reminders.MINUTES, e.avisoMin)
                        put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
                    })
                }
            }
        }
        for ((uid, id) in existentes) {
            if (uid !in vistos) cr.delete(uriEventos, "${CalendarContract.Events._ID}=?", arrayOf(id.toString()))
        }
        return vistos.size
    }
}
