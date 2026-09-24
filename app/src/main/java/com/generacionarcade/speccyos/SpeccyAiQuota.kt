/*
 * Speccy OS — frontend retro para Android
 * Copyright (c) 2026 LV-Webstudio · lv-webstudio.com
 * Desarrollado por Speccy81 (LORPAGC) · administracion@lv-webstudio.com
 * Todos los derechos reservados.
 */
package com.generacionarcade.speccyos

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Espejo del consumo diario de IA FUERA de los datos de la aplicacion.
 *
 * EL PROBLEMA
 * -----------
 * Los topes viven en `SharedPreferences`, y "Borrar datos" en los ajustes de
 * Android los borra: el contador vuelve a cero y la cuota es infinita para
 * quien quiera saltarsela. Con la app gratuita y sin anuncios, cada llamada la
 * paga el desarrollador en Vertex AI, asi que ese agujero es una factura.
 *
 * QUE HACE ESTO
 * -------------
 * Guarda una copia del contador en la carpeta de ROMs que el usuario ya
 * concedio por SAF (`SpeccyOS_cfg/speccy_ai_quota.txt`). Esa carpeta es
 * almacenamiento compartido: **sobrevive a "Borrar datos" y a desinstalar**.
 * Al empezar una consulta se toma el MAYOR de los dos contadores, asi que
 * borrar los datos ya no regala cuota.
 *
 * QUE NO HACE, Y CONVIENE SABERLO
 * -------------------------------
 * No es una caja fuerte. El fichero esta a la vista y el usuario puede
 * borrarlo. Es un freno honesto contra el reinicio accidental o casual, no
 * contra alguien decidido.
 *
 * **El cierre de verdad es contar en servidor**, con el uid de Firebase o un
 * identificador estable de dispositivo, y eso exige una Cloud Function o unas
 * reglas de Firestore desplegadas: trabajo de backend, no de esta capa.
 *
 * Si el usuario no ha concedido la carpeta de ROMs no hay espejo posible, y se
 * cae con elegancia al contador local de siempre.
 */
object SpeccyAiQuota {

    private const val TAG = "SpeccyAiQuota"
    private const val CARPETA = "SpeccyOS_cfg"

    // SAF anade la extension del MIME al crear, asi que se nombra ya con ella
    // (misma leccion que costo un fichero por partida en SpeccyRaTuning).
    private const val FICHERO = "speccy_ai_quota.txt"

    private fun hoy(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    private fun arbolDeRoms(context: Context): Uri? = runCatching {
        SettingsManager(context).romsLocation.takeIf { it.isNotEmpty() }?.let { Uri.parse(it) }
    }.getOrNull()

    private fun documento(context: Context, crear: Boolean): DocumentFile? = runCatching {
        val raiz = DocumentFile.fromTreeUri(context, arbolDeRoms(context) ?: return null)
        val dir = raiz?.findFile(CARPETA)
            ?: (if (crear) raiz?.createDirectory(CARPETA) else null)
            ?: return null
        dir.findFile(FICHERO)
            ?: (if (crear) dir.createFile("text/plain", FICHERO) else null)
    }.getOrNull()

    /** Contadores del espejo para hoy, o null si no hay espejo utilizable. */
    private fun leer(context: Context): Pair<Int, Int>? = runCatching {
        val doc = documento(context, crear = false) ?: return null
        val texto = context.contentResolver.openInputStream(doc.uri)
            ?.bufferedReader()?.use { it.readText() } ?: return null

        val campos = texto.trim().split(" ").mapNotNull { campo ->
            val partes = campo.split("=")
            if (partes.size == 2) partes[0] to partes[1] else null
        }.toMap()

        // De otro dia: el espejo no aporta nada y no debe frenar nada.
        if (campos["fecha"] != hoy()) return null

        (campos["chat"]?.toIntOrNull() ?: 0) to (campos["ocr"]?.toIntOrNull() ?: 0)
    }.getOrNull()

    private fun escribir(context: Context, chat: Int, ocr: Int) {
        runCatching {
            val doc = documento(context, crear = true) ?: return
            context.contentResolver.openOutputStream(doc.uri, "wt")?.use { salida ->
                salida.write("fecha=${hoy()} chat=$chat ocr=$ocr".toByteArray())
            }
        }.onFailure { Log.d(TAG, "No se pudo escribir el espejo de cuota: ${it.message}") }
    }

    /**
     * Sube los contadores locales al nivel del espejo si este va por delante.
     *
     * Llamar SIEMPRE en un hilo de IO antes de comprobar un tope: es I/O por
     * SAF, no vale para el hilo principal.
     */
    fun sincronizar(context: Context, settings: SettingsManager) {
        val (chatEspejo, ocrEspejo) = leer(context) ?: return
        if (chatEspejo > settings.aiQueriesToday) settings.aiQueriesToday = chatEspejo
        if (ocrEspejo > settings.ocrTranslationsToday) settings.ocrTranslationsToday = ocrEspejo
    }

    /** Vuelca los contadores locales al espejo. Tambien en IO. */
    fun anotar(context: Context, settings: SettingsManager) {
        escribir(context, settings.aiQueriesToday, settings.ocrTranslationsToday)
    }
}
