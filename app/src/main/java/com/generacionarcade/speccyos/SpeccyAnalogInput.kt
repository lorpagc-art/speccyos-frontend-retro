package com.generacionarcade.speccyos

import android.os.Handler
import android.os.Looper
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent

/**
 * SpeccyAnalogInput
 * ----------------------------------------------------------------------------
 * Navegacion de la interfaz con los STICKS ANALOGICOS.
 *
 * EL HUECO QUE CUBRE
 * ------------------
 * En los 97 ficheros del proyecto no habia NI UNA referencia a `MotionEvent`,
 * `AXIS_*`, `onGenericMotionEvent` ni `InputDevice`: todo el input de la interfaz
 * era `KEYCODE_DPAD_*`.
 *
 * Y sin embargo `KeyMapper` SI define `lStickUp/Down/Left/Right` y `rStick*`:
 * existia la pantalla de mapeo de sticks y no hacia absolutamente nada en el
 * launcher. En una Retroid, una AYANEO o cualquier RG-series, mucha gente navega
 * por defecto con el stick izquierdo, y la app no respondia.
 *
 * COMO FUNCIONA
 * -------------
 * Traduce el stick a eventos `KEYCODE_DPAD_*` sinteticos, de modo que TODO el
 * codigo de navegacion existente (que ya escucha D-pad) funciona sin tocar una
 * sola pantalla.
 *
 * DETALLE IMPORTANTE: LA REPETICION VA POR RELOJ, NO POR EVENTOS.
 * Android solo emite `ACTION_MOVE` cuando el valor del eje CAMBIA. Con el stick
 * mantenido a tope, un Hall Effect bien calibrado deja de generar eventos: si la
 * autorrepeticion dependiera de ellos, el cursor avanzaria una posicion y se
 * quedaria clavado. Por eso, al entrar en zona activa se arranca un repetidor
 * propio con `Handler`, y se cancela al volver al centro.
 */
object SpeccyAnalogInput {

    /** Umbral de activacion. Por debajo se considera reposo. */
    private const val DEAD_ZONE = 0.5f
    /** Umbral de liberacion, mas bajo: evita el parpadeo justo en el limite. */
    private const val RELEASE_ZONE = 0.35f

    private const val FIRST_REPEAT_MS = 380L
    private const val NEXT_REPEAT_MS = 110L

    private val handler = Handler(Looper.getMainLooper())

    private var activeDirection = 0
    private var emit: ((Int) -> Unit)? = null

    private val repeater = object : Runnable {
        override fun run() {
            val dir = activeDirection
            if (dir == 0) return
            emit?.invoke(dir)
            handler.postDelayed(this, NEXT_REPEAT_MS)
        }
    }

    /**
     * Registra el consumidor de direcciones. Lo llama la Activity una vez.
     * @param onDirection recibe un KEYCODE_DPAD_* y debe sintetizar el evento.
     */
    fun attach(onDirection: (Int) -> Unit) {
        emit = onDirection
    }

    fun detach() {
        stopRepeat()
        emit = null
    }

    /**
     * Procesa un evento de movimiento.
     * @return true si el evento se ha consumido.
     */
    fun onMotionEvent(event: MotionEvent): Boolean {
        if (!isJoystick(event)) return false
        if (event.action != MotionEvent.ACTION_MOVE) return false

        // Stick izquierdo (X/Y) y, como alternativa, el hat digital que algunos
        // mandos exponen como eje en vez de como tecla.
        val x = axis(event, MotionEvent.AXIS_X, MotionEvent.AXIS_HAT_X)
        val y = axis(event, MotionEvent.AXIS_Y, MotionEvent.AXIS_HAT_Y)

        val magnitudeX = kotlin.math.abs(x)
        val magnitudeY = kotlin.math.abs(y)
        val threshold = if (activeDirection == 0) DEAD_ZONE else RELEASE_ZONE

        if (magnitudeX < threshold && magnitudeY < threshold) {
            // Vuelta al centro: se corta la repeticion y se rearma.
            stopRepeat()
            return true
        }

        // Solo el eje dominante: una diagonal no debe mover en dos direcciones.
        val direction = if (magnitudeX >= magnitudeY) {
            if (x < 0) KeyEvent.KEYCODE_DPAD_LEFT else KeyEvent.KEYCODE_DPAD_RIGHT
        } else {
            if (y < 0) KeyEvent.KEYCODE_DPAD_UP else KeyEvent.KEYCODE_DPAD_DOWN
        }

        if (direction != activeDirection) {
            stopRepeat()
            activeDirection = direction
            emit?.invoke(direction)
            handler.postDelayed(repeater, FIRST_REPEAT_MS)
        }
        return true
    }

    /** Reinicia el estado. Conviene llamarlo al pausar la Activity. */
    fun reset() = stopRepeat()

    private fun stopRepeat() {
        handler.removeCallbacks(repeater)
        activeDirection = 0
    }

    private fun isJoystick(event: MotionEvent): Boolean {
        val source = event.source
        return (source and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK ||
            (source and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD ||
            (source and InputDevice.SOURCE_DPAD) == InputDevice.SOURCE_DPAD
    }

    /**
     * Lee un eje aplicando el aplanamiento que declara el propio mando: sin esto,
     * un stick con deriva de fabrica mueve el cursor solo.
     */
    private fun axis(event: MotionEvent, primary: Int, fallback: Int): Float {
        val device = event.device
        for (axisId in intArrayOf(primary, fallback)) {
            val value = event.getAxisValue(axisId)
            val range = device?.getMotionRange(axisId, event.source)
            val flat = range?.flat ?: 0f
            if (kotlin.math.abs(value) > flat) return value
        }
        return 0f
    }
}
