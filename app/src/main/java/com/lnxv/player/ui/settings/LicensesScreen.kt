package com.lnxv.player.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lnxv.player.R
import com.lnxv.player.core.design.GlassPanel
import com.lnxv.player.core.design.GlassTopBar
import com.lnxv.player.core.design.Typography
import com.lnxv.player.core.design.ambientBackground
import com.lnxv.player.core.design.appColors
import com.lnxv.player.core.model.LicenseType
import com.lnxv.player.core.model.OpenSourceLibraries
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Lists every third-party library with its license text (read from assets/licenses, offline). */
@Composable
fun LicensesScreen(onBackClick: () -> Unit) {
    val context = LocalContext.current
    val hazeState = remember { HazeState() }
    var expandedName by remember { mutableStateOf<String?>(null) }
    val texts = remember { mutableStateMapOf<LicenseType, String>() }
    val missing = stringResource(R.string.licenses_license_missing)

    LaunchedEffect(expandedName) {
        val name = expandedName ?: return@LaunchedEffect
        val type = OpenSourceLibraries.ALL.first { it.name == name }.license
        if (type !in texts) {
            texts[type] = withContext(Dispatchers.IO) {
                runCatching { context.assets.open(type.assetPath).bufferedReader().use { it.readText() } }.getOrDefault(missing)
            }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize().ambientBackground(),
        containerColor = Color.Transparent,
        topBar = { GlassTopBar(title = stringResource(R.string.licenses_title), onBack = onBackClick, hazeState = hazeState) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().haze(state = hazeState),
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = padding.calculateTopPadding() + 12.dp,
                bottom = padding.calculateBottomPadding() + 24.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(stringResource(R.string.licenses_intro), color = appColors.textSecondary, fontSize = 13.sp)
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.licenses_ffmpeg_note), color = appColors.textSecondary, fontSize = 13.sp)
            }
            items(OpenSourceLibraries.ALL, key = { it.name }) { lib ->
                val expanded = expandedName == lib.name
                GlassPanel(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    backgroundColor = appColors.glassFill
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { expandedName = if (expanded) null else lib.name }
                            .padding(16.dp)
                    ) {
                        Text(lib.name, color = appColors.textPrimary, style = Typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            stringResource(R.string.licenses_version, lib.version) + "  •  " + lib.license.displayName,
                            color = appColors.textTertiary,
                            fontSize = 12.sp
                        )
                        if (lib.note.isNotEmpty()) {
                            Spacer(Modifier.height(4.dp))
                            Text(lib.note, color = appColors.textSecondary, fontSize = 12.sp)
                        }
                        Spacer(Modifier.height(2.dp))
                        Text(lib.url, color = appColors.accent, fontSize = 12.sp)
                        if (expanded) {
                            Spacer(Modifier.height(10.dp))
                            Text(texts[lib.license] ?: "…", color = appColors.textSecondary, fontSize = 11.sp, lineHeight = 14.sp)
                        }
                    }
                }
            }
        }
    }
}
