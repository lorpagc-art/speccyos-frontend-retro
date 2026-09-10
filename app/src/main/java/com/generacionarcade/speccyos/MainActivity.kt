/**
 * Speccy OS E5 Ultra - Community Web Edition
 * Desarrollado por: Speccy81 / Lola Vico Webstudio 2026
 * 
 * Este software es una obra original creada para la preservación y disfrute del sistema retro.
 * Queda prohibida la redistribución no autorizada bajo marcas ajenas.
 */

package com.generacionarcade.speccyos

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.KeyEvent as NativeKeyEvent
import android.view.WindowManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.OptIn
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.documentfile.provider.DocumentFile
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import coil.compose.AsyncImage
import com.generacionarcade.speccyos.theme.NeonBlue
// import ...theme.Typography — ya no se usa: la tipografia la aporta SpeccyTheme
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.ConnectionResult
import com.google.firebase.FirebaseApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.google.android.play.core.review.ReviewManagerFactory
import android.view.MotionEvent
import rikka.shizuku.Shizuku
import androidx.lifecycle.compose.collectAsStateWithLifecycle

class MainActivity : ComponentActivity() {
    private val tag = "SpeccyOS_Main"
    private val mainViewModel: MainViewModel by viewModels()
    private val hardwareViewModel: HardwareViewModel by viewModels()
    private lateinit var settingsManager: SettingsManager
    private lateinit var userStatusManager: UserStatusManager
    private lateinit var soundManager: SoundManager
    private lateinit var hapticHandler: HapticHandler
    private lateinit var navController: NavController

    private val shizukuListener = Shizuku.OnRequestPermissionResultListener { _, _ ->
        hardwareViewModel.reconnectShizuku()
    }

    private val lifecycleObserver = LifecycleEventObserver { _, event ->
        when (event) {
            Lifecycle.Event.ON_STOP -> {
                // El launcher deja de monitorizar hardware mientras no se ve:
                // el bucle de HardwareViewModel sondea sysfs cada 2 s y, con un
                // emulador delante, ese goteo compite por CPU con el juego.
                hardwareViewModel.setMonitoringActive(false)

                if (LauncherManager.gameSessionActive) {
                    // ATENCION: NO bajar a ECO aqui. Pasamos a segundo plano
                    // PORQUE acabamos de lanzar un juego; limitar el CPU justo
                    // en ese instante es lo que dejaba a RetroArch sin ciclos
                    // ("ANR ... Waited 5001ms for KeyEvent"). El hardware es del
                    // emulador hasta que el usuario vuelva al launcher.
                    Log.d(tag, "App en segundo plano con juego en marcha: se mantiene el perfil de rendimiento.")
                } else {
                    Log.d(tag, "App en segundo plano: Protocolo de ahorro extremo.")
                    HardwareControlManagerBeta.applyHardwareMode("ECO")
                }
            }
            Lifecycle.Event.ON_START -> {
                Log.d(tag, "App en primer plano: Restaurando hardware.")
                LauncherManager.gameSessionActive = false
                hardwareViewModel.setMonitoringActive(true)
                if (::settingsManager.isInitialized && settingsManager.isHardwareConfigured) {
                    val mode = settingsManager.manualProfile.ifEmpty { "BALANCED" }
                    HardwareControlManagerBeta.applyHardwareMode(mode)
                }
            }
            else -> {}
        }
    }

    private val googleSignInLauncher: ActivityResultLauncher<Intent> = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            settingsManager.userEmail = account?.email
            userStatusManager.syncUser(
                email = account?.email ?: "",
                name = account?.displayName ?: "Usuario",
                photoUrl = account?.photoUrl?.toString() ?: ""
            )
            onLoginSuccessAction?.invoke()
        } catch (e: ApiException) {
            Log.e(tag, "Google Login Error: ${e.statusCode} - ${e.message}")
            Toast.makeText(this, "Acceso Google cancelado o fallido (${e.statusCode})", Toast.LENGTH_SHORT).show()
        }
    }

    private val projectionLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val serviceIntent = Intent(this, OverlayService::class.java).apply {
                action = "START_PROJECTION"
                putExtra("RESULT_CODE", result.resultCode)
                putExtra("DATA", result.data)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
        }
    }

    private var onLoginSuccessAction: (() -> Unit)? = null

    @OptIn(UnstableApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        lifecycle.addObserver(lifecycleObserver)
        Shizuku.addRequestPermissionResultListener(shizukuListener)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Anuncios y UMP eliminados para la versión Viral Gratuita

        try {
            FirebaseApp.initializeApp(this)
        } catch (e: Exception) {
            Log.e(tag, "Firebase initialization failed: ${e.message}")
        }

        settingsManager = SettingsManager(this)
        userStatusManager = UserStatusManager(this)
        RetroArchDatabase.initialize(this)
        soundManager = SoundManager(this)
        hapticHandler = HapticHandler(this)

        // Callbacks de sincronización RetroArch → SpeccyOS
        RetroArchSyncManager.onReturnFromGame = { platformId ->
            Log.d(tag, "Retorno de RetroArch detectado (plataforma: $platformId)")
            // El hardware ya fue restaurado a BALANCED por RetroArchSyncManager.
            // Aquí podemos añadir lógica extra de UI si se necesita en el futuro.
        }
        RetroArchSyncManager.onThermalThrottle = { tempMc, newMode ->
            val tempC = tempMc / 1000
            Log.w(tag, "Thermal throttle: ${tempC}°C → modo $newMode")
            // Toast informativo solo en modo debug para no molestar al usuario en producción
            if (com.generacionarcade.speccyos.BuildConfig.DEBUG) {
                android.widget.Toast.makeText(
                    this,
                    "⚠️ Temperatura alta (${tempC}°C) — reduciendo rendimiento",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
        }

        // El stick analogico se traduce a eventos de cruceta: asi todas las
        // pantallas existentes, que ya escuchan D-pad, funcionan sin tocarlas.
        SpeccyAnalogInput.attach { keyCode ->
            val now = android.os.SystemClock.uptimeMillis()
            dispatchKeyEvent(NativeKeyEvent(now, now, NativeKeyEvent.ACTION_DOWN, keyCode, 0))
            dispatchKeyEvent(NativeKeyEvent(now, now, NativeKeyEvent.ACTION_UP, keyCode, 0))
        }

        DailyNotificationScheduler.schedule(this)

        handleIntent(intent)

        setContent {
            // El escalado de fuente de "Ajustes de pantalla" (70–150 %) entra en el
            // tema, no en cada Text(): asi afecta a toda la interfaz de una vez.
            val uiFontScale = if (::settingsManager.isInitialized) settingsManager.uiFontScale else 1f
            val progress by mainViewModel.scanProgress.collectAsStateWithLifecycle()
            val statusMsg by mainViewModel.scanStatusMessage.collectAsStateWithLifecycle()
            val isScanning by mainViewModel.isScanning.collectAsStateWithLifecycle()
            val isReady by mainViewModel.isReady.collectAsStateWithLifecycle()

            val scaleX by mainViewModel.screenScaleX.collectAsStateWithLifecycle()
            val scaleY by mainViewModel.screenScaleY.collectAsStateWithLifecycle()
            val aspectR by mainViewModel.screenAspectRatio.collectAsStateWithLifecycle()

            // SpeccyTheme aporta tres cosas que el MaterialTheme anterior no daba:
            //  - los DIECIOCHO roles de color (antes ocho; el resto caia en el
            //    morado por defecto de Material),
            //  - tipografia escalada por clase de aparato, con piso de 12 sp,
            //  - LocalDeviceClass, para que los composables sepan si estan en un
            //    handheld de 4,5", una tablet o un televisor a tres metros.
            SpeccyTheme(fontScale = uiFontScale) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .then(
                                if (aspectR == SettingsManager.ASPECT_RATIO_AUTO || aspectR.isEmpty()) {
                                    Modifier
                                } else {
                                    val parts = aspectR.split(":")
                                    if (parts.size == 2) {
                                        val w = parts[0].toFloatOrNull() ?: 16f
                                        val h = parts[1].toFloatOrNull() ?: 9f
                                        Modifier.aspectRatio(w / h)
                                    } else Modifier
                                }
                            )
                            .graphicsLayer {
                                this.scaleX = scaleX
                                this.scaleY = scaleY
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        val currentNavController = rememberNavController()
                        navController = currentNavController

                        NavHost(navController = currentNavController, startDestination = "intro", modifier = Modifier.fillMaxSize()) {
                            composable("intro") {
                                IntroScreen {
                                    val nextDest = when {
                                        settingsManager.userEmail == null -> if (BuildConfig.IS_FULL_VERSION) "auth" else "profile_setup"
                                        !settingsManager.isHardwareConfigured -> "onboarding_viral"
                                        settingsManager.romsLocation.isEmpty() || !isRomsLocationValid(settingsManager.romsLocation) -> "setup_folder"
                                        else -> "loading"
                                    }
                                    currentNavController.navigate(nextDest) { popUpTo("intro") { inclusive = true } }
                                }
                            }
                            composable("auth") { LoginScreen { currentNavController.navigate("welcome") { popUpTo("auth") { inclusive = true } } } }
                            composable("profile_setup") { ProfileCreationScreen { currentNavController.navigate("welcome") { popUpTo("profile_setup") { inclusive = true } } } }
                            composable("welcome") {
                                WelcomeScreen(
                                    onAutoDetect = { soundManager.playClick(); currentNavController.navigate("tech_info") },
                                    onManualSelect = { soundManager.playClick(); currentNavController.navigate("setup_hardware") }
                                )
                            }
                            composable("onboarding_viral") {
                                ViralOnboardingFlow(
                                    settingsManager = settingsManager,
                                    userStatusManager = userStatusManager,
                                    onComplete = {
                                        currentNavController.navigate("setup_folder") {
                                            popUpTo("onboarding_viral") { inclusive = true }
                                        }
                                    }
                                )
                            }
                            composable("tech_info") { TechnicalInfoScreen(
                                onConfirmDetected = { id ->
                                    soundManager.playClick()
                                    settingsManager.manualHardwareId = id
                                    settingsManager.isHardwareConfigured = true
                                    HardwareControlManagerBeta.initialize(id, application)
                                    currentNavController.navigate("setup_folder")
                                },
                                onBack = { soundManager.playBack(); currentNavController.popBackStack() }
                            ) }
                            composable("setup_hardware") { HardwareCarouselSelector(
                                onSelected = { id ->
                                    soundManager.playClick()
                                    hapticHandler.playClick()
                                    settingsManager.manualHardwareId = id
                                    settingsManager.isHardwareConfigured = true
                                    HardwareControlManagerBeta.initialize(id, application)
                                    currentNavController.navigate("setup_folder")
                                },
                                onBack = { soundManager.playBack(); currentNavController.popBackStack() }
                            ) }
                            composable("setup_folder") { FolderSetupScreen(
                                onNext = {
                                    soundManager.playClick()
                                    if (Settings.canDrawOverlays(this@MainActivity)) {
                                        currentNavController.navigate("loading")
                                    } else {
                                        currentNavController.navigate("disclosure")
                                    }
                                },
                                onBack = { soundManager.playBack(); currentNavController.popBackStack() }
                            ) }
                            composable("disclosure") {
                                DisclosureScreen(
                                    onAccept = { currentNavController.navigate("setup_permissions") },
                                    onBack = { currentNavController.popBackStack() }
                                )
                            }
                            composable("setup_permissions") { MediaPermissionsScreen(
                                onNext = { soundManager.playClick(); currentNavController.navigate("loading") },
                                onBack = { soundManager.playBack(); currentNavController.popBackStack() }
                            ) }
                            composable("loading") {
                                LaunchedEffect(Unit) {
                                    delay(600)
                                    withContext(Dispatchers.IO) { HardwareControlManagerBeta.initialize(settingsManager.manualHardwareId, application) }
                                    if (Settings.canDrawOverlays(this@MainActivity)) { startService(Intent(this@MainActivity, OverlayService::class.java)) }

                                    val gameCount = withContext(Dispatchers.IO) { mainViewModel.getGameCountSync() }
                                    if (gameCount == 0 && settingsManager.romsLocation.isNotEmpty() && !isScanning) {
                                        mainViewModel.fullRescan()
                                    }
                                }
                                LaunchedEffect(isReady, isScanning) {
                                    if (isReady && !isScanning) {
                                        delay(1500)
                                        currentNavController.navigate("dashboard") { popUpTo(0) }
                                    }
                                }
                                val context = LocalContext.current
                                ViralBootShareScreen(
                                    progress = progress,
                                    status = statusMsg,
                                    settingsManager = settingsManager,
                                    onShareReady = { file ->
                                        BootShareGenerator.shareImage(context, file)
                                        mainViewModel.achievementEngine.onBootShared()
                                    }
                                )
                            }
                            composable("dashboard") {
                                var showManualDialog by remember { mutableStateOf(false) }

                                if (showManualDialog) {
                                    AlertDialog(
                                        onDismissRequest = { showManualDialog = false },
                                        title = { Text("Manual del Sistema", color = NeonBlue, fontFamily = ThemeManager.titleFont) },
                                        text = {
                                            Text(
                                                "Speccy OS E5 Ultra - Guía Rápida:\n\n" +
                                                "1. Copia tus ROMs en las carpetas correspondientes.\n" +
                                                "2. Selecciona la plataforma y el juego.\n" +
                                                "3. Para ajustar el rendimiento, usa el Tweaker.\n" +
                                                "4. El Trivial Arcade te permite ganar logros.\n" +
                                                "5. Disfruta de la mejor experiencia retro.",
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        },
                                        confirmButton = {
                                            TextButton(onClick = { showManualDialog = false }) {
                                                Text("Entendido", color = NeonBlue)
                                            }
                                        },
                                        containerColor = MaterialTheme.colorScheme.surface
                                    )
                                }

                                SpeccyDashboard(
                                    mainViewModel = mainViewModel,
                                    hardwareViewModel = hardwareViewModel,
                                    userStatusManager = userStatusManager,
                                    settingsManager = settingsManager,
                                    soundManager = soundManager,
                                    hapticHandler = hapticHandler,
                                    onSettingsClick = { soundManager.playClick(); currentNavController.navigate("settings") },
                                    onArchitectClick = { soundManager.playClick(); currentNavController.navigate("architect") },
                                    onManualClick = { soundManager.playClick(); showManualDialog = true },
                                    onBenchmarkClick = { soundManager.playClick(); currentNavController.navigate("benchmark") },
                                    // El buscador ya no es un "proximamente": SmartSearchScreen se abre como
                                    // overlay dentro del propio dashboard (tecla X), donde tiene
                                    // acceso a launchGameFunction. Este callback queda sin uso.
                                    onSearchClick = { },
                                    onTweakerClick = { soundManager.playClick(); currentNavController.navigate("tweaker") },
                                    onAchievementsClick = { soundManager.playClick(); currentNavController.navigate("achievements") },
                                    onWarRoomClick = { soundManager.playClick(); currentNavController.navigate("war_room") }
                                )
                            }
                            composable("achievements") {
                                AchievementsScreen(
                                    engine = mainViewModel.achievementEngine,
                                    onBack = { currentNavController.popBackStack() }
                                )
                            }
                            // Destino "web_auth" ELIMINADO. WebAuthScreen daba la sesion por
                            // buena solo porque la URL navegada contenia "auth_success?email=",
                            // sin token ni verificacion contra el servidor: cualquier redirect
                            // en el dominio autenticaba el correo que se quisiera, con
                            // JavaScript habilitado. No lo alcanzaba ningun otro destino del
                            // NavHost, pero seguia vivo en el grafo y en el binario publicado.
                            // Si se recupera el login web, tiene que devolver un token firmado
                            // y validarse en el backend antes de tocar userStatusManager.
                            composable("settings") {
                                SettingsScreen(
                                    hardwareViewModel = hardwareViewModel,
                                    mainViewModel = mainViewModel,
                                    settingsManager = settingsManager,
                                    soundManager = soundManager,
                                    onBack = { soundManager.playBack(); currentNavController.popBackStack() }
                                )
                            }
                            composable("architect") {
                                ArchitectChatScreen(
                                    mainViewModel = mainViewModel,
                                    hardwareViewModel = hardwareViewModel,
                                    onSafeClose = { soundManager.playBack(); currentNavController.popBackStack() }
                                )
                            }
                            composable("benchmark") {
                                BenchmarkScreen(
                                    onBack = { soundManager.playBack(); currentNavController.popBackStack() }
                                )
                            }
                            composable("tweaker") {
                                NebulaTweakerScreen(
                                    hardwareViewModel = hardwareViewModel,
                                    soundManager = soundManager,
                                    onBack = { soundManager.playBack(); currentNavController.popBackStack() }
                                )
                            }
                            composable("war_room") {
                                HardwareWarRoom(
                                    hardwareViewModel = hardwareViewModel,
                                    onBack = { soundManager.playBack(); currentNavController.popBackStack() }
                                )
                            }
                            composable("credits") {
                                CreditsScreen(onBack = { soundManager.playBack(); currentNavController.popBackStack() })
                            }
                        }
                    }
                }
            }
        }
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.getBooleanExtra("REQUEST_PROJECTION", false) == true) {
            val mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            projectionLauncher.launch(mediaProjectionManager.createScreenCaptureIntent())
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    // Interceptamos BACK para evitar cierre accidental. Se notifica a super en todos los casos.
    override fun onKeyDown(keyCode: Int, event: NativeKeyEvent?): Boolean {
        if (keyCode == NativeKeyEvent.KEYCODE_BACK) {
            super.onKeyDown(keyCode, event)
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    /**
     * NAVEGACION CON STICK ANALOGICO.
     *
     * En los 97 ficheros del proyecto no habia ni una referencia a MotionEvent ni
     * a onGenericMotionEvent: toda la interfaz se movia SOLO con la cruceta,
     * mientras `KeyMapper` ya definia lStick/rStick sin que nada los usara. En
     * Retroid, AYANEO y la serie RG mucha gente navega por defecto con el stick
     * izquierdo, y la app simplemente no respondia.
     *
     * Aqui el stick se traduce a eventos de cruceta, con lo que todas las
     * pantallas existentes funcionan sin tocar ni una.
     */
    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if (SpeccyAnalogInput.onMotionEvent(event)) return true
        return super.onGenericMotionEvent(event)
    }

    override fun onPause() {
        super.onPause()
        // Evita que una direccion quede "pegada" al volver de un emulador.
        SpeccyAnalogInput.reset()
    }

    override fun onDestroy() {
        // Estas dos lambdas capturan la Activity (una para el Toast) y viven en un
        // `object` estático: sin anularlas, cada recreación deja una MainActivity
        // destruida retenida indefinidamente.
        SpeccyAnalogInput.detach()
        RetroArchSyncManager.releaseUiCallbacks()
        // SoundPool, MediaPlayer del BGM y ToneGenerator se filtraban en cada
        // recreacion de la Activity: nadie llamaba nunca a release().
        if (::soundManager.isInitialized) soundManager.release()
        Shizuku.removeRequestPermissionResultListener(shizukuListener)
        // NO se llama a RetroArchSyncManager.destroy(): su ciclo de vida es el del
        // proceso (lo registró SpeccyApplication), no el de esta Activity.
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()

        if (::settingsManager.isInitialized && settingsManager.isReturningFromGame) {
            settingsManager.isReturningFromGame = false

            // Restaurar hardware a BALANCED y limpiar estado de sesión RetroArch.
            // Esto actúa como fallback si el BroadcastReceiver de EXIT no llegó
            // (ocurre cuando Android mató el proceso de SpeccyOS durante la sesión).
            RetroArchSyncManager.handleReturnFromGame()

            // Aprendizaje de cores: RetroArch no contesta si el core cargo o
            // no, pero el tiempo que se ha tardado en volver lo dice. Volver a
            // los pocos segundos = no arranco (pantalla negra); jugar un rato =
            // ese core vale para esta consola y sus ROMs.
            SpeccyCoreResolver.anotarRegreso(this, settingsManager)?.let { intento ->
                Toast.makeText(
                    this,
                    "Parece que ${intento.coreFallido} no arrancó. " +
                        "La próxima vez se probará con ${intento.coreSugerido}.",
                    Toast.LENGTH_LONG
                ).show()
            }

            val count = settingsManager.gamesLaunchedCount
            if (count == 5 || (count > 5 && count % 20 == 0)) {
                triggerInAppReview()
            }
        }
    }

    private fun triggerInAppReview() {
        val reviewManager = ReviewManagerFactory.create(this)
        val requestFlow = reviewManager.requestReviewFlow()

        requestFlow.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val reviewInfo = task.result
                val flow = reviewManager.launchReviewFlow(this, reviewInfo)
                flow.addOnCompleteListener {
                    Log.d(tag, "In-App Review process completed.")
                }
            } else {
                Log.e(tag, "In-App Review request failed.")
            }
        }
    }

    private fun isRomsLocationValid(uriString: String): Boolean {
        return try {
            val uri = uriString.toUri()
            val doc = DocumentFile.fromTreeUri(this, uri)
            doc != null && doc.exists() && doc.canRead()
        } catch (_: Exception) {
            false
        }
    }

    @Composable
    fun DisclosureScreen(onAccept: () -> Unit, onBack: () -> Unit) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(24.dp)) {
            Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                Icon(Icons.Default.Security, null, tint = NeonBlue, modifier = Modifier.size(64.dp))
                Spacer(Modifier.height(16.dp))
                Text("PRIVACIDAD Y POLÍTICAS", color = MaterialTheme.colorScheme.onSurface, fontSize = 24.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.height(24.dp))

                DisclosureItem(
                    title = stringResource(R.string.perm_accessibility_title).uppercase(),
                    desc = stringResource(R.string.perm_accessibility_msg)
                )

                DisclosureItem(
                    title = stringResource(R.string.perm_notifications_title).uppercase(),
                    desc = stringResource(R.string.perm_notifications_msg)
                )

                DisclosureItem(
                    title = stringResource(R.string.perm_storage_title).uppercase(),
                    desc = stringResource(R.string.perm_storage_msg)
                )

                DisclosureItem(
                    title = stringResource(R.string.perm_query_packages_title).uppercase(),
                    desc = stringResource(R.string.perm_query_packages_msg)
                )

                DisclosureItem(
                    title = stringResource(R.string.perm_overlay_title).uppercase(),
                    desc = stringResource(R.string.perm_overlay_msg)
                )

                Spacer(Modifier.height(32.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = onBack) { Text("CANCELAR", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    Button(
                        onClick = {
                            onAccept()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = NeonBlue),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("ACEPTAR Y CONTINUAR", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.height(40.dp))
            }
        }
    }

    @Composable
    fun DisclosureItem(title: String, desc: String) {
        Column(modifier = Modifier.padding(bottom = 20.dp)) {
            Text(title, color = NeonBlue, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(desc, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, lineHeight = 18.sp)
        }
    }

    @Composable
    fun RetroScanScreen(progress: Float, status: String) {
        var lines by remember { mutableStateOf(listOf<String>()) }
        val scanPercent = (progress * 100).toInt()
        val hwProfile = remember {
            HardwareControlManagerBeta.hardwareProfiles[settingsManager.manualHardwareId]
                ?: HardwareControlManagerBeta.hardwareProfiles["generic"]!!
        }
        LaunchedEffect(Unit) {
            val bootSequence = listOf(
                "SPECCY OS E5 ULTRA BIOS v0.6.9",
                "Copyright (C) 2024 Generacion Arcade",
                "",
                "DETECTED HARDWARE: ${hwProfile.name.uppercase()} [OK]",
                "CPU: ${hwProfile.chipset.uppercase()} [OK]",
                "GPU: ${hwProfile.gpu.uppercase()} [VULKAN READY]",
                "RAM: ${hwProfile.ram.uppercase()} INSTALLED",
                "COOLING SYSTEM: ${if (hwProfile.hasActiveCooling) "ACTIVE FAN DETECTED" else "PASSIVE"} [OK]",
                "CAPACITY CLASS: ${hwProfile.emulationCapacity.uppercase()}",
                "Initializing System Kernel... [ONLINE]",
                ""
            )
            for (line in bootSequence) { lines = lines + line; delay(150) }
        }
        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(24.dp)) {
            Column(modifier = Modifier.fillMaxSize()) {
                lines.forEach { Text(text = it, color = SpeccyPalette.ok, fontSize = 14.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold) }
                if (lines.size > 8) {
                    Text(text = "Mounting Filesystem... [${scanPercent}%]", color = SpeccyPalette.warn, fontSize = 14.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                    if (status.isNotEmpty()) Text(text = "> $status", color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                }
            }
        }
    }

    @Composable
    fun ActionChip(btn: String, label: String, color: Color, onClick: () -> Unit = {}) {
        Surface(modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable { onClick() }, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f), border = BorderStroke(1.dp, color.copy(alpha = 0.8f))) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                Box(modifier = Modifier.size(28.dp).clip(CircleShape).background(color), contentAlignment = Alignment.Center) {
                    Text(text = btn, color = if (color.luminance() > 0.45f) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Black)
                }
                Spacer(Modifier.width(12.dp))
                Text(text = label.uppercase(), color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
            }
        }
    }

    @Composable
    fun IntroScreen(onFinished: () -> Unit) {
        val currentIdx = if (settingsManager.lastIntroImageIndex == 2) 3 else 2
        LaunchedEffect(Unit) { settingsManager.lastIntroImageIndex = currentIdx; delay(4000); onFinished() }
        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
            AsyncImage(model = "file:///android_asset/contentimg/$currentIdx.webp", contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
    }

    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    fun LoginScreen(onSuccess: () -> Unit) {
        var showManualInput by remember { mutableStateOf(false) }
        var manualEmail by remember { mutableStateOf("") }

        onLoginSuccessAction = onSuccess

        val hasGms = remember {
            GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(this@MainActivity) == ConnectionResult.SUCCESS
        }

        LaunchedEffect(hasGms) {
            if (!hasGms) showManualInput = true
        }

        val konamiFocusRequester = remember { FocusRequester() }
        LaunchedEffect(Unit) { konamiFocusRequester.requestFocus() }

        val konamiSequence = listOf(
            NativeKeyEvent.KEYCODE_DPAD_UP, NativeKeyEvent.KEYCODE_DPAD_UP,
            NativeKeyEvent.KEYCODE_DPAD_DOWN, NativeKeyEvent.KEYCODE_DPAD_DOWN,
            NativeKeyEvent.KEYCODE_DPAD_LEFT, NativeKeyEvent.KEYCODE_DPAD_RIGHT,
            NativeKeyEvent.KEYCODE_DPAD_LEFT, NativeKeyEvent.KEYCODE_DPAD_RIGHT,
            NativeKeyEvent.KEYCODE_BUTTON_B, NativeKeyEvent.KEYCODE_BUTTON_A
        )
        var konamiInput by remember { mutableStateOf(emptyList<Int>()) }

        val webClientId = stringResource(id = R.string.default_web_client_id)

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(webClientId)
            .requestEmail()
            .requestProfile()
            .build()

        // remember: se reconstruia el cliente entero en cada recomposicion de la
        // pantalla de login.
        val client = remember(gso) { GoogleSignIn.getClient(this@MainActivity, gso) }

        Box(
            Modifier
                .fillMaxSize()
                .focusRequester(konamiFocusRequester)
                .focusable()
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown) {
                        val code = event.nativeKeyEvent.keyCode
                        val newInput = (konamiInput + code).takeLast(konamiSequence.size)
                        konamiInput = newInput

                        // Easter egg sin efectos: la app es gratuita y no hay nada
                        // que desbloquear. Antes concedia el nivel Imperial e inyectaba
                        // el correo del desarrollador como sesion iniciada.
                        if (konamiInput == konamiSequence) {
                            konamiInput = emptyList()
                            soundManager.playLaunch()
                            Toast.makeText(this@MainActivity, "↑↑↓↓←→←→ B A", Toast.LENGTH_SHORT).show()
                            true
                        } else false
                    } else false
                },
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(model = "file:///android_asset/contentimg/banner-neon.webp", contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background.copy(alpha = 0.5f)))
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                Icon(Icons.Default.Lock, null, tint = NeonBlue, modifier = Modifier.size(64.dp))
                Spacer(Modifier.height(24.dp))
                Text(text = if (hasGms) "CONTROL DE ACCESO" else "AUTENTICACIÓN LOCAL", color = MaterialTheme.colorScheme.onSurface, fontSize = 24.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.height(48.dp))

                if (!showManualInput && hasGms) {
                    Button(onClick = { client.signOut().addOnCompleteListener { googleSignInLauncher.launch(client.signInIntent) } }, modifier = Modifier.fillMaxWidth().height(60.dp)) { Text("IDENTIFICARSE CON GOOGLE") }
                    Spacer(Modifier.height(16.dp))
                    TextButton(onClick = { showManualInput = true }) { Text("USAR CUENTA LOCAL", color = NeonBlue) }
                } else {
                    OutlinedTextField(
                        value = manualEmail,
                        onValueChange = { manualEmail = it },
                        label = { Text("Nombre de perfil o email") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = MaterialTheme.colorScheme.onSurface, unfocusedTextColor = MaterialTheme.colorScheme.onSurface)
                    )

                    Button(onClick = {
                        // Perfil local sin contrasena: la app es gratuita y no hay
                        // nada que proteger. Antes habia dos contrasenas maestras en
                        // texto plano dentro del binario publicado.
                        if (manualEmail.isNotBlank()) {
                            settingsManager.userEmail = manualEmail
                            userStatusManager.syncUser(manualEmail, manualEmail.substringBefore("@"), "")
                            onSuccess()
                        } else {
                            Toast.makeText(this@MainActivity, "Escribe un nombre para tu perfil", Toast.LENGTH_SHORT).show()
                        }
                    }, modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
                        Text("ENTRAR AL SISTEMA")
                    }

                    if (hasGms) {
                        TextButton(onClick = { showManualInput = false }) { Text("VOLVER AL LOGIN GOOGLE", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
            }
        }
    }

    @Composable
    fun ProfileCreationScreen(onSuccess: () -> Unit) {
        var nickname by remember { mutableStateOf("") }
        val avatars = listOf(
            "https://api.dicebear.com/8.x/pixel-art/png?seed=Retro1&size=128",
            "https://api.dicebear.com/8.x/pixel-art/png?seed=Arcade2&size=128",
            "https://api.dicebear.com/8.x/pixel-art/png?seed=Gamer3&size=128",
            "https://api.dicebear.com/8.x/pixel-art/png?seed=Speccy4&size=128",
            "https://api.dicebear.com/8.x/pixel-art/png?seed=Player5&size=128",
            "https://api.dicebear.com/8.x/pixel-art/png?seed=Ninja6&size=128",
            "https://api.dicebear.com/8.x/pixel-art/png?seed=Ultra7&size=128"
        )
        var selectedAvatar by remember { mutableStateOf(avatars.first()) }
        val scrollState = rememberScrollState()

        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
            AsyncImage(model = "file:///android_asset/contentimg/banner-neon.webp", contentDescription = null, modifier = Modifier.fillMaxSize().alpha(0.3f).blur(12.dp), contentScale = ContentScale.Crop)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally, 
                modifier = Modifier
                    .fillMaxHeight()
                    .verticalScroll(scrollState)
                    .padding(32.dp)
                    .widthIn(max = 500.dp)
            ) {
                Icon(Icons.Default.Person, null, tint = NeonBlue, modifier = Modifier.size(64.dp))
                Spacer(Modifier.height(16.dp))
                Text(text = "CREA TU PERFIL RETRO", color = MaterialTheme.colorScheme.onSurface, fontSize = 28.sp, fontWeight = FontWeight.Black)
                Text(text = "Personaliza tu identidad en el sistema", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                Spacer(Modifier.height(24.dp))

                val infiniteTransition = rememberInfiniteTransition(label = "avatar")
                val glowAlpha by infiniteTransition.animateFloat(
                    initialValue = 0.3f, targetValue = 0.8f,
                    animationSpec = infiniteRepeatable(tween(1200), RepeatMode.Reverse), label = "glow"
                )

                Box(
                    modifier = Modifier.size(100.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surface.copy(alpha = 0.1f)).border(2.dp, NeonBlue.copy(alpha = glowAlpha), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(model = selectedAvatar, contentDescription = "Avatar Seleccionado", modifier = Modifier.size(60.dp), contentScale = ContentScale.Fit)
                }

                Spacer(Modifier.height(24.dp))

                LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                    items(avatars) { avatar ->
                        Box(
                            modifier = Modifier
                                .size(60.dp)
                                .clip(CircleShape)
                                .background(if (selectedAvatar == avatar) NeonBlue.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surface.copy(alpha = 0.05f))
                                .border(if (selectedAvatar == avatar) 2.dp else 1.dp, if (selectedAvatar == avatar) NeonBlue else MaterialTheme.colorScheme.outline, CircleShape)
                                .clickable { selectedAvatar = avatar },
                            contentAlignment = Alignment.Center
                        ) {
                            AsyncImage(model = avatar, contentDescription = null, modifier = Modifier.size(36.dp), contentScale = ContentScale.Fit)
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))

                OutlinedTextField(
                    value = nickname,
                    onValueChange = { nickname = it },
                    label = { Text("TU APODO (NICKNAME)") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = MaterialTheme.colorScheme.onSurface, unfocusedTextColor = MaterialTheme.colorScheme.onSurface, focusedBorderColor = NeonBlue),
                    singleLine = true
                )

                Spacer(Modifier.height(24.dp))

                Button(
                    onClick = {
                        val finalNick = nickname.ifEmpty { "Jugador Retro" }
                        val dummyEmail = "webuser_${System.currentTimeMillis()}@generacionarcade.com"
                        settingsManager.userEmail = dummyEmail
                        userStatusManager.syncUser(dummyEmail, finalNick, selectedAvatar)
                        onSuccess()
                    },
                    modifier = Modifier.fillMaxWidth().height(60.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = NeonBlue),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("INICIAR SESIÓN EN EL SISTEMA", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Black)
                }
                
                Spacer(Modifier.height(32.dp))
            }
        }
    }

    @Composable
    fun WelcomeScreen(onAutoDetect: () -> Unit, onManualSelect: () -> Unit) {
        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
            AsyncImage(model = "file:///android_asset/contentimg/banner-neon.webp", contentDescription = null, modifier = Modifier.fillMaxSize().alpha(0.4f).blur(8.dp), contentScale = ContentScale.Crop)
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                Text(text = "Speccy OS E5 Ultra", color = NeonBlue, fontSize = 42.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
                Spacer(Modifier.height(80.dp))
                Button(onClick = onAutoDetect, modifier = Modifier.width(340.dp).height(70.dp), shape = RoundedCornerShape(20.dp)) { Text("DETECCIÓN AUTOMÁTICA") }
                Spacer(Modifier.height(20.dp))
                OutlinedButton(onClick = onManualSelect, modifier = Modifier.width(340.dp).height(70.dp), shape = RoundedCornerShape(20.dp)) { Text("SELECCIÓN MANUAL", color = MaterialTheme.colorScheme.onSurface) }
            }
        }
    }

    @Composable
    fun TechnicalInfoScreen(onConfirmDetected: (String) -> Unit, onBack: () -> Unit) {
        val profile = remember { HardwareControlManagerBeta.detectActualHardware() }
        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
            AsyncImage(model = "file:///android_asset/contentimg/hardware.webp", contentDescription = null, modifier = Modifier.fillMaxSize().alpha(0.15f), contentScale = ContentScale.Fit)
            Column(modifier = Modifier.padding(horizontal = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = "INFORME DE SISTEMA", color = NeonBlue, fontSize = 32.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.height(32.dp))
                Text(text = "PERFIL DETECTADO: ${profile.name}", color = MaterialTheme.colorScheme.onSurface, fontSize = 18.sp)
                Spacer(Modifier.height(64.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    ActionChip("B", "VOLVER", MaterialTheme.colorScheme.onSurfaceVariant, onClick = onBack)
                    ActionChip("A", "VINCULAR PERFIL", NeonBlue, onClick = { onConfirmDetected(profile.id) })
                }
            }
        }
    }

    @Composable
    fun HardwareCarouselSelector(onSelected: (String) -> Unit, onBack: () -> Unit) {
        val allProfiles = remember { HardwareControlManagerBeta.hardwareProfiles.values.toList() }
        var selectedType by remember { mutableStateOf("HANDHELD") }
        val filteredProfiles by remember(selectedType) {
            derivedStateOf { allProfiles.filter { it.type == selectedType || (selectedType == "OTROS" && it.type != "HANDHELD" && it.type != "SMARTPHONE" && it.type != "TABLET") } }
        }
        val primaryColor = MaterialTheme.colorScheme.primary
        BackHandler { onBack() }
        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val gridStep = 80f
                for (i in 0..10) { val y = i * gridStep; drawLine(color = primaryColor.copy(alpha = 0.05f), start = Offset(0f, y), end = Offset(size.width, y), strokeWidth = 2f) }
            }
            Box(modifier = Modifier.fillMaxSize().background(Brush.radialGradient(listOf(primaryColor.copy(alpha = 0.15f), Color.Transparent), radius = 1500f)))
            Column(modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 24.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 24.dp)) {
                    Icon(Icons.Default.Memory, null, tint = primaryColor, modifier = Modifier.size(32.dp))
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(text = "SELECCIONAR PERFIL DE HARDWARE", color = MaterialTheme.colorScheme.onSurface, fontSize = 24.sp, fontWeight = FontWeight.Black)
                        Text(text = "Speccy OS adaptará el sistema a este perfil", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(bottom = 24.dp)) {
                    HardwareTypeTab("CONSOLAS", selectedType == "HANDHELD", primaryColor) { selectedType = "HANDHELD" }
                    HardwareTypeTab("MÓVILES", selectedType == "SMARTPHONE", primaryColor) { selectedType = "SMARTPHONE" }
                    HardwareTypeTab("TABLETS", selectedType == "TABLET", primaryColor) { selectedType = "TABLET" }
                    HardwareTypeTab("OTROS", selectedType == "OTROS", primaryColor) { selectedType = "OTROS" }
                }
                LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(bottom = 32.dp)) {
                    val chunked = filteredProfiles.chunked(2)
                    // Con key, cambiar de pestana reutiliza las filas en vez de
                    // recomponerlas y medirlas todas de cero.
                    items(chunked, key = { row -> row.joinToString("|") { it.id } }) { rowProfiles ->
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            rowProfiles.forEach { profile ->
                                val isSelected = profile.id == settingsManager.manualHardwareId
                                Box(Modifier.weight(1f)) { HardwareCardRedesigned(profile, isSelected, primaryColor) { onSelected(profile.id) } }
                            }
                            if (rowProfiles.size < 2) Spacer(Modifier.weight(1f))
                        }
                    }
                }
                Row(modifier = Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.Start) { ActionChip("B", "VOLVER", MaterialTheme.colorScheme.onSurfaceVariant, onClick = onBack) }
            }
        }
    }

    @Composable
    fun HardwareTypeTab(label: String, isSelected: Boolean, primaryColor: Color, onClick: () -> Unit) {
        var isFocused by remember { mutableStateOf(false) }
        val scale by animateFloatAsState(if (isFocused) 1.05f else 1f, label = "tab_scale")
        Surface(modifier = Modifier.height(40.dp).onFocusChanged { isFocused = it.isFocused }.focusable().onKeyEvent { if (it.type == KeyEventType.KeyDown && (it.nativeKeyEvent.keyCode == NativeKeyEvent.KEYCODE_ENTER || it.nativeKeyEvent.keyCode == NativeKeyEvent.KEYCODE_DPAD_CENTER)) { onClick(); true } else false }.clickable { onClick() }.scale(scale), color = if (isSelected || isFocused) primaryColor.copy(alpha = if(isSelected) 1f else 0.5f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f), shape = RoundedCornerShape(20.dp), border = if (!isSelected && !isFocused) BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)) else null) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 24.dp)) { Text(text = label, color = if (isSelected || isFocused) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface, fontSize = 12.sp, fontWeight = FontWeight.Black) }
        }
    }

    @Composable
    fun HardwareCardRedesigned(profile: HardwareControlManagerBeta.HardwareProfile, isSelected: Boolean, primaryColor: Color, onClick: () -> Unit) {
        var isFocused by remember { mutableStateOf(false) }
        val scale by animateFloatAsState(if (isFocused) 1.03f else 1f, label = "card_scale")
        val pulseAlpha by rememberInfiniteTransition(label = "pulse").animateFloat(initialValue = 0.4f, targetValue = 1f, animationSpec = infiniteRepeatable(tween(800, easing = FastOutLinearInEasing), RepeatMode.Reverse), label = "alpha")
        Surface(modifier = Modifier.fillMaxWidth().height(110.dp).onFocusChanged { isFocused = it.isFocused }.focusable().onKeyEvent { if (it.type == KeyEventType.KeyDown && (it.nativeKeyEvent.keyCode == NativeKeyEvent.KEYCODE_ENTER || it.nativeKeyEvent.keyCode == NativeKeyEvent.KEYCODE_DPAD_CENTER || it.nativeKeyEvent.keyCode == NativeKeyEvent.KEYCODE_BUTTON_A)) { onClick(); true } else false }.clickable { onClick() }.graphicsLayer { scaleX = scale; scaleY = scale }, color = if (isFocused) primaryColor.copy(alpha = 0.15f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f), shape = RoundedCornerShape(16.dp), border = BorderStroke(width = if (isFocused) 2.dp else if (isSelected) 1.dp else 0.dp, color = if (isFocused) primaryColor.copy(alpha = pulseAlpha) else if (isSelected) primaryColor.copy(alpha = 0.4f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(56.dp).background(Brush.verticalGradient(listOf(primaryColor.copy(alpha = 0.3f), Color.Transparent)), CircleShape).border(1.dp, primaryColor.copy(alpha = 0.5f), CircleShape), contentAlignment = Alignment.Center) { Icon(painter = painterResource(profile.iconRes), contentDescription = null, tint = if(isFocused) MaterialTheme.colorScheme.onSurface else primaryColor, modifier = Modifier.size(28.dp)) }
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = profile.name.uppercase(), color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Memory, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(10.dp)); Spacer(Modifier.width(4.dp)); Text(text = profile.chipset, color = primaryColor, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                    Spacer(Modifier.height(4.dp))
                    Text(text = profile.emulationCapacity, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (isFocused) { Icon(Icons.Default.PlayArrow, null, tint = primaryColor, modifier = Modifier.size(24.dp)) }
            }
        }
    }

    @Composable
    fun FolderSetupScreen(onNext: () -> Unit, onBack: () -> Unit) {
        val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri != null) {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                settingsManager.romsLocation = uri.toString()
                onNext()
            }
        }

        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                Icon(Icons.Default.Folder, null, tint = NeonBlue, modifier = Modifier.size(80.dp))
                Spacer(Modifier.height(24.dp))
                Text("CONFIGURACIÓN DE ARCHIVOS", color = MaterialTheme.colorScheme.onSurface, fontSize = 24.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.height(16.dp))
                Text("Selecciona la carpeta donde guardas tus ROMs y BIOS para que el sistema pueda indexarlas.", color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                Spacer(Modifier.height(48.dp))
                Button(onClick = { launcher.launch(null) }, modifier = Modifier.fillMaxWidth().height(60.dp), shape = RoundedCornerShape(12.dp)) {
                    Text("SELECCIONAR CARPETA")
                }
                Spacer(Modifier.height(16.dp))
                ActionChip("B", "VOLVER", MaterialTheme.colorScheme.onSurfaceVariant, onClick = onBack)
            }
        }
    }

    @Composable
    fun MediaPermissionsScreen(onNext: () -> Unit, onBack: () -> Unit) {
        var showOverlayDialog by remember { mutableStateOf(false) }
        var showAccessibilityDialog by remember { mutableStateOf(false) }
        var showNotificationDialog by remember { mutableStateOf(false) }

        BackHandler { onBack() }
        val isAtLeastT = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

        val notificationLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission(),
            onResult = { isGranted ->
                if (isGranted) Log.d(tag, "Notificaciones concedidas")
            }
        )

        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            AsyncImage(model = "file:///android_asset/contentimg/banner-neon.webp", contentDescription = null, modifier = Modifier.fillMaxSize().alpha(0.15f), contentScale = ContentScale.Crop)

            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxSize().padding(horizontal = 40.dp, vertical = 32.dp)) {
                Icon(Icons.Default.VerifiedUser, null, tint = NeonBlue, modifier = Modifier.size(70.dp))
                Spacer(Modifier.height(16.dp))
                Text(text = "AUTORIZACIÓN FINAL", color = MaterialTheme.colorScheme.onSurface, fontSize = 28.sp, fontWeight = FontWeight.Black)
                Text(text = "CONCEDE PERMISOS PARA EL OVERLAY Y NOTIFICACIONES", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, textAlign = TextAlign.Center)
                Spacer(Modifier.height(32.dp))

                Button(
                    onClick = { showOverlayDialog = true },
                    modifier = Modifier.width(320.dp).height(55.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.1f)),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
                ) {
                    Text(text = "PERMISO DE DIBUJO (OVERLAY)", color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }

                Spacer(Modifier.height(12.dp))

                if (isAtLeastT) {
                    Button(
                        onClick = { showNotificationDialog = true },
                        modifier = Modifier.width(320.dp).height(55.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.1f)),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
                    ) {
                        Text(text = "PERMISO DE NOTIFICACIONES", color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(12.dp))
                }

                Button(
                    onClick = { showAccessibilityDialog = true },
                    modifier = Modifier.width(320.dp).height(55.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.1f)),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
                ) {
                    Text(text = "SERVICIO DE ACCESIBILIDAD", color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }

            Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.BottomEnd) {
                ActionChip("A", "FINALIZAR", NeonBlue, onClick = onNext)
            }

            Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.BottomStart) {
                ActionChip("B", "VOLVER", MaterialTheme.colorScheme.onSurfaceVariant, onClick = onBack)
            }
        }

        if (showOverlayDialog) {
            AlertDialog(
                onDismissRequest = { showOverlayDialog = false },
                title = { Text(stringResource(R.string.perm_overlay_title)) },
                text = { Text(stringResource(R.string.perm_overlay_msg)) },
                confirmButton = {
                    TextButton(onClick = {
                        showOverlayDialog = false
                        val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, "package:$packageName".toUri())
                        startActivity(intent)
                    }) { Text("CONCEDER") }
                },
                dismissButton = { TextButton(onClick = { showOverlayDialog = false }) { Text("CANCELAR") } }
            )
        }

        if (showNotificationDialog) {
            AlertDialog(
                onDismissRequest = { showNotificationDialog = false },
                title = { Text(stringResource(R.string.perm_notifications_title)) },
                text = { Text(stringResource(R.string.perm_notifications_msg)) },
                confirmButton = {
                    TextButton(onClick = {
                        showNotificationDialog = false
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationLauncher.launch("android.permission.POST_NOTIFICATIONS")
                        }
                    }) { Text("CONCEDER") }
                },
                dismissButton = { TextButton(onClick = { showNotificationDialog = false }) { Text("CANCELAR") } }
            )
        }

        if (showAccessibilityDialog) {
            AlertDialog(
                onDismissRequest = { showAccessibilityDialog = false },
                title = { Text(stringResource(R.string.perm_accessibility_title)) },
                text = { Text(stringResource(R.string.perm_accessibility_msg)) },
                confirmButton = {
                    TextButton(onClick = {
                        showAccessibilityDialog = false
                        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                        startActivity(intent)
                    }) { Text("IR A AJUSTES") }
                },
                dismissButton = { TextButton(onClick = { showAccessibilityDialog = false }) { Text("CANCELAR") } }
            )
        }
    }
}
