package com.generacionarcade.speccyos;

/**
 * IPerformanceService — Servicio AIDL de control de hardware vía Shizuku.
 *
 * ── MEJORAS v2 ────────────────────────────────────────────────────────────────
 * Añadidos métodos para:
 * - Escritura directa en sysfs de governor/frecuencia para RetroArch
 * - Lectura de temperatura y throttling del SoC
 * - Control de latencia de audio en tiempo real
 * - Consulta de PID del proceso RetroArch (para setpriority / cpuset)
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * NOTA: Los IDs de transacción son fijos y no deben cambiar para mantener
 * compatibilidad con versiones anteriores del servicio vinculado.
 */
interface IPerformanceService {

    // ── Comandos genéricos (existentes, sin cambio de ID) ────────────────
    /** Ejecuta un comando de shell con privilegios Shizuku. */
    void executeCommand(String command) = 1;

    /** Ejecuta una lista de comandos en secuencia. */
    void executeCommands(in List<String> commands) = 2;

    /** Lee el contenido de un nodo sysfs. */
    String readSysfs(String path) = 3;

    /** Termina el servicio. */
    void exit() = 4;

    // ── Nuevos métodos de rendimiento RetroArch ──────────────────────────

    /**
     * Escribe un valor directamente en un nodo sysfs.
     * Útil para governor, freq_min/max, cpuset, etc.
     * @param path  Ruta del nodo (ej: "/sys/devices/system/cpu/cpu0/cpufreq/scaling_governor")
     * @param value Valor a escribir (ej: "performance")
     * @return true si la escritura fue exitosa.
     */
    boolean writeSysfs(String path, String value) = 5;

    /**
     * Devuelve la temperatura del SoC en milligrados (mC).
     * Permite detectar throttling térmico antes de que afecte al juego.
     * @return Temperatura en mC, o -1 si no está disponible.
     */
    int getSocTemperatureMc() = 6;

    /**
     * Busca el PID del proceso RetroArch activo.
     * Permite aplicar renice / cpuset desde SpeccyOS sin root.
     * @param retroArchPkg Nombre del paquete (ej: "com.retroarch.aarch64")
     * @return PID como String, o "" si no está en ejecución.
     */
    String getRetroArchPid(String retroArchPkg) = 7;

    /**
     * Aplica un perfil de CPU/GPU de forma atómica: governor + frecuencias mínima/máxima.
     * Equivale a varias llamadas a writeSysfs pero en una sola transacción IPC.
     * @param cpuGovernor  "performance", "schedutil", "powersave", etc.
     * @param cpuMinFreqKhz Frecuencia mínima en kHz (0 = no cambiar)
     * @param cpuMaxFreqKhz Frecuencia máxima en kHz (0 = no cambiar)
     * @param gpuGovernor  Governor GPU (ej: "msm-adreno-tz", "simple_ondemand")
     */
    void applyPerformanceProfile(
        String cpuGovernor,
        int cpuMinFreqKhz,
        int cpuMaxFreqKhz,
        String gpuGovernor
    ) = 8;

    /**
     * Obtiene las estadísticas de renderizado de un paquete (dumpsys gfxinfo) para calcular FPS y jank.
     * Requiere privilegios Shizuku/ADB.
     */
    String getPackageGfxInfo(String packageName) = 9;

    /**
     * Nombres de los ficheros de un directorio, separados por "\n".
     * Hace falta para mirar la carpeta `system` de RetroArch, que en Android 11+
     * una app normal no puede listar aunque el usuario tenga las BIOS ahi.
     * @return lista separada por saltos de linea, o "" si no se puede leer.
     */
    String listDir(String dir) = 10;

    /**
     * Copia un fichero. Se usa para llevar una BIOS que el usuario tiene en su
     * carpeta de ROMs (o en Descargas) a la carpeta donde el core la busca.
     * @return true si el destino quedo escrito con el mismo tamano.
     */
    boolean copyFile(String src, String dst) = 11;
}
