package com.generacionarcade.speccyos

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.util.Log

data class AndroidApp(
    val packageName: String,
    val label: String,
    val icon: Drawable,
    val isSystemShortcut: Boolean = false
)

class AppLauncherManager(private val context: Context) {

    /**
     * Obtiene la lista completa de aplicaciones del usuario, incluyendo juegos.
     */
    fun getInstalledGames(): List<AndroidApp> {
        val pm = context.packageManager
        val apps = mutableListOf<AndroidApp>()

        // 1. Añadir accesos directos del sistema (Opcional, pero útil para acceso rápido)
        addShortcutIfInstalled(apps, "com.android.vending", "PLAY STORE")
        addShortcutIfInstalled(apps, "com.google.android.play.games", "PLAY GAMES")

        // 2. Escanear todas las apps instaladas que tengan un lanzador (interfaz de usuario)
        val installedApps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        for (app in installedApps) {
            val launchIntent = try {
                pm.getLaunchIntentForPackage(app.packageName)
            } catch (e: Exception) {
                Log.w("AppLauncher", "Error obteniendo intent para ${app.packageName}: ${e.message}")
                null
            }
            
            // Filtro: Solo apps con Launcher, que no sea la propia Speccy OS
            if (launchIntent != null && app.packageName != context.packageName) {
                
                // Determinamos si es un juego para el sistema (opcional, ahora mostramos todo)
                val isGame = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    app.category == ApplicationInfo.CATEGORY_GAME
                } else {
                    app.packageName.contains("game", ignoreCase = true)
                }

                // Añadimos la aplicación a la lista
                apps.add(AndroidApp(
                    packageName = app.packageName,
                    label = pm.getApplicationLabel(app).toString(),
                    icon = pm.getApplicationIcon(app)
                ))
            }
        }
        
        // Ordenamos alfabéticamente por nombre de la app
        return apps.distinctBy { it.packageName }.sortedBy { it.label.lowercase() }
    }

    private fun addShortcutIfInstalled(list: MutableList<AndroidApp>, pkg: String, label: String) {
        try {
            val icon = context.packageManager.getApplicationIcon(pkg)
            if (list.none { it.packageName == pkg }) {
                list.add(AndroidApp(pkg, label, icon, true))
            }
        } catch (e: Exception) {
            Log.w("AppLauncher", "Acceso directo omitido: $pkg no instalado.")
        }
    }

    fun launchApp(packageName: String) {
        try {
            val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            }
        } catch (e: Exception) {
            Log.e("AppLauncher", "Error al lanzar app $packageName", e)
        }
    }
}
