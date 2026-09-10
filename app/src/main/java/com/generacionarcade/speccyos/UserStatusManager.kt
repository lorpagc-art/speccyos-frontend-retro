package com.generacionarcade.speccyos

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class UserLevel {
    FREE, IMPERIAL
}

/**
 * 👑 USER STATUS MANAGER - Speccy OS E5 Ultra (Full Free Edition)
 * Se ha eliminado la restricción de niveles y energía. Todos los usuarios son "Imperial".
 */
class UserStatusManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("user_status_prefs", Context.MODE_PRIVATE)
    
    // Ahora siempre devolvemos IMPERIAL por defecto
    private val _userLevel = MutableStateFlow(UserLevel.IMPERIAL)
    val userLevel = _userLevel.asStateFlow()

    private val _userName = MutableStateFlow(prefs.getString("user_display_name", "Invitado") ?: "Invitado")
    val userName = _userName.asStateFlow()

    private val _userEmail = MutableStateFlow(prefs.getString("user_email", "") ?: "")
    val userEmail = _userEmail.asStateFlow()

    private val _userPhotoUrl = MutableStateFlow(prefs.getString("user_photo_url", "") ?: "")
    val userPhotoUrl = _userPhotoUrl.asStateFlow()

    // Energía infinita (se mantiene el flow para no romper la UI, pero siempre al máximo)
    private val _aiEnergy = MutableStateFlow(99)
    val aiEnergy = _aiEnergy.asStateFlow()

    private fun loadUserLevel(): UserLevel = UserLevel.IMPERIAL

    fun syncUser(email: String, name: String, photoUrl: String) {
        prefs.edit().apply {
            putString("user_email", email)
            putString("user_display_name", name)
            putString("user_photo_url", photoUrl)
            putString("user_level", UserLevel.IMPERIAL.name)
        }.apply()
        
        _userEmail.value = email
        _userName.value = name
        _userPhotoUrl.value = photoUrl
        _userLevel.value = UserLevel.IMPERIAL
    }

    fun logout() {
        prefs.edit().apply {
            remove("user_email")
            remove("user_display_name")
            remove("user_photo_url")
            remove("user_level")
            remove("ai_energy")
        }.apply()
        _userEmail.value = ""
        _userName.value = "Invitado"
        _userPhotoUrl.value = ""
        _userLevel.value = UserLevel.IMPERIAL
        _aiEnergy.value = 99
    }

    fun updateProfile(name: String?, photoUrl: String?) {
        prefs.edit().apply {
            putString("user_display_name", name ?: "Invitado")
            putString("user_photo_url", photoUrl ?: "")
        }.apply()
        _userName.value = name ?: "Invitado"
        _userPhotoUrl.value = photoUrl ?: ""
    }

    fun setImperialStatus(isImperial: Boolean) {
        // Ignoramos el parámetro y forzamos Imperial
        _userLevel.value = UserLevel.IMPERIAL
    }

    fun isImperial(): Boolean = true
    
    // --- LÓGICA DE ENERGÍA (DESACTIVADA / INFINITA) ---
    fun canUseAiFeature(): Boolean = true

    fun consumeEnergy() {
        // No-op en versión gratuita
    }

    fun addEnergy(amount: Int) {
        // No-op o mantener visualmente al máximo
        _aiEnergy.value = 99
    }
}
