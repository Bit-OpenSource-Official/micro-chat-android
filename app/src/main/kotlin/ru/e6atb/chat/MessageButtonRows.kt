@file:Suppress("EXPOSED_FUNCTION_RETURN_TYPE", "EXPOSED_PARAMETER_TYPE")

package ru.e6atb.chat

object MessageButtonRows {
    @JvmStatic fun group(buttons: List<MST5.Button>?): List<List<MST5.Button>?> {
        val rows = ArrayList<MutableList<MST5.Button>?>()
        buttons.orEmpty().forEach { button ->
            if (button.text.isNullOrEmpty()) return@forEach
            val index = button.row.coerceIn(0, 11)
            while (rows.size <= index) rows.add(null)
            val row = rows[index] ?: ArrayList<MST5.Button>().also { rows[index] = it }
            row.add(button)
        }
        return rows
    }
}
