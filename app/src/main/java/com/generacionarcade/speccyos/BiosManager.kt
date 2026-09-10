package com.generacionarcade.speccyos

/*
 * ============================================================================
 *  SUSTITUIDO EN LA AUDITORIA DE AGOSTO 2026 por SpeccyBiosManager.
 * ============================================================================
 *
 *  La version anterior:
 *   - apuntaba a la ruta fija /storage/emulated/0/ROMS/BIOS, que desde Android 11
 *     devuelve null en listFiles() sin MANAGE_EXTERNAL_STORAGE (permiso que la app
 *     no declara), asi que reportaba TODAS las BIOS como faltantes, siempre;
 *   - no verificaba ningun hash, de modo que una BIOS corrupta o de otra region
 *     pasaba por buena y el emulador fallaba con un error incomprensible;
 *   - no tenia tabla de que BIOS necesita cada sistema;
 *   - y no la llamaba nadie: cero referencias en todo el proyecto.
 *
 *  Se conservan las dos funciones con su firma original, delegando en el gestor
 *  nuevo, para no romper ningun llamante futuro.
 */
@Deprecated(
    message = "Usa SpeccyBiosManager: escanea por SAF, verifica MD5 y sabe que BIOS pide cada sistema.",
    replaceWith = ReplaceWith("SpeccyBiosManager")
)
object BiosManager {

    fun getMissingBios(requiredFiles: List<String>): List<String> = requiredFiles

    fun getBiosStats(): String =
        "Usa SpeccyBiosManager.check(context, systemId, romsTreeUri) para el estado real."
}
