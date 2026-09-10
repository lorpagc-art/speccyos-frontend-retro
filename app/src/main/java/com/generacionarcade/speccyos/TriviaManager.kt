package com.generacionarcade.speccyos

import android.content.Context
import android.util.Log
import org.json.JSONArray
import java.io.InputStreamReader
import androidx.core.content.edit

data class TriviaQuestion(
    val id: Int,
    val question: String,
    val options: List<String>,
    val answer: String,
    val funFact: String,
    val level: String,
    val image: String? = null
)

class TriviaManager(private val context: Context, private val langCode: String = "es") {
    // Usamos SharedPreferences por idioma para evitar mezclar IDs de preguntas
    private val prefs = context.getSharedPreferences("trivia_prefs_$langCode", Context.MODE_PRIVATE)
    
    private var allQuestions: MutableList<TriviaQuestion> = mutableListOf()
    private var currentSession: MutableList<TriviaQuestion> = mutableListOf()
    private var currentIndex = 0
    private var score = 0

    init {
        loadLocalQuestions()
    }

    private fun loadLocalQuestions() {
        try {
            // Intentamos cargar el JSON localizado si existe. Si no, lanza excepción y delega a la IA.
            val filename = if (langCode == "es") "trivia_questions.json" else "trivia_questions_$langCode.json"
            val jsonStr = context.assets.open(filename).bufferedReader().use { it.readText() }
            parseAndAddQuestions(jsonStr)
        } catch (e: Exception) {
            Log.e("TriviaManager", "No se encontró $langCode local. Se usará Vertex AI.")
        }
    }

    fun addDynamicQuestions(jsonString: String): Boolean {
        return try {
            parseAndAddQuestions(jsonString)
            true
        } catch (e: Exception) {
            Log.e("TriviaManager", "Error parseando JSON de Vertex AI: ${e.message}")
            false
        }
    }

    private fun parseAndAddQuestions(jsonStr: String) {
        val jsonArray = JSONArray(jsonStr)
        
        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.getJSONObject(i)
            val optsArray = obj.getJSONArray("opts")
            val opts = mutableListOf<String>()
            for (j in 0 until optsArray.length()) opts.add(optsArray.getString(j))
            
            allQuestions.add(
                TriviaQuestion(
                    id = obj.getInt("id"),
                    question = obj.getString("q"),
                    options = opts.shuffled(),
                    answer = obj.getString("ans"),
                    funFact = obj.optString("funFact", obj.optString("fun_fact", "")),
                    level = obj.getString("level"),
                    image = if (obj.has("image")) obj.getString("image").takeIf { it.isNotBlank() } else null
                )
            )
        }
        allQuestions = allQuestions.distinctBy { it.id }.toMutableList()
    }

    private fun getPlayedIds(): Set<String> = prefs.getStringSet("played_ids", emptySet()) ?: emptySet()
    
    private fun markAsPlayed(id: Int) {
        val played = getPlayedIds().toMutableSet()
        played.add(id.toString())
        prefs.edit { putStringSet("played_ids", played) }
    }

    fun resetPlayedHistory() {
        prefs.edit { remove("played_ids") }
        Log.d("TriviaManager", "Historial de preguntas reseteado.")
    }

    fun getAvailableQuestionsCount(): Int {
        val played = getPlayedIds()
        return allQuestions.count { it.id.toString() !in played }
    }

    private fun levelWeight(level: String): Int = when(level.lowercase()) {
        "easy" -> 1
        "medium" -> 2
        "hard" -> 3
        "expert" -> 4
        else -> 2
    }

    fun startCasualSession() {
        val numQuestions = 4
        val played = getPlayedIds()
        var unplayed = allQuestions.filter { it.id.toString() !in played }.shuffled()
        
        if (unplayed.size < numQuestions) {
            resetPlayedHistory()
            unplayed = allQuestions.shuffled()
        }

        if (unplayed.isEmpty()) {
            currentSession = mutableListOf()
            return
        }

        currentSession = unplayed.take(numQuestions).toMutableList()
        currentSession.forEach { markAsPlayed(it.id) }

        currentIndex = 0
        score = 0
    }

    fun startOnlineSession() {
        val numQuestions = 10
        val played = getPlayedIds()
        var unplayed = allQuestions.filter { it.id.toString() !in played }.shuffled()
        
        if (unplayed.size < numQuestions) {
            resetPlayedHistory()
            unplayed = allQuestions.shuffled()
        }

        if (unplayed.isEmpty()) {
            currentSession = mutableListOf()
            return
        }

        // Seleccionamos 10 aleatorias y luego las ORDENAMOS por dificultad de menos a más.
        currentSession = unplayed.take(numQuestions).sortedBy { levelWeight(it.level) }.toMutableList()
        currentSession.forEach { markAsPlayed(it.id) }

        currentIndex = 0
        score = 0
    }

    fun getCurrentQuestion(): TriviaQuestion? {
        if (currentIndex < currentSession.size) return currentSession[currentIndex]
        return null
    }

    fun getProgressText(): String = "Ronda ${currentIndex + 1}/${currentSession.size}"

    fun checkAnswer(selectedOption: String): Boolean {
        val q = currentSession[currentIndex]
        val correct = selectedOption == q.answer
        if (correct) score++
        return correct
    }

    fun nextQuestion() {
        currentIndex++
    }

    fun isFinished(): Boolean = currentSession.isNotEmpty() && currentIndex >= currentSession.size

    fun getFinalRank(lang: String = "es"): String {
        val percent = if (currentSession.isEmpty()) 0 else (score * 100) / currentSession.size
        return when (lang) {
            "en" -> when {
                percent == 100 -> "GOD OF ARCADE 👑"
                percent >= 80 -> "ARCADE MASTER 🕹️"
                percent >= 60 -> "16-BIT VETERAN 🎮"
                percent >= 40 -> "CASUAL GAMER 👾"
                else -> "PONG ROOKIE 📺"
            }
            "fr" -> when {
                percent == 100 -> "DIEU DE L'ARCADE 👑"
                percent >= 80 -> "MAÎTRE D'ARCADE 🕹️"
                percent >= 60 -> "VÉTÉRAN 16-BITS 🎮"
                percent >= 40 -> "JOUEUR OCCASIONNEL 👾"
                else -> "DÉBUTANT DE PONG 📺"
            }
            "de" -> when {
                percent == 100 -> "GOTT DER ARCADE 👑"
                percent >= 80 -> "ARCADE-MEISTER 🕹️"
                percent >= 60 -> "16-BIT VETERAN 🎮"
                percent >= 40 -> "GELEGENHEITSSPIELER 👾"
                else -> "PONG ANFÄNGER 📺"
            }
            "it" -> when {
                percent == 100 -> "DIO DELL'ARCADE 👑"
                percent >= 80 -> "MAESTRO ARCADE 🕹️"
                percent >= 60 -> "VETERANO 16-BIT 🎮"
                percent >= 40 -> "GIOCATORE CASUAL 👾"
                else -> "PRINCIPIANTE PONG 📺"
            }
            "zh" -> when {
                percent == 100 -> "街机之神 👑"
                percent >= 80 -> "街机大师 🕹️"
                percent >= 60 -> "16位老兵 🎮"
                percent >= 40 -> "休闲玩家 👾"
                else -> "PONG 新手 📺"
            }
            else -> when { // Spanish default
                percent == 100 -> "DIOS DEL ARCADE 👑"
                percent >= 80 -> "MAESTRO DE GAELCO 🕹️"
                percent >= 60 -> "VETERANO DE 16-BITS 🎮"
                percent >= 40 -> "JUGADOR CASUAL 👾"
                else -> "NOVATO DEL PONG 📺"
            }
        }
    }
    
    fun getScoreString() = "$score / ${currentSession.size}"
}
