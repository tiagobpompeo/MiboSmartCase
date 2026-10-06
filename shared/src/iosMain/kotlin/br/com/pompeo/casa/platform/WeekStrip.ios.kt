package br.com.pompeo.casa.platform

import platform.Foundation.NSCalendar
import platform.Foundation.NSCalendarUnitDay
import platform.Foundation.NSCalendarUnitWeekday
import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSLocale

private const val POSIX_LOCALE = "en_US_POSIX"
private const val NS_SUNDAY = 1
private const val ISO_SUNDAY = 7

actual fun weekStrip(): List<WeekDay> {
    val calendar = NSCalendar.currentCalendar
    val today = NSDate()
    // Somar dias pelo calendário (e não 86 400 s) respeita a troca de horário de verão.
    fun dateAt(offset: Int): NSDate =
        calendar.dateByAddingUnit(NSCalendarUnitDay, value = offset.toLong(), toDate = today, options = 0uL) ?: today
    return buildWeekStrip(
        dayOfMonthAt = { offset -> calendar.component(NSCalendarUnitDay, fromDate = dateAt(offset)).toInt() },
        isoDayOfWeekAt = { offset -> isoWeekday(calendar.component(NSCalendarUnitWeekday, fromDate = dateAt(offset)).toInt()) },
    )
}

actual fun todayMonthDay(): String = formatToday("MM-dd")

actual fun todayDayMonth(): String = formatToday("dd-MM")

/** NSCalendar numera 1 = domingo … 7 = sábado; a ISO usa 1 = segunda … 7 = domingo. */
private fun isoWeekday(weekday: Int): Int = if (weekday == NS_SUNDAY) ISO_SUNDAY else weekday - 1

// en_US_POSIX: sem ele a preferência regional do iPhone (ex.: calendário não gregoriano) muda o padrão.
private fun formatToday(pattern: String): String =
    NSDateFormatter().apply {
        locale = NSLocale(localeIdentifier = POSIX_LOCALE)
        dateFormat = pattern
    }.stringFromDate(NSDate())
