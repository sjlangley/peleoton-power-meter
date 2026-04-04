package com.sjlangley.peleotonpowermeter.setup

import android.app.Activity
import android.bluetooth.le.ScanFilter
import android.companion.AssociationInfo
import android.companion.AssociationRequest
import android.companion.BluetoothLeDeviceFilter
import android.companion.CompanionDeviceManager
import android.content.Context
import android.content.IntentSender
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
        associationId: Int,
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
            activity.mainExecutor,
            object : CompanionDeviceManager.Callback() {
                override fun onAssociationPending(intentSender: IntentSender) {
                    onAssociationPending(intentSender)
                }

                override fun onAssociationCreated(associationInfo: AssociationInfo) {
                    onAssociationCreated(associationInfo.toRememberedDevice(role))
                }

                override fun onFailure(error: CharSequence?) {
                    onFailure(error?.toString() ?: "Could not pair ${role.label}.")
                }
            },
        )
    }

    override fun disassociate(
        context: Context,
        associationId: Int,
    ) {
        context.getSystemService(CompanionDeviceManager::class.java)?.disassociate(associationId)
    }
}

private fun AssociationInfo.toRememberedDevice(role: SetupDeviceRole): RememberedDevice =
    RememberedDevice(
        associationId = id,
        association =
            DeviceAssociation(
                deviceId = getDeviceMacAddress()?.toString()?.uppercase() ?: "association-$id",
                displayName = getDisplayName()?.toString().orEmpty().ifBlank { role.label },
            ),
    )
