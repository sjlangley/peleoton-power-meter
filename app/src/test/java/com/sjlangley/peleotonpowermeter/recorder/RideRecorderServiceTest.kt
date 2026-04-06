package com.sjlangley.peleotonpowermeter.recorder

import android.app.NotificationManager
import android.content.ComponentName
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.sjlangley.peleotonpowermeter.setup.RememberedDeviceStore
import com.sjlangley.peleotonpowermeter.setup.RememberedDevices
import com.sjlangley.peleotonpowermeter.testutil.FakeRecorderSessionController
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RideRecorderServiceTest {
    private lateinit var recorderSessionController: FakeRecorderSessionController
    private lateinit var recorderSessionStateStore: RecorderSessionStateStore
    private lateinit var rememberedDeviceStore: RememberedDeviceStore

    @Before
    fun setUp() {
        recorderSessionController = FakeRecorderSessionController()
        recorderSessionStateStore = RecorderSessionStateStore()
        rememberedDeviceStore = mock()
        
        // Default: no devices paired (use demo controller)
        whenever(rememberedDeviceStore.loadRememberedDevices()).thenReturn(RememberedDevices())
        
        RideRecorderService.recorderSessionControllerOverride = recorderSessionController
        RideRecorderService.recorderSessionStateStoreOverride = recorderSessionStateStore
        RideRecorderService.rememberedDeviceStoreOverride = rememberedDeviceStore
    }

    @After
    fun tearDown() {
        RideRecorderService.recorderSessionControllerOverride = null
        RideRecorderService.recorderSessionStateStoreOverride = null
        RideRecorderService.rememberedDeviceStoreOverride = null
    }

    @Test
    fun onCreateStartsForegroundNotificationAndChannel() {
        val service = Robolectric.buildService(RideRecorderService::class.java).create().get()

        val notificationManager = service.getSystemService(NotificationManager::class.java)
        assertNotNull(notificationManager.getNotificationChannel(RideRecorderService.CHANNEL_ID))
        assertNotNull(shadowOf(notificationManager).allNotifications.single())
    }

    @Test
    fun startActionDelegatesToRecorderController() {
        val service = Robolectric.buildService(RideRecorderService::class.java).create().get()

        service.onStartCommand(RideRecorderService.startDemoRideIntent(service), 0, 1)
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(1, recorderSessionController.startCalls)
    }

    @Test
    fun startRideAction_usesDemoControllerWhenNoDevicesPaired() {
        // rememberedDeviceStore returns empty RememberedDevices (set in setUp)
        val service = Robolectric.buildService(RideRecorderService::class.java).create().get()

        service.onStartCommand(RideRecorderService.startRideIntent(service), 0, 1)
        shadowOf(Looper.getMainLooper()).idle()

        // Should initialize controller and call startRide (not startDemoRide)
        assertEquals(1, recorderSessionController.startRideCalls)
    }

    @Test
    fun toggleActionDelegatesToRecorderController() {
        val service = Robolectric.buildService(RideRecorderService::class.java).create().get()

        // Initialize controller with a start command first
        service.onStartCommand(RideRecorderService.startDemoRideIntent(service), 0, 1)
        shadowOf(Looper.getMainLooper()).idle()

        service.onStartCommand(RideRecorderService.toggleDropoutIntent(service), 0, 1)
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(1, recorderSessionController.toggleCalls)
    }

    @Test
    fun finishActionDelegatesToRecorderController() {
        val service = Robolectric.buildService(RideRecorderService::class.java).create().get()

        // Initialize controller with a start command first
        service.onStartCommand(RideRecorderService.startDemoRideIntent(service), 0, 1)
        shadowOf(Looper.getMainLooper()).idle()

        service.onStartCommand(RideRecorderService.finishIntent(service), 0, 1)
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(1, recorderSessionController.finishCalls)
        assertEquals(1, shadowOf(service).stopSelfId)
    }

    @Test
    fun servicePublishesControllerStateIntoSharedStore() {
        val service = Robolectric.buildService(RideRecorderService::class.java).create().get()

        // Initialize controller with a start command first
        service.onStartCommand(RideRecorderService.startDemoRideIntent(service), 0, 1)
        shadowOf(Looper.getMainLooper()).idle()

        val state =
            RecorderSessionState.Completed(
                rideId = "ride-1",
            )
        recorderSessionController.emit(state)
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(state, recorderSessionStateStore.sessionState.value)
    }

    @Test
    fun manifestDeclaresRecorderAsShortService() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val serviceInfo =
            context.packageManager.getServiceInfo(
                ComponentName(context, RideRecorderService::class.java),
                PackageManager.ComponentInfoFlags.of(0),
            )

        assertEquals(ServiceInfo.FOREGROUND_SERVICE_TYPE_SHORT_SERVICE, serviceInfo.foregroundServiceType)
    }

    @Test
    fun toggleActionWithoutInitializedController_stopsService() {
        val service = Robolectric.buildService(RideRecorderService::class.java).create().get()

        // Send toggle action without initializing controller
        service.onStartCommand(RideRecorderService.toggleDropoutIntent(service), 0, 42)
        shadowOf(Looper.getMainLooper()).idle()

        // Should stop the service
        assertEquals(42, shadowOf(service).stopSelfId)
        assertEquals(0, recorderSessionController.toggleCalls)
    }

    @Test
    fun finishActionWithoutInitializedController_stopsService() {
        val service = Robolectric.buildService(RideRecorderService::class.java).create().get()

        // Send finish action without initializing controller
        service.onStartCommand(RideRecorderService.finishIntent(service), 0, 99)
        shadowOf(Looper.getMainLooper()).idle()

        // Should stop the service
        assertEquals(99, shadowOf(service).stopSelfId)
        assertEquals(0, recorderSessionController.finishCalls)
    }
}
