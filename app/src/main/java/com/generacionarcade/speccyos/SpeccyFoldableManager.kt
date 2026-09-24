/*
 * Speccy OS — frontend retro para Android
 * Copyright (c) 2026 LV-Webstudio · lv-webstudio.com
 * Desarrollado por Speccy81 (LORPAGC) · administracion@lv-webstudio.com
 * Todos los derechos reservados.
 */
package com.generacionarcade.speccyos

import android.app.Activity
import android.content.Context
import android.hardware.display.DisplayManager
import android.view.Display
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import kotlinx.coroutines.flow.map

/**
 * SpeccyFoldableManager
 * ----------------------------------------------------------------------------
 * Soporte de DOBLE PANTALLA y PLEGABLES, al estilo de lo que hacen los
 * frontends de escritorio (ES-DE con su segunda vista, Pegasus con paneles).
 *
 * Cubre tres casos que hoy la app trata igual y no debería:
 *
 *  1. PLEGABLE EN MESA (half-opened, bisagra horizontal) — Galaxy Z Fold/Flip,
 *     Retroid Pocket Flip 2. Arriba el arte/vídeo del juego, abajo la lista y
 *     los controles. Es el modo "Flex" nativo de Android.
 *  2. PLEGABLE ABIERTO (flat, pantalla grande) — dos paneles lado a lado:
 *     sistemas | juegos, con el detalle a la derecha.
 *  3. DOBLE PANTALLA FÍSICA — AYANEO Pocket DS, ONEXSUGAR Sugar 1. Hay un
 *     Display secundario real: SpeccyOS puede mandar allí la pantalla
 *     secundaria de NDS/3DS/Wii U o el arte del juego mientras se navega.
 *
 * `ThemeDualScreen` ya existe en el proyecto pero se elegía a mano. Aquí se
 * decide por el estado real del dispositivo.
 */
object SpeccyFoldableManager {

    enum class Posture {
        /** Pantalla única normal. */
        SINGLE,
        /** Plegable abierto del todo: caben dos paneles lado a lado. */
        UNFOLDED_WIDE,
        /** Plegable en ángulo con bisagra horizontal: modo mesa / Flex. */
        TABLETOP,
        /** Plegable en ángulo con bisagra vertical: modo libro. */
        BOOK,
        /** Dos paneles físicos independientes (consolas tipo DS). */
        DUAL_PHYSICAL
    }

    data class Layout(
        val posture: Posture,
        /** Posición de la bisagra desde el borde superior (o izquierdo en libro). */
        val hingeOffset: Dp = 0.dp,
        /** Grosor ocupado por la bisagra: hay que dejarlo libre de contenido. */
        val hingeThickness: Dp = 0.dp,
        val isSeparating: Boolean = false
    ) {
        /** ¿Debe la interfaz partirse en dos zonas? */
        val isDual: Boolean
            get() = posture != Posture.SINGLE

        /** ¿La división es horizontal (arriba/abajo)? */
        val isHorizontalSplit: Boolean
            get() = posture == Posture.TABLETOP || posture == Posture.DUAL_PHYSICAL
    }

    /** Detecta si hay un segundo panel físico utilizable (no un cast ni HDMI). */
    fun secondaryDisplay(context: Context): Display? {
        val dm = context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager ?: return null
        return dm.displays.firstOrNull {
            it.displayId != Display.DEFAULT_DISPLAY &&
                (it.flags and Display.FLAG_PRESENTATION) != 0
        }
    }

    fun hasPhysicalDualScreen(context: Context): Boolean =
        SpeccyHardwareRegistry.detect().dualScreen || secondaryDisplay(context) != null

    /**
     * Observa la postura real del dispositivo.
     * Se usa desde cualquier pantalla para decidir el layout.
     */
    @Composable
    fun rememberLayout(): Layout {
        val context = LocalContext.current
        val activity = remember(context) { context.findActivity() }
        var layout by remember { mutableStateOf(Layout(Posture.SINGLE)) }

        // Doble pantalla física: no depende de la bisagra, se comprueba una vez.
        LaunchedEffect(activity) {
            if (activity != null && hasPhysicalDualScreen(activity)) {
                layout = Layout(Posture.DUAL_PHYSICAL)
            }
        }

        LaunchedEffect(activity) {
            if (activity == null) return@LaunchedEffect
            val density = activity.resources.displayMetrics.density
            WindowInfoTracker.getOrCreate(activity)
                .windowLayoutInfo(activity)
                .map { info -> info.displayFeatures.filterIsInstance<FoldingFeature>().firstOrNull() }
                .collect { fold ->
                    if (fold == null) {
                        // Sin bisagra: si es un plegable abierto del todo, el ancho manda.
                        val wDp = activity.resources.configuration.screenWidthDp
                        layout = if (layout.posture == Posture.DUAL_PHYSICAL) layout
                        else if (wDp >= 720) Layout(Posture.UNFOLDED_WIDE)
                        else Layout(Posture.SINGLE)
                        return@collect
                    }
                    // El Rect de la bisagra es largo y estrecho: el grosor es el MENOR
                    // de los dos lados (con coerceAtLeast se reservaba media pantalla).
                    val thickness = ((fold.bounds.height().coerceAtMost(fold.bounds.width())) / density).dp
                    val horizontal = fold.orientation == FoldingFeature.Orientation.HORIZONTAL
                    val offset = if (horizontal) (fold.bounds.top / density).dp
                                 else (fold.bounds.left / density).dp

                    layout = when {
                        // Sugar 1 / Pocket DS / Flip 2 tienen bisagra Y doble panel real.
                        layout.posture == Posture.DUAL_PHYSICAL -> layout
                        fold.state == FoldingFeature.State.HALF_OPENED && horizontal ->
                            Layout(Posture.TABLETOP, offset,
                                ((fold.bounds.height()) / density).dp, fold.isSeparating)
                        fold.state == FoldingFeature.State.HALF_OPENED ->
                            Layout(Posture.BOOK, offset,
                                ((fold.bounds.width()) / density).dp, fold.isSeparating)
                        fold.isSeparating ->
                            Layout(Posture.BOOK, offset, thickness, true)
                        else -> Layout(Posture.UNFOLDED_WIDE)
                    }
                }
        }
        return layout
    }

    /**
     * Tema recomendado según la postura. Se enchufa en ThemeManager para que
     * el launcher cambie solo al plegar/desplegar, sin que el usuario entre
     * en ajustes.
     */
    fun recommendedThemeFor(layout: Layout, userTheme: String): String = when (layout.posture) {
        // Bisagra horizontal (mesa) o doble panel fisico: la interfaz de dos
        // pantallas es literalmente para esto.
        Posture.TABLETOP, Posture.DUAL_PHYSICAL -> SettingsManager.THEME_DUAL_SCREEN
        // Abierto del todo o en libro: hay ancho de sobra, se respeta lo que
        // haya elegido el usuario.
        Posture.BOOK, Posture.UNFOLDED_WIDE -> userTheme
        Posture.SINGLE -> userTheme
    }

    private fun Context.findActivity(): Activity? {
        var c: Context? = this
        while (c is android.content.ContextWrapper) {
            if (c is Activity) return c
            c = c.baseContext
        }
        return null
    }
}
