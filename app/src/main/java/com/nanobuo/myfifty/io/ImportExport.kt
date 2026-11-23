package com.nanobuo.myfifty.io

import com.nanobuo.myfifty.domain.Category
import com.nanobuo.myfifty.domain.Expense
import com.nanobuo.myfifty.domain.ExpenseKind
import com.nanobuo.myfifty.domain.ExpenseWrite
import com.nanobuo.myfifty.domain.Household
import com.nanobuo.myfifty.domain.Member
import com.nanobuo.myfifty.domain.Money
import com.nanobuo.myfifty.domain.resolve

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * Formati supportati:
 *  - JSON (backup completo, reimportabile senza duplicati)
 *  - CSV con separatore `;` (apribile con Excel / Fogli Google)
 */
object ImportExport {

    @Serializable
    data class ExportMember(val id: String, val name: String)

    @Serializable
    data class ExportHousehold(val id: String, val name: String, val currency: String)

    @Serializable
    data class ExportCategory(val key: String, val name: String, val icon: String = "tag", val color: String = "#8A8F98")

    @Serializable
    data class ExportExpense(
        val id: String,
        val kind: String,
        val description: String,
        val category: String,
        val amountCents: Long,
        val date: String,
        val paidBy: String,
        val splitAmong: List<String>,
        val recurring: Boolean = false,
        val notes: String = "",
    )

    @Serializable
    data class ExportFile(
        val format: String = FORMAT,
        val version: Int = 1,
        val exportedAt: String,
        val household: ExportHousehold,
        val members: List<ExportMember>,
        val categories: List<ExportCategory> = emptyList(),
        val expenses: List<ExportExpense>,
    )

    const val FORMAT = "myfifty"
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true; encodeDefaults = true }

    data class ImportResult(
        /** categorie da creare prima di scrivere i movimenti */
        val newCategories: List<Category>,
        val toWrite: List<ExpenseWrite>,
        val newCount: Int,
        val updatedCount: Int,
        val skipped: List<String>,
    )

    // ------------------------------------------------------------- export

    fun exportJson(h: Household, members: List<Member>, categories: List<Category>, expenses: List<Expense>): String =
        json.encodeToString(
            ExportFile.serializer(),
            ExportFile(
                exportedAt = Instant.now().toString(),
                household = ExportHousehold(h.id, h.name, h.currency),
                members = members.map { ExportMember(it.id, it.name) },
                categories = categories.map { ExportCategory(it.key, it.name, it.icon, it.color) },
                expenses = expenses.map {
                    ExportExpense(it.id, it.kind, it.description, it.category, it.amountCents, it.date.take(10),
                        it.paidBy, it.splitAmong, it.recurring, it.notes.orEmpty())
                },
            )
        )

    val CSV_HEADER = listOf("id", "data", "tipo", "descrizione", "categoria", "importo", "pagato_da", "diviso_tra", "ricorrente", "note")

    fun exportCsv(members: List<Member>, categories: List<Category>, expenses: List<Expense>): String {
        val names = members.associate { it.id to it.name }
        val catNames = categories.associate { it.key to it.name }
        val sb = StringBuilder()
        sb.append('\uFEFF') // BOM: Excel riconosce l'UTF-8
        sb.append(CSV_HEADER.joinToString(";")).append("\r\n")
        expenses.sortedBy { it.date }.forEach { e ->
            val row = listOf(
                e.id,
                e.date.take(10),
                e.kindEnum.label,
                e.description,
                catNames[e.category] ?: Category.unknown(e.category).name,
                Money.plain(e.amountCents),
                names[e.paidBy] ?: e.paidBy,
                e.splitAmong.joinToString("|") { names[it] ?: it },
                if (e.recurring) "si" else "no",
                e.notes.orEmpty(),
            )
            sb.append(row.joinToString(";") { csvEscape(it) }).append("\r\n")
        }
        return sb.toString()
    }

    private fun csvEscape(s: String): String =
        if (s.any { it == ';' || it == '"' || it == '\n' || it == '\r' || it == ',' }) "\"" + s.replace("\"", "\"\"") + "\"" else s

    // ------------------------------------------------------------- import

    fun parse(
        text: String,
        household: Household,
        members: List<Member>,
        categories: List<Category>,
        existing: List<Expense>,
    ): ImportResult {
        val t = text.trimStart('\uFEFF', ' ', '\n', '\r', '\t')
        val cats = CategoryResolver(household.id, categories)
        return if (t.startsWith("{")) parseJson(t, household, members, cats, existing)
        else parseCsv(t, household, members, cats, existing)
    }

    /** Trova le categorie per chiave/nome e crea quelle che mancano (una sola volta). */
    private class CategoryResolver(val householdId: String, existing: List<Category>) {
        private val all = existing.toMutableList()
        val created = mutableListOf<Category>()

        fun keyFor(name: String, icon: String = "tag", color: String? = null, preferredKey: String? = null): String {
            if (name.isBlank()) return Category.OTHER
            preferredKey?.let { k -> all.firstOrNull { it.key == k && it.name.equals(name.trim(), true) }?.let { return it.key } }
            all.resolve(name)?.let { return it.key }
            val c = Category(
                householdId = householdId,
                key = Category.newKey(),
                name = name.trim().take(40),
                icon = icon,
                color = color ?: Category.palette[(all.size) % Category.palette.size],
                position = 100 + all.size,
            )
            all += c; created += c
            return c.key
        }
    }

    private fun memberResolver(members: List<Member>): (String) -> String? {
        val byId = members.associateBy { it.id }
        val byName = members.associateBy { it.name.trim().lowercase() }
        return { key -> byId[key.trim()]?.id ?: byName[key.trim().lowercase()]?.id }
    }

    private fun parseJson(text: String, h: Household, members: List<Member>, cats: CategoryResolver, existing: List<Expense>): ImportResult {
        val file = json.decodeFromString(ExportFile.serializer(), text)
        require(file.format == FORMAT) { "File non riconosciuto" }
        val resolve = memberResolver(members)
        // id esportato -> id attuale (per id uguale, oppure per nome)
        val exportedNames = file.members.associate { it.id to it.name }
        fun map(id: String): String? = resolve(id) ?: exportedNames[id]?.let(resolve)

        val sameHousehold = file.household.id == h.id
        val exportedCats = file.categories.associateBy { it.key }
        fun categoryKey(key: String): String {
            val ec = exportedCats[key] ?: return Category.defaults.firstOrNull { it.key == key }?.let { cats.keyFor(it.name, it.icon, it.color, it.key) }
                ?: Category.OTHER
            return cats.keyFor(ec.name, ec.icon, ec.color, ec.key)
        }
        val existingIds = existing.map { it.id }.toSet()
        val skipped = mutableListOf<String>()
        val out = mutableListOf<ExpenseWrite>()
        var updated = 0
        file.expenses.forEachIndexed { i, e ->
            val label = e.description.ifBlank { "movimento ${i + 1}" }
            val payer = map(e.paidBy)
            val split = e.splitAmong.map { map(it) }
            if (payer == null || split.any { it == null } || split.isEmpty()) {
                skipped += "$label: membro non trovato in questa casa"; return@forEachIndexed
            }
            if (e.amountCents <= 0) { skipped += "$label: importo non valido"; return@forEachIndexed }
            val id = if (sameHousehold) e.id else UUID.randomUUID().toString()
            if (id in existingIds) updated++
            out += ExpenseWrite(id, h.id, ExpenseKind.fromKey(e.kind).key, e.description.take(120),
                categoryKey(e.category), e.amountCents, LocalDate.parse(e.date.take(10)).toString(),
                payer, split.filterNotNull().distinct(), e.recurring, e.notes.take(500))
        }
        return ImportResult(cats.created, out, out.size - updated, updated, skipped)
    }

    private fun parseCsv(text: String, h: Household, members: List<Member>, cats: CategoryResolver, existing: List<Expense>): ImportResult {
        val rows = parseCsvRows(text)
        require(rows.isNotEmpty()) { "File vuoto" }
        val header = rows.first().map { it.trim().lowercase() }
        fun col(name: String) = header.indexOf(name)
        val iDate = col("data"); val iAmount = col("importo"); val iPaid = col("pagato_da")
        require(iDate >= 0 && iAmount >= 0 && iPaid >= 0) {
            "Intestazione CSV non valida: servono almeno le colonne data;importo;pagato_da"
        }
        val iId = col("id"); val iKind = col("tipo"); val iDesc = col("descrizione"); val iCat = col("categoria")
        val iSplit = col("diviso_tra"); val iRec = col("ricorrente"); val iNotes = col("note")
        val resolve = memberResolver(members)
        val byId = existing.associateBy { it.id }
        val skipped = mutableListOf<String>()
        val out = mutableListOf<ExpenseWrite>()
        var updated = 0

        rows.drop(1).forEachIndexed { idx, r ->
            if (r.all { it.isBlank() }) return@forEachIndexed
            val line = idx + 2
            fun get(i: Int) = if (i >= 0 && i < r.size) r[i].trim() else ""
            val date = parseDate(get(iDate)) ?: run { skipped += "riga $line: data non valida"; return@forEachIndexed }
            val amount = Money.parse(get(iAmount))?.takeIf { it != 0L }
                ?: run { skipped += "riga $line: importo non valido"; return@forEachIndexed }
            val payer = resolve(get(iPaid)) ?: run { skipped += "riga $line: '${get(iPaid)}' non è un membro"; return@forEachIndexed }
            val splitRaw = get(iSplit)
            val split = if (splitRaw.isBlank()) members.map { it.id }
            else splitRaw.split('|', ',').filter { it.isNotBlank() }.map { resolve(it) ?: run {
                skipped += "riga $line: '${it.trim()}' non è un membro"; return@forEachIndexed } }
            val kindText = get(iKind).lowercase()
            var kind = ExpenseKind.entries.firstOrNull { it.key == kindText || it.label.lowercase() == kindText }
                ?: if (kindText.startsWith("rimbors")) ExpenseKind.REFUND
                else if (kindText.startsWith("saldo")) ExpenseKind.SETTLEMENT else ExpenseKind.EXPENSE
            // importo negativo = rimborso
            var cents = amount
            if (cents < 0) { cents = -cents; if (kind == ExpenseKind.EXPENSE) kind = ExpenseKind.REFUND }
            if (kind == ExpenseKind.SETTLEMENT && split.distinct().size != 1) {
                skipped += "riga $line: un saldo deve avere un solo destinatario"; return@forEachIndexed
            }
            val existingId = get(iId).takeIf { it.isNotBlank() && byId[it]?.householdId == h.id }
            if (existingId != null) updated++
            out += ExpenseWrite(
                id = existingId ?: UUID.randomUUID().toString(),
                householdId = h.id,
                kind = kind.key,
                description = get(iDesc).take(120),
                category = cats.keyFor(get(iCat)),
                amountCents = cents,
                date = date.toString(),
                paidBy = payer,
                splitAmong = split.distinct(),
                recurring = get(iRec).lowercase() in setOf("si", "sì", "yes", "true", "1", "x"),
                notes = get(iNotes).take(500),
            )
        }
        return ImportResult(cats.created, out, out.size - updated, updated, skipped)
    }

    private val dateFormats = listOf(
        DateTimeFormatter.ISO_LOCAL_DATE,
        DateTimeFormatter.ofPattern("d/M/yyyy"),
        DateTimeFormatter.ofPattern("d-M-yyyy"),
        DateTimeFormatter.ofPattern("d.M.yyyy"),
    )

    private fun parseDate(s: String): LocalDate? =
        dateFormats.firstNotNullOfOrNull { f -> runCatching { LocalDate.parse(s.take(10).trim(), f) }.getOrNull() }

    /** Parser CSV minimale con supporto a virgolette; separatore `;` o `,` (autodetect). */
    fun parseCsvRows(text: String): List<List<String>> {
        val firstLine = text.lineSequence().firstOrNull().orEmpty()
        val sep = if (firstLine.count { it == ';' } >= firstLine.count { it == ',' }) ';' else ','
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val cell = StringBuilder()
        var quoted = false
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (quoted) {
                if (c == '"') {
                    if (i + 1 < text.length && text[i + 1] == '"') { cell.append('"'); i++ } else quoted = false
                } else cell.append(c)
            } else when (c) {
                '"' -> quoted = true
                sep -> { row += cell.toString(); cell.clear() }
                '\r' -> {}
                '\n' -> { row += cell.toString(); cell.clear(); rows += row; row = mutableListOf() }
                else -> cell.append(c)
            }
            i++
        }
        if (cell.isNotEmpty() || row.isNotEmpty()) { row += cell.toString(); rows += row }
        return rows
    }

}
