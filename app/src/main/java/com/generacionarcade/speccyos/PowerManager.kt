package com.generacionarcade.speccyos

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build

data class PowerStats(
    val percentage: Int,
    val isCharging: Boolean,
    val currentNowMa: Int, // Corriente en mA
    val chargeCounterMah: Int,  // mAh restantes
    val remainingTimeMinutes: Int, // Tiempo estimado restante
    val health: String,
    val temperature: Float,
    val cycleCount: Int = -1
)

class PowerManager(private val context: Context) {

    private val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager

    fun getPowerStats(): PowerStats {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val pct = if (level != -1 && scale != -1) (level * 100 / scale.toFloat()).toInt() else 0
        
        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        
        // Corriente en microamperios (convertir a mA)
        val currentNow = try {
            batteryManager.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW) / 1000
        } catch (e: Exception) { 0L }
        
        val temp = (intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10f
        
        val healthInt = intent?.getIntExtra(BatteryManager.EXTRA_HEALTH, BatteryManager.BATTERY_HEALTH_UNKNOWN) ?: BatteryManager.BATTERY_HEALTH_UNKNOWN
        val healthString = when(healthInt) {
            BatteryManager.BATTERY_HEALTH_GOOD -> "ÓPTIMA"
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> "CALIENTE"
            BatteryManager.BATTERY_HEALTH_DEAD -> "CRÍTICA"
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "SOBREVOLTAJE"
            else -> "NORMAL"
        }

        // Estimación de tiempo restante (Imperial Algorithm)
        val chargeCounter = try {
            batteryManager.getLongProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER) / 1000 // mAh restantes
        } catch (e: Exception) { 0L }
        
        val remainingMinutes = if (!isCharging && currentNow < 0) {
            val absCurrent = Math.abs(currentNow)
            if (absCurrent > 0) (chargeCounter * 60 / absCurrent).toInt() else -1
        } else if (isCharging && currentNow > 0) {
            // Estimación de carga (Asumiendo 5000mAh si no se puede deducir)
            val estimatedTotal = if (pct > 0) (chargeCounter * 100 / pct) else 5000L
            val toCharge = estimatedTotal - chargeCounter
            if (toCharge > 0) (toCharge * 60 / currentNow).toInt() else -1
        } else {
            -1
        }

        // BATTERY_PROPERTY_CYCLE_COUNT is constant 6 (API 26+)
        val cycles = if (Build.VERSION.SDK_INT >= 26) {
            try {
                batteryManager.getIntProperty(6)
            } catch (e: Exception) { -1 }
        } else -1

        return PowerStats(
            percentage = pct,
            isCharging = isCharging,
            currentNowMa = currentNow.toInt(),
            chargeCounterMah = chargeCounter.toInt(),
            remainingTimeMinutes = remainingMinutes,
            health = healthString,
            temperature = temp,
            cycleCount = cycles
        )
    }
}
