package com.nanobuo.myfifty.domain

import java.time.YearMonth
import java.util.UUID

object Recurring {
    private fun key(e: Expense) = Triple(e.kind, e.category, e.description.trim().lowercase())

    /**
     * Movimenti ricorrenti del mese precedente a [target] che non sono ancora
     * presenti in [target] (stessa descrizione, categoria e tipo).
     */
    fun pending(all: List<Expense>, target: YearMonth): List<Expense> {
        val prev = target.minusMonths(1)
        val existing = all.filter { it.yearMonth == target }.map(::key).toSet()
        return all.filter { it.recurring && it.yearMonth == prev && key(it) !in existing }
            .distinctBy(::key)
    }

    fun copyInto(e: Expense, target: YearMonth): ExpenseWrite {
        val day = minOf(e.localDate.dayOfMonth, target.lengthOfMonth())
        return e.toWrite().copy(id = UUID.randomUUID().toString(), date = target.atDay(day).toString())
    }
}
