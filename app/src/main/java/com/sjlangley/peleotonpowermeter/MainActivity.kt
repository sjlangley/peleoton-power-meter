package com.sjlangley.peleotonpowermeter

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.sjlangley.peleotonpowermeter.data.model.SummaryUiState
import com.sjlangley.peleotonpowermeter.data.repo.RideStore
import com.sjlangley.peleotonpowermeter.recorder.RecorderSessionController
import com.sjlangley.peleotonpowermeter.recorder.RideRecorderService
import com.sjlangley.peleotonpowermeter.ui.AppViewModel
import com.sjlangley.peleotonpowermeter.ui.RecorderApp
import com.sjlangley.peleotonpowermeter.ui.theme.PeleotonPowerMeterTheme
import kotlinx.coroutines.launch

open class MainActivity : ComponentActivity() {
    private val rideStore: RideStore by lazy {
        rideStoreOverride ?: (application as PeleotonPowerMeterApp).rideStore
    }

    private val recorderSessionController: RecorderSessionController by lazy {
        recorderSessionControllerOverride ?: (application as PeleotonPowerMeterApp).recorderSessionController
    }

    private val viewModel by viewModels<AppViewModel> {
        AppViewModel.factory(rideStore, recorderSessionController)
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
                    onSetupSecondaryAction = viewModel::onSetupSecondaryAction,
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
            startRideRecorderService()
        }
        viewModel.onSetupPrimaryAction()
    }

    internal suspend fun handleLivePrimaryAction() {
        startService(RideRecorderService.finishIntent(this))
    }

    internal suspend fun handleLiveSecondaryAction() {
        startService(RideRecorderService.toggleDropoutIntent(this))
    }

    internal fun startRideRecorderService() {
        try {
            startForegroundRideRecorder(RideRecorderService.startIntent(this))
        } catch (error: IllegalStateException) {
            Log.w(TAG, "Could not start foreground ride recorder.", error)
            showRideRecorderStartError()
        } catch (error: SecurityException) {
            Log.w(TAG, "Missing permission to start foreground ride recorder.", error)
            showRideRecorderStartError()
        }
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

    internal companion object {
        var rideStoreOverride: RideStore? = null
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
