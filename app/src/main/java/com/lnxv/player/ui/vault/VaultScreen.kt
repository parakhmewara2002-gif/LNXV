package com.lnxv.player.ui.vault

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import android.app.Activity
import android.graphics.BitmapFactory
import android.view.WindowManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lnxv.player.core.design.ambientBackground
import com.lnxv.player.core.design.appColors
import com.lnxv.player.core.design.CrimsonAlert
import com.lnxv.player.core.design.GlassIconButton
import com.lnxv.player.core.design.GlassPanel
import com.lnxv.player.core.design.NeonCyan
import com.lnxv.player.core.design.Typography
import com.lnxv.player.core.design.liquidGlassSurface
import com.lnxv.player.core.model.VideoItem

@Composable
fun VaultScreen(
    viewModel: VaultViewModel,
    onBackClick: () -> Unit,
    onVideoClick: (VideoItem) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val vaultVideos by viewModel.vaultVideos.collectAsState()
    val legacyCount by viewModel.legacyCount.collectAsState()
    val job by viewModel.jobState.collectAsState()

    // FLAG_SECURE on the vault screen: no screenshots / recents thumbnail of vault content.
    val activity = LocalContext.current as? Activity
    DisposableEffect(activity) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose {
            activity?.window?.let { w ->
                if (!com.lnxv.player.LNXVApplication.instance.screenshotsBlockedGlobally) {
                    w.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
            }
        }
    }

    if (uiState.showChangePin) {
        var oldPin by remember { mutableStateOf("") }
        var newPin by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = viewModel::closeChangePin,
            title = { Text("Change PIN") },
            text = {
                Column {
                    androidx.compose.material3.OutlinedTextField(
                        value = oldPin,
                        onValueChange = { oldPin = it.filter(Char::isDigit).take(8) },
                        label = { Text("Current PIN") },
                        singleLine = true,
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword
                        )
                    )
                    Spacer(Modifier.height(8.dp))
                    androidx.compose.material3.OutlinedTextField(
                        value = newPin,
                        onValueChange = { newPin = it.filter(Char::isDigit).take(8) },
                        label = { Text("New PIN (4-8 digits)") },
                        singleLine = true,
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword
                        )
                    )
                    uiState.changePinMessage?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(it, color = CrimsonAlert, fontSize = 13.sp)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.changePin(oldPin, newPin) }, enabled = oldPin.length >= 4 && newPin.length >= 4) {
                    Text("Change")
                }
            },
            dismissButton = { TextButton(onClick = viewModel::closeChangePin) { Text("Cancel") } }
        )
    }

    if (uiState.showForgotConfirm) {
        AlertDialog(
            onDismissRequest = viewModel::dismissForgotPin,
            title = { Text("Forgot PIN?") },
            text = {
                Text("The vault is encrypted with a key protected by your PIN. Resetting the PIN permanently deletes " +
                    "ALL videos stored in the vault. This cannot be undone.")
            },
            confirmButton = {
                TextButton(onClick = viewModel::confirmWipe) { Text("Delete vault & reset", color = CrimsonAlert) }
            },
            dismissButton = { TextButton(onClick = viewModel::dismissForgotPin) { Text("Cancel") } }
        )
    }

    Scaffold(
        modifier = Modifier.fillMaxSize().ambientBackground(),
        containerColor = Color.Transparent,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 40.dp, bottom = 16.dp, start = 16.dp, end = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                GlassIconButton(
                    icon = Icons.Default.ArrowBack,
                    contentDescription = "Back",
                    onClick = onBackClick
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = "Private Vault",
                    color = appColors.textPrimary,
                    style = Typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                if (uiState.authState == VaultAuthState.AUTHENTICATED) {
                    TextButton(onClick = viewModel::openChangePin) { Text("Change PIN", color = appColors.textTertiary) }
                    TextButton(onClick = {
                        viewModel.lock()
                        onBackClick()
                    }) { Text("Lock", color = appColors.accent) }
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (uiState.authState == VaultAuthState.AUTHENTICATED) {
                // Display Vault Videos List
                Column(modifier = Modifier.fillMaxSize()) {
                if (job.isRunning) {
                    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                        Text(
                            text = (if (job.restoring) "Restoring " else "Encrypting ") + job.title +
                                if (job.queued > 0) " (+${job.queued} queued)" else "",
                            color = appColors.textPrimary, fontSize = 13.sp, maxLines = 1
                        )
                        Spacer(Modifier.height(6.dp))
                        LinearProgressIndicator(progress = { job.progress }, modifier = Modifier.fillMaxWidth())
                        TextButton(onClick = viewModel::cancelTransfers) { Text("Cancel") }
                    }
                }
                if (legacyCount > 0 && !job.isRunning) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "$legacyCount video(s) are hidden but not encrypted.",
                            color = appColors.textTertiary, fontSize = 13.sp, modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = viewModel::encryptLegacyItems) { Text("Encrypt now", color = appColors.accent) }
                    }
                }
                if (vaultVideos.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = appColors.textTertiary,
                                modifier = Modifier.size(64.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Your Vault is Empty",
                                color = appColors.textPrimary,
                                style = Typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Move videos here from the home screen options menu.",
                                color = appColors.textTertiary,
                                fontSize = 13.sp
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(vaultVideos, key = { it.id }) { video ->
                            GlassPanel(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onVideoClick(video) },
                                shape = RoundedCornerShape(16.dp),
                                backgroundColor = appColors.glassFill
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    val thumb by produceState<ImageBitmap?>(null, video.id, video.vaultFile) {
                                        value = withContext(Dispatchers.IO) {
                                            viewModel.loadThumbnail(video.id)?.let { b ->
                                                BitmapFactory.decodeByteArray(b, 0, b.size)?.asImageBitmap()
                                            }
                                        }
                                    }
                                    thumb?.let {
                                        Image(
                                            bitmap = it,
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.size(width = 80.dp, height = 48.dp).clip(RoundedCornerShape(8.dp))
                                        )
                                        Spacer(Modifier.width(12.dp))
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = video.title,
                                            color = appColors.textPrimary,
                                            style = Typography.titleSmall
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "${video.durationFormatted} • ${video.sizeFormatted}",
                                            color = appColors.textTertiary,
                                            fontSize = 12.sp
                                        )
                                    }
                                    IconButton(onClick = { viewModel.restoreFromVault(video) }) {
                                        Icon(
                                            imageVector = Icons.Default.Restore,
                                            contentDescription = "Restore to Library",
                                            tint = appColors.accent
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                }
            } else {
                // PIN Verification & Setup Keypad
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .liquidGlassSurface(
                                shape = CircleShape,
                                backgroundColor = appColors.accent.copy(alpha = 0.15f),
                                borderColor = appColors.accent.copy(alpha = 0.5f)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = appColors.accent,
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    val promptText = when (uiState.authState) {
                        VaultAuthState.SETUP_PIN -> "Create a Vault PIN (4-8 digits, 6 recommended)"
                        VaultAuthState.CONFIRM_PIN -> "Confirm your PIN"
                        VaultAuthState.ENTER_PIN -> "Enter your Vault PIN"
                        VaultAuthState.AUTHENTICATED -> ""
                    }

                    Text(
                        text = promptText,
                        color = appColors.textPrimary,
                        style = Typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(28.dp))

                    // PIN dots (shake on a wrong PIN; translation is animated in a graphicsLayer lambda)
                    val shake = remember { Animatable(0f) }
                    LaunchedEffect(uiState.shakeCount) {
                        if (uiState.shakeCount > 0) {
                            for (t in listOf(-24f, 24f, -16f, 16f, -8f, 0f)) shake.animateTo(t, tween(45))
                        }
                    }
                    val dotCount = if (uiState.authState == VaultAuthState.ENTER_PIN) uiState.pinLength
                    else maxOf(6, uiState.enteredPin.length)
                    Row(
                        modifier = Modifier.graphicsLayer { translationX = shake.value },
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        repeat(dotCount) { index ->
                            val isFilled = index < uiState.enteredPin.length
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isFilled) appColors.accent else appColors.glassBorder
                                    )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    AnimatedVisibility(visible = uiState.errorMessage != null || uiState.lockoutRemainingSec > 0) {
                        Text(
                            text = if (uiState.lockoutRemainingSec > 0)
                                "Locked. Try again in ${uiState.lockoutRemainingSec / 60}:${"%02d".format(uiState.lockoutRemainingSec % 60)}"
                            else uiState.errorMessage ?: "",
                            color = CrimsonAlert,
                            fontSize = 13.sp
                        )
                    }
                    if (uiState.needsConfirmButton) {
                        TextButton(onClick = viewModel::submit, enabled = uiState.enteredPin.length >= 4) {
                            Text("OK", color = appColors.accent)
                        }
                    }
                    if (uiState.authState == VaultAuthState.ENTER_PIN) {
                        TextButton(onClick = viewModel::requestForgotPin) {
                            Text("Forgot PIN?", color = appColors.textTertiary, fontSize = 12.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(36.dp))

                    // Numeric Pad (1 to 9, 0, Backspace)
                    val keys = listOf(
                        listOf("1", "2", "3"),
                        listOf("4", "5", "6"),
                        listOf("7", "8", "9"),
                        listOf("", "0", "DEL")
                    )

                    Column(
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        keys.forEach { row ->
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(24.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                row.forEach { key ->
                                    if (key.isEmpty()) {
                                        Spacer(modifier = Modifier.size(68.dp))
                                    } else if (key == "DEL") {
                                        Box(
                                            modifier = Modifier
                                                .size(68.dp)
                                                .clip(CircleShape)
                                                .clickable { viewModel.deleteDigit() },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Backspace,
                                                contentDescription = "Delete",
                                                tint = appColors.textPrimary
                                            )
                                        }
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .size(68.dp)
                                                .liquidGlassSurface(
                                                    shape = CircleShape,
                                                    backgroundColor = appColors.glassSubtle,
                                                    borderColor = appColors.glassBorder
                                                )
                                                .clickable { viewModel.enterDigit(key) },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = key,
                                                color = appColors.textPrimary,
                                                fontSize = 24.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
