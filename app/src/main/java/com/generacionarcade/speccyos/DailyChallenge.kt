package com.generacionarcade.speccyos

/**
 * DailyChallenge.kt
 * ─────────────────────────────────────────────────────────────────
 * Sistema de Reto Diario + Máquina del Tiempo.
 *
 * CÓMO FUNCIONA:
 *  1. Firebase Remote Config empuja el reto del día (JSON):
 *       { "title":"Sonic 1 — Zona Verde",
 *         "platform":"megadrive",
 *         "objective":"Llega al boss en menos de 3 minutos",
 *         "points":500,
 *         "date":"2026-06-14" }
 *  2. El resultado (tiempo, puntos) se sube a Firestore y se muestra
 *     en el Ranking Global (ver DailyChallengeRanking más abajo).
 *  3. Si el usuario tiene ese juego en su biblioteca, el botón "JUGAR"
 *     lo lanza directamente en RetroArch con el save-state correcto.
 *  4. "Hoy en la historia" usa TimeMachineManager.getGamesReleasedToday()
 *     que ya existe en el proyecto — solo lo conectamos a la UI.
 *
 * INTEGRACIÓN:
 *   En SpeccyDashboard.kt añade DailyChallengeCard() como primer
 *   elemento del LazyColumn principal (antes del carrusel de plataformas).
 * ─────────────────────────────────────────────────────────────────
 */

import android.content.Context
import android.util.Log
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.unit.sp
import com.generacionarcade.speccyos.theme.NeonBlue
import com.google.firebase.Firebase
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.firestore
import com.google.firebase.remoteconfig.remoteConfig
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.tasks.await
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle

// ─── DATA MODELS ──────────────────────────────────────────────────

data class DailyChallenge(
    val title: String,
    val platform: String,
    val objective: String,
    val points: Int,
    val date: String,
    val tipFromArchitect: String = ""
)

data class ChallengeRankingEntry(
    val rank: Int,
    val userName: String,
    val device: String,
    val score: Int,
    val timeMs: Long
)

// ─── MANAGER ──────────────────────────────────────────────────────

object DailyChallengeManager {

    private const val TAG = "DailyChallengeManager"
    private const val COLLECTION = "daily_challenge_results"

    private val _challenge = MutableStateFlow<DailyChallenge?>(null)
    val challenge: StateFlow<DailyChallenge?> = _challenge.asStateFlow()

    private val _ranking = MutableStateFlow<List<ChallengeRankingEntry>>(emptyList())
    val ranking: StateFlow<List<ChallengeRankingEntry>> = _ranking.asStateFlow()

    /** Carga el reto del día desde Firebase Remote Config */
    suspend fun loadTodayChallenge() = withContext(Dispatchers.IO) {
        try {
            Firebase.remoteConfig.fetchAndActivate().await()
            val json = Firebase.remoteConfig.getString("daily_challenge")
            if (json.isNotBlank() && json != "0") {
                val obj = JSONObject(json)
                _challenge.value = DailyChallenge(
                    title = obj.optString("title", "Reto del día"),
                    platform = obj.optString("platform", ""),
                    objective = obj.optString("objective", ""),
                    points = obj.optInt("points", 100),
                    date = obj.optString("date", todayString()),
                    tipFromArchitect = obj.optString("tip", "")
                )
                loadRankingForToday()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error cargando reto: ${e.message}")
            // Reto por defecto si Remote Config falla
            _challenge.value = DailyChallenge(
                title = "Sonic the Hedgehog",
                platform = "megadrive",
                objective = "Completa Green Hill Zone Acto 1 en menos de 45 segundos",
                points = 300,
                date = todayString(),
                tipFromArchitect = "Mantén pulsado el botón de salto al inicio para coger velocidad máxima."
            )
        }
    }

    /** Sube el resultado del usuario a Firestore */
    suspend fun submitResult(
        userName: String,
        deviceModel: String,
        scorePoints: Int,
        elapsedMs: Long
    ) = withContext(Dispatchers.IO) {
        try {
            // Las reglas de Firestore exigen uid == request.auth.uid: sin sesión
            // la escritura se rechaza, así que salimos antes de intentarlo.
            val uid = SpeccyIdentity.ensureSignedIn() ?: return@withContext
            val today = todayString()
            Firebase.firestore
                .collection(COLLECTION)
                .document(today)
                .collection("entries")
                .add(
                    mapOf(
                        "uid"         to uid,
                        "userName"    to userName.take(32),
                        "device"      to deviceModel,
                        "score"       to scorePoints,
                        "elapsedMs"   to elapsedMs,
                        "timestamp"   to FieldValue.serverTimestamp()
                    )
                )
                .await()
            loadRankingForToday()
        } catch (e: Exception) {
            Log.e(TAG, "Error subiendo resultado: ${e.message}")
        }
    }

    private suspend fun loadRankingForToday() = withContext(Dispatchers.IO) {
        try {
            val today = todayString()
            val snapshot = Firebase.firestore
                .collection(COLLECTION)
                .document(today)
                .collection("entries")
                .orderBy("score", Query.Direction.DESCENDING)
                .limit(10)
                .get()
                .await()

            _ranking.value = snapshot.documents.mapIndexed { i, doc ->
                ChallengeRankingEntry(
                    rank = i + 1,
                    userName = doc.getString("userName") ?: "Desconocido",
                    device = doc.getString("device") ?: "",
                    score = (doc.getLong("score") ?: 0L).toInt(),
                    timeMs = doc.getLong("elapsedMs") ?: 0L
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error cargando ranking: ${e.message}")
        }
    }

    private fun todayString() =
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
}

// ─── COMPOSABLE PRINCIPAL ─────────────────────────────────────────

@Composable
fun DailyChallengeCard(
    mainViewModel: MainViewModel,
    userStatusManager: UserStatusManager,
    settingsManager: SettingsManager,
    onLaunchGame: (Game) -> Unit
) {
    val challenge by DailyChallengeManager.challenge.collectAsStateWithLifecycle()
    val ranking by DailyChallengeManager.ranking.collectAsStateWithLifecycle()
    val userName by userStatusManager.userName.collectAsStateWithLifecycle()
    val todayGames by mainViewModel.gamesReleasedToday.collectAsStateWithLifecycle()

    var showRanking by remember { mutableStateOf(false) }
    var hasPlayed by remember { mutableStateOf(false) }

    // Cierre PERSISTENTE. Antes era un `remember` normal, asi que la tarjeta
    // volvia sola en cuanto el composable salia de la composicion: al entrar en
    // una plataforma, al saltar el modo atraccion o al reiniciar. Ahora el
    // cierre se guarda con la fecha del reto y dura hasta el reto siguiente.
    var hiddenOn by remember { mutableStateOf(settingsManager.dailyChallengeHiddenOn) }

    LaunchedEffect(Unit) {
        DailyChallengeManager.loadTodayChallenge()
    }

    val ch = challenge ?: return
    if (hiddenOn == ch.date) return

    // Juego real de la biblioteca al que se refiere el reto. Se resuelve una
    // vez por reto, fuera del hilo principal.
    var matchingGame by remember(ch.title, ch.platform) { mutableStateOf<Game?>(null) }
    var lookupDone by remember(ch.title, ch.platform) { mutableStateOf(false) }
    LaunchedEffect(ch.title, ch.platform) {
        matchingGame = mainViewModel.findGameForChallenge(ch.platform, ch.title)
        lookupDone = true
    }

    // ── DISCRETO POR DEFECTO ─────────────────────────────────────────────
    //
    // En el dashboard solo se ve una pastilla pequena. El detalle -consejo del
    // Arquitecto, botones y ranking- se abre al tocarla, en un dialogo centrado.
    //
    // Antes esto era un panel `fillMaxWidth()` con el fondo al 5 % de opacidad
    // pegado al borde superior: se comia media pantalla, dejaba ver el carrusel
    // por debajo -no se leia ni una cosa ni la otra- y competia con el carrusel,
    // que es lo que el usuario viene a mirar.
    //
    // La posicion (arriba a la derecha, por debajo del bloque de usuario) es la
    // unica banda libre en los tres temas que ofrece Ajustes: en Ultra queda
    // bajo el avatar, en Speccy OS bajo el chip de CPU/TEMP y por encima de la
    // rejilla, y en Pure sobre arte sin interfaz.
    var expanded by remember { mutableStateOf(false) }

    ChipRetoDelDia(
        challenge = ch,
        onOpen = { expanded = true },
        onHide = {
            settingsManager.dailyChallengeHiddenOn = ch.date
            hiddenOn = ch.date
        }
    )

    if (!expanded) return

    // usePlatformDefaultWidth = false es necesario: con el valor por defecto,
    // Dialog impone el ancho estandar de Android y se queda MUY por debajo de
    // los 460.dp que pide el contenido. El sintoma era el boton "RANKING"
    // partido en tres lineas ("RAN/KIN/G").
    Dialog(
        onDismissRequest = { expanded = false },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
    Column(
        modifier = Modifier
            .width(460.dp)
            .verticalScroll(rememberScrollState())
    ) {

        // ── RETO DEL DÍA ──────────────────────────────────────────
        run {
            Box {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                    border = BorderStroke(1.dp, NeonBlue.copy(alpha = 0.7f)),
                    shadowElevation = 12.dp
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.EmojiEvents, null, tint = SpeccyPalette.imperial, modifier = Modifier.size(24.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("RETO DEL DÍA", color = SpeccyPalette.imperial, fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                            Spacer(Modifier.weight(1f))
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = NeonBlue.copy(alpha = 0.2f),
                                modifier = Modifier.padding(end = 32.dp)
                            ) {
                                Text("+${ch.points} pts", color = NeonBlue, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
                            }
                        }

                        Spacer(Modifier.height(12.dp))
                        Text(ch.title, color = MaterialTheme.colorScheme.onSurface, fontSize = 18.sp, fontWeight = FontWeight.Black)
                        Spacer(Modifier.height(4.dp))
                        Text(ch.objective, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, lineHeight = 18.sp)

                        if (ch.tipFromArchitect.isNotEmpty()) {
                            Spacer(Modifier.height(10.dp))
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = NeonBlue.copy(alpha = 0.08f),
                                border = BorderStroke(0.5.dp, NeonBlue.copy(alpha = 0.3f))
                            ) {
                                Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.Top) {
                                    Icon(Icons.Default.AutoAwesome, null, tint = NeonBlue, modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("El Arquitecto: ${ch.tipFromArchitect}", color = NeonBlue.copy(alpha = 0.9f), fontSize = 12.sp, lineHeight = 16.sp)
                                }
                            }
                        }

                        Spacer(Modifier.height(16.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            // Boton jugar. `matchingGame` sale de la biblioteca
                            // real; antes era un `null as Game?` fijo, asi que
                            // pulsarlo no lanzaba nada y aun asi se marcaba
                            // "COMPLETADO". Si el juego no esta, se dice.
                            val juego = matchingGame
                            Button(
                                onClick = {
                                    juego?.let {
                                        hasPlayed = true
                                        onLaunchGame(it)
                                    }
                                },
                                enabled = juego != null && !hasPlayed,
                                colors = ButtonDefaults.buttonColors(containerColor = NeonBlue),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    when {
                                        hasPlayed -> "COMPLETADO ✓"
                                        juego != null -> "JUGAR"
                                        lookupDone -> "NO LO TIENES"
                                        else -> "BUSCANDO…"
                                    },
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            }

                            OutlinedButton(
                                onClick = { showRanking = !showRanking },
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                            ) {
                                Icon(Icons.Default.Leaderboard, null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("RANKING", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1)
                            }
                        }

                        // Ranking global expandible
                        if (showRanking && ranking.isNotEmpty()) {
                            Spacer(Modifier.height(16.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                            Spacer(Modifier.height(12.dp))
                            Text("TOP 10 HOY", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, letterSpacing = 2.sp)
                            Spacer(Modifier.height(8.dp))
                            ranking.forEach { entry ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val rankColor = when (entry.rank) {
                                        1 -> Color(0xFFFFD700)
                                        2 -> Color(0xFFC0C0C0)
                                        3 -> Color(0xFFCD7F32)
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                    Text("#${entry.rank}", color = rankColor, fontSize = 12.sp, fontWeight = FontWeight.Black, modifier = Modifier.width(28.dp))
                                    Text(entry.userName, color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp, modifier = Modifier.weight(1f))
                                    Text("${entry.score} pts", color = NeonBlue, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
                // Aqui dentro el aspa solo CIERRA el detalle. Ocultar el reto
                // del dia es la otra aspa, la de la pastilla: son dos acciones
                // distintas y conviene que no se confundan.
                IconButton(
                    onClick = { expanded = false },
                    modifier = Modifier.align(Alignment.TopEnd).padding(4.dp)
                ) {
                    Icon(Icons.Default.Close, "Cerrar", tint = MaterialTheme.colorScheme.onSurface)
                }
            }
        }

        // ── HOY EN LA HISTORIA ────────────────────────────────────
        if (todayGames.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f),
                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AccessTime, null, tint = SpeccyPalette.warn, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("HOY EN LA HISTORIA", color = SpeccyPalette.warn, fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                    }
                    Spacer(Modifier.height(10.dp))
                    todayGames.take(3).forEach { game ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("⚡", fontSize = 14.sp, modifier = Modifier.width(22.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(game.title, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                if (!game.releaseDate.isNullOrEmpty() && game.releaseDate != "N/A") {
                                    Text(game.releaseDate!!, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                                }
                            }
                            TextButton(
                                onClick = { onLaunchGame(game) },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text("JUGAR", color = NeonBlue, fontSize = 12.sp, fontWeight = FontWeight.Black)
                            }
                        }
                    }
                }
            }
        }
    }
    }
}

/**
 * La pastilla que se ve en el dashboard: una linea de titulo y el nombre del
 * juego. Nada mas.
 *
 * Es lo unico que ocupa sitio de forma permanente, asi que se mantiene por
 * debajo del ancho de una tarjeta del carrusel y sin animaciones continuas: el
 * dashboard ya tiene 20 corriendo y el objetivo del proyecto es la fluidez en
 * Mali-G57.
 */
@Composable
private fun ChipRetoDelDia(
    challenge: DailyChallenge,
    onOpen: () -> Unit,
    onHide: () -> Unit
) {
    Surface(
        modifier = Modifier
            .padding(top = 104.dp, end = 24.dp)
            .widthIn(max = 260.dp)
            .clickable(onClick = onOpen),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
        border = BorderStroke(1.dp, NeonBlue.copy(alpha = 0.35f)),
        shadowElevation = 6.dp
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.EmojiEvents, null,
                tint = SpeccyPalette.imperial,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f, fill = false)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "RETO DEL DÍA",
                        color = SpeccyPalette.imperial,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "+${challenge.points}",
                        color = NeonBlue,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    challenge.title,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = onHide, modifier = Modifier.size(28.dp)) {
                Icon(
                    Icons.Default.Close,
                    "Ocultar el reto de hoy",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}
