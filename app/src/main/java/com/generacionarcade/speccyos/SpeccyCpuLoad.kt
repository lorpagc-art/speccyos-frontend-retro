/*
 * Speccy OS — frontend retro para Android
 * Copyright (c) 2026 LV-Webstudio · lv-webstudio.com
 * Desarrollado por Speccy81 (LORPAGC) · administracion@lv-webstudio.com
 * Todos los derechos reservados.
 */
package com.generacionarcade.speccyos

import java.io.File

/**
 * Carga real de CPU, leida de `/proc/stat`.
 *
 * POR QUE EXISTE
 * --------------
 * `HardwareViewModel` no medía nada: daba a `cpuLoad` una constante sacada del
 * perfil de rendimiento elegido (ECO 0.3, BALANCED 0.5, PERFORMANCE 0.8,
 * EXTREME 0.95). El widget enseñaba ese numero como si fuera telemetria, y no
 * lo era: con el perfil quieto, la cifra no se movia hiciera lo que hiciera la
 * consola.
 *
 * COMO SE CALCULA
 * ---------------
 * La primera linea de `/proc/stat` acumula tiempos de CPU desde el arranque.
 * La carga es la fraccion de tiempo NO ocioso entre dos lecturas, asi que hace
 * falta una muestra previa: la primera llamada devuelve null a proposito.
 *
 * SI NO SE PUEDE LEER
 * -------------------
 * Devuelve **null**, no un valor inventado. Algunos fabricantes restringen
 * `/proc` a las apps normales, y en ese caso la interfaz debe decir "—" en vez
 * de mentir. Es justo el patron que sobra en este proyecto: fallar en silencio
 * enseñando un dato falso.
 */
object SpeccyCpuLoad {

    /** (ocioso, total) de la lectura anterior. */
    @Volatile
    private var anterior: Pair<Long, Long>? = null

    /** Carga entre 0 y 1, o null si no hay dato fiable todavia. */
    fun leer(): Float? {
        val primeraLinea = runCatching {
            File("/proc/stat").bufferedReader().use { it.readLine() }
        }.getOrNull() ?: return null

        if (!primeraLinea.startsWith("cpu ")) return null

        // cpu  user nice system idle iowait irq softirq steal guest guest_nice
        val campos = primeraLinea.trim().split(Regex("\\s+"))
            .drop(1)
            .mapNotNull { it.toLongOrNull() }
        if (campos.size < 5) return null

        val ocioso = campos[3] + campos[4]   // idle + iowait
        val total = campos.sum()

        val previa = anterior
        anterior = ocioso to total
        if (previa == null) return null      // sin muestra anterior no hay delta

        val deltaOcioso = ocioso - previa.first
        val deltaTotal = total - previa.second
        if (deltaTotal <= 0L) return null

        return ((deltaTotal - deltaOcioso).toFloat() / deltaTotal).coerceIn(0f, 1f)
    }
}
