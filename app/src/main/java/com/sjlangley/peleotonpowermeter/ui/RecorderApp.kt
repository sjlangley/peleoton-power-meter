package com.sjlangley.peleotonpowermeter.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sjlangley.peleotonpowermeter.data.model.AppScreen
import com.sjlangley.peleotonpowermeter.data.model.AppUiState
import com.sjlangley.peleotonpowermeter.data.model.ConnectionState
import com.sjlangley.peleotonpowermeter.data.model.LiveRideUiState
import com.sjlangley.peleotonpowermeter.data.model.SetupUiState
import com.sjlangley.peleotonpowermeter.data.model.SummaryUiState

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun RecorderApp(
    uiState: AppUiState,
    onSetupPrimaryAction: () -> Unit,
    onSetupSecondaryAction: () -> Unit,
    onLivePrimaryAction: () -> Unit,
    onLiveSecondaryAction: () -> Unit,
    onSummaryExport: () -> Unit,
    onSummaryReset: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Peleoton Power Meter") },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            when (uiState.currentScreen) {
                AppScreen.SETUP ->
                    SetupScreen(
                        state = uiState.setup,
                        onPrimaryAction = onSetupPrimaryAction,
                        onSecondaryAction = onSetupSecondaryAction,
                    )
                AppScreen.LIVE ->
                    LiveRideScreen(
                        state = uiState.live,
                        onPrimaryAction = onLivePrimaryAction,
                        onSecondaryAction = onLiveSecondaryAction,
                    )
                AppScreen.SUMMARY ->
                    SummaryScreen(
                        state = uiState.summary,
                        onExport = onSummaryExport,
                        onReset = onSummaryReset,
                    )
            }
        }
    }
}

@Composable
private fun SetupScreen(
    state: SetupUiState,
    onPrimaryAction: () -> Unit,
    onSecondaryAction: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            text = "Ready To Ride",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = "Your ride stays on this phone until you export it.",
            style = MaterialTheme.typography.bodyLarge,
        )

        state.devices.forEach { device ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(device.label, style = MaterialTheme.typography.titleMedium)
                    StatusPill(
                        label = device.statusLabel,
                        state = device.state,
                    )
                }
            }
        }

        Text(
            text = state.overallStatus,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.secondary,
        )

        Button(
            onClick = onPrimaryAction,
            modifier = Modifier.fillMaxWidth(),
            enabled = state.primaryActionEnabled,
        ) {
            Text(state.primaryActionLabel)
        }

        Button(onClick = onSecondaryAction, modifier = Modifier.fillMaxWidth()) {
            Text(state.secondaryActionLabel)
        }
    }
}

@Composable
private fun LiveRideScreen(
    state: LiveRideUiState,
    onPrimaryAction: () -> Unit,
    onSecondaryAction: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Recording", style = MaterialTheme.typography.titleMedium)
            Text(state.elapsedLabel, style = MaterialTheme.typography.titleMedium)
        }

        state.truthStrip?.let { truth ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(16.dp),
                    )
                    .padding(16.dp),
            ) {
                Text(
                    text = truth,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        Column {
            Text(
                text = state.powerWatts.toString(),
                fontSize = 72.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 72.sp,
            )
            Text("watts", style = MaterialTheme.typography.titleMedium)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            MetricColumn("Cadence", state.cadenceRpm?.let { "$it rpm" } ?: "--")
            MetricColumn("HR", state.heartRateBpm?.let { "$it bpm" } ?: "--")
        }

        Text(state.zoneLabel, style = MaterialTheme.typography.titleMedium)
        LinearProgressIndicator(
            progress = { state.zoneProgress },
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp),
        )

        Button(onClick = onPrimaryAction, modifier = Modifier.fillMaxWidth()) {
            Text(state.primaryActionLabel)
        }

        Button(onClick = onSecondaryAction, modifier = Modifier.fillMaxWidth()) {
            Text(state.secondaryActionLabel)
        }
    }
}

@Composable
private fun SummaryScreen(
    state: SummaryUiState,
    onExport: () -> Unit,
    onReset: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            text = "Ride Complete",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(text = state.rideLabel, style = MaterialTheme.typography.bodyLarge)

        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            MetricColumn("Avg Power", state.averagePowerLabel)
            MetricColumn("Avg Cadence", state.averageCadenceLabel)
            MetricColumn("Avg HR", state.averageHeartRateLabel)
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "Asymmetry",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Medium,
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .background(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(12.dp),
                        ),
                ) {}

                state.asymmetryIntervals.forEach { interval ->
                    Text(
                        text = "${interval.startLabel}-${interval.endLabel}  Right ${interval.rightPercent}% / Left ${interval.leftPercent}%",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                Text(
                    text = state.asymmetryMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
        }

        Button(onClick = onExport, modifier = Modifier.fillMaxWidth()) {
            Text(state.exportLabel)
        }

        Button(onClick = onReset, modifier = Modifier.fillMaxWidth()) {
            Text(state.resetLabel)
        }
    }
}

@Composable
private fun MetricColumn(
    label: String,
    value: String,
) {
    Column {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Spacer(modifier = Modifier.height(4.dp))
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun StatusPill(
    label: String,
    state: ConnectionState,
) {
    val background =
        when (state) {
            ConnectionState.CONNECTED -> MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
            ConnectionState.SEARCHING -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f)
            ConnectionState.PARTIAL -> MaterialTheme.colorScheme.error.copy(alpha = 0.15f)
            ConnectionState.DISCONNECTED -> MaterialTheme.colorScheme.error.copy(alpha = 0.2f)
        }

    Box(
        modifier = Modifier
            .background(background, RoundedCornerShape(999.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}
