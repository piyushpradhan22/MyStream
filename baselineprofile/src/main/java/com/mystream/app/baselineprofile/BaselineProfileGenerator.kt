package com.mystream.app.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Records a measured baseline profile for MyStream: cold start into the home screen,
 * then D-pad scrolling through the carousels to capture the actual scroll hot path.
 *
 * Generate with:  ./gradlew :app:generateReleaseBaselineProfile
 * Output is written to app/src/release/generated/baselineProfiles/ and merged into the APK.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(
        packageName = "com.mystream.app",
        includeInStartupProfile = true
    ) {
        pressHome()
        startActivityAndWait()

        // Let the home carousels populate before exercising navigation.
        device.waitForIdle()
        Thread.sleep(5000)

        // Scroll the focused carousel right to capture per-item poster rendering.
        repeat(12) {
            device.pressDPadRight()
            Thread.sleep(180)
        }
        device.waitForIdle()

        // Move focus down through category rows / pills to warm those paths too.
        repeat(3) {
            device.pressDPadDown()
            Thread.sleep(300)
        }
        device.waitForIdle()
    }
}
