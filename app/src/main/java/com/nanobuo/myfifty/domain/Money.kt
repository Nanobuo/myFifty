package com.nanobuo.myfifty.domain

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

object Money {
    private val locale: Locale = Locale.ITALY

    fun format(cents: Long, currency: String = "EUR"): String {
        val nf = NumberFormat.getCurrencyInstance(locale)
        runCatching { nf.currency = Currency.getInstance(currency) }
        return nf.format(BigDecimal.valueOf(cents, 2))
    }

    fun formatSigned(cents: Long, currency: String = "EUR"): String =
        (if (cents > 0) "+" else if (cents < 0) "−" else "") + format(kotlin.math.abs(cents), currency)

    /** "12,50" / "12.5" / "1.234,56" / "1,234.56" -> centesimi. null se non valido. */
    fun parse(input: String): Long? {
        var s = input.trim().replace("€", "").replace(" ", "").replace(" ", "")
        if (s.isEmpty()) return null
        val lastComma = s.lastIndexOf(',')
        val lastDot = s.lastIndexOf('.')
        s = when {
            lastComma >= 0 && lastDot >= 0 ->
                if (lastComma > lastDot) s.replace(".", "").replace(',', '.') else s.replace(",", "")
            lastComma >= 0 -> s.replace(',', '.')
            else -> s
        }
        return runCatching {
            BigDecimal(s).setScale(2, RoundingMode.HALF_UP).movePointRight(2).longValueExact()
        }.getOrNull()
    }

    /** Centesimi -> "12,50" (senza simbolo), per i campi di input e il CSV. */
    fun plain(cents: Long): String = BigDecimal.valueOf(cents, 2).toPlainString().replace('.', ',')
}
