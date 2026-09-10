package com.generacionarcade.speccyos

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.view.KeyEvent as NativeKeyEvent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.generacionarcade.speccyos.network.OnlineScraperService
import kotlinx.coroutines.launch
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * REDISEÑO ULTRA E5 - CENTRO DE CONTROL OPTIMIZADO
 * Rediseñado sin botones laterales, aprovechando toda la pantalla y con menú superior adaptable (ES-DE style).
 */

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun SettingsScreen(
    hardwareViewModel: HardwareViewModel,
    mainViewModel: MainViewModel,
    settingsManager: SettingsManager,
    soundManager: SoundManager,
    onBack: () -> Unit
) {
    var selectedSector by remember { mutableIntStateOf(0) }
    val primaryColor = ThemeManager.primaryColor
    val accentColor  = ThemeManager.accentColor
    val activePlatforms by mainViewModel.activePlatforms.collectAsStateWithLifecycle()

    val scanOffset by rememberInfiniteTransition(label = "scan")
        .animateFloat(
            initialValue = 0f, targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(3000, easing = LinearEasing)),
            label = "scanY"
        )

    data class SectorDef(val label: String, val icon: ImageVector, val color: Color, val shortCode: String)
    val sectors = listOf(
        // Colores de IDENTIDAD de cada sector: son señalización, no roles del
        // tema. Cada apartado se reconoce por su color, asi que se quedan
        // literales y centralizados aquí a propósito.
        SectorDef("EMULACIÓN",   Icons.Default.SportsEsports, Color(0xFF00F2FF), "EMU"),
        SectorDef("INTERFAZ",    Icons.Default.Palette,       Color(0xFFD500F9), "VIS"),
        SectorDef("FANART",      Icons.Default.Image,         Color(0xFFFF9800), "ART"),
        SectorDef("SCRAPER",     Icons.Default.CloudDownload, Color(0xFF00E676), "NET"),
        SectorDef("CUENTA",      Icons.Default.AccountCircle, Color(0xFF9C27B0), "ACC"),
        SectorDef("LOGROS",      Icons.Default.EmojiEvents,   Color(0xFFFFD600), "ACH"),
        SectorDef("DISPOSITIVO", Icons.Default.Memory,        Color(0xFFFF3D00), "HW"),
        SectorDef("CONTROLES",   Icons.Default.Gamepad,       Color(0xFF2979FF), "CTL"),
        SectorDef("ACERCA DE",   Icons.Default.Info,          Color(0xFFFF0055), "INF")
    )
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val sp = 56.dp.toPx()
            val lc = primaryColor.copy(alpha = 0.06f)
            for (x in 0..(size.width / sp).toInt() + 1) drawLine(lc, Offset(x * sp, 0f), Offset(x * sp, size.height))
            for (y in 0..(size.height / sp).toInt() + 1) drawLine(lc, Offset(0f, y * sp), Offset(size.width, y * sp))
        }

        Column(Modifier.fillMaxSize()) {
            SettingsHeader(primaryColor, accentColor, onBack, soundManager)

            Row(Modifier.weight(1f).fillMaxWidth()) {
                // ══ PANEL IZQUIERDO — Sector Tiles ══
                Box(
                    modifier = Modifier
                        .width(210.dp).fillMaxHeight()
                        .background(MaterialTheme.colorScheme.background)
                        .border(
                            width = 1.dp,
                            brush = Brush.verticalGradient(listOf(primaryColor.copy(.4f), accentColor.copy(.1f))),
                            shape = RoundedCornerShape(0.dp)
                        )
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.6f).height(2.dp)
                            .offset(y = (scanOffset * 600f).dp)
                            .background(Brush.horizontalGradient(listOf(Color.Transparent, primaryColor.copy(.3f), Color.Transparent)))
                    )
                    Column(Modifier.fillMaxSize().padding(vertical = 8.dp).verticalScroll(rememberScrollState())) {
                        Text("// SECTORES", color = primaryColor.copy(.45f), fontSize = 12.sp,
                            fontWeight = FontWeight.Black, letterSpacing = 2.sp,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp))

                        sectors.forEachIndexed { index, sector ->
                            val isSelected = selectedSector == index
                            var isFocused by remember { mutableStateOf(false) }
                            val glowAlpha by rememberInfiniteTransition(label = "g$index").animateFloat(
                                initialValue = if (isSelected || isFocused) 0.3f else 0f,
                                targetValue  = if (isSelected || isFocused) 0.7f else 0f,
                                animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
                                label = "ga"
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth().height(52.dp)
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (isSelected) sector.color.copy(.12f)
                                        else if (isFocused) MaterialTheme.colorScheme.onSurface.copy(.05f)
                                        else Color.Transparent
                                    )
                                    .onFocusChanged { isFocused = it.isFocused }
                                    .focusRequester(if (index == 0) focusRequester else remember { FocusRequester() })
                                    .focusable()
                                    .onKeyEvent {
                                        if (it.type == KeyEventType.KeyDown && (it.nativeKeyEvent.keyCode == NativeKeyEvent.KEYCODE_ENTER || it.nativeKeyEvent.keyCode == NativeKeyEvent.KEYCODE_DPAD_CENTER)) {
                                            if (selectedSector != index) { soundManager.playClick(); selectedSector = index }
                                            true
                                        } else false
                                    }
                                    .border(
                                        width = if (isSelected || isFocused) 1.dp else 0.dp,
                                        color = if (isFocused) sector.color else sector.color.copy(glowAlpha),
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .clickable {
                                        if (selectedSector != index) { soundManager.playClick(); selectedSector = index }
                                    }
                                    .drawBehind {
                                        if (isSelected) drawLine(sector.color.copy(.9f),
                                            Offset(size.width - 3f, 8f), Offset(size.width - 3f, size.height - 8f), 3f)
                                    }
                                    .padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(sector.icon, null,
                                    tint = if (isSelected || isFocused) sector.color else MaterialTheme.colorScheme.onSurfaceVariant.copy(.5f),
                                    modifier = Modifier.size(if (isSelected || isFocused) 22.dp else 18.dp))
                                Text(sector.label,
                                    color = if (isSelected || isFocused) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(.55f),
                                    fontWeight = if (isSelected || isFocused) FontWeight.Black else FontWeight.Medium,
                                    fontSize = if (isSelected || isFocused) 12.sp else 12.sp, maxLines = 1)
                            }
                        }
                        Spacer(Modifier.height(24.dp))
                        Text("SPECCY OS // v1.1.4", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp))
                    }
                }

                // ══ PANEL DERECHO — Contenido ══
                val activeSector = sectors[selectedSector]
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().height(46.dp)
                            .background(Brush.horizontalGradient(listOf(activeSector.color.copy(.12f), Color.Transparent)))
                            .drawBehind {
                                drawLine(activeSector.color.copy(.35f),
                                    Offset(0f, size.height), Offset(size.width, size.height), 1.5f)
                            }
                            .padding(horizontal = 32.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(activeSector.icon, null, tint = activeSector.color, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(activeSector.label, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Black,
                            fontSize = 15.sp, letterSpacing = 2.sp)
                        Spacer(Modifier.weight(1f))
                        Text("[${activeSector.shortCode}]", color = activeSector.color.copy(.5f),
                            fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                    }

                    AnimatedContent(
                        targetState = selectedSector,
                        transitionSpec = {
                            (fadeIn(tween(220)) + slideInVertically { it / 6 })
                                .togetherWith(fadeOut(tween(150)) + slideOutVertically { -it / 6 })
                        },
                        label = "SectorContent",
                        modifier = Modifier.weight(1f).fillMaxWidth()
                    ) { target ->
                        Box(Modifier.fillMaxSize().padding(horizontal = 36.dp, vertical = 20.dp)) {
                            when (target) {
                                0 -> EmulationSettingsScreen(settingsManager, activeSector.color, activePlatforms.toList())
                                1 -> InterfaceSettingsScreen(settingsManager, mainViewModel, soundManager, activeSector.color, accentColor)
                                2 -> FanartSettingsScreen(settingsManager, soundManager, activeSector.color)
                                3 -> ScraperSettingsScreen(settingsManager, mainViewModel, activeSector.color, activePlatforms.toList())
                                4 -> AccountSettingsContent(settingsManager, activeSector.color)
                                5 -> RetroAchievementsSettingsScreen(mainViewModel.raManager, settingsManager, activeSector.color)
                                6 -> DeviceSettingsScreen(settingsManager, hardwareViewModel, mainViewModel, activeSector.color)
                                7 -> AdvancedControlsMapper(settingsManager, KeyMapper(LocalContext.current), activeSector.color)
                                8 -> ProjectSupportScreen(settingsManager, activeSector.color)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsHeader(
    primaryColor: Color,
    accentColor: Color,
    onBack: () -> Unit,
    soundManager: SoundManager
) {
    val dotAlpha by rememberInfiniteTransition(label = "dot").animateFloat(
        0.3f, 1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "d"
    )
    Row(
        modifier = Modifier.fillMaxWidth().height(50.dp)
            .background(Brush.horizontalGradient(listOf(MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.background)))
            .drawBehind {
                drawLine(primaryColor.copy(.18f), Offset(0f, size.height), Offset(size.width, size.height), 1f)
            }
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = { soundManager.playBack(); onBack() }, modifier = Modifier.size(Touch.min)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver", tint = primaryColor, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(8.dp))
        Box(Modifier.width(2.dp).height(18.dp).background(primaryColor.copy(.55f)))
        Spacer(Modifier.width(12.dp))
        Text("CONTROL MATRIX", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Black,
            fontSize = 14.sp, letterSpacing = 2.sp, fontFamily = ThemeManager.titleFont)
        Spacer(Modifier.weight(1f))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Box(Modifier.size(6.dp).clip(CircleShape).background(primaryColor.copy(dotAlpha)))
            Text("SYS_OK", color = primaryColor.copy(.7f), fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
        }
        Spacer(Modifier.width(16.dp))
        Text("v1.1.4", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(.35f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}



@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmulationSettingsScreen(settingsManager: SettingsManager, primaryColor: Color, activePlatforms: List<String>) {
    val context = LocalContext.current

    val allSystems = remember(activePlatforms) {
        val fullList = RetroArchDatabase.systems
        val filtered = fullList.filter { it.id in activePlatforms }
        if (filtered.isNotEmpty()) filtered.sortedBy { it.name } else fullList.sortedBy { it.name }
    }

    var selectedPlatformId by remember { mutableStateOf(allSystems.firstOrNull()?.id ?: "") }

    var currentEmu by remember(selectedPlatformId) { mutableStateOf(settingsManager.getPreferredEmulator(selectedPlatformId)) }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        UltraSectionHeader("SISTEMAS Y NÚCLEOS", primaryColor)
        Text("Configura el emulador y núcleo para los sistemas instalados.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, modifier = Modifier.padding(bottom = 16.dp))

        if (allSystems.isEmpty()) {
            Text("No se detectaron sistemas con ROMs.", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
            return@Column
        }

        Row(modifier = Modifier.fillMaxWidth().height(400.dp)) {
            Surface(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                color = MaterialTheme.colorScheme.background,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, primaryColor.copy(alpha = 0.1f))
            ) {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(allSystems) { sys ->
                        val isSelected = selectedPlatformId == sys.id
                        FocusableSurface(
                            modifier = Modifier.fillMaxWidth().padding(4.dp),
                            isSelected = isSelected,
                            primaryColor = primaryColor,
                            onClick = { selectedPlatformId = sys.id }
                        ) {
                            Text(
                                text = sys.name.uppercase(),
                                fontWeight = if (isSelected) FontWeight.Black else FontWeight.Bold,
                                modifier = Modifier.padding(12.dp),
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.width(16.dp))

            Surface(
                modifier = Modifier.weight(2f).fillMaxHeight(),
                color = MaterialTheme.colorScheme.background,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, primaryColor.copy(alpha = 0.1f))
            ) {
                val selectedSystem = allSystems.find { it.id == selectedPlatformId } ?: return@Surface
                Column(Modifier.padding(24.dp).verticalScroll(rememberScrollState())) {
                    Text("SISTEMA SELECCIONADO: ${selectedSystem.name.uppercase()}", color = primaryColor, fontSize = 14.sp, fontWeight = FontWeight.Black)
                    Spacer(Modifier.height(24.dp))

                    Text("MODO DE EJECUCIÓN", color = primaryColor, fontSize = 12.sp, fontWeight = FontWeight.Black)

                    Row(modifier = Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        listOf(SettingsManager.EMULATOR_RETROARCH to "RetroArch", SettingsManager.EMULATOR_STANDALONE to "Standalone").forEach { (type, name) ->
                            val isSelected = currentEmu == type
                            FocusableSurface(
                                modifier = Modifier.weight(1f),
                                isSelected = isSelected,
                                primaryColor = primaryColor,
                                onClick = {
                                    settingsManager.setPreferredEmulator(selectedPlatformId, type)
                                    currentEmu = type
                                }
                            ) {
                                Text(name, modifier = Modifier.padding(vertical = 12.dp), textAlign = TextAlign.Center, fontWeight = FontWeight.Black, fontSize = 12.sp)
                            }
                        }
                    }

                    if (currentEmu == SettingsManager.EMULATOR_RETROARCH) {
                        Spacer(Modifier.height(32.dp))
                        Text("NÚCLEO (LIBRETRO)", color = primaryColor, fontSize = 12.sp, fontWeight = FontWeight.Black)

                        var currentCore by remember(selectedPlatformId) { mutableStateOf(settingsManager.getPreferredCore(selectedPlatformId) ?: selectedSystem.defaultCore) }
                        // El orden importa: esta lista ponia primero el
                        // `defaultCore` del systeminfo.txt, que para todo el
                        // arcade es `mamearcade` (el MAME actual). Es el core
                        // mas estricto con la version del set de ROMs, asi que
                        // quien elegia el primero de la lista se quedaba con
                        // pantalla negra. Ahora manda el orden de
                        // SpeccyCoreResolver y el primero se marca como
                        // recomendado.
                        val cores = remember(selectedPlatformId) {
                            SpeccyCoreResolver.candidatosPara(context, selectedPlatformId, selectedSystem)
                                .ifEmpty {
                                    (listOf(selectedSystem.defaultCore) + selectedSystem.alternativeCores).distinct()
                                }
                        }

                        Column(modifier = Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            cores.forEach { core ->
                                val isSelected = currentCore == core
                                FocusableSurface(
                                    modifier = Modifier.fillMaxWidth(),
                                    isSelected = isSelected,
                                    primaryColor = primaryColor,
                                    onClick = {
                                        settingsManager.setPreferredCore(selectedPlatformId, core)
                                        currentCore = core
                                    }
                                ) {
                                    Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        Text(core, fontWeight = if (isSelected) FontWeight.Black else FontWeight.Bold, fontSize = 12.sp)
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            if (core == cores.firstOrNull()) {
                                                Text(
                                                    "RECOMENDADO",
                                                    color = primaryColor,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Black,
                                                    modifier = Modifier.padding(end = 8.dp)
                                                )
                                            }
                                            if (isSelected) Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(32.dp))
                        Text("INYECCIÓN EN RETROARCH (SPECCY ENGINE)", color = primaryColor, fontSize = 12.sp, fontWeight = FontWeight.Black)
                        Spacer(Modifier.height(16.dp))
                        
                        var isRAVisualsEnabled by remember { mutableStateOf(settingsManager.isRetroArchVisualsEnabled) }
                        UltraToggleRow(
                            title = "Inyectar Optimizaciones Visuales (Tema XMB y Shaders)",
                            checked = isRAVisualsEnabled,
                            primaryColor = primaryColor
                        ) {
                            isRAVisualsEnabled = it
                            settingsManager.isRetroArchVisualsEnabled = it
                        }
                        
                        var isRAPerfEnabled by remember { mutableStateOf(settingsManager.isRetroArchPerformanceEnabled) }
                        UltraToggleRow(
                            title = "Inyectar Rendimiento (Drivers de Video/Audio y Sincronización)",
                            checked = isRAPerfEnabled,
                            primaryColor = primaryColor
                        ) {
                            isRAPerfEnabled = it
                            settingsManager.isRetroArchPerformanceEnabled = it
                        }
                        
                        var isRAKeysEnabled by remember { mutableStateOf(settingsManager.isCustomControllerMappingEnabled) }
                        UltraToggleRow(
                            title = "Mapeado propio de Speccy OS (NO sobrescribe RetroArch)",
                            checked = isRAKeysEnabled,
                            primaryColor = primaryColor
                        ) {
                            isRAKeysEnabled = it
                            settingsManager.isCustomControllerMappingEnabled = it
                        }
                    } else {
                        Spacer(Modifier.height(32.dp))
                        Text("APP EMULADORA (STANDALONE)", color = primaryColor, fontSize = 12.sp, fontWeight = FontWeight.Black)

                        val launcherManager = remember { LauncherManager(context) }
                        val installedEmulators = remember(selectedPlatformId) {
                            launcherManager.getCompatibleInstalledEmulators(selectedPlatformId)
                        }
                        var currentPkg by remember(selectedPlatformId) { mutableStateOf(settingsManager.getStandalonePackage(selectedPlatformId)) }

                        Column(modifier = Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (installedEmulators.isEmpty()) {
                                Text("No se detectan aplicaciones compatibles instaladas.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                            }
                            installedEmulators.forEach { (pkg, name) ->
                                val isSelected = currentPkg == pkg
                                FocusableSurface(
                                    modifier = Modifier.fillMaxWidth(),
                                    isSelected = isSelected,
                                    primaryColor = primaryColor,
                                    onClick = {
                                        settingsManager.setStandalonePackage(selectedPlatformId, pkg)
                                        currentPkg = pkg
                                    }
                                ) {
                                    Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        Text(name, fontWeight = if (isSelected) FontWeight.Black else FontWeight.Bold, fontSize = 12.sp)
                                        if (isSelected) Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(40.dp))
    }
}

@Composable
fun DeviceSettingsScreen(settingsManager: SettingsManager, hardwareViewModel: HardwareViewModel, mainViewModel: MainViewModel, primaryColor: Color) {
    val state by hardwareViewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let {
            context.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            mainViewModel.addExtraRomsLocation(it.toString())
        }
    }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        UltraSectionHeader("ESTADO DEL HARDWARE", primaryColor)

        Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TelemetryMiniCard("TEMP", "${state.temperature.toInt()}°C", state.temperature > 65, primaryColor)
            TelemetryMiniCard("CPU", if (state.cpuLoad < 0f) "—" else "${(state.cpuLoad * 100).toInt()}%", state.cpuLoad > 0.85f, primaryColor)
            TelemetryMiniCard("BAT", "${(state.batteryLevel * 100).toInt()}%", state.batteryLevel < 0.2f, primaryColor)
        }

        UltraSectionHeader("RUTAS DE ALMACENAMIENTO (MULTI-FOLDER)", primaryColor)
        
        UltraCard(primaryColor) {
            Column(Modifier.padding(16.dp)) {
                Text("CARPETA PRINCIPAL", color = primaryColor, fontSize = 12.sp, fontWeight = FontWeight.Black)
                Text(settingsManager.romsLocation.ifEmpty { "No definida" }, color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp, modifier = Modifier.padding(vertical = 8.dp))

                Spacer(Modifier.height(16.dp))
                Text("CARPETAS ADICIONALES (EXTERNAS/OTRAS)", color = primaryColor, fontSize = 12.sp, fontWeight = FontWeight.Black)

                val extras = settingsManager.extraRomsLocations.toList()
                if (extras.isEmpty()) {
                    Text("No hay rutas adicionales configuradas", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, modifier = Modifier.padding(vertical = 8.dp))
                } else {
                    extras.forEach { path ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Folder, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(12.dp))
                            Text(path.substringAfterLast("%3A"), color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp, modifier = Modifier.weight(1f), maxLines = 1)
                            IconButton(onClick = { mainViewModel.removeExtraRomsLocation(path) }, modifier = Modifier.size(Touch.min)) {
                                Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f), modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }

                FocusableButton(
                    onClick = { folderPicker.launch(null) },
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    primaryColor = primaryColor
                ) {
                    Icon(Icons.Default.Add, null)
                    Spacer(Modifier.width(8.dp))
                    Text("AÑADIR NUEVA RUTA DE ROMS", fontSize = 12.sp, fontWeight = FontWeight.Black)
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        UltraSectionHeader("PERFIL DE POTENCIA", primaryColor)
        UltraCard(primaryColor) {
            Column(Modifier.padding(24.dp)) {
                if (BuildConfig.IS_FULL_VERSION) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("ECO", "BALANCED", "PERFORMANCE", "EXTREME").forEach { profile ->
                            val isSelected = state.currentProfile == profile
                            val isLocked = profile == "EXTREME" && !state.isPro
                            FocusableSurface(
                                modifier = Modifier.weight(1f),
                                isSelected = isSelected,
                                primaryColor = primaryColor,
                                enabled = !isLocked,
                                onClick = { hardwareViewModel.setManualProfile(profile) }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(profile, modifier = Modifier.padding(vertical = 12.dp), fontWeight = FontWeight.Black, fontSize = 12.sp)
                                    if (isLocked) Icon(Icons.Default.Lock, null, modifier = Modifier.size(10.dp).align(Alignment.TopEnd).padding(2.dp))
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(32.dp))
                    Text("VENTILACIÓN", color = primaryColor, fontSize = 12.sp, fontWeight = FontWeight.Black)
                    Slider(
                        value = state.fanSpeedLevel,
                        onValueChange = { hardwareViewModel.setManualFanSpeed((it * 3).toInt().toFloat() / 3f) },
                        valueRange = 0f..1f,
                        steps = 2,
                        colors = SliderDefaults.colors(thumbColor = primaryColor, activeTrackColor = primaryColor, inactiveTrackColor = primaryColor.copy(alpha = 0.1f))
                    )

                    Spacer(Modifier.height(24.dp))
                    var isDynamicPerfEnabled by remember { mutableStateOf(settingsManager.isDynamicTuningEnabled) }
                    FocusableSurface(
                        modifier = Modifier.fillMaxWidth(),
                        isSelected = isDynamicPerfEnabled,
                        primaryColor = primaryColor,
                        onClick = {
                            isDynamicPerfEnabled = !isDynamicPerfEnabled
                            settingsManager.isDynamicTuningEnabled = isDynamicPerfEnabled
                        }
                    ) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(Modifier.weight(1f)) {
                                Text("TUNING DINÁMICO DE RENDIMIENTO", color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp, fontWeight = FontWeight.Black)
                                Text("Ajusta automáticamente frecuencias en caliente según los FPS y estrés térmico in-game.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                            }
                            Switch(
                                checked = isDynamicPerfEnabled,
                                onCheckedChange = {
                                    isDynamicPerfEnabled = it
                                    settingsManager.isDynamicTuningEnabled = it
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                    checkedTrackColor = primaryColor,
                                    uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            )
                        }
                    }
                } else {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.BatteryChargingFull, contentDescription = null, tint = primaryColor, modifier = Modifier.size(32.dp))
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text("GESTIÓN DE ENERGÍA Y RENDIMIENTO", color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Black)
                            Text("Las opciones avanzadas de control térmico y de perfiles de rendimiento están disponibles en la versión completa de Google Play Store.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        var isTempEnabled by remember { mutableStateOf(true) }
        UltraToggleRow("Termostato Automático (Acelerador si T > 85°C)", isTempEnabled, primaryColor) { isTempEnabled = it }

        Spacer(Modifier.height(16.dp))
        FocusableButton(
            onClick = { mainViewModel.fullRescan() },
            modifier = Modifier.fillMaxWidth().height(48.dp),
            primaryColor = MaterialTheme.colorScheme.error
        ) {
            Text("RESTABLECIMIENTO DE FÁBRICA (REESCANEO TOTAL)", fontWeight = FontWeight.Black, fontSize = 12.sp)
        }
        Spacer(Modifier.height(100.dp))
    }
}

@Composable
fun AdvancedControlsMapper(settingsManager: SettingsManager, keyMapper: KeyMapper, primaryColor: Color) {
    var mappingAction by remember { mutableStateOf<String?>(null) }
    var isCustomMappingEnabled by remember { mutableStateOf(settingsManager.isCustomControllerMappingEnabled) }
    val focusRequester = remember { FocusRequester() }
    val controlList: List<Pair<String, String>> = listOf(
        "A" to "actionA",
        "B" to "actionB",
        "X" to "actionX",
        "Y" to "actionY",
        "UP" to "dpadUp",
        "DOWN" to "dpadDown",
        "LEFT" to "dpadLeft",
        "RIGHT" to "dpadRight",
        "L-STICK UP" to "lStickUp",
        "L-STICK DOWN" to "lStickDown",
        "L-STICK LEFT" to "lStickLeft",
        "L-STICK RIGHT" to "lStickRight",
        "R-STICK UP" to "rStickUp",
        "R-STICK DOWN" to "rStickDown",
        "R-STICK LEFT" to "rStickLeft",
        "R-STICK RIGHT" to "rStickRight",
        "L1" to "btnL1",
        "R1" to "btnR1",
        "L2" to "btnL2",
        "R2" to "btnR2",
        "L3" to "btnL3",
        "R3" to "btnR3",
        "START" to "btnStart",
        "SELECT" to "btnSelect",
        "MENU" to "btnMenu",
        "HOME" to "btnHome",
        "RESET/BACK" to "btnBack",
        "HOTKEY: MOSTRAR MENÚ" to "hotkeyMenu",
        "HOTKEY: CERRAR RA" to "hotkeyExit",
        "HABILITAR HOTKEYS (SHIFT)" to "hotkeyEnable"
    )

    Column(modifier = Modifier.fillMaxSize()) {
        UltraSectionHeader("MAPEADO FÍSICO", primaryColor)
        
        Text(
            text = "Este mapeado se aplica dentro de Speccy OS (menús, atajos del launcher). " +
                "NO se inyecta en RetroArch: sus códigos de tecla de Android no equivalen a los " +
                "índices de botón que usa RetroArch, y forzarlos deja sin efecto los atajos del " +
                "emulador. Los mandos dentro del juego los configura RetroArch por su cuenta.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        UltraToggleRow(
            title = "Usar este mapeado en Speccy OS",
            checked = isCustomMappingEnabled,
            primaryColor = primaryColor
        ) {
            isCustomMappingEnabled = it
            settingsManager.isCustomControllerMappingEnabled = it
        }
        
        Spacer(Modifier.height(16.dp))

        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(controlList) { (label, action) ->
                val currentKey = keyMapper.getMapping(action)
                val isMapping = mappingAction == action
                FocusableSurface(
                    modifier = Modifier.fillMaxWidth().alpha(if (isCustomMappingEnabled) 1f else 0.5f),
                    isSelected = isMapping,
                    enabled = isCustomMappingEnabled,
                    primaryColor = primaryColor,
                    onClick = { mappingAction = action }
                ) {
                    Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(label, fontWeight = FontWeight.Black, fontSize = 13.sp)
                        Text(if (isMapping) "ESPERANDO..." else if (currentKey != -1) "CODE: $currentKey" else "PENDIENTE", color = if (!isMapping && currentKey != -1) primaryColor else Color.Unspecified, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            item { Spacer(Modifier.height(100.dp)) }
        }
    }
    if (mappingAction != null) {
        Box(Modifier.fillMaxSize().focusRequester(focusRequester).focusable().onKeyEvent { if (it.type == KeyEventType.KeyDown) { keyMapper.setMapping(mappingAction!!, it.nativeKeyEvent.keyCode); mappingAction = null; true } else false })
        LaunchedEffect(mappingAction) { focusRequester.requestFocus() }
    }
}

@Composable
fun ProjectSupportScreen(settingsManager: SettingsManager, primaryColor: Color) {
    val context = LocalContext.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) { 
        Spacer(Modifier.height(40.dp))
        Icon(Icons.Default.Favorite, null, tint = primaryColor, modifier = Modifier.size(64.dp))
        Text("APOYA A SPECCY OS", color = MaterialTheme.colorScheme.onSurface, fontSize = 24.sp, fontWeight = FontWeight.Black, fontFamily = ThemeManager.titleFont)
        Text("Desarrollado con ❤️ para la comunidad retro", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        
        Spacer(Modifier.height(32.dp))
        Column(modifier = Modifier.width(440.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Speccy OS es un proyecto gratuito y sin publicidad. Si te gusta y quieres apoyar su desarrollo continuo, considera hacer una donación. ¡Cada aporte ayuda enormemente!", 
                 color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(24.dp))
            
            FocusableButton(
                onClick = { 
                    val paypalUrl = "https://www.paypal.com/pools/c/9rrCSRvafl"
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(paypalUrl))
                    context.startActivity(intent)
                },
                primaryColor = Color(0xFF0079C1), // PayPal Blue
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Payment, contentDescription = null)
                    Spacer(Modifier.width(12.dp))
                    Text("DONAR A TRAVÉS DE PAYPAL", fontWeight = FontWeight.Black)
                }
            }
            
            Spacer(Modifier.height(16.dp))
            
            FocusableButton(
                onClick = { 
                    val kofiUrl = "https://ko-fi.com/SpeccyOS" // Placeholder
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(kofiUrl))
                    context.startActivity(intent)
                },
                primaryColor = Color(0xFFFF5E5B), // Ko-fi Red
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.LocalCafe, contentDescription = null)
                    Spacer(Modifier.width(12.dp))
                    Text("INVÍTAME A UN CAFÉ EN KO-FI", fontWeight = FontWeight.Black)
                }
            }
        }
        Spacer(Modifier.height(100.dp))
    }
}

@Composable
fun InterfaceSettingsScreen(settingsManager: SettingsManager, mainViewModel: MainViewModel, soundManager: SoundManager, primaryColor: Color, accentColor: Color) {
    val context = LocalContext.current
    var currentTheme by remember { mutableStateOf(settingsManager.currentTheme) }
    var fontScale by remember { mutableFloatStateOf(settingsManager.uiFontScale) }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        UltraSectionHeader("PERSONALIZACIÓN DE ADN VISUAL", primaryColor)

        val themes = listOf(
            SettingsManager.THEME_SPECCY_DESKTOP to "Speccy OS",
            SettingsManager.THEME_ULTRA to "Ultra HD",
            SettingsManager.THEME_INMERSIVE to "Pure"
        )

        UltraCard(primaryColor) {
            @OptIn(ExperimentalLayoutApi::class)
            FlowRow(
                modifier = Modifier.padding(12.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                maxItemsInEachRow = 3
            ) {
                themes.forEach { (id, name) ->
                    val isSelected = currentTheme == id
                    FocusableSurface(
                        modifier = Modifier.weight(1f, fill = false).height(44.dp),
                        isSelected = isSelected,
                        primaryColor = primaryColor,
                        onClick = {
                            soundManager.playClick()
                            currentTheme = id
                            settingsManager.currentTheme = id
                        }
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 12.dp)) {
                            Text(
                                text = name.uppercase(),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Black,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(32.dp))
        UltraSectionHeader("AJUSTES DE PANTALLA Y ESCALADO", primaryColor)

        val screenScaleX by mainViewModel.screenScaleX.collectAsStateWithLifecycle()
        val screenScaleY by mainViewModel.screenScaleY.collectAsStateWithLifecycle()
        val screenAspectRatio by mainViewModel.screenAspectRatio.collectAsStateWithLifecycle()

        UltraCard(primaryColor) {
            Column(Modifier.padding(24.dp)) {
                Text("RELACIÓN DE ASPECTO", color = primaryColor, fontSize = 12.sp, fontWeight = FontWeight.Black)
                Row(modifier = Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val ratios = listOf(
                        SettingsManager.ASPECT_RATIO_AUTO to "AUTO",
                        SettingsManager.ASPECT_RATIO_16_9 to "16:9",
                        SettingsManager.ASPECT_RATIO_4_3 to "4:3",
                        SettingsManager.ASPECT_RATIO_1_1 to "1:1"
                    )
                    ratios.forEach { (id, name) ->
                        val isSelected = screenAspectRatio == id
                        FocusableSurface(
                            modifier = Modifier.weight(1f).height(40.dp),
                            isSelected = isSelected,
                            primaryColor = primaryColor,
                            onClick = { mainViewModel.updateScreenMetrics(screenScaleX, screenScaleY, id) }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(name, fontSize = 12.sp, fontWeight = FontWeight.Black)
                            }
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("ESCALADO HORIZONTAL (ANCHO)", color = primaryColor, fontSize = 12.sp, fontWeight = FontWeight.Black)
                    Text("${(screenScaleX * 100).toInt()}%", color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp, fontWeight = FontWeight.Black)
                }
                Slider(
                    value = screenScaleX,
                    onValueChange = { mainViewModel.updateScreenMetrics(it, screenScaleY, screenAspectRatio) },
                    valueRange = 0.5f..1.5f,
                    colors = SliderDefaults.colors(thumbColor = primaryColor, activeTrackColor = primaryColor, inactiveTrackColor = primaryColor.copy(alpha = 0.2f))
                )

                Spacer(Modifier.height(16.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("ESCALADO VERTICAL (ALTO)", color = primaryColor, fontSize = 12.sp, fontWeight = FontWeight.Black)
                    Text("${(screenScaleY * 100).toInt()}%", color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp, fontWeight = FontWeight.Black)
                }
                Slider(
                    value = screenScaleY,
                    onValueChange = { mainViewModel.updateScreenMetrics(screenScaleX, it, screenAspectRatio) },
                    valueRange = 0.5f..1.5f,
                    colors = SliderDefaults.colors(thumbColor = primaryColor, activeTrackColor = primaryColor, inactiveTrackColor = primaryColor.copy(alpha = 0.2f))
                )

                Spacer(Modifier.height(16.dp))
                
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("TAMAÑO DE FUENTE GLOBAL", color = primaryColor, fontSize = 12.sp, fontWeight = FontWeight.Black)
                    Text("${(fontScale * 100).toInt()}%", color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp, fontWeight = FontWeight.Black)
                }
                Slider(
                    value = fontScale,
                    onValueChange = { 
                        fontScale = it
                        settingsManager.uiFontScale = it 
                    },
                    valueRange = 0.7f..1.5f,
                    colors = SliderDefaults.colors(thumbColor = primaryColor, activeTrackColor = primaryColor, inactiveTrackColor = primaryColor.copy(alpha = 0.2f))
                )

                Spacer(Modifier.height(16.dp))

                FocusableButton(
                    onClick = { 
                        mainViewModel.updateScreenMetrics(1.0f, 1.0f, SettingsManager.ASPECT_RATIO_AUTO)
                        fontScale = 1.0f
                        settingsManager.uiFontScale = 1.0f
                    },
                    modifier = Modifier.fillMaxWidth(),
                    primaryColor = MaterialTheme.colorScheme.onSurfaceVariant
                ) {
                    Text("RESTABLECER AJUSTES DE PANTALLA", fontWeight = FontWeight.Black, fontSize = 12.sp)
                }
            }
        }

        Spacer(Modifier.height(32.dp))
        UltraSectionHeader("PERFILES DE COLOR NEÓN", primaryColor)
        
        var isDynamicEnabled by remember { mutableStateOf(settingsManager.isDynamicThemeEnabled) }
        UltraToggleRow("Cambio automático según sistema", isDynamicEnabled, primaryColor) {
            isDynamicEnabled = it
            settingsManager.isDynamicThemeEnabled = it
            if (!it) {
                // Si desactivamos el dinámico, forzamos la recarga del color guardado por el usuario
                ThemeManager.initialize(context)
            }
        }

        Spacer(Modifier.height(12.dp))

        UltraCard(primaryColor) {
            @OptIn(ExperimentalLayoutApi::class)
            FlowRow(
                modifier = Modifier.padding(12.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ThemeManager.neonPresets.forEach { profile ->
                    NeonColorButton(
                        profile = profile,
                        isSelected = ThemeManager.primaryColor == profile.main && ThemeManager.accentColor == profile.accent,
                        onClick = {
                            soundManager.playClick()
                            ThemeManager.saveNeonProfile(context, profile)
                            settingsManager.isDynamicThemeEnabled = false
                            isDynamicEnabled = false
                        }
                    )
                }
            }
        }

        Spacer(Modifier.height(32.dp))
        UltraSectionHeader("SISTEMA SONORO Y VISUAL", primaryColor)

        var crtEnabled by remember { mutableStateOf(settingsManager.showCrtEffect) }
        UltraToggleRow("Efecto CRT Retro", crtEnabled, primaryColor) { crtEnabled = it; settingsManager.showCrtEffect = it }
        
        var bgmState by remember { mutableStateOf(settingsManager.isBackgroundMusicEnabled) }
        UltraToggleRow("Música de Fondo", bgmState, primaryColor) {
            bgmState = it
            settingsManager.isBackgroundMusicEnabled = it
            soundManager.toggleBgm(it)
        }
        
        var attractEnabled by remember { mutableStateOf(settingsManager.isAttractModeEnabled) }
        UltraToggleRow("Attract Mode (Salvapantallas Arcade)", attractEnabled, primaryColor) { attractEnabled = it; settingsManager.isAttractModeEnabled = it }

        Spacer(Modifier.height(32.dp))
        UltraSectionHeader("MENÚ DE BIBLIOTECA (GAME LIST)", primaryColor)
        Text("Personaliza qué elementos visuales se muestran en la lista de juegos.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, modifier = Modifier.padding(bottom = 12.dp))
        
        UltraCard(primaryColor) {
            Column(Modifier.padding(16.dp)) {
                var libShowVideos by remember { mutableStateOf(settingsManager.libShowVideos) }
                UltraToggleRow("Mostrar Vídeos", libShowVideos, primaryColor) { libShowVideos = it; settingsManager.libShowVideos = it }

                var libShowBoxArt by remember { mutableStateOf(settingsManager.libShowBoxArt) }
                UltraToggleRow("Mostrar Carátulas", libShowBoxArt, primaryColor) { libShowBoxArt = it; settingsManager.libShowBoxArt = it }

                var libShowWheel by remember { mutableStateOf(settingsManager.libShowWheel) }
                UltraToggleRow("Mostrar Logos (Wheel Art)", libShowWheel, primaryColor) { libShowWheel = it; settingsManager.libShowWheel = it }

                var libShowFanart by remember { mutableStateOf(settingsManager.libShowFanart) }
                UltraToggleRow("Fondos Dinámicos (Fanart)", libShowFanart, primaryColor) { libShowFanart = it; settingsManager.libShowFanart = it }

                var libShowDescription by remember { mutableStateOf(settingsManager.libShowDescription) }
                UltraToggleRow("Mostrar Descripción", libShowDescription, primaryColor) { libShowDescription = it; settingsManager.libShowDescription = it }

                var libShowCdArt by remember { mutableStateOf(settingsManager.libShowCdArt) }
                UltraToggleRow("Mostrar CD/Cartucho", libShowCdArt, primaryColor) { libShowCdArt = it; settingsManager.libShowCdArt = it }

                var libShowScreenshot by remember { mutableStateOf(settingsManager.libShowScreenshot) }
                UltraToggleRow("Mostrar Capturas de Pantalla", libShowScreenshot, primaryColor) { libShowScreenshot = it; settingsManager.libShowScreenshot = it }

                Spacer(Modifier.height(24.dp))
                Text("MEDIO EN PANEL CENTRAL (HÉROE)", color = primaryColor, fontSize = 12.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val mediaOptions = listOf(
                        "VIDEO" to "VÍDEO",
                        "BOXART" to "CARÁTULA",
                        "SCREENSHOT" to "CAPTURA",
                        "CD_ART" to "CD/DISCO",
                        "FANART" to "FONDO"
                    )
                    var selectedMedia by remember { mutableStateOf(settingsManager.libCentralMediaSlot) }
                    mediaOptions.forEach { (value, label) ->
                        val isSelected = selectedMedia == value
                        FocusableSurface(
                            modifier = Modifier.weight(1f),
                            isSelected = isSelected,
                            primaryColor = primaryColor,
                            onClick = {
                                selectedMedia = value
                                settingsManager.libCentralMediaSlot = value
                            }
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 8.dp)) {
                                Text(label, fontWeight = FontWeight.Black, fontSize = 12.sp)
                            }
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))
                Text("DISTRIBUCIÓN DE COLUMNAS (LAYOUT)", color = primaryColor, fontSize = 12.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val layoutOptions = listOf(
                        "LIST_HERO_INFO" to "LISTA | HÉROE | INFO",
                        "INFO_HERO_LIST" to "INFO | HÉROE | LISTA",
                        "LIST_INFO_HERO" to "LISTA | INFO | HÉROE"
                    )
                    var selectedLayout by remember { mutableStateOf(settingsManager.libImmersiveLayoutOrder) }
                    layoutOptions.forEach { (value, label) ->
                        val isSelected = selectedLayout == value
                        FocusableSurface(
                            modifier = Modifier.weight(1f),
                            isSelected = isSelected,
                            primaryColor = primaryColor,
                            onClick = {
                                selectedLayout = value
                                settingsManager.libImmersiveLayoutOrder = value
                            }
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 8.dp)) {
                                Text(label, fontWeight = FontWeight.Black, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(40.dp))
    }
}

@Composable
fun ScraperSettingsScreen(settingsManager: SettingsManager, mainViewModel: MainViewModel, primaryColor: Color, activePlatforms: List<String>) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var source by remember { mutableStateOf(settingsManager.scraperSource) }
    val cacheSize by mainViewModel.mediaCacheSize.collectAsStateWithLifecycle()

    val scrapedCount by mainViewModel.scrapedGamesCount.collectAsState(initial = 0)
    val missingCount by mainViewModel.missingMediaCount.collectAsState(initial = 0)
    val platformStats by mainViewModel.scrapingStatsByPlatform.collectAsState(initial = emptyList())

    var user by remember { mutableStateOf(settingsManager.scraperUsername) }
    var pass by remember { mutableStateOf(settingsManager.scraperPassword) }
    var testResult by remember { mutableStateOf("") }
    var isTesting by remember { mutableStateOf(false) }

    val isScrapingActive by mainViewModel.isScrapingActive.collectAsStateWithLifecycle()
    val scraperService = remember { OnlineScraperService(context, settingsManager) }

    // Sin esto el tamaño se quedaba en "Calculando..." para siempre.
    LaunchedEffect(Unit) { mainViewModel.calculateMediaCacheSize() }

    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let {
            context.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            settingsManager.scraperMediaLocation = it.toString()
            mainViewModel.refreshDashboardPacks()
            mainViewModel.calculateMediaCacheSize()
        }
    }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        // Estos tres contadores y las estadisticas por sistema ya se recogian
        // del ViewModel pero no se pintaban en ninguna parte.
        UltraSectionHeader("ESTADO DE LA BIBLIOTECA", primaryColor)
        Row(Modifier.fillMaxWidth().padding(bottom = 24.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TelemetryMiniCard("CON MEDIA", scrapedCount.toString(), false, primaryColor)
            TelemetryMiniCard("SIN MEDIA", missingCount.toString(), missingCount > 0, primaryColor)
            TelemetryMiniCard("SISTEMAS", platformStats.size.toString(), false, primaryColor)
        }

        if (platformStats.isNotEmpty()) {
            UltraCard(primaryColor) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("COBERTURA POR SISTEMA", color = primaryColor, fontSize = 12.sp, fontWeight = FontWeight.Black)
                    platformStats.sortedBy { it.platformId }.forEach { st ->
                        val ratio = if (st.total > 0) st.scraped.toFloat() / st.total else 0f
                        Column {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(st.platformId.uppercase(), color = MaterialTheme.colorScheme.onSurface,
                                     fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                Text("${st.scraped} / ${st.total}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                            }
                            Spacer(Modifier.height(4.dp))
                            LinearProgressIndicator(
                                progress = { ratio },
                                modifier = Modifier.fillMaxWidth().height(4.dp),
                                color = if (ratio >= 1f) SpeccyPalette.ok else primaryColor,
                                trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(32.dp))
        }

        UltraSectionHeader("MOTOR DE SINCRONIZACIÓN", primaryColor)

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            FocusableButton(onClick = { source = "THEGAMESDB"; settingsManager.scraperSource = "THEGAMESDB" }, primaryColor = primaryColor, isSelected = source == "THEGAMESDB", modifier = Modifier.weight(1f)) { Text("THE GAMES DB (GRATIS)") }
            FocusableButton(onClick = { source = "SCREEN_SCRAPER"; settingsManager.scraperSource = "SCREEN_SCRAPER" }, primaryColor = primaryColor, isSelected = source == "SCREEN_SCRAPER", modifier = Modifier.weight(1f)) { Text("SCREEN SCRAPER (PRO)") }
        }

        AnimatedVisibility(visible = source == "SCREEN_SCRAPER") {
            Column {
                Spacer(Modifier.height(16.dp))
                UltraCard(primaryColor) {
                    Column(Modifier.padding(16.dp)) {
                        Text("CREDENCIALES DE SCREEN SCRAPER", color = primaryColor, fontSize = 12.sp, fontWeight = FontWeight.Black)
                        Spacer(Modifier.height(16.dp))
                        OutlinedTextField(
                            value = user,
                            onValueChange = { user = it; settingsManager.scraperUsername = it },
                            label = { Text("Usuario (Opcional pero recomendado)") },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = MaterialTheme.colorScheme.onSurface, unfocusedTextColor = MaterialTheme.colorScheme.onSurface, focusedBorderColor = primaryColor)
                        )
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = pass,
                            onValueChange = { pass = it; settingsManager.scraperPassword = it },
                            label = { Text("Contraseña") },
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = MaterialTheme.colorScheme.onSurface, unfocusedTextColor = MaterialTheme.colorScheme.onSurface, focusedBorderColor = primaryColor)
                        )
                        Spacer(Modifier.height(16.dp))
                        
                        FocusableButton(
                            onClick = {
                                isTesting = true
                                scope.launch {
                                    val result = scraperService.testScreenScraperConnection(user, pass)
                                    isTesting = false
                                    testResult = result
                                }
                            },
                            primaryColor = primaryColor,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (isTesting) CircularProgressIndicator(modifier = Modifier.size(18.dp))
                            else Text("PROBAR CONEXIÓN", fontWeight = FontWeight.Bold)
                        }
                        if (testResult.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            Text(testResult, color = if (testResult.contains("ACTIVA") || testResult.contains("EXITOSA")) SpeccyPalette.ok else MaterialTheme.colorScheme.error, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(32.dp))
        UltraSectionHeader("REGLAS DE DESCARGA", primaryColor)
        
        var scrapeBoxArt by remember { mutableStateOf(settingsManager.scrapeBoxArt) }
        UltraToggleRow("Descargar Carátulas 2D", scrapeBoxArt, primaryColor) { scrapeBoxArt = it; settingsManager.scrapeBoxArt = it }
        var scrapeVideos by remember { mutableStateOf(settingsManager.scrapeVideos) }
        UltraToggleRow("Descargar Vídeos (Consume mucho espacio)", scrapeVideos, primaryColor) { scrapeVideos = it; settingsManager.scrapeVideos = it }

        Spacer(Modifier.height(32.dp))
        UltraSectionHeader("CONTROL DEL SCRAPER", primaryColor)
        
        if (isScrapingActive) {
            FocusableButton(
                onClick = { mainViewModel.stopScraping() },
                primaryColor = MaterialTheme.colorScheme.error,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Stop, null)
                Spacer(Modifier.width(8.dp))
                Text("DETENER PROCESO ACTIVO", fontWeight = FontWeight.Black)
            }
        } else {
            FocusableButton(
                onClick = { mainViewModel.forceScrapeAll() },
                primaryColor = primaryColor,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.PlayArrow, null)
                Spacer(Modifier.width(8.dp))
                Text("INICIAR ESCANEO TOTAL (CON AUTO-SYNC LOCAL)", fontWeight = FontWeight.Black)
            }
        }

        Spacer(Modifier.height(32.dp))
        UltraSectionHeader("SCRAPEO POR SISTEMA", primaryColor)
        Text("Escanea carpetas y descarga media solo para sistemas específicos.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, modifier = Modifier.padding(bottom = 8.dp))
        Text("Nota: Speccy OS sincroniza automáticamente tus carpetas locales antes de usar internet (Zero-Scraping).", color = primaryColor.copy(0.7f), fontSize = 12.sp, modifier = Modifier.padding(bottom = 12.dp))

        val allSystems = RetroArchDatabase.systems.filter { it.id in activePlatforms }.sortedBy { it.name }
        
        Surface(
            color = MaterialTheme.colorScheme.background,
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, primaryColor.copy(alpha = 0.1f)),
            modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp)
        ) {
            LazyColumn(Modifier.padding(8.dp)) {
                items(allSystems) { sys ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp, horizontal = 8.dp)
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.03f), RoundedCornerShape(8.dp))
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(sys.name.uppercase(), color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        FocusableButton(
                            onClick = { mainViewModel.forceScrapePlatform(sys.id) },
                            primaryColor = primaryColor,
                            enabled = !isScrapingActive,
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text("SCRAPE", fontSize = 12.sp, fontWeight = FontWeight.Black)
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(32.dp))
        UltraSectionHeader("ALMACENAMIENTO DE MEDIOS", primaryColor)
        UltraCard(primaryColor) {
            Column(Modifier.padding(16.dp)) {
                Text("CARPETA DE CARÁTULAS Y VÍDEOS", color = primaryColor, fontSize = 12.sp, fontWeight = FontWeight.Black)
                Text(
                    settingsManager.scraperMediaLocation.ifEmpty { "Carpeta interna de la app (por defecto)" },
                    color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp,
                    modifier = Modifier.padding(vertical = 8.dp), maxLines = 2
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Storage, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Ocupado: $cacheSize", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                }
                FocusableButton(
                    onClick = { folderPicker.launch(null) },
                    primaryColor = primaryColor,
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
                ) {
                    Icon(Icons.Default.DriveFolderUpload, null)
                    Spacer(Modifier.width(8.dp))
                    Text("ELEGIR CARPETA DE MEDIOS", fontSize = 12.sp, fontWeight = FontWeight.Black)
                }
            }
        }

        Spacer(Modifier.height(32.dp))
        UltraSectionHeader("MANTENIMIENTO", primaryColor)
        FocusableButton(
            onClick = { mainViewModel.purgeVideos(); Toast.makeText(context, "Vídeos eliminados", Toast.LENGTH_SHORT).show() }, 
            primaryColor = MaterialTheme.colorScheme.error.copy(alpha = 0.6f), 
            modifier = Modifier.fillMaxWidth()
        ) { 
            Icon(Icons.Default.DeleteSweep, null)
            Spacer(Modifier.width(8.dp))
            Text("PURGAR TODOS LOS VÍDEOS DEL SISTEMA", fontSize = 12.sp) 
        }

        Spacer(Modifier.height(40.dp))
    }
}

@Composable
fun AccountSettingsContent(settingsManager: SettingsManager, primaryColor: Color) {
    var isCloudEnabled by remember { mutableStateOf(settingsManager.isCloudSyncEnabled) }
    val context = LocalContext.current

    // Carpeta de RetroArch por SAF. Sin esto, Nebula Sync y la Maquina del
    // Tiempo no ven NADA en Android 11+: /sdcard/RetroArch es ilegible con
    // java.io.File sin "Acceso a todos los archivos", que es un permiso
    // sensible en Play y no se declara a proposito.
    var carpetaRa by remember { mutableStateOf(settingsManager.retroarchFolderUri) }
    val selectorRetroArch = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let {
            context.contentResolver.takePersistableUriPermission(
                it,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            settingsManager.retroarchFolderUri = it.toString()
            carpetaRa = it.toString()
        }
    }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        UltraSectionHeader("NÚCLEO DE USUARIO", primaryColor)
        
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.AccountCircle, null, tint = primaryColor, modifier = Modifier.size(48.dp))
            Spacer(Modifier.width(16.dp))
            Column {
                Text(settingsManager.userEmail ?: "No identificado", color = MaterialTheme.colorScheme.onSurface, fontSize = 18.sp, fontWeight = FontWeight.Black)
                Text("Versión completa · gratuita", color = SpeccyPalette.imperial, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(Modifier.height(32.dp))
        UltraSectionHeader("NEBULA SYNC (CLOUD SAVES)", primaryColor)
        
        UltraToggleRow("Sincronización en Google Drive", isCloudEnabled, primaryColor) { isCloudEnabled = it; settingsManager.isCloudSyncEnabled = it }

        Spacer(Modifier.height(16.dp))
        UltraCard(primaryColor) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "CARPETA DE RETROARCH",
                    color = primaryColor, fontSize = 12.sp,
                    fontWeight = FontWeight.Black, letterSpacing = 2.sp
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    if (carpetaRa.isEmpty())
                        "Sin conceder. Speccy OS no puede ver tus partidas guardadas hasta que elijas la carpeta RetroArch."
                    else "Concedida ✓",
                    color = if (carpetaRa.isEmpty()) SpeccyPalette.warn
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { selectorRetroArch.launch(null) },
                    colors = ButtonDefaults.buttonColors(containerColor = primaryColor)
                ) {
                    Icon(Icons.Default.FolderOpen, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (carpetaRa.isEmpty()) "ELEGIR CARPETA" else "CAMBIAR CARPETA",
                        fontWeight = FontWeight.Black
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Elige la carpeta llamada RetroArch del almacenamiento interno: ahí es donde el emulador guarda saves y states.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        Text("Tus partidas guardadas (.state) se subirán automáticamente a tu Google Drive para que no pierdas el progreso.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)

        Spacer(Modifier.height(40.dp))
        UltraSectionHeader("ZONA PELIGROSA", MaterialTheme.colorScheme.error)
        // Borrado de cuenta dentro de la app: requisito de Google Play para toda
        // app con cuentas. Antes sólo existía logout(), que dejaba los datos del
        // usuario en Firestore y Storage indefinidamente.
        DeleteAccountSection(settingsManager)

        Spacer(Modifier.height(40.dp))
    }
}

@Composable
fun RetroAchievementsSettingsScreen(raManager: RetroAchievementsManager, settingsManager: SettingsManager, primaryColor: Color) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var user by remember { mutableStateOf(settingsManager.raUsername) }
    var token by remember { mutableStateOf(settingsManager.raToken) }
    var statusText by remember { mutableStateOf("Desconectado") }
    var isChecking by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { 
        if (settingsManager.raToken.isNotEmpty()) statusText = raManager.getUserProfile() 
    }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        UltraSectionHeader("RETROACHIEVEMENTS", primaryColor)
        
        OutlinedTextField(
            value = user,
            onValueChange = { user = it },
            label = { Text("Usuario") },
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = MaterialTheme.colorScheme.onSurface, unfocusedTextColor = MaterialTheme.colorScheme.onSurface, focusedBorderColor = primaryColor)
        )
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = token,
            onValueChange = { token = it },
            label = { Text("Web API Key") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = MaterialTheme.colorScheme.onSurface, unfocusedTextColor = MaterialTheme.colorScheme.onSurface, focusedBorderColor = primaryColor)
        )
        
        Spacer(Modifier.height(24.dp))
        
        FocusableButton(
            onClick = {
                isChecking = true
                scope.launch {
                    val success = if (token.length > 20) {
                        raManager.verifyAndSaveApiKey(user, token)
                    } else {
                        raManager.loginSilent(user, token)
                    }
                    isChecking = false
                    if (success) {
                        statusText = raManager.getUserProfile()
                        Toast.makeText(context, "Credenciales Guardadas", Toast.LENGTH_SHORT).show()
                    } else {
                        statusText = "Login Fallido"
                        Toast.makeText(context, "Error en credenciales", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            primaryColor = primaryColor,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (isChecking) CircularProgressIndicator(modifier = Modifier.size(18.dp))
            else Text("VINCULAR CUENTA", fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.height(16.dp))
        Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, primaryColor.copy(alpha = 0.2f))) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Stars, null, tint = primaryColor)
                Spacer(Modifier.width(16.dp))
                Text(statusText, color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Black)
            }
        }
        
        Spacer(Modifier.height(12.dp))
        
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clipData = clipboard.primaryClip
                    if (clipData != null && clipData.itemCount > 0) {
                        val text = clipData.getItemAt(0).text.toString().trim()
                        if (text.length >= 20) {
                            token = text
                            Toast.makeText(context, "Token pegado correctamente", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "El texto copiado no parece un Token válido", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                .border(1.dp, primaryColor.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                .padding(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.ContentPaste, null, tint = primaryColor, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text("PEGAR API KEY DESDE CHROME", color = primaryColor, fontSize = 12.sp, fontWeight = FontWeight.Black)
            }
        }
    }
}


// --- COMPONENTES UI REUTILIZABLES ---

@Composable
fun UltraSectionHeader(title: String, color: Color) {
    Column(modifier = Modifier.padding(bottom = 16.dp)) {
        Text(title, color = color, fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Brush.horizontalGradient(listOf(color, Color.Transparent))))
    }
}

@Composable
fun FocusableButton(
    onClick: () -> Unit,
    primaryColor: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isSelected: Boolean = false,
    content: @Composable RowScope.() -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    // El relleno se calcula UNA vez y el color del contenido sale de el.
    // Antes el relleno por defecto era casi transparente sobre fondo oscuro y
    // muchas llamadas ponian el texto en negro: texto invisible.
    val fondo = MaterialTheme.colorScheme.background
    val relleno = if (isSelected) primaryColor
                  else if (isFocused) primaryColor.copy(alpha = 0.3f)
                  else MaterialTheme.colorScheme.surface.copy(alpha = 0.05f)
    val contenido = speccyContentColorOn(relleno.compositeOver(fondo))

    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .onKeyEvent {
                if (it.type == KeyEventType.KeyDown && (it.nativeKeyEvent.keyCode == NativeKeyEvent.KEYCODE_ENTER || it.nativeKeyEvent.keyCode == NativeKeyEvent.KEYCODE_BUTTON_A)) {
                    onClick()
                    true
                } else false
            },
        colors = ButtonDefaults.buttonColors(
            containerColor = relleno,
            contentColor = contenido
        ),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, if (isSelected || isFocused) primaryColor else Color.Transparent)
    ) {
        content()
    }
}

@Composable
fun FocusableSurface(
    modifier: Modifier = Modifier,
    isSelected: Boolean = false,
    enabled: Boolean = true,
    primaryColor: Color,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    val fondo = MaterialTheme.colorScheme.background
    val relleno = if (isSelected) primaryColor
                  else if (isFocused) primaryColor.copy(alpha = 0.1f)
                  else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.03f)
    // Seleccionado = relleno SOLIDO. Antes era un tinte al 20% sobre el fondo
    // oscuro mientras las etiquetas iban en negro: negro sobre casi negro.
    val contenido = speccyContentColorOn(relleno.compositeOver(fondo))

    Surface(
        modifier = modifier
            .onFocusChanged { isFocused = it.isFocused }
            .focusable(enabled)
            .onKeyEvent {
                if (enabled && it.type == KeyEventType.KeyDown && (it.nativeKeyEvent.keyCode == NativeKeyEvent.KEYCODE_ENTER || it.nativeKeyEvent.keyCode == NativeKeyEvent.KEYCODE_DPAD_CENTER)) {
                    onClick()
                    true
                } else false
            }
            .clickable(enabled = enabled) { onClick() }
            .scale(scaleX = 1f, scaleY = 1f),
        color = relleno,
        contentColor = contenido,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, if (isSelected || isFocused) primaryColor else Color.Transparent)
    ) {
        content()
    }
}

@Composable
fun TelemetryMiniCard(title: String, value: String, isWarning: Boolean, primaryColor: Color) {
    Surface(modifier = Modifier.width(120.dp), color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, if (isWarning) MaterialTheme.colorScheme.error else primaryColor.copy(alpha = 0.2f))) {
        Column(Modifier.padding(12.dp)) {
            Text(title, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text(value, color = if (isWarning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface, fontSize = 18.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
fun UltraToggleRow(title: String, checked: Boolean, primaryColor: Color, onCheckedChange: (Boolean) -> Unit) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (isFocused) 1.02f else 1f, label = "scale")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .onKeyEvent {
                if (it.type == KeyEventType.KeyDown && (it.nativeKeyEvent.keyCode == NativeKeyEvent.KEYCODE_ENTER || it.nativeKeyEvent.keyCode == NativeKeyEvent.KEYCODE_DPAD_CENTER)) {
                    onCheckedChange(!checked)
                    true
                } else false
            }
            .clickable { onCheckedChange(!checked) }
            .background(if (isFocused) MaterialTheme.colorScheme.surface.copy(alpha = 0.05f) else Color.Transparent, RoundedCornerShape(8.dp))
            .border(if (isFocused) BorderStroke(1.dp, primaryColor.copy(alpha = 0.5f)) else BorderStroke(0.dp, Color.Transparent), RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 12.dp)
            .scale(scale),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(title, color = if (isFocused) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, fontWeight = if (isFocused) FontWeight.Black else FontWeight.Medium)
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = primaryColor,
                checkedTrackColor = primaryColor.copy(alpha = 0.3f),
                uncheckedBorderColor = if (isFocused) primaryColor.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurfaceVariant
            )
        )
    }
}

@Composable
fun UltraCard(primaryColor: Color, content: @Composable () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.background, shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, primaryColor.copy(alpha = 0.1f)), modifier = Modifier.fillMaxWidth()) { content() }
}

@Composable
fun NeonColorButton(profile: ThemeManager.NeonProfile, isSelected: Boolean, onClick: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (isFocused) 1.1f else 1f)

    val infiniteTransition = rememberInfiniteTransition(label = "neon_glow")
    val glowSize by infiniteTransition.animateFloat(
        initialValue = 2f, targetValue = 10f,
        animationSpec = infiniteRepeatable(tween(1500), RepeatMode.Reverse)
    )

    Box(
        modifier = Modifier
            .size(80.dp, 40.dp)
            .scale(scale)
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .clickable { onClick() }
            .background(
                brush = Brush.horizontalGradient(listOf(profile.main, profile.accent)),
                shape = RoundedCornerShape(8.dp)
            )
            .border(
                width = if (isSelected || isFocused) 2.dp else 0.dp,
                color = if (isSelected || isFocused) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                shape = RoundedCornerShape(8.dp)
            )
            .shadow(
                elevation = if (isSelected || isFocused) glowSize.dp else 0.dp,
                spotColor = profile.main,
                shape = RoundedCornerShape(8.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = profile.name,
            color = speccyContentColorOn(profile.main),
            fontSize = 12.sp,
            fontWeight = FontWeight.ExtraBold,
            style = TextStyle(shadow = Shadow(speccyContentColorOn(profile.main).copy(alpha = 0.35f), blurRadius = 2f))
        )
    }
}

@Composable
fun FanartSettingsScreen(settingsManager: SettingsManager, soundManager: SoundManager, primaryColor: Color) {
    var artworkStyle by remember { mutableStateOf(settingsManager.systemArtworkStyle) }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        UltraSectionHeader("ESTILO DE FANART DE SISTEMAS", primaryColor)
        Text("Selecciona el tipo de arte visual que se mostrará en las tarjetas de las consolas en el menú principal.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, modifier = Modifier.padding(bottom = 16.dp))

        Surface(
            color = MaterialTheme.colorScheme.background,
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, primaryColor.copy(alpha = 0.1f)),
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
        ) {
            Column(Modifier.padding(24.dp)) {
                // He eliminado la restricción de IS_FULL_VERSION para que todos tengan acceso al fanart
                val styles = listOf(
                    SettingsManager.ARTWORK_STYLE_CYBERPUNK to "CYBERPUNK",
                    SettingsManager.ARTWORK_STYLE_ICONIC to "PERSONAJE",
                    SettingsManager.ARTWORK_STYLE_STUDIO to "ESTUDIO (NUEVO)"
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    styles.forEach { (id, name) ->
                        val isSelected = artworkStyle == id
                        FocusableSurface(
                            modifier = Modifier.weight(1f).height(80.dp),
                            isSelected = isSelected,
                            primaryColor = primaryColor,
                            onClick = {
                                soundManager.playClick()
                                artworkStyle = id
                                settingsManager.systemArtworkStyle = id
                            }
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 12.dp)) {
                                Text(
                                    text = name.uppercase(),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Black,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(100.dp))
    }
}
