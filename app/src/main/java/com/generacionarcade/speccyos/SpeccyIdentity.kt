/*
 * Speccy OS — frontend retro para Android
 * Copyright (c) 2026 LV-Webstudio · lv-webstudio.com
 * Desarrollado por Speccy81 (LORPAGC) · administracion@lv-webstudio.com
 * Todos los derechos reservados.
 */
package com.generacionarcade.speccyos

import android.content.Context
import android.util.Log
// OJO: los artefactos `firebase-*-ktx` y sus paquetes `.ktx` DESAPARECIERON en el
// BoM 34; su contenido se fusiono en los modulos principales. El resto del
// proyecto ya usa la forma nueva (ver DailyChallenge.kt y AiManager.kt).
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.appCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import com.google.firebase.auth.auth
import com.google.firebase.firestore.firestore
import com.google.firebase.storage.storage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * SpeccyIdentity
 * ----------------------------------------------------------------------------
 * Identidad de usuario en la nube.
 *
 * EL PROBLEMA QUE RESUELVE
 * ------------------------
 * El proyecto no tenía NINGUNA referencia a FirebaseAuth. El identificador de
 * usuario era su CORREO ELECTRÓNICO, usado como id de documento
 * (`users/{email}`) y como prefijo de ruta en Storage (`users/{email}/saves/`).
 *
 * Para que eso funcionase, las reglas tenían que ser `allow read, write: if true`.
 * Consecuencia real: cualquiera podía volcar la lista completa de correos de los
 * usuarios, leer y borrar sus partidas, y falsificar el ranking. Es una brecha de
 * datos personales notificable bajo RGPD.
 *
 * Ahora todo se indexa por UID de Firebase Auth (anónimo por defecto, vinculable
 * a Google después) y las reglas pueden exigir `request.auth.uid == uid`.
 *
 * Añade además:
 *  - App Check con Play Integrity: bloquea peticiones que no vengan de la app real.
 *  - Borrado de cuenta dentro de la app, que Play exige para toda app con cuentas
 *    y que hoy no existía (logout() sólo limpiaba SharedPreferences y dejaba los
 *    documentos y ficheros en la nube indefinidamente).
 */
object SpeccyIdentity {

    private const val TAG = "SpeccyIdentity"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _uid = MutableStateFlow<String?>(null)
    val uid: StateFlow<String?> = _uid.asStateFlow()

    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    @Volatile private var initialized = false

    /** Se llama una sola vez desde SpeccyApplication.onCreate(). No bloquea. */
    fun initialize(context: Context) {
        if (initialized) return
        initialized = true

        runCatching { FirebaseApp.initializeApp(context) }

        // App Check ANTES de cualquier lectura/escritura, para que los tokens ya
        // viajen en la primera petición.
        runCatching {
            Firebase.appCheck.installAppCheckProviderFactory(
                if (BuildConfig.DEBUG) DebugAppCheckProviderFactory.getInstance()
                else PlayIntegrityAppCheckProviderFactory.getInstance()
            )
        }.onFailure { Log.w(TAG, "App Check no disponible: ${it.message}") }

        scope.launch {
            val id = ensureSignedIn()
            _uid.value = id
            _ready.value = true
            Log.i(TAG, if (id != null) "Sesión lista (uid anónimo)." else "Sin sesión: la nube queda deshabilitada.")
        }
    }

    /**
     * Devuelve el UID, iniciando sesión anónima si hace falta.
     * Nunca lanza: si Firebase no está disponible devuelve null y el llamante
     * simplemente no sincroniza.
     */
    suspend fun ensureSignedIn(): String? = withContext(Dispatchers.IO) {
        runCatching {
            Firebase.auth.currentUser?.uid
                ?: Firebase.auth.signInAnonymously().await().user?.uid
        }.getOrElse {
            Log.e(TAG, "No se pudo iniciar sesión anónima", it)
            null
        }
    }

    /** UID ya disponible, sin esperar. Para llamadas síncronas de UI. */
    fun uidOrNull(): String? = _uid.value ?: runCatching { Firebase.auth.currentUser?.uid }.getOrNull()

    /** ¿La cuenta actual está vinculada a un proveedor real (Google) o es anónima? */
    fun isAnonymous(): Boolean = runCatching { Firebase.auth.currentUser?.isAnonymous ?: true }.getOrDefault(true)

    /**
     * BORRADO DE CUENTA (requisito de Google Play).
     * Elimina el documento del usuario y todo su árbol de partidas antes de borrar
     * la propia credencial. Devuelve true sólo si todo el proceso se completó.
     */
    suspend fun deleteAccountAndData(context: Context): Boolean = withContext(Dispatchers.IO) {
        val id = uidOrNull() ?: return@withContext false
        var ok = true

        // 1. Ficheros de partidas en Storage
        runCatching {
            val root = Firebase.storage.reference.child("users/$id")
            deleteRecursive(root)
        }.onFailure { ok = false; Log.e(TAG, "Error borrando Storage", it) }

        // 2. Documentos de Firestore (config + juegos)
        runCatching {
            val userDoc = Firebase.firestore.collection("users").document(id)
            listOf("config", "games").forEach { sub ->
                userDoc.collection(sub).get().await().documents.forEach { it.reference.delete().await() }
            }
            userDoc.delete().await()
        }.onFailure { ok = false; Log.e(TAG, "Error borrando Firestore", it) }

        // 3. Preferencias locales
        runCatching {
            listOf("user_status_prefs", "speccy_settings", "SpeccyPrefs").forEach { name ->
                context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().apply()
            }
        }

        // 4. La credencial en sí
        runCatching { Firebase.auth.currentUser?.delete()?.await() }
            .onFailure { ok = false; Log.e(TAG, "Error borrando la cuenta", it) }

        _uid.value = null
        ok
    }

    private suspend fun deleteRecursive(ref: com.google.firebase.storage.StorageReference) {
        val listing = ref.listAll().await()
        listing.items.forEach { it.delete().await() }
        listing.prefixes.forEach { deleteRecursive(it) }
    }
}
