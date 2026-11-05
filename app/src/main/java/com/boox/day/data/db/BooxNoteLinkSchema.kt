package com.boox.day.data.db

import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Deleting a calendar event clears the note links pointing at it, on every path, including whole
 * calendars and accounts. A foreign key did this until v35, but it also refused links to tablet-only
 * events, which live in local_events. Those are cleared by BooxNoteRepository.handleDeletedEvents.
 */
object BooxNoteLinkSchema {
    const val EVENT_DELETED_TRIGGER = "boox_note_link_event_deleted"

    fun createTriggers(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TRIGGER IF NOT EXISTS `$EVENT_DELETED_TRIGGER`
            AFTER DELETE ON `calendar_events`
            BEGIN
                UPDATE `boox_note_links` SET `linkedEventId` = NULL WHERE `linkedEventId` = old.`id`;
            END
            """.trimIndent()
        )
    }

    val CALLBACK = object : RoomDatabase.Callback() {
        override fun onOpen(db: SupportSQLiteDatabase) {
            super.onOpen(db)
            createTriggers(db)
        }
    }
}
