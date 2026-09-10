package com.generacionarcade.speccyos

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import java.security.MessageDigest
import kotlin.random.Random
import kotlin.system.measureTimeMillis

class BenchmarkManager(private val context: Context) {

    data class BenchmarkResult(
        val cpuScore: Int = 0,
        val gpuScore: Int = 0,
        val ramScore: Int = 0,
        val totalScore: Int = 0,
        val timestamp: Long = System.currentTimeMillis(),
        val deviceModel: String = android.os.Build.MODEL.uppercase(),
        val supportedSystems: List<String> = emptyList(),
        val overclockProfile: String = "BALANCED",
        val isOverclocked: Boolean = false
    )
    
    data class RankingEntry(
        val position: Int,
        val deviceModel: String,
        val userName: String,
        val score: Int,
        val overclockProfile: String,
        val isOverclocked: Boolean
    )

    private val _isRuning = MutableStateFlow(false)
    val isRunning = _isRuning.asStateFlow()

    private val _progress = MutableStateFlow(0f)
    val progress = _progress.asStateFlow()

    private val _currentTest = MutableStateFlow("")
    val currentTest = _currentTest.asStateFlow()

    private val db = FirebaseFirestore.getInstance()

    suspend fun runFullBenchmark(): BenchmarkResult = withContext(Dispatchers.Default) {
        _isRuning.value = true
        _progress.value = 0f
        
        val settingsManager = SettingsManager(context)
        val currentProfile = settingsManager.manualProfile
        val isOc = currentProfile == "PERFORMANCE" || currentProfile == "EXTREME"
        
        _currentTest.value = "TESTING CPU MULTI-CORE (ALU & HASHING)..."
        val cpuScore = runCpuTest()
        _progress.value = 0.33f
        
        _currentTest.value = "TESTING GPU/VIDEO RENDER (3D MATH & BLUR)..."
        val gpuScore = runGpuVideoTest()
        _progress.value = 0.66f
        
        _currentTest.value = "TESTING RAM BANDWIDTH (I/O & CACHE)..."
        val ramScore = runRamTest()
        _progress.value = 1.0f
        
        val total = (cpuScore + gpuScore + ramScore) / 3
        val result = BenchmarkResult(
            cpuScore = cpuScore,
            gpuScore = gpuScore,
            ramScore = ramScore,
            totalScore = total,
            supportedSystems = calculateSupportedSystems(total),
            overclockProfile = currentProfile,
            isOverclocked = isOc
        )
        
        _isRuning.value = false
        _currentTest.value = "BENCHMARK COMPLETED"
        result
    }

    /**
     * Prueba de CPU: Generación de Hashes SHA-256 de forma masiva en multi-hilo
     * Simula la carga de decodificación de instrucciones de los emuladores.
     */
    private suspend fun runCpuTest(): Int = coroutineScope {
        val time = measureTimeMillis {
            val cores = Runtime.getRuntime().availableProcessors()
            val jobs = List(cores) {
                launch(Dispatchers.Default) {
                    val md = MessageDigest.getInstance("SHA-256")
                    var dummy = "SpeccyOS_Benchmark"
                    for (i in 0..10_000) {
                        val bytes = md.digest((dummy + i).toByteArray())
                        dummy = bytes.joinToString("") { "%02x".format(it) }.substring(0, 10)
                    }
                }
            }
            jobs.joinAll()
        }
        val score = (500000 / time.coerceAtLeast(1)).toInt()
        score.coerceIn(100, 15000)
    }

    /**
     * Prueba de GPU y Video: Generación de una textura (Bitmap) en alta resolución
     * y aplicación de convolución (Desenfoque Gaussiano / Edge Detection matemático).
     * Simula renderizado 3D de software y post-procesado de video y shaders.
     */
    private suspend fun runGpuVideoTest(): Int = withContext(Dispatchers.Default) {
        val time = measureTimeMillis {
            val width = 1024
            val height = 1024
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val pixels = IntArray(width * height)
            
            // Llenar con ruido
            for (i in pixels.indices) {
                pixels[i] = Color.rgb(Random.nextInt(256), Random.nextInt(256), Random.nextInt(256))
            }
            bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
            
            // Aplicar un filtro de Blur matemático (convolución de matriz) simplificado en CPU/GPU bridge
            val resultPixels = IntArray(width * height)
            val radius = 3
            for (y in radius until height - radius) {
                for (x in radius until width - radius) {
                    var r = 0; var g = 0; var b = 0
                    var count = 0
                    for (dy in -radius..radius) {
                        for (dx in -radius..radius) {
                            val pixel = pixels[(y + dy) * width + (x + dx)]
                            r += Color.red(pixel)
                            g += Color.green(pixel)
                            b += Color.blue(pixel)
                            count++
                        }
                    }
                    resultPixels[y * width + x] = Color.rgb(r / count, g / count, b / count)
                }
            }
        }
        val score = (800000 / time.coerceAtLeast(1)).toInt()
        score.coerceIn(100, 15000)
    }

    /**
     * Prueba de Memoria RAM: Asignación masiva, escritura y lectura desordenada
     * para reventar la caché L2/L3 y medir el ancho de banda puro.
     */
    private suspend fun runRamTest(): Int = withContext(Dispatchers.Default) {
        val size = 5_000_000
        val time = measureTimeMillis {
            val array1 = IntArray(size) { it }
            val array2 = IntArray(size) { 0 }
            
            // Escritura lineal
            for (i in 0 until size) {
                array2[i] = array1[size - 1 - i]
            }
            
            // Lectura/Escritura Pseudo-aleatoria (destruir cache L2)
            for (i in 0 until size step 16) {
                val temp = array1[i]
                array1[i] = array2[i]
                array2[i] = temp
            }
        }
        val score = (200000 / time.coerceAtLeast(1)).toInt()
        score.coerceIn(100, 15000)
    }

    private fun calculateSupportedSystems(score: Int): List<String> {
        val systems = mutableListOf<String>()
        if (score > 100) systems.addAll(listOf("NES", "GB", "GBC", "SMS", "ATARI"))
        if (score > 500) systems.addAll(listOf("SNES", "GENESIS", "GBA", "PC-ENGINE"))
        if (score > 1500) systems.addAll(listOf("PSX", "N64", "MAME", "CPS1/2"))
        if (score > 3000) systems.addAll(listOf("PSP", "DREAMCAST", "NDS", "CPS3"))
        if (score > 5000) systems.addAll(listOf("GAMECUBE", "PS2", "WII", "3DS"))
        if (score > 7000) systems.addAll(listOf("SWITCH (Beta)", "PS3", "VITA"))
        if (score > 10000) systems.addAll(listOf("PC GAMES (WINLATOR)", "SWITCH (Heavy)"))
        return systems
    }
    
    /**
     * Sube el resultado a Firebase Firestore y devuelve la lista de ranking actualizada.
     */
    suspend fun uploadAndGetRanking(result: BenchmarkResult, userName: String): List<RankingEntry>? = withContext(Dispatchers.IO) {
        try {
            val finalUserName = if (userName.isBlank()) "Anonymous" else userName
            
            // 1. Guardar en Firestore
            val uid = SpeccyIdentity.ensureSignedIn()
            if (uid == null) {
                Log.w("Benchmark", "Sin sesión: el resultado no se sube al ranking.")
                return@withContext null
            }
            val docData = hashMapOf(
                // Las reglas exigen que uid == request.auth.uid: sin esto la
                // escritura se rechaza y nadie puede falsificar entradas ajenas.
                "uid" to uid,
                "device_model" to result.deviceModel.take(64),
                "user_name" to finalUserName.take(32),
                "cpu_score" to result.cpuScore,
                "gpu_score" to result.gpuScore,
                "ram_score" to result.ramScore,
                "total_score" to result.totalScore,
                "timestamp" to result.timestamp,
                "overclock_profile" to result.overclockProfile,
                "is_overclocked" to result.isOverclocked
            )
            
            db.collection("speccy_benchmarks").add(docData).await()
            Log.d("Benchmark", "✅ Resultado subido a Firestore correctamente.")

            // 2. Obtener el Top 100 de Firestore
            val snapshot = db.collection("speccy_benchmarks")
                .orderBy("total_score", Query.Direction.DESCENDING)
                .limit(100)
                .get()
                .await()

            val rankingList = mutableListOf<RankingEntry>()
            var position = 1
            for (doc in snapshot.documents) {
                rankingList.add(
                    RankingEntry(
                        position = position++,
                        deviceModel = doc.getString("device_model") ?: "Unknown",
                        userName = doc.getString("user_name") ?: "Anonymous",
                        score = doc.getLong("total_score")?.toInt() ?: 0,
                        overclockProfile = doc.getString("overclock_profile") ?: "BALANCED",
                        isOverclocked = doc.getBoolean("is_overclocked") ?: false
                    )
                )
            }
            
            // Si por algún motivo Firestore está vacío, generamos mock para rellenar
            if (rankingList.isEmpty()) {
                return@withContext generateMockRanking(result, finalUserName)
            }
            
            rankingList
        } catch (e: Exception) {
            Log.e("Benchmark", "❌ Fallo al subir a Firestore: ${e.message}")
            // Fallback a Mock si no hay internet o error en base de datos
            generateMockRanking(result, userName)
        }
    }
    
    private fun generateMockRanking(result: BenchmarkResult, userName: String): List<RankingEntry> {
        val list = mutableListOf(
            RankingEntry(1, "SAMSUNG S24 ULTRA", "Lorpagc", 11450, "EXTREME", true),
            RankingEntry(2, "AYN ODIN 2 MAX", "Retrogamer_88", 10900, "PERFORMANCE", true),
            RankingEntry(3, "POCO F5 PRO", "Alex_Speed", 8200, "BALANCED", false),
            RankingEntry(4, "RETROID POCKET 4 PRO", "Taki_Udon", 7100, "EXTREME", true),
            RankingEntry(5, "ANBERNIC RG556", "Retro_Corp", 5400, "BALANCED", false),
            RankingEntry(6, "GAMEMT E5 ULTRA", "Architect", 4800, "PERFORMANCE", true),
            RankingEntry(7, "GAMEMT E5 PLUS", "Tester_01", 3100, "BALANCED", false),
            RankingEntry(8, "ANBERNIC RG35XX H", "Pixel_Lover", 1200, "BALANCED", false)
        )
        
        list.add(RankingEntry(0, result.deviceModel, userName.ifEmpty { "Tú" }, result.totalScore, result.overclockProfile, result.isOverclocked))
        val sortedList = list.sortedByDescending { it.score }.take(100)
        return sortedList.mapIndexed { index, entry -> entry.copy(position = index + 1) }
    }
}