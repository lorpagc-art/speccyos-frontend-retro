package com.generacionarcade.speccyos

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScrapingDashboardScreen(viewModel: MainViewModel, onBack: () -> Unit) {
    val scanProgress by viewModel.scanProgress.collectAsStateWithLifecycle()
    val isScanning by viewModel.isScanning.collectAsStateWithLifecycle()
    val scanStatus by viewModel.scanStatusMessage.collectAsStateWithLifecycle()
    val platformCounts by viewModel.platformGameCounts.collectAsStateWithLifecycle()
    val settingsManager = viewModel.settingsManager

    // Estados para persistencia reactiva en UI (Scraper - Descargas)
    var scrapeBoxArt by remember { mutableStateOf(settingsManager.scrapeBoxArt) }
    var scrapeCd by remember { mutableStateOf(settingsManager.scrapeCd) }
    var scrapeScreenshot by remember { mutableStateOf(settingsManager.scrapeScreenshot) }
    var scrapeFanart by remember { mutableStateOf(settingsManager.scrapeFanart) }
    var scrapeWheel by remember { mutableStateOf(settingsManager.scrapeWheel) }
    var scrapeVideos by remember { mutableStateOf(settingsManager.scrapeVideos) }

    // Estados para persistencia reactiva en UI (Biblioteca - Visualización)
    var libShowVideos by remember { mutableStateOf(settingsManager.libShowVideos) }
    var libShowBoxArt by remember { mutableStateOf(settingsManager.libShowBoxArt) }
    var libShowWheel by remember { mutableStateOf(settingsManager.libShowWheel) }
    var libShowFanart by remember { mutableStateOf(settingsManager.libShowFanart) }
    var libShowDescription by remember { mutableStateOf(settingsManager.libShowDescription) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("MANTENIMIENTO DE DATOS E INTERFAZ", color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp, fontWeight = FontWeight.Black) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            item {
                Spacer(Modifier.height(8.dp))
                // --- ESTADO GENERAL ---
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("ESTADO DEL MOTOR DE DATOS", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            if (isScanning) "PROCESANDO INFORMACIÓN..." else "NÚCLEO EN ESPERA",
                            color = if (isScanning) ThemeManager.primaryColor else MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Black,
                            fontSize = 18.sp
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        LinearProgressIndicator(
                            progress = { scanProgress },
                            modifier = Modifier.fillMaxWidth(),
                            color = ThemeManager.primaryColor,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(scanStatus, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    }
                }
            }

            item {
                // --- PANEL DE CONFIGURACIÓN DINÁMICA ---
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    // COLUMNA 1: DESCARGAS
                    Column(modifier = Modifier.weight(1f)) {
                        Text("1. QUÉ DESCARGAR (SCRAPER)", color = ThemeManager.primaryColor, fontSize = 12.sp, fontWeight = FontWeight.Black)
                        Spacer(Modifier.height(8.dp))
                        ScraperToggle("Carátulas", scrapeBoxArt) { scrapeBoxArt = it; settingsManager.scrapeBoxArt = it }
                        ScraperToggle("CD/Cartucho", scrapeCd) { scrapeCd = it; settingsManager.scrapeCd = it }
                        ScraperToggle("Capturas", scrapeScreenshot) { scrapeScreenshot = it; settingsManager.scrapeScreenshot = it }
                        ScraperToggle("Fanart", scrapeFanart) { scrapeFanart = it; settingsManager.scrapeFanart = it }
                        ScraperToggle("Logos (Wheel)", scrapeWheel) { scrapeWheel = it; settingsManager.scrapeWheel = it }
                        ScraperToggle("Vídeos", scrapeVideos) { scrapeVideos = it; settingsManager.scrapeVideos = it }
                    }

                    // COLUMNA 2: VISUALIZACIÓN
                    Column(modifier = Modifier.weight(1f)) {
                        Text("2. QUÉ MOSTRAR (GALERÍA)", color = ThemeManager.primaryColor, fontSize = 12.sp, fontWeight = FontWeight.Black)
                        Spacer(Modifier.height(8.dp))
                        ScraperToggle("Mostrar Vídeos", libShowVideos) { libShowVideos = it; settingsManager.libShowVideos = it }
                        ScraperToggle("Mostrar Cajas", libShowBoxArt) { libShowBoxArt = it; settingsManager.libShowBoxArt = it }
                        ScraperToggle("Mostrar Logos", libShowWheel) { libShowWheel = it; settingsManager.libShowWheel = it }
                        ScraperToggle("Fondos Dinámicos", libShowFanart) { libShowFanart = it; settingsManager.libShowFanart = it }
                        ScraperToggle("Descripción", libShowDescription) { libShowDescription = it; settingsManager.libShowDescription = it }
                    }
                }
            }

            item {
                // --- ACCIONES RÁPIDAS ---
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { viewModel.forceScrapeAll() },
                        modifier = Modifier.weight(1f).height(45.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ThemeManager.primaryColor),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("SCRAPE TOTAL", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Black, fontSize = 12.sp)
                    }
                    Button(
                        onClick = { viewModel.fullRescan() },
                        modifier = Modifier.weight(1f).height(45.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("RE-ESCANEO", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Black, fontSize = 12.sp)
                    }
                }
            }

            item {
                Text("SCRAPER MANUAL POR SISTEMA", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Black, fontSize = 14.sp)
            }

            // --- LISTADO DE SISTEMAS PARA SCRAPE MANUAL ---
            val systems = platformCounts.toList().sortedByDescending { it.second }
            items(systems) { entry ->
                PlatformStatusItem(entry.first, entry.second, viewModel)
            }
        }
    }
}

@Composable
fun ScraperToggle(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().height(40.dp).clickable { onCheckedChange(!checked) },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = CheckboxDefaults.colors(checkedColor = ThemeManager.primaryColor, uncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant)
        )
        Text(label, color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun PlatformStatusItem(platform: String, count: Int, viewModel: MainViewModel) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.05f))
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(
                    model = "file:///android_asset/contentimg/logos/${platform.lowercase()}.webp",
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    contentScale = ContentScale.Fit
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(platform.uppercase(), color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Black, fontSize = 13.sp)
                    Text("$count juegos", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                }
            }

            Button(
                onClick = { viewModel.forceScrapePlatform(platform) },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                modifier = Modifier.height(30.dp),
                shape = RoundedCornerShape(4.dp)
            ) {
                Text("RE-SCRAPE", color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp, fontWeight = FontWeight.Black)
            }
        }
    }
}
