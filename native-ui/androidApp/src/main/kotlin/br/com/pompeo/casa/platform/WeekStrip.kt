package br.com.pompeo.casa.platform

import java.time.LocalDate
import java.time.format.DateTimeFormatter

// Mudou no porte: na raiz WeekDay/weekInitial/buildWeekStrip ficavam em commonMain e weekStrip(), todayMonthDay()
// e todayDayMonth() eram `expect` com `actual` em androidMain (java.time) e iosMain (NSCalendar). Aqui é só
// Android, então são funções normais num arquivo só.

data class WeekDay(val initial: String, val day: String, val isToday: Boolean)

/** De hoje−6 até hoje+1 (a faixa do app Mibo termina em "amanhã"). */
val WEEK_STRIP_OFFSETS: IntRange = -6..1

/** Iniciais em português a partir do dia ISO (1 = segunda … 7 = domingo). */
fun weekInitial(isoDayOfWeek: Int): String = when (isoDayOfWeek) { 1, 5, 6 -> "S"; 2 -> "T"; 3, 4 -> "Q"; 7 -> "D"; else -> "?" }

/** Pura para ser testada com um calendário falso: recebe funções que respondem o dia do mês e o dia ISO por deslocamento. */
fun buildWeekStrip(dayOfMonthAt: (offset: Int) -> Int, isoDayOfWeekAt: (offset: Int) -> Int): List<WeekDay> =
    WEEK_STRIP_OFFSETS.map { offset -> WeekDay(weekInitial(isoDayOfWeekAt(offset)), dayOfMonthAt(offset).toString().padStart(2, '0'), offset == 0) }

// java.time sem desugaring porque o minSdk é 26; DateTimeFormatter é imutável e seguro entre threads.
private val MONTH_DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("MM-dd")
private val DAY_MONTH: DateTimeFormatter = DateTimeFormatter.ofPattern("dd-MM")

fun weekStrip(): List<WeekDay> {
    val today = LocalDate.now()
    return buildWeekStrip(
        dayOfMonthAt = { offset -> today.plusDays(offset.toLong()).dayOfMonth },
        // DayOfWeek.value já segue a ISO (1 = segunda … 7 = domingo): nada a converter.
        isoDayOfWeekAt = { offset -> today.plusDays(offset.toLong()).dayOfWeek.value },
    )
}

/** Hoje em "MM-dd" (abas do hub). */
fun todayMonthDay(): String = LocalDate.now().format(MONTH_DAY)

/** Hoje em "dd-MM" (pílula do histórico). */
fun todayDayMonth(): String = LocalDate.now().format(DAY_MONTH)
