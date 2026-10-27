package app.kozica.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class ExifInstantTest {
    private val warsaw = ZoneId.of("Europe/Warsaw")

    @Test
    fun `the camera's own offset wins over the phone's zone`() {
        assertEquals(
            Instant.parse("2026-07-14T07:30:00Z"),
            exifInstant("2026:07:14 09:30:00", "+02:00", ZoneId.of("UTC")),
        )
    }

    @Test
    fun `with no offset the time is read in the phone's zone`() {
        assertEquals(Instant.parse("2026-07-14T07:30:00Z"), exifInstant("2026:07:14 09:30:00", null, warsaw))
        assertEquals(Instant.parse("2026-01-14T08:30:00Z"), exifInstant("2026:01:14 09:30:00", null, warsaw))
    }

    @Test
    fun `an unreadable offset falls back to the zone, an unreadable date to no time at all`() {
        assertEquals(Instant.parse("2026-07-14T07:30:00Z"), exifInstant("2026:07:14 09:30:00", "  :  ", warsaw))
        assertNull(exifInstant("0000:00:00 00:00:00", "+02:00", warsaw))
        assertNull(exifInstant("", null, warsaw))
    }
}
