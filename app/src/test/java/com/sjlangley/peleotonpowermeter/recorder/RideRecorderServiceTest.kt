package com.sjlangley.peleotonpowermeter.recorder

import android.app.NotificationManager
import android.content.ComponentName
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.sjlangley.peleotonpowermeter.testutil.FakeRecorderSessionController
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RideRecorderServiceTest {
    private lateinit var recorderSessionController: FakeRecorderSessionController
    private lateinit var recorderSessionStateStore: RecorderSessionStateStore

    @Before
    fun setUp() {
        recorderSessionController = FakeRecorderSessionController()
        recorderSessionStateStore = RecorderSessionStateStore()
        RideRecorderService.recorderSessionControllerOverride = recorderSessionController
        RideRecorderService.recorderSessionStateStoreOverride = recorderSessionStateStore
    }

    @After
    fun tearDown() {
        RideRecorderService.recorderSessionControllerOverride = null
        RideRecorderService.recorderSessionStateStoreOverride = null
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

        service.onStartCommand(RideRecorderService.startIntent(service), 0, 1)
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(1, recorderSessionController.startCalls)
    }

    @Test
    fun toggleActionDelegatesToRecorderController() {
        val service = Robolectric.buildService(RideRecorderService::class.java).create().get()

        service.onStartCommand(RideRecorderService.toggleDropoutIntent(service), 0, 1)
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(1, recorderSessionController.toggleCalls)
    }

    @Test
    fun finishActionDelegatesToRecorderController() {
        val service = Robolectric.buildService(RideRecorderService::class.java).create().get()

        service.onStartCommand(RideRecorderService.finishIntent(service), 0, 1)
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(1, recorderSessionController.finishCalls)
        assertEquals(1, shadowOf(service).stopSelfId)
    }

    @Test
    fun servicePublishesControllerStateIntoSharedStore() {
        Robolectric.buildService(RideRecorderService::class.java).create().get()

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
}
