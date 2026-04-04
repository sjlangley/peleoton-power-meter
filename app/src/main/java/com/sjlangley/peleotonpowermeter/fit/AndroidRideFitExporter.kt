package com.sjlangley.peleotonpowermeter.fit

import android.content.Context
import androidx.core.content.FileProvider
import com.sjlangley.peleotonpowermeter.data.model.DerivedSummary
import com.sjlangley.peleotonpowermeter.data.model.RideSample
import com.sjlangley.peleotonpowermeter.data.model.RideSession
import java.io.File
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

class AndroidRideFitExporter(
    private val context: Context,
    ) : RideFitExporter {
    override fun export(
        session: RideSession,
        samples: List<RideSample>,
        summary: DerivedSummary,
    ): ExportedFitFile {
        val outputFile = outputFileFor(session)
        val parentDirectory = outputFile.parentFile
        if (parentDirectory != null && !parentDirectory.exists()) {
            val created = parentDirectory.mkdirs()
            if (!created && !parentDirectory.exists()) {
                throw IllegalStateException(
                    "Failed to create FIT export directory: ${parentDirectory.absolutePath}",
                )
            }
        }
        FitActivityFileEncoder.writeTo(outputFile, session, samples, summary)

        return ExportedFitFile(
            contentUri =
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    outputFile,
                ),
            fileName = outputFile.name,
        )
    }

    private fun outputFileFor(session: RideSession): File {
        val startedAtLabel =
            FILE_NAME_TIME_FORMATTER.format(
                Instant.ofEpochSecond(session.startedAtEpochSeconds).atOffset(ZoneOffset.UTC),
            )
        val safeRideId =
            session.rideId
                .lowercase()
                .map { character ->
                    if (character.isLetterOrDigit() || character == '-') {
                        character
                    } else {
                        '-'
                    }
                }.joinToString("")
                .trim('-')
                .ifBlank { "ride" }
                .take(32)

        return File(
            File(context.cacheDir, EXPORT_DIRECTORY_NAME),
            "peleoton-$startedAtLabel-$safeRideId.fit",
        )
    }

    private companion object {
        const val EXPORT_DIRECTORY_NAME = "fit_exports"
        val FILE_NAME_TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
    }
}
