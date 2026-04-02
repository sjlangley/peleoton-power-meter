package com.sjlangley.peleotonpowermeter

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
                    onSetupPrimaryAction = viewModel::onSetupPrimaryAction,
                    onSetupSecondaryAction = viewModel::onSetupSecondaryAction,
                    onLivePrimaryAction = viewModel::onLivePrimaryAction,
                    onLiveSecondaryAction = viewModel::onLiveSecondaryAction,
                    onSummaryReset = viewModel::onSummaryReset,
                )
            }
        }
    }
}
