/*
 * Speccy OS — frontend retro para Android
 * Copyright (c) 2026 LV-Webstudio · lv-webstudio.com
 * Desarrollado por Speccy81 (LORPAGC) · administracion@lv-webstudio.com
 * Todos los derechos reservados.
 */
package com.generacionarcade.speccyos

import android.content.Context
import android.view.KeyEvent

class KeyMapper(context: Context) {
    private val prefs = context.getSharedPreferences("key_mappings", Context.MODE_PRIVATE)

    // Botones Principales
    var actionA: Int
        get() = prefs.getInt("btn_a", KeyEvent.KEYCODE_BUTTON_A)
        set(value) = prefs.edit().putInt("btn_a", value).apply()

    var actionB: Int
        get() = prefs.getInt("btn_b", KeyEvent.KEYCODE_BUTTON_B)
        set(value) = prefs.edit().putInt("btn_b", value).apply()

    var actionX: Int
        get() = prefs.getInt("btn_x", KeyEvent.KEYCODE_BUTTON_X)
        set(value) = prefs.edit().putInt("btn_x", value).apply()

    var actionY: Int
        get() = prefs.getInt("btn_y", KeyEvent.KEYCODE_BUTTON_Y)
        set(value) = prefs.edit().putInt("btn_y", value).apply()

    // Cruceta (DPAD)
    var dpadUp: Int
        get() = prefs.getInt("dpad_up", KeyEvent.KEYCODE_DPAD_UP)
        set(value) = prefs.edit().putInt("dpad_up", value).apply()

    var dpadDown: Int
        get() = prefs.getInt("dpad_down", KeyEvent.KEYCODE_DPAD_DOWN)
        set(value) = prefs.edit().putInt("dpad_down", value).apply()

    var dpadLeft: Int
        get() = prefs.getInt("dpad_left", KeyEvent.KEYCODE_DPAD_LEFT)
        set(value) = prefs.edit().putInt("dpad_left", value).apply()

    var dpadRight: Int
        get() = prefs.getInt("dpad_right", KeyEvent.KEYCODE_DPAD_RIGHT)
        set(value) = prefs.edit().putInt("dpad_right", value).apply()

    // Stick Izquierdo
    var lStickUp: Int
        get() = prefs.getInt("lstick_up", -1)
        set(value) = prefs.edit().putInt("lstick_up", value).apply()
    var lStickDown: Int
        get() = prefs.getInt("lstick_down", -1)
        set(value) = prefs.edit().putInt("lstick_down", value).apply()
    var lStickLeft: Int
        get() = prefs.getInt("lstick_left", -1)
        set(value) = prefs.edit().putInt("lstick_left", value).apply()
    var lStickRight: Int
        get() = prefs.getInt("lstick_right", -1)
        set(value) = prefs.edit().putInt("lstick_right", value).apply()

    // Stick Derecho
    var rStickUp: Int
        get() = prefs.getInt("rstick_up", -1)
        set(value) = prefs.edit().putInt("rstick_up", value).apply()
    var rStickDown: Int
        get() = prefs.getInt("rstick_down", -1)
        set(value) = prefs.edit().putInt("rstick_down", value).apply()
    var rStickLeft: Int
        get() = prefs.getInt("rstick_left", -1)
        set(value) = prefs.edit().putInt("rstick_left", value).apply()
    var rStickRight: Int
        get() = prefs.getInt("rstick_right", -1)
        set(value) = prefs.edit().putInt("rstick_right", value).apply()

    // Gatillos y Hombros
    var btnL1: Int
        get() = prefs.getInt("btn_l1", KeyEvent.KEYCODE_BUTTON_L1)
        set(value) = prefs.edit().putInt("btn_l1", value).apply()
    var btnR1: Int
        get() = prefs.getInt("btn_r1", KeyEvent.KEYCODE_BUTTON_R1)
        set(value) = prefs.edit().putInt("btn_r1", value).apply()
    var btnL2: Int
        get() = prefs.getInt("btn_l2", KeyEvent.KEYCODE_BUTTON_L2)
        set(value) = prefs.edit().putInt("btn_l2", value).apply()
    var btnR2: Int
        get() = prefs.getInt("btn_r2", KeyEvent.KEYCODE_BUTTON_R2)
        set(value) = prefs.edit().putInt("btn_r2", value).apply()
    var btnL3: Int
        get() = prefs.getInt("btn_l3", KeyEvent.KEYCODE_BUTTON_THUMBL)
        set(value) = prefs.edit().putInt("btn_l3", value).apply()
    var btnR3: Int
        get() = prefs.getInt("btn_r3", KeyEvent.KEYCODE_BUTTON_THUMBR)
        set(value) = prefs.edit().putInt("btn_r3", value).apply()

    // Sistema y Menú
    var btnStart: Int
        get() = prefs.getInt("btn_start", KeyEvent.KEYCODE_BUTTON_START)
        set(value) = prefs.edit().putInt("btn_start", value).apply()
    var btnSelect: Int
        get() = prefs.getInt("btn_select", KeyEvent.KEYCODE_BUTTON_SELECT)
        set(value) = prefs.edit().putInt("btn_select", value).apply()
    var btnMenu: Int
        get() = prefs.getInt("btn_menu", KeyEvent.KEYCODE_MENU)
        set(value) = prefs.edit().putInt("btn_menu", value).apply()
    var btnHome: Int
        get() = prefs.getInt("btn_home", KeyEvent.KEYCODE_HOME)
        set(value) = prefs.edit().putInt("btn_home", value).apply()
    var btnBack: Int
        get() = prefs.getInt("btn_back", KeyEvent.KEYCODE_BACK)
        set(value) = prefs.edit().putInt("btn_back", value).apply()

    // Hotkeys
    var hotkeyMenu: Int
        get() = prefs.getInt("hk_menu", -1)
        set(value) = prefs.edit().putInt("hk_menu", value).apply()
    var hotkeyExit: Int
        get() = prefs.getInt("hk_exit", -1)
        set(value) = prefs.edit().putInt("hk_exit", value).apply()
    var hotkeyEnable: Int
        get() = prefs.getInt("hk_enable", -1)
        set(value) = prefs.edit().putInt("hk_enable", value).apply()

    fun getMapping(action: String): Int {
        return when (action) {
            "actionA" -> actionA
            "actionB" -> actionB
            "actionX" -> actionX
            "actionY" -> actionY
            "dpadUp" -> dpadUp
            "dpadDown" -> dpadDown
            "dpadLeft" -> dpadLeft
            "dpadRight" -> dpadRight
            "lStickUp" -> lStickUp
            "lStickDown" -> lStickDown
            "lStickLeft" -> lStickLeft
            "lStickRight" -> lStickRight
            "rStickUp" -> rStickUp
            "rStickDown" -> rStickDown
            "rStickLeft" -> rStickLeft
            "rStickRight" -> rStickRight
            "btnL1" -> btnL1
            "btnR1" -> btnR1
            "btnL2" -> btnL2
            "btnR2" -> btnR2
            "btnL3" -> btnL3
            "btnR3" -> btnR3
            "btnStart" -> btnStart
            "btnSelect" -> btnSelect
            "btnMenu" -> btnMenu
            "btnHome" -> btnHome
            "btnBack" -> btnBack
            "hotkeyMenu" -> hotkeyMenu
            "hotkeyExit" -> hotkeyExit
            "hotkeyEnable" -> hotkeyEnable
            else -> -1
        }
    }

    fun setMapping(action: String, keyCode: Int) {
        when (action) {
            "actionA" -> actionA = keyCode
            "actionB" -> actionB = keyCode
            "actionX" -> actionX = keyCode
            "actionY" -> actionY = keyCode
            "dpadUp" -> dpadUp = keyCode
            "dpadDown" -> dpadDown = keyCode
            "dpadLeft" -> dpadLeft = keyCode
            "dpadRight" -> dpadRight = keyCode
            "lStickUp" -> lStickUp = keyCode
            "lStickDown" -> lStickDown = keyCode
            "lStickLeft" -> lStickLeft = keyCode
            "lStickRight" -> lStickRight = keyCode
            "rStickUp" -> rStickUp = keyCode
            "rStickDown" -> rStickDown = keyCode
            "rStickLeft" -> rStickLeft = keyCode
            "rStickRight" -> rStickRight = keyCode
            "btnL1" -> btnL1 = keyCode
            "btnR1" -> btnR1 = keyCode
            "btnL2" -> btnL2 = keyCode
            "btnR2" -> btnR2 = keyCode
            "btnL3" -> btnL3 = keyCode
            "btnR3" -> btnR3 = keyCode
            "btnStart" -> btnStart = keyCode
            "btnSelect" -> btnSelect = keyCode
            "btnMenu" -> btnMenu = keyCode
            "btnHome" -> btnHome = keyCode
            "btnBack" -> btnBack = keyCode
            "hotkeyMenu" -> hotkeyMenu = keyCode
            "hotkeyExit" -> hotkeyExit = keyCode
            "hotkeyEnable" -> hotkeyEnable = keyCode
        }
    }

    fun reset() {
        prefs.edit().clear().apply()
    }
}
