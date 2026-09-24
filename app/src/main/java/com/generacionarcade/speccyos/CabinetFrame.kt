package com.generacionarcade.speccyos

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Muebles y consolas de `assets/contentimg/cabinets/`: un arcade para los
 * sistemas arcade, una tele para las consolas de sobremesa y la propia
 * maquina para las portatiles. Cada imagen es 1920x1080 con la pantalla
 * TRANSPARENTE: el video o la captura se pinta debajo, en el hueco, y el
 * mueble encima. La Game Boy no tiene hueco sino una pantalla translucida
 * verdosa, que hace de filtro LCD sobre el video.
 *
 * El mueble ocupa la mitad izquierda de la imagen (la derecha es un
 * degradado transparente para fondos), asi que se recorta a [ANCHO_UTIL]
 * y el rectangulo de pantalla se expresa en fracciones de ese recorte.
 * Los rectangulos se midieron con un script sobre el canal alfa (12-sep-2026).
 */
object CabinetFrames {

    /** Fraccion de la imagen que ocupa el mueble (el resto es fondo). */
    private const val ANCHO_UTIL = 0.5f
    const val ASPECTO = (1920f * ANCHO_UTIL) / 1080f

    /** Hueco de pantalla, en fracciones del recorte (izq, arriba, der, abajo). */
    data class Pantalla(val izq: Float, val arriba: Float, val der: Float, val abajo: Float)

    private val PANTALLAS: Map<String, Pantalla> = mapOf(
        "3do" to Pantalla(0.2292f, 0.3111f, 0.9188f, 0.7315f),
        "amiga" to Pantalla(0.2386f, 0.3315f, 0.9156f, 0.7593f),
        "amigacd32" to Pantalla(0.2292f, 0.3111f, 0.9198f, 0.7333f),
        "amstradcpc" to Pantalla(0.2302f, 0.3250f, 0.9198f, 0.7694f),
        "androidapps" to Pantalla(0.2156f, 0.3565f, 0.9282f, 0.7093f),
        "androidgames" to Pantalla(0.2156f, 0.3565f, 0.9282f, 0.7093f),
        "apple2" to Pantalla(0.2438f, 0.3259f, 0.9146f, 0.7472f),
        "arcade" to Pantalla(0.2364f, 0.3250f, 0.9146f, 0.7574f),
        "atari2600" to Pantalla(0.2292f, 0.3111f, 0.9198f, 0.7398f),
        "atari5200" to Pantalla(0.2396f, 0.3250f, 0.9094f, 0.7111f),
        "atari7800" to Pantalla(0.2292f, 0.3111f, 0.9198f, 0.7398f),
        "atari800" to Pantalla(0.2678f, 0.3546f, 0.8750f, 0.7231f),
        "atarijaguar" to Pantalla(0.2292f, 0.3111f, 0.9198f, 0.7333f),
        "atarist" to Pantalla(0.2292f, 0.3111f, 0.9198f, 0.7398f),
        "atomiswave" to Pantalla(0.2282f, 0.3074f, 0.9218f, 0.7722f),
        "auto-allgames" to Pantalla(0.2292f, 0.3111f, 0.9188f, 0.7556f),
        "auto-favorites" to Pantalla(0.2292f, 0.3111f, 0.9188f, 0.7556f),
        "auto-lastplayed" to Pantalla(0.2292f, 0.3111f, 0.9188f, 0.7556f),
        "bbcmicro" to Pantalla(0.2322f, 0.3361f, 0.9188f, 0.7130f),
        "c64" to Pantalla(0.2376f, 0.3278f, 0.9156f, 0.7546f),
        "colecovision" to Pantalla(0.2292f, 0.3111f, 0.9188f, 0.7426f),
        "cps" to Pantalla(0.2364f, 0.3250f, 0.9146f, 0.7574f),
        "cps1" to Pantalla(0.2364f, 0.3250f, 0.9146f, 0.7574f),
        "cps2" to Pantalla(0.2364f, 0.3250f, 0.9146f, 0.7574f),
        "cps3" to Pantalla(0.2364f, 0.3250f, 0.9146f, 0.7574f),
        "custom-collections" to Pantalla(0.2292f, 0.3111f, 0.9188f, 0.7556f),
        "daphne" to Pantalla(0.2364f, 0.3250f, 0.9146f, 0.7574f),
        "dos" to Pantalla(0.2396f, 0.3324f, 0.9114f, 0.7639f),
        "dreamcast" to Pantalla(0.2292f, 0.3111f, 0.9198f, 0.7398f),
        "fba" to Pantalla(0.2364f, 0.3250f, 0.9146f, 0.7574f),
        "fbneo" to Pantalla(0.2364f, 0.3250f, 0.9146f, 0.7574f),
        "gamegear" to Pantalla(0.3792f, 0.3528f, 0.7896f, 0.6185f),
        "gba" to Pantalla(0.2020f, 0.3074f, 0.9396f, 0.7741f),
        "gbc" to Pantalla(0.2958f, 0.3120f, 0.8614f, 0.7620f),
        "gc" to Pantalla(0.2292f, 0.3111f, 0.9198f, 0.7398f),
        "genesis" to Pantalla(0.2292f, 0.3111f, 0.9198f, 0.7398f),
        "gx4000" to Pantalla(0.2292f, 0.3111f, 0.9198f, 0.7398f),
        "mame-advmame" to Pantalla(0.2364f, 0.3250f, 0.9146f, 0.7574f),
        "mame" to Pantalla(0.2364f, 0.3250f, 0.9146f, 0.7574f),
        "mastersystem" to Pantalla(0.2292f, 0.3111f, 0.9198f, 0.7398f),
        "megadrive" to Pantalla(0.2292f, 0.3111f, 0.9198f, 0.7398f),
        "model2" to Pantalla(0.2364f, 0.3250f, 0.9146f, 0.7574f),
        "model3" to Pantalla(0.2364f, 0.3250f, 0.9146f, 0.7574f),
        "msx" to Pantalla(0.2292f, 0.3111f, 0.9198f, 0.7398f),
        "n3ds" to Pantalla(0.2364f, 0.3528f, 0.9406f, 0.7250f),
        "n64" to Pantalla(0.2292f, 0.3111f, 0.9198f, 0.7398f),
        "naomi" to Pantalla(0.2094f, 0.3037f, 0.9406f, 0.7731f),
        "naomi2" to Pantalla(0.2094f, 0.3037f, 0.9406f, 0.7731f),
        "naomigd" to Pantalla(0.2094f, 0.3037f, 0.9406f, 0.7731f),
        "nds" to Pantalla(0.2532f, 0.3417f, 0.8938f, 0.7417f),
        "neogeo" to Pantalla(0.2292f, 0.3111f, 0.9198f, 0.7398f),
        "neogeocd" to Pantalla(0.2292f, 0.3111f, 0.9198f, 0.7398f),
        "nes" to Pantalla(0.2292f, 0.3111f, 0.9198f, 0.7398f),
        "odyssey2" to Pantalla(0.2438f, 0.3343f, 0.9114f, 0.7639f),
        "openbor" to Pantalla(0.2292f, 0.3111f, 0.9198f, 0.7398f),
        "pcengine" to Pantalla(0.2292f, 0.3111f, 0.9198f, 0.7398f),
        "pcenginecd" to Pantalla(0.2292f, 0.3111f, 0.9198f, 0.7398f),
        "ps2" to Pantalla(0.2292f, 0.3111f, 0.9198f, 0.7398f),
        "ps3" to Pantalla(0.2292f, 0.3111f, 0.9198f, 0.7398f),
        "psp" to Pantalla(0.2260f, 0.3630f, 0.9218f, 0.7093f),
        "psvita" to Pantalla(0.2208f, 0.3667f, 0.9166f, 0.7056f),
        "psx" to Pantalla(0.2292f, 0.3111f, 0.9198f, 0.7398f),
        "saturn" to Pantalla(0.2292f, 0.3111f, 0.9198f, 0.7398f),
        "scummvm" to Pantalla(0.2552f, 0.3176f, 0.8968f, 0.7704f),
        "sega32xna" to Pantalla(0.2292f, 0.3111f, 0.9198f, 0.7398f),
        "segacd" to Pantalla(0.2292f, 0.3111f, 0.9198f, 0.7398f),
        "sfc" to Pantalla(0.2292f, 0.3111f, 0.9198f, 0.7398f),
        "sg-1000" to Pantalla(0.2354f, 0.3269f, 0.9198f, 0.7713f),
        "snesna" to Pantalla(0.2292f, 0.3111f, 0.9198f, 0.7398f),
        "switch" to Pantalla(0.2156f, 0.3583f, 0.9344f, 0.7185f),
        "tg-cd" to Pantalla(0.2292f, 0.3102f, 0.9198f, 0.7398f),
        "tg16" to Pantalla(0.2292f, 0.3102f, 0.9198f, 0.7398f),
        "vectrex" to Pantalla(0.3646f, 0.3074f, 0.7916f, 0.7731f),
        "wii" to Pantalla(0.2292f, 0.3111f, 0.9198f, 0.7398f),
        "wiiu" to Pantalla(0.2260f, 0.3694f, 0.9178f, 0.7102f),
        "wonderswan" to Pantalla(0.2688f, 0.3630f, 0.8834f, 0.7111f),
        "wonderswancolor" to Pantalla(0.2770f, 0.3639f, 0.8916f, 0.7130f),
        "xbox360" to Pantalla(0.2292f, 0.3111f, 0.9198f, 0.7398f),
        "zxspectrum" to Pantalla(0.2292f, 0.3111f, 0.9198f, 0.7398f),
        "gb" to Pantalla(0.2760f, 0.3000f, 0.8680f, 0.7640f)
    )

    private val ALIAS = mapOf(
        "snes" to "snesna", "genesis" to "megadrive", "md" to "megadrive", "sms" to "mastersystem",
        "dc" to "dreamcast", "3ds" to "n3ds", "32x" to "sega32xna", "sega32x" to "sega32xna",
        "vita" to "psvita", "sg1000" to "sg-1000", "tgcd" to "tg-cd", "pce" to "pcengine",
        "gameboy" to "gb", "gameboycolor" to "gbc", "gameboyadvance" to "gba",
        "ps1" to "psx", "playstation" to "psx", "ngc" to "gc", "gamecube" to "gc",
        "mame2003" to "mame", "mame2003_plus" to "mame", "mame2010" to "mame", "mame2015" to "mame",
        "neogeoaes" to "neogeo", "mvs" to "neogeo", "aes" to "neogeo", "cps" to "cps1",
        "recent" to "auto-lastplayed", "favorites" to "auto-favorites", "all" to "auto-allgames",
        "android" to "androidgames", "ports" to "dos", "pc" to "dos"
    )

    /** Nombre del fichero (sin .webp) para una plataforma, o null si no hay mueble. */
    fun nombrePara(platformId: String): String? {
        val p = platformId.trim().lowercase()
        val n = if (p in PANTALLAS) p else ALIAS[p] ?: return null
        return if (n in PANTALLAS) n else null
    }

    fun pantallaDe(nombre: String): Pantalla = PANTALLAS.getValue(nombre)
}

/**
 * Pinta [contenido] dentro de la pantalla del mueble de [nombre] y el mueble
 * encima. El conjunto mantiene la proporcion del recorte y se centra en el
 * espacio disponible.
 */
@Composable
fun CabinetFrame(nombre: String, modifier: Modifier = Modifier, contenido: @Composable () -> Unit) {
    val context = LocalContext.current
    val imagen by produceState<ImageBitmap?>(initialValue = null, nombre) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                context.assets.open("contentimg/cabinets/$nombre.webp").use { input ->
                    // A mitad de resolucion: 960x540 bastan para un panel y son
                    // 4 MB menos por mueble en una consola con 4-6 GB.
                    val opts = BitmapFactory.Options().apply { inSampleSize = 2 }
                    BitmapFactory.decodeStream(input, null, opts)?.asImageBitmap()
                }
            }.getOrNull()
        }
    }
    val pantalla = CabinetFrames.pantallaDe(nombre)

    Box(modifier, contentAlignment = Alignment.Center) {
        BoxWithConstraints(Modifier.aspectRatio(CabinetFrames.ASPECTO)) {
            val ancho = maxWidth
            val alto = maxHeight
            // Primero la pantalla (debajo), luego el mueble (encima).
            Box(
                Modifier
                    .offset(x = ancho * pantalla.izq, y = alto * pantalla.arriba)
                    .size(width = ancho * (pantalla.der - pantalla.izq), height = alto * (pantalla.abajo - pantalla.arriba))
            ) { contenido() }
            val img = imagen
            if (img != null) {
                Canvas(Modifier.fillMaxSize()) {
                    val srcW = (img.width * 0.5f).toInt()
                    drawImage(
                        image = img,
                        srcOffset = IntOffset(0, 0),
                        srcSize = IntSize(srcW, img.height),
                        dstSize = IntSize(size.width.toInt(), size.height.toInt())
                    )
                }
            }
        }
    }
}
