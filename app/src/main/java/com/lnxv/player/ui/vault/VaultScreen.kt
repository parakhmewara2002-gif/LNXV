package com.lnxv.player.ui.vault

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.FastOutSlowInEasing
import com.lnxv.player.core.design.GlassProgress
import com.lnxv.player.core.design.GlassDialog
import com.lnxv.player.core.design.GlassDialogButton
import com.lnxv.player.core.design.GlassButton
import com.lnxv.player.core.design.bouncyClickable
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.contentDescription
import com.lnxv.player.core.design.GlassTextField
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
import com.lnxv.player.core.design.icons.Backspace
import androidx.compose.material.icons.filled.Lock
import com.lnxv.player.core.design.icons.Restore
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
import android.graphics.BitmapFactory
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
    val reduceEffects = com.lnxv.player.core.design.LocalReduceEffects.current
    val uiState by viewModel.uiState.collectAsState()
    val vaultVideos by viewModel.vaultVideos.collectAsState()
    val legacyCount by viewModel.legacyCount.collectAsState()
    val job by viewModel.jobState.collectAsState()

    // FLAG_SECURE on the vault screen: no screenshots / recents thumbnail of vault content.
    val activity = com.lnxv.player.findLnxvMainActivity(LocalContext.current)
    DisposableEffect(activity) {
        activity?.setSecureWindowReason("vault-screen", true)
        onDispose { activity?.setSecureWindowReason("vault-screen", false) }
    }

    if (uiState.showChangePin) {
        var oldPin by remember { mutableStateOf("") }
        var newPin by remember { mutableStateOf("") }
        GlassDialog(
            onDismissRequest = viewModel::closeChangePin,
            title = { Text(androidx.compose.ui.res.stringResource(com.lnxv.player.R.string.pin_change_title)) },
            text = {
                Column {
                    GlassTextField(
                        value = oldPin,
                        onValueChange = { oldPin = it.filter(Char::isDigit).take(8) },
                        placeholder = androidx.compose.ui.res.stringResource(com.lnxv.player.R.string.pin_current),
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword
                        )
                    )
                    Spacer(Modifier.height(8.dp))
                    GlassTextField(
                        value = newPin,
                        onValueChange = { newPin = it.filter(Char::isDigit).take(8) },
                        placeholder = androidx.compose.ui.res.stringResource(com.lnxv.player.R.string.pin_new),
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
                GlassDialogButton(
                    text = androidx.compose.ui.res.stringResource(com.lnxv.player.R.string.action_change),
                    onClick = { viewModel.changePin(oldPin, newPin) },
                    enabled = oldPin.length >= 4 && newPin.length >= 4,
                    primary = true
                )
            },
            dismissButton = {
                GlassDialogButton(
                    text = androidx.compose.ui.res.stringResource(com.lnxv.player.R.string.action_cancel),
                    onClick = viewModel::closeChangePin
                )
            }
        )
    }

    if (uiState.showForgotConfirm) {
        GlassDialog(
            onDismissRequest = viewModel::dismissForgotPin,
            title = { Text(androidx.compose.ui.res.stringResource(com.lnxv.player.R.string.pin_forgot)) },
            text = {
                Text("The vault is encrypted with a key protected by your PIN. Resetting the PIN permanently deletes " +
                    "ALL videos stored in the vault. This cannot be undone.")
            },
            confirmButton = {
                GlassDialogButton(text = "Delete vault & reset", onClick = viewModel::confirmWipe, primary = true, destructive = true)
            },
            dismissButton = {
                GlassDialogButton(
                    text = androidx.compose.ui.res.stringResource(com.lnxv.player.R.string.action_cancel),
                    onClick = viewModel::dismissForgotPin
                )
            }
        )
    }

    Scaffold(
        modifier = Modifier.fillMaxSize().ambientBackground(),
        containerColor = Color.Transparent,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
                    .liquidGlassSurface(
                        shape = RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp),
                        backgroundColor = appColors.barFill.copy(alpha = 0.55f),
                        borderColor = appColors.glassBorder
                    )
                    .padding(top = 44.dp, bottom = 14.dp, start = 16.dp, end = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                GlassIconButton(
                    icon = Icons.Default.ArrowBack,
                    contentDescription = androidx.compose.ui.res.stringResource(com.lnxv.player.R.string.cd_back),
                    onClick = onBackClick
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = androidx.compose.ui.res.stringResource(com.lnxv.player.R.string.vault_title),
                    color = appColors.textPrimary,
                    style = Typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                if (uiState.authState == VaultAuthState.AUTHENTICATED) {
                    VaultPill(text = "Change PIN", color = appColors.textSecondary, onClick = viewModel::openChangePin)
                    Spacer(modifier = Modifier.width(8.dp))
                    VaultPill(text = "Lock", color = appColors.accent, onClick = {
                        // Animate transition smoothly back to keypad, then lock
                        viewModel.lock()
                    })
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            AnimatedContent(
                targetState = uiState.authState == VaultAuthState.AUTHENTICATED,
                transitionSpec = {
                    if (reduceEffects) {
                        fadeIn(tween(0)).togetherWith(fadeOut(tween(0)))
                    } else if (targetState) {
                        // Unlocking: Vault videos scale/fade in, keypad scales/fades out
                        (fadeIn(tween(300, easing = FastOutSlowInEasing)) + scaleIn(tween(350, easing = FastOutSlowInEasing), initialScale = 0.94f))
                            .togetherWith(fadeOut(tween(200, easing = FastOutSlowInEasing)) + scaleOut(tween(200, easing = FastOutSlowInEasing), targetScale = 1.05f))
                    } else {
                        // Locking: Keypad smoothly scales and fades back in, vault list scales down and fades out
                        (fadeIn(tween(300, easing = FastOutSlowInEasing)) + scaleIn(tween(350, easing = FastOutSlowInEasing), initialScale = 1.05f))
                            .togetherWith(fadeOut(tween(220, easing = FastOutSlowInEasing)) + scaleOut(tween(220, easing = FastOutSlowInEasing), targetScale = 0.94f))
                    }
                },
                label = "VaultAuthContentTransition"
            ) { isAuthenticated ->
                if (isAuthenticated) {
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
                            GlassProgress(progress = { job.progress }, modifier = Modifier.fillMaxWidth())
                            TextButton(onClick = viewModel::cancelTransfers) { Text(androidx.compose.ui.res.stringResource(com.lnxv.player.R.string.action_cancel)) }
                        }
                    }
                    if (legacyCount > 0 && !job.isRunning) {
                        GlassPanel(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                            shape = RoundedCornerShape(20.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 10.dp, top = 10.dp, bottom = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "$legacyCount video(s) are hidden but not encrypted.",
                                    color = appColors.textSecondary, fontSize = 13.sp, modifier = Modifier.weight(1f).padding(end = 10.dp)
                                )
                                GlassButton(text = "Encrypt now", onClick = viewModel::encryptLegacyItems, isPrimary = true)
                            }
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
                                    text = androidx.compose.ui.res.stringResource(com.lnxv.player.R.string.vault_empty_title),
                                    color = appColors.textPrimary,
                                    style = Typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = androidx.compose.ui.res.stringResource(com.lnxv.player.R.string.vault_empty_hint),
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
                                        .then(if (reduceEffects) Modifier else Modifier.animateItem())
                                        .fillMaxWidth()
                                        .clickable { onVideoClick(video) },
                                    shape = RoundedCornerShape(24.dp),
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
                                                modifier = Modifier.size(width = 80.dp, height = 48.dp).clip(RoundedCornerShape(14.dp))
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
                                        GlassIconButton(
                                            icon = Icons.Default.Restore,
                                            contentDescription = androidx.compose.ui.res.stringResource(com.lnxv.player.R.string.vault_restore),
                                            onClick = { viewModel.restoreFromVault(video) },
                                            tint = appColors.accent,
                                            size = 40.dp
                                        )
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
                        LaunchedEffect(uiState.shakeCount, reduceEffects) {
                            if (reduceEffects) {
                                shake.snapTo(0f)
                            } else if (uiState.shakeCount > 0) {
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
                                            if (isFilled) (if (uiState.busy) appColors.accent.copy(alpha = 0.5f) else appColors.accent)
                                            else appColors.glassBorder
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (uiState.busy && isFilled) {
                                        // Subtle pulse or indicator when busy verifying
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        if (uiState.busy) {
                            Box(modifier = Modifier.height(20.dp), contentAlignment = Alignment.Center) {
                                com.lnxv.player.core.design.GlassProgress(
                                    progress = { -1f },
                                    modifier = Modifier.width(60.dp)
                                )
                            }
                        } else {
                            AnimatedVisibility(
                                visible = uiState.errorMessage != null || uiState.lockoutRemainingSec > 0,
                                enter = if (reduceEffects) androidx.compose.animation.fadeIn(tween(0)) else androidx.compose.animation.fadeIn(),
                                exit = if (reduceEffects) androidx.compose.animation.fadeOut(tween(0)) else androidx.compose.animation.fadeOut()
                            ) {
                                Text(
                                    text = if (uiState.lockoutRemainingSec > 0)
                                        "Locked. Try again in ${uiState.lockoutRemainingSec / 60}:${"%02d".format(uiState.lockoutRemainingSec % 60)}"
                                    else uiState.errorMessage ?: "",
                                    color = CrimsonAlert,
                                    fontSize = 13.sp
                                )
                            }
                        }
                        if (uiState.needsConfirmButton) {
                            GlassButton(text = "OK", onClick = viewModel::submit, isPrimary = true, enabled = uiState.enteredPin.length >= 4)
                        }
                        if (uiState.authState == VaultAuthState.ENTER_PIN && uiState.biometricEnabled && uiState.lockoutRemainingSec == 0L &&
                            com.lnxv.player.core.security.BiometricGate.canAuthenticate(androidx.compose.ui.platform.LocalContext.current)
                        ) {
                            TextButton(onClick = {
                                (activity as? androidx.fragment.app.FragmentActivity)?.let { a ->
                                    val cipher = viewModel.biometricCipher()
                                    if (cipher != null) {
                                        com.lnxv.player.core.security.BiometricGate.promptCrypto(a, cipher, "Unlock vault", "Use PIN", { c -> viewModel.unlockWithBiometric(c) })
                                    }
                                }
                            }) { Text("Use biometrics", color = appColors.accent, fontSize = 13.sp) }
                        }
                        if (uiState.authState == VaultAuthState.ENTER_PIN) {
                            TextButton(onClick = viewModel::requestForgotPin) {
                                Text("Forgot PIN?", color = appColors.textTertiary, fontSize = 12.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(36.dp))

                        val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
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
                                                    .semantics {
                                                        role = androidx.compose.ui.semantics.Role.Button
                                                        contentDescription = "Delete last digit"
                                                    }
                                                    .bouncyClickable(pressedScale = 0.88f) {
                                                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                                        viewModel.deleteDigit()
                                                    },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Backspace,
                                                    contentDescription = androidx.compose.ui.res.stringResource(com.lnxv.player.R.string.action_delete),
                                                    tint = appColors.textPrimary
                                                )
                                            }
                                        } else {
                                            Box(
                                                modifier = Modifier
                                                    .size(68.dp)
                                                    .semantics {
                                                        role = androidx.compose.ui.semantics.Role.Button
                                                        contentDescription = "Enter digit $key"
                                                    }
                                                    .bouncyClickable(pressedScale = 0.88f) {
                                                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                                        viewModel.enterDigit(key)
                                                    }
                                                    .liquidGlassSurface(
                                                        shape = CircleShape,
                                                        backgroundColor = appColors.glassSubtle,
                                                        borderColor = appColors.glassBorder
                                                    ),
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
}

/** Small glass capsule used for the vault header actions (Change PIN / Lock). */
@Composable
private fun VaultPill(text: String, color: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .bouncyClickable(pressedScale = 0.93f) { onClick() }
            .liquidGlassSurface(
                shape = CircleShape,
                backgroundColor = appColors.glassFill,
                borderColor = appColors.glassBorder
            )
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text = text, color = color, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}
