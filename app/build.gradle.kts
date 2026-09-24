import java.io.File
import java.util.Properties

val localProperties = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) load(f.inputStream())
}

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.services)
    alias(libs.plugins.firebase.crashlytics)
}

/**
 * Lee un secreto de `local.properties` (ignorado por git) y, si no esta ahi, de
 * una variable de entorno. Asi la misma configuracion sirve en tu equipo y en
 * cualquier CI sin escribir contrasenas en ningun fichero.
 */
fun secret(propertyKey: String, envKey: String): String? =
    localProperties.getProperty(propertyKey)?.takeIf { it.isNotBlank() }
        ?: System.getenv(envKey)?.takeIf { it.isNotBlank() }

// ── CLAVE DE FIRMA ──────────────────────────────────────────────────────────
// Se usa la MISMA clave con la que ya se firmaba desde el asistente de Android
// Studio ("Generate Signed Bundle / APK"), que es la clave de subida registrada
// en Google Play. Cambiarla haria que Play rechazara la actualizacion.
//
// La ruta y el alias van en local.properties; las contrasenas pueden ir ahi o en
// las variables de entorno SPECCY_KEYSTORE_PASSWORD y SPECCY_KEY_PASSWORD.
val ksPath     = secret("speccy.keystore.path", "SPECCY_KEYSTORE_PATH")
val ksFile     = ksPath?.let { File(it) }?.takeIf { it.isFile }
val ksPassword = secret("speccy.keystore.password", "SPECCY_KEYSTORE_PASSWORD")
val ksAlias    = secret("speccy.key.alias", "SPECCY_KEY_ALIAS")
// Es habitual que la contrasena de la clave sea la misma que la del almacen.
val ksKeyPassword = secret("speccy.key.password", "SPECCY_KEY_PASSWORD") ?: ksPassword

val hasReleaseKeystore = ksFile != null && !ksPassword.isNullOrBlank() &&
    !ksAlias.isNullOrBlank() && !ksKeyPassword.isNullOrBlank()

// Diagnostico util: dice EXACTAMENTE que falta, sin filtrar ninguna contrasena.
val signingDiagnosis: String = when {
    hasReleaseKeystore -> "OK"
    ksPath == null -> "falta speccy.keystore.path en local.properties"
    ksFile == null -> "no existe el almacen en: $ksPath"
    ksAlias.isNullOrBlank() -> "falta speccy.key.alias"
    ksPassword.isNullOrBlank() -> "falta speccy.keystore.password (o \$SPECCY_KEYSTORE_PASSWORD)"
    else -> "falta speccy.key.password (o \$SPECCY_KEY_PASSWORD)"
}

android {
    // Play exige API 36 para apps nuevas y para TODA actualización desde 2026.
    compileSdk = 36
    namespace = "com.generacionarcade.speccyos"

    defaultConfig {
        applicationId = "com.generacionarcade.speccyos"
        minSdk = 24
        targetSdk = 36
        versionCode = 52
        versionName = "1.2.3"

        // Credenciales ScreenScraper — leídas desde local.properties (no versionado).
        // OJO: buildConfigField compila el literal dentro de BuildConfig.class y R8 no
        // lo oculta. Para producción real, mover la firma de peticiones a una Cloud
        // Function propia y dejar aquí sólo un identificador público.
        buildConfigField("String", "SS_DEV_ID",         "\"${localProperties["screenscraper.dev_id"] ?: ""}\"")
        buildConfigField("String", "SS_DEV_PASSWORD",   "\"${localProperties["screenscraper.dev_password"] ?: ""}\"")
        buildConfigField("String", "SS_DEBUG_PASSWORD", "\"${localProperties["screenscraper.debug_password"] ?: ""}\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"


        vectorDrawables {
            useSupportLibrary = true
        }
    }

    // ── FIRMA DE RELEASE ──────────────────────────────────────────────────────
    // Ver el bloque de configuracion al principio del fichero. Nunca se cae a la
    // clave de debug: si falta algo, el build de release falla con un mensaje que
    // dice exactamente que falta (ver la tarea de comprobacion al final).
    signingConfigs {
        if (hasReleaseKeystore) {
            create("release") {
                storeFile = ksFile
                storePassword = ksPassword
                keyAlias = ksAlias
                keyPassword = ksKeyPassword
                // v1 (JAR) sigue haciendo falta para minSdk 24; v2/v3/v4 son las
                // modernas y v4 habilita la instalacion incremental por ADB.
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
                enableV4Signing = true
            }
        }
    }

    flavorDimensions += "distribution"

    productFlavors {
        // VERSIÓN COMPLETA (Google Play)
        create("playstore") {
            dimension = "distribution"
            versionNameSuffix = "-PLAYSTORE"
            buildConfigField("boolean", "IS_FULL_VERSION", "true")
            // El AccessibilityService NO se declara en este flavor: la politica de
            // Play lo restringe. Solo esta en src/systemos/AndroidManifest.xml.
            // El ajuste privilegiado (root / Shizuku) SI se permite aqui: no lo
            // prohibe ninguna politica de Play y es opcional para el usuario.
            buildConfigField("boolean", "ALLOW_PRIVILEGED_TUNING", "true")
        }

        // VERSIÓN LIMITADA (web / GitHub)
        create("website") {
            dimension = "distribution"
            applicationIdSuffix = ".web"
            versionNameSuffix = "-WEB"
            buildConfigField("boolean", "IS_FULL_VERSION", "false")
            buildConfigField("boolean", "ALLOW_PRIVILEGED_TUNING", "true")
        }

        // VERSIÓN LAUNCHER / CUSTOM ROM (sideload)
        create("systemos") {
            dimension = "distribution"
            versionNameSuffix = "-SYSTEMOS"
            buildConfigField("boolean", "IS_FULL_VERSION", "true")
            buildConfigField("boolean", "ALLOW_PRIVILEGED_TUNING", "true")
        }
    }

    // Interruptor de diagnóstico. Con -PnoMinify se compila un release SIN R8:
    //
    //     gradlew.bat assemblePlaystoreRelease -PnoMinify
    //
    // Sirve para dos cosas. Primera, separar en un solo intento un fallo de código
    // de un fallo de reglas de ProGuard: si con -PnoMinify arranca y sin él no,
    // el problema es una regla que falta, no la app. Segunda, el logcat sale con
    // los nombres de clase reales en vez de a.b.c, así que se lee sin necesitar el
    // mapping.txt (110 MB). NO subir a Play un APK compilado así.
    val noMinify = project.hasProperty("noMinify")

    buildTypes {
        release {
            isMinifyEnabled = !noMinify
            isShrinkResources = !noMinify

            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")

            if (hasReleaseKeystore) {
                signingConfig = signingConfigs.getByName("release")
            }
            // Sin bloque ndk{}: el proyecto no tiene codigo nativo (no hay
            // src/main/jniLibs ni src/main/cpp), asi que debugSymbolLevel = "FULL"
            // no aporta nada y obliga a AGP a resolver el NDK en bundleRelease.
            // En un equipo sin NDK instalado eso rompe el build de release.
        }
        debug {
            isMinifyEnabled = false
            // OJO: NO poner aqui applicationIdSuffix = ".debug".
            // El plugin google-services exige que el applicationId final exista
            // como client en app/google-services.json, y ese fichero solo tiene
            // dos clientes registrados en la consola de Firebase:
            //     com.generacionarcade.speccyos
            //     com.generacionarcade.speccyos.web
            // Con el sufijo, TODAS las variantes debug de TODOS los flavours
            // fallan con "No matching client found for package name ...debug".
            // Si algun dia se quiere el sufijo, primero hay que dar de alta en
            // Firebase las apps .debug y .web.debug y bajar el JSON de nuevo.
            versionNameSuffix = "-debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
        aidl = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/DEPENDENCIES"
            excludes += "META-INF/LICENSE*"
            excludes += "META-INF/NOTICE*"
            excludes += "META-INF/*.kotlin_module"
            excludes += "**/attach_hotspot_obj*"
            // Los baseline profiles YA NO se excluyen: son la única optimización AOT
            // gratuita y valen un 20-35 % de tiempo de arranque en handhelds de gama baja.
        }
        jniLibs {
            useLegacyPackaging = false
        }
    }

    // NOTA sobre app/schemas y los tests de migracion de Room
    // ------------------------------------------------------------------
    // Room escribe los .json del esquema en app/schemas (ver el bloque ksp{}
    // del final). Para que `MigrationTestHelper` los encuentre en tiempo de
    // ejecucion hay que empaquetarlos como assets del APK de androidTest:
    //
    //     sourceSets {
    //         getByName("androidTest").assets.directories.add("$projectDir/schemas")
    //     }
    //
    // Ahora mismo NO hace falta: en src/androidTest solo esta
    // ExampleInstrumentedTest y no hay ni un test de migracion, asi que lo unico
    // que conseguiamos era meter JSONs muertos en el APK de pruebas.
    // (La version antigua usaba assets.srcDirs(...), que AGP 9 ya marca como
    // deprecada en favor de la coleccion `directories`.)
    // Cuando se escriban los tests de migracion 1->2->3->4, se descomenta.

    lint {
        abortOnError = false
        checkReleaseBuilds = true
        warningsAsErrors = false
    }
}

// exportSchema = true en AppDatabase necesita saber DONDE escribir los .json del
// esquema. Sin esto KSP avisa ("Schema export directory was not provided") y no
// exporta nada, con lo que las migraciones seguirian sin poder testearse.
// OJO: este bloque es la extension del plugin KSP y va en el nivel superior del
// script, NUNCA dentro de android { defaultConfig { } }.
ksp {
    arg("room.schemaLocation", "${projectDir}/schemas")
}

dependencies {
    implementation(libs.material)

    // --- ARRANQUE (Baseline Profiles + App Startup) ---
    implementation("androidx.profileinstaller:profileinstaller:1.4.1")
    implementation("androidx.startup:startup-runtime:1.2.0")

    // --- GOOGLE IN-APP REVIEW ---
    implementation("com.google.android.play:review:2.0.2")
    implementation("com.google.android.play:review-ktx:2.0.2")

    // --- GOOGLE ML KIT (OCR para Traductor Neural) ---
    // La variante *bundled* (com.google.mlkit:text-recognition) empaqueta el modelo
    // y libmlkit_google_ocr_pipeline.so para las 4 ABIs: 38 MB dentro del APK, de los
    // cuales 21 MB son x86/x86_64 que ninguna consola ARM puede ejecutar.
    //
    // playstore -> variante *unbundled*: el modelo lo sirve Google Play Services bajo
    //              demanda. Quita ~38 MB del paquete. Misma API, no cambia el codigo.
    // website / systemos -> se mantiene la *bundled*: son sideload y custom ROM, donde
    //              no se puede dar por supuesto que haya Google Play Services.
    "playstoreImplementation"("com.google.android.gms:play-services-mlkit-text-recognition:19.0.1")
    "websiteImplementation"("com.google.mlkit:text-recognition:16.0.0")
    "systemosImplementation"("com.google.mlkit:text-recognition:16.0.0")

    // --- ROOM (+ Paging) ---
    implementation(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.room.ktx)
    // Debe ir SIEMPRE a la misma version que room-runtime (2.8.4 en libs.versions.toml):
    // room-compiler 2.7+ genera codigo contra un LimitOffsetPagingSource distinto
    // al de 2.6.1, y las @Query que devuelven PagingSource no compilarian.
    implementation("androidx.room:room-paging:2.8.4")
    implementation("androidx.paging:paging-runtime-ktx:3.3.6")
    implementation("androidx.paging:paging-compose:3.3.6")

    // --- RETROFIT/MOSHI ---
    implementation(libs.retrofit.core)
    implementation(libs.retrofit.converter.moshi)
    implementation(libs.moshi.core)
    implementation("com.squareup.moshi:moshi-kotlin:1.15.2")
    ksp(libs.moshi.codegen)

    // --- OKHTTP ---
    implementation("com.squareup.okhttp3:okhttp:5.3.2")
    implementation(libs.okhttp.logging)

    // --- MULTIMEDIA ---
    implementation(libs.coil.compose)
    implementation("io.coil-kt:coil-gif:2.6.0")
    implementation("io.coil-kt:coil-svg:2.6.0")
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)
    implementation("com.google.code.gson:gson:2.10.1")
    implementation(libs.androidx.palette.ktx)

    // --- NAVEGACIÓN Y CICLO DE VIDA ---
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    // collectAsStateWithLifecycle: evita que Room siga emitiendo mientras el
    // usuario está dentro de un emulador durante horas.
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")

    // --- FIREBASE ---
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.vertexai)
    implementation(libs.firebase.crashlytics)
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.config)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.storage)
    // Autenticación anónima: sin esto las reglas de Firestore/Storage tienen que
    // estar abiertas (allow if true) y cualquiera puede volcar los datos.
    // Los artefactos -ktx desaparecieron en el BoM 34: su contenido esta en el
    // modulo principal.
    implementation("com.google.firebase:firebase-auth")
    // App Check con Play Integrity: bloquea peticiones que no vengan de la app real.
    implementation("com.google.firebase:firebase-appcheck-playintegrity")
    implementation("com.google.firebase:firebase-appcheck-debug")

    // --- HARDWARE & PRIVILEGIOS ---
    implementation("com.github.topjohnwu.libsu:core:5.2.2")
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")

    // --- GAMEPADS ---
    implementation("androidx.games:games-controller:2.0.2")

    // --- GOOGLE SERVICES & DRIVE ---
    // play-services-drive:17.0.0 ELIMINADO: descontinuado y sin uso (CloudSaveManager
    // usa google-api-services-drive). Eran varios MB de APK y method count muertos.
    // Play exige 8.0.0+ desde el 31-ago-2026 (por debajo de eso rechaza las
    // actualizaciones). Se sube directamente a la rama 9, que es la que Google
    // recomienda. Requiere targetSdk >= 35: aqui es 36.
    // Google Play Billing eliminado: la app es gratuita, sin compras ni suscripcion.
    // Mantenerlo hacia que Play Console la clasificara como app con compras integradas.
    implementation("com.google.android.gms:play-services-auth:21.3.0")
    implementation("com.google.api-client:google-api-client-android:2.2.0")
    implementation("com.google.apis:google-api-services-drive:v3-rev20230822-2.0.0")

    // --- JETPACK COMPOSE ---
    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.window:window:1.3.0")

    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    // WindowSizeClass: layouts adaptativos en handhelds 16:10, tablets y TV.
    implementation("androidx.compose.material3:material3-window-size-class")

    implementation(libs.androidx.documentfile)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.work.runtime.ktx)
}

/**
 * Si se pide un release sin clave de firma, mejor fallar aqui con un mensaje
 * util que generar un AAB sin firmar (o, peor, firmado con la clave de debug,
 * que Play rechaza y que impide actualizar cualquier APK ya distribuido).
 * Los builds de debug no se ven afectados.
 */
/**
 * COMPROBACION DE FIRMA — EN TIEMPO DE CONFIGURACION.
 *
 * La version anterior lo hacia con `tasks...configureEach { doFirst { } }`, y eso
 * rompe la CACHE DE CONFIGURACION: una lambda declarada en un .kts y usada como
 * accion de tarea arrastra una referencia al propio objeto script, que Gradle no
 * sabe serializar ("cannot serialize Gradle script object references"). El APK se
 * generaba, pero el build terminaba en FAILED al guardar la cache.
 *
 * Mirando `startParameter.taskNames` se consigue lo mismo sin crear ninguna
 * accion de tarea: si se pide un release sin clave, se falla aqui mismo con un
 * mensaje util, en lugar de generar un AAB sin firmar.
 */
val releaseRequested = gradle.startParameter.taskNames.any { name ->
    val n = name.substringAfterLast(':')
    n.contains("Release", ignoreCase = true) &&
        (n.startsWith("assemble", true) || n.startsWith("bundle", true) || n.startsWith("package", true))
}
if (releaseRequested && !hasReleaseKeystore) {
    throw GradleException(
        "\n\nNo hay clave de firma de release configurada: " + signingDiagnosis + "\n" +
        "Anade a local.properties (que NO se versiona):\n" +
        "  speccy.keystore.path=C:/ruta/a/tu/almacen\n" +
        "  speccy.key.alias=key0\n" +
        "  speccy.keystore.password=...\n" +
        "  speccy.key.password=...\n" +
        "Las dos contrasenas pueden ir en su lugar en las variables de entorno\n" +
        "SPECCY_KEYSTORE_PASSWORD y SPECCY_KEY_PASSWORD.\n"
    )
}

// Alias de testing unitario para evitar la ambigüedad de los product flavors.
tasks.register("testDebugUnitTest") {
    dependsOn("testPlaystoreDebugUnitTest", "testWebsiteDebugUnitTest", "testSystemosDebugUnitTest")
    description = "Ejecuta los tests unitarios de debug para todos los sabores."
    group = "verification"
}

tasks.withType<Test>().configureEach {
    failOnNoDiscoveredTests = false
}
