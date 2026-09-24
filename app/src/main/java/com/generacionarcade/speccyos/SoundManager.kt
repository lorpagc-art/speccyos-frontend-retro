/*
 * Speccy OS — frontend retro para Android
 * Copyright (c) 2026 LV-Webstudio · lv-webstudio.com
 * Desarrollado por Speccy81 (LORPAGC) · administracion@lv-webstudio.com
 * Todos los derechos reservados.
 */
package com.generacionarcade.speccyos

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.SoundPool
import android.media.ToneGenerator
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SoundManager(private val context: Context) {

    private val soundPool: SoundPool
    private val toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 40)
    private val settingsManager = SettingsManager(context)

    private var clickSoundId = 0
    private var backSoundId = 0
    private var launchSoundId = 0

    // OJO AL ORDEN: esta propiedad DEBE declararse antes del bloque `init`, que
    // la usa. En Kotlin los inicializadores y los bloques init se ejecutan en
    // orden de declaración: declararla después dejaba `scope` a null en init.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var mediaPlayer: MediaPlayer? = null
    var isBgmEnabled: Boolean = settingsManager.isBackgroundMusicEnabled
        private set

    init {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        soundPool = SoundPool.Builder()
            .setMaxStreams(5)
            .setAudioAttributes(audioAttributes)
            .build()

        // Antes: `CoroutineScope(Dispatchers.IO).launch { }` efímero y no
        // cancelable. Si la pantalla se destruía durante prepare(), el MediaPlayer
        // quedaba huérfano y las corrutinas seguían vivas sin dueño.
        scope.launch { loadSounds() }
    }

    /** Debe llamarse al destruir la pantalla o el servicio que lo creó. */
    fun release() {
        scope.cancel()
        stopBgm()
        runCatching { mediaPlayer?.stop(); mediaPlayer?.release() }
        mediaPlayer = null
        runCatching { soundPool.release() }
        runCatching { toneGenerator.release() }
    }

    private fun loadSounds() {
        try {
            clickSoundId = loadAssetSound("audio/click.ogg")
            backSoundId = loadAssetSound("audio/back.ogg")
            launchSoundId = loadAssetSound("audio/launch.ogg")
        } catch (e: Exception) {
            Log.e("SoundManager", "Error cargando sonidos", e)
        }
    }

    private fun loadAssetSound(path: String): Int {
        return try {
            val descriptor = context.assets.openFd(path)
            soundPool.load(descriptor, 1)
        } catch (e: Exception) { 0 }
    }

    fun playClick() {
        if (clickSoundId != 0) soundPool.play(clickSoundId, 1f, 1f, 1, 0, 1f)
        else toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP, 30)
    }

    fun playBack() {
        if (backSoundId != 0) soundPool.play(backSoundId, 1f, 1f, 1, 0, 1f)
        else toneGenerator.startTone(ToneGenerator.TONE_PROP_NACK, 50)
    }

    fun playLaunch() {
        if (launchSoundId != 0) soundPool.play(launchSoundId, 1f, 1f, 1, 0, 1f)
        else toneGenerator.startTone(ToneGenerator.TONE_CDMA_HIGH_L, 150)
    }

    fun toggleBgm(enabled: Boolean) {
        isBgmEnabled = enabled
        settingsManager.isBackgroundMusicEnabled = enabled
        if (enabled) {
            startBgm()
        } else {
            stopBgm()
        }
    }

    fun startBgm() {
        if (!isBgmEnabled || mediaPlayer != null) return
        scope.launch {
            try {
                val mp = MediaPlayer()
                // setDataSource(FileDescriptor, ...) NO se queda con el descriptor:
                // hay que cerrarlo a mano o se filtra uno por cada startBgm(), y
                // el usuario puede alternar la musica en ajustes cuantas veces
                // quiera. Con use{} se cierra siempre, tambien si prepare() falla.
                context.assets.openFd("contentimg/Neon_Dreams.mp3").use { descriptor ->
                    mp.setDataSource(descriptor.fileDescriptor, descriptor.startOffset, descriptor.length)
                }
                mp.isLooping = true
                mp.setVolume(0.4f, 0.4f)
                mp.prepare()
                mediaPlayer = mp
                mp.start()
            } catch (e: Exception) {
                Log.w("SoundManager", "BGM Neon_Dreams.mp3 no encontrado.")
            }
        }
    }

    fun pauseBgm() = mediaPlayer?.pause()
    fun resumeBgm() { if (isBgmEnabled) mediaPlayer?.start() }
    fun stopBgm() {
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
    }

}
