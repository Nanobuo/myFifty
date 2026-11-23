package com.nanobuo.myfifty.domain

enum class Category(val key: String, val label: String, val color: Long) {
    GROCERIES("groceries", "Alimentari", 0xFF4E9E6E),
    ELECTRICITY("electricity", "Luce", 0xFFE6B23C),
    GAS("gas", "Gas", 0xFFE07B39),
    WATER("water", "Acqua", 0xFF3D8FD1),
    INTERNET("internet", "Internet e telefono", 0xFF7A6FD6),
    SUBSCRIPTIONS("subscriptions", "Abbonamenti", 0xFFC75B9B),
    RENT("rent", "Affitto", 0xFF8C6B4F),
    CONDO("condo", "Condominio", 0xFF5E8C8A),
    HOUSEHOLD("household", "Prodotti per la casa", 0xFF9BB84A),
    MAINTENANCE("maintenance", "Manutenzione", 0xFFB5523B),
    OTHER("other", "Altro", 0xFF8A8F98);

    companion object {
        fun fromKey(key: String): Category =
            entries.firstOrNull { it.key == key } ?: OTHER

        /** Accetta sia la chiave sia l'etichetta italiana (utile per l'import CSV). */
        fun parse(text: String): Category {
            val t = text.trim()
            return entries.firstOrNull { it.key.equals(t, true) || it.label.equals(t, true) } ?: OTHER
        }
    }
}
