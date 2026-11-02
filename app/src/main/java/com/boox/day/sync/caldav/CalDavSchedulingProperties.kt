package com.boox.day.sync.caldav

import at.bitfire.dav4jvm.Property
import at.bitfire.dav4jvm.PropertyFactory
import at.bitfire.dav4jvm.PropertyRegistry
import at.bitfire.dav4jvm.XmlUtils
import at.bitfire.dav4jvm.property.HrefListProperty
import org.xmlpull.v1.XmlPullParser

/**
 * RFC 6638 properties dav4jvm 2.2.1 doesn't read, for the calendar a server uses for new events
 * (owner's choice, 2 Nov 2026: a new account's default calendar is the server's own, else the first
 * that isn't shared). Registered once by [CalDavConnection].
 */
internal class ScheduleInboxUrl : HrefListProperty() {
    companion object {
        val NAME = Property.Name(XmlUtils.NS_CALDAV, "schedule-inbox-URL")
    }

    object Factory : HrefListProperty.Factory() {
        override fun getName() = NAME
        override fun create(parser: XmlPullParser) = create(parser, ScheduleInboxUrl())
    }
}

/**
 * The calendar for new events. RFC 6638 wraps its address in DAV:href; iCloud sends it as plain text
 * (seen 2 Nov 2026), so the first text either way.
 */
internal class ScheduleDefaultCalendarUrl(val url: String?) : Property {
    companion object {
        val NAME = Property.Name(XmlUtils.NS_CALDAV, "schedule-default-calendar-URL")
    }

    object Factory : PropertyFactory {
        override fun getName() = NAME

        override fun create(parser: XmlPullParser): ScheduleDefaultCalendarUrl {
            val depth = parser.depth
            var found: String? = null
            var event = parser.eventType
            while (!(event == XmlPullParser.END_TAG && parser.depth == depth) && event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.TEXT && found == null) found = parser.text.trim().ifEmpty { null }
                event = parser.next()
            }
            return ScheduleDefaultCalendarUrl(found)
        }
    }
}

internal object CalDavSchedulingProperties {
    private val registered by lazy { PropertyRegistry.register(listOf(ScheduleInboxUrl.Factory, ScheduleDefaultCalendarUrl.Factory)) }

    fun register() = registered
}

/**
 * The calendar new events go to on a new account: the one the server names as its default, else the
 * first writable calendar that isn't shared, else the first writable one.
 */
internal fun chooseDefaultCalendar(calendars: List<CalDavConnection.RemoteCalendar>, serverDefault: String?): String? {
    val writable = calendars.filter { !it.readOnly }
    return serverDefault?.takeIf { href -> writable.any { it.href == href } }
        ?: writable.firstOrNull { !it.shared }?.href
        ?: writable.firstOrNull()?.href
}
