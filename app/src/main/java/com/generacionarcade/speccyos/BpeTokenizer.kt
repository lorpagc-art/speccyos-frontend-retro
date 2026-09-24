/*
 * Speccy OS — frontend retro para Android
 * Copyright (c) 2026 LV-Webstudio · lv-webstudio.com
 * Desarrollado por Speccy81 (LORPAGC) · administracion@lv-webstudio.com
 * Todos los derechos reservados.
 */
package com.generacionarcade.speccyos

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.BufferedReader
import java.io.InputStreamReader

class BpeTokenizer(context: Context) {

    private val TAG = "BpeTokenizer"
    private var vocab: Map<String, Int> = emptyMap()
    private var merges: List<Pair<String, String>> = emptyList()

    init {
        try {
            vocab = loadVocab(context, "vocab.json")
            merges = loadMerges(context, "merges.txt")
            Log.d(TAG, "BPE Tokenizer loaded successfully. Vocab size: ${vocab.size}, Merges: ${merges.size}")
        } catch (e: Exception) {
            Log.e(TAG, "Error loading BPE tokenizer files", e)
        }
    }

    fun tokenize(text: String): List<Int> {
        if (vocab.isEmpty() || merges.isEmpty()) {
            Log.e(TAG, "Tokenizer not properly initialized.")
            return text.split(' ').map { it.hashCode() } // Fallback
        }

        var tokens = text.lowercase().split(' ').flatMap { it.toList().map { c -> c.toString() } } // Simple tokenization by char

        var appliedMerges = true
        while (appliedMerges) {
            appliedMerges = false
            var bestMerge: Pair<String, String>? = null
            var bestMergeIndex = -1

            for (i in 0 until tokens.size - 1) {
                val pair = tokens[i] + " " + tokens[i+1]
                val merge = merges.find { it.first == pair }
                if (merge != null) {
                    if (bestMerge == null || merges.indexOf(merge) < merges.indexOf(bestMerge!!)) {
                        bestMerge = merge
                        bestMergeIndex = i
                    }
                }
            }

            if (bestMerge != null) {
                val newTokens = mutableListOf<String>()
                for (i in 0 until tokens.size) {
                    if (i == bestMergeIndex) {
                        newTokens.add(bestMerge.second)
                    } else if (i == bestMergeIndex + 1) {
                        // Skip the second part of the merged pair
                    } else {
                        newTokens.add(tokens[i])
                    }
                }
                tokens = newTokens
                appliedMerges = true
            }
        }

        // Convert resulting tokens to IDs using the vocabulary
        return tokens.mapNotNull { vocab[it] }
    }

    fun detokenize(tokens: List<Int>): String {
        if (vocab.isEmpty() || merges.isEmpty()) {
            Log.e(TAG, "Tokenizer not properly initialized.")
            return tokens.joinToString(" ") { it.toString() } // Fallback
        }

        // Reconstruct text from IDs - this is a simplified approach
        val tokenMap = vocab.entries.associate { (key, value) -> value to key }
        val words = tokens.mapNotNull { tokenMap[it] }

        // The actual detokenization depends heavily on how the model outputs tokens
        // For simplicity, we join the words found in the vocabulary
        return words.joinToString(" ").replace(" @@", "") // Adjust as needed based on model output format
    }

    private fun loadVocab(context: Context, fileName: String): Map<String, Int> {
        return try {
            val inputStream = context.assets.open(fileName)
            val reader = BufferedReader(InputStreamReader(inputStream))
            val jsonString = reader.readText()
            val gson = Gson()
            val itemType = object : TypeToken<Map<String, Int>>() {}.type
            gson.fromJson(jsonString, itemType)
        } catch (e: Exception) {
            Log.e(TAG, "Error loading vocab file $fileName", e)
            emptyMap()
        }
    }

    private fun loadMerges(context: Context, fileName: String): List<Pair<String, String>> {
        val mergeList = mutableListOf<Pair<String, String>>()
        try {
            val inputStream = context.assets.open(fileName)
            val reader = BufferedReader(InputStreamReader(inputStream))
            reader.forEachLine {
                val parts = it.split(' ')
                if (parts.size == 2) {
                    mergeList.add(Pair(parts[0], parts[1]))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading merges file $fileName", e)
        }
        return mergeList
    }
}
