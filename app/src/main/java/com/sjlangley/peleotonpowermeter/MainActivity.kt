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
import com.sjlangley.peleotonpowermeter.data.model.SummaryUiState
import com.sjlangley.peleotonpowermeter.recorder.RideRecorderService
import com.sjlangley.peleotonpowermeter.ui.RecorderApp
import com.sjlangley.peleotonpowermeter.ui.AppViewModel
import com.sjlangley.peleotonpowermeter.ui.theme.PeleotonPowerMeterTheme

class MainActivity : ComponentActivity() {
    private val viewModel by viewModels<AppViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()

            PeleotonPowerMeterTheme {
                RecorderApp(
                    uiState = uiState,
                    onSetupPrimaryAction = ::handleSetupPrimaryAction,
                    onSetupSecondaryAction = viewModel::onSetupSecondaryAction,
                    onLivePrimaryAction = ::handleLivePrimaryAction,
                    onLiveSecondaryAction = viewModel::onLiveSecondaryAction,
                    onSummaryExport = ::shareSummaryExport,
                    onSummaryReset = viewModel::onSummaryReset,
                )
            }
        }
    }

    private fun handleSetupPrimaryAction() {
        val shouldStartRecorder = viewModel.uiState.value.setup.canStartRide
        viewModel.onSetupPrimaryAction()

        if (shouldStartRecorder) {
            startRideRecorderService()
        }
    }

    private fun handleLivePrimaryAction() {
        stopService(Intent(this, RideRecorderService::class.java))
        viewModel.onLivePrimaryAction()
    }

    private fun startRideRecorderService() {
        try {
            startForegroundService(Intent(this, RideRecorderService::class.java))
        } catch (error: IllegalStateException) {
            Log.w(TAG, "Could not start foreground ride recorder.", error)
            Toast.makeText(
                this,
                "Could not start ride recording.",
                Toast.LENGTH_SHORT,
            ).show()
        } catch (error: SecurityException) {
            Log.w(TAG, "Missing permission to start foreground ride recorder.", error)
            Toast.makeText(
                this,
                "Could not start ride recording.",
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    private fun shareSummaryExport() {
        val summary = viewModel.uiState.value.summary
        val shareIntent =
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "Peleoton demo ride summary")
                putExtra(Intent.EXTRA_TEXT, summary.asShareText())
            }

        startActivity(Intent.createChooser(shareIntent, "Share demo ride summary"))
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
