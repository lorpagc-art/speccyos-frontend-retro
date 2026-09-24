/*
 * Speccy OS — frontend retro para Android
 * Copyright (c) 2026 LV-Webstudio · lv-webstudio.com
 * Desarrollado por Speccy81 (LORPAGC) · administracion@lv-webstudio.com
 * Todos los derechos reservados.
 */
package com.generacionarcade.speccyos

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import java.net.ServerSocket

/**
 * NetplayLobbyManager — Descubrimiento y emparejamiento automático por Wi-Fi.
 *
 * Utiliza Network Service Discovery (NSD) de Android para publicar partidas activas
 * de Speccy OS en la red local y permitir el descubrimiento mutuo sin configuraciones
 * complejas de IP.
 */
object NetplayLobbyManager {
    private const val TAG = "SpeccyNetplay"
    private const val SERVICE_TYPE = "_speccyos-play._tcp."
    private const val SERVICE_PREFIX = "SpeccyOS"

    private var nsdManager: NsdManager? = null
    private var registrationListener: NsdManager.RegistrationListener? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null

    var registeredServiceName: String? = null
        private set

    data class NetplayHost(
        val serviceName: String,
        val platformId: String,
        val gameTitle: String,
        val hostIp: String,
        val port: Int
    )

    /**
     * Publica una partida multijugador local en la red Wi-Fi.
     */
    fun registerGameHost(context: Context, platformId: String, gameTitle: String, port: Int = 55435) {
        try {
            unregisterGameHost() // Limpiar previo si existiera

            nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager

            val localPort = findFreePort(port)
            // Codificamos los datos en el nombre del servicio para máxima compatibilidad con API < 28
            val serviceName = "$SERVICE_PREFIX|$platformId|$gameTitle"

            val serviceInfo = NsdServiceInfo().apply {
                this.serviceName = serviceName
                this.serviceType = SERVICE_TYPE
                this.port = localPort
            }

            registrationListener = object : NsdManager.RegistrationListener {
                override fun onServiceRegistered(info: NsdServiceInfo) {
                    registeredServiceName = info.serviceName
                    Log.i(TAG, "✅ Partida registrada localmente: $registeredServiceName en puerto ${info.port}")
                }

                override fun onRegistrationFailed(info: NsdServiceInfo, errorCode: Int) {
                    Log.e(TAG, "❌ Fallo al registrar partida local: código de error $errorCode")
                }

                override fun onServiceUnregistered(info: NsdServiceInfo) {
                    Log.i(TAG, "Partida cancelada localmente: ${info.serviceName}")
                    registeredServiceName = null
                }

                override fun onUnregistrationFailed(info: NsdServiceInfo, errorCode: Int) {
                    Log.e(TAG, "❌ Fallo al cancelar partida local: código de error $errorCode")
                }
            }

            nsdManager?.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener)
        } catch (e: Exception) {
            Log.e(TAG, "Error registrando host Netplay", e)
        }
    }

    /**
     * Detiene la publicación de la partida local.
     */
    fun unregisterGameHost() {
        try {
            registrationListener?.let {
                nsdManager?.unregisterService(it)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error cancelando registro de servicio", e)
        } finally {
            registrationListener = null
            registeredServiceName = null
        }
    }

    /**
     * Escanea activamente la red Wi-Fi buscando partidas de Speccy OS.
     */
    fun startDiscovery(context: Context, onHostFound: (NetplayHost) -> Unit, onHostLost: (String) -> Unit) {
        try {
            stopDiscovery() // Detener búsquedas activas previas

            val manager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
            nsdManager = manager

            discoveryListener = object : NsdManager.DiscoveryListener {
                override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                    Log.e(TAG, "❌ Fallo al iniciar escaneo de red: $errorCode")
                    stopDiscovery()
                }

                override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                    Log.e(TAG, "❌ Fallo al detener escaneo de red: $errorCode")
                    stopDiscovery()
                }

                override fun onDiscoveryStarted(serviceType: String) {
                    Log.i(TAG, "📡 Escaneo de partidas multijugador iniciado.")
                }

                override fun onDiscoveryStopped(serviceType: String) {
                    Log.i(TAG, "Escaneo de partidas multijugador detenido.")
                }

                override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                    Log.d(TAG, "Partida detectada en red: ${serviceInfo.serviceName}")
                    if (serviceInfo.serviceType == SERVICE_TYPE && serviceInfo.serviceName.startsWith(SERVICE_PREFIX)) {
                        manager.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                            override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) {
                                Log.e(TAG, "❌ Fallo al resolver IP para: ${info.serviceName} (código $errorCode)")
                            }

                            override fun onServiceResolved(info: NsdServiceInfo) {
                                val name = info.serviceName
                                val parts = name.split("|")
                                if (parts.size >= 3) {
                                    val platform = parts[1]
                                    val title = parts[2]
                                    val hostAddress = info.host?.hostAddress ?: ""
                                    if (hostAddress.isNotEmpty()) {
                                        val host = NetplayHost(
                                            serviceName = name,
                                            platformId = platform,
                                            gameTitle = title,
                                            hostIp = hostAddress,
                                            port = info.port
                                        )
                                        Log.i(TAG, "✅ Partida resuelta: $title ($platform) en $hostAddress:${info.port}")
                                        onHostFound(host)
                                    }
                                }
                            }
                        })
                    }
                }

                override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                    Log.i(TAG, "Partida perdida: ${serviceInfo.serviceName}")
                    if (serviceInfo.serviceName.startsWith(SERVICE_PREFIX)) {
                        onHostLost(serviceInfo.serviceName)
                    }
                }
            }

            manager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
        } catch (e: Exception) {
            Log.e(TAG, "Error iniciando descubrimiento", e)
        }
    }

    /**
     * Detiene la escucha de la red local.
     */
    fun stopDiscovery() {
        try {
            discoveryListener?.let {
                nsdManager?.stopServiceDiscovery(it)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error deteniendo descubrimiento", e)
        } finally {
            discoveryListener = null
        }
    }

    private fun findFreePort(basePort: Int): Int {
        return try {
            ServerSocket(0).use { it.localPort }
        } catch (e: Exception) {
            basePort
        }
    }
}
