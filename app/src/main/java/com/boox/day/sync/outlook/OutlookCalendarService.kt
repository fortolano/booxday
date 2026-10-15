package com.boox.day.sync.outlook

import com.boox.day.data.model.ProviderCalendar
import com.boox.day.data.model.ProviderEvent
import com.boox.day.sync.NewEventData
import com.boox.day.sync.UpdateEventData
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * Service for interacting with Microsoft Graph API for calendar operations.
 *
 * This class handles all HTTP communication with the Graph API including:
 * - Fetching user profile information
 * - Listing calendars
 * - Fetching events with date range filtering
 * - Creating, updating, and deleting events
 *
 * ## API Reference
 * - Base URL: https://graph.microsoft.com/v1.0
 * - All requests require Bearer token in Authorization header
 * - Dates use ISO 8601 format: yyyy-MM-dd'T'HH:mm:ss
 *
 * ## Error Handling
 * - 401 responses indicate token expiry - caller should refresh and retry
 * - All methods return Result<T> for explicit error handling
 *
 * @param authManager The OutlookAuthManager for token acquisition
 */
class OutlookCalendarService(
    private val authManager: OutlookAuthManager
) {
    companion object {
        private const val TAG = "OutlookCalendarService"
        private const val GRAPH_BASE_URL = "https://graph.microsoft.com/v1.0"
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()
        private val DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")
    }

    private val client = OkHttpClient.Builder()
        .build()

    private val gson = Gson()

    // ═══════════════════════════════════════════════════════════════════════════
    // User Profile
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Fetch user profile information from Graph API.
     *
     * @param accessToken The access token for authentication
     * @return Result containing UserProfile, or failure
     */
    suspend fun fetchUserProfile(accessToken: String): Result<UserProfile> = withContext(Dispatchers.IO) {
        val url = "$GRAPH_BASE_URL/me?\$select=displayName,mail,userPrincipalName"

        try {
            val response = executeRequest(accessToken, url)
            val jsonResponse = gson.fromJson(response, JsonObject::class.java)

            val displayName = jsonResponse.get("displayName")?.asString
            val email = jsonResponse.get("mail")?.asString
                ?: jsonResponse.get("userPrincipalName")?.asString

            Result.success(UserProfile(displayName, email))

        } catch (e: Exception) {
            Log.e(TAG, "Error fetching user profile", e)
            Result.failure(e)
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Calendar Operations
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Fetch all calendars for the authenticated user.
     *
     * @param accessToken The access token for authentication
     * @return Result containing list of ProviderCalendar, or failure
     */
    suspend fun fetchCalendars(accessToken: String): Result<List<ProviderCalendar>> = withContext(Dispatchers.IO) {
        val url = "$GRAPH_BASE_URL/me/calendars"

        try {
            val response = executeRequest(accessToken, url)
            val jsonResponse = gson.fromJson(response, JsonObject::class.java)
            val values = jsonResponse.getAsJsonArray("value")

            val calendars = mutableListOf<ProviderCalendar>()
            for (element in values) {
                val calObj = element.asJsonObject
                val id = calObj.get("id")?.asString ?: continue
                val name = calObj.get("name")?.asString ?: "Unknown"
                val color = calObj.get("hexColor")?.asString
                val isDefault = calObj.get("isDefaultCalendar")?.asBoolean ?: false
                val canEdit = calObj.get("canEdit")?.asBoolean ?: true

                calendars.add(
                    ProviderCalendar.fromOutlookCalendar(
                        id = id,
                        name = name,
                        isDefaultCalendar = isDefault,
                        canEdit = canEdit,
                        color = color
                    )
                )
            }

            Log.d(TAG, "Fetched ${calendars.size} calendars")
            Result.success(calendars)

        } catch (e: Exception) {
            Log.e(TAG, "Error fetching calendars", e)
            Result.failure(e)
        }
    }

    /**
     * Fetch events from specific calendars within a date range.
     *
     * Note: Graph API requires fetching from each calendar individually when
     * filtering by calendar. The /me/calendarview endpoint returns all calendars.
     *
     * @param accessToken The access token for authentication
     * @param calendarIds Set of calendar IDs to fetch events from
     * @param startDate Start of date range (inclusive)
     * @param endDate End of date range (inclusive)
     * @return Result containing list of ProviderEvent, or failure
     */
    suspend fun fetchEvents(
        accessToken: String,
        calendarIds: Set<String>,
        startDate: LocalDate,
        endDate: LocalDate
    ): Result<List<ProviderEvent>> = withContext(Dispatchers.IO) {
        // Convert dates to datetime strings for Graph API
        val startDateTime = startDate.atStartOfDay()
        val endDateTime = endDate.plusDays(1).atStartOfDay() // End of end date

        val startStr = startDateTime.format(DATE_TIME_FORMATTER)
        val endStr = endDateTime.format(DATE_TIME_FORMATTER)

        val allEvents = mutableListOf<ProviderEvent>()
        val failures = mutableListOf<CalendarFetchFailure>()

        try {
            for (calendarId in calendarIds) {
                try {
                    val events = fetchEventsForCalendar(accessToken, calendarId, startStr, endStr)
                    allEvents.addAll(events)
                } catch (e: Exception) {
                    failures.add(CalendarFetchFailure(calendarId, e))
                    logCalendarFetchFailure(calendarId, e)
                }
            }

            if (failures.isNotEmpty()) {
                Log.w(
                    TAG,
                    "Fetched ${allEvents.size} Outlook events with ${failures.size} calendar failure(s)"
                )
                return@withContext Result.failure(
                    PartialCalendarFetchException(
                        partialEvents = allEvents.toList(),
                        failures = failures.toList()
                    )
                )
            }

            Log.d(TAG, "Fetched ${allEvents.size} events from ${calendarIds.size} calendars")
            Result.success(allEvents)

        } catch (e: Exception) {
            Log.e(TAG, "Error fetching events", e)
            Result.failure(e)
        }
    }

    private fun logCalendarFetchFailure(calendarId: String, error: Exception) {
        when (error) {
            is java.net.UnknownHostException ->
                Log.w(TAG, "Fetch events skipped for calendar $calendarId: no internet connection")
            is java.net.SocketTimeoutException ->
                Log.w(TAG, "Fetch events timed out for calendar $calendarId")
            else ->
                Log.e(TAG, "Error fetching events from calendar $calendarId", error)
        }
    }

    /**
     * Fetch events from a single calendar.
     */
    private fun fetchEventsForCalendar(
        accessToken: String,
        calendarId: String,
        startStr: String,
        endStr: String
    ): List<ProviderEvent> {
        val url = "$GRAPH_BASE_URL/me/calendars/$calendarId/calendarview?" +
            "startDateTime=$startStr&endDateTime=$endStr&\$top=500&\$orderby=start/dateTime"

        val events = mutableListOf<ProviderEvent>()
        var nextPageUrl: String? = url

        // Handle pagination
        while (nextPageUrl != null) {
            val response = executeRequest(accessToken, nextPageUrl)
            val jsonResponse = gson.fromJson(response, JsonObject::class.java)
            val values = jsonResponse.getAsJsonArray("value") ?: break

            for (element in values) {
                val eventObj = element.asJsonObject
                val event = parseEvent(eventObj, calendarId)
                if (event != null) {
                    events.add(event)
                }
            }

            // Check for next page
            nextPageUrl = jsonResponse.get("@odata.nextLink")?.asString
        }

        return events
    }

    /**
     * Parse a single event from Graph API JSON response.
     */
    private fun parseEvent(eventObj: JsonObject, calendarId: String): ProviderEvent? {
        try {
            val id = eventObj.get("id")?.asString ?: return null
            val subject = eventObj.get("subject")?.asString ?: ""
            val isAllDay = eventObj.get("isAllDay")?.asBoolean ?: false

            // Parse start time
            val startObj = eventObj.getAsJsonObject("start")
            val startDateTimeStr = startObj?.get("dateTime")?.asString ?: return null
            val startTimeZone = startObj.get("timeZone")?.asString ?: "UTC"
            val startTime = parseGraphDateTime(startDateTimeStr, startTimeZone)

            // Parse end time
            val endObj = eventObj.getAsJsonObject("end")
            val endDateTimeStr = endObj?.get("dateTime")?.asString
            val endTimeZone = endObj?.get("timeZone")?.asString ?: "UTC"
            val endTime = if (endDateTimeStr != null) {
                parseGraphDateTime(endDateTimeStr, endTimeZone)
            } else null

            // Parse location
            val locationObj = eventObj.getAsJsonObject("location")
            val location = locationObj?.get("displayName")?.asString

            // Parse body preview
            val bodyPreview = eventObj.get("bodyPreview")?.asString

            // Parse attendees (simplified)
            val attendeesArray = eventObj.getAsJsonArray("attendees")
            val attendees = if (attendeesArray != null && attendeesArray.size() > 0) {
                attendeesArray.mapNotNull { attendee ->
                    attendee.asJsonObject
                        ?.getAsJsonObject("emailAddress")
                        ?.get("address")?.asString
                }.joinToString(", ")
            } else null

            // Determine if read-only
            val isOrganizer = eventObj.get("isOrganizer")?.asBoolean ?: true

            return ProviderEvent.fromOutlookEvent(
                id = id,
                calendarId = calendarId,
                subject = subject,
                startTime = startTime,
                endTime = endTime,
                isAllDay = isAllDay,
                locationDisplayName = location,
                bodyPreview = bodyPreview,
                attendees = attendees,
                canEdit = isOrganizer
            )

        } catch (e: Exception) {
            Log.e(TAG, "Error parsing event", e)
            return null
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // CRUD Operations
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Create a new event in a calendar.
     *
     * @param accessToken The access token for authentication
     * @param calendarId The calendar to create the event in
     * @param event The event data
     * @return Result containing the created ProviderEvent, or failure
     */
    suspend fun createEvent(
        accessToken: String,
        calendarId: String,
        event: NewEventData
    ): Result<ProviderEvent> = withContext(Dispatchers.IO) {
        val url = "$GRAPH_BASE_URL/me/calendars/$calendarId/events"

        try {
            val eventJson = buildEventJson(event)
            val response = executePostRequest(accessToken, url, eventJson)
            val eventObj = gson.fromJson(response, JsonObject::class.java)
            val createdEvent = parseEvent(eventObj, calendarId)

            if (createdEvent != null) {
                Log.i(TAG, "Created event: ${createdEvent.title}")
                Result.success(createdEvent)
            } else {
                Result.failure(IOException("Failed to parse created event response"))
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error creating event", e)
            Result.failure(e)
        }
    }

    /**
     * Update an existing event.
     *
     * @param accessToken The access token for authentication
     * @param calendarId The calendar containing the event
     * @param eventId The event ID
     * @param event The updated event data
     * @return Result containing the updated ProviderEvent, or failure
     */
    suspend fun updateEvent(
        accessToken: String,
        calendarId: String,
        eventId: String,
        event: UpdateEventData
    ): Result<ProviderEvent> = withContext(Dispatchers.IO) {
        val url = "$GRAPH_BASE_URL/me/calendars/$calendarId/events/$eventId"

        try {
            val eventJson = buildUpdateEventJson(event)
            val response = executePatchRequest(accessToken, url, eventJson)
            val eventObj = gson.fromJson(response, JsonObject::class.java)
            val updatedEvent = parseEvent(eventObj, calendarId)

            if (updatedEvent != null) {
                Log.i(TAG, "Updated event: ${updatedEvent.title}")
                Result.success(updatedEvent)
            } else {
                Result.failure(IOException("Failed to parse updated event response"))
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error updating event $eventId", e)
            Result.failure(e)
        }
    }

    /**
     * Delete an event.
     *
     * @param accessToken The access token for authentication
     * @param calendarId The calendar containing the event
     * @param eventId The event ID
     * @return Result indicating success or failure
     */
    suspend fun deleteEvent(
        accessToken: String,
        calendarId: String,
        eventId: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val url = "$GRAPH_BASE_URL/me/calendars/$calendarId/events/$eventId"

        try {
            executeDeleteRequest(accessToken, url)
            Log.i(TAG, "Deleted event: $eventId")
            Result.success(Unit)

        } catch (e: Exception) {
            Log.e(TAG, "Error deleting event $eventId", e)
            Result.failure(e)
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // HTTP Helpers
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Execute a GET request to the Graph API.
     */
    private fun executeRequest(accessToken: String, url: String): String {
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $accessToken")
            .build()

        val response = client.newCall(request).execute()

        if (!response.isSuccessful) {
            val errorBody = response.body?.string() ?: ""
            throw GraphApiException(response.code, errorBody)
        }

        return response.body?.string() ?: ""
    }

    /**
     * Execute a POST request to the Graph API.
     */
    private fun executePostRequest(accessToken: String, url: String, jsonBody: String): String {
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $accessToken")
            .addHeader("Content-Type", "application/json")
            .post(jsonBody.toRequestBody(JSON_MEDIA_TYPE))
            .build()

        val response = client.newCall(request).execute()

        if (!response.isSuccessful) {
            val errorBody = response.body?.string() ?: ""
            throw GraphApiException(response.code, errorBody)
        }

        return response.body?.string() ?: ""
    }

    /**
     * Execute a PATCH request to the Graph API.
     */
    private fun executePatchRequest(accessToken: String, url: String, jsonBody: String): String {
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $accessToken")
            .addHeader("Content-Type", "application/json")
            .patch(jsonBody.toRequestBody(JSON_MEDIA_TYPE))
            .build()

        val response = client.newCall(request).execute()

        if (!response.isSuccessful) {
            val errorBody = response.body?.string() ?: ""
            throw GraphApiException(response.code, errorBody)
        }

        return response.body?.string() ?: ""
    }

    /**
     * Execute a DELETE request to the Graph API.
     */
    private fun executeDeleteRequest(accessToken: String, url: String) {
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $accessToken")
            .delete()
            .build()

        val response = client.newCall(request).execute()

        if (!response.isSuccessful) {
            val errorBody = response.body?.string() ?: ""
            throw GraphApiException(response.code, errorBody)
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // JSON Builders
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Build JSON body for creating a new event.
     */
    private fun buildEventJson(event: NewEventData): String {
        val timeZone = ZoneId.systemDefault().id

        // Parse date and construct start/end datetime
        val date = LocalDate.parse(event.date)
        val startDateTime = date.atTime(event.startTimeHour, event.startTimeMinute)
        val endDateTime = if (event.endTimeHour != null && event.endTimeMinute != null) {
            date.atTime(event.endTimeHour, event.endTimeMinute)
        } else {
            startDateTime.plusHours(1)
        }

        val json = JsonObject().apply {
            addProperty("subject", event.title)
            add("start", JsonObject().apply {
                addProperty("dateTime", startDateTime.format(DATE_TIME_FORMATTER))
                addProperty("timeZone", timeZone)
            })
            add("end", JsonObject().apply {
                addProperty("dateTime", endDateTime.format(DATE_TIME_FORMATTER))
                addProperty("timeZone", timeZone)
            })
            addProperty("isAllDay", event.isAllDay)
            event.location?.let {
                add("location", JsonObject().apply {
                    addProperty("displayName", it)
                })
            }
            event.description?.let {
                add("body", JsonObject().apply {
                    addProperty("contentType", "text")
                    addProperty("content", it)
                })
            }
            event.attendees?.let { attendeesString ->
                val attendeesList = attendeesString.split(",")
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                if (attendeesList.isNotEmpty()) {
                    val attendeesArray = JsonArray()
                    attendeesList.forEach { email ->
                        attendeesArray.add(JsonObject().apply {
                            add("emailAddress", JsonObject().apply {
                                addProperty("address", email)
                            })
                            addProperty("type", "required")
                        })
                    }
                    add("attendees", attendeesArray)
                }
            }
        }

        return gson.toJson(json)
    }

    /**
     * Build JSON body for updating an event.
     */
    private fun buildUpdateEventJson(event: UpdateEventData): String {
        val timeZone = ZoneId.systemDefault().id
        val json = JsonObject()

        json.addProperty("subject", event.title)

        // Parse date and construct start/end datetime
        val date = LocalDate.parse(event.date)
        val startDateTime = date.atTime(event.startTimeHour, event.startTimeMinute)
        val endDateTime = if (event.endTimeHour != null && event.endTimeMinute != null) {
            date.atTime(event.endTimeHour, event.endTimeMinute)
        } else {
            startDateTime.plusHours(1)
        }

        json.add("start", JsonObject().apply {
            addProperty("dateTime", startDateTime.format(DATE_TIME_FORMATTER))
            addProperty("timeZone", timeZone)
        })
        json.add("end", JsonObject().apply {
            addProperty("dateTime", endDateTime.format(DATE_TIME_FORMATTER))
            addProperty("timeZone", timeZone)
        })

        json.addProperty("isAllDay", event.isAllDay)

        event.location?.let {
            json.add("location", JsonObject().apply {
                addProperty("displayName", it)
            })
        }
        event.description?.let {
            json.add("body", JsonObject().apply {
                addProperty("contentType", "text")
                addProperty("content", it)
            })
        }
        event.attendees?.let { attendeesString ->
            val attendeesList = attendeesString.split(",")
                .map { it.trim() }
                .filter { it.isNotBlank() }
            if (attendeesList.isNotEmpty()) {
                val attendeesArray = JsonArray()
                attendeesList.forEach { email ->
                    attendeesArray.add(JsonObject().apply {
                        add("emailAddress", JsonObject().apply {
                            addProperty("address", email)
                        })
                        addProperty("type", "required")
                    })
                }
                json.add("attendees", attendeesArray)
            }
        }

        return gson.toJson(json)
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Date/Time Parsing
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Parse Graph API datetime string to epoch milliseconds.
     *
     * Graph API returns datetime in format: "2024-01-15T10:00:00.0000000"
     * with a separate timeZone field indicating the timezone.
     */
    private fun parseGraphDateTime(dateTimeStr: String, timeZoneStr: String): Long {
        try {
            // Remove microseconds if present (Graph API sometimes returns .0000000)
            val cleanedDateTimeStr = dateTimeStr.split(".")[0]

            val localDateTime = LocalDateTime.parse(cleanedDateTimeStr, DATE_TIME_FORMATTER)

            val zoneId = try {
                ZoneId.of(timeZoneStr)
            } catch (e: Exception) {
                // Fallback to UTC if timezone is unrecognized
                ZoneId.of("UTC")
            }

            return localDateTime.atZone(zoneId).toInstant().toEpochMilli()

        } catch (e: Exception) {
            Log.e(TAG, "Error parsing datetime: $dateTimeStr ($timeZoneStr)", e)
            return System.currentTimeMillis()
        }
    }
}

/**
 * User profile information from Graph API.
 */
data class UserProfile(
    val displayName: String?,
    val email: String?
)

/**
 * Exception for Graph API errors.
 */
class GraphApiException(
    val statusCode: Int,
    val errorBody: String
) : IOException("Graph API error: $statusCode - $errorBody") {

    /**
     * Check if this is an authentication error (token expired).
     */
    val isAuthError: Boolean
        get() = statusCode == 401

    /**
     * Check if this is a not found error.
     */
    val isNotFound: Boolean
        get() = statusCode == 404

    /**
     * Check if this is a rate limit error.
     */
    val isRateLimited: Boolean
        get() = statusCode == 429

    /**
     * Check if this is a permission error.
     */
    val isPermissionError: Boolean
        get() = statusCode == 403
}

data class CalendarFetchFailure(
    val calendarId: String,
    val error: Throwable
) {
    val message: String
        get() = error.message ?: "Unknown error"
}

class PartialCalendarFetchException(
    val partialEvents: List<ProviderEvent>,
    val failures: List<CalendarFetchFailure>
) : IOException("Failed to fetch ${failures.size} Outlook calendar(s)") {

    val primaryCause: Throwable?
        get() = failures.firstOrNull()?.error

    val errorMessages: List<String>
        get() = failures.map { "${it.calendarId}: ${it.message}" }

    val isAllNetworkFailures: Boolean
        get() = failures.isNotEmpty() && failures.all {
            it.error is java.net.UnknownHostException || it.error is java.net.SocketTimeoutException
        }

    val isAllAuthFailures: Boolean
        get() = failures.isNotEmpty() && failures.all {
            (it.error as? GraphApiException)?.isAuthError == true
        }

    val isAllPermissionFailures: Boolean
        get() = failures.isNotEmpty() && failures.all {
            (it.error as? GraphApiException)?.isPermissionError == true
        }

    val isAllRateLimitFailures: Boolean
        get() = failures.isNotEmpty() && failures.all {
            (it.error as? GraphApiException)?.isRateLimited == true
        }
}
