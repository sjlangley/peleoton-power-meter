package com.sjlangley.peleotonpowermeter

import android.app.Activity
import android.bluetooth.BluetoothDevice
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.companion.CompanionDeviceManager
import android.content.Intent
import android.content.IntentSender
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.sjlangley.peleotonpowermeter.data.model.DeviceAssociation
import com.sjlangley.peleotonpowermeter.data.model.SyncState
import com.sjlangley.peleotonpowermeter.data.repo.RideStore
import com.sjlangley.peleotonpowermeter.fit.RideFitExporter
import com.sjlangley.peleotonpowermeter.recorder.RecorderSessionController
import com.sjlangley.peleotonpowermeter.recorder.RideRecorderService
import com.sjlangley.peleotonpowermeter.setup.CompanionAssociationStarter
import com.sjlangley.peleotonpowermeter.setup.NO_ASSOCIATION_ID
import com.sjlangley.peleotonpowermeter.setup.RememberedDevice
import com.sjlangley.peleotonpowermeter.setup.RememberedDeviceStore
import com.sjlangley.peleotonpowermeter.setup.SetupDeviceRole
import com.sjlangley.peleotonpowermeter.ui.AppViewModel
import com.sjlangley.peleotonpowermeter.ui.RecorderApp
import com.sjlangley.peleotonpowermeter.ui.theme.PeleotonPowerMeterTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

open class MainActivity : ComponentActivity() {
    private val associationConfirmationLauncher =
        registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                handleLegacyAssociationResult(result)
            }
            // On API 33+, CompanionDeviceManager delivers the actual success/failure callbacks.
        }

    private val rideStore: RideStore by lazy {
        rideStoreOverride ?: (application as PeleotonPowerMeterApp).rideStore
    }

    private val rememberedDeviceStore: RememberedDeviceStore by lazy {
        rememberedDeviceStoreOverride ?: (application as PeleotonPowerMeterApp).rememberedDeviceStore
    }

    private val companionAssociationStarter: CompanionAssociationStarter by lazy {
        companionAssociationStarterOverride ?: (application as PeleotonPowerMeterApp).companionAssociationStarter
    }

    private val recorderSessionController: RecorderSessionController by lazy {
        recorderSessionControllerOverride ?: (application as PeleotonPowerMeterApp).recorderSessionController
    }

    private val rideFitExporter: RideFitExporter by lazy {
        rideFitExporterOverride ?: (application as PeleotonPowerMeterApp).rideFitExporter
    }

    private val viewModel by viewModels<AppViewModel> {
        AppViewModel.factory(rememberedDeviceStore, rideStore, recorderSessionController)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()

            PeleotonPowerMeterTheme {
                RecorderApp(
                    uiState = uiState,
                    onSetupPrimaryAction = {
                        lifecycleScope.launch {
                            handleSetupPrimaryAction()
                        }
                    },
                    onSetupSecondaryAction = {
                        lifecycleScope.launch {
                            handleSetupSecondaryAction()
                        }
                    },
                    onLivePrimaryAction = {
                        lifecycleScope.launch {
                            handleLivePrimaryAction()
                        }
                    },
                    onLiveSecondaryAction = {
                        lifecycleScope.launch {
                            handleLiveSecondaryAction()
                        }
                    },
                    onSummaryExport = {
                        lifecycleScope.launch {
                            shareSummaryExport()
                        }
                    },
                    onSummaryReset = {
                        recorderSessionController.reset()
                        viewModel.onSummaryReset()
                    },
                )
            }
        }
    }

    internal suspend fun handleSetupPrimaryAction() {
        val shouldStartRecorder = viewModel.uiState.value.setup.canStartRide
        if (shouldStartRecorder) {
            if (startRideRecorderService()) {
                viewModel.onSetupPrimaryAction()
            }
            return
        }

        if (viewModel.isAssociationPending()) {
            return
        }

        val nextRole = viewModel.nextAssociationRole() ?: return
        viewModel.onSetupAssociationRequested(nextRole)
        startDeviceAssociation(nextRole)
    }

    internal suspend fun handleSetupSecondaryAction() {
        viewModel.rememberedDevices().allRememberedDevices().forEach { rememberedDevice ->
            companionAssociationStarter.disassociate(this, rememberedDevice)
        }
        viewModel.onSetupSecondaryAction()
    }

    internal suspend fun handleLivePrimaryAction() {
        startService(RideRecorderService.finishIntent(this))
    }

    internal suspend fun handleLiveSecondaryAction() {
        startService(RideRecorderService.toggleDropoutIntent(this))
    }

    internal fun startRideRecorderService(): Boolean {
        try {
            startForegroundRideRecorder(RideRecorderService.startIntent(this))
            return true
        } catch (error: IllegalStateException) {
            Log.w(TAG, "Could not start foreground ride recorder.", error)
            showRideRecorderStartError()
        } catch (error: SecurityException) {
            Log.w(TAG, "Missing permission to start foreground ride recorder.", error)
            showRideRecorderStartError()
        }

        return false
    }

    internal open fun startForegroundRideRecorder(intent: Intent) {
        startForegroundService(intent)
    }

    internal open fun showRideRecorderStartError() {
        Toast.makeText(
            this,
            "Could not start ride recording.",
            Toast.LENGTH_SHORT,
        ).show()
    }

    internal open fun launchAssociationConfirmation(intentSender: IntentSender) {
        associationConfirmationLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
    }

    internal open fun showAssociationError(role: SetupDeviceRole, message: String) {
        Toast.makeText(
            this,
            message,
            Toast.LENGTH_SHORT,
        ).show()
    }

    internal open fun showFitExportError() {
        Toast.makeText(
            this,
            "Could not export FIT. Your ride is still stored on this phone.",
            Toast.LENGTH_SHORT,
        ).show()
    }

    internal suspend fun shareSummaryExport() {
        val summaryState = viewModel.uiState.value.summary
        val rideId = summaryState.rideId
        if (rideId.isBlank()) {
            showFitExportError()
            return
        }
        try {
            val exportedFit =
                withContext(Dispatchers.IO) {
                    val session = rideStore.loadSession(rideId)
                    val samples = rideStore.loadSamples(rideId)
                    val storedSummary = rideStore.loadSummary(rideId)
                    if (session == null || samples.isEmpty() || storedSummary == null) {
                        null
                    } else {
                        rideFitExporter.export(session, samples, storedSummary)
                    }
                }

            if (exportedFit == null) {
                viewModel.onSummaryExportStateChanged(rideId, SyncState.EXPORT_FAILED)
                showFitExportError()
                return
            }

            val shareIntent =
                Intent(Intent.ACTION_SEND).apply {
                    type = FIT_FILE_MIME_TYPE
                    putExtra(Intent.EXTRA_SUBJECT, "Peleoton ride FIT export")
                    putExtra(Intent.EXTRA_STREAM, exportedFit.contentUri)
                    clipData = ClipData.newRawUri(exportedFit.fileName, exportedFit.contentUri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }

            startActivity(Intent.createChooser(shareIntent, "Export FIT"))
            viewModel.onSummaryExportStateChanged(rideId, SyncState.EXPORTED)
        } catch (error: CancellationException) {
            throw error
        } catch (error: ActivityNotFoundException) {
            Log.w(TAG, "No app is available to receive the FIT export for ride $rideId.", error)
            viewModel.onSummaryExportStateChanged(rideId, SyncState.EXPORT_FAILED)
            showFitExportError()
        } catch (@Suppress("TooGenericExceptionCaught") error: Exception) {
            Log.w(TAG, "Could not export FIT for ride $rideId.", error)
            viewModel.onSummaryExportStateChanged(rideId, SyncState.EXPORT_FAILED)
            showFitExportError()
        }
    }

    internal fun currentUiState() = viewModel.uiState.value

    @Suppress("DEPRECATION", "MissingPermission")
    internal fun handleLegacyAssociationResult(result: ActivityResult) {
        val role = viewModel.pendingAssociationRole() ?: return
        if (result.resultCode == Activity.RESULT_OK) {
            val device: BluetoothDevice? =
                result.data?.getParcelableExtra(CompanionDeviceManager.EXTRA_DEVICE)
            if (device != null) {
                val rememberedDevice =
                    RememberedDevice(
                        associationId = NO_ASSOCIATION_ID,
                        association =
                            DeviceAssociation(
                                deviceId = device.address?.uppercase()
                                    ?: "pending-${role.name.lowercase()}",
                                displayName = device.name?.takeIf { it.isNotBlank() } ?: role.label,
                            ),
                    )
                viewModel.onSetupAssociationSucceeded(role, rememberedDevice)
            } else {
                viewModel.onSetupAssociationFailed()
            }
        } else {
            viewModel.onSetupAssociationFailed()
        }
    }

    private fun startDeviceAssociation(role: SetupDeviceRole) {
        companionAssociationStarter.startAssociation(
            activity = this,
            role = role,
            onAssociationPending = { intentSender ->
                try {
                    launchAssociationConfirmation(intentSender)
                } catch (error: IntentSender.SendIntentException) {
                    Log.w(TAG, "Could not launch association confirmation for ${role.label}.", error)
                    viewModel.onSetupAssociationFailed()
                    showAssociationError(role, "Could not pair ${role.label}.")
                }
            },
            onAssociationCreated = { rememberedDevice ->
                viewModel.onSetupAssociationSucceeded(role, rememberedDevice)
            },
            onFailure = { errorMessage ->
                Log.w(TAG, "Companion association failed for ${role.label}: $errorMessage")
                viewModel.onSetupAssociationFailed()
                showAssociationError(role, errorMessage)
            },
        )
    }

    internal companion object {
        var rideStoreOverride: RideStore? = null
        var rememberedDeviceStoreOverride: RememberedDeviceStore? = null
        var companionAssociationStarterOverride: CompanionAssociationStarter? = null
        var rideFitExporterOverride: RideFitExporter? = null
        var recorderSessionControllerOverride: RecorderSessionController? = null
    }
}

private const val TAG = "MainActivity"
private const val FIT_FILE_MIME_TYPE = "application/octet-stream"
