package com.generacionarcade.speccyos

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.util.Log

class SpeccyAccessibilityService : AccessibilityService() {

    private lateinit var keyMapper: KeyMapper
    private val TAG = "SpeccyAccess"

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(TAG, "DENTRO: Servicio de Accesibilidad conectado.")
        
        // ALCANCE MÍNIMO IMPRESCINDIBLE.
        //
        // Antes se pedía `TYPES_ALL_MASK` + `FLAG_RETRIEVE_INTERACTIVE_WINDOWS`,
        // es decir acceso al CONTENIDO DE PANTALLA de todas las apps del
        // dispositivo, cuando este servicio sólo implementa `onKeyEvent` y deja
        // `onAccessibilityEvent` vacío. Era un permiso enorme sin ninguna función,
        // y la clase de petición que Google retira de Play.
        //
        // Ahora: cero eventos observados, sólo filtrado de teclas físicas.
        val info = AccessibilityServiceInfo().apply {
            eventTypes = 0
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
            notificationTimeout = 100
        }
        this.serviceInfo = info
        
        keyMapper = KeyMapper(this)
        Log.i(TAG, "Configuración imperial aplicada. Vigilando botones...")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onKeyEvent(event: KeyEvent): Boolean {
        val keyCode = event.keyCode
        val action = event.action

        if (action == KeyEvent.ACTION_DOWN) {
            Log.d(TAG, "TECLA DETECTADA -> Código: $keyCode")
            
            val mappedMenuKey = keyMapper.btnMenu
            val mappedSelectKey = keyMapper.btnSelect
            
            // Si el código coincide con M, el menú estándar o el 109 (común en E5 Ultra)
            if (keyCode == mappedMenuKey || keyCode == KeyEvent.KEYCODE_MENU || keyCode == 109) {
                Log.i(TAG, "¡COINCIDENCIA MENU! Disparando Overlay para KeyCode: $keyCode")
                toggleOverlay("QUICK_ACCESS")
                return true 
            }

            // Detección del botón SELECT para el Menú de Texto
            if (keyCode == mappedSelectKey || keyCode == KeyEvent.KEYCODE_BUTTON_SELECT) {
                Log.i(TAG, "¡COINCIDENCIA SELECT! Disparando Menú de Texto para KeyCode: $keyCode")
                toggleOverlay("TEXT_MENU")
                return true
            }
        }
        
        return super.onKeyEvent(event)
    }

    private fun toggleOverlay(type: String) {
        val intent = Intent(this, OverlayService::class.java).apply {
            action = "TOGGLE_OVERLAY"
            putExtra("OVERLAY_TYPE", type)
        }
        Log.d(TAG, "Lanzando Intent TOGGLE_OVERLAY con tipo: $type")
        startService(intent)
    }
}
