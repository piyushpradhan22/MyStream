package com.mystream.app.ui.utils

import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Ensures a safe top padding for screen header bars that will never collapse to 0
 * or overlap the system status bar, clock, battery icons, or camera cutout,
 * even during or after orientation transitions (e.g., returning from fullscreen video).
 */
@Composable
fun Modifier.appTopBarPadding(additionalTop: Dp = 0.dp): Modifier {
    val context = LocalContext.current
    val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
    val isTv = uiModeManager?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION ||
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)

    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val cutoutTop = WindowInsets.displayCutout.asPaddingValues().calculateTopPadding()

    // Minimum 38.dp fallback on mobile ensures headers never end up behind the status bar
    val effectiveTop = if (isTv) 0.dp else maxOf(statusBarTop, cutoutTop, 38.dp)
    return this.padding(top = effectiveTop + additionalTop)
}
