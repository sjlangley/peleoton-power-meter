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
import androidx.room.Room
import com.sjlangley.peleotonpowermeter.data.local.AppDatabase
import com.sjlangley.peleotonpowermeter.data.model.SummaryUiState
import com.sjlangley.peleotonpowermeter.data.repo.RoomRideStore
import com.sjlangley.peleotonpowermeter.recorder.RideRecorderService
import com.sjlangley.peleotonpowermeter.ui.AppViewModel
import com.sjlangley.peleotonpowermeter.ui.RecorderApp
import com.sjlangley.peleotonpowermeter.ui.theme.PeleotonPowerMeterTheme
import kotlinx.coroutines.launch

open class MainActivity : ComponentActivity() {
    private val rideStore by lazy {
        val database =
            Room.databaseBuilder(
                applicationContext,
                AppDatabase::class.java,
                DATABASE_NAME,
            ).build()
        RoomRideStore(database.rideDao())
    }

    private val viewModel by viewModels<AppViewModel> {
        AppViewModel.factory(rideStore)
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
                    onLiveSecondaryAction = viewModel::onLiveSecondaryAction,
                    onSummaryExport = ::shareSummaryExport,
                    onSummaryReset = viewModel::onSummaryReset,
                )
            }
        }
    }

    internal suspend fun handleSetupPrimaryAction() {
        val shouldStartRecorder = viewModel.uiState.value.setup.canStartRide
        viewModel.onSetupPrimaryAction()

        if (shouldStartRecorder) {
            startRideRecorderService()
        }
    }

    internal suspend fun handleLivePrimaryAction() {
        stopService(Intent(this, RideRecorderService::class.java))
        viewModel.onLivePrimaryAction()
    }

    internal fun startRideRecorderService() {
        try {
            startForegroundRideRecorder(Intent(this, RideRecorderService::class.java))
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
private const val DATABASE_NAME = "peleoton-power-meter.db"
