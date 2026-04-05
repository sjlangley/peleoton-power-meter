package com.sjlangley.peleotonpowermeter

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.net.Uri
import android.os.Looper
import com.sjlangley.peleotonpowermeter.data.model.AppScreen
import com.sjlangley.peleotonpowermeter.data.model.DeviceAssociation
import com.sjlangley.peleotonpowermeter.data.model.PreviewRideData
import com.sjlangley.peleotonpowermeter.data.model.SyncState
import com.sjlangley.peleotonpowermeter.domain.RideSummaryCalculator
import com.sjlangley.peleotonpowermeter.fit.ExportedFitFile
import com.sjlangley.peleotonpowermeter.fit.RideFitExporter
import com.sjlangley.peleotonpowermeter.recorder.RecorderSessionState
import com.sjlangley.peleotonpowermeter.recorder.RideRecorderService
import com.sjlangley.peleotonpowermeter.setup.CompanionAssociationStarter
import com.sjlangley.peleotonpowermeter.setup.RememberedDevice
import com.sjlangley.peleotonpowermeter.setup.RememberedDevices
import com.sjlangley.peleotonpowermeter.setup.SetupDeviceRole
import com.sjlangley.peleotonpowermeter.testutil.FakeRecorderSessionController
import com.sjlangley.peleotonpowermeter.testutil.FakeRememberedDeviceStore
import com.sjlangley.peleotonpowermeter.testutil.FakeRideStore
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
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
    private lateinit var rideStore: FakeRideStore
    private lateinit var recorderSessionController: FakeRecorderSessionController
    private lateinit var rememberedDeviceStore: FakeRememberedDeviceStore
    private lateinit var companionAssociationStarter: FakeCompanionAssociationStarter
    private lateinit var rideFitExporter: FakeRideFitExporter

    @Before
    fun setUp() {
        rideStore = FakeRideStore()
        recorderSessionController = FakeRecorderSessionController()
        rememberedDeviceStore = FakeRememberedDeviceStore()
        companionAssociationStarter = FakeCompanionAssociationStarter()
        rideFitExporter = FakeRideFitExporter()
        MainActivity.rideStoreOverride = rideStore
        MainActivity.rememberedDeviceStoreOverride = rememberedDeviceStore
        MainActivity.companionAssociationStarterOverride = companionAssociationStarter
        MainActivity.rideFitExporterOverride = rideFitExporter
        MainActivity.recorderSessionControllerOverride = recorderSessionController
    }

    @After
    fun tearDown() {
        MainActivity.rideStoreOverride = null
        MainActivity.rememberedDeviceStoreOverride = null
        MainActivity.companionAssociationStarterOverride = null
        MainActivity.rideFitExporterOverride = null
        MainActivity.recorderSessionControllerOverride = null
    }

    @Test
    fun handleSetupPrimaryActionStartsNextAssociationWhenSetupIsIncomplete() =
        runBlocking {
            val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()

            activity.handleSetupPrimaryAction()

            assertEquals(listOf(SetupDeviceRole.LEFT_PEDAL), companionAssociationStarter.startedRoles)
            assertEquals("Left Pedal", activity.currentUiState().setup.devices[0].statusLabel)
            assertEquals("Waiting for right pedal", activity.currentUiState().setup.overallStatus)
            assertEquals(AppScreen.SETUP, activity.currentUiState().currentScreen)
        }

    @Test
    fun handleSetupPrimaryActionStartsServiceWhenSetupIsReady() =
        runBlocking {
            rememberedDeviceStore =
                FakeRememberedDeviceStore(
                    initialDevices =
                        RememberedDevices(
                            leftPedal = rememberedDevice(1, "Left Pedal", "left-id"),
                            rightPedal = rememberedDevice(2, "Right Pedal", "right-id"),
                            heartRate = rememberedDevice(3, "Heart Rate", "hr-id"),
                        ),
                )
            MainActivity.rememberedDeviceStoreOverride = rememberedDeviceStore
            val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()

            activity.handleSetupPrimaryAction()

            assertEquals(AppScreen.LIVE, activity.currentUiState().currentScreen)
            assertEquals(
                RideRecorderService::class.java.name,
                shadowOf(activity).nextStartedService.component?.className,
            )
        }

    @Test
    fun handleSetupDebugActionStartsServiceWhenDebugDemoSensorsAreEnabled() =
        runBlocking {
            val activity = Robolectric.buildActivity(DebugDemoSensorsMainActivity::class.java).setup().get()

            activity.handleSetupDebugAction()

            assertEquals(AppScreen.LIVE, activity.currentUiState().currentScreen)
            assertEquals(
                RideRecorderService::class.java.name,
                shadowOf(activity).nextStartedService.component?.className,
            )
        }

    @Test
    fun handleSetupSecondaryActionClearsRememberedDevicesAndDisassociates() =
        runBlocking {
            rememberedDeviceStore =
                FakeRememberedDeviceStore(
                    initialDevices =
                        RememberedDevices(
                            leftPedal = rememberedDevice(11, "Left Pedal", "left-id"),
                            rightPedal = rememberedDevice(12, "Right Pedal", "right-id"),
                            heartRate = rememberedDevice(13, "Heart Rate", "hr-id"),
                        ),
                )
            MainActivity.rememberedDeviceStoreOverride = rememberedDeviceStore
            val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()

            activity.handleSetupSecondaryAction()

            assertEquals(listOf(11, 12, 13), companionAssociationStarter.disassociatedIds)
            assertEquals("Waiting for left pedal", activity.currentUiState().setup.overallStatus)
            assertEquals("Pair Left Pedal", activity.currentUiState().setup.primaryActionLabel)
        }

    @Test
    fun handleLivePrimaryActionBuildsSummaryScreen() =
        runBlocking {
            rememberedDeviceStore =
                FakeRememberedDeviceStore(
                    initialDevices =
                        RememberedDevices(
                            leftPedal = rememberedDevice(1, "Left Pedal", "left-id"),
                            rightPedal = rememberedDevice(2, "Right Pedal", "right-id"),
                            heartRate = rememberedDevice(3, "Heart Rate", "hr-id"),
                        ),
                )
            MainActivity.rememberedDeviceStoreOverride = rememberedDeviceStore
            val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
            val samples = PreviewRideData.demoRideSamples()
            val rideId = "ride-1"
            rideStore.startSession(PreviewRideData.demoRideSession(rideId))
            rideStore.appendSamples(rideId, samples)
            rideStore.finishSession(rideId, samples.last().timestampEpochSeconds)
            rideStore.saveSummary(rideId, RideSummaryCalculator.calculate(samples))

            activity.handleSetupPrimaryAction()
            activity.handleLivePrimaryAction()
            recorderSessionController.emit(RecorderSessionState.Completed(rideId))
            shadowOf(Looper.getMainLooper()).idle()

            val summary = activity.currentUiState().summary
            assertEquals(AppScreen.SUMMARY, activity.currentUiState().currentScreen)
            assertEquals("02:40 indoor ride", summary.rideLabel)
            assertEquals("100 W", summary.averagePowerLabel)
        }

    @Test
    fun handleLiveSecondaryActionSendsToggleIntentToService() =
        runBlocking {
            val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()

            activity.handleLiveSecondaryAction()

            assertEquals(
                RideRecorderService.ACTION_TOGGLE_PEDAL_DROPOUT,
                shadowOf(activity).nextStartedService.action,
            )
        }

    @Test
    fun shareSummaryExportShowsToastEarlyWhenRideIdIsBlank() =
        runBlocking {
            val samples = PreviewRideData.demoRideSamples()
            val blankRideId = ""
            rideStore.startSession(PreviewRideData.demoRideSession(blankRideId))
            rideStore.appendSamples(blankRideId, samples)
            rideStore.finishSession(blankRideId, samples.last().timestampEpochSeconds)
            rideStore.saveSummary(blankRideId, RideSummaryCalculator.calculate(samples))
            val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()

            recorderSessionController.emit(RecorderSessionState.Completed(blankRideId))
            shadowOf(Looper.getMainLooper()).idle()

            activity.shareSummaryExport()

            assertEquals(
                "Could not export FIT. Your ride is still stored on this phone.",
                ShadowToast.getTextOfLatestToast(),
            )
            assertTrue(rideFitExporter.exportCallCount == 0)
        }

    @Test
    fun shareSummaryExportShowsToastWhenRideDataIsMissing() =
        runBlocking {
            val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()

            // Default state has rideId = "preview-ride" but no data in the store
            activity.shareSummaryExport()

            assertEquals(
                "Could not export FIT. Your ride is still stored on this phone.",
                ShadowToast.getTextOfLatestToast(),
            )
            assertTrue(rideFitExporter.exportCallCount == 0)
        }

    @Test
    fun shareSummaryExportLaunchesChooserIntent() =
        runBlocking {
            rememberedDeviceStore =
                FakeRememberedDeviceStore(
                    initialDevices =
                        RememberedDevices(
                            leftPedal = rememberedDevice(1, "Left Pedal", "left-id"),
                            rightPedal = rememberedDevice(2, "Right Pedal", "right-id"),
                            heartRate = rememberedDevice(3, "Heart Rate", "hr-id"),
                        ),
                )
            MainActivity.rememberedDeviceStoreOverride = rememberedDeviceStore
            val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
            val samples = PreviewRideData.demoRideSamples()
            val rideId = "ride-1"
            rideStore.startSession(PreviewRideData.demoRideSession(rideId))
            rideStore.appendSamples(rideId, samples)
            rideStore.finishSession(rideId, samples.last().timestampEpochSeconds)
            rideStore.saveSummary(rideId, RideSummaryCalculator.calculate(samples))

            recorderSessionController.emit(RecorderSessionState.Completed(rideId))
            shadowOf(Looper.getMainLooper()).idle()

            activity.shareSummaryExport()

            val chooserIntent = shadowOf(activity).nextStartedActivity
            assertNotNull(chooserIntent)
            assertEquals(Intent.ACTION_CHOOSER, chooserIntent.action)
            val sendIntent = chooserIntent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
            assertNotNull(sendIntent)
            assertEquals(Intent.ACTION_SEND, sendIntent?.action)
            assertEquals("application/octet-stream", sendIntent?.type)
            assertEquals(rideFitExporter.exportedUri, sendIntent?.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java))
            assertEquals(SyncState.EXPORTED, rideStore.loadSummary(rideId)?.exportState)
            assertEquals("Export FIT Again", activity.currentUiState().summary.exportLabel)
        }

    @Test
    fun shareSummaryExportShowsToastAndMarksFailureWhenExportFails() =
        runBlocking {
            rideFitExporter.throwOnExport = true
            val samples = PreviewRideData.demoRideSamples()
            val rideId = "ride-1"
            rideStore.startSession(PreviewRideData.demoRideSession(rideId))
            rideStore.appendSamples(rideId, samples)
            rideStore.finishSession(rideId, samples.last().timestampEpochSeconds)
            rideStore.saveSummary(rideId, RideSummaryCalculator.calculate(samples))
            val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()

            recorderSessionController.emit(RecorderSessionState.Completed(rideId))
            shadowOf(Looper.getMainLooper()).idle()

            activity.shareSummaryExport()

            assertEquals(
                "Could not export FIT. Your ride is still stored on this phone.",
                ShadowToast.getTextOfLatestToast(),
            )
            assertEquals(SyncState.EXPORT_FAILED, rideStore.loadSummary(rideId)?.exportState)
            assertEquals("Retry FIT Export", activity.currentUiState().summary.exportLabel)
        }

    @Test
    fun startRideRecorderServiceShowsToastWhenForegroundStartThrowsIllegalStateException() {
        rememberedDeviceStore =
            FakeRememberedDeviceStore(
                initialDevices =
                    RememberedDevices(
                        leftPedal = rememberedDevice(1, "Left Pedal", "left-id"),
                        rightPedal = rememberedDevice(2, "Right Pedal", "right-id"),
                        heartRate = rememberedDevice(3, "Heart Rate", "hr-id"),
                    ),
            )
        MainActivity.rememberedDeviceStoreOverride = rememberedDeviceStore
        val activity =
            Robolectric.buildActivity(IllegalStateMainActivity::class.java).setup().get()

        activity.startRideRecorderService()

        assertEquals("Could not start ride recording.", ShadowToast.getTextOfLatestToast())
    }

    @Test
    fun startRideRecorderServiceShowsToastWhenForegroundStartThrowsSecurityException() {
        rememberedDeviceStore =
            FakeRememberedDeviceStore(
                initialDevices =
                    RememberedDevices(
                        leftPedal = rememberedDevice(1, "Left Pedal", "left-id"),
                        rightPedal = rememberedDevice(2, "Right Pedal", "right-id"),
                        heartRate = rememberedDevice(3, "Heart Rate", "hr-id"),
                    ),
            )
        MainActivity.rememberedDeviceStoreOverride = rememberedDeviceStore
        val activity =
            Robolectric.buildActivity(SecurityExceptionMainActivity::class.java).setup().get()

        activity.startRideRecorderService()

        assertEquals("Could not start ride recording.", ShadowToast.getTextOfLatestToast())
    }

    @Test
    fun handleSetupPrimaryActionKeepsSetupScreenWhenForegroundStartFails() =
        runBlocking {
            rememberedDeviceStore =
                FakeRememberedDeviceStore(
                    initialDevices =
                        RememberedDevices(
                            leftPedal = rememberedDevice(1, "Left Pedal", "left-id"),
                            rightPedal = rememberedDevice(2, "Right Pedal", "right-id"),
                            heartRate = rememberedDevice(3, "Heart Rate", "hr-id"),
                        ),
                )
            MainActivity.rememberedDeviceStoreOverride = rememberedDeviceStore
            val activity =
                Robolectric.buildActivity(IllegalStateMainActivity::class.java).setup().get()

            activity.handleSetupPrimaryAction()

            assertEquals(AppScreen.SETUP, activity.currentUiState().currentScreen)
            assertEquals("Could not start ride recording.", ShadowToast.getTextOfLatestToast())
        }

    @Test
    fun handleSetupPrimaryActionShowsAssociationToastWhenPairingFails() =
        runBlocking {
            companionAssociationStarter.nextFailure = "Association failed"
            val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()

            activity.handleSetupPrimaryAction()

            assertEquals("Association failed", ShadowToast.getTextOfLatestToast())
            assertEquals(AppScreen.SETUP, activity.currentUiState().currentScreen)
        }

    @Test
    fun handleLegacyAssociationResultWithNullDeviceClearsPendingRole() =
        runBlocking {
            companionAssociationStarter.yieldPendingChooser = true
            val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()

            activity.handleSetupPrimaryAction()
            assertTrue(activity.currentUiState().setup.overallStatus.startsWith("Searching"))

            activity.handleLegacyAssociationResult(
                androidx.activity.result.ActivityResult(Activity.RESULT_OK, null),
            )

            assertFalse(activity.currentUiState().setup.overallStatus.startsWith("Searching"))
        }

    @Test
    fun handleLegacyAssociationResultCancelClearsPendingRole() =
        runBlocking {
            companionAssociationStarter.yieldPendingChooser = true
            val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()

            activity.handleSetupPrimaryAction()
            activity.handleLegacyAssociationResult(
                androidx.activity.result.ActivityResult(Activity.RESULT_CANCELED, null),
            )

            assertFalse(activity.currentUiState().setup.overallStatus.startsWith("Searching"))
        }

    private fun rememberedDevice(
        associationId: Int,
        displayName: String,
        deviceId: String,
    ) = RememberedDevice(associationId, DeviceAssociation(deviceId = deviceId, displayName = displayName))
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

private class DebugDemoSensorsMainActivity : MainActivity() {
    override fun debugDemoSensorsEnabled(): Boolean = true
}

private class FakeCompanionAssociationStarter : CompanionAssociationStarter {
    val startedRoles = mutableListOf<SetupDeviceRole>()
    val disassociatedIds = mutableListOf<Int>()
    var nextFailure: String? = null
    /** When true, simulates the pre-33 path: only calls onAssociationPending (not onAssociationCreated). */
    var yieldPendingChooser = false

    override fun startAssociation(
        activity: Activity,
        role: SetupDeviceRole,
        onAssociationPending: (IntentSender) -> Unit,
        onAssociationCreated: (RememberedDevice) -> Unit,
        onFailure: (String) -> Unit,
    ) {
        startedRoles += role
        val failure = nextFailure
        if (failure != null) {
            onFailure(failure)
            return
        }

        if (yieldPendingChooser) {
            // Simulate the pre-33 path where success arrives via activity result
            return
        }

        onAssociationCreated(
            RememberedDevice(
                associationId = role.ordinal + 1,
                association =
                    DeviceAssociation(
                        deviceId = "${role.name.lowercase()}-id",
                        displayName = role.label,
                    ),
            ),
        )
    }

    override fun disassociate(
        context: Context,
        rememberedDevice: RememberedDevice,
    ) {
        disassociatedIds += rememberedDevice.associationId
    }
}

private class FakeRideFitExporter : RideFitExporter {
    val exportedUri: Uri = Uri.parse("content://tests/ride.fit")
    var throwOnExport: Boolean = false
    var exportCallCount: Int = 0

    override fun export(
        session: com.sjlangley.peleotonpowermeter.data.model.RideSession,
        samples: List<com.sjlangley.peleotonpowermeter.data.model.RideSample>,
        summary: com.sjlangley.peleotonpowermeter.data.model.DerivedSummary,
    ): ExportedFitFile {
        exportCallCount++
        require(!throwOnExport) { "boom" }

        return ExportedFitFile(
            contentUri = exportedUri,
            fileName = "ride.fit",
        )
    }
}
