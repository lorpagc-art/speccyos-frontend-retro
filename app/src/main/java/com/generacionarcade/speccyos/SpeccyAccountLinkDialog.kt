package com.generacionarcade.speccyos

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * SpeccyAccountLinkDialog
 * ----------------------------------------------------------------------------
 * Reemplazo seguro de `UltraLoginWebView` + `WebTokenBridge`, que han sido
 * ELIMINADOS del proyecto.
 *
 * POR QUÉ SE ELIMINARON
 * ---------------------
 * Aquel diálogo inyectaba un `@JavascriptInterface` en una página de TERCEROS,
 * con JavaScript activo, `MIXED_CONTENT_ALWAYS_ALLOW`, cookies de terceros y
 * User-Agent falsificado, y **sin validar la URL en ningún punto**: un redirect,
 * un anuncio o un XSS en el sitio heredaba el bridge. El script recorría todos
 * los `<input>` de la página, trataba como token cualquier valor alfanumérico de
 * 20+ caracteres, y guardaba la **contraseña en claro** del usuario.
 *
 * Para Google Play eso es captura de credenciales de un servicio ajeno dentro de
 * una WebView: el patrón que la política de Comportamiento Engañoso trata como
 * phishing, y una de las causas más probables de retirada.
 *
 * QUÉ HACE ESTE EN SU LUGAR
 * -------------------------
 * Abre la página oficial en el navegador del sistema (fuera de nuestro proceso,
 * sin acceso a nada nuestro) y pide al usuario que pegue su **API key** — que
 * RetroAchievements publica en su propio panel de control y que es revocable —
 * en vez de su contraseña. La clave se valida contra la API antes de guardarla.
 */
@Composable
fun SpeccyAccountLinkDialog(
    settingsManager: SettingsManager,
    onDismiss: () -> Unit,
    onSuccess: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val manager = remember { RetroAchievementsManager(context, settingsManager) }

    var user by remember { mutableStateOf(settingsManager.raUsername) }
    var apiKey by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Conectar RetroAchievements") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "SpeccyOS nunca te pide tu contraseña. Copia tu clave de API " +
                        "desde tu panel de RetroAchievements y pégala aquí. Puedes " +
                        "revocarla cuando quieras desde su web.",
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedTextField(
                    value = user,
                    onValueChange = { user = it.trim() },
                    label = { Text("Usuario") },
                    singleLine = true,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                )
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it.trim(); error = null },
                    label = { Text("Clave de API") },
                    singleLine = true,
                    enabled = !busy,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    isError = error != null,
                    supportingText = { error?.let { Text(it) } },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done
                    )
                )
                TextButton(
                    enabled = !busy,
                    onClick = {
                        // Se abre FUERA de la app: el navegador del sistema no
                        // comparte proceso, cookies ni bridge con SpeccyOS.
                        runCatching {
                            context.startActivity(
                                Intent(
                                    Intent.ACTION_VIEW,
                                    Uri.parse("https://retroachievements.org/controlpanel.php")
                                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }
                    }
                ) { Text("Abrir mi panel de RetroAchievements") }

                if (busy) {
                    Spacer(Modifier.height(4.dp))
                    CircularProgressIndicator(Modifier.height(20.dp))
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !busy && user.isNotBlank() && apiKey.isNotBlank(),
                onClick = {
                    busy = true
                    error = null
                    scope.launch {
                        val ok = manager.verifyAndSaveApiKey(user, apiKey)
                        busy = false
                        if (ok) {
                            onSuccess("RetroAchievements conectado como $user")
                            onDismiss()
                        } else {
                            error = "No hemos podido validar esa clave. Revisa el usuario y vuelve a copiarla."
                        }
                    }
                }
            ) { Text("Conectar") }
        },
        dismissButton = {
            TextButton(enabled = !busy, onClick = onDismiss) { Text("Cancelar") }
        }
    )
}
