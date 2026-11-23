/*
 * PRE Juveniles CDL — versión web (GitHub Pages).
 *
 * La app es la MISMA que usa la APK: se baja completa desde Apps Script (PRE_apiAppApk)
 * y queda guardada en el navegador. Así abre al instante y no pasa por el link de Google.
 * Los datos siguen yendo y viniendo de Apps Script como siempre.
 *
 * Este archivo lo usan la página de carga (index.html) y la app guardada (app.html):
 *   - PREWeb.bajar()   baja la app y la guarda.
 *   - El botón "Actualizar ahora" de la app baja la versión nueva acá (no va al link de Google).
 */
(function () {
  'use strict';

  var API_URL = 'https://script.google.com/macros/s/AKfycbxRUfbOcsthxsI4CSnqJWy2nBTNwMbg58ki0ZBuMN0DGXOgGM_eCT07TMfrlp02zzrF/exec';
  var CACHE = 'pre-contenido';
  var CADA_MS = 10 * 60 * 1000;

  // La app usa esta dirección para hablar con el servidor (igual que la APK).
  window.PRE_API_URL = API_URL;
  try { localStorage.setItem('PRE_API_URL', API_URL); } catch (e) {}

  function leer(k) { try { return localStorage.getItem(k) || ''; } catch (e) { return ''; } }
  function guardar(k, v) { try { localStorage.setItem(k, v); } catch (e) {} }

  function llamar(fn, ms) {
    var control = typeof AbortController === 'function' ? new AbortController() : null;
    var reloj = setTimeout(function () { if (control) control.abort(); }, ms || 5 * 60 * 1000);
    return fetch(API_URL, {
      method: 'POST',
      headers: { 'Content-Type': 'text/plain;charset=utf-8' },
      body: JSON.stringify({ fn: fn, args: [] }),
      redirect: 'follow',
      cache: 'no-store',
      signal: control ? control.signal : undefined
    }).then(function (res) {
      if (!res.ok) throw new Error('El servidor respondió ' + res.status);
      return res.text();
    }).then(function (texto) {
      clearTimeout(reloj);
      var d;
      try { d = JSON.parse(texto); } catch (e) { throw new Error('Respuesta inválida del servidor'); }
      if (!d || d.ok !== true) throw new Error((d && d.error) || 'Error del servidor');
      if (!d.r || d.r.ok === false) throw new Error((d.r && d.r.error) || 'Error del servidor');
      return d.r;
    }, function (e) {
      clearTimeout(reloj);
      if (e && e.name === 'AbortError') throw new Error('El servidor tardó demasiado');
      throw e;
    });
  }

  // La app guardada lleva este mismo archivo, para revisar versiones nuevas sola.
  function prepararHtml(html) {
    var etiqueta = '<script src="pre-web.js"></script>';
    if (/<head[^>]*>/i.test(html)) return html.replace(/<head[^>]*>/i, function (m) { return m + etiqueta; });
    return etiqueta + html;
  }

  var bajando = null;
  function bajar() {
    if (bajando) return bajando;
    bajando = llamar('PRE_apiAppApk', 7 * 60 * 1000).then(function (r) {
      if (!r.html || r.html.length < 1000) throw new Error('La app llegó vacía');
      var html = prepararHtml(r.html);
      var listo = window.caches
        ? caches.open(CACHE).then(function (c) {
            return c.put('app.html', new Response(html, { headers: { 'Content-Type': 'text/html; charset=utf-8' } }));
          })
        : Promise.resolve();
      return listo.then(function () {
        guardar('PRE_WEB_BUILD', r.build || '');
        return { build: r.build || '', html: html };
      });
    });
    bajando.then(function () { bajando = null; }, function () { bajando = null; });
    return bajando;
  }

  function tieneApp() {
    if (!window.caches) return Promise.resolve(false);
    return caches.open(CACHE).then(function (c) { return c.match('app.html'); }).then(function (r) { return !!r; }, function () { return false; });
  }

  // ---------- Aviso de versión nueva (sólo dentro de la app) ----------
  function cartel() {
    if (document.getElementById('preWebNueva')) return;
    var d = document.createElement('div');
    d.id = 'preWebNueva';
    d.setAttribute('role', 'status');
    d.style.cssText = 'position:fixed;left:12px;right:12px;bottom:calc(12px + env(safe-area-inset-bottom));z-index:2147483647;' +
      'max-width:520px;margin:0 auto;background:#213c9c;color:#fff;border-radius:16px;padding:12px 14px;' +
      'box-shadow:0 10px 30px rgba(0,0,0,.35);display:flex;gap:10px;align-items:center;font:600 14px system-ui,-apple-system,Segoe UI,Roboto,sans-serif';
    d.innerHTML = '<span style="flex:1">Hay una versión nueva de la app.</span>' +
      '<button type="button" id="preWebDespues" style="border:0;background:transparent;color:#dbe2ff;font-weight:700;padding:8px">Después</button>' +
      '<button type="button" id="preWebAhora" style="border:0;border-radius:10px;background:#fff;color:#213c9c;font-weight:800;padding:9px 14px">Actualizar</button>';
    document.body.appendChild(d);
    document.getElementById('preWebDespues').onclick = function () { d.remove(); };
    document.getElementById('preWebAhora').onclick = function () { location.reload(); };
  }

  // La app PRE ya avisa sola cuando hay versión nueva ("Nueva versión disponible").
  // Su botón "Actualizar ahora" iba al link de Google: acá baja la versión nueva y recarga.
  function engancharActualizar(intentos) {
    var A = window.PRE_APP;
    if (!A || typeof A.actualizarAhora !== 'function') {
      if ((intentos || 0) < 120) setTimeout(function () { engancharActualizar((intentos || 0) + 1); }, 500);
      return;
    }
    A.actualizarAhora = function () {
      try { localStorage.removeItem('PRE_VERSION_POSPUESTA'); } catch (e) {}
      var botones = document.querySelectorAll('#preVersionMask button');
      var boton = botones.length ? botones[botones.length - 1] : null;
      if (boton) { boton.disabled = true; boton.textContent = 'Descargando…'; }
      bajar().then(function () { location.reload(); }, function (e) {
        if (boton) { boton.disabled = false; boton.textContent = 'Reintentar'; }
        try { if (typeof window.toast === 'function') window.toast('No se pudo actualizar: ' + e.message, true); } catch (x) {}
      });
    };
  }
  // ¿Hay versión nueva publicada? Se compara la "huella" del código (igual que la APK).
  // Si cambió, se baja por detrás y aparece el cartel "Hay una versión nueva" → Actualizar.
  // (Antes no se revisaba: la web de GitHub quedaba con la versión vieja, por ejemplo sin la TPT.)
  var revisando = false, avisada = false;
  function revisar() {
    if (revisando || avisada || navigator.onLine === false) return;
    revisando = true;
    llamar('PRE_apiHuellaApp', 60000).then(function (r) {
      var nueva = String((r && r.huella) || '');
      if (!nueva || nueva === leer('PRE_WEB_BUILD')) return;
      return bajar().then(function () { avisada = true; cartel(); });
    }).catch(function () {}).then(function () { revisando = false; });
  }

  window.PREWeb = { apiUrl: API_URL, bajar: bajar, tieneApp: tieneApp, revisar: revisar };

  // Dentro de la app guardada.
  if (/app\.html$/.test(location.pathname)) {
    engancharActualizar(0);
    setTimeout(revisar, 4000);
    setInterval(revisar, CADA_MS);
    document.addEventListener('visibilitychange', function () { if (document.visibilityState === 'visible') revisar(); });
  }
})();
