package com.generacionarcade.speccyos

object Translator {
    
    private val dictionary = mapOf(
        "tab_emulation" to mapOf("es" to "EMULACIÓN", "en" to "EMULATION", "fr" to "ÉMULATION", "de" to "EMULATION", "it" to "EMULAZIONE", "zh" to "模拟"),
        "tab_interface" to mapOf("es" to "INTERFAZ", "en" to "INTERFACE", "fr" to "INTERFACE", "de" to "SCHNITTSTELLE", "it" to "INTERFACCIA", "zh" to "界面"),
        "tab_scraper" to mapOf("es" to "SCRAPER", "en" to "SCRAPER", "fr" to "SCRAPER", "de" to "SCRAPER", "it" to "SCRAPER", "zh" to "刮削器"),
        "tab_achievements" to mapOf("es" to "LOGROS", "en" to "ACHIEVEMENTS", "fr" to "SUCCÈS", "de" to "ERFOLGE", "it" to "OBBIETTIVI", "zh" to "成就"),
        "tab_device" to mapOf("es" to "DISPOSITIVO", "en" to "DEVICE", "fr" to "APPAREIL", "de" to "GERÄT", "it" to "DISPOSITIVO", "zh" to "设备"),
        "tab_controls" to mapOf("es" to "CONTROLES", "en" to "CONTROLS", "fr" to "CONTRÔLES", "de" to "STEUERUNG", "it" to "CONTROLLI", "zh" to "控制"),
        "tab_account" to mapOf("es" to "CUENTA", "en" to "ACCOUNT", "fr" to "COMPTE", "de" to "KONTO", "it" to "ACCOUNT", "zh" to "账户"),
        "tab_store" to mapOf("es" to "PRO STORE", "en" to "PRO STORE", "fr" to "BOUTIQUE PRO", "de" to "PRO STORE", "it" to "PRO STORE", "zh" to "专业商店"),
        
        "btn_play_trivia" to mapOf("es" to "JUGAR TRIVIAL", "en" to "PLAY TRIVIA", "fr" to "JOUER AU TRIVIA", "de" to "TRIVIA SPIELEN", "it" to "GIOCA A TRIVIA", "zh" to "玩问答游戏"),
        "btn_manual" to mapOf("es" to "MANUAL DE USUARIO", "en" to "USER MANUAL", "fr" to "MANUEL UTILISATEUR", "de" to "BENUTZERHANDBUCH", "it" to "MANUALE UTENTE", "zh" to "用户手册"),
        "trivia_correct" to mapOf("es" to "¡CORRECTO!", "en" to "CORRECT!", "fr" to "CORRECT !", "de" to "RICHTIG!", "it" to "ESATTO!", "zh" to "正确！"),
        "trivia_incorrect" to mapOf("es" to "INCORRECTO", "en" to "INCORRECT", "fr" to "INCORRECT", "de" to "FALSCH", "it" to "SBAGLIATO", "zh" to "错误"),
        "trivia_fun_fact" to mapOf("es" to "CURIOSIDAD:", "en" to "FUN FACT:", "fr" to "LE SAVIEZ-VOUS :", "de" to "SCHON GEWUSST:", "it" to "CURIOSITÀ:", "zh" to "有趣的事实："),
        "trivia_next" to mapOf("es" to "SIGUIENTE PREGUNTA", "en" to "NEXT QUESTION", "fr" to "QUESTION SUIVANTE", "de" to "NÄCHSTE FRAGE", "it" to "PROSSIMA DOMANDA", "zh" to "下一题"),
        "trivia_share" to mapOf("es" to "COMPARTIR MI RANGO", "en" to "SHARE MY RANK", "fr" to "PARTAGER MON RANG", "de" to "MEINEN RANG TEILEN", "it" to "CONDIVIDI IL MIO RANGO", "zh" to "分享我的排名"),
        "trivia_again" to mapOf("es" to "JUGAR OTRA VEZ", "en" to "PLAY AGAIN", "fr" to "REJOUER", "de" to "NOCHMAL SPIELEN", "it" to "GIOCA DI NUOVO", "zh" to "再玩一次"),
        
        "ai_forging" to mapOf("es" to "EL ARQUITECTO ESTÁ FORJANDO\nNUEVOS DESAFÍOS EN LA NUBE...", "en" to "THE ARCHITECT IS FORGING\nNEW CHALLENGES IN THE CLOUD...", "fr" to "L'ARCHITECTE FORGE DE\nNOUVEAUX DÉFIS DANS LE CLOUD...", "de" to "DER ARCHITEKT SCHMIEDET\nNEUE HERAUSFORDERUNGEN IN DER CLOUD...", "it" to "L'ARCHITETTO STA FORGIANDO\nNUOVE SFIDE NEL CLOUD...", "zh" to "架构师正在云端\n打造新的挑战..."),
        "ai_placeholder" to mapOf("es" to "Consultar al Arquitecto...", "en" to "Ask the Architect...", "fr" to "Demander à l'Architecte...", "de" to "Frag den Architekten...", "it" to "Chiedi all'Architetto...", "zh" to "询问架构师..."),
        
        "language_section" to mapOf("es" to "IDIOMA DEL SISTEMA / SYSTEM LANGUAGE", "en" to "SYSTEM LANGUAGE", "fr" to "LANGUE DU SYSTÈME", "de" to "SYSTEMSPRACHE", "it" to "LINGUA DI SISTEMA", "zh" to "系统语言"),
        
        "dashboard_apps" to mapOf("es" to "APLICACIONES", "en" to "APPLICATIONS", "fr" to "APPLICATIONS", "de" to "ANWENDUNGEN", "it" to "APPLICAZIONI", "zh" to "应用"),
        "dashboard_back" to mapOf("es" to "VOLVER", "en" to "BACK", "fr" to "RETOUR", "de" to "ZURÜCK", "it" to "INDIETRO", "zh" to "返回")
    )

    fun t(key: String, lang: String): String {
        val entry = dictionary[key]
        return entry?.get(lang) ?: entry?.get("es") ?: key
    }
    
    fun getLanguageName(langCode: String): String {
        return when(langCode) {
            "en" -> "English"
            "fr" -> "Français"
            "de" -> "Deutsch"
            "it" -> "Italiano"
            "zh" -> "中文"
            else -> "Español"
        }
    }
}
