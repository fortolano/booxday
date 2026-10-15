package com.boox.day.sync.outlook

import android.content.ContextWrapper
import com.boox.day.sync.NewEventData
import com.boox.day.sync.UpdateEventData
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OutlookCalendarServiceTest {

    @Test
    fun `outlook create includes attendees when provided`() {
        val service = newService()
        val data = NewEventData(
            calendarId = "cal-1",
            title = "Test",
            date = "2024-01-10",
            startTimeHour = 10,
            startTimeMinute = 0,
            endTimeHour = 11,
            endTimeMinute = 0,
            attendees = "a@b.com, c@d.com"
        )

        val json = invokePrivate(service, "buildEventJson", data)
        val obj = JsonParser.parseString(json).asJsonObject
        assertTrue(obj.has("attendees"))
        val attendees = obj.getAsJsonArray("attendees")
        assertEquals(2, attendees.size())
        assertEquals(
            "a@b.com",
            attendees[0].asJsonObject.getAsJsonObject("emailAddress").get("address").asString
        )
    }

    @Test
    fun `outlook update includes attendees when provided`() {
        val service = newService()
        val data = UpdateEventData(
            eventId = "evt-1",
            calendarId = "cal-1",
            title = "Test",
            date = "2024-01-10",
            startTimeHour = 10,
            startTimeMinute = 0,
            endTimeHour = 11,
            endTimeMinute = 0,
            attendees = "a@b.com, c@d.com"
        )

        val json = invokePrivate(service, "buildUpdateEventJson", data)
        val obj = JsonParser.parseString(json).asJsonObject
        assertTrue(obj.has("attendees"))
        val attendees = obj.getAsJsonArray("attendees")
        assertEquals(2, attendees.size())
        assertEquals(
            "c@d.com",
            attendees[1].asJsonObject.getAsJsonObject("emailAddress").get("address").asString
        )
    }

    private fun invokePrivate(service: OutlookCalendarService, methodName: String, arg: Any): String {
        val method = OutlookCalendarService::class.java.getDeclaredMethod(methodName, arg::class.java)
        method.isAccessible = true
        return method.invoke(service, arg) as String
    }

    private fun newService(): OutlookCalendarService {
        return OutlookCalendarService(OutlookAuthManager(ContextWrapper(null)))
    }
}
