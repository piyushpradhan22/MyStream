package com.mystream.app.ui.utils

import android.app.ActivityManager
import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration

object DeviceUtils {

    /**
     * Returns true if the current device is an Android TV / Google TV or running in Leanback mode.
     */
    fun isTvDevice(context: Context): Boolean {
        val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
        val isTelevisionMode = uiModeManager?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
        val hasLeanbackFeature = context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
        val hasNoTouchScreen = !context.packageManager.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN)
        return isTelevisionMode || hasLeanbackFeature || (hasNoTouchScreen && isLandscape(context))
    }

    /**
     * Checks if the device is considered low-RAM (either flagged lowRamDevice or total heap <= 192MB).
     */
    fun isLowRamDevice(context: Context): Boolean {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return false
        return am.isLowRamDevice || am.memoryClass <= 192
    }

    private fun isLandscape(context: Context): Boolean {
        return context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    }
}
