package com.example.apkpre

/**
 * Configuración de la APK.
 *
 * API_URL: pegá acá la URL de la implementación de Apps Script (la que termina en /exec).
 * La muestra PRE_API_PRUEBA() en el registro del editor ("urlParaLaApk").
 */
object Config {
    const val API_URL = "https://script.google.com/macros/s/AKfycbxRUfbOcsthxsI4CSnqJWy2nBTNwMbg58ki0ZBuMN0DGXOgGM_eCT07TMfrlp02zzrF/exec"

    /** Repositorio de GitHub donde se compila y publica la APK (actualizaciones de la parte Android). */
    const val GITHUB_REPO = "oscaralejandrojuarezleal-beep/pre-juveniles-apk"

    /** Dirección interna desde donde el WebView carga la app (no es internet). */
    const val URL_INICIO = "https://appassets.androidplatform.net/index.html"

    /** Nombre del archivo de preferencias interno. */
    const val PREFS = "pre_apk"

    /** Función liviana que devuelve la "huella" de la app publicada. */
    const val FN_HUELLA = "PRE_apiHuellaApp"

    /** Hora aproximada del chequeo nocturno de novedades (24 h). */
    const val HORA_CHEQUEO = 21
    const val MINUTO_CHEQUEO = 30
}
