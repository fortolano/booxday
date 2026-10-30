package com.boox.day.today

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

/** The line daily notes and the sleep screen show in Preferences (Oct 2026, plan B2). */
class LastWriteTest {

    private val zone = ZoneId.of("America/Vancouver")
    private fun at(y: Int, mo: Int, d: Int, h: Int, mi: Int) = LocalDateTime.of(y, mo, d, h, mi).atZone(zone).toInstant().toEpochMilli()
    private val now = at(2026, 10, 29, 18, 30)

    private fun text(record: LastWrite.Record, use24Hour: Boolean = true) =
        LastWrite.statusText(record, done = "written", use24Hour = use24Hour, nowMs = now, zone = zone)

    @Test
    fun neverWritten() {
        assertEquals("Not written yet", text(LastWrite.Record(0, 0, null)))
    }

    @Test
    fun lastSuccessTodayYesterdayEarlier() {
        assertEquals("Last written today at 18:05", text(LastWrite.Record(at(2026, 10, 29, 18, 5), 0, null)))
        assertEquals("Last written yesterday at 12:05 AM", text(LastWrite.Record(at(2026, 10, 28, 0, 5), 0, null), use24Hour = false))
        assertEquals("Last written on Mon 26 Oct at 09:00", text(LastWrite.Record(at(2026, 10, 26, 9, 0), 0, null)))
    }

    @Test
    fun aNewerFailureSaysWhy() {
        val record = LastWrite.Record(at(2026, 10, 28, 0, 5), at(2026, 10, 29, 18, 5), "the folder can't be written to")
        assertEquals("Last try failed today at 18:05: the folder can't be written to", text(record))
        // A success after the failure wins
        assertEquals("Last written today at 18:20", text(record.copy(okMs = at(2026, 10, 29, 18, 20))))
    }
}
