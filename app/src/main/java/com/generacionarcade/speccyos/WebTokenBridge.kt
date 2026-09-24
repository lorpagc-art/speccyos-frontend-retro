/*
 * Speccy OS — frontend retro para Android
 * Copyright (c) 2026 LV-Webstudio · lv-webstudio.com
 * Desarrollado por Speccy81 (LORPAGC) · administracion@lv-webstudio.com
 * Todos los derechos reservados.
 */
package com.generacionarcade.speccyos

/*
 * ============================================================================
 *  ELIMINADO EN LA AUDITORÍA DE AGOSTO 2026 — este fichero ya no declara nada.
 * ============================================================================
 *
 *  Era el `@JavascriptInterface` que se inyectaba en una página web de TERCEROS
 *  desde UltraLoginWebView. Recibía usuario y token de RetroAchievements y, peor,
 *  usuario y CONTRASEÑA EN CLARO de ScreenScraper, extraídos recorriendo todos los
 *  <input> de la página.
 *
 *  Para Google Play esto es captura de credenciales de un servicio ajeno dentro de
 *  una WebView: el patrón que la política de Comportamiento Engañoso trata como
 *  phishing y una de las causas más probables de suspensión de la app.
 *
 *  Sustituido por `SpeccyAccountLinkDialog`, que abre la web oficial en el
 *  navegador del sistema y pide al usuario que pegue su clave de API (revocable),
 *  nunca su contraseña.
 *
 *  Este fichero se puede borrar del proyecto sin más.
 */
