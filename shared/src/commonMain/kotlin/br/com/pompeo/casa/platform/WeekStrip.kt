package br.com.pompeo.casa.platform

data class WeekDay(val initial: String, val day: String, val isToday: Boolean)

/** De hoje−6 até hoje+1 (a faixa do app Mibo termina em "amanhã"). */
val WEEK_STRIP_OFFSETS: IntRange = -6..1

/** Iniciais em português a partir do dia ISO (1 = segunda … 7 = domingo). */
fun weekInitial(isoDayOfWeek: Int): String = when (isoDayOfWeek) { 1, 5, 6 -> "S"; 2 -> "T"; 3, 4 -> "Q"; 7 -> "D"; else -> "?" }

/** Pura para ser testada com um calendário falso: recebe funções que respondem o dia do mês e o dia ISO por deslocamento. */
fun buildWeekStrip(dayOfMonthAt: (offset: Int) -> Int, isoDayOfWeekAt: (offset: Int) -> Int): List<WeekDay> =
    WEEK_STRIP_OFFSETS.map { offset -> WeekDay(weekInitial(isoDayOfWeekAt(offset)), dayOfMonthAt(offset).toString().padStart(2, '0'), offset == 0) }

expect fun weekStrip(): List<WeekDay>
/** Hoje em "MM-dd" (abas do hub). */
expect fun todayMonthDay(): String
/** Hoje em "dd-MM" (pílula do histórico). */
expect fun todayDayMonth(): String
