package com.sjlangley.peleotonpowermeter

import android.content.Intent
import android.content.IntentSender
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.sjlangley.peleotonpowermeter.data.model.SummaryUiState
import com.sjlangley.peleotonpowermeter.data.repo.RideStore
import com.sjlangley.peleotonpowermeter.recorder.RecorderSessionController
import com.sjlangley.peleotonpowermeter.recorder.RideRecorderService
import com.sjlangley.peleotonpowermeter.setup.CompanionAssociationStarter
import com.sjlangley.peleotonpowermeter.setup.RememberedDeviceStore
import com.sjlangley.peleotonpowermeter.setup.SetupDeviceRole
import com.sjlangley.peleotonpowermeter.ui.AppViewModel
import com.sjlangley.peleotonpowermeter.ui.RecorderApp
import com.sjlangley.peleotonpowermeter.ui.theme.PeleotonPowerMeterTheme
import kotlinx.coroutines.launch

open class MainActivity : ComponentActivity() {
    private val associationConfirmationLauncher =
        registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
            // CompanionDeviceManager delivers the actual success/failure callbacks.
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
                    onSummaryExport = ::shareSummaryExport,
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

    internal open fun showAssociationError(role: SetupDeviceRole) {
        Toast.makeText(
            this,
            "Could not pair ${role.label}.",
            Toast.LENGTH_SHORT,
        ).show()
    }

    internal fun shareSummaryExport() {
        val summary = viewModel.uiState.value.summary
        val shareIntent =
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "Peleoton demo ride summary")
                putExtra(Intent.EXTRA_TEXT, summary.asShareText())
            }

        startActivity(Intent.createChooser(shareIntent, "Share demo ride summary"))
    }

    internal fun currentUiState() = viewModel.uiState.value

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
                    showAssociationError(role)
                }
            },
            onAssociationCreated = { rememberedDevice ->
                viewModel.onSetupAssociationSucceeded(role, rememberedDevice)
            },
            onFailure = {
                Log.w(TAG, "Companion association failed for ${role.label}: $it")
                viewModel.onSetupAssociationFailed()
                showAssociationError(role)
            },
        )
    }

    internal companion object {
        var rideStoreOverride: RideStore? = null
        var rememberedDeviceStoreOverride: RememberedDeviceStore? = null
        var companionAssociationStarterOverride: CompanionAssociationStarter? = null
        var recorderSessionControllerOverride: RecorderSessionController? = null
    }
}

private fun SummaryUiState.asShareText(): String =
    buildString {
        appendLine(rideLabel)
        appendLine("Avg Power: $averagePowerLabel")
        appendLine("Avg Cadence: $averageCadenceLabel")
        appendLine("Avg HR: $averageHeartRateLabel")
        appendLine()
        appendLine("Asymmetry")
        asymmetryIntervals.forEach { interval ->
            appendLine(
                "${interval.startLabel}-${interval.endLabel}: " +
                    "Right ${interval.rightPercent}% / Left ${interval.leftPercent}%",
            )
        }
        appendLine(asymmetryMessage)
        appendLine()
        append("Demo scaffold: FIT export is not wired yet, so this shares the ride summary.")
    }

private const val TAG = "MainActivity"
