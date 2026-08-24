package com.mystream.app.ui.utils

import androidx.compose.ui.focus.FocusRequester

/**
 * Safely requests focus without throwing IllegalStateException if the FocusRequester
 * is not yet initialized or attached to the composition tree.
 */
fun FocusRequester.safeRequestFocus(): Boolean {
    return try {
        requestFocus()
        true
    } catch (_: Throwable) {
        false
    }
}
