package com.boox.day.io

import com.boox.day.data.datastore.AppSettings
import com.boox.day.data.db.DayPageRepository
import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/** What the Export dialog can cover (Nov 2026). */
@Serializable
enum class ExportWhat { PAGE, DAY, WEEK, DATES }

/** What it can make: a PDF, a PNG for each page, or Markdown notes. */
@Serializable
enum class ExportKind { PDF, IMAGES, MARKDOWN }

/** The dialog's choices, remembered for next time ([AppSettings.exportChoices]). */
@Serializable
data class ExportChoices(
    val what: ExportWhat = ExportWhat.DAY,
    val kind: ExportKind = ExportKind.PDF,
    /** Fit more on each page; off is one memo per page. */
    val fitMore: Boolean = true,
    val typedText: Boolean = false,
    val eventsAndTasks: Boolean = false,
    val pageImages: Boolean = true
)

/** The dates a choice covers, and the memo pages in them. */
object ExportScope {
    const val MAX_DAYS = 31

    /** The week [day] falls in, starting on the first day of the week from Settings. */
    fun weekOf(day: LocalDate, firstDay: AppSettings.FirstDayOfWeek): ClosedRange<LocalDate> {
        val start = day.with(TemporalAdjusters.previousOrSame(firstDay.dayOfWeek))
        return start..start.plusDays(6)
    }

    /** True when [from] to [to] is a range the dialog can export. */
    fun isValid(from: LocalDate, to: LocalDate): Boolean =
        !to.isBefore(from) && ChronoUnit.DAYS.between(from, to) < MAX_DAYS

    /**
     * The days from [from] to [to] that have memo pages, in order. Read only: looking at a date
     * never makes a day or a page for it.
     */
    fun resolve(repository: DayPageRepository, from: LocalDate, to: LocalDate): ExportTarget.Days {
        val days = generateSequence(from) { it.plusDays(1) }
            .takeWhile { !it.isAfter(to) }
            .mapNotNull { date ->
                val day = date.toString()
                repository.getByDate(day)?.pageIds?.takeIf { it.isNotEmpty() }?.let { ExportTarget.DayPages(day, it) }
            }
            .toList()
        return ExportTarget.Days(from.toString(), to.toString(), days)
    }
}
