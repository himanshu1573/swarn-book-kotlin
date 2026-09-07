package com.swarnabook.billing.ui.common

/** "Anita Sharma" -> "AS", "Anukul" -> "A", "" -> "#": text for the round avatars. */
object Initials {
    fun of(name: String): String {
        val parts = name.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        if (parts.isEmpty()) return "#"
        val first = parts.first().first()
        val second = parts.getOrNull(1)?.first()
        return listOfNotNull(first, second).joinToString("").uppercase()
    }
}
