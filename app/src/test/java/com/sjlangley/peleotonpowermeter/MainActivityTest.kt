package com.sjlangley.peleotonpowermeter

import android.content.Intent
import com.sjlangley.peleotonpowermeter.data.model.AppScreen
import com.sjlangley.peleotonpowermeter.recorder.RideRecorderService
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MainActivityTest {
    @Test
    fun handleSetupPrimaryActionStartsServiceAndShowsLiveScreen() =
        runBlocking {
            val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()

            activity.handleSetupPrimaryAction()

            assertEquals(AppScreen.LIVE, activity.currentUiState().currentScreen)
            assertEquals(
                RideRecorderService::class.java.name,
                shadowOf(activity).nextStartedService.component?.className,
            )
        }

    @Test
    fun handleLivePrimaryActionBuildsSummaryScreen() =
        runBlocking {
            val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()

            activity.handleSetupPrimaryAction()
            activity.handleLivePrimaryAction()

            val summary = activity.currentUiState().summary
            assertEquals(AppScreen.SUMMARY, activity.currentUiState().currentScreen)
            assertEquals("02:40 indoor ride", summary.rideLabel)
            assertEquals("100 W", summary.averagePowerLabel)
        }

    @Test
    fun shareSummaryExportLaunchesChooserIntent() =
        runBlocking {
            val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
            activity.handleSetupPrimaryAction()
            activity.handleLivePrimaryAction()

            activity.shareSummaryExport()

            val chooserIntent = shadowOf(activity).nextStartedActivity
            assertNotNull(chooserIntent)
            assertEquals(Intent.ACTION_CHOOSER, chooserIntent.action)
            val sendIntent = chooserIntent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
            assertNotNull(sendIntent)
            assertEquals(Intent.ACTION_SEND, sendIntent?.action)
            assertEquals("text/plain", sendIntent?.type)
        }

    @Test
    fun startRideRecorderServiceShowsToastWhenForegroundStartThrowsIllegalStateException() {
        val activity =
            Robolectric.buildActivity(IllegalStateMainActivity::class.java).setup().get()

        activity.startRideRecorderService()

        assertEquals("Could not start ride recording.", ShadowToast.getTextOfLatestToast())
    }

    @Test
    fun startRideRecorderServiceShowsToastWhenForegroundStartThrowsSecurityException() {
        val activity =
            Robolectric.buildActivity(SecurityExceptionMainActivity::class.java).setup().get()

        activity.startRideRecorderService()

        assertEquals("Could not start ride recording.", ShadowToast.getTextOfLatestToast())
    }
}

private class IllegalStateMainActivity : MainActivity() {
    override fun startForegroundRideRecorder(intent: Intent) {
        error("Simulated test failure")
    }
}

private class SecurityExceptionMainActivity : MainActivity() {
    override fun startForegroundRideRecorder(intent: Intent) {
        throw SecurityException("Simulated test failure")
    }
}
