# APK PRE Juveniles CDL

App Android (Kotlin) que muestra **la misma app de Apps Script**, guardada en el celular.
Sin Firebase, 100 % gratis.

## Cómo funciona

- **Una sola base de código:** la APK baja la app completa desde tu script (`PRE_apiAppApk` por `doPost`).
  Lo que cambiás en Apps Script le llega a la web y a la APK.
- **Rápida y sin conexión:** la app queda guardada en el celular y abre al instante.
  Biblias, libros y datos se guardan como ya lo hacía la web (IndexedDB), pero Android no los borra.
- **Actualizaciones automáticas:** con sólo hacer Implementar → Nueva versión, la "huella" de la app cambia
  y a cada usuario le aparece "Hay una versión nueva de la app → Actualizar" (se revisa al abrir y cada
  10 minutos; si tocan "Después", no insiste hasta el día siguiente). No hay que reinstalar ni tocar números.
- **Sin conexión completa:** las librerías y fuentes de internet quedan guardadas en el celular (`CacheExterna.kt`).
- **Chequeo nocturno (21:30 aprox.)** con la app cerrada: si hay novedades, llega una notificación.
  Al abrir la app, aparecen los botones para elegir qué hacer.

## Archivos principales (app/src/main/java/com/example/apkpre)

| Archivo | Para qué |
|---|---|
| `Config.kt` | **Pegá acá la URL /exec** del script |
| `MainActivity.kt` | La pantalla: WebView, botón Atrás, links externos, subir archivos |
| `PaginasLocales.kt` | Sirve la app guardada en el celular |
| `Actualizador.kt` | Descarga la versión nueva de la app |
| `PuenteJs.kt` | Funciones de Android que usa la app (`window.PREAndroid`) |
| `Novedades.kt` | Chequeo nocturno y notificación |
| `Notificaciones.kt` | Notificaciones del celular |
| `Red.kt` | Llamadas a internet |
| `CacheExterna.kt` | Guarda librerías y fuentes para usar sin internet |
| `assets/www/index.html` | Pantalla "Preparando la app…" (sólo la primera vez) |

## Dos tipos de actualización

1. **Pantallas, estilos y lógica (Apps Script):** sólo Implementar → Nueva versión. Las apps muestran
   "Hay una versión nueva de la app → Actualizar". No se reinstala nada.
2. **Parte Android (esta carpeta):** cada cambio subido a la rama `main` lo compila GitHub solo
   (pestaña **Actions**) y lo publica en **Releases**. Al abrir la app aparece
   "Actualización disponible → Instalar". El número de versión lo pone GitHub solo.

## Firma (muy importante)

La APK se firma con una clave fija (`pre-juveniles.jks`). Si se pierde, las apps instaladas no aceptan
actualizaciones y hay que desinstalar. La clave **no está en este repositorio**: está en los secretos de
GitHub y en el archivo de RESPALDO guardado en la PC y en Drive.

## Costo

Cero. Repositorio público + GitHub Actions + Releases son gratis sin límite para repositorios públicos.
