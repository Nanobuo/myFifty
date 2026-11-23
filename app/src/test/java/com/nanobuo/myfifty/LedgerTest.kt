package com.nanobuo.myfifty

import com.nanobuo.myfifty.domain.Expense
import com.nanobuo.myfifty.domain.Household
import com.nanobuo.myfifty.domain.Ledger
import com.nanobuo.myfifty.domain.Member
import com.nanobuo.myfifty.domain.Money
import com.nanobuo.myfifty.domain.Recurring
import com.nanobuo.myfifty.io.ImportExport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.YearMonth

class LedgerTest {
    private val h = Household("h1", "Casa", "ABCD2345")
    private val members = listOf(Member("a", "Anna"), Member("b", "Bruno"), Member("c", "Carla"))

    private fun e(id: String, kind: String, cents: Long, paid: String, split: List<String>, date: String = "2026-11-05", rec: Boolean = false, desc: String = "x") =
        Expense(id, "h1", kind, desc, "groceries", cents, date, paid, split, rec)

    @Test fun splitDistributesRemainderDeterministically() {
        val s = Ledger.split(1000, listOf("c", "a", "b"))
        assertEquals(mapOf("a" to 334L, "b" to 333L, "c" to 333L), s)
        assertEquals(1000L, s.values.sum())
    }

    @Test fun balancesSumToZero() {
        val list = listOf(
            e("1", "expense", 9000, "a", listOf("a", "b", "c")),
            e("2", "refund", 1500, "b", listOf("a", "b", "c")),
            e("3", "settlement", 2000, "c", listOf("a")),
            e("4", "expense", 1001, "c", listOf("b", "c")),
        )
        val b = Ledger.balances(list, members.map { it.id })
        assertEquals(0L, b.values.sum())
        // a: +9000 -3000 +500 -2000 = 4500
        assertEquals(4500L, b["a"])
    }

    @Test fun settleUpZeroesBalances() {
        val bal = mapOf("a" to 4500L, "b" to -3000L, "c" to -1500L)
        val t = Ledger.settleUp(bal)
        val after = bal.toMutableMap()
        t.forEach { after[it.from] = after[it.from]!! + it.amountCents; after[it.to] = after[it.to]!! - it.amountCents }
        assertTrue(after.values.all { it == 0L })
        assertEquals(2, t.size)
    }

    @Test fun summaryExcludesSettlements() {
        val list = listOf(
            e("1", "expense", 9000, "a", listOf("a", "b", "c")),
            e("2", "refund", 1500, "b", listOf("a", "b", "c")),
            e("3", "settlement", 2000, "c", listOf("a")),
        )
        val s = Ledger.summary(list)
        assertEquals(7500L, s.totalCents)
        assertEquals(2500L, s.shareOf["a"])
        assertEquals(-1500L, s.paidBy["b"])
    }

    @Test fun moneyParsing() {
        assertEquals(1250L, Money.parse("12,50"))
        assertEquals(1250L, Money.parse("12.5"))
        assertEquals(123456L, Money.parse("1.234,56"))
        assertEquals(123456L, Money.parse("1,234.56"))
        assertEquals(-500L, Money.parse("-5"))
        assertNull(Money.parse("abc"))
        assertEquals("12,50", Money.plain(1250))
    }

    @Test fun recurringCopiesOnlyMissing() {
        val list = listOf(
            e("1", "expense", 50000, "a", listOf("a", "b"), "2026-10-31", rec = true, desc = "Affitto"),
            e("2", "expense", 3000, "a", listOf("a", "b"), "2026-10-03", rec = true, desc = "Internet"),
            e("3", "expense", 3000, "a", listOf("a", "b"), "2026-11-03", rec = true, desc = "internet"),
        )
        val pending = Recurring.pending(list, YearMonth.of(2026, 11))
        assertEquals(listOf("1"), pending.map { it.id })
        assertEquals("2026-11-30", Recurring.copyInto(pending[0], YearMonth.of(2026, 11)).date)
    }

    @Test fun jsonRoundTripKeepsIdsInSameHousehold() {
        val list = listOf(e("11111111-1111-1111-1111-111111111111", "expense", 1234, "a", listOf("a", "b")))
        val json = ImportExport.exportJson(h, members, list)
        val r = ImportExport.parse(json, h, members, list)
        assertEquals(1, r.toWrite.size)
        assertEquals(1, r.updatedCount)
        assertEquals(list[0].id, r.toWrite[0].id)
    }

    @Test fun jsonImportIntoOtherHouseholdMapsByName() {
        val list = listOf(e("x1", "expense", 1234, "a", listOf("a", "b")))
        val json = ImportExport.exportJson(h, members, list)
        val other = Household("h2", "Altra")
        val otherMembers = listOf(Member("z", "anna"), Member("y", "Bruno"))
        val r = ImportExport.parse(json, other, otherMembers, emptyList())
        assertEquals(1, r.newCount)
        assertEquals("z", r.toWrite[0].paidBy)
        assertEquals(setOf("z", "y"), r.toWrite[0].splitAmong.toSet())
        assertEquals("h2", r.toWrite[0].householdId)
    }

    @Test fun csvRoundTrip() {
        val list = listOf(
            e("e1", "expense", 1234, "a", listOf("a", "b"), desc = "Spesa; \"grande\""),
            e("e2", "settlement", 500, "b", listOf("a"), desc = "Saldo"),
        )
        val csv = ImportExport.exportCsv(members, list)
        val r = ImportExport.parse(csv, h, members, list)
        assertTrue(r.skipped.toString(), r.skipped.isEmpty())
        assertEquals(2, r.updatedCount)
        assertEquals("Spesa; \"grande\"", r.toWrite[0].description)
        assertEquals("settlement", r.toWrite[1].kind)
    }

    @Test fun csvMinimalWithNegativeAmountIsRefund() {
        val csv = "data,importo,pagato_da,descrizione\n05/11/2026,\"-12,50\",Carla,Conguaglio\n2026-11-06,10,Pippo,x\n"
        val r = ImportExport.parse(csv, h, members, emptyList())
        assertEquals(1, r.toWrite.size)
        assertEquals("refund", r.toWrite[0].kind)
        assertEquals(1250L, r.toWrite[0].amountCents)
        assertEquals(3, r.toWrite[0].splitAmong.size)
        assertEquals(1, r.skipped.size)
    }
}
