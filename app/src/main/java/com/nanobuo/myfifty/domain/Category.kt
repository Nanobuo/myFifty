package com.nanobuo.myfifty.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.UUID

/** Categoria di spesa di una casa (tabella `categories`). */
@Serializable
data class Category(
    @SerialName("household_id") val householdId: String = "",
    val key: String,
    val name: String,
    val icon: String = "tag",
    val color: String = "#8A8F98",
    val position: Int = 100,
) {
    /** Colore ARGB per Compose. */
    val argb: Long
        get() = runCatching { 0xFF000000 or color.removePrefix("#").toLong(16) }.getOrDefault(0xFF8A8F98)

    val isOther: Boolean get() = key == OTHER

    companion object {
        const val OTHER = "other"

        /** Le stesse categorie create da `seed_categories` su Supabase. */
        val defaults: List<Category> = listOf(
            Category(key = "groceries", name = "Alimentari", icon = "cart", color = "#4E9E6E", position = 1),
            Category(key = "electricity", name = "Luce", icon = "bolt", color = "#E6B23C", position = 2),
            Category(key = "gas", name = "Gas", icon = "fire", color = "#E07B39", position = 3),
            Category(key = "water", name = "Acqua", icon = "water", color = "#3D8FD1", position = 4),
            Category(key = "internet", name = "Internet e telefono", icon = "wifi", color = "#7A6FD6", position = 5),
            Category(key = "subscriptions", name = "Abbonamenti", icon = "tv", color = "#C75B9B", position = 6),
            Category(key = "rent", name = "Affitto", icon = "house", color = "#8C6B4F", position = 7),
            Category(key = "condo", name = "Condominio", icon = "building", color = "#5E8C8A", position = 8),
            Category(key = "household", name = "Prodotti per la casa", icon = "cleaning", color = "#9BB84A", position = 9),
            Category(key = "maintenance", name = "Manutenzione", icon = "build", color = "#B5523B", position = 10),
            Category(key = OTHER, name = "Altro", icon = "more", color = "#8A8F98", position = 999),
        )

        /** Colori proposti per le nuove categorie. */
        val palette: List<String> = listOf(
            "#4E9E6E", "#2F7A50", "#9BB84A", "#E6B23C", "#E07B39", "#B5523B",
            "#C75B9B", "#7A6FD6", "#3D8FD1", "#5E8C8A", "#8C6B4F", "#8A8F98",
        )

        fun newKey(): String = "c_" + UUID.randomUUID().toString().replace("-", "").take(12)

        /** Usata quando un movimento punta a una categoria che non esiste più. */
        fun unknown(key: String) = defaults.firstOrNull { it.key == key } ?: defaults.last()
    }
}

/** Cerca una categoria per chiave o per nome (senza distinzione di maiuscole). */
fun List<Category>.resolve(text: String): Category? {
    val t = text.trim()
    if (t.isEmpty()) return null
    return firstOrNull { it.key.equals(t, true) } ?: firstOrNull { it.name.equals(t, true) }
}
