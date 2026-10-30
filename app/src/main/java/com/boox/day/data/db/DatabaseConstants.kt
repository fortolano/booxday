package com.boox.day.data.db

const val CURRENT_DB_VERSION = 30
const val FIRST_PUBLIC_DB_VERSION = 25
const val APP_DATABASE_FILE_NAME = "app_database"

// IDs per `IN (:ids)` statement. SQLite binds one variable per ID and allows 999 before 3.32
// (Android 10/11) and 32,766 after, so longer lists are split into chunks of this size.
const val SQLITE_ID_CHUNK_SIZE = 900
