package com.shilapi.xcertplay.location

import android.location.Location
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class AndroidCarPlayLocationFixTest {
    @Test
    fun ellipsoidAltitudeDoesNotBecomeMeanSeaLevelAltitude() {
        val location = Location("gps").apply {
            latitude = 48.1173
            longitude = 11.5166667
            altitude = 545.4
            time = 1_800_000_000_000L
        }

        val fix = location.toCarPlayLocationFix()

        assertNull(fix.altitudeMeters)
        assertNull(fix.speedMetersPerSecond)
        assertNull(fix.bearingDegrees)
        assertNull(fix.accuracyMeters)
        assertEquals(location.latitude, fix.latitudeDegrees, 0.0)
        assertEquals(location.longitude, fix.longitudeDegrees, 0.0)
        assertEquals(location.time, fix.timestampMillis)
    }

    @Test
    @Config(sdk = [34])
    fun explicitMeanSeaLevelAltitudeIsUsedInsteadOfEllipsoidAltitude() {
        val location = Location("gps").apply {
            altitude = 545.4
            mslAltitudeMeters = 500.0
            speed = 0f
            bearing = 0f
            accuracy = 4.5f
        }

        val fix = location.toCarPlayLocationFix()

        assertEquals(500.0, fix.altitudeMeters!!, 0.0)
        assertEquals(0.0, fix.speedMetersPerSecond!!, 0.0)
        assertEquals(0.0, fix.bearingDegrees!!, 0.0)
        assertEquals(4.5, fix.accuracyMeters!!, 0.0)
        location.removeMslAltitude()
        assertNull(location.toCarPlayLocationFix().altitudeMeters)
    }
}
