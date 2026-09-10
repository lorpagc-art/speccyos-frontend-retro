# REGLAS PROGUARD PARA SPECCY OS

# Proteger WorkManager (Corrección de error OverwritingInputMerger)
-keep class androidx.work.multiprocess.** { *; }
-keep class androidx.work.impl.workers.** { *; }
-keep class androidx.work.impl.background.** { *; }
-keep class androidx.work.WorkerParameters { *; }
-keep class androidx.work.Data { *; }
-keep class * extends androidx.work.InputMerger { *; }
-keep class * extends androidx.work.ListenableWorker { *; }

# Proteger ROOM (Base de datos)
-keep class androidx.room.paging.** { *; }
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# Proteger RETROFIT & OKHTTP
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepattributes RuntimeInvisibleAnnotations, RuntimeInvisibleParameterAnnotations
-dontwarn okhttp3.**
-dontwarn retrofit2.**
-dontwarn javax.annotation.**
-keep class retrofit2.** { *; }
-keep class okhttp3.** { *; }

# Proteger MOSHI (JSON)
-keep class com.squareup.moshi.** { *; }
-keep @com.squareup.moshi.JsonQualifier interface *
-keepclassmembers class * {
    @com.squareup.moshi.Json *;
}

# Proteger GOOGLE DRIVE & API CLIENT
-keep class com.google.api.** { *; }
-keep class com.google.cloud.** { *; }
-dontwarn com.google.api.**
-dontwarn com.google.common.**
-dontwarn org.apache.http.**
-dontwarn com.google.j2objc.**
-dontwarn javax.annotation.**

# Proteger FIREBASE & VERTEX AI
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }

# Proteger LIBSU & SHIZUKU (Hardware)
#
# OJO: estas dos reglas estaban escritas con el IDENTIFICADOR DE MAVEN en lugar
# de con el PAQUETE JAVA, así que no protegían absolutamente nada:
#
#   dependencia            com.github.topjohnwu.libsu:core   -> paquete com.topjohnwu.superuser
#   dependencia            dev.rikka.shizuku:api / :provider -> paquete rikka.shizuku
#
# Comprobado en app/build/outputs/mapping/playstoreRelease/usage.txt: R8 eliminó
# 46 clases de cada uno, ShizukuProvider y ShizukuBinderWrapper incluidos. Ambas
# bibliotecas se usan por reflexión y a través de AIDL, que es justo lo que R8 no
# puede ver.
-keep class com.topjohnwu.superuser.** { *; }
-keep class com.topjohnwu.superuser.internal.** { *; }
-dontwarn com.topjohnwu.superuser.**

-keep class rikka.shizuku.** { *; }
-keep class rikka.sui.** { *; }
-keep class moe.shizuku.** { *; }
-keep interface moe.shizuku.server.IShizukuService { *; }
-dontwarn rikka.shizuku.**
-dontwarn moe.shizuku.**

# androidx.startup instancia los Initializer por NOMBRE desde el <meta-data> del
# InitializationProvider. Si R8 borra uno, el proceso muere antes incluso de
# llegar a Application.onCreate y no se ve absolutamente nada en pantalla.
-keep class * extends androidx.startup.Initializer { *; }
-keep class androidx.startup.InitializationProvider { *; }

# Proteger los modelos de datos de tu app
-keep class com.generacionarcade.speccyos.Game { *; }
-keep class com.generacionarcade.speccyos.data.models.** { *; }
-keep class com.generacionarcade.speccyos.models.** { *; }
-keep class com.generacionarcade.speccyos.network.** { *; }

# Evitar que se borren los recursos de Compose
-keepclassmembers class androidx.compose.ui.platform.AndroidComposeView {
    *** onCheckIsFullEditor();
}

# ─────────────────────────────────────────────────────────────────────────────
# AÑADIDO EN LA AUDITORÍA DE AGOSTO 2026
# ─────────────────────────────────────────────────────────────────────────────

# Firebase Auth / App Check (reflexión en la inicialización de componentes)
-keep class com.google.firebase.auth.** { *; }
-keep class com.google.firebase.appcheck.** { *; }
-dontwarn com.google.firebase.appcheck.**

# Modelos que Firestore serializa por reflexión: sin esto los toObject() de
# SystemConfig / GameProgress devuelven null en release y la nube "no funciona"
# sólo en el APK firmado.
-keepclassmembers class com.generacionarcade.speccyos.SystemConfig { *; }
-keepclassmembers class com.generacionarcade.speccyos.GameProgress { *; }
-keepclassmembers class com.generacionarcade.speccyos.BugReport { *; }
-keep class com.generacionarcade.speccyos.SpeccyCloudModel { *; }

# Room: los DAO y entidades generados no deben renombrarse
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keepclassmembers class * { @androidx.room.* <methods>; }

# AIDL del servicio privilegiado: el Stub se resuelve por nombre
-keep class com.generacionarcade.speccyos.IPerformanceService { *; }
-keep class com.generacionarcade.speccyos.IPerformanceService$* { *; }
-keep class com.generacionarcade.speccyos.PerformanceService { *; }

# Catálogo de hardware y motor de rendimiento
-keep class com.generacionarcade.speccyos.SpeccyHardwareRegistry { *; }
-keep class com.generacionarcade.speccyos.SpeccyHardwareRegistry$* { *; }

# Compose: mantener los nombres de los @Composable de nivel superior facilita
# leer los stack traces de Crashlytics sin desofuscar.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Eliminar el logging de depuración del binario de release. HardwareControlManagerBeta
# registraba los comandos privilegiados y ScraperWorker los títulos y rutas del
# usuario: en release no deben quedar ni en logcat.
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static int println(...);
}
