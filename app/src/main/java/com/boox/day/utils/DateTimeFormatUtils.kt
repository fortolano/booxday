package com.boox.day.utils

import com.boox.day.data.datastore.AppSettings
import com.boox.day.data.datastore.GlobalAppSettings
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Centralized date/time formatting utilities that respect user settings.
 * All formatting throughout the app should use these functions.
 */
object DateTimeFormatUtils {

    // ========== TIME FORMATTING ==========

    /**
     * Format a time (LocalTime) according to user's time format preference.
     * @param time LocalTime to format
     * @param timeFormat Optional override; defaults to GlobalAppSettings
     * @return Formatted time string (e.g., "2:30 PM" or "14:30")
     */
    fun formatTime(
        time: LocalTime,
        timeFormat: AppSettings.TimeFormat = GlobalAppSettings.current.timeFormat
    ): String {
        val pattern = when (timeFormat) {
            AppSettings.TimeFormat.TwelveHour -> "h:mm a"
            AppSettings.TimeFormat.TwentyFourHour -> "HH:mm"
        }
        return time.format(DateTimeFormatter.ofPattern(pattern, Locale.getDefault()))
    }

    /**
     * Format a time from epoch milliseconds according to user's time format preference.
     * @param timeMillis Epoch milliseconds
     * @param timeFormat Optional override; defaults to GlobalAppSettings
     * @return Formatted time string
     */
    fun formatTimeFromMillis(
        timeMillis: Long,
        timeFormat: AppSettings.TimeFormat = GlobalAppSettings.current.timeFormat
    ): String {
        return try {
            val localTime = Instant.ofEpochMilli(timeMillis)
                .atZone(ZoneId.systemDefault())
                .toLocalTime()
            formatTime(localTime, timeFormat)
        } catch (e: Exception) {
            ""
        }
    }

    /**
     * Format a time range (start - end) according to user's time format preference.
     * @param startMillis Start time in epoch milliseconds
     * @param endMillis End time in epoch milliseconds (nullable)
     * @param timeFormat Optional override; defaults to GlobalAppSettings
     * @return Formatted range string (e.g., "2:30 PM - 3:30 PM" or "14:30 - 15:30")
     */
    fun formatTimeRange(
        startMillis: Long,
        endMillis: Long?,
        timeFormat: AppSettings.TimeFormat = GlobalAppSettings.current.timeFormat
    ): String {
        return try {
            val startTime = formatTimeFromMillis(startMillis, timeFormat)
            if (endMillis != null) {
                val endTime = formatTimeFromMillis(endMillis, timeFormat)
                "$startTime - $endTime"
            } else {
                startTime
            }
        } catch (e: Exception) {
            "Unknown time"
        }
    }

    /**
     * Format hour and minute for display in time selectors.
     * @param hour Hour (0-23)
     * @param minute Minute (0-59)
     * @param timeFormat Optional override; defaults to GlobalAppSettings
     * @return Formatted time string
     */
    fun formatHourMinute(
        hour: Int,
        minute: Int,
        timeFormat: AppSettings.TimeFormat = GlobalAppSettings.current.timeFormat
    ): String {
        val time = LocalTime.of(hour, minute)
        return formatTime(time, timeFormat)
    }

    /**
     * Get hour display value for time selector based on format.
     * For 12-hour: returns 1-12
     * For 24-hour: returns 0-23
     */
    fun getDisplayHour(
        hour: Int,
        timeFormat: AppSettings.TimeFormat = GlobalAppSettings.current.timeFormat
    ): Int {
        return when (timeFormat) {
            AppSettings.TimeFormat.TwelveHour -> {
                when {
                    hour == 0 -> 12
                    hour > 12 -> hour - 12
                    else -> hour
                }
            }
            AppSettings.TimeFormat.TwentyFourHour -> hour
        }
    }

    /**
     * Get AM/PM indicator for a given hour.
     * Returns empty string for 24-hour format.
     */
    fun getAmPm(
        hour: Int,
        timeFormat: AppSettings.TimeFormat = GlobalAppSettings.current.timeFormat
    ): String {
        return when (timeFormat) {
            AppSettings.TimeFormat.TwelveHour -> if (hour < 12) "AM" else "PM"
            AppSettings.TimeFormat.TwentyFourHour -> ""
        }
    }

    /**
     * Check if current format is 24-hour.
     */
    fun is24HourFormat(
        timeFormat: AppSettings.TimeFormat = GlobalAppSettings.current.timeFormat
    ): Boolean {
        return timeFormat == AppSettings.TimeFormat.TwentyFourHour
    }

    // ========== DATE FORMATTING ==========

    /**
     * Format a date (LocalDate) according to user's date format preference.
     * @param date LocalDate to format
     * @param dateFormat Optional override; defaults to GlobalAppSettings
     * @param includeYear Whether to include the year
     * @return Formatted date string
     */
    fun formatDate(
        date: LocalDate,
        dateFormat: AppSettings.DateFormat = GlobalAppSettings.current.dateFormat,
        includeYear: Boolean = true
    ): String {
        val pattern = when (dateFormat) {
            AppSettings.DateFormat.MDY -> if (includeYear) "MM/dd/yyyy" else "MM/dd"
            AppSettings.DateFormat.DMY -> if (includeYear) "dd/MM/yyyy" else "dd/MM"
        }
        return date.format(DateTimeFormatter.ofPattern(pattern, Locale.getDefault()))
    }

    /**
     * Format a date for full display (e.g., "Monday, January 15, 2024" or "Monday, 15 January 2024").
     * @param date LocalDate to format
     * @param dateFormat Optional override; defaults to GlobalAppSettings
     * @return Full formatted date string
     */
    fun formatDateFull(
        date: LocalDate,
        dateFormat: AppSettings.DateFormat = GlobalAppSettings.current.dateFormat
    ): String {
        val pattern = when (dateFormat) {
            AppSettings.DateFormat.MDY -> "EEEE, MMMM d, yyyy"
            AppSettings.DateFormat.DMY -> "EEEE, d MMMM yyyy"
        }
        return date.format(DateTimeFormatter.ofPattern(pattern, Locale.getDefault()))
    }

    /**
     * Format a date from ISO string (yyyy-MM-dd) for full display.
     * @param dayId Date string in ISO format
     * @param dateFormat Optional override; defaults to GlobalAppSettings
     * @return Full formatted date string
     */
    fun formatDateFullFromString(
        dayId: String,
        dateFormat: AppSettings.DateFormat = GlobalAppSettings.current.dateFormat
    ): String {
        return try {
            val localDate = LocalDate.parse(dayId)
            formatDateFull(localDate, dateFormat)
        } catch (e: Exception) {
            dayId
        }
    }

    /**
     * Format a date for short display (e.g., "Jan 15" or "15 Jan").
     * @param date LocalDate to format
     * @param dateFormat Optional override; defaults to GlobalAppSettings
     * @return Short formatted date string
     */
    fun formatDateShort(
        date: LocalDate,
        dateFormat: AppSettings.DateFormat = GlobalAppSettings.current.dateFormat
    ): String {
        val pattern = when (dateFormat) {
            AppSettings.DateFormat.MDY -> "MMM d"
            AppSettings.DateFormat.DMY -> "d MMM"
        }
        return date.format(DateTimeFormatter.ofPattern(pattern, Locale.getDefault()))
    }
}
