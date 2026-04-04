package com.sjlangley.peleotonpowermeter.setup

import android.app.Activity
import android.bluetooth.le.ScanFilter
import android.companion.AssociationInfo
import android.companion.AssociationRequest
import android.companion.BluetoothLeDeviceFilter
import android.companion.CompanionDeviceManager
import android.content.Context
import android.content.IntentSender
import android.os.Build
import androidx.annotation.RequiresApi
import com.sjlangley.peleotonpowermeter.data.model.DeviceAssociation

interface CompanionAssociationStarter {
    fun startAssociation(
        activity: Activity,
        role: SetupDeviceRole,
        onAssociationPending: (IntentSender) -> Unit,
        onAssociationCreated: (RememberedDevice) -> Unit,
        onFailure: (String) -> Unit,
    )

    fun disassociate(
        context: Context,
        rememberedDevice: RememberedDevice,
    )
}

class AndroidCompanionAssociationStarter : CompanionAssociationStarter {
    override fun startAssociation(
        activity: Activity,
        role: SetupDeviceRole,
        onAssociationPending: (IntentSender) -> Unit,
        onAssociationCreated: (RememberedDevice) -> Unit,
        onFailure: (String) -> Unit,
    ) {
        val companionDeviceManager = activity.getSystemService(CompanionDeviceManager::class.java)
        if (companionDeviceManager == null) {
            onFailure("Companion device setup is not available on this device.")
            return
        }

        val request =
            AssociationRequest.Builder()
                .setSingleDevice(true)
                .addDeviceFilter(
                    BluetoothLeDeviceFilter.Builder()
                        .setScanFilter(ScanFilter.Builder().build())
                        .build(),
                )
                .build()

        companionDeviceManager.associate(
            request,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Api33AssociationCallback(role, onAssociationPending, onAssociationCreated, onFailure)
            } else {
                LegacyAssociationCallback(onAssociationPending, onFailure)
            },
            null,
        )
    }

    override fun disassociate(
        context: Context,
        rememberedDevice: RememberedDevice,
    ) {
        val companionDeviceManager = context.getSystemService(CompanionDeviceManager::class.java) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            rememberedDevice.associationId != NO_ASSOCIATION_ID
        ) {
            companionDeviceManager.disassociate(rememberedDevice.associationId)
            return
        }

        rememberedDevice.association.deviceId
            .takeIf(::looksLikeMacAddress)
            ?.let(companionDeviceManager::disassociate)
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private class Api33AssociationCallback(
    private val role: SetupDeviceRole,
    private val onPending: (IntentSender) -> Unit,
    private val onCreated: (RememberedDevice) -> Unit,
    private val onFail: (String) -> Unit,
) : CompanionDeviceManager.Callback() {
    override fun onAssociationPending(intentSender: IntentSender) = onPending(intentSender)

    override fun onAssociationCreated(associationInfo: AssociationInfo) =
        onCreated(associationInfo.toRememberedDevice(role))

    override fun onFailure(error: CharSequence?) =
        onFail(error?.toString() ?: "Could not pair ${role.label}.")
}

// On API 31/32 the success result is delivered via activity result (EXTRA_DEVICE), not a callback.
// The launcher in MainActivity is responsible for completing the association on pre-33 devices.
private class LegacyAssociationCallback(
    private val onPending: (IntentSender) -> Unit,
    private val onFail: (String) -> Unit,
) : CompanionDeviceManager.Callback() {
    @Suppress("OVERRIDE_DEPRECATION")
    override fun onDeviceFound(chooserLauncher: IntentSender) = onPending(chooserLauncher)

    override fun onFailure(error: CharSequence?) =
        onFail(error?.toString() ?: "Could not start pairing.")
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun AssociationInfo.toRememberedDevice(role: SetupDeviceRole): RememberedDevice =
    RememberedDevice(
        associationId = id,
        association =
            DeviceAssociation(
                deviceId = getDeviceMacAddress()?.toString()?.uppercase() ?: "association-$id",
                displayName = getDisplayName()?.toString().orEmpty().ifBlank { role.label },
            ),
    )

private fun looksLikeMacAddress(value: String): Boolean =
    MAC_ADDRESS_REGEX.matches(value)

internal const val NO_ASSOCIATION_ID = -1
private val MAC_ADDRESS_REGEX = Regex("([0-9A-F]{2}:){5}[0-9A-F]{2}")
