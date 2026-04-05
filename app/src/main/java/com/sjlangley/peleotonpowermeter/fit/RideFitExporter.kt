package com.sjlangley.peleotonpowermeter.fit

import android.net.Uri
import com.sjlangley.peleotonpowermeter.data.model.DerivedSummary
import com.sjlangley.peleotonpowermeter.data.model.RideSample
import com.sjlangley.peleotonpowermeter.data.model.RideSession

interface RideFitExporter {
    fun export(
        session: RideSession,
        samples: List<RideSample>,
        summary: DerivedSummary,
    ): ExportedFitFile
}

data class ExportedFitFile(
    val contentUri: Uri,
    val fileName: String,
)
