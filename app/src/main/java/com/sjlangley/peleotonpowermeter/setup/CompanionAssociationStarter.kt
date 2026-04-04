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
            object : CompanionDeviceManager.Callback() {
                override fun onAssociationPending(intentSender: IntentSender) {
                    onAssociationPending(intentSender)
                }

                override fun onAssociationCreated(associationInfo: AssociationInfo) {
                    onAssociationCreated(companionDeviceManager.toRememberedDevice(role, associationInfo))
                }

                override fun onFailure(error: CharSequence?) {
                    onFailure(error?.toString() ?: "Could not pair ${role.label}.")
                }
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

private fun CompanionDeviceManager.toRememberedDevice(
    role: SetupDeviceRole,
    associationInfo: AssociationInfo,
): RememberedDevice =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        associationInfo.toRememberedDevice(role)
    } else {
        RememberedDevice(
            associationId = NO_ASSOCIATION_ID,
            association =
                DeviceAssociation(
                    deviceId = associations.lastOrNull()?.uppercase() ?: "pending-${role.name.lowercase()}",
                    displayName = role.label,
                ),
        )
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

private const val NO_ASSOCIATION_ID = -1
private val MAC_ADDRESS_REGEX = Regex("([0-9A-F]{2}:){5}[0-9A-F]{2}")
