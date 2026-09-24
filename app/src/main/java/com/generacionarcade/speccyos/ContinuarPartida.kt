package com.generacionarcade.speccyos

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import coil.compose.AsyncImage
import com.generacionarcade.speccyos.theme.NeonBlue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * "Continuar donde lo dejaste".
 *
 * Al cerrar una partida, RetroArch deja `<juego>.state.auto` gracias al
 * override de core que escribe [RetroArchOverrides] (`savestate_auto_save`).
 * Esta tarjeta busca ese fichero para el ultimo juego lanzado y, si existe,
 * ofrece reanudarlo con un toque: el lanzamiento va con
 * [RetroArchOverrides.REANUDAR], que pone `savestate_auto_load` solo para
 * esa partida. Un lanzamiento normal del mismo juego sigue empezando de cero.
 *
 * Es una pastilla discreta en la misma banda que el reto del dia; se
 * reevalua cada vez que el dashboard vuelve a primer plano, que es cuando
 * puede haber cambiado.
 */
@Composable
fun ContinuarPartidaCard(
    mainViewModel: MainViewModel,
    settingsManager: SettingsManager,
    onResume: (Game) -> Unit,
    topPadding: Dp = 104.dp
) {
    if (!settingsManager.continuarPartida) return
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val es = remember { settingsManager.appLanguage.startsWith("es") }

    // Se vuelve a mirar en cada ON_RESUME: al volver de RetroArch el
    // .state.auto es nuevo, o ha dejado de existir.
    var vuelta by remember { mutableIntStateOf(0) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) vuelta++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    var juego by remember { mutableStateOf<Game?>(null) }
    var fecha by remember { mutableStateOf<Long?>(null) }
    var ocultoPara by remember { mutableStateOf(settingsManager.continuarOcultoPara) }

    LaunchedEffect(vuelta) {
        val ruta = settingsManager.lastPlayedGamePath
        if (ruta.isBlank()) { juego = null; return@LaunchedEffect }
        val g = mainViewModel.findGameByPath(ruta)
        if (g == null) { juego = null; return@LaunchedEffect }
        val f = withContext(Dispatchers.IO) {
            val plataforma = g.platformId.lowercase()
            val core = SpeccyGameOverrides(context).getCore(g)
                ?: SpeccyCoreResolver.resolver(
                    context = context,
                    platformId = plataforma,
                    sys = RetroArchDatabase.findSystemById(plataforma),
                    preferidoDelUsuario = settingsManager.getPreferredCore(plataforma)
                )
            RetroArchOverrides(context).fechaAutoEstado(g, core)
        }
        juego = g
        fecha = f
    }

    val g = juego ?: return
    val f = fecha ?: return
    if (ocultoPara == g.path) return
    // Un estado de hace mas de un mes ya no es "donde lo dejaste".
    val edadMs = System.currentTimeMillis() - f
    if (edadMs > 30L * 24 * 3600 * 1000) return

    Surface(
        modifier = Modifier
            .padding(top = topPadding, end = 24.dp)
            .widthIn(max = 280.dp)
            .clickable { onResume(g) },
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
        border = BorderStroke(1.dp, NeonBlue.copy(alpha = 0.35f)),
        shadowElevation = 6.dp
    ) {
        Row(
            modifier = Modifier.padding(start = 8.dp, top = 6.dp, bottom = 6.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!g.boxArt.isNullOrBlank()) {
                AsyncImage(
                    model = g.boxArt,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(width = 30.dp, height = 40.dp)
                        .clip(RoundedCornerShape(6.dp))
                )
            } else {
                Icon(Icons.Default.PlayArrow, null, tint = NeonBlue, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f, fill = false)) {
                Text(
                    if (es) "CONTINUAR" else "CONTINUE",
                    color = NeonBlue,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp
                )
                Text(
                    g.title,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(1.dp))
                Text(
                    haceCuanto(edadMs, es),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.sp
                )
            }
            IconButton(
                onClick = {
                    settingsManager.continuarOcultoPara = g.path
                    ocultoPara = g.path
                },
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    Icons.Default.Close,
                    if (es) "No volver a ofrecer esta partida" else "Stop offering this game",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

private fun haceCuanto(ms: Long, es: Boolean): String {
    val min = ms / 60_000L
    val h = min / 60
    val d = h / 24
    return when {
        min < 1 -> if (es) "ahora mismo" else "just now"
        min < 60 -> if (es) "hace $min min" else "$min min ago"
        h < 24 -> if (es) "hace $h h" else "$h h ago"
        d == 1L -> if (es) "ayer" else "yesterday"
        else -> if (es) "hace $d días" else "$d days ago"
    }
}
