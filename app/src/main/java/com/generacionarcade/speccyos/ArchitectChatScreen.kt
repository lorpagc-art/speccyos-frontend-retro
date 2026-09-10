package com.generacionarcade.speccyos

import android.content.Intent
import android.view.KeyEvent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.google.firebase.Timestamp

@Composable
fun ArchitectChatScreen(
    mainViewModel: MainViewModel,
    hardwareViewModel: HardwareViewModel,
    onSafeClose: () -> Unit
) {
    val context = LocalContext.current
    val settingsManager = remember { mainViewModel.settingsManager }
    val lang = settingsManager.appLanguage

    var activeTriviaMode by remember { mutableStateOf<String?>(null) } // "CASUAL" o "ONLINE"
    val triviaManager = remember { TriviaManager(context, lang) }
    var currentQuestion by remember { mutableStateOf<TriviaQuestion?>(null) }
    var isGeneratingTrivia by remember { mutableStateOf(false) }
    var showFact by remember { mutableStateOf(false) }
    var isCorrect by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            // CABECERA
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onSafeClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver", tint = MaterialTheme.colorScheme.onSurface)
                    }
                    Spacer(Modifier.width(8.dp))
                    Box(
                        modifier = Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surface.copy(alpha = 0.1f)).border(1.dp, ThemeManager.primaryColor, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.SportsEsports, contentDescription = null, tint = ThemeManager.primaryColor)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "TRIVIAL ARCADE",
                            color = ThemeManager.primaryColor,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 2.sp
                        )
                        Text(
                            text = "Pon a prueba tus conocimientos retro",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            if (activeTriviaMode == null) {
                // MODO SELECCIÓN
                Column(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("SELECCIONA EL MODO DE JUEGO", color = MaterialTheme.colorScheme.onSurface, fontSize = 24.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                    Spacer(Modifier.height(40.dp))
                    
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        // CASUAL
                        Surface(
                            modifier = Modifier.weight(1f).height(200.dp).clickable {
                                triviaManager.startCasualSession()
                                currentQuestion = triviaManager.getCurrentQuestion()
                                showFact = false
                                activeTriviaMode = "CASUAL"
                            },
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(2.dp, MaterialTheme.colorScheme.outline)
                        ) {
                            Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                Icon(Icons.Default.VideogameAsset, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(48.dp))
                                Spacer(Modifier.height(16.dp))
                                Text("MODO CASUAL", color = MaterialTheme.colorScheme.onSurface, fontSize = 18.sp, fontWeight = FontWeight.Black)
                                Spacer(Modifier.height(8.dp))
                                Text("4 Preguntas. Aleatorio. Sin Ranking. Ideal para entrenar.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, textAlign = TextAlign.Center)
                            }
                        }

                        // ONLINE
                        Surface(
                            modifier = Modifier.weight(1f).height(200.dp).clickable {
                                triviaManager.startOnlineSession()
                                currentQuestion = triviaManager.getCurrentQuestion()
                                showFact = false
                                activeTriviaMode = "ONLINE"
                            },
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(2.dp, ThemeManager.primaryColor)
                        ) {
                            Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                Icon(Icons.Default.Public, null, tint = ThemeManager.primaryColor, modifier = Modifier.size(48.dp))
                                Spacer(Modifier.height(16.dp))
                                Text("COMPETITIVO ONLINE", color = ThemeManager.primaryColor, fontSize = 18.sp, fontWeight = FontWeight.Black)
                                Spacer(Modifier.height(8.dp))
                                Text("10 Preguntas (Dificultad Ascendente). Sube tu puntuación al Ranking Mundial.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, textAlign = TextAlign.Center)
                            }
                        }
                    }
                }
            } else if (isGeneratingTrivia) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = ThemeManager.primaryColor)
                        Spacer(Modifier.height(16.dp))
                        Text("Generando banco de preguntas...", color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                if (currentQuestion != null && !showFact) {
                    TriviaQuestionCard(
                        question = currentQuestion!!,
                        progress = triviaManager.getProgressText(),
                        primaryColor = ThemeManager.primaryColor,
                        onOptionSelected = { option ->
                            isCorrect = triviaManager.checkAnswer(option)
                            showFact = true
                        }
                    )
                } else if (currentQuestion != null && showFact) {
                    TriviaFactCard(
                        isCorrect = isCorrect,
                        question = currentQuestion!!,
                        primaryColor = ThemeManager.primaryColor,
                        onNext = {
                            triviaManager.nextQuestion()
                            if (triviaManager.isFinished()) {
                                currentQuestion = null
                            } else {
                                currentQuestion = triviaManager.getCurrentQuestion()
                                showFact = false
                            }
                        }
                    )
                } else if (triviaManager.isFinished()) {
                    TriviaResultCard(
                        rank = triviaManager.getFinalRank(lang),
                        scoreStr = triviaManager.getScoreString(),
                        primaryColor = ThemeManager.primaryColor,
                        lang = lang,
                        mode = activeTriviaMode ?: "CASUAL",
                        onShare = {
                            val rank = triviaManager.getFinalRank(lang)
                            val imgUri = ImageProcessUtils.generateTriviaShareCard(
                                context,
                                rank,
                                triviaManager.getScoreString(),
                                ThemeManager.primaryColor.toArgb()
                            )

                            val shareIntent = Intent().apply {
                                action = Intent.ACTION_SEND
                                putExtra(Intent.EXTRA_TEXT, "¡He conseguido el rango '$rank' en el Trivial de Generación Arcade! 🕹️ ¿Te atreves a superarme?")
                                if (imgUri != null) {
                                    putExtra(Intent.EXTRA_STREAM, imgUri)
                                    type = "image/png"
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                } else {
                                    type = "text/plain"
                                }
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Desafía a tus amigos"))
                        },
                        onRestart = {
                            activeTriviaMode = null
                        },
                        mainViewModel = mainViewModel
                    )
                }
            }
        }
    }
}

@Composable
fun TriviaQuestionCard(
    question: TriviaQuestion,
    progress: String,
    primaryColor: Color,
    onOptionSelected: (String) -> Unit
) {
    val focusRequester = remember { FocusRequester() }

    // Mapeo seguro de las 4 primeras opciones a botones de mando
    val options = question.options.take(4)
    val buttonIcons = listOf("A", "B", "X", "Y")
    val buttonColors = listOf(Color(0xFF00FF00), Color(0xFFFF0000), Color(0xFF0088FF), Color(0xFFFFFF00)) // Colores Xbox style
    val buttonKeyCodes = listOf(KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_BUTTON_X, KeyEvent.KEYCODE_BUTTON_Y)

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(4.dp)
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown) {
                    val code = keyEvent.nativeKeyEvent.keyCode
                    val index = buttonKeyCodes.indexOf(code)
                    if (index != -1 && index < options.size) {
                        onOptionSelected(options[index])
                        true
                    } else false
                } else false
            },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = progress, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))

        Surface(
            modifier = Modifier.fillMaxWidth().weight(1f),
            color = MaterialTheme.colorScheme.background,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(2.dp, primaryColor.copy(alpha = 0.5f))
        ) {
            Column(
                modifier = Modifier.fillMaxSize().padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // SECCIÓN PREGUNTA (Mitad superior maximizada)
                Column(
                    modifier = Modifier.weight(1.5f).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    if (question.image != null) {
                        AsyncImage(
                            model = question.image,
                            contentDescription = null,
                            modifier = Modifier.height(100.dp).padding(bottom = 8.dp),
                            contentScale = ContentScale.Fit
                        )
                    }

                    Text(
                        text = question.question,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 18.sp, // Tamaño reducido para que quepa siempre
                        fontWeight = FontWeight.Black,
                        textAlign = TextAlign.Center,
                        lineHeight = 24.sp
                    )
                }

                Spacer(Modifier.height(12.dp))

                // SECCIÓN RESPUESTAS (Mitad inferior compacta)
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Fila 1 (A y B)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (options.size > 0) MillionaireOptionButton(Modifier.weight(1f), options[0], buttonIcons[0], buttonColors[0]) { onOptionSelected(options[0]) }
                        if (options.size > 1) MillionaireOptionButton(Modifier.weight(1f), options[1], buttonIcons[1], buttonColors[1]) { onOptionSelected(options[1]) }
                    }
                    // Fila 2 (X e Y)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (options.size > 2) MillionaireOptionButton(Modifier.weight(1f), options[2], buttonIcons[2], buttonColors[2]) { onOptionSelected(options[2]) }
                        if (options.size > 3) MillionaireOptionButton(Modifier.weight(1f), options[3], buttonIcons[3], buttonColors[3]) { onOptionSelected(options[3]) }
                    }
                }
            }
        }
    }
}

@Composable
fun MillionaireOptionButton(modifier: Modifier, text: String, physicalBtn: String, btnColor: Color, onClick: () -> Unit) {
    Surface(
        modifier = modifier.height(65.dp).clickable { onClick() },
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(24.dp).background(btnColor.copy(alpha = 0.2f), CircleShape).border(2.dp, btnColor, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(physicalBtn, color = btnColor, fontWeight = FontWeight.Black, fontSize = 12.sp)
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = text,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 3,
                lineHeight = 14.sp
            )
        }
    }
}

@Composable
fun TriviaFactCard(
    isCorrect: Boolean,
    question: TriviaQuestion,
    primaryColor: Color,
    onNext: () -> Unit
) {
    val color = if (isCorrect) SpeccyPalette.ok else MaterialTheme.colorScheme.error
    val title = if (isCorrect) "¡CORRECTO!" else "INCORRECTO"
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown && (keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_BUTTON_A || keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_ENTER || keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_CENTER)) {
                    onNext()
                    true
                } else false
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(2.dp, color.copy(alpha = 0.5f))
        ) {
            Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = if (isCorrect) Icons.Default.CheckCircle else Icons.Default.Cancel,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(48.dp).padding(bottom = 8.dp)
                )

                Text(text = title, color = color, fontSize = 20.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.height(8.dp))

                if (!isCorrect) {
                    Text(text = "La respuesta correcta era:", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    Text(text = question.answer, color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.padding(bottom = 8.dp))
                }

                if (question.funFact.isNotEmpty()) {
                    Surface(color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.5f), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Lightbulb, null, tint = primaryColor, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("SABÍAS QUE...", color = primaryColor, fontSize = 12.sp, fontWeight = FontWeight.Black)
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(text = question.funFact, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, lineHeight = 16.sp)
                        }
                    }
                }

                Button(
                    onClick = onNext,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp).height(50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = primaryColor),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(text = "PULSA (A) PARA CONTINUAR", color = MaterialTheme.colorScheme.onPrimary, fontSize = 12.sp, fontWeight = FontWeight.Black)
                }
            }
        }
    }
}

@Composable
fun TriviaResultCard(
    rank: String,
    scoreStr: String,
    primaryColor: Color,
    lang: String,
    mode: String,
    onShare: () -> Unit,
    onRestart: () -> Unit,
    mainViewModel: MainViewModel
) {
    val context = LocalContext.current
    var isUploading by remember { mutableStateOf(false) }
    var uploadSuccess by remember { mutableStateOf(false) }

    val userStatus = remember { UserStatusManager(context) }

    LaunchedEffect(Unit) {
        if (mode == "ONLINE" && userStatus.userEmail.value.isNotEmpty()) {
            isUploading = true
            try {
                // Antes se usaba el CORREO como id de documento, lo que obligaba a
                // tener las reglas de Firestore abiertas. Ahora, UID de Auth.
                val userId = SpeccyIdentity.ensureSignedIn() ?: return@LaunchedEffect
                val userName = userStatus.userName.value
                val scoreNum = scoreStr.substringBefore("/").trim().toIntOrNull() ?: 0

                val progress = GameProgress(
                    gameId = "trivia_rank",
                    gameName = userName,
                    lastPlayed = Timestamp.now(),
                    score = scoreNum,
                    rankTitle = rank
                )
                FirebaseSyncManager(context).syncGameProgress(userId, progress)
                uploadSuccess = true
            } catch (e: Exception) {
                // Fallo silencioso si no hay red
            } finally {
                isUploading = false
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth().padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(2.dp, primaryColor)
        ) {
            Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    if (mode == "ONLINE") Icons.Default.Public else Icons.Default.VideogameAsset,
                    contentDescription = null,
                    tint = if (mode == "ONLINE") ThemeManager.primaryColor else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(48.dp).padding(bottom = 8.dp)
                )

                Text(text = "EVALUACIÓN COMPLETADA", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                Text(text = scoreStr, color = MaterialTheme.colorScheme.onSurface, fontSize = 28.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(vertical = 4.dp))

                Surface(color = primaryColor.copy(alpha = 0.2f), shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, primaryColor)) {
                    Text(text = rank.uppercase(), color = primaryColor, fontSize = 14.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
                }

                Spacer(Modifier.height(16.dp))

                if (mode == "ONLINE") {
                    Surface(
                        color = MaterialTheme.colorScheme.onPrimary,
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, if(uploadSuccess) SpeccyPalette.ok else MaterialTheme.colorScheme.outline),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                    ) {
                        Row(modifier = Modifier.padding(8.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                            if (isUploading) {
                                CircularProgressIndicator(modifier = Modifier.size(14.dp), color = primaryColor, strokeWidth = 2.dp)
                                Spacer(Modifier.width(6.dp))
                                Text("SUBIENDO DATOS AL IMPERIO...", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            } else if (uploadSuccess) {
                                Icon(Icons.Default.Check, null, tint = SpeccyPalette.ok, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("¡PUNTUACIÓN REGISTRADA EN EL RANKING OFICIAL!", color = SpeccyPalette.ok, fontSize = 12.sp, fontWeight = FontWeight.Black)
                            } else {
                                Icon(Icons.Default.CloudOff, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("FALLO DE CONEXIÓN CON EL RANKING", color = MaterialTheme.colorScheme.error, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onRestart,
                        modifier = Modifier.weight(1f).height(45.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(text = "VOLVER AL MENÚ", color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = onShare,
                        modifier = Modifier.weight(1f).height(45.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = primaryColor),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Share, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(text = "COMPARTIR", color = MaterialTheme.colorScheme.onPrimary, fontSize = 12.sp, fontWeight = FontWeight.Black)
                    }
                }
            }
        }
    }
}
