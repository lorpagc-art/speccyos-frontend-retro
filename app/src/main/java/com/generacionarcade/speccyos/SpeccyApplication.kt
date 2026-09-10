package com.generacionarcade.speccyos

import android.app.Application
import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache

class SpeccyApplication : Application(), ImageLoaderFactory {

    override fun onCreate() {
        super.onCreate()

        // Identidad en la nube: App Check + sesión anónima. No bloquea el arranque.
        SpeccyIdentity.initialize(this)

        // Inicializar RetroArchSyncManager al arrancar el proceso.
        // Registra el BroadcastReceiver para org.libretro.RetroArch.EXIT
        // antes de que cualquier Activity esté en primer plano.
        RetroArchSyncManager.initialize(this)
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .memoryCache {
                // El presupuesto depende de la RAM real: una Fire TV de 2 GB que
                // además ejecuta un emulador no puede permitirse lo mismo que una
                // Odin 2 de 16 GB.
                val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                val lowRam = am.isLowRamDevice || am.memoryClass <= 128
                MemoryCache.Builder(this)
                    .maxSizePercent(if (lowRam) 0.08 else 0.18)
                    // strongReferences impide que el GC recupere bitmaps bajo presión:
                    // desactivado en equipos con poca RAM.
                    .strongReferencesEnabled(!lowRam)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    // 150 MB — razonable para carátulas en consolas con almacenamiento limitado
                    .maxSizeBytes(150L * 1024 * 1024)
                    .build()
            }
            .crossfade(true)
            // false: ScreenScraper envía cabeceras restrictivas y con `true` las
            // carátulas ya cacheadas se volvían a descargar en cada arranque.
            // Las carátulas de un juego retro no cambian: la caché es permanente.
            .respectCacheHeaders(false)
            // RGB_565 ahorra la mitad de memoria por bitmap y es indistinguible en
            // carátulas y miniaturas.
            .bitmapConfig(Bitmap.Config.RGB_565)
            .build()
    }
}
