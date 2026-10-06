package br.com.pompeo.casa.platform

import java.time.LocalDate
import java.time.format.DateTimeFormatter

// java.time sem desugaring porque o minSdk é 26; DateTimeFormatter é imutável e seguro entre threads.
private val MONTH_DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("MM-dd")
private val DAY_MONTH: DateTimeFormatter = DateTimeFormatter.ofPattern("dd-MM")

actual fun weekStrip(): List<WeekDay> {
    val today = LocalDate.now()
    return buildWeekStrip(
        dayOfMonthAt = { offset -> today.plusDays(offset.toLong()).dayOfMonth },
        // DayOfWeek.value já segue a ISO (1 = segunda … 7 = domingo): nada a converter.
        isoDayOfWeekAt = { offset -> today.plusDays(offset.toLong()).dayOfWeek.value },
    )
}

actual fun todayMonthDay(): String = LocalDate.now().format(MONTH_DAY)

actual fun todayDayMonth(): String = LocalDate.now().format(DAY_MONTH)
