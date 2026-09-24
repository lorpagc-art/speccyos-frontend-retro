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
 *  Era una WebView que cargaba sitios de terceros con:
 *    - javaScriptEnabled = true
 *    - addJavascriptInterface(WebTokenBridge, "AndroidBridge")
 *    - MIXED_CONTENT_ALWAYS_ALLOW
 *    - cookies de terceros activadas
 *    - User-Agent de escritorio falsificado
 *    - SIN shouldOverrideUrlLoading: la URL no se validaba en ningún punto, así que
 *      un redirect, un anuncio o un XSS en el sitio heredaba el bridge.
 *
 *  Además NO tenía ni una sola llamada en todo el proyecto: era superficie de
 *  riesgo pura, con coste cero de eliminación.
 *
 *  Sustituido por `SpeccyAccountLinkDialog`.
 *
 *  Este fichero se puede borrar del proyecto sin más.
 */
