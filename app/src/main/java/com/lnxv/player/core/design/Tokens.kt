package com.lnxv.player.core.design

import androidx.compose.ui.unit.dp

/** Spacing scale (4 dp grid). New UI uses these instead of ad-hoc dp literals; existing screens migrate gradually. */
object Space {
    val xxs = 2.dp
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
}

/** Corner radii. */
object Radius {
    val chip = 12.dp
    val card = 20.dp
    val sheet = 28.dp
}

/** Accessibility sizes. 48 dp is the Android minimum touch target. */
object Touch {
    val minTarget = 48.dp
}
