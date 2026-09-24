/*
 * Speccy OS — frontend retro para Android
 * Copyright (c) 2026 LV-Webstudio · lv-webstudio.com
 * Desarrollado por Speccy81 (LORPAGC) · administracion@lv-webstudio.com
 * Todos los derechos reservados.
 */
package com.generacionarcade.speccyos

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/**
 * SpeccyAccountDeletion
 * ----------------------------------------------------------------------------
 * Borrado de cuenta DENTRO de la app.
 *
 * Google Play lo exige para toda app que permita crear una cuenta, y SpeccyOS no
 * lo tenía: `UserStatusManager.logout()` sólo limpiaba SharedPreferences, así que
 * los documentos de Firestore y los ficheros de Storage del usuario se quedaban
 * en la nube indefinidamente. Es motivo de rechazo directo en revisión.
 *
 * Se declara aparte para poder insertarlo en `AccountSettingsContent` con una
 * sola línea y para que la lógica de borrado quede en un único sitio auditable
 * (SpeccyIdentity.deleteAccountAndData).
 */
@Composable
fun DeleteAccountSection(
    settingsManager: SettingsManager,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var confirming by remember { mutableStateOf(false) }
    var working by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<Boolean?>(null) }

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            stringResource(R.string.account_delete_title),
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.account_delete_summary),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp
        )
        Spacer(Modifier.height(12.dp))

        Button(
            onClick = { confirming = true },
            enabled = !working,
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8C1F14)),
            modifier = Modifier.fillMaxWidth().height(48.dp)   // 48dp: objetivo táctil mínimo
        ) {
            if (working) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    color = MaterialTheme.colorScheme.onSurface,
                    strokeWidth = 2.dp
                )
                Spacer(Modifier.width(10.dp))
                Text(stringResource(R.string.account_delete_working), color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp)
            } else {
                Icon(Icons.Default.DeleteForever, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.account_delete_cta), color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp)
            }
        }

        result?.let { ok ->
            Spacer(Modifier.height(10.dp))
            Text(
                stringResource(
                    if (ok) R.string.account_delete_done else R.string.account_delete_failed
                ),
                color = if (ok) SpeccyPalette.ok else MaterialTheme.colorScheme.error,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }

    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text(stringResource(R.string.account_delete_title)) },
            text = { Text(stringResource(R.string.account_delete_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    confirming = false
                    working = true
                    scope.launch {
                        val ok = SpeccyIdentity.deleteAccountAndData(context)
                        if (ok) {
                            settingsManager.userEmail = null
                            UserStatusManager(context).logout()
                        }
                        working = false
                        result = ok
                    }
                }) { Text(stringResource(R.string.account_delete_cta), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirming = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}
