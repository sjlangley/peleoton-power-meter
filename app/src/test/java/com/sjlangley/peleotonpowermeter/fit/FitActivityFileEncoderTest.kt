package com.sjlangley.peleotonpowermeter.fit

import com.garmin.fit.ActivityMesg
import com.garmin.fit.ActivityMesgListener
import com.garmin.fit.Decode
import com.garmin.fit.File
import com.garmin.fit.FileIdMesg
import com.garmin.fit.FileIdMesgListener
import com.garmin.fit.LapMesg
import com.garmin.fit.LapMesgListener
import com.garmin.fit.MesgBroadcaster
import com.garmin.fit.RecordMesg
import com.garmin.fit.RecordMesgListener
import com.garmin.fit.SessionMesg
import com.garmin.fit.SessionMesgListener
import com.garmin.fit.Sport
import com.garmin.fit.SubSport
import com.sjlangley.peleotonpowermeter.data.model.PreviewRideData
import com.sjlangley.peleotonpowermeter.domain.RideSummaryCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

class FitActivityFileEncoderTest {
    @Test
    fun encodeGeneratesValidActivityFitFileFromStoredRideData() {
        val samples = PreviewRideData.demoRideSamples()
        val session =
            PreviewRideData.demoRideSession("ride-1").copy(
                endedAtEpochSeconds = samples.last().timestampEpochSeconds,
            )
        val summary = RideSummaryCalculator.calculate(samples)

        val bytes = FitActivityFileEncoder.encode(session, samples, summary)
        var fileId: FileIdMesg? = null
        var sessionMesg: SessionMesg? = null
        var lapMesg: LapMesg? = null
        var activityMesg: ActivityMesg? = null
        val records = mutableListOf<RecordMesg>()
        val broadcaster = MesgBroadcaster()

        broadcaster.addListener(
            FileIdMesgListener { mesg ->
                fileId = mesg
            },
        )
        broadcaster.addListener(
            SessionMesgListener { mesg ->
                sessionMesg = mesg
            },
        )
        broadcaster.addListener(
            LapMesgListener { mesg ->
                lapMesg = mesg
            },
        )
        broadcaster.addListener(
            ActivityMesgListener { mesg ->
                activityMesg = mesg
            },
        )
        broadcaster.addListener(
            RecordMesgListener { mesg ->
                records += mesg
            },
        )

        assertTrue(Decode().isFileFit(ByteArrayInputStream(bytes)))
        assertTrue(Decode().read(ByteArrayInputStream(bytes)))
        broadcaster.run(ByteArrayInputStream(bytes))

        assertEquals(samples.size, records.size)
        assertNotNull(fileId)
        assertEquals(File.ACTIVITY, fileId?.type)
        assertNotNull(sessionMesg)
        assertEquals(Sport.CYCLING, sessionMesg?.sport)
        assertEquals(SubSport.INDOOR_CYCLING, sessionMesg?.subSport)
        assertEquals(summary.averagePowerWatts, sessionMesg?.avgPower)
        assertNotNull(lapMesg)
        assertEquals(summary.maxPowerWatts, lapMesg?.maxPower)
        assertNotNull(activityMesg)
        assertEquals(1, activityMesg?.numSessions)
        assertEquals(samples.first().totalPowerWatts, records.first().power)
    }
}
