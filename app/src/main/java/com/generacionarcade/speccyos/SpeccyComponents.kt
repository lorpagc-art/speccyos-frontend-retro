package com.generacionarcade.speccyos

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * SpeccyComponents
 * ----------------------------------------------------------------------------
 * Componentes base con FOCO Y ACCESIBILIDAD DE FABRICA, no opcionales.
 *
 * QUE ARREGLAN
 * ------------
 * Diez pantallas completas del proyecto tenian CERO `focusable` y CERO
 * `onKeyEvent`: ViralOnboarding, QuickLaunchBar, MoodLauncherScreen,
 * RouletteScreen, GamingDnaScreen, BenchmarkScreen, HardwareSettingsScreen,
 * CreditsScreen, ScrapingDashboardScreen, ThemeClassic y ThemeCyberpunk.
 *
 * El caso mas grave era el ONBOARDING: el primer arranque entero (seleccion de
 * hardware, "confirmar modelo", apodo, "empezar") solo respondia a `clickable{}`,
 * mientras el manifiesto declara explicitamente que la pantalla tactil NO es
 * obligatoria. En una Odin conectada a la television, o en Android TV, la app
 * era un ladrillo desde el primer segundo.
 *
 * Ademas: de 81 `contentDescription`, 65 eran null, y `semantics` no aparecia ni
 * una vez en los 97 ficheros. Aqui la etiqueta es un parametro OBLIGATORIO.
 */

/** Teclas que cuentan como "aceptar" en un mando, un teclado o un televisor. */
private val CONFIRM_KEYS = setOf(
    Key.Enter, Key.NumPadEnter, Key.DirectionCenter,
    Key.ButtonA, Key.Spacebar
)

/**
 * Contenedor pulsable universal.
 *
 * Garantiza, por construccion:
 *  - foco navegable con D-pad y anillo visible,
 *  - activacion con A / Enter / centro del D-pad ademas del toque,
 *  - objetivo tactil de 48 dp,
 *  - etiqueta para TalkBack.
 */
@Composable
fun SpeccyFocusable(
    label: String,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentAlignment: Alignment = Alignment.Center,
    content: @Composable BoxScope.() -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val device = LocalDeviceClass.current
    val scale by animateFloatAsState(
        targetValue = if (focused) Focus.scale else 1f,
        label = "speccyFocusScale"
    )
    val ring = if (device == DeviceClass.TV) Focus.ringTv else Focus.ring
    val shape = MaterialTheme.shapes.medium

    Box(
        modifier = modifier
            .defaultMinSize(minWidth = Touch.min, minHeight = Touch.min)
            .semantics {
                this.role = Role.Button
                this.contentDescription = label
            }
            .onFocusChanged { focused = it.isFocused }
            // NO se añade `.focusable()`: `clickable` ya instala su propio nodo de
            // foco. Encadenar ambos crea DOS paradas de foco anidadas en el mismo
            // componente y obliga a pulsaciones de mas para atravesar cada elemento,
            // que es justo el problema que este componente viene a resolver.
            .onKeyEvent { event ->
                if (enabled && event.type == KeyEventType.KeyDown && event.key in CONFIRM_KEYS) {
                    onSelect(); true
                } else false
            }
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onSelect() }
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .border(
                width = if (focused) ring else 0.dp,
                color = if (focused) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape = shape
            ),
        contentAlignment = contentAlignment,
        content = content
    )
}

/**
 * Fila de interruptor con TITULO Y RESUMEN.
 * `UltraToggleRow` solo aceptaba `title`, asi que ajustes como "Modo Nebula" o
 * "Sync cloud" no explicaban en ningun sitio que hacen.
 */
@Composable
fun SpeccyToggleRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    enabled: Boolean = true
) {
    SpeccyFocusable(
        label = if (summary != null) "$title. $summary" else title,
        onSelect = { if (enabled) onCheckedChange(!checked) },
        enabled = enabled,
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (!summary.isNullOrBlank()) {
                    Text(
                        summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.size(Spacing.md))
            Switch(
                checked = checked,
                onCheckedChange = { if (enabled) onCheckedChange(it) },
                enabled = enabled,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.primary,
                    checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    }
}

/**
 * Estados de UI unificados. En toda la app habia solo 12 indicadores de carga y
 * dos comprobaciones de lista vacia; no existia ningun estado de error para un
 * scraping fallido, una BIOS ausente o un emulador no instalado.
 */
sealed interface SpeccyUiState {
    data object Loading : SpeccyUiState
    data class Empty(val title: String, val body: String) : SpeccyUiState
    data class Error(val title: String, val body: String, val onRetry: (() -> Unit)? = null) : SpeccyUiState
    data object Content : SpeccyUiState
}

@Composable
fun SpeccyStateBox(
    state: SpeccyUiState,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    when (state) {
        is SpeccyUiState.Content -> content()

        is SpeccyUiState.Loading -> Box(modifier.fillMaxSize(), Alignment.Center) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }

        is SpeccyUiState.Empty -> Box(modifier.fillMaxSize(), Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(Spacing.xl)
            ) {
                Text(
                    state.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(Spacing.sm))
                Text(
                    state.body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }

        is SpeccyUiState.Error -> Box(modifier.fillMaxSize(), Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(Spacing.xl)
            ) {
                Text(
                    state.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(Spacing.sm))
                Text(
                    state.body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                state.onRetry?.let { retry ->
                    Spacer(Modifier.height(Spacing.md))
                    SpeccyFocusable(label = "Reintentar", onSelect = retry) {
                        Text(
                            "Reintentar",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Superposicion de lanzamiento.
 *
 * Antes, al pulsar A el usuario no veia absolutamente nada mientras RetroArch
 * arrancaba: el unico indicador existente (`isSyncingCloud`) solo aparecia si la
 * sincronizacion en la nube estaba activada. Y los 21 `Toast` que se usaban como
 * canal de error principal son invisibles en modo inmersivo a pantalla completa
 * y no llegan a quien navega con mando.
 */
@Composable
fun SpeccyLaunchOverlay(visible: Boolean, gameTitle: String?) {
    AnimatedVisibility(visible = visible) {
        Box(
            Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background.copy(alpha = 0.86f)),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(Spacing.md))
                Text(
                    if (gameTitle.isNullOrBlank()) "Iniciando..." else "Iniciando $gameTitle...",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = Spacing.xl)
                )
            }
        }
    }
}

/**
 * Centra el elemento enfocado en la lista en lugar de dejarlo pegado al borde.
 *
 * Los cinco sitios que reimplementaban la navegacion por D-pad usaban
 * `scrollToItem(index)` sin animacion y sin centrado: en un `LazyColumn` el
 * elemento seleccionado quedaba clavado arriba del todo.
 */
suspend fun LazyListState.centerOnItem(index: Int) {
    val info = layoutInfo
    // LazyListState sirve para LazyColumn y LazyRow: hay que mirar el eje de
    // scroll real, no asumir vertical (en una fila la altura tambien es > 0 y el
    // desplazamiento calculado no centraba nada).
    val viewport = if (info.orientation == Orientation.Vertical) info.viewportSize.height
                   else info.viewportSize.width
    val itemSize = info.visibleItemsInfo.firstOrNull()?.size ?: 0
    val offset = if (viewport > 0 && itemSize > 0) -(viewport - itemSize) / 2 else 0
    animateScrollToItem(index.coerceAtLeast(0), offset)
}
