package com.nanobuo.myfifty.domain

import java.time.YearMonth

/** Calcoli su divisioni, saldi e statistiche. Pure Kotlin, testato in unit test. */
object Ledger {

    /**
     * Divide [amountCents] in parti uguali tra [participants]. I centesimi avanzati
     * vanno ai primi partecipanti in ordine di id, così il risultato è identico su
     * tutti i dispositivi.
     */
    fun split(amountCents: Long, participants: List<String>): Map<String, Long> {
        val ids = participants.distinct().sorted()
        if (ids.isEmpty()) return emptyMap()
        val base = amountCents / ids.size
        val rest = (amountCents % ids.size).toInt()
        return ids.mapIndexed { i, id -> id to base + if (i < rest) 1 else 0 }.toMap()
    }

    /**
     * Effetto di un movimento sul saldo di ciascuno.
     * Positivo = gli altri gli devono soldi; negativo = deve soldi.
     */
    fun effect(e: Expense): Map<String, Long> {
        val out = HashMap<String, Long>()
        fun add(id: String, v: Long) { out[id] = (out[id] ?: 0L) + v }
        when (e.kindEnum) {
            ExpenseKind.EXPENSE, ExpenseKind.SETTLEMENT -> {
                add(e.paidBy, e.amountCents)
                split(e.amountCents, e.splitAmong).forEach { (id, s) -> add(id, -s) }
            }
            ExpenseKind.REFUND -> {
                add(e.paidBy, -e.amountCents)
                split(e.amountCents, e.splitAmong).forEach { (id, s) -> add(id, s) }
            }
        }
        return out
    }

    fun balances(expenses: List<Expense>, memberIds: Collection<String> = emptyList()): Map<String, Long> {
        val out = LinkedHashMap<String, Long>()
        memberIds.forEach { out[it] = 0L }
        expenses.forEach { e -> effect(e).forEach { (id, v) -> out[id] = (out[id] ?: 0L) + v } }
        return out
    }

    data class Transfer(val from: String, val to: String, val amountCents: Long)

    /** Suggerisce il minor numero (circa) di pagamenti per azzerare i saldi. */
    fun settleUp(balances: Map<String, Long>): List<Transfer> {
        val debtors = balances.filter { it.value < 0 }.map { it.key to -it.value }
            .sortedWith(compareByDescending<Pair<String, Long>> { it.second }.thenBy { it.first }).toMutableList()
        val creditors = balances.filter { it.value > 0 }.map { it.key to it.value }
            .sortedWith(compareByDescending<Pair<String, Long>> { it.second }.thenBy { it.first }).toMutableList()
        val result = mutableListOf<Transfer>()
        var i = 0; var j = 0
        val d = debtors.map { it.second }.toLongArray()
        val c = creditors.map { it.second }.toLongArray()
        while (i < d.size && j < c.size) {
            val x = minOf(d[i], c[j])
            if (x > 0) result += Transfer(debtors[i].first, creditors[j].first, x)
            d[i] -= x; c[j] -= x
            if (d[i] == 0L) i++
            if (c[j] == 0L) j++
        }
        return result
    }

    /** Costo netto del movimento per la casa (spese − rimborsi; i saldi non contano). */
    fun netCost(e: Expense): Long = when (e.kindEnum) {
        ExpenseKind.EXPENSE -> e.amountCents
        ExpenseKind.REFUND -> -e.amountCents
        ExpenseKind.SETTLEMENT -> 0L
    }

    data class Summary(
        val totalCents: Long,
        /** chiave categoria -> totale netto, in ordine decrescente */
        val byCategory: List<Pair<String, Long>>,
        /** quanto ciascuno ha effettivamente pagato (spese pagate − rimborsi incassati) */
        val paidBy: Map<String, Long>,
        /** la quota di costo di ciascuno (la sua parte delle spese − parte dei rimborsi) */
        val shareOf: Map<String, Long>,
        val count: Int,
        val largest: Expense?,
    )

    fun summary(expenses: List<Expense>): Summary {
        val costs = expenses.filter { it.kindEnum != ExpenseKind.SETTLEMENT }
        val byCat = costs.groupBy { it.category }
            .mapValues { (_, l) -> l.sumOf { netCost(it) } }
            .filterValues { it != 0L }
            .toList().sortedByDescending { it.second }
        val paid = HashMap<String, Long>()
        val share = HashMap<String, Long>()
        costs.forEach { e ->
            val sign = if (e.kindEnum == ExpenseKind.REFUND) -1 else 1
            paid[e.paidBy] = (paid[e.paidBy] ?: 0L) + sign * e.amountCents
            split(e.amountCents, e.splitAmong).forEach { (id, s) -> share[id] = (share[id] ?: 0L) + sign * s }
        }
        return Summary(
            totalCents = costs.sumOf { netCost(it) },
            byCategory = byCat,
            paidBy = paid,
            shareOf = share,
            count = costs.size,
            largest = costs.filter { it.kindEnum == ExpenseKind.EXPENSE }.maxByOrNull { it.amountCents },
        )
    }

    /** Totale netto per ciascuno dei mesi richiesti (0 se vuoto). */
    fun monthlyTotals(expenses: List<Expense>, months: List<YearMonth>): List<Pair<YearMonth, Long>> {
        val grouped = expenses.groupBy { it.yearMonth }
        return months.map { m -> m to (grouped[m]?.sumOf { netCost(it) } ?: 0L) }
    }
}

/** Saldo di fine mese: chi deve dare cosa a chi, considerando tutto fino all'ultimo giorno del mese. */
object MonthlySettlement {
    fun closingBalances(expenses: List<Expense>, month: YearMonth, memberIds: Collection<String>): Map<String, Long> =
        Ledger.balances(expenses.filter { !it.yearMonth.isAfter(month) }, memberIds)

    fun transfers(expenses: List<Expense>, month: YearMonth, memberIds: Collection<String>): List<Ledger.Transfer> =
        Ledger.settleUp(closingBalances(expenses, month, memberIds))

    /**
     * Quale mese proporre di saldare oggi, o null:
     *  - negli ultimi [lastDays] giorni del mese: il mese corrente;
     *  - nei primi [firstDays] giorni: il mese precedente, se non è già in pari.
     */
    fun monthToSettle(
        expenses: List<Expense>,
        memberIds: Collection<String>,
        today: java.time.LocalDate,
        lastDays: Int = 3,
        firstDays: Int = 10,
    ): YearMonth? {
        val current = YearMonth.from(today)
        if (today.dayOfMonth > current.lengthOfMonth() - lastDays &&
            transfers(expenses, current, memberIds).isNotEmpty()) return current
        val prev = current.minusMonths(1)
        if (today.dayOfMonth <= firstDays && transfers(expenses, prev, memberIds).isNotEmpty()) return prev
        return null
    }
}
