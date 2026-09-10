package com.generacionarcade.speccyos

/**
 * AchievementsSystem.kt
 * ─────────────────────────────────────────────────────────────────
 * Sistema de Colección y Logros para generar retención diaria.
 *
 * LOGROS:
 *   - Se definen en AchievementDefinition (lista local ampliable)
 *   - Se comprueban automáticamente desde AchievementEngine
 *     en los momentos clave: scan de ROMs, lanzamiento de juego,
 *     favorito añadido, etc.
 *   - Se almacenan en SharedPreferences (ligero, sin depender de Firestore)
 *   - Los desbloqueados muestran un Toast animado al instante
 *
 * MARCADORES DE COLECCIÓN (Game.kt ya tiene isFavorite, playCount, lastPlayed)
 *   - "Jugado" / "Completado" / "Deseado" se añaden como extensión de Game
 *     via una nueva tabla GameCollection en Room (ver abajo)
 *
 * INTEGRACIÓN:
 *   1. Añade AchievementEngine.check*() en los eventos de MainViewModel
 *   2. Añade AchievementsScreen a la navegación (ruta "achievements")
 *   3. Añade el botón en SpeccyDashboard → onAchievementsClick
 * ─────────────────────────────────────────────────────────────────
 */

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.generacionarcade.speccyos.theme.NeonBlue
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

// ─── MODELO DE LOGRO ──────────────────────────────────────────────

data class AchievementDefinition(
    val id: String,
    val title: String,
    val description: String,
    val icon: ImageVector,
    val tier: AchievementTier,
    val points: Int
)

enum class AchievementTier(val color: Color, val label: String) {
    BRONZE  (Color(0xFFCD7F32), "Bronce"),
    SILVER  (Color(0xFFC0C0C0), "Plata"),
    GOLD    (Color(0xFFFFD700), "Oro"),
    IMPERIAL(Color(0xFF00CFFF), "Imperial")
}

// ─── DEFINICIONES ─────────────────────────────────────────────────

object AchievementCatalog {
    val all = listOf(
        // Biblioteca
        AchievementDefinition("lib_10",    "Coleccionista",       "10 juegos en biblioteca",          Icons.Default.CollectionsBookmark, AchievementTier.BRONZE,   50),
        AchievementDefinition("lib_100",   "Archivista",          "100 juegos indexados",             Icons.Default.Archive,             AchievementTier.SILVER,  200),
        AchievementDefinition("lib_500",   "Preservacionista",    "500 juegos en tu colección",       Icons.Default.LibraryBooks,        AchievementTier.GOLD,    500),
        AchievementDefinition("lib_1000",  "Museo Digital",       "1.000 juegos — ¡increíble!",       Icons.Default.Museum,              AchievementTier.IMPERIAL,2000),

        // Plataformas
        AchievementDefinition("plat_5",    "Multi-plataforma",    "Juegos de 5 plataformas distintas",Icons.Default.Devices,             AchievementTier.BRONZE,  100),
        AchievementDefinition("plat_15",   "Historiador Retro",   "15 plataformas distintas",         Icons.Default.History,             AchievementTier.SILVER,  300),
        AchievementDefinition("plat_30",   "Enciclopedia Viviente","30 plataformas — ¡leyenda!",      Icons.Default.EmojiEvents,         AchievementTier.IMPERIAL,1000),

        // Juego activo
        AchievementDefinition("play_1",    "Primera partida",     "Lanzaste tu primer juego",         Icons.Default.PlayArrow,           AchievementTier.BRONZE,   25),
        AchievementDefinition("play_50",   "Arcade Habitual",     "50 juegos lanzados",               Icons.Default.SportsEsports,       AchievementTier.SILVER,  150),
        AchievementDefinition("play_500",  "Maratoniano",         "500 sesiones de juego",            Icons.Default.Timer,               AchievementTier.GOLD,    400),

        // Favoritos
        AchievementDefinition("fav_1",     "Amor a primera vista","Tu primer favorito",               Icons.Default.Favorite,            AchievementTier.BRONZE,   30),
        AchievementDefinition("fav_25",    "Lista de clásicos",   "25 favoritos marcados",            Icons.Default.FavoriteBorder,      AchievementTier.SILVER,  100),

        // Hardware
        AchievementDefinition("hw_fast",   "Overclocker",         "Perfil de hardware configurado",   Icons.Default.Memory,              AchievementTier.BRONZE,   75),
        AchievementDefinition("hw_bench",  "Ingeniero de Silicio","Benchmark completado",             Icons.Default.Speed,               AchievementTier.SILVER,  200),

        // Social
        AchievementDefinition("share_boot","Viral Boot",          "Compartiste tu pantalla de boot",  Icons.Default.Share,               AchievementTier.GOLD,    250),
        AchievementDefinition("daily_3",   "Constante",           "3 retos diarios completados",      Icons.Default.CalendarToday,       AchievementTier.SILVER,  150),
        AchievementDefinition("daily_30",  "Dedicado",            "30 retos diarios completados",     Icons.Default.EmojiFlags,          AchievementTier.GOLD,    500),

        // Arcade especiales
        AchievementDefinition("arcade_neo","Neo Geo Forever",     "Lanzaste un juego de NeoGeo",      Icons.Default.Games,               AchievementTier.BRONZE,   60),
        AchievementDefinition("arcade_dc", "Soñador del Siglo XX","Jugaste en Dreamcast",             Icons.Default.Videocam,            AchievementTier.SILVER,  120),
        AchievementDefinition("arcade_zx", "Spectrum Puro",       "Lanzaste un juego de ZX Spectrum", Icons.Default.Computer,            AchievementTier.GOLD,    300)
    )
}

// ─── ENGINE ───────────────────────────────────────────────────────

class AchievementEngine(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("achievements_v1", Context.MODE_PRIVATE)

    private val _newlyUnlocked = MutableSharedFlow<AchievementDefinition>(extraBufferCapacity = 5)
    val newlyUnlocked: SharedFlow<AchievementDefinition> = _newlyUnlocked.asSharedFlow()

    fun isUnlocked(id: String) = prefs.getBoolean(id, false)

    fun getUnlockedCount() = AchievementCatalog.all.count { isUnlocked(it.id) }

    fun getTotalPoints(): Int =
        AchievementCatalog.all.filter { isUnlocked(it.id) }.sumOf { it.points }

    private fun unlock(achievement: AchievementDefinition) {
        if (!isUnlocked(achievement.id)) {
            prefs.edit().putBoolean(achievement.id, true).apply()
            _newlyUnlocked.tryEmit(achievement)
        }
    }

    // ─── TRIGGERS ─────────────────────────────────────────────────

    fun onLibraryUpdated(gameCount: Int, platformCount: Int) {
        when {
            gameCount  >= 1000 -> AchievementCatalog.all.find { it.id == "lib_1000" }?.let { unlock(it) }
            gameCount  >=  500 -> AchievementCatalog.all.find { it.id == "lib_500" }?.let { unlock(it) }
            gameCount  >=  100 -> AchievementCatalog.all.find { it.id == "lib_100" }?.let { unlock(it) }
            gameCount  >=   10 -> AchievementCatalog.all.find { it.id == "lib_10" }?.let { unlock(it) }
        }
        when {
            platformCount >= 30 -> AchievementCatalog.all.find { it.id == "plat_30" }?.let { unlock(it) }
            platformCount >= 15 -> AchievementCatalog.all.find { it.id == "plat_15" }?.let { unlock(it) }
            platformCount >=  5 -> AchievementCatalog.all.find { it.id == "plat_5" }?.let { unlock(it) }
        }
    }

    fun onGameLaunched(game: Game, totalLaunched: Int) {
        if (totalLaunched == 1)   AchievementCatalog.all.find { it.id == "play_1" }?.let { unlock(it) }
        if (totalLaunched >= 50)  AchievementCatalog.all.find { it.id == "play_50" }?.let { unlock(it) }
        if (totalLaunched >= 500) AchievementCatalog.all.find { it.id == "play_500" }?.let { unlock(it) }

        // Logros por plataforma específica
        when (game.platformId.lowercase()) {
            "neogeo", "fbneo", "fba" -> AchievementCatalog.all.find { it.id == "arcade_neo" }?.let { unlock(it) }
            "dreamcast"              -> AchievementCatalog.all.find { it.id == "arcade_dc" }?.let { unlock(it) }
            "zxspectrum"             -> AchievementCatalog.all.find { it.id == "arcade_zx" }?.let { unlock(it) }
        }
    }

    fun onFavoriteAdded(totalFavorites: Int) {
        if (totalFavorites == 1)  AchievementCatalog.all.find { it.id == "fav_1" }?.let { unlock(it) }
        if (totalFavorites >= 25) AchievementCatalog.all.find { it.id == "fav_25" }?.let { unlock(it) }
    }

    fun onHardwareConfigured() = AchievementCatalog.all.find { it.id == "hw_fast" }?.let { unlock(it) }
    fun onBenchmarkCompleted() = AchievementCatalog.all.find { it.id == "hw_bench" }?.let { unlock(it) }
    fun onBootShared()         = AchievementCatalog.all.find { it.id == "share_boot" }?.let { unlock(it) }

    fun onDailyChallengeCompleted(totalCompleted: Int) {
        if (totalCompleted >= 3)  AchievementCatalog.all.find { it.id == "daily_3" }?.let { unlock(it) }
        if (totalCompleted >= 30) AchievementCatalog.all.find { it.id == "daily_30" }?.let { unlock(it) }
    }
}

// ─── TOAST DE DESBLOQUEO (overlay en dashboard) ───────────────────

@Composable
fun AchievementUnlockToast(achievement: AchievementDefinition, onDismiss: () -> Unit) {
    var visible by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        delay(3000)
        visible = false
        delay(500)
        onDismiss()
    }

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
        exit  = slideOutVertically(targetOffsetY = { -it }) + fadeOut()
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.95f),
            border = BorderStroke(1.dp, achievement.tier.color)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(achievement.tier.color.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(achievement.icon, null, tint = achievement.tier.color, modifier = Modifier.size(28.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "¡LOGRO DESBLOQUEADO!",
                        color = achievement.tier.color,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 2.sp
                    )
                    Text(achievement.title, color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text(achievement.description, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                }
                Text(
                    "+${achievement.points}",
                    color = achievement.tier.color,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black
                )
            }
        }
    }
}

// ─── PANTALLA DE LOGROS ───────────────────────────────────────────

@Composable
fun AchievementsScreen(
    engine: AchievementEngine,
    onBack: () -> Unit
) {
    val unlockedCount = remember { engine.getUnlockedCount() }
    val totalPoints   = remember { engine.getTotalPoints() }
    val total         = AchievementCatalog.all.size

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            IconButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart)) {
                Icon(Icons.Default.ArrowBack, null, tint = MaterialTheme.colorScheme.onSurface)
            }
            Column(modifier = Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("LOGROS", color = MaterialTheme.colorScheme.onSurface, fontSize = 20.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                Text("$unlockedCount / $total  ·  $totalPoints pts", color = NeonBlue, fontSize = 12.sp)
            }
        }

        // Barra de progreso global
        LinearProgressIndicator(
            progress = { unlockedCount.toFloat() / total },
            modifier = Modifier.fillMaxWidth().height(3.dp),
            color = NeonBlue,
            trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
        )

        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Agrupa por tier
            AchievementTier.entries.reversed().forEach { tier ->
                val tierAchievements = AchievementCatalog.all.filter { it.tier == tier }
                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(tier.color)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(tier.label.uppercase(), color = tier.color, fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                    }
                }
                items(tierAchievements) { ach ->
                    val unlocked = engine.isUnlocked(ach.id)
                    AchievementRow(ach, unlocked)
                }
            }
        }
    }
}

@Composable
private fun AchievementRow(ach: AchievementDefinition, unlocked: Boolean) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = if (unlocked) ach.tier.color.copy(alpha = 0.1f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.03f),
        border = BorderStroke(0.5.dp, if (unlocked) ach.tier.color.copy(alpha = 0.6f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.08f))
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(if (unlocked) ach.tier.color.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surface.copy(alpha = 0.05f))
                    .alpha(if (unlocked) 1f else 0.35f),
                contentAlignment = Alignment.Center
            ) {
                Icon(ach.icon, null, tint = if (unlocked) ach.tier.color else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    ach.title,
                    color = if (unlocked) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    ach.description,
                    color = if (unlocked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "+${ach.points}",
                    color = if (unlocked) ach.tier.color else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Black
                )
                if (unlocked) {
                    Icon(Icons.Default.CheckCircle, null, tint = ach.tier.color, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}
