/*
 * Speccy OS — frontend retro para Android
 * Copyright (c) 2026 LV-Webstudio · lv-webstudio.com
 * Desarrollado por Speccy81 (LORPAGC) · administracion@lv-webstudio.com
 * Todos los derechos reservados.
 */
package com.generacionarcade.speccyos

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.pm.ServiceInfo
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.AudioManager
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent as NativeKeyEvent
import android.view.WindowManager
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.*
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import androidx.core.app.NotificationCompat
import com.generacionarcade.speccyos.theme.NeonBlue
import com.topjohnwu.superuser.Shell
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class OverlayService : Service(), LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

    private lateinit var windowManager: WindowManager
    private var edgeView: ComposeView? = null
    private var menuView: ComposeView? = null
    private var toastView: ComposeView? = null
    private var translationView: ComposeView? = null
    
    private val lifecycleRegistry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry
    
    private val store = ViewModelStore()
    override val viewModelStore: ViewModelStore get() = store
    
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var mediaProjection: MediaProjection? = null
    private lateinit var mediaProjectionManager: MediaProjectionManager

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startForegroundService()
        
        if (Settings.canDrawOverlays(this)) {
            showEdgeTrigger() 
        } else {
            Log.e("OverlayService", "Permiso SYSTEM_ALERT_WINDOW denegado. Deteniendo servicio.")
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!Settings.canDrawOverlays(this)) {
            Log.e("OverlayService", "Intento de interacción sin permiso de Overlay. Abortando.")
            return super.onStartCommand(intent, flags, startId)
        }

        when(intent?.action) {
            "TOGGLE_OVERLAY" -> {
                val type = intent.getStringExtra("OVERLAY_TYPE") ?: "QUICK_ACCESS"
                if (menuView == null) showFullMenu(type) else hideFullMenu()
            }
            "SHOW_ACHIEVEMENT" -> {
                val title = intent.getStringExtra("TITLE") ?: "Logro Desbloqueado"
                val desc = intent.getStringExtra("DESC") ?: ""
                showAchievementPopUp(title, desc)
            }
            "START_PROJECTION" -> {
                val resultCode = intent.getIntExtra("RESULT_CODE", 0)
                val data = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra("DATA", Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra("DATA")
                }
                if (data != null) {
                    mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, data)
                    performTranslation()
                }
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    private fun performTranslation() {
        val projection = mediaProjection ?: return
        val metrics = windowManager.defaultDisplay.let { d ->
            val m = android.util.DisplayMetrics()
            d.getMetrics(m)
            m
        }
        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val density = metrics.densityDpi

        val imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        val virtualDisplay = projection.createVirtualDisplay(
            "SpeccyCapture", width, height, density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader.surface, null, null
        )

        promoteToMediaProjectionForeground()

        imageReader.setOnImageAvailableListener({ reader ->
            val image = reader.acquireLatestImage()
            if (image != null) {
                val planes = image.planes
                val buffer = planes[0].buffer
                val pixelStride = planes[0].pixelStride
                val rowStride = planes[0].rowStride
                val rowPadding = rowStride - pixelStride * width
                val bitmap = Bitmap.createBitmap(width + rowPadding / pixelStride, height, Bitmap.Config.ARGB_8888)
                bitmap.copyPixelsFromBuffer(buffer)
                image.close()
                reader.setOnImageAvailableListener(null, null)
                // createVirtualDisplay() esta anotado como @Nullable a partir de
                // API 36: con compileSdk 35 el compilador no lo veia.
                virtualDisplay?.release()

                // El ImageReader mantiene DOS buffers a pantalla completa en memoria
                // nativa: sin close() se filtraban en cada traducción.
                runCatching { reader.close() }
                // La proyección se para en cuanto tenemos la captura: no seguimos
                // grabando la pantalla del usuario mientras traducimos.
                runCatching { projection.stop() }
                mediaProjection = null

                processImageForTranslation(bitmap)
            }
        }, Handler(Looper.getMainLooper()))
    }

    private fun processImageForTranslation(bitmap: Bitmap) {
        val image = InputImage.fromBitmap(bitmap, 0)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

        // Un bitmap ARGB_8888 a 1080p son ~8 MB: hay que soltarlo, y cerrar el
        // recognizer, pase lo que pase con el reconocimiento.
        recognizer.process(image)
            .addOnCompleteListener {
                runCatching { recognizer.close() }
                if (!bitmap.isRecycled) bitmap.recycle()
            }
            .addOnSuccessListener { visionText ->
                val detectedText = visionText.text
                if (detectedText.isNotBlank()) {
                    translateText(detectedText)
                } else {
                    showTranslationPopUp("No se detectó texto en pantalla.")
                }
            }
            .addOnFailureListener {
                showTranslationPopUp("Error al procesar la imagen.")
            }
    }

    private fun translateText(text: String) {
        val aiManager = AiManager(this)
        val settings = SettingsManager(this)
        serviceScope.launch {
            val translation = aiManager.translateOcrText(
                ocrText = text,
                platformId = "emulador",
                gameTitle = "Juego en ejecución",
                targetLang = settings.appLanguage
            )
            showTranslationPopUp(translation)
        }
    }

    private fun showTranslationPopUp(text: String) {
        if (translationView != null) {
            windowManager.removeView(translationView)
            translationView = null
        }

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
        }

        translationView = ComposeView(this).apply {
            setContent {
                TranslationDialogUI(text) {
                    windowManager.removeView(translationView)
                    translationView = null
                }
            }
        }
        setupView(translationView!!)
        windowManager.addView(translationView, params)
    }

    private fun showAchievementPopUp(title: String, desc: String) {
        if (toastView != null) {
            windowManager.removeView(toastView)
            toastView = null
        }

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = 50 
        }

        toastView = ComposeView(this).apply {
            setContent {
                AchievementToastUI(title, desc)
            }
        }

        setupView(toastView!!)
        windowManager.addView(toastView, params)

        Handler(Looper.getMainLooper()).postDelayed({
            toastView?.let { 
                try { windowManager.removeView(it) } catch(e: Exception) {}
                toastView = null
            }
        }, 5000)
    }

    private fun startForegroundService() {
        val channelId = "overlay_service_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Speccy OS Imperial", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Speccy OS Imperial").setContentText("Panel Activo").setSmallIcon(R.drawable.ic_generic_hardware).build()

        // En Android 14+ un startForeground() SIN tipo aplica TODOS los tipos
        // declarados en el manifiesto, incluido mediaProjection — y como todavía
        // no existe token de proyección, el sistema lanza SecurityException y mata
        // el servicio. Hay que declarar sólo specialUse aquí, y volver a llamar con
        // mediaProjection únicamente cuando la proyección ya está concedida.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, notification)
        }
    }

    /** Se llama justo después de obtener el MediaProjection, nunca antes. */
    private fun promoteToMediaProjectionForeground() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        val channelId = "overlay_service_channel"
        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Speccy OS Imperial")
            .setContentText("Traduciendo pantalla")
            .setSmallIcon(R.drawable.ic_generic_hardware)
            .build()
        runCatching {
            startForeground(
                1, notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE or
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        }
    }

    private fun showEdgeTrigger() {
        if (edgeView != null) return
        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE
        val params = WindowManager.LayoutParams(45, WindowManager.LayoutParams.MATCH_PARENT, layoutType, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT).apply { gravity = Gravity.END }
        edgeView = ComposeView(this).apply {
            setContent {
                Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Color.Transparent, NeonBlue.copy(alpha = 0.15f)))).pointerInput(Unit) {
                    detectHorizontalDragGestures { _, dragAmount -> if (dragAmount < -10f) showFullMenu("QUICK_ACCESS") }
                })
            }
        }
        setupView(edgeView!!)
        windowManager.addView(edgeView, params)
    }

    private fun showFullMenu(type: String) {
        if (menuView != null) return
        edgeView?.let { try { windowManager.removeView(it) } catch(e: Exception) {}; edgeView = null }
        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE
        val params = WindowManager.LayoutParams(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT, layoutType, WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL, PixelFormat.TRANSLUCENT)
        menuView = ComposeView(this).apply {
            setContent { 
                if (type == "TEXT_MENU") TextMenuUI(onClose = { hideFullMenu() }) 
                else QuickAccessOverlayUI(
                    onClose = { hideFullMenu() },
                    onTranslate = {
                        hideFullMenu()
                        requestMediaProjection()
                    }
                ) 
            }
        }
        setupView(menuView!!)
        windowManager.addView(menuView, params)
    }

    private fun requestMediaProjection() {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra("REQUEST_PROJECTION", true)
        }
        startActivity(intent)
    }

    private fun hideFullMenu() {
        menuView?.let { windowManager.removeView(it); menuView = null; showEdgeTrigger() }
    }

    private fun setupView(view: ComposeView) {
        view.setViewTreeLifecycleOwner(this)
        view.setViewTreeViewModelStoreOwner(this)
        view.setViewTreeSavedStateRegistryOwner(this)
    }

    override fun onBind(intent: Intent?): IBinder? = null
    override fun onDestroy() {
        // Las corrutinas de traducción seguían vivas reteniendo el Service, y el
        // ViewModelStore nunca se vaciaba. Se limpian ANTES de super.onDestroy().
        serviceScope.cancel()
        store.clear()
        edgeView?.let { try { windowManager.removeView(it) } catch(e: Exception) {} }
        menuView?.let { try { windowManager.removeView(it) } catch(e: Exception) {} }
        toastView?.let { try { windowManager.removeView(it) } catch(e: Exception) {} }
        translationView?.let { try { windowManager.removeView(it) } catch(e: Exception) {} }
        mediaProjection?.stop()
        mediaProjection = null
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        super.onDestroy()
    }
}

@Composable
fun TranslationDialogUI(text: String, onClose: () -> Unit) {
    val primaryColor = ThemeManager.primaryColor
    Surface(
        modifier = Modifier.padding(24.dp).widthIn(max = 400.dp),
        color = MaterialTheme.colorScheme.background,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(2.dp, primaryColor),
        shadowElevation = 20.dp
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Translate, null, tint = primaryColor, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(12.dp))
                Text("TRADUCTOR NEURAL", color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Black)
            }
            Spacer(Modifier.height(16.dp))
            Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, lineHeight = 20.sp)
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = onClose,
                modifier = Modifier.align(Alignment.End),
                colors = ButtonDefaults.buttonColors(containerColor = primaryColor)
            ) {
                Text("CERRAR", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun AchievementToastUI(title: String, desc: String) {
    var visible by remember { mutableStateOf(false) }
    val primaryColor = ThemeManager.primaryColor

    LaunchedEffect(Unit) { visible = true }

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut()
    ) {
        Surface(
            modifier = Modifier.padding(16.dp).width(320.dp),
            color = MaterialTheme.colorScheme.background,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(2.dp, primaryColor),
            shadowElevation = 12.dp
        ) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(40.dp).background(primaryColor.copy(alpha = 0.2f), CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.EmojiEvents, null, tint = primaryColor, modifier = Modifier.size(24.dp))
                }
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(title.uppercase(), color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp, fontWeight = FontWeight.Black)
                    if (desc.isNotEmpty()) Text(desc, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, maxLines = 1)
                }
            }
        }
    }
}

// --- MENÚ DE TEXTO INTEGRAL (SELECT) ---

@Composable
fun TextMenuUI(onClose: () -> Unit) {
    val context = LocalContext.current
    val settings = remember { SettingsManager(context) }
    val primaryColor = ThemeManager.primaryColor
    val focusRequester = remember { FocusRequester() }
    
    var currentPath by remember { mutableStateOf(listOf("AJUSTES SISTEMA")) }
    var selectedIndex by remember { mutableIntStateOf(0) }
    val listState = rememberLazyListState()

    // Estructura de Datos del Menú
    val menuData = remember(currentPath) {
        when (currentPath.last()) {
            "AJUSTES SISTEMA" -> listOf(
                MenuEntry("EMULACIÓN", Icons.Default.SportsEsports, type = EntryType.SUBMENU),
                MenuEntry("INTERFAZ", Icons.Default.Palette, type = EntryType.SUBMENU),
                MenuEntry("SCRAPER", Icons.Default.CloudDownload, type = EntryType.SUBMENU),
                MenuEntry("DISPOSITIVO", Icons.Default.Memory, type = EntryType.SUBMENU),
                MenuEntry("REESCANEO TOTAL", Icons.Default.Refresh, type = EntryType.ACTION),
                MenuEntry("SALIR", Icons.Default.Close, type = EntryType.ACTION)
            )
            "EMULACIÓN" -> listOf(
                MenuEntry("VOLVER", Icons.AutoMirrored.Filled.ArrowBack, type = EntryType.BACK),
                MenuEntry("MODO POR DEFECTO", Icons.Default.Settings, value = settings.getPreferredEmulator("ps1"), options = listOf("RETROARCH", "STANDALONE"), onUpdate = { settings.setPreferredEmulator("ps1", it) }),
                MenuEntry("LIMPIAR CACHÉ ROMS", Icons.Default.DeleteSweep, type = EntryType.ACTION)
            )
            "INTERFAZ" -> listOf(
                MenuEntry("VOLVER", Icons.AutoMirrored.Filled.ArrowBack, type = EntryType.BACK),
                MenuEntry("TEMA ACTUAL", Icons.Default.ColorLens, value = settings.currentTheme, options = listOf("ULTRA", "SPECCY_OS", "INMERSIVE"), onUpdate = { settings.currentTheme = it }),
                MenuEntry("EFECTO CRT", Icons.Default.Tv, value = if(settings.showCrtEffect) "ON" else "OFF", options = listOf("ON", "OFF"), onUpdate = { settings.showCrtEffect = it == "ON" }),
                MenuEntry("MÚSICA FONDO", Icons.Default.MusicNote, value = if(settings.isBackgroundMusicEnabled) "ON" else "OFF", options = listOf("ON", "OFF"), onUpdate = { settings.isBackgroundMusicEnabled = it == "ON" })
            )
            "SCRAPER" -> listOf(
                MenuEntry("VOLVER", Icons.AutoMirrored.Filled.ArrowBack, type = EntryType.BACK),
                MenuEntry("PROVEEDOR", Icons.Default.Cloud, value = "THEGAMESDB", options = listOf("THEGAMESDB", "SCREEN_SCRAPER", "IGDB"), onUpdate = {  }),
                MenuEntry("FORZAR SCRAPING", Icons.Default.PlayArrow, type = EntryType.ACTION)
            )
            "DISPOSITIVO" -> listOf(
                MenuEntry("VOLVER", Icons.AutoMirrored.Filled.ArrowBack, type = EntryType.BACK),
                MenuEntry("PERFIL POTENCIA", Icons.Default.Bolt, value = settings.manualProfile, options = listOf("ECO", "BALANCED", "PERFORMANCE", "EXTREME"), onUpdate = { settings.manualProfile = it }),
                MenuEntry("INFO HARDWARE", Icons.Default.Info, type = EntryType.ACTION)
            )
            else -> emptyList()
        }
    }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    
    // Reset de índice al cambiar de submenú
    LaunchedEffect(currentPath) { selectedIndex = 0; listState.scrollToItem(0) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.85f))
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown) {
                    when (keyEvent.nativeKeyEvent.keyCode) {
                        NativeKeyEvent.KEYCODE_DPAD_UP -> { selectedIndex = (selectedIndex - 1).coerceAtLeast(0); true }
                        NativeKeyEvent.KEYCODE_DPAD_DOWN -> { selectedIndex = (selectedIndex + 1).coerceAtMost(menuData.size - 1); true }
                        NativeKeyEvent.KEYCODE_DPAD_LEFT, NativeKeyEvent.KEYCODE_DPAD_RIGHT -> {
                            val item = menuData[selectedIndex]
                            if (item.options != null && item.onUpdate != null) {
                                val curIdx = item.options.indexOf(item.value)
                                val nextIdx = if (keyEvent.nativeKeyEvent.keyCode == NativeKeyEvent.KEYCODE_DPAD_RIGHT) {
                                    (curIdx + 1) % item.options.size
                                } else {
                                    (curIdx - 1 + item.options.size) % item.options.size
                                }
                                item.onUpdate.invoke(item.options[nextIdx])
                                true
                            } else false
                        }
                        NativeKeyEvent.KEYCODE_ENTER, NativeKeyEvent.KEYCODE_DPAD_CENTER, NativeKeyEvent.KEYCODE_BUTTON_A -> {
                            val item = menuData[selectedIndex]
                            when (item.type) {
                                EntryType.SUBMENU -> currentPath = currentPath + item.label
                                EntryType.BACK -> currentPath = currentPath.dropLast(1)
                                EntryType.ACTION -> {
                                    if (item.label == "SALIR") onClose()
                                }
                                else -> { /* Toggles */ }
                            }
                            true
                        }
                        NativeKeyEvent.KEYCODE_BACK, NativeKeyEvent.KEYCODE_BUTTON_B -> {
                            if (currentPath.size > 1) currentPath = currentPath.dropLast(1) else onClose()
                            true
                        }
                        else -> false
                    }
                } else false
            },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .width(400.dp)
                .background(MaterialTheme.colorScheme.background, RoundedCornerShape(2.dp))
                .border(1.dp, primaryColor.copy(alpha = 0.3f))
                .padding(2.dp)
        ) {
            // Header Estilo Terminal
            Box(Modifier.fillMaxWidth().background(primaryColor).padding(8.dp)) {
                Text(
                    text = currentPath.joinToString(" > "),
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1
                )
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.heightIn(max = 400.dp).padding(vertical = 8.dp)
            ) {
                itemsIndexed(menuData) { index, item ->
                    val isFocused = index == selectedIndex
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(if (isFocused) primaryColor.copy(alpha = 0.15f) else Color.Transparent)
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = null,
                            tint = if (isFocused) primaryColor else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(16.dp))
                        Text(
                            text = item.label,
                            color = if (isFocused) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 14.sp,
                            fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier.weight(1f)
                        )
                        
                        if (item.options != null) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.ChevronLeft, null, tint = if(isFocused) primaryColor else Color.Transparent, modifier = Modifier.size(14.dp))
                                Text(
                                    text = item.value ?: "",
                                    color = if (isFocused) primaryColor else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Black,
                                    modifier = Modifier.padding(horizontal = 4.dp)
                                )
                                Icon(Icons.Default.ChevronRight, null, tint = if(isFocused) primaryColor else Color.Transparent, modifier = Modifier.size(14.dp))
                            }
                        } else if (item.type == EntryType.SUBMENU) {
                            Icon(Icons.AutoMirrored.Filled.ArrowForwardIos, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(10.dp))
                        }
                    }
                }
            }

            ActionLegend(primaryColor)
        }
    }
}

@Composable
fun ActionLegend(color: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        LegendItem("↑↓ NAV", MaterialTheme.colorScheme.onSurfaceVariant)
        LegendItem("←→ CAMBIAR", MaterialTheme.colorScheme.onSurfaceVariant)
        LegendItem("A SELECCIONAR", color)
        LegendItem("B VOLVER", MaterialTheme.colorScheme.error.copy(alpha = 0.7f))
    }
}

@Composable
fun LegendItem(text: String, color: Color) {
    Text(text, color = color, fontSize = 12.sp, fontWeight = FontWeight.Black)
}

enum class EntryType { SUBMENU, ACTION, TOGGLE, BACK }
data class MenuEntry(
    val label: String,
    val icon: ImageVector,
    val type: EntryType = EntryType.TOGGLE,
    val value: String? = null,
    val options: List<String>? = null,
    val onUpdate: ((String) -> Unit)? = null
)

// --- QUICK ACCESS OVERLAY (EXISTENTE) ---

@Composable
fun QuickAccessOverlayUI(onClose: () -> Unit, onTranslate: () -> Unit = {}) {
    val context = LocalContext.current
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    
    var volume by remember { mutableFloatStateOf(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)) }
    var currentProfile by remember { mutableStateOf("BALANCED") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.6f))
            .clickable { onClose() },
        contentAlignment = Alignment.CenterEnd
    ) {
        Surface(
            modifier = Modifier
                .width(340.dp)
                .fillMaxHeight()
                .clickable(enabled = false) {}
                .shadow(25.dp, RoundedCornerShape(topStart = 40.dp, bottomStart = 40.dp), spotColor = NeonBlue),
            color = MaterialTheme.colorScheme.background,
            shape = RoundedCornerShape(topStart = 40.dp, bottomStart = 40.dp),
            border = BorderStroke(1.dp, Brush.verticalGradient(listOf(NeonBlue, Color.Transparent)))
        ) {
            Column(modifier = Modifier.padding(28.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Dashboard, null, tint = NeonBlue, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Text("IMPERIAL ACCESS", color = MaterialTheme.colorScheme.onSurface, fontSize = 18.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                }
                
                Spacer(Modifier.height(32.dp))

                OverlaySlider("VOLUMEN", volume, Icons.AutoMirrored.Filled.VolumeUp) {
                    volume = it
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, (it * audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)).toInt(), 0)
                }
                
                Spacer(Modifier.height(40.dp))
                
                Text("NÚCLEO DE RENDIMIENTO", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(16.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("ECO", "BAL", "PERF", "MAX").forEach { p ->
                        val isSelected = (p == "BAL" && currentProfile == "BALANCED") || (currentProfile.startsWith(p))
                        Surface(
                            modifier = Modifier.weight(1f).height(44.dp).clickable { 
                                val fullProfile = when(p) { "BAL" -> "BALANCED"; "PERF" -> "PERFORMANCE"; "MAX" -> "EXTREME"; else -> "ECO" }
                                currentProfile = fullProfile
                                HardwareControlManagerBeta.setPerformanceMode(fullProfile == "PERFORMANCE" || fullProfile == "EXTREME")
                            },
                            color = if (isSelected) NeonBlue.copy(alpha = 0.2f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.03f),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, if (isSelected) NeonBlue else MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(p, color = if (isSelected) NeonBlue else MaterialTheme.colorScheme.onSurface, fontSize = 12.sp, fontWeight = FontWeight.Black)
                            }
                        }
                    }
                }

                Spacer(Modifier.height(32.dp))
                
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    QuickActionButton(Icons.Default.Psychology, "ARCHITECT", Modifier.weight(1f)) {
                        // Antes era Shell.cmd("am start ..."): reabrir nuestra propia
                        // Activity no necesita shell, y sin root concedido el comando
                        // no se ejecutaba y el boton no hacia absolutamente nada, sin
                        // aviso ninguno (submit() es asincrono y no mira el resultado).
                        // OJO: MainActivity todavia no lee el extra "screen", asi que
                        // por ahora abre el dashboard, no el Arquitecto directamente.
                        context.startActivity(
                            Intent(context, MainActivity::class.java).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                                putExtra("screen", "architect")
                            }
                        )
                        onClose()
                    }
                    QuickActionButton(Icons.Default.Translate, "TRANSLATE", Modifier.weight(1f)) {
                        onTranslate()
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                Button(
                    onClick = {
                        // Igual que arriba: sin root, el am start no llegaba a correr
                        // y el boton de volver al dashboard se quedaba muerto.
                        context.startActivity(
                            Intent(context, MainActivity::class.java).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                            }
                        )
                        onClose()
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.15f)),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
                ) {
                    Icon(Icons.Default.Home, null, tint = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.width(12.dp))
                    Text("DASHBOARD PRINCIPAL", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Black, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
fun QuickActionButton(icon: ImageVector, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        modifier = modifier.height(60.dp).clickable { onClick() },
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(icon, null, tint = NeonBlue, modifier = Modifier.size(20.dp))
            Spacer(Modifier.height(4.dp))
            Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun OverlaySlider(label: String, value: Float, icon: ImageVector, onValueChange: (Float) -> Unit) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = NeonBlue, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(12.dp))
            Text(label, color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(8.dp))
        Slider(
            value = value,
            onValueChange = onValueChange,
            colors = SliderDefaults.colors(thumbColor = NeonBlue, activeTrackColor = NeonBlue, inactiveTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
        )
    }
}
