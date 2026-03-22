package com.example.gamechat.ui.solver

data class SolverMode(
    val id: Int,
    val alias: String,
    val title: String
)

object SolverModes {
    private val modes = listOf(
        SolverMode(id = 1, alias = "anagramma", title = "Анаграмма"),
        SolverMode(id = 8, alias = "anagramma2", title = "2Анаграмма"),
        SolverMode(id = 2, alias = "association", title = "Ассоциации"),
        SolverMode(id = 36, alias = "dick", title = "Расчлененка"),
        SolverMode(id = 3, alias = "mask", title = "Маска"),
        SolverMode(id = 9, alias = "maskword", title = "Маска + слово"),
        SolverMode(id = 7, alias = "phrase", title = "Фразеологизмы"),
        SolverMode(id = 12, alias = "meta", title = "Метаграммы"),
        SolverMode(id = 28, alias = "morze", title = "Азбука Морзе"),
        SolverMode(id = 14, alias = "logo", title = "Логогрифы"),
        SolverMode(id = 15, alias = "cross", title = "Общая часть"),
        SolverMode(id = 16, alias = "any", title = "Все форматы"),
        SolverMode(id = 13, alias = "brukva", title = "Брюква"),
        SolverMode(id = 22, alias = "subword", title = "Подслова"),
        SolverMode(id = 23, alias = "longword", title = "Надслова"),
        SolverMode(id = 33, alias = "alphabet", title = "Цифры по алфавиту"),
        SolverMode(id = 29, alias = "bacon", title = "Бэкон"),
        SolverMode(id = 30, alias = "bodo", title = "Бодо"),
        SolverMode(id = 31, alias = "binary", title = "Двоичка"),
        SolverMode(id = 34, alias = "tm", title = "Таблица Менделеева"),
        SolverMode(id = 20, alias = "gapoifika", title = "ГаПоИФиКа"),
        SolverMode(id = 11, alias = "plus", title = "Плюсограмма"),
        SolverMode(id = 4, alias = "books", title = "Книги"),
        SolverMode(id = 5, alias = "film", title = "Фильмы")
    )

    fun all(): List<SolverMode> = modes

    fun byId(id: Int): SolverMode? = modes.firstOrNull { it.id == id }

    fun byAlias(alias: String): SolverMode? {
        val normalized = normalizeAlias(alias)
        return modes.firstOrNull {
            it.alias.equals(normalized, ignoreCase = true)
        }
    }

    fun default(): SolverMode = modes.first()

    private fun normalizeAlias(alias: String): String {
        return when (alias.trim().lowercase()) {
            "book" -> "books"
            "books" -> "books"
            "films" -> "film"
            "film" -> "film"
            "plus" -> "plus"
            "anagramma2" -> "anagramma2"
            "maskword" -> "maskword"
            "cross" -> "cross"
            "meta" -> "meta"
            "brukva" -> "brukva"
            "logo" -> "logo"
            "any" -> "any"
            "subword" -> "subword"
            "longword" -> "longword"
            "morze" -> "morze"
            "bacon" -> "bacon"
            "bodo" -> "bodo"
            "binary" -> "binary"
            "alphabet" -> "alphabet"
            "tm" -> "tm"
            "gapoifika" -> "gapoifika"
            "dick" -> "dick"
            "frazeologism" -> "phrase"
            "phrase" -> "phrase"
            else -> alias.trim().lowercase()
        }
    }
}
