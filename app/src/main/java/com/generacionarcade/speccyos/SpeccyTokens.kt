/*
 * Speccy OS — frontend retro para Android
 * Copyright (c) 2026 LV-Webstudio · lv-webstudio.com
 * Desarrollado por Speccy81 (LORPAGC) · administracion@lv-webstudio.com
 * Todos los derechos reservados.
 */
package com.generacionarcade.speccyos

import android.content.Context
import android.content.res.Configuration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import kotlin.math.pow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape

/**
 * SpeccyTokens
 * ----------------------------------------------------------------------------
 * Design system unico de SpeccyOS.
 *
 * EL PROBLEMA
 * -----------
 * La app tenia 198 literales `Color(0x...)` repartidos por 40 ficheros, frente a
 * solo TRES ficheros que usaban `MaterialTheme`. El `ColorScheme` estaba
 * duplicado literalmente dos veces en `ThemeManager` y definia unicamente ocho
 * roles: sin `surfaceVariant`, `outline` ni `error`, cualquier componente
 * Material3 que los use cae en el morado por defecto, fuera de tema.
 *
 * Peor aun, el color se propagaba POR PARAMETRO MANUAL (`primaryColor: Color`)
 * en mas de veinte firmas de composables, lo que hacia imposible cambiar de tema
 * sin tocar veinte funciones.
 *
 * Espaciados sin sistema (6/8/10/12/16/24/32/36 dp), radios (8/10/12/16/20/22),
 * y 203 textos por debajo de 12 sp, seis de ellos a 7 sp: ilegibles en un panel
 * de 4,5 pulgadas y absolutamente ilegibles a tres metros en un televisor.
 *
 * Aqui vive el origen unico de verdad.
 */

// ─────────────────────────────────────────────────────────────────────────────
// TOKENS
// ─────────────────────────────────────────────────────────────────────────────

object Spacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 16.dp
    val lg = 24.dp
    val xl = 32.dp
    val xxl = 48.dp
}

object Radii {
    val sm = 8.dp
    val md = 12.dp
    val lg = 20.dp
    val pill = 999.dp
}

object Elevation {
    val flat = 0.dp
    val raised = 4.dp
    val focused = 12.dp
}

object Focus {
    /** Grosor del anillo de foco. En TV hace falta mas cuerpo para verlo a 3 m. */
    val ring = 2.dp
    val ringTv = 3.dp
    /** Escala del elemento enfocado. Sutil: el anillo es quien comunica el foco. */
    const val scale = 1.06f
}

object Touch {
    /** Objetivo tactil minimo. Habia 125 elementos interactivos por debajo. */
    val min = 48.dp
}

object Overscan {
    /** Margen de seguridad en televisores: el 5 % de cada borde puede recortarse. */
    const val tvFraction = 0.05f
}

// ─────────────────────────────────────────────────────────────────────────────
// CLASE DE DISPOSITIVO
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Resuelve de una vez los tres problemas de tamano que la app trataba igual:
 * handheld 16:10 estrecho, tablet, y television a tres metros.
 */
enum class DeviceClass { HANDHELD, PHONE, TABLET, TV }

val LocalDeviceClass = staticCompositionLocalOf { DeviceClass.PHONE }

fun detectDeviceClass(context: Context, widthDp: Int, heightDp: Int): DeviceClass {
    val uiMode = context.resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK
    if (uiMode == Configuration.UI_MODE_TYPE_TELEVISION) return DeviceClass.TV
    val hasTouch = context.packageManager.hasSystemFeature("android.hardware.touchscreen")
    if (!hasTouch) return DeviceClass.TV
    val shortest = minOf(widthDp, heightDp)
    return when {
        shortest >= 600 -> DeviceClass.TABLET
        // Una consola portatil es apaisada, estrecha y con mandos fisicos.
        widthDp > heightDp && shortest < 420 -> DeviceClass.HANDHELD
        else -> DeviceClass.PHONE
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// TIPOGRAFIA ESCALADA
// ─────────────────────────────────────────────────────────────────────────────

/**
 * PISO DURO DE 12 sp. Ningun texto de la app debe bajar de ahi: los 7 sp y 8 sp
 * que habia no se leen ni en la mano ni, mucho menos, en un televisor.
 */
fun speccyTypography(device: DeviceClass, userScale: Float = 1f): Typography {
    // userScale es el deslizador de "Ajustes de pantalla" (0,7–1,5). Se acota aqui
    // para que un valor guardado corrupto no deje la interfaz ilegible.
    val k = userScale.coerceIn(0.7f, 1.5f) * when (device) {
        DeviceClass.TV -> 1.6f
        DeviceClass.HANDHELD -> 1.15f
        DeviceClass.TABLET -> 1.1f
        DeviceClass.PHONE -> 1f
    }
    return Typography(
        displaySmall = TextStyle(fontSize = 32.sp * k, fontWeight = FontWeight.Black),
        headlineMedium = TextStyle(fontSize = 26.sp * k, fontWeight = FontWeight.Black),
        titleLarge = TextStyle(fontSize = 22.sp * k, fontWeight = FontWeight.Bold),
        titleMedium = TextStyle(fontSize = 18.sp * k, fontWeight = FontWeight.Bold),
        titleSmall = TextStyle(fontSize = 15.sp * k, fontWeight = FontWeight.SemiBold),
        bodyLarge = TextStyle(fontSize = 16.sp * k),
        bodyMedium = TextStyle(fontSize = 14.sp * k),
        bodySmall = TextStyle(fontSize = 13.sp * k),
        labelLarge = TextStyle(fontSize = 14.sp * k, fontWeight = FontWeight.SemiBold),
        labelMedium = TextStyle(fontSize = 12.sp * k, fontWeight = FontWeight.Medium),
        labelSmall = TextStyle(fontSize = 12.sp * k, fontWeight = FontWeight.Medium)
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// PALETA BASE
// ─────────────────────────────────────────────────────────────────────────────

object SpeccyPalette {
    val bg = Color(0xFF0A0A0F)
    val surface = Color(0xFF14181D)
    val surfaceVariant = Color(0xFF1E252B)
    val outline = Color(0xFF3A454C)
    val onSurface = Color(0xFFE6EDEF)
    val onSurfaceVariant = Color(0xFFA9B6BC)
    val error = Color(0xFFF0827A)
    val ok = Color(0xFF67C795)
    val warn = Color(0xFFE0AC4B)

    /**
     * Oro "Imperial": no es un rol del tema, es una marca de producto (usuario
     * de pago). Vive aqui como token para que no siga repetido como 0xFFFFD700
     * suelto por las pantallas y se pueda cambiar en un solo sitio.
     */
    val imperial = Color(0xFFFFD700)
}

/**
 * Construye el ColorScheme completo a partir del color de acento del usuario.
 * `ThemeManager` tenia este bloque DUPLICADO literalmente dos veces y solo con
 * ocho roles definidos.
 */
fun speccyColorScheme(primary: Color, accent: Color) = darkColorScheme(
    primary = primary,
    onPrimary = Color.Black,
    primaryContainer = primary.copy(alpha = 0.22f),
    onPrimaryContainer = primary,
    secondary = accent,
    onSecondary = Color.Black,
    tertiary = accent,
    onTertiary = Color.Black,
    background = SpeccyPalette.bg,
    onBackground = SpeccyPalette.onSurface,
    surface = SpeccyPalette.surface,
    onSurface = SpeccyPalette.onSurface,
    surfaceVariant = SpeccyPalette.surfaceVariant,
    onSurfaceVariant = SpeccyPalette.onSurfaceVariant,
    outline = SpeccyPalette.outline,
    outlineVariant = SpeccyPalette.outline.copy(alpha = 0.5f),
    error = SpeccyPalette.error,
    onError = Color.Black
)

/**
 * Devuelve el color de contenido (texto/iconos) legible sobre `background`.
 *
 * No sirve fijar onPrimary a mano en cada sitio: el color de relleno puede ser
 * el primario del tema (que el usuario elige entre siete presets neon), el
 * color de identidad de un sector de Ajustes, el rojo de error o el azul de
 * PayPal. Sobre el morado de CUENTA (#9C27B0) el negro da 3,33:1 y el blanco
 * 5,32:1; sobre el cian es justo al reves. Aqui se mide de verdad y gana el que
 * mas contraste da.
 *
 * IMPORTANTE: si el relleno es translucido, componlo antes sobre el fondo
 * (Color.compositeOver), o se mide un color que nunca se ve en pantalla.
 */
@Composable
fun speccyContentColorOn(background: Color): Color {
    val claro = SpeccyPalette.onSurface
    val oscuro = Color.Black
    return if (contrastRatio(oscuro, background) >= contrastRatio(claro, background)) oscuro else claro
}

/** Razon de contraste WCAG entre dos colores OPACOS. */
private fun contrastRatio(a: Color, b: Color): Float {
    val la = relativeLuminance(a)
    val lb = relativeLuminance(b)
    val alto = maxOf(la, lb)
    val bajo = minOf(la, lb)
    return (alto + 0.05f) / (bajo + 0.05f)
}

private fun relativeLuminance(c: Color): Float {
    fun canal(v: Float) = if (v <= 0.03928f) v / 12.92f
                          else ((v + 0.055f) / 1.055f).toDouble().pow(2.4).toFloat()
    return 0.2126f * canal(c.red) + 0.7152f * canal(c.green) + 0.0722f * canal(c.blue)
}

// ─────────────────────────────────────────────────────────────────────────────
// ENVOLTORIO DE TEMA
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun SpeccyTheme(
    primary: Color = ThemeManager.primaryColor,
    accent: Color = ThemeManager.accentColor,
    fontScale: Float = 1f,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val config = LocalConfiguration.current
    val device = remember(config.screenWidthDp, config.screenHeightDp, config.uiMode) {
        detectDeviceClass(context, config.screenWidthDp, config.screenHeightDp)
    }

    CompositionLocalProvider(LocalDeviceClass provides device) {
        MaterialTheme(
            colorScheme = speccyColorScheme(primary, accent),
            typography = speccyTypography(device, fontScale),
            shapes = Shapes(
                extraSmall = RoundedCornerShape(Radii.sm),
                small = RoundedCornerShape(Radii.sm),
                medium = RoundedCornerShape(Radii.md),
                large = RoundedCornerShape(Radii.lg),
                extraLarge = RoundedCornerShape(Radii.lg)
            ),
            content = content
        )
    }
}

/**
 * Coste de los efectos segun el equipo real.
 *
 * El blur gaussiano de Compose es de lo mas caro que se le puede pedir a una GPU
 * modesta -el coste crece con el cuadrado del radio- y varios de los cinco temas
 * lo usan a PANTALLA COMPLETA. En un Mali-G57, que es el hardware objetivo, un
 * radio de 40 dp cubriendo toda la pantalla se come el presupuesto de frame el
 * solo.
 *
 * Aqui no se quita el efecto: se ajusta el radio al equipo. Los dispositivos de
 * gama alta (tier 4 y 5: PS2 completo, Switch) conservan el radio de diseno
 * exacto; por debajo se reduce, que a las opacidades bajas con las que se usa
 * (0.08-0.4) es practicamente indistinguible y cuesta una fraccion.
 */
object SpeccyFx {

    /** Radio de blur adaptado al tier del equipo, conservando la identidad visual. */
    fun blurRadius(base: Dp): Dp =
        if (SpeccyPerformanceTuner.activeDevice().tier.rank >= 4) base
        else (base.value * 0.4f).dp.coerceAtLeast(6.dp)
}
