/*
 * Speccy OS — frontend retro para Android
 * Copyright (c) 2026 LV-Webstudio · lv-webstudio.com
 * Desarrollado por Speccy81 (LORPAGC) · administracion@lv-webstudio.com
 * Todos los derechos reservados.
 */
package com.generacionarcade.speccyos

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import androidx.core.content.FileProvider
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

object ImageProcessUtils {

    /**
     * Convierte un flujo de entrada de imagen a un formato WebP comprimido.
     */
    fun convertToWebP(inputStream: InputStream, quality: Int = 80): ByteArray? {
        return try {
            val bitmap = BitmapFactory.decodeStream(inputStream) ?: return null
            val outputStream = ByteArrayOutputStream()
            
            val format = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Bitmap.CompressFormat.WEBP_LOSSY
            } else {
                @Suppress("DEPRECATION")
                Bitmap.CompressFormat.WEBP
            }
            
            bitmap.compress(format, quality, outputStream)
            val result = outputStream.toByteArray()
            
            bitmap.recycle() // Liberar memoria
            result
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Genera una tarjeta de Trivial (Bitmap) estilo Retro y devuelve la URI para compartir.
     */
    fun generateTriviaShareCard(context: Context, rank: String, scoreStr: String, primaryColorHex: Int): android.net.Uri? {
        try {
            val width = 1080
            val height = 1080
            // Usamos createBitmap de la forma recomendada en KTX si estuviera, 
            // pero para máxima compatibilidad seguimos usando la factoría nativa.
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)

            // 1. FONDO NEGRO Y SCANLINES (Efecto CRT)
            canvas.drawColor(Color.BLACK)
            val scanlinePaint = Paint().apply {
                color = Color.argb(40, 255, 255, 255)
                strokeWidth = 2f
            }
            for (y in 0..height step 8) {
                canvas.drawLine(0f, y.toFloat(), width.toFloat(), y.toFloat(), scanlinePaint)
            }

            // 2. BORDE NEÓN
            val borderPaint = Paint().apply {
                color = primaryColorHex
                style = Paint.Style.STROKE
                strokeWidth = 16f
                setShadowLayer(30f, 0f, 0f, primaryColorHex)
            }
            val inset = 40f
            canvas.drawRoundRect(inset, inset, width - inset, height - inset, 32f, 32f, borderPaint)

            // 3. TEXTOS (Usando Typeface Monospace nativo para look pixelado/terminal)
            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = Typeface.MONOSPACE
                textAlign = Paint.Align.CENTER
            }

            // Título: SPECCY OS
            textPaint.color = Color.WHITE
            textPaint.textSize = 60f
            textPaint.letterSpacing = 0.2f
            canvas.drawText("SPECCY OS E5 ULTRA", width / 2f, 180f, textPaint)

            // Subtítulo: TRIVIAL ARCADE
            textPaint.color = primaryColorHex
            textPaint.textSize = 90f
            textPaint.letterSpacing = 0.1f
            textPaint.setShadowLayer(15f, 0f, 0f, primaryColorHex)
            canvas.drawText("TRIVIAL ARCADE", width / 2f, 320f, textPaint)

            // Rango
            textPaint.clearShadowLayer()
            textPaint.color = Color.LTGRAY
            textPaint.textSize = 50f
            textPaint.letterSpacing = 0.05f
            canvas.drawText("RANGO IMPERIAL ALCANZADO:", width / 2f, 520f, textPaint)

            textPaint.color = Color.YELLOW
            textPaint.textSize = 100f
            textPaint.isFakeBoldText = true
            textPaint.setShadowLayer(20f, 0f, 0f, Color.parseColor("#888800"))
            canvas.drawText(rank.uppercase(), width / 2f, 650f, textPaint)

            // Puntuación
            textPaint.clearShadowLayer()
            textPaint.isFakeBoldText = false
            textPaint.color = Color.WHITE
            textPaint.textSize = 60f
            canvas.drawText("SCORE: $scoreStr", width / 2f, 850f, textPaint)

            // Footer
            textPaint.color = Color.DKGRAY
            textPaint.textSize = 35f
            canvas.drawText("¿TE ATREVES A SUPERARME?", width / 2f, 980f, textPaint)

            // 4. GUARDAR EN CACHÉ Y OBTENER URI
            val cachePath = File(context.cacheDir, "images")
            cachePath.mkdirs()
            val file = File(cachePath, "speccy_trivia_result.png")
            val fileOutputStream = FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, fileOutputStream)
            fileOutputStream.close()

            return FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }
}
