/*
 * Speccy OS — frontend retro para Android
 * Copyright (c) 2026 LV-Webstudio · lv-webstudio.com
 * Desarrollado por Speccy81 (LORPAGC) · administracion@lv-webstudio.com
 * Todos los derechos reservados.
 */
package com.generacionarcade.speccyos

import android.content.Context
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.remoteconfig.remoteConfig
import com.google.firebase.remoteconfig.remoteConfigSettings
import com.google.firebase.vertexai.vertexAI
import com.google.firebase.vertexai.type.generationConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * 🧠 ARQUITECTO IA V6.0 - Edición Global Gratuita
 * Se han eliminado las restricciones de cuotas y energía. 
 */
object AiContextCache {
    private var _masterIndex: String? = null
    private val manualCache = mutableMapOf<String, String>()

    fun getMasterIndex(context: android.content.Context): String {
        return _masterIndex ?: try {
            context.assets.open("metadata/master_retroarch_systems_index.json")
                .bufferedReader().use { it.readText() }
                .also { _masterIndex = it }
        } catch (e: kotlinx.coroutines.CancellationException) {
            // Una cancelacion no es un error: sin relanzarla, cerrar la
            // pantalla se registraba como fallo y la corrutina seguia
            // trabajando un rato mas, gastando bateria y red.
            throw e
        } catch (e: Exception) { "{}" }
    }

    fun getManual(context: android.content.Context, langCode: String): String {
        return manualCache.getOrPut(langCode) {
            val filename = if (langCode == "es") "manual.md" else "manual_$langCode.md"
            try {
                context.assets.open(filename).bufferedReader().use { it.readText() }
            } catch (e: kotlinx.coroutines.CancellationException) {
                // Una cancelacion no es un error: sin relanzarla, cerrar la
                // pantalla se registraba como fallo y la corrutina seguia
                // trabajando un rato mas, gastando bateria y red.
                throw e
            } catch (e: Exception) {
                try {
                    context.assets.open("manual_en.md").bufferedReader().use { it.readText() }
                } catch (e2: Exception) { "Manual not available." }
            }
        }
    }

    fun clear() { _masterIndex = null; manualCache.clear() }
}


class AiManager(private val context: Context) {

    private val TAG = "AiManager"
    
    private val generativeModel = Firebase.vertexAI.generativeModel(
        modelName = "gemini-2.5-flash-lite",
        generationConfig = generationConfig {
            temperature = 0.7f
            topK = 40
            topP = 0.95f
            maxOutputTokens = 2048
        }
    )

    init {
        setupRemoteConfig()
    }

    private fun setupRemoteConfig() {
        val remoteConfig = Firebase.remoteConfig
        val configSettings = remoteConfigSettings {
            minimumFetchIntervalInSeconds = 3600 
        }
        remoteConfig.setConfigSettingsAsync(configSettings)
        remoteConfig.setDefaultsAsync(mapOf(
            // Topes diarios por dispositivo. Se pueden subir o bajar desde la consola
            // de Firebase sin publicar una version nueva. Poner 0 o negativo usa el
            // valor de respaldo del codigo, nunca "sin limite".
            // Interruptor general de la IA: se apaga desde la consola de Firebase
            // sin publicar nada. Es lo que permitio cortar en caliente el abuso de
            // Vertex AI de septiembre de 2026. Con la bandera a false, ninguna
            // funcion de IA llama al modelo.
            "ai_enabled" to true,
            "ai_chat_daily_limit" to 20L,
            "ai_ocr_daily_limit" to 30L,
            "architect_system_instruction" to """
                Eres El Arquitecto, la IA central de Speccy OS E5 Ultra. Tu tono es técnico, imperial y proactivo. Evita mencionar que eres una inteligencia artificial, un modelo de lenguaje o que fuiste creado por Google. Preséntate siempre como una creación directa de Speccy Generacionarcade.com.
            """.trimIndent()
        ))
    }

    /**
     * Tope diario para una funcion de IA. La app es gratuita: esto no es un muro de
     * pago, es proteccion del coste de Vertex AI, que paga el desarrollador en cada
     * llamada. El valor real se sirve desde Remote Config.
     */
    private fun dailyLimit(key: String, fallback: Int): Int =
        Firebase.remoteConfig.getLong(key).toInt().takeIf { it > 0 } ?: fallback

    /**
     * ¿Estan permitidas las funciones de IA? Si todavia no ha llegado ningun
     * valor de Firebase (VALUE_SOURCE_STATIC) se responde true: el valor
     * compilado por defecto ya es true y no tiene sentido dejar la app sin IA
     * por no haber podido consultar.
     */
    fun aiEnabled(): Boolean {
        val v = Firebase.remoteConfig.getValue("ai_enabled")
        return if (v.source == com.google.firebase.remoteconfig.FirebaseRemoteConfig.VALUE_SOURCE_STATIC) true
        else v.asBoolean()
    }

    /** Texto que ve el usuario cuando la IA esta apagada. */
    fun aiDisabledMessage(): String =
        "Las funciones de inteligencia artificial están desactivadas en esta versión."

    private fun limitReachedMessage(lang: String, isOcr: Boolean): String {
        val es = if (isOcr)
            "Has alcanzado el limite de traducciones de hoy. El traductor vuelve manana. " +
            "Speccy OS es gratis y sin anuncios: este tope solo existe para que el " +
            "servicio siga siendo sostenible."
        else
            "Has alcanzado el limite de consultas al Arquitecto por hoy. Vuelve manana. " +
            "Speccy OS es gratis y sin anuncios: este tope solo existe para que el " +
            "servicio siga siendo sostenible."
        val en = if (isOcr)
            "You have reached today's translation limit. The translator is back tomorrow. " +
            "Speccy OS is free and ad-free: this cap only exists to keep the service sustainable."
        else
            "You have reached today's limit of Architect queries. Come back tomorrow. " +
            "Speccy OS is free and ad-free: this cap only exists to keep the service sustainable."
        return if (lang.startsWith("es")) es else en
    }

    /** SettingsManager propio para los contadores que no llegan por parametro. */
    private val settings by lazy { SettingsManager(context) }

    suspend fun translateOcrText(
        ocrText: String,
        platformId: String,
        gameTitle: String,
        targetLang: String = "es"
    ): String = withContext(Dispatchers.IO) {
        if (!aiEnabled()) return@withContext aiDisabledMessage()
        val prompt = """
            Actúa como el Traductor Imperial de Speccy OS. He capturado texto de un videojuego mediante OCR.
            JUEGO: $gameTitle
            PLATAFORMA: $platformId
            IDIOMA DESTINO: $targetLang
            
            TEXTO CAPTURADO:
            "$ocrText"
            
            TAREA:
            1. Limpia el ruido del OCR (caracteres extraños).
            2. Traduce el texto al idioma destino de forma natural, manteniendo el contexto del juego (lore, diálogos, menús).
            3. Si el texto es un menú, mantén el formato de lista.
            4. Si el texto es un diálogo, hazlo sonar inmersivo.
            5. Responde ÚNICAMENTE con la traducción limpia. Sin explicaciones ni saludos.
        """.trimIndent()

        // Tope diario: cada traduccion es una llamada nueva a Vertex AI y la cache
        // no puede reutilizarla, porque cada captura de pantalla es distinta.
        // Espejo fuera de los datos de la app: borrar datos ya no regala cuota.
        SpeccyAiQuota.sincronizar(context.applicationContext, settings)
        val usedToday = settings.ocrTranslationsToday
        if (usedToday >= dailyLimit("ai_ocr_daily_limit", 30)) {
            return@withContext limitReachedMessage(targetLang, isOcr = true)
        }

        return@withContext try {
            val response = generativeModel.generateContent(prompt)
            settings.ocrTranslationsToday = usedToday + 1
            SpeccyAiQuota.anotar(context.applicationContext, settings)
            response.text ?: "No se pudo traducir el fragmento."
        } catch (e: kotlinx.coroutines.CancellationException) {
            // Una cancelacion no es un error: sin relanzarla, cerrar la
            // pantalla se registraba como fallo y la corrutina seguia
            // trabajando un rato mas, gastando bateria y red.
            throw e
        } catch (e: Exception) {
            "Error de conexión neural en la traducción."
        }
    }

    suspend fun generateResponse(
        prompt: String, 
        librarySummary: String = "", 
        settingsManager: SettingsManager, 
        aiCacheDao: AiCacheDao,
        // isProUser eliminado: no se leia en ninguna linea del cuerpo y hacia creer
        // que habia una cuota distinta para usuarios de pago. La app es gratuita y
        // el limite es el mismo para todos.
        langCode: String = "es"
    ): String = withContext(Dispatchers.IO) {
        
        // SHA-256 y no String.hashCode(): 32 bits con colisiones conocidas como
        // clave de cache significa servirle al usuario la respuesta de OTRA
        // pregunta, sin error visible, y ademas contar como acierto de cache algo
        // que no lo es.
        val queryHash = HashUtility.sha256(prompt.lowercase().trim())
        val cachedResponse = aiCacheDao.getCachedResponse(queryHash)
        // Una respuesta servida desde cache no cuesta nada, asi que no gasta cuota.
        if (cachedResponse != null) return@withContext cachedResponse.responseText

        // Tope diario del chat, comprobado solo cuando hay que llamar al modelo.
        // Se sincroniza antes con el espejo de almacenamiento compartido, para
        // que borrar los datos de la app no reinicie la cuota.
        if (!aiEnabled()) return@withContext aiDisabledMessage()
        SpeccyAiQuota.sincronizar(context.applicationContext, settingsManager)
        if (settingsManager.aiQueriesToday >= dailyLimit("ai_chat_daily_limit", 20)) {
            return@withContext limitReachedMessage(langCode, isOcr = false)
        }

        val masterIndex = AiContextCache.getMasterIndex(context)
        val manualContent = AiContextCache.getManual(context, langCode)
        val languageName = Translator.getLanguageName(langCode)
        
        var systemInstruction = "Eres El Arquitecto, la IA central de Speccy OS. Experto en hardware Android y emulación avanzada. Creado por Speccy Generacionarcade.com."
        try {
            Firebase.remoteConfig.fetchAndActivate().await()
            systemInstruction = Firebase.remoteConfig.getString("architect_system_instruction")
        } catch (e: kotlinx.coroutines.CancellationException) {
            // Una cancelacion no es un error: sin relanzarla, cerrar la
            // pantalla se registraba como fallo y la corrutina seguia
            // trabajando un rato mas, gastando bateria y red.
            throw e
        } catch (e: Exception) { Log.e(TAG, "No se pudo cargar config remota, usando local") }
        
        val contextPrompt = """
            $systemInstruction
            
            IMPORTANTE: NUNCA menciones que eres una Inteligencia Artificial, un modelo de lenguaje, o que has sido entrenado o creado por Google, OpenAI o cualquier otra empresa. Si te preguntan quién te ha creado, responde SIEMPRE que has sido creado y programado por "Speccy Generacionarcade.com". Actúa como el asistente y arquitecto oficial del sistema.
            
            IDIOMA: $languageName ($langCode).
            
            CONOCIMIENTO MAESTRO (ENCICLOPEDIA DE EMULACIÓN):
            Utiliza estos datos para recomendar cores y perfiles:
            $masterIndex
            
            MANUAL DE USUARIO ACTUALIZADO:
            El usuario ahora cuenta con:
            1. NEBULA TWEAKER: Menú avanzado de Overclock, control de ventiladores, clusters de CPU y GPU.
            2. ZERO-SCRAPING: Enlace instantáneo de vídeos y carátulas locales (carpeta 'media').
            3. FANART CUSTOMIZABLE: Cyberpunk o Personajes Icónicos.
            4. ACCESO TOTAL: Todas las funciones están desbloqueadas de forma gratuita.
            
            CONTENIDO OFICIAL DEL MANUAL EN DISCO:
            $manualContent
            
            BIBLIOTECA DEL USUARIO:
            $librarySummary
            
            CONOCIMIENTO AVANZADO DE HARDWARE:
            - UNISOC (T618, T620, T820): Sistemas hasta GC/PS2 ligero.
            - MEDIATEK (Helio G99, Dimensity 900/1100): Potencia excelente para PS2/Wii.
            - SNAPDRAGON (865, 8 Gen 2, 8 Gen 3, G3x): La élite. Emulan Switch y PC.
            - ROCKCHIP (RK3566, RK3326): Clásicos, PS1 y N64.
            
            GUÍA DE CORES FALTANTES:
            Indica al usuario: RetroArch -> Actualizador en línea -> Descargador de núcleos.
            
            INSTRUCCIÓN DE COMANDOS:
            [CMD:SET_PROFILE:platformId:PROFILE_NAME]
            [CMD:SET_CORE:platformId:core_name]
            
            REGLA DE ORO PARA LANZAR JUEGOS:
            Si el usuario te pide jugar algo, toma la decisión tú solo. No preguntes.
            Respuesta breve y directa.
            INCLUYE SIEMPRE: [CMD:LAUNCH_GAME:Nombre Del Juego]
        """.trimIndent()

        val fullPrompt = "$contextPrompt\n\nPREGUNTA USUARIO: $prompt"

        return@withContext try {
            val response = generativeModel.generateContent(fullPrompt)
            val responseText = response.text ?: generateOfflineFallback(prompt, masterIndex, langCode, "Empty response")
            
            aiCacheDao.insertCache(AiCache(queryHash = queryHash, responseText = responseText))
            settingsManager.aiQueriesToday = settingsManager.aiQueriesToday + 1
            SpeccyAiQuota.anotar(context.applicationContext, settingsManager)
            responseText
        } catch (e: kotlinx.coroutines.CancellationException) {
            // Una cancelacion no es un error: sin relanzarla, cerrar la
            // pantalla se registraba como fallo y la corrutina seguia
            // trabajando un rato mas, gastando bateria y red.
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "ERROR CLOUD VERTEX AI: ${e.message}")
            generateOfflineFallback(prompt, masterIndex, langCode, e.message ?: "Protocol Error")
        }
    }

    private fun generateOfflineFallback(prompt: String, jsonContent: String, langCode: String, errorDetail: String): String {
        return try {
            val root = JSONObject(jsonContent)
            val systems = root.optJSONArray("systems") ?: return "EL ARQUITECTO: Núcleo fuera de línea."
            val query = prompt.lowercase()
            var response = "MODO LOCAL (Sin conexión): "
            var found = false
            for (i in 0 until systems.length()) {
                val sys = systems.getJSONObject(i)
                val name = sys.getString("name")
                if (query.contains(name.lowercase()) || query.contains(sys.getString("id").lowercase())) {
                    val core = sys.optJSONObject("emulation")?.optJSONObject("retroarch")?.optString("core") ?: "N/A"
                    val tuning = sys.optJSONObject("tuning")?.optJSONObject("unisoc_t620")?.optString("mode") ?: "BALANCED"
                    response += "\n\nSISTEMA: $name\nCORE RECOMENDADO: $core\nPERFIL: $tuning"
                    found = true; break
                }
            }
            if (!found) response += "\nNo reconozco el sistema. Consulta el manual o conecta a internet."
            response
        } catch (e: kotlinx.coroutines.CancellationException) {
            // Una cancelacion no es un error: sin relanzarla, cerrar la
            // pantalla se registraba como fallo y la corrutina seguia
            // trabajando un rato mas, gastando bateria y red.
            throw e
        } catch (e: Exception) { "EL ARQUITECTO: Error en protocolos locales." }
    }

    suspend fun generateTriviaQuestions(langCode: String = "es"): String? = withContext(Dispatchers.IO) {
        if (!aiEnabled()) return@withContext null
        // El trivial tambien es una llamada al modelo: comparte el tope del chat.
        SpeccyAiQuota.sincronizar(context.applicationContext, settings)
        val usedToday = settings.aiQueriesToday
        if (usedToday >= dailyLimit("ai_chat_daily_limit", 20)) return@withContext null
        try {
            val prompt = "Generate 5 retro gaming trivia questions in ${Translator.getLanguageName(langCode)} in strict JSON format."
            val response = generativeModel.generateContent(prompt)
            settings.aiQueriesToday = usedToday + 1
            SpeccyAiQuota.anotar(context.applicationContext, settings)
            response.text?.replace("```json", "")?.replace("```", "")?.trim()
        } catch (e: kotlinx.coroutines.CancellationException) {
            // Una cancelacion no es un error: sin relanzarla, cerrar la
            // pantalla se registraba como fallo y la corrutina seguia
            // trabajando un rato mas, gastando bateria y red.
            throw e
        } catch (e: Exception) { null }
    }
}
