package com.mobicloud.presentation.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Switch
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.serialization.Serializable
import kotlin.math.roundToLong

@Serializable
object SettingsRoute

private const val HALF_GB = 512L * 1024 * 1024

@Composable
fun SettingsScreen(
    onRestoreIdentity: () -> Unit = {},
    onSetupPin: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val usedBytes by viewModel.usedStorageBytes.collectAsStateWithLifecycle()
    val freeBytes by viewModel.freeSpaceBytes.collectAsStateWithLifecycle()
    val hasPinSet by viewModel.hasPinSet.collectAsStateWithLifecycle()
    val showWarning by viewModel.showWarningDialog.collectAsStateWithLifecycle()
    val recoveryCode by viewModel.recoveryCode.collectAsStateWithLifecycle()
    val exportError by viewModel.exportError.collectAsStateWithLifecycle()
    val showCloudDialog by viewModel.showCloudDialog.collectAsStateWithLifecycle()
    val cloudError by viewModel.cloudError.collectAsStateWithLifecycle()
    val cloudSuccess by viewModel.cloudSuccess.collectAsStateWithLifecycle()
    var cloudEmail by remember { mutableStateOf("") }
    var cloudPassword by remember { mutableStateOf("") }
    var cloudIsRegister by remember { mutableStateOf(true) }

    val minBytes = HALF_GB
    val maxBytes = ((freeBytes * 0.80f).toLong()).coerceAtLeast(minBytes)
    val steps = (((maxBytes - minBytes) / HALF_GB).toInt() - 1).coerceAtLeast(0)

    var sliderValue by remember(settings.allocatedStorageBytes) {
        mutableFloatStateOf(settings.allocatedStorageBytes.coerceIn(minBytes, maxBytes).toFloat())
    }

    var storageVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        storageVisible = true
    }

    val sectionEnter = fadeIn(tween(350)) + slideInVertically(tween(350)) { it / 3 }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        AnimatedVisibility(visible = storageVisible, enter = sectionEnter) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, Color(0xFFF1F1F5)),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Storage,
                                contentDescription = null,
                                tint = Color(0xFF0A84FF),
                                modifier = Modifier.size(24.dp)
                            )
                            Text(
                                text = "Space I share",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1C1C1E)
                            )
                        }

                        Text(
                            text = sliderValue.toLong().toReadable(),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF0A84FF)
                        )
                    }

                    Slider(
                        value = sliderValue,
                        onValueChange = { sliderValue = it },
                        onValueChangeFinished = {
                            viewModel.requestUpdateAllocatedStorage(sliderValue.roundToLong())
                        },
                        valueRange = minBytes.toFloat()..maxBytes.toFloat(),
                        steps = steps.coerceAtLeast(0),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text(
                        text = "Used: ${usedBytes.toReadable()} · The more you share, the more files you can save with friends.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF8E8E93)
                    )
                }
            }
        }

        AnimatedVisibility(visible = storageVisible, enter = sectionEnter) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, Color(0xFFF1F1F5)),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(imageVector = Icons.Outlined.Lock, contentDescription = null, tint = Color(0xFF0A84FF), modifier = Modifier.size(24.dp))
                            Text("PIN Code", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color(0xFF1C1C1E))
                        }
                        Switch(
                            checked = hasPinSet,
                            onCheckedChange = { enabled ->
                                if (enabled) onSetupPin() else viewModel.disablePin()
                            }
                        )
                    }
                    Text(
                        text = if (hasPinSet) "PIN is enabled. The app will ask for it every time you open it." else "Enable a PIN to protect access to the app.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF8E8E93),
                    )
                    if (hasPinSet) {
                        TextButton(onClick = onSetupPin) {
                            Text("Change PIN", color = Color(0xFF0A84FF))
                        }
                    }
                }
            }
        }

        AnimatedVisibility(visible = storageVisible, enter = sectionEnter) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, Color(0xFFF1F1F5)),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(imageVector = Icons.Outlined.Key, contentDescription = null, tint = Color(0xFF0A84FF), modifier = Modifier.size(24.dp))
                        Text("Recovery", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color(0xFF1C1C1E))
                    }
                    Text("Export your recovery code to restore your identity on a new device.", style = MaterialTheme.typography.bodySmall, color = Color(0xFF8E8E93))
                    TextButton(onClick = { viewModel.exportIdentity() }) {
                        Text("Show recovery code", color = Color(0xFF0A84FF))
                    }
                    TextButton(onClick = { onRestoreIdentity() }) {
                        Text("Restore with a code", color = Color(0xFF8E8E93))
                    }
                    TextButton(onClick = { viewModel.requestCloudBackup() }) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Outlined.CloudUpload, contentDescription = null, tint = Color(0xFF0A84FF), modifier = Modifier.size(16.dp))
                            Text("Back up to cloud", color = Color(0xFF0A84FF))
                        }
                    }
                }
            }
        }
    }

    recoveryCode?.let { code ->
        val context = LocalContext.current
        var copied by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { viewModel.dismissRecoveryCode() },
            title = { Text("Recovery code", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Write this code down or take a screenshot. It lets you restore your account on a new device.",
                        fontSize = 13.sp,
                        color = Color(0xFF8E8E93)
                    )
                    Text(
                        text = code,
                        fontSize = 15.sp,
                        color = Color(0xFF1C1C1E),
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        lineHeight = 22.sp
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("recovery_code", code))
                    copied = true
                }) {
                    Text(if (copied) "Copied ✓" else "Copy code", color = Color(0xFF0A84FF), fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissRecoveryCode() }) { Text("Close") }
            }
        )
    }

    exportError?.let { error ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissExportError() },
            title = { Text("Export failed") },
            text = { Text(error) },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissExportError() }) { Text("OK") }
            }
        )
    }

    if (showCloudDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissCloudDialog() },
            title = { Text(if (cloudIsRegister) "Create a cloud account" else "Update cloud backup") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        if (cloudIsRegister)
                            "Create an account to back up your identity in an encrypted form."
                        else
                            "Sign in to your existing account to update the backup.",
                        fontSize = 13.sp, color = Color(0xFF8E8E93)
                    )
                    OutlinedTextField(
                        value = cloudEmail,
                        onValueChange = { cloudEmail = it },
                        label = { Text("Email") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = cloudPassword,
                        onValueChange = { cloudPassword = it },
                        label = { Text("Password") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth()
                    )
                    cloudError?.let { Text(it, color = Color(0xFFFF3B30), fontSize = 12.sp) }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (cloudIsRegister) "Already have an account?" else "New account?",
                            fontSize = 12.sp, color = Color(0xFF8E8E93)
                        )
                        TextButton(onClick = { cloudIsRegister = !cloudIsRegister; viewModel.clearCloudError() }) {
                            Text(
                                if (cloudIsRegister) "Sign in" else "Sign up",
                                fontSize = 12.sp, color = Color(0xFF0A84FF)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (cloudIsRegister) viewModel.doCloudBackup(cloudEmail, cloudPassword)
                        else viewModel.doCloudLogin(cloudEmail, cloudPassword)
                    },
                    enabled = cloudEmail.isNotBlank() && cloudPassword.isNotBlank()
                ) { Text(if (cloudIsRegister) "Create & back up" else "Sign in & back up") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissCloudDialog() }) { Text("Cancel") }
            }
        )
    }

    if (cloudSuccess) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissCloudSuccess() },
            title = { Text("Backup saved") },
            text = { Text("Your identity is backed up in the cloud. You can restore it on a new device using your credentials.") },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissCloudSuccess() }) { Text("OK") }
            }
        )
    }

    if (showWarning) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissWarningDialog() },
            title = { Text("Reduce shared space?") },
            text = { Text("If you reduce this space, some of your friends' files will no longer be protected on your device.") },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmReduceQuota() }) {
                    Text("Confirm")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissWarningDialog() }) {
                    Text("Cancel")
                }
            }
        )
    }
}

private fun Long.toReadable(): String {
    val kb = this / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return when {
        gb >= 1.0 -> "%.2f GB".format(gb)
        mb >= 1.0 -> "%.2f MB".format(mb)
        kb >= 1.0 -> "%.1f KB".format(kb)
        else -> "$this B"
    }
}
