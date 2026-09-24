/*
 * Speccy OS — frontend retro para Android
 * Copyright (c) 2026 LV-Webstudio · lv-webstudio.com
 * Desarrollado por Speccy81 (LORPAGC) · administracion@lv-webstudio.com
 * Todos los derechos reservados.
 */
package com.generacionarcade.speccyos

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.util.Log
import androidx.core.content.FileProvider
import java.io.File

/**
 * Momento: la captura de la partida convertida en una tarjeta para compartir.
 *
 * Nace de la burbuja del juego: la misma captura de pantalla que alimenta al
 * traductor y a la pista del Arquitecto, pero en vez de leerla se enmarca con
 * el titulo, la consola y el tiempo de partida, y se abre el panel de
 * compartir de Android. Es el unico camino por el que una partida de Speccy
 * OS sale de la consola hacia WhatsApp, Instagram o Telegram, y va firmada
 * con el nombre de la app: cada momento compartido es una recomendacion.
 *
 * Formato 4:5 (1080x1350), el que Instagram muestra a tamano completo en el
 * feed y WhatsApp no recorta. Todo se dibuja con Canvas: sin Compose, porque
 * se genera desde un Service sin ventana.
 */
object SpeccyMoments {

    private const val TAG = "SpeccyMoments"
    private const val ANCHO = 1080
    private const val ALTO = 1350
    private const val MARGEN = 72f
    private const val NEON = 0xFF00E5FF.toInt()
    private const val FONDO = 0xFF0A0E17.toInt()
    private const val MAX_GUARDADOS = 5

    /**
     * Dibuja la tarjeta y la deja en la cache de la app. Devuelve null si algo
     * falla; el que llama avisa al usuario.
     */
    fun crearTarjeta(
        context: Context,
        capturaOriginal: Bitmap,
        titulo: String,
        plataforma: String,
        minutosJugados: Long,
        lang: String
    ): File? = runCatching {
        val bitmap = Bitmap.createBitmap(ANCHO, ALTO, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val es = lang.startsWith("es")

        // Fondo con un halo de neon arriba, donde va la captura.
        canvas.drawColor(FONDO)
        canvas.drawRect(0f, 0f, ANCHO.toFloat(), ALTO.toFloat(), Paint().apply {
            shader = RadialGradient(
                ANCHO / 2f, 420f, 900f,
                intArrayOf(0x3300E5FF, 0x0000E5FF), floatArrayOf(0f, 1f), Shader.TileMode.CLAMP
            )
        })

        // Cabecera.
        val marca = textPaint(34f, NEON, Typeface.BOLD).apply { letterSpacing = 0.18f }
        canvas.drawText("SPECCY OS", MARGEN, 118f, marca)
        val etiqueta = textPaint(30f, 0x99FFFFFF.toInt(), Typeface.NORMAL).apply {
            letterSpacing = 0.12f
            textAlign = Paint.Align.RIGHT
        }
        canvas.drawText(if (es) "MOMENTO" else "MOMENT", ANCHO - MARGEN, 116f, etiqueta)

        // Captura sin las bandas negras del emulador (un 4:3 en una pantalla
        // 3:2 o 16:9 llega con columnas negras a los lados), encajada sin
        // deformar en una ventana con esquinas redondeadas.
        val captura = recortarBordesNegros(capturaOriginal)
        val ventana = RectF(MARGEN, 170f, ANCHO - MARGEN, 990f)
        val escala = minOf(ventana.width() / captura.width, ventana.height() / captura.height)
        val w = captura.width * escala
        val h = captura.height * escala
        val destino = RectF(
            ventana.centerX() - w / 2f, ventana.centerY() - h / 2f,
            ventana.centerX() + w / 2f, ventana.centerY() + h / 2f
        )
        val radio = 28f
        canvas.drawRoundRect(
            RectF(destino).apply { inset(-6f, -6f) }, radio + 6f, radio + 6f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x5500E5FF }
        )
        canvas.save()
        canvas.clipPath(Path().apply { addRoundRect(destino, radio, radio, Path.Direction.CW) })
        canvas.drawBitmap(
            captura, Rect(0, 0, captura.width, captura.height), destino,
            Paint(Paint.FILTER_BITMAP_FLAG)
        )
        canvas.restore()
        canvas.drawRoundRect(destino, radio, radio, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 3f
            color = NEON
        })

        // Titulo del juego, hasta dos lineas.
        val textoTitulo = titulo.ifBlank { if (es) "Partida" else "Game" }
        val tituloPaint = textPaint(58f, Color.WHITE, Typeface.BOLD)
        val anchoTexto = (ANCHO - 2 * MARGEN).toInt()
        val layout = StaticLayout.Builder
            .obtain(textoTitulo, 0, textoTitulo.length, tituloPaint, anchoTexto)
            .setMaxLines(2)
            .setEllipsize(TextUtils.TruncateAt.END)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .build()
        canvas.save()
        canvas.translate(MARGEN, 1040f)
        layout.draw(canvas)
        canvas.restore()

        // Consola y tiempo de partida.
        val detalle = buildString {
            append(plataforma.ifBlank { "RetroArch" })
            if (minutosJugados > 0) {
                append("  ·  ")
                append(if (es) "$minutosJugados min de partida" else "$minutosJugados min played")
            }
        }
        canvas.drawText(
            detalle, MARGEN, 1040f + layout.height + 52f,
            textPaint(34f, 0xFF9AD9FF.toInt(), Typeface.NORMAL)
        )

        // Pie: linea de neon y la firma.
        canvas.drawRect(MARGEN, 1262f, ANCHO - MARGEN, 1264f, Paint().apply {
            shader = LinearGradient(
                MARGEN, 0f, ANCHO - MARGEN, 0f,
                intArrayOf(NEON, 0x0000E5FF), null, Shader.TileMode.CLAMP
            )
        })
        canvas.drawText(
            "generacionarcade.com/speccyos", MARGEN, 1310f,
            textPaint(28f, 0x99FFFFFF.toInt(), Typeface.NORMAL)
        )

        val carpeta = File(context.cacheDir, "images").apply { mkdirs() }
        limpiarAntiguos(carpeta)
        val fichero = File(carpeta, "speccy_momento_${System.currentTimeMillis()}.png")
        fichero.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        if (captura !== capturaOriginal) captura.recycle()
        fichero
    }.onFailure { Log.e(TAG, "No se pudo generar la tarjeta del momento", it) }.getOrNull()

    /** Abre el panel de compartir. Vale desde un Service: lleva NEW_TASK. */
    fun compartir(context: Context, fichero: File, titulo: String, lang: String) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", fichero)
        val texto = if (lang.startsWith("es"))
            "Mi momento en $titulo con Speccy OS 🎮 generacionarcade.com/speccyos"
        else
            "My moment in $titulo with Speccy OS 🎮 generacionarcade.com/speccyos"
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, texto)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val selector = Intent.createChooser(intent, if (lang.startsWith("es")) "Compartir momento" else "Share moment")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(selector)
    }

    /**
     * Quita las filas y columnas de borde que son practicamente negras.
     * Se muestrea cada 4 px; si al final queda menos del 40 % de la imagen
     * (una pantalla casi negra, un fundido) se devuelve la original tal cual.
     */
    private fun recortarBordesNegros(b: Bitmap): Bitmap {
        val w = b.width
        val h = b.height
        val paso = 4
        val umbral = 28
        fun oscuro(p: Int) = (p shr 16 and 0xFF) < umbral && (p shr 8 and 0xFF) < umbral && (p and 0xFF) < umbral
        fun columnaNegra(x: Int): Boolean { var y = 0; while (y < h) { if (!oscuro(b.getPixel(x, y))) return false; y += paso }; return true }
        fun filaNegra(y: Int): Boolean { var x = 0; while (x < w) { if (!oscuro(b.getPixel(x, y))) return false; x += paso }; return true }
        var izq = 0; while (izq < w - 1 && columnaNegra(izq)) izq++
        var der = w - 1; while (der > izq && columnaNegra(der)) der--
        var arr = 0; while (arr < h - 1 && filaNegra(arr)) arr++
        var aba = h - 1; while (aba > arr && filaNegra(aba)) aba--
        val nw = der - izq + 1
        val nh = aba - arr + 1
        if (nw < w * 0.4f || nh < h * 0.4f) return b
        if (nw == w && nh == h) return b
        return Bitmap.createBitmap(b, izq, arr, nw, nh)
    }

    private fun textPaint(tamano: Float, color: Int, estilo: Int) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        textSize = tamano
        typeface = Typeface.create(Typeface.SANS_SERIF, estilo)
    }

    /** La cache no es un album: se conservan solo los ultimos momentos. */
    private fun limpiarAntiguos(carpeta: File) {
        carpeta.listFiles { f -> f.name.startsWith("speccy_momento_") }
            ?.sortedByDescending { it.lastModified() }
            ?.drop(MAX_GUARDADOS - 1)
            ?.forEach { it.delete() }
    }
}
