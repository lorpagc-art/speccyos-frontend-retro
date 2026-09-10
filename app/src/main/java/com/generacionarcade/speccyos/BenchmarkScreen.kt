@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
package com.generacionarcade.speccyos

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import android.content.res.Configuration
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun BenchmarkScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val benchmarkManager = remember { BenchmarkManager(context) }
    val userStatusManager = remember { UserStatusManager(context) }
    val userName by userStatusManager.userName.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    
    var result by remember { mutableStateOf<BenchmarkManager.BenchmarkResult?>(null) }
    val isRunning by benchmarkManager.isRunning.collectAsStateWithLifecycle()
    val progress by benchmarkManager.progress.collectAsStateWithLifecycle()
    val currentTest by benchmarkManager.currentTest.collectAsStateWithLifecycle()
    
    var isUploading by remember { mutableStateOf(false) }
    var uploadSuccess by remember { mutableStateOf<Boolean?>(null) }
    var rankingList by remember { mutableStateOf<List<BenchmarkManager.RankingEntry>?>(null) }

    val primaryColor = ThemeManager.primaryColor
    val backgroundColor = MaterialTheme.colorScheme.background

    Scaffold(
        containerColor = backgroundColor,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("CORE PERFORMANCE BENCHMARK", color = primaryColor, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black, fontSize = 16.sp) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = primaryColor) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (isRunning) {
                BenchmarkActiveVisualizer(currentTest, progress, primaryColor)
            } else if (result == null) {
                StartBenchmarkView(primaryColor) {
                    scope.launch {
                        result = benchmarkManager.runFullBenchmark()
                    }
                }
            } else if (rankingList == null) {
                // Pantalla de resultados personales ANTES de subir.
                // Se captura a un val local: `result` es un var por delegacion, asi que
                // Kotlin no puede smart-castearlo y el !! vivia ademas dentro de una
                // lambda que corre despues de la comprobacion.
                val resultado = result
                if (resultado != null) BenchmarkResultView(resultado, primaryColor, isUploading, uploadSuccess) {
                    scope.launch {
                        isUploading = true
                        val newRanking = benchmarkManager.uploadAndGetRanking(resultado, userName)
                        if (newRanking != null) {
                            rankingList = newRanking
                            uploadSuccess = true
                        } else {
                            uploadSuccess = false
                        }
                        isUploading = false
                    }
                }
            } else {
                // Pantalla de RANKING MUNDIAL
                val ranking = rankingList
                val resultadoSubido = result
                if (ranking != null && resultadoSubido != null) RankingLeaderboardView(ranking, resultadoSubido, primaryColor) {
                    result = null
                    rankingList = null
                }
            }
        }
    }
}

@Composable
fun BenchmarkActiveVisualizer(test: String, progress: Float, color: Color) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            modifier = Modifier.size(250.dp),
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(2.dp, color.copy(alpha = 0.5f))
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                when {
                    test.contains("CPU") -> CpuMathVisualizer(color)
                    test.contains("GPU") -> GpuGeometryVisualizer(color)
                    test.contains("RAM") -> RamHexVisualizer(color)
                    else -> CircularProgressIndicator(color = color, modifier = Modifier.size(80.dp), strokeWidth = 8.dp)
                }
            }
        }
        
        Spacer(Modifier.height(48.dp))
        
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth(0.7f).height(12.dp),
            color = color,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
        
        Spacer(Modifier.height(24.dp))
        
        Text(test, color = MaterialTheme.colorScheme.onSurface, fontFamily = FontFamily.Monospace, fontSize = 18.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text("${(progress * 100).toInt()}% COMPLETED", color = color.copy(alpha = 0.8f), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun CpuMathVisualizer(color: Color) {
    var calculations by remember { mutableStateOf(listOf<String>()) }
    LaunchedEffect(Unit) {
        while (true) {
            val calcList = mutableListOf<String>()
            repeat(15) {
                val a = (1000..9999).random()
                val b = (1000..9999).random()
                val op = listOf("×", "÷", "+", "-", "MOD").random()
                calcList.add(String.format("0x%04X: %d %s %d = %.2f", (0..0xFFFF).random(), a, op, b, Math.random() * 1000))
            }
            calculations = calcList
            delay(50) // Ultra fast
        }
    }
    Column(modifier = Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        calculations.forEach { calc ->
            Text(text = calc, color = color.copy(alpha = 0.7f), fontFamily = FontFamily.Monospace, fontSize = 12.sp, maxLines = 1)
        }
    }
}

@Composable
fun GpuGeometryVisualizer(color: Color) {
    val infiniteTransition = rememberInfiniteTransition(label = "rotation")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(2000, easing = LinearEasing)), label = "rot"
    )
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.5f, targetValue = 1.5f,
        animationSpec = infiniteRepeatable(tween(1000, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "scale"
    )

    Canvas(modifier = Modifier.fillMaxSize().padding(32.dp)) {
        val cx = size.width / 2
        val cy = size.height / 2
        val radius = size.minDimension / 2f
        
        withTransform({
            rotate(rotation, Offset(cx, cy))
            scale(scale, scale, Offset(cx, cy))
        }) {
            drawRect(
                color = color,
                topLeft = Offset(cx - radius/2, cy - radius/2),
                size = Size(radius, radius),
                style = Stroke(width = 3.dp.toPx())
            )
            drawRect(
                color = color.copy(alpha = 0.5f),
                topLeft = Offset(cx - radius/4, cy - radius/4),
                size = Size(radius/2, radius/2),
                style = Stroke(width = 2.dp.toPx())
            )
            
            drawLine(color, Offset(cx - radius/2, cy - radius/2), Offset(cx - radius/4, cy - radius/4), strokeWidth = 2.dp.toPx())
            drawLine(color, Offset(cx + radius/2, cy - radius/2), Offset(cx + radius/4, cy - radius/4), strokeWidth = 2.dp.toPx())
            drawLine(color, Offset(cx - radius/2, cy + radius/2), Offset(cx - radius/4, cy + radius/4), strokeWidth = 2.dp.toPx())
            drawLine(color, Offset(cx + radius/2, cy + radius/2), Offset(cx + radius/4, cy + radius/4), strokeWidth = 2.dp.toPx())
        }
        
        withTransform({
            rotate(-rotation * 2, Offset(cx, cy))
        }) {
            val path = Path().apply {
                moveTo(cx, cy - radius/2)
                lineTo(cx - radius/2, cy + radius/2)
                lineTo(cx + radius/2, cy + radius/2)
                close()
            }
            drawPath(path, color, style = Stroke(width = 2.dp.toPx()))
        }
    }
}

@Composable
fun RamHexVisualizer(color: Color) {
    var hexBlocks by remember { mutableStateOf(listOf<String>()) }
    LaunchedEffect(Unit) {
        val hexChars = "0123456789ABCDEF".toCharArray()
        while (true) {
            val blocks = mutableListOf<String>()
            repeat(15) {
                val address = String.format("0x%08X", (0..0xFFFFFFFF).random())
                val data = (1..8).joinToString(" ") { 
                    "" + hexChars.random() + hexChars.random() 
                }
                blocks.add("$address  $data")
            }
            hexBlocks = blocks
            delay(30) // Lightning fast
        }
    }
    Column(modifier = Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        hexBlocks.forEach { block ->
            Text(text = block, color = color.copy(alpha = 0.8f), fontFamily = FontFamily.Monospace, fontSize = 12.sp, maxLines = 1)
        }
    }
}

@Composable
fun StartBenchmarkView(color: Color, onStart: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Default.Speed, null, modifier = Modifier.size(120.dp), tint = color)
        Spacer(Modifier.height(24.dp))
        Text("SPECCY PERFORMANCE TEST", color = MaterialTheme.colorScheme.onSurface, fontSize = 24.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        Text(
            "Este test estresará tu CPU, GPU y RAM mediante simulación de decodificación masiva e interpolación gráfica para determinar la capacidad de emulación real de tu dispositivo.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            fontFamily = FontFamily.Monospace
        )
        Spacer(Modifier.height(48.dp))
        Button(
            onClick = onStart,
            modifier = Modifier.height(60.dp).fillMaxWidth(0.8f),
            colors = ButtonDefaults.buttonColors(containerColor = color),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("INICIAR DIAGNÓSTICO", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Black)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BenchmarkResultView(result: BenchmarkManager.BenchmarkResult, color: Color, isUploading: Boolean, uploadSuccess: Boolean?, onUpload: () -> Unit) {
    val isPortrait = LocalConfiguration.current.orientation == Configuration.ORIENTATION_PORTRAIT

    Column(modifier = Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState())) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = color.copy(alpha = 0.1f),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(2.dp, color)
        ) {
            Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("GLOBAL SCORE", color = color, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text("${result.totalScore}", color = MaterialTheme.colorScheme.onSurface, fontSize = 64.sp, fontWeight = FontWeight.Black)
                Text("DISPOSITIVO: ${result.deviceModel}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                
                if (result.isOverclocked) {
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Bolt, contentDescription = "OC", tint = SpeccyPalette.imperial, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("OVERCLOCKED: ${result.overclockProfile}", color = SpeccyPalette.imperial, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        
        Spacer(Modifier.height(24.dp))
        
        if (isPortrait) {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard("CPU SCORE", result.cpuScore, color, Modifier.fillMaxWidth())
                StatCard("GPU RENDER", result.gpuScore, color, Modifier.fillMaxWidth())
                StatCard("RAM I/O", result.ramScore, color, Modifier.fillMaxWidth())
            }
        } else {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard("CPU SCORE", result.cpuScore, color, Modifier.weight(1f))
                StatCard("GPU RENDER", result.gpuScore, color, Modifier.weight(1f))
                StatCard("RAM I/O", result.ramScore, color, Modifier.weight(1f))
            }
        }

        Spacer(Modifier.height(32.dp))
        
        Text("SISTEMAS SOPORTADOS ESTIMADOS", color = color, fontWeight = FontWeight.Black, fontSize = 18.sp)
        Spacer(Modifier.height(16.dp))
        
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            result.supportedSystems.forEach { system ->
                Surface(
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(system, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        
        Spacer(Modifier.height(48.dp))
        
        if (uploadSuccess == null || !uploadSuccess) {
            Button(
                onClick = onUpload,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = color),
                enabled = !isUploading
            ) {
                if (isUploading) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Icon(Icons.Default.CloudUpload, null, tint = MaterialTheme.colorScheme.onPrimary)
                    Spacer(Modifier.width(8.dp))
                    Text("VER RANKING MUNDIAL", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
                }
            }
            if (uploadSuccess == false) {
                Text(
                    text = "ERROR DE CONEXIÓN AL SERVIDOR. INTÉNTALO DE NUEVO.",
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp).fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
fun RankingLeaderboardView(
    rankingList: List<BenchmarkManager.RankingEntry>,
    myResult: BenchmarkManager.BenchmarkResult,
    color: Color,
    onClose: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("RANKING MUNDIAL DE POTENCIA", color = color, fontSize = 18.sp, fontWeight = FontWeight.Black)
                Text("Resultados generados por la comunidad de Speccy OS", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            }
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, null, tint = color)
            }
        }
        
        Spacer(Modifier.height(24.dp))
        
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, color.copy(alpha = 0.5f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("TU PUNTUACIÓN", color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(myResult.deviceModel, color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp, fontWeight = FontWeight.Black)
                    Text("${myResult.totalScore} PTS", color = color, fontSize = 20.sp, fontWeight = FontWeight.Black)
                }
            }
        }
        
        Spacer(Modifier.height(24.dp))
        
        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            rankingList.forEach { entry ->
                val isMe = entry.score == myResult.totalScore && entry.deviceModel == myResult.deviceModel.uppercase()
                
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    color = if (isMe) color.copy(alpha = 0.2f) else MaterialTheme.colorScheme.background,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, if (isMe) color else MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "#${entry.position}",
                            color = if (entry.position <= 3) SpeccyPalette.imperial else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black,
                            modifier = Modifier.width(40.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = entry.deviceModel, color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(text = "Por: ${entry.userName}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                                if (entry.isOverclocked) {
                                    Spacer(Modifier.width(8.dp))
                                    Icon(Icons.Default.Bolt, contentDescription = "OC", tint = SpeccyPalette.imperial, modifier = Modifier.size(10.dp))
                                    Text(text = " ${entry.overclockProfile}", color = SpeccyPalette.imperial, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        Text(
                            text = "${entry.score}",
                            color = if (isMe) color else MaterialTheme.colorScheme.onSurface,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun StatCard(label: String, score: Int, color: Color, modifier: Modifier) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.onPrimary,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, color.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Spacer(Modifier.height(4.dp))
            Text("$score", color = MaterialTheme.colorScheme.onSurface, fontSize = 18.sp, fontWeight = FontWeight.Black)
        }
    }
}
