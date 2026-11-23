package com.nanobuo.myfifty.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.YearMonth

@Serializable
data class Household(
    val id: String,
    val name: String,
    @SerialName("invite_code") val inviteCode: String = "",
    val currency: String = "EUR",
    @SerialName("created_by") val createdBy: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
)

data class Member(
    val id: String,
    val name: String,
    val role: String = "member",
)

enum class ExpenseKind(val key: String, val label: String) {
    EXPENSE("expense", "Spesa"),
    REFUND("refund", "Rimborso"),
    SETTLEMENT("settlement", "Saldo tra membri");

    companion object {
        fun fromKey(key: String): ExpenseKind = entries.firstOrNull { it.key == key } ?: EXPENSE
    }
}

/** Movimento così come è salvato su Supabase (tabella `expenses`). */
@Serializable
data class Expense(
    val id: String,
    @SerialName("household_id") val householdId: String,
    val kind: String = ExpenseKind.EXPENSE.key,
    val description: String = "",
    val category: String = Category.OTHER.key,
    @SerialName("amount_cents") val amountCents: Long,
    @SerialName("expense_date") val date: String,
    @SerialName("paid_by") val paidBy: String,
    @SerialName("split_among") val splitAmong: List<String>,
    val recurring: Boolean = false,
    val notes: String? = null,
    @SerialName("created_by") val createdBy: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_by") val updatedBy: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
) {
    val kindEnum: ExpenseKind get() = ExpenseKind.fromKey(kind)
    val localDate: LocalDate get() = LocalDate.parse(date.take(10))
    val yearMonth: YearMonth get() = YearMonth.from(localDate)
    val categoryEnum: Category get() = Category.fromKey(category)
}

/** Payload di scrittura (insert / update / upsert). */
@Serializable
data class ExpenseWrite(
    val id: String? = null,
    @SerialName("household_id") val householdId: String,
    val kind: String,
    val description: String,
    val category: String,
    @SerialName("amount_cents") val amountCents: Long,
    @SerialName("expense_date") val date: String,
    @SerialName("paid_by") val paidBy: String,
    @SerialName("split_among") val splitAmong: List<String>,
    val recurring: Boolean,
    val notes: String,
)

fun Expense.toWrite(): ExpenseWrite = ExpenseWrite(
    id = id, householdId = householdId, kind = kind, description = description,
    category = category, amountCents = amountCents, date = date, paidBy = paidBy,
    splitAmong = splitAmong, recurring = recurring, notes = notes.orEmpty(),
)
