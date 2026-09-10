package com.generacionarcade.speccyos

/**
 * DailyNotificationWorker.kt
 * ─────────────────────────────────────────────────────────────────
 * Notificación push diaria "Hoy en la historia" usando WorkManager.
 *
 * Se programa al abrir la app por primera vez cada día.
 * Si el usuario tiene juegos con fecha de lanzamiento coincidente
 * con hoy, envía una notificación personalizada.
 * Si no, usa el reto del día de Firebase Remote Config.
 *
 * INTEGRACIÓN en SpeccyApplication.kt o MainActivity.onCreate():
 *   DailyNotificationScheduler.schedule(context)
 * ─────────────────────────────────────────────────────────────────
 */

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.concurrent.TimeUnit

class DailyNotificationWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val CHANNEL_ID   = "speccy_daily"
        private const val CHANNEL_NAME = "Especias del Día"
        private const val NOTIF_ID     = 1001
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            createChannel()

            // Busca juegos lanzados hoy, EN SQL.
            // Antes se cargaba la tabla entera con getAllGamesSync() y se filtraba
            // con un Regex en Kotlin — con una colección MAME son 30.000 objetos
            // Game construidos para acabar usando uno solo, dentro de un Worker
            // en segundo plano al que el sistema puede matar por consumo.
            val db = AppDatabase.getDatabase(applicationContext)
            val cal = java.util.Calendar.getInstance()
            val dd = String.format(java.util.Locale.US, "%02d", cal.get(java.util.Calendar.DAY_OF_MONTH))
            val mm = String.format(java.util.Locale.US, "%02d", cal.get(java.util.Calendar.MONTH) + 1)
            val todayGames = runCatching {
                db.gameDao().getGamesReleasedOn("%-$mm-$dd%", "%$dd/$mm/%")
            }.getOrDefault(emptyList())

            val (title, body) = if (todayGames.isNotEmpty()) {
                val game = todayGames.first()
                "🕹️ ${game.title}" to "Hoy en la historia, salió este clásico. ¿Lo tienes en tu biblioteca?"
            } else {
                "⚡ Reto del día disponible" to "¿Puedes completarlo hoy? Entra y súmate al ranking global."
            }

            val intent = applicationContext.packageManager
                .getLaunchIntentForPackage(applicationContext.packageName)
                ?.apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP }

            val pendingIntent = PendingIntent.getActivity(
                applicationContext, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(body)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build()

            val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.notify(NOTIF_ID, notification)

            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notificaciones de retos diarios y efemérides retro"
            }
            val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }
}

// ─── SCHEDULER ────────────────────────────────────────────────────

object DailyNotificationScheduler {

    private const val WORK_NAME = "speccy_daily_notification"

    /** Llama esto desde MainActivity.onResume() o SpeccyApplication.onCreate() */
    fun schedule(context: Context) {
        // Calcula milisegundos hasta las 18:00 de hoy (hora retro)
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 18)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            if (before(now)) add(Calendar.DAY_OF_YEAR, 1) // Si ya pasaron las 18h, programa para mañana
        }
        val delayMs = target.timeInMillis - now.timeInMillis

        val request = OneTimeWorkRequestBuilder<DailyNotificationWorker>()
            .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                    .build()
            )
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }
}
