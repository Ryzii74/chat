package com.example.gamechat.ui.solver

import java.util.Locale

class SolverEngine(
    private val repository: SolverDataRepository
) {
    data class AnyFormatResult(
        val title: String,
        val answers: List<String>
    )

    data class BrukvaResult(
        val title: String,
        val answers: List<String>
    )

    data class PlusResult(
        val title: String,
        val answers: List<String>
    )

    data class GapoifikaResult(
        val title: String,
        val answers: List<String>
    )

    data class CaesarResult(
        val shift: Int,
        val decoded: String,
        val isRealWord: Boolean
    )

    fun resolve(mode: SolverMode, rawInput: String): String {
        val input = normalize(rawInput)
        if (input.isBlank()) return "Введите слово или шаблон."
        return when (mode.alias) {
            "anagramma" -> resolveAnagramma(input)
            "anagramma2" -> resolveAnagramma2(input)
            "association" -> resolveAssociation(input)
            "mask" -> resolveMask(input)
            "maskword" -> resolveMaskWord(input)
            "slovogen" -> resolveSlovogen(input)
            "plus" -> resolvePlus(rawInput)
            "meta" -> resolveMeta(rawInput)
            "brukva" -> resolveBrukva(rawInput)
            "logo" -> resolveLogo(rawInput)
            "cross" -> resolveCross(rawInput)
            "any" -> resolveAny(rawInput)
            "roman" -> resolveRoman(input)
            "adj" -> resolveAdj(rawInput)
            "noun" -> resolveNoun(rawInput)
            "gapoifika" -> resolveGapoifika(input)
            "ss" -> resolveSs(input)
            "subword" -> resolveSubword(input)
            "longword" -> resolveLongword(rawInput)
            "sborka" -> resolveSborka(rawInput)
            "sborkaline" -> resolveSborkaLine(rawInput)
            "vigenere" -> resolveVigenere(rawInput)
            "caesar" -> resolveCaesar(rawInput)
            "morze" -> resolveMorze(rawInput)
            "bacon" -> resolveBacon(rawInput)
            "bodo" -> resolveBodo(rawInput)
            "binary" -> resolveBinary(rawInput)
            "brail" -> resolveBrail(rawInput)
            "alphabet" -> resolveAlphabet(rawInput)
            "tm" -> resolveTM(rawInput)
            "regions" -> resolveRegions(rawInput)
            "dick" -> resolveDick(rawInput)
            "notes" -> resolveNotes(rawInput)
            "books" -> resolveBooks(input)
            "film" -> resolveFilms(input)
            "painting" -> resolvePaintings(input)
            "phrase" -> resolvePhrase(rawInput)
            else -> "Режим пока не поддерживается."
        }
    }

    private fun resolveAnagramma(input: String): String {
        val source = repository.wordsForText(input)
        val candidates = findAnagramCandidates(input, source)
            .distinct()
            .take(PAGINATION_RESULTS_LIMIT)
        return if (candidates.isEmpty()) {
            "[Анаграмма]\nНичего не найдено."
        } else {
            "[Анаграмма]\nНайдено:\n${candidates.joinToString("\n")}"
        }
    }

    private fun findAnagramCandidates(pattern: String, words: List<String>): List<String> {
        val isAnyLettersCount = pattern.contains('*')
        val minusCount = pattern.count { it == '-' }
        val minLength = pattern.length - minusCount * 2 - if (isAnyLettersCount) 1 else 0
        val cleanPattern = pattern.replace(Regex("[*?-]"), "")
        val letterCounts = cleanPattern.groupingBy { it }.eachCount()
        val letters = letterCounts.keys.toList()
        val letterToIndex = mutableMapOf<Char, Int>()
        letters.forEachIndexed { index, char ->
            letterToIndex[char] = index
        }
        val requiredCounts = IntArray(letters.size) { index ->
            letterCounts[letters[index]] ?: 0
        }

        return words.filter { word ->
            val normalizedWord = word
            if (isAnyLettersCount) {
                if (normalizedWord.length < minLength) return@filter false
            } else if (normalizedWord.length != minLength) {
                return@filter false
            }

            if (requiredCounts.isEmpty()) return@filter true

            val matchedCounts = IntArray(requiredCounts.size)
            normalizedWord.forEach { char ->
                val index = letterToIndex[char] ?: return@forEach
                if (matchedCounts[index] < requiredCounts[index]) {
                    matchedCounts[index]++
                }
            }

            var lettersOmit = 0
            for (index in requiredCounts.indices) {
                lettersOmit += requiredCounts[index] - matchedCounts[index]
                if (lettersOmit > minusCount) {
                    return@filter false
                }
            }
            true
        }.sortedBy { it.length }
    }

    private fun resolveMask(input: String): String {
        val source = repository.wordsForText(input)
        val reg = buildMaskRegex(input)
        val repeatedDigits = prepareRepeatedDigitPositions(input)

        val answers = source.filter { word ->
            reg.matches(word) && repeatedDigits.all { indexes ->
                val first = word.getOrNull(indexes[0]) ?: return@all false
                indexes.all { word.getOrNull(it) == first }
            }
        }.distinct().take(PAGINATION_RESULTS_LIMIT)

        return if (answers.isEmpty()) {
            "По маске ничего не найдено."
        } else {
            "По маске найдено (${answers.size}): ${answers.joinToString(", ")}"
        }
    }

    private fun resolveMaskWord(input: String): String {
        val source = repository.wordsForText(input)
        val regex = runCatching {
            Regex(
                "^" + input.replace("?", "(\\\\S*)") + "$",
                setOf(RegexOption.IGNORE_CASE)
            )
        }.getOrNull() ?: return "По маске ничего не найдено."

        val answers = mutableListOf<String>()
        source.forEach { word ->
            val match = regex.matchEntire(word) ?: return@forEach
            if (match.groupValues.size < 2) return@forEach
            val insertedWord = match.groupValues[1]
            if (!repository.wordExistsForText(input, insertedWord)) return@forEach
            answers.add(
                word.replaceFirst(
                    insertedWord,
                    insertedWord.uppercase(Locale.ROOT)
                )
            )
        }

        val limited = answers.distinct().take(PAGINATION_RESULTS_LIMIT)
        return if (limited.isEmpty()) {
            "По маске ничего не найдено."
        } else {
            "По маске найдено (${limited.size}): ${limited.joinToString(", ")}"
        }
    }

    private fun resolveAnagramma2(input: String): String {
        val target = input.replace(" ", "")
        if (target.isBlank()) return "Введите буквы для поиска пары слов."
        val lettersCount = countLetters(target)
        val wordsArray = repository.wordsForText(target)

        val validWords = wordsArray.asSequence()
            .filter { canForm(countLetters(it), lettersCount) }
            .distinct()
            .toList()

        if (validWords.isEmpty()) {
            return "Ничего не найдено."
        }

        val pairs = mutableListOf<String>()
        for (i in validWords.indices) {
            val word1 = validWords[i]
            val count1 = countLetters(word1)
            val remaining = subtractCounts(lettersCount, count1) ?: continue
            for (j in i + 1 until validWords.size) {
                val word2 = validWords[j]
                val count2 = countLetters(word2)
                if (isEqualCount(remaining, count2)) {
                    pairs.add("$word1 $word2")
                }
            }
        }

        val limited = pairs.take(PAGINATION_RESULTS_LIMIT)
        return if (limited.isEmpty()) {
            "Ничего не найдено."
        } else {
            "Найдено (${limited.size}): ${limited.joinToString(", ")}"
        }
    }

    private fun buildMaskRegex(input: String): Regex {
        val result = StringBuilder("^")
        input.forEach { ch ->
            when {
                ch == '*' -> result.append("\\S*")
                ch == '?' || ch in '1'..'9' -> result.append("\\S")
                ch in RegexSpecial -> {
                    result.append("\\").append(ch)
                }

                else -> result.append(ch)
            }
        }
        result.append("$")
        return Regex(result.toString(), setOf(RegexOption.IGNORE_CASE))
    }

    private fun prepareRepeatedDigitPositions(input: String): List<List<Int>> {
        val byDigit = mutableMapOf<Char, MutableList<Int>>()
        input.forEachIndexed { index, ch ->
            if (ch in '1'..'9') {
                byDigit.getOrPut(ch) { mutableListOf() }.add(index)
            }
        }
        return byDigit.values.filter { it.size > 1 }.map { it.toList() }
    }

    private fun resolveAssociation(input: String): String {
        val words = input.split("\\s+".toRegex()).filter { it.isNotBlank() }
        if (words.isEmpty()) return "Введите слово."
        if (words.size == 1) {
            val associations = repository.associationsForWord(words[0]).take(PAGINATION_RESULTS_LIMIT)
            return if (associations.isEmpty()) {
                "Ассоциации не найдены."
            } else {
                "Ассоциации: ${associations.joinToString(", ")}"
            }
        }

        val first = repository.associationsForWord(words[0]).toSet()
        val second = repository.associationsForWord(words[1]).toSet()
        val common = first.intersect(second).sorted().take(PAGINATION_RESULTS_LIMIT)
        return if (common.isEmpty()) {
            "Общих ассоциаций не найдено."
        } else {
            "Общие ассоциации: ${common.joinToString(", ")}"
        }
    }

    private fun resolveMeta(rawInput: String): String {
        val words = rawInput.trim().split("\\s+".toRegex()).filter { it.isNotBlank() }
        if (words.isEmpty()) return "Введите слово."
        if (words.size == 1) {
            val target = normalize(words.first())
            val answers = repository.wordsForText(target)
                .asSequence()
                .map(::normalize)
                .filter { isMetagramma(it, target) }
                .distinct()
                .take(PAGINATION_RESULTS_LIMIT)
                .toList()
            return if (answers.isEmpty()) "Нет результатов" else "Найдено (${answers.size}): ${answers.joinToString(", ")}"
        }

        val word1Raw = words[0]
        val word2Raw = words[1]
        val source1: List<String>
        val source2: List<String>
        val formatter: (String, String) -> String
        if (word1Raw.endsWith("!")) {
            source1 = listOf(normalize(word1Raw.removeSuffix("!")))
            source2 = repository.associationsForWord(word2Raw)
            formatter = { _, b -> b }
        } else {
            source1 = repository.associationsForWord(word1Raw)
            source2 = repository.associationsForWord(word2Raw)
            formatter = { a, b -> "$a $b" }
        }
        val answers = mutableListOf<String>()
        source1.forEach { a ->
            source2.forEach { b ->
                if (isMetagramma(a, b)) answers.add(formatter(a, b))
            }
        }
        val unique = answers.distinct().take(PAGINATION_RESULTS_LIMIT)
        return if (unique.isEmpty()) "Нет результатов" else "Найдено (${unique.size}): ${unique.joinToString(", ")}"
    }

    private fun resolvePlus(rawInput: String): String {
        val results = resolvePlusBreakdown(rawInput)
        return results.joinToString("\n\n") { result ->
            if (result.answers.isEmpty()) {
                "${result.title}\nНет результатов"
            } else {
                "${result.title}\n${result.answers.take(PAGINATION_RESULTS_LIMIT).joinToString(" ")}"
            }
        }
    }

    fun resolvePlusBreakdown(rawInput: String): List<PlusResult> {
        val inputWords = rawInput.trim().split("\\s+".toRegex()).filter { it.isNotBlank() }
        val baseLength = normalize(inputWords.firstOrNull().orEmpty()).length
        val answers = resolveSeveralWordsList(rawInput, ::isPlusogramma).distinct()

        val plusOne = answers.filter { normalize(it).length == baseLength + 1 }
        val minusOne = answers.filter { normalize(it).length == baseLength - 1 }

        return listOf(
            PlusResult(title = "+1 буква", answers = plusOne),
            PlusResult(title = "-1 буква", answers = minusOne)
        )
    }

    private fun resolveBrukva(rawInput: String): String {
        val results = resolveBrukvaBreakdown(rawInput)
        return results.joinToString("\n\n") { result ->
            if (result.answers.isEmpty()) {
                "${result.title}\nНет результатов"
            } else {
                "${result.title}\n${result.answers.take(PAGINATION_RESULTS_LIMIT).joinToString(" ")}"
            }
        }
    }

    fun resolveBrukvaBreakdown(rawInput: String): List<BrukvaResult> {
        val inputWords = rawInput.trim().split("\\s+".toRegex()).filter { it.isNotBlank() }
        val baseLength = normalize(inputWords.firstOrNull().orEmpty()).length
        val answers = resolveSeveralWordsList(rawInput, ::isBrukva).distinct()

        val plusOne = answers.filter { normalize(it).length == baseLength + 1 }
        val minusOne = answers.filter { normalize(it).length == baseLength - 1 }

        return listOf(
            BrukvaResult(title = "+1 буква", answers = plusOne),
            BrukvaResult(title = "-1 буква", answers = minusOne)
        )
    }

    private fun resolveLogo(rawInput: String): String {
        return resolveSeveralWordsSimple(rawInput, ::isLogogrif)
    }

    private fun resolveAny(rawInput: String): String {
        val nonEmpty = resolveAnyBreakdown(rawInput).filter { it.answers.isNotEmpty() }
        if (nonEmpty.isEmpty()) {
            return "Нет результатов"
        }
        return nonEmpty.joinToString("\n\n") { result ->
            "${result.title}\n${result.answers.take(PAGINATION_RESULTS_LIMIT).joinToString(" ")}"
        }
    }

    fun resolveAnyBreakdown(rawInput: String): List<AnyFormatResult> {
        return listOf(
            AnyFormatResult(
                title = "Метаграмма",
                answers = resolveSeveralWordsList(rawInput, ::isMetagramma)
            ),
            AnyFormatResult(
                title = "Логогриф",
                answers = resolveSeveralWordsList(rawInput, ::isLogogrif)
            ),
            AnyFormatResult(
                title = "Плюсограмма",
                answers = resolveSeveralWordsList(rawInput, ::isPlusogramma)
            ),
            AnyFormatResult(
                title = "Анаграмма",
                answers = resolveSeveralWordsList(rawInput, ::isAnagrammaSimple)
            ),
            AnyFormatResult(
                title = "Брюква",
                answers = resolveSeveralWordsList(rawInput, ::isBrukva)
            )
        )
    }

    private fun resolveCross(rawInput: String): String {
        val words = rawInput.trim().split("\\s+".toRegex()).filter { it.isNotBlank() }.map(::normalize)
        if (words.size < 2) {
            return "Нужно указать хотя бы 2 слова через пробел"
        }
        val firstWord = words.first()
        val otherWords = words.drop(1)
        val dictWords = repository.wordsForText(rawInput).map(::normalize)
        val dictSet = dictWords.toHashSet()

        val result = mutableListOf<String>()
        val resultAll = mutableListOf<String>()

        dictWords.forEach { word ->
            val index = word.indexOf(firstWord)
            val diff = word.length - firstWord.length
            if (index == 0 || (index > 0 && index == word.length - firstWord.length)) {
                val subword = if (index == 0) word.takeLast(diff) else word.take(index)
                if (result.any { it.startsWith(subword) }) return@forEach

                val wordsFound = mutableListOf<String>()
                otherWords.forEach { otherWord ->
                    val wordsToAdd = mutableListOf<String>()
                    if (dictSet.contains(subword + otherWord)) {
                        wordsToAdd.add(subword.uppercase(Locale.ROOT) + otherWord)
                    }
                    if (dictSet.contains(otherWord + subword)) {
                        wordsToAdd.add(otherWord + subword.uppercase(Locale.ROOT))
                    }
                    if (wordsToAdd.isNotEmpty()) {
                        wordsFound.add(wordsToAdd.joinToString("/"))
                    }
                }
                if (wordsFound.isNotEmpty()) {
                    val updatedWord = word.replaceFirst(subword, subword.uppercase(Locale.ROOT))
                    val row = "$subword - $updatedWord ${wordsFound.joinToString(" ")}"
                    if (wordsFound.size + 1 == words.size && words.size > 2) {
                        resultAll.add(row)
                    } else {
                        result.add(row)
                    }
                }
            }
        }

        val blocks = mutableListOf<String>()
        if (result.isNotEmpty()) {
            blocks.add(result.sortedByDescending { it.length }.joinToString("\n"))
        }
        if (words.size > 2 && resultAll.isNotEmpty()) {
            blocks.add(
                "СОВПАДЕНИЯ ВСЕХ СЛОВ\n" +
                    resultAll.sortedByDescending { it.length }.joinToString("\n")
            )
        }
        return if (blocks.isEmpty()) "Нет результатов" else blocks.joinToString("\n\n")
    }

    private fun resolveRoman(input: String): String {
        val romanNumerals = setOf('i', 'v', 'x', 'l', 'c', 'd', 'm')
        val isRoman = input.all { romanNumerals.contains(it.lowercaseChar()) }
        val response = if (isRoman) {
            romanToArabic(input.lowercase(Locale.ROOT))?.toString()
        } else {
            input.toIntOrNull()?.let { arabicToRoman(it) }
        }
        return response ?: "Нет результатов"
    }

    private fun resolveAdj(rawInput: String): String {
        return resolveKartaslovPairs(rawInput, repository::loadAdjectives)
    }

    private fun resolveNoun(rawInput: String): String {
        return resolveKartaslovPairs(rawInput, repository::loadNouns)
    }

    private fun resolveKartaslovPairs(
        rawInput: String,
        source: (String) -> List<String>
    ): String {
        val words = rawInput.trim().split("\\s+".toRegex()).filter { it.isNotBlank() }
        if (words.isEmpty()) return "Введите слово."
        return try {
            if (words.size == 1) {
                val values = source(words[0]).distinct().take(PAGINATION_RESULTS_LIMIT)
                if (values.isEmpty()) "Нет результатов" else "Найдено (${values.size}): ${values.joinToString(", ")}"
            } else {
                val first = source(words[0]).map(::normalize).toSet()
                val second = source(words[1]).map(::normalize).toSet()
                val equal = first.intersect(second).sorted().take(PAGINATION_RESULTS_LIMIT)
                if (equal.isEmpty()) "Нет результатов" else "Найдено (${equal.size}): ${equal.joinToString(", ")}"
            }
        } catch (_: Exception) {
            "Ошибка получения данных от сервера!"
        }
    }

    private fun resolveGapoifika(input: String): String {
        val parts = resolveGapoifikaBreakdown(input).filter { it.answers.isNotEmpty() }
        if (parts.isEmpty()) {
            return "Ничего не найдено!"
        }
        return parts.joinToString("\n\n") { "${it.title}\n${it.answers.joinToString("\n")}" }
    }

    fun resolveGapoifikaBreakdown(input: String): List<GapoifikaResult> {
        val text = normalize(input)
        val foundBooks = repository.getBookTitles().filter { isGapoifika(text, it) }
        val foundFilms = repository.getFilmTitles().filter { isGapoifika(text, it) }
        val foundPaintings = repository.getPaintingTitles().filter { isGapoifika(text, it) }

        return listOf(
            GapoifikaResult(title = "Картины", answers = foundPaintings),
            GapoifikaResult(title = "Книги", answers = foundBooks),
            GapoifikaResult(title = "Фильмы", answers = foundFilms)
        )
    }

    private fun resolveSs(input: String): String {
        val text = input.lowercase(Locale.ROOT)
        val answers = mutableListOf<String>()
        if (isBinary(text)) {
            answers.add("2 -> 10: ${text.toLong(2)}")
            answers.add("2 -> 16: ${text.toLong(2).toString(16)}")
        }
        if (isDecimal(text)) {
            val number = text.toLongOrNull()
            if (number != null) {
                answers.add("10 -> 2: ${number.toString(2)}")
                answers.add("10 -> 16: ${number.toString(16)}")
            }
        }
        if (isHex(text)) {
            val number = text.toLong(16)
            answers.add("16 -> 10: $number")
            answers.add("16 -> 2: ${number.toString(2)}")
        }
        return if (answers.isEmpty()) "Нет результатов" else answers.joinToString("\n")
    }

    private fun resolveSubword(input: String): String {
        val text = normalize(input)
        val answers = repository.wordsForText(text).asSequence()
            .map(::normalize)
            .filter { isSubword(text, it) }
            .distinct()
            .take(PAGINATION_RESULTS_LIMIT)
            .toList()
        return if (answers.isEmpty()) "Нет результатов" else answers.joinToString("\n")
    }

    private fun resolveLongword(rawInput: String): String {
        return resolveSeveralWordsSimple(rawInput, ::isSubword)
    }

    private fun resolveSborka(rawInput: String): String {
        val wordsSet = repository.wordsForText(rawInput).map(::normalize).toHashSet()
        var linesAndNumbers = getSborkaLines(rawInput)
        var lines = linesAndNumbers.first
        val numbers = linesAndNumbers.second

        if (lines.size == 1 && numbers.size > 1) {
            lines = numbers.map { lines[0] }
            return getSborkaByNumbers(lines, numbers).let(::getWordsFromLine)
        }

        val base = listOf(
            "Лесенка: ${getWordsFromLine(getSborkaLadder(lines), wordsSet)}",
            "Арбуз: ${getWordsFromLine(getSborkaWatermelon(lines), wordsSet)}",
            "Арбуз с конца: ${getWordsFromLine(getSborkaWatermelonBack(lines), wordsSet)}",
            "Лесенка с конца: ${getWordsFromLine(getSborkaLadderBack(lines), wordsSet)}"
        )
        if (numbers.isEmpty()) return base.joinToString("\n")

        val withNumbers = listOf(
            "*С ЧИСЛАМИ*",
            "Номера построчно: ${getWordsFromLine(getSborkaByNumbers(lines, numbers), wordsSet)}",
            "По номеру строки арбуз: ${getWordsFromLine(getSborkaByLineNumbersWatermelon(lines, numbers), wordsSet)}",
            "По номеру строки арбуз с конца: ${getWordsFromLine(getSborkaByLineNumbersWatermelonBack(lines, numbers), wordsSet)}",
            "По номеру строки лесенкой: ${getWordsFromLine(getSborkaByLineNumbersLadder(lines, numbers), wordsSet)}",
            "По номеру строки лесенкой наоборот: ${getWordsFromLine(getSborkaByLineNumbersLadderBack(lines, numbers), wordsSet)}"
        )
        return (base + listOf("") + withNumbers).joinToString("\n")
    }

    private fun resolveSborkaLine(rawInput: String): String {
        val rawLines = rawInput.split('\n')
        if (rawLines.size < 2) return "Нужно передать строку и числа"
        val numbers = rawLines.last().trim().split(" ").mapNotNull { it.toIntOrNull() }
        if (numbers.isEmpty()) return "Нужно передать числа"

        val letters = rawLines.dropLast(1).joinToString("").replace(" ", "")
        val lines = numbers.map { letters }
        val answers = mutableListOf<String>()
        answers.add("По номеру буквы: ${getWordsFromLine(getSborkaByNumbers(lines, numbers))}")

        val numbersWithoutZero = numbers.filter { it != 0 }
        if (numbersWithoutZero.isNotEmpty() &&
            numbersWithoutZero.min() == 1 &&
            numbersWithoutZero.max() == numbersWithoutZero.size
        ) {
            val wordLetters = MutableList<Char?>(numbers.size) { null }
            numbers.forEachIndexed { index, number ->
                if (number == 0) return@forEachIndexed
                wordLetters[number - 1] = letters.getOrNull(index)
            }
            val word = wordLetters.joinToString("") { it?.toString() ?: "?" }
            answers.add("По позиции цифры: ${getWordsFromLine(word)}")
        }
        return answers.joinToString("\n")
    }

    private fun resolveVigenere(rawInput: String): String {
        val words = rawInput.trim().split("\\s+".toRegex()).filter { it.isNotBlank() }
        if (words.size < 2) return "Нужно указать хотя бы 2 слова через пробел"
        val key = words.last()
        val input = words.dropLast(1).joinToString(" ")
        val encrypted = vigenereTransform(input, key, encrypt = true)
        val decrypted = vigenereTransform(input, key, encrypt = false)
        return "Encode: ${getWordsFromLine(encrypted)}\nDecode: ${getWordsFromLine(decrypted)}"
    }

    private fun resolveCaesar(input: String): String {
        return resolveCaesarBreakdown(input)
            .joinToString("\n") { result ->
                "${result.shift}: ${getWordsFromLine(result.decoded)}"
            }
    }

    fun resolveCaesarBreakdown(input: String): List<CaesarResult> {
        val dictSet = repository.wordsForText(input).asSequence()
            .map(::normalize)
            .toSet()
        val responses = mutableListOf<CaesarResult>()
        for (i in 1..32) {
            val key = lettersRu.getOrNull(i)?.toString().orEmpty()
            if (key.isBlank()) continue
            val decoded = vigenereTransform(input, key, encrypt = false)
            val normalizedDecoded = normalize(decoded)
            val isRealWord = decoded.isNotBlank() &&
                !decoded.contains(' ') &&
                dictSet.contains(normalizedDecoded)
            responses.add(
                CaesarResult(
                    shift = i,
                    decoded = decoded,
                    isRealWord = isRealWord
                )
            )
        }
        return responses
    }

    private fun resolveMorze(rawInput: String): String {
        val text = rawInput.trim().lowercase(Locale.ROOT)
        val ru = morzeTranslate(text, morzeRuMap, repository.wordsForText("привет"), true)
        val en = morzeTranslate(text, morzeEnMap, repository.wordsForText("hello"), false)
        val blocks = mutableListOf<String>()
        blocks.add("АНГЛИЙСКИЙ\n${en.joinToString("\n").ifBlank { "Нет результатов" }}")
        blocks.add("РУССКИЙ\n${ru.joinToString("\n").ifBlank { "Нет результатов" }}")
        val digitsBlock = morzeDigitsBlock(text)
        if (digitsBlock != null) blocks.add("ЦИФРЫ\n$digitsBlock")
        return blocks.joinToString("\n\n")
    }

    private fun morzeTranslate(
        text: String,
        symbols: Map<String, String>,
        dictionaryArray: List<String>,
        isRu: Boolean
    ): List<String> {
        if (text.contains(' ')) {
            val translation = text.split(' ')
                .map { group -> translateMorzeGroup(group, symbols) ?: "?" }
                .joinToString("")
            return listOf(translation)
        }
        if (text.length > 24) return listOf("Слишком длинный ввод")
        val one = translateMorzeGroup(text, symbols)
        if (one != null) return listOf(one)
        val response = linkedSetOf<String>()
        fun rec(current: String, rest: String) {
            for (i in 0..5) {
                val group = rest.take(i)
                val symbol = symbols[group] ?: continue
                val nextWord = current + symbol
                val nextRest = rest.drop(i)
                if (nextRest.isEmpty()) {
                    val check = if (isRu) normalize(nextWord) else nextWord.lowercase(Locale.ROOT)
                    if (dictionaryArray.any { (if (isRu) normalize(it) else it.lowercase(Locale.ROOT)) == check }) {
                        response.add(nextWord)
                    }
                } else {
                    rec(nextWord, nextRest)
                }
            }
        }
        rec("", text)
        return response.toList()
    }

    private fun translateMorzeGroup(word: String, symbols: Map<String, String>): String? {
        symbols[word]?.let { return it }
        val qm = word.count { it == '?' }
        if (qm == 0) return null
        val letters = mutableListOf<String>()
        for (i in 0 until (1 shl qm)) {
            val chars = word.toCharArray()
            var qIndex = 0
            for (j in chars.indices) {
                if (chars[j] == '?') {
                    val bit = (i shr qIndex) and 1
                    chars[j] = if (bit == 1) '.' else '-'
                    qIndex++
                }
            }
            symbols[String(chars)]?.let { letters.add(it) }
        }
        return if (letters.isNotEmpty()) "[${letters.joinToString("")}]" else null
    }

    private fun morzeDigitsBlock(text: String): String? {
        val compact = text.replace(" ", "")
        if (compact.length % 5 != 0) return null
        val groups = compact.chunked(5)
        val digitsMap = mapOf(
            ".----" to "1", "..---" to "2", "...--" to "3", "....-" to "4", "....." to "5",
            "-...." to "6", "--..." to "7", "---.." to "8", "----." to "9", "-----" to "0"
        )
        val ans = groups.joinToString("") { digitsMap[it] ?: "?" }
        return if (ans.all { it == '?' }) null else ans
    }

    private fun resolveBacon(rawInput: String): String {
        val words = rawInput.trim().split(" ").filter { it.isNotBlank() }.map { it.padStart(5, '0') }
        val phrase1 = words.joinToString("") { baconMap[it] ?: getInvalidSymbol(it) }
        val phrase2 = words.joinToString("") { baconMap[invertBits(it)] ?: getInvalidSymbol(it) }
        val out = mutableListOf<String>()
        if (isValidAnswer(phrase1)) out.add("Bacon1: ${getWordsFromLine(phrase1)}")
        if (isValidAnswer(phrase2)) out.add("Bacon2: ${getWordsFromLine(phrase2)}")
        return if (out.isEmpty()) "Нет результатов" else out.joinToString("\n")
    }

    private fun resolveBodo(rawInput: String): String {
        val words = rawInput.trim().split(" ").filter { it.isNotBlank() }.map { it.padStart(5, '0') }
        val backward = words.map(::invertBits)
        val phrase1 = words.joinToString("") { bodoMap[it] ?: getInvalidSymbol(it) }
        val phrase2 = backward.joinToString("") { bodoMap[it] ?: getInvalidSymbol(it) }
        val digits1 = words.joinToString("") { bodoDigits[it] ?: getInvalidSymbol(it) }
        val digits2 = backward.joinToString("") { bodoDigits[it] ?: getInvalidSymbol(it) }
        val out = mutableListOf<String>()
        if (isValidAnswer(phrase1)) out.add("Bodo1: ${getWordsFromLine(phrase1)}")
        if (isValidAnswer(phrase2)) out.add("Bodo2: ${getWordsFromLine(phrase2)}")
        if (isValidAnswer(digits1)) out.add("Bodo digits1: `$digits1`")
        if (isValidAnswer(digits2)) out.add("Bodo digits2: `$digits2`")
        return if (out.isEmpty()) "Нет результатов" else out.joinToString("\n")
    }

    private fun resolveBinary(rawInput: String): String {
        val words = rawInput.trim().split(" ").filter { it.isNotBlank() }.map { it.padStart(5, '0') }
        val backward = words.map(::invertBits)
        val ru1 = words.joinToString("") { binaryRu[it] ?: getInvalidSymbol(it) }
        val ru2 = backward.joinToString("") { binaryRu[it] ?: getInvalidSymbol(it) }
        val en1 = words.joinToString("") { binaryEn[it] ?: getInvalidSymbol(it) }
        val en2 = backward.joinToString("") { binaryEn[it] ?: getInvalidSymbol(it) }
        val out = mutableListOf<String>()
        if (isValidAnswer(ru1)) out.add("Binary RU1: ${getWordsFromLine(ru1)}")
        if (isValidAnswer(ru2)) out.add("Binary RU2: ${getWordsFromLine(ru2)}")
        if (isValidAnswer(en1)) out.add("Binary EN1: ${getWordsFromLine(en1)}")
        if (isValidAnswer(en2)) out.add("Binary EN2: ${getWordsFromLine(en2)}")
        return if (out.isEmpty()) "Нет результатов" else out.joinToString("\n")
    }

    private fun resolveBrail(rawInput: String): String {
        val words = rawInput.trim().split(" ").filter { it.isNotBlank() }
        if (words.any { it.length != 6 && it != "?" }) return "Нет результатов"
        var ru = ""
        var ruBack = ""
        var en = ""
        var enBack = ""
        var digits = ""
        var digitsBack = ""
        words.forEach { word ->
            val back = invertBits(word)
            ru += brailRu[word] ?: getInvalidSymbol(word)
            ruBack += brailRu[back] ?: getInvalidSymbol(back)
            en += brailEn[word] ?: getInvalidSymbol(word)
            enBack += brailEn[back] ?: getInvalidSymbol(back)
            digits += brailDigits[word] ?: getInvalidSymbol(word)
            digitsBack += brailDigits[back] ?: getInvalidSymbol(back)
        }
        val out = mutableListOf<String>()
        if (isValidAnswer(ru)) out.add("Brail RU 1: ${getWordsFromLine(ru)}")
        if (isValidAnswer(ruBack)) out.add("Brail RU 2: ${getWordsFromLine(ruBack)}")
        if (isValidAnswer(en)) out.add("Brail EN 1: ${getWordsFromLine(en)}")
        if (isValidAnswer(enBack)) out.add("Brail EN 2: ${getWordsFromLine(enBack)}")
        if (isValidAnswer(digits)) out.add("Brail Digits 1: `$digits`")
        if (isValidAnswer(digitsBack)) out.add("Brail Digits 2: `$digitsBack`")
        return if (out.isEmpty()) "Нет результатов" else out.joinToString("\n")
    }

    private fun resolveAlphabet(rawInput: String): String {
        val words = rawInput.trim().split(" ").filter { it.isNotBlank() }
        val en = words.joinToString("") { lettersEn.getOrNull((it.toIntOrNull() ?: 0) - 1)?.toString() ?: "?" }
        val ru = words.joinToString("") { lettersRu.getOrNull((it.toIntOrNull() ?: 0) - 1)?.toString() ?: "?" }
        val enCycle = words.joinToString("") { lettersEn.getOrNull(((it.toIntOrNull() ?: 0) - 1).mod(lettersEn.size))?.toString() ?: "?" }
        val ruCycle = words.joinToString("") { lettersRu.getOrNull(((it.toIntOrNull() ?: 0) - 1).mod(lettersRu.size))?.toString() ?: "?" }
        val out = mutableListOf(
            "EN: ${getWordsFromLine(en)}",
            "RU: ${getWordsFromLine(ru)}"
        )
        if (enCycle != en) out.add("EN цикл: ${getWordsFromLine(enCycle)}")
        if (ruCycle != ru) out.add("RU цикл: ${getWordsFromLine(ruCycle)}")
        return out.joinToString("\n")
    }

    private fun resolveTM(rawInput: String): String {
        val words = rawInput.trim().split(" ").filter { it.isNotBlank() }.map { it.lowercase(Locale.ROOT) }
        val table = repository.getMendeleevElements()
        val symbols = table.map { it.symbol.lowercase(Locale.ROOT) }.toSet()
        return if (words.isNotEmpty() && words.all { symbols.contains(it) }) {
            val elements = words.mapNotNull { w -> table.find { it.symbol.equals(w, true) } }
            val numbers = elements.joinToString(" ") { it.number.toString() }
            val namesRu = elements.joinToString(" ") { it.nameRu }
            val first = elements.joinToString("") { it.nameRu.firstOrNull()?.toString() ?: "" }
            val namesEn = elements.joinToString(" ") { it.name }
            "`$numbers`\n$namesRu ($first)\n$namesEn"
        } else {
            val phrase = words.joinToString("") { w ->
                val idx = w.toIntOrNull()?.minus(1) ?: -1
                table.getOrNull(idx)?.symbol ?: "?"
            }
            "TM: `$phrase`"
        }
    }

    private fun resolveRegions(rawInput: String): String {
        val words = rawInput.trim().split(" ").filter { it.isNotBlank() }
        val regionsFromWords = words.map { regionsMap[it] ?: "??????????????" }
        val first = "Регионы арбуз: ${getWordsFromLine(regionsFromWords.joinToString("") { it.firstOrNull()?.toString() ?: "?" })}"
        val ladder = "Регионы лестн: ${getWordsFromLine(regionsFromWords.map { it.split(" ")[0].replace("-", "") }.mapIndexed { index, s -> s.getOrNull(index) ?: '?' }.joinToString(""))}"
        val list = "Регионы список:\n" + regionsFromWords.joinToString("\n") { "`$it`" }
        return "$first\n$ladder\n$list"
    }

    private fun resolveDick(rawInput: String): String {
        val text = rawInput.trim().lowercase(Locale.ROOT)
        if (!Regex("^([а-яА-Яa-zA-Z]+\\d+\\s?)+$").matches(text)) return "Нет результатов"
        val baseData = text.split(" ").filter { it.isNotBlank() }.map { el ->
            val count = el.takeLast(1).toIntOrNull() ?: 0
            val word = el.dropLast(1)
            RaschItem(word = word, count = count, options = word.length - count + 1)
        }
        val optionsProduct = baseData.fold(1L) { acc, item -> acc * item.options.toLong() }
        if (optionsProduct > 250000L) return "Слишком много вариантов для перебора"
        val combinations = getCombinations(baseData.map { it.options })
        val wordsToFind = linkedSetOf<String>()
        combinations.forEach { combination ->
            var word = ""
            combination.forEachIndexed { index, variant ->
                val item = baseData[index]
                word += item.word.substring(variant, variant + item.count)
            }
            wordsToFind.add(word)
        }
        val correctWords = wordsToFind.filter { repository.isInRaschCombinedSources(it) }
        val correctWords2 = findRaschTwoWords(wordsToFind.toList(), repository.getSlovoformsSet())
        if (correctWords.isEmpty() && correctWords2.isEmpty()) return "Нет результатов"
        val out = mutableListOf<String>()
        if (correctWords2.isNotEmpty()) out.add("НЕСКОЛЬКО СЛОВ\n${correctWords2.joinToString("\n")}")
        if (correctWords.isNotEmpty()) out.add("ЦЕЛЫЕ СЛОВА\n${correctWords.joinToString("\n")}")
        return out.joinToString("\n\n")
    }

    private fun resolveNotes(rawInput: String): String {
        val text = rawInput.trim().lowercase(Locale.ROOT)
        val words = text.split(" ").filter { it.isNotBlank() }
        if (words.any { !notesMap.containsKey(it) }) return "Должны быть ноты через пробел"
        val notes = words.mapNotNull { notesMap[it] }
        val lettersPattern = notes.joinToString("") { it.letter }
        val reg = Regex("^$lettersPattern$")
        val wordsFromLetters = repository.wordsForText("hello")
            .filter { reg.matches(it.lowercase(Locale.ROOT)) }
            .take(PAGINATION_RESULTS_LIMIT)
        val numbers = notes.joinToString(" ") { it.number.toString() }
        val wordRu = notes.joinToString("") { lettersRu[it.number - 1].toString() }
        val wordEn = notes.joinToString("") { lettersEn[it.number - 1].toString() }
        return listOf(
            "буквы: $lettersPattern ${if (wordsFromLetters.isNotEmpty()) "(${wordsFromLetters.joinToString(" ")})" else ""}".trim(),
            "Числа: $numbers",
            "RU Буквы по числам: $wordRu${if (repository.wordExistsRu(wordRu)) " (!!!)" else ""}",
            "EN Буквы по числам: $wordEn${if (repository.wordExistsEn(wordEn)) " (!!!)" else ""}"
        ).joinToString("\n")
    }

    private fun resolveSlovogen(input: String): String {
        val dictionaryWords = repository.wordsForText(input)
        val normalizedInput = normalizeWordLikeSlovogen(input)
        if (normalizedInput.isBlank()) return "Введите буквы."
        val trie = buildTrie(dictionaryWords)
        val letterCounts = mutableMapOf<Char, Int>()
        normalizedInput.forEach { ch ->
            letterCounts[ch] = (letterCounts[ch] ?: 0) + 1
        }

        val results = linkedSetOf<String>()
        val path = StringBuilder()
        fun dfs(node: TrieNode) {
            if (node.end && path.length >= 3) {
                results.add(path.toString())
            }
            node.next.forEach { (ch, child) ->
                val left = letterCounts[ch] ?: 0
                if (left <= 0) return@forEach
                letterCounts[ch] = left - 1
                path.append(ch)
                dfs(child)
                path.deleteCharAt(path.lastIndex)
                letterCounts[ch] = left
            }
        }
        dfs(trie)
        val sorted = results.sortedWith(compareBy<String> { it.length }.thenBy { it }).take(PAGINATION_RESULTS_LIMIT)
        return if (sorted.isEmpty()) {
            "Нет результатов"
        } else {
            "Найдено (${sorted.size}): ${sorted.joinToString(", ")}"
        }
    }

    private fun resolveBooks(input: String): String {
        val found = repository.searchBooks(input)
        return if (found.isEmpty()) {
            "Книги не найдены."
        } else {
            "Книги (${found.size}): ${found.joinToString(", ")}"
        }
    }

    private fun resolveFilms(input: String): String {
        val found = repository.searchFilms(input)
        return if (found.isEmpty()) {
            "Фильмы не найдены."
        } else {
            "Фильмы (${found.size}): ${found.joinToString(", ")}"
        }
    }

    private fun resolvePaintings(input: String): String {
        val found = repository.searchPaintings(input)
        return if (found.isEmpty()) {
            "Картины не найдены."
        } else {
            "Картины (${found.size}): ${found.joinToString(", ")}"
        }
    }

    private fun resolvePhrase(inputRaw: String): String {
        val result = repository.searchPhraseologisms(inputRaw)

        if (result.wikiPhrases.isEmpty() && result.directPhrases.isEmpty() && result.allPhrases.isEmpty()) {
            return "Нет результатов"
        }

        val blocks = mutableListOf<String>()
        if (result.allPhrases.isNotEmpty()) {
            blocks.add("ЧАСТИЧНЫЕ СОВПАДЕНИЯ СЛОВ\n${result.allPhrases.joinToString("\n")}")
        }
        if (result.directPhrases.isNotEmpty()) {
            blocks.add("ПОЛНЫЕ СОВПАДЕНИЯ СЛОВ\n${result.directPhrases.joinToString("\n")}")
        }
        if (result.dslovPhrases.isNotEmpty()) {
            blocks.add("ЕЩЕ КАКИЕ-ТО\n${result.dslovPhrases.joinToString("\n")}")
        }
        if (result.pogovorkiPhrases.isNotEmpty()) {
            blocks.add("ПОГОВОРКИ\n${result.pogovorkiPhrases.joinToString("\n")}")
        }
        if (result.wikiPairs.isNotEmpty()) {
            blocks.add("ВИКИСЛОВАРЬ (УСТОЙЧИВЫЕ ВЫРАЖЕНИЯ)\n${result.wikiPairs.joinToString("\n")}")
        }
        if (result.wikiPhrases.isNotEmpty()) {
            blocks.add("ВИКИСЛОВАРЬ (ФРАЗЕОЛОГИЗМЫ)\n${result.wikiPhrases.joinToString("\n")}")
        }
        return blocks.joinToString("\n\n")
    }

    private fun normalize(value: String): String {
        return value.trim()
            .lowercase(Locale.ROOT)
            .replace('ё', 'е')
    }

    private fun countLetters(word: String): Map<Char, Int> {
        val count = mutableMapOf<Char, Int>()
        normalize(word).forEach { char ->
            count[char] = (count[char] ?: 0) + 1
        }
        return count
    }

    private fun canForm(wordCount: Map<Char, Int>, lettersCount: Map<Char, Int>): Boolean {
        wordCount.forEach { (char, count) ->
            if ((lettersCount[char] ?: 0) < count) return false
        }
        return true
    }

    private fun subtractCounts(
        total: Map<Char, Int>,
        subtract: Map<Char, Int>
    ): Map<Char, Int>? {
        val result = mutableMapOf<Char, Int>()
        total.forEach { (char, count) ->
            val left = count - (subtract[char] ?: 0)
            if (left < 0) return null
            if (left > 0) result[char] = left
        }
        return result
    }

    private fun isEqualCount(count1: Map<Char, Int>, count2: Map<Char, Int>): Boolean {
        if (count1.size != count2.size) return false
        count1.forEach { (char, value) ->
            if (count2[char] != value) return false
        }
        return true
    }

    private fun escapeForRegex(input: String): String {
        val escaped = StringBuilder()
        input.forEach { ch ->
            if (ch in RegexSpecial || ch == '?') {
                escaped.append("\\")
            }
            escaped.append(ch)
        }
        return escaped.toString()
    }

    private fun isMetagramma(word1Raw: String, word2Raw: String): Boolean {
        val word1 = normalize(word1Raw)
        val word2 = normalize(word2Raw)
        if (word1.length != word2.length) return false
        var equal = 0
        for (i in word1.indices) {
            if (word1[i] == word2[i]) equal++
        }
        return equal == word1.length - 1
    }

    private fun isPlusogramma(word1Raw: String, word2Raw: String): Boolean {
        val word1 = normalize(word1Raw)
        val word2 = normalize(word2Raw)
        if (word1.length == word2.length) return false
        val sorted1 = word1.toCharArray().sorted().joinToString("")
        val sorted2 = word2.toCharArray().sorted().joinToString("")
        val shortWord = if (sorted1.length > sorted2.length) sorted2 else sorted1
        val longWord = if (sorted1.length > sorted2.length) sorted1 else sorted2
        var diffFound = false
        for (i in longWord.indices) {
            val shortIndex = if (diffFound) i - 1 else i
            val shortChar = shortWord.getOrNull(shortIndex)
            if (shortChar != longWord[i]) {
                if (diffFound) return false
                diffFound = true
            }
        }
        return true
    }

    private fun isBrukva(word1Raw: String, word2Raw: String): Boolean {
        val word1 = normalize(word1Raw)
        val word2 = normalize(word2Raw)
        if (kotlin.math.abs(word1.length - word2.length) != 1) return false
        var longWord = if (word1.length > word2.length) word1 else word2
        val shortWord = if (word1.length > word2.length) word2 else word1
        var diffFound = false
        for (i in shortWord.indices) {
            if (longWord[i] != shortWord[i]) {
                if (diffFound) return false
                longWord = longWord.removeRange(i, i + 1)
                diffFound = true
            }
        }
        return diffFound
    }

    private fun isSubword(word1Raw: String, word2Raw: String): Boolean {
        val word1 = normalize(word1Raw)
        val word2 = normalize(word2Raw)
        if (word1 == word2) return false
        var matched = 0
        for (i in word1.indices) {
            if (word1[i] == word2.getOrNull(matched)) {
                matched++
            }
        }
        return matched == word2.length
    }

    private fun isLogogrif(word1Raw: String, word2Raw: String): Boolean {
        val word1 = normalize(word1Raw)
        val word2 = normalize(word2Raw)
        if (word1.length == word2.length || kotlin.math.abs(word1.length - word2.length) > 2) return false
        val isWord1Longer = word1.length > word2.length
        val longWord = if (isWord1Longer) word1 else word2
        val shortWord = if (isWord1Longer) word2 else word1
        var isBreak = false
        var diff = 0
        var equal = 0
        var i = 0
        while (i < longWord.length) {
            val shortIndex = i - diff
            if (shortWord.getOrNull(shortIndex) != longWord[i]) {
                if (isBreak) return false
                diff++
            }
            if (shortWord.getOrNull(i - diff) == longWord[i]) {
                equal++
                if (diff > 0) isBreak = true
            }
            i++
        }
        if (isBreak && shortWord.length > i - diff) return false
        return kotlin.math.abs(equal - longWord.length) == 1
    }

    private fun isAnagrammaSimple(word1Raw: String, word2Raw: String): Boolean {
        val w1 = normalize(word1Raw).toCharArray().sorted().joinToString("")
        val w2 = normalize(word2Raw).toCharArray().sorted().joinToString("")
        return w1 == w2
    }

    private fun resolveSeveralWordsSimple(
        rawInput: String,
        task: (String, String) -> Boolean
    ): String {
        val answers = resolveSeveralWordsList(rawInput, task)
        return if (answers.isEmpty()) "Нет результатов" else "Найдено (${answers.size}): ${answers.joinToString(", ")}"
    }

    private fun resolveSeveralWordsByLength(
        rawInput: String,
        task: (String, String) -> Boolean
    ): String {
        val answers = resolveSeveralWordsList(rawInput, task)
        if (answers.isEmpty()) {
            return "ПОКОРОЧЕ\nНет результатов\n\nПОДЛИННЕЕ\nНет результатов"
        }
        val grouped = answers.groupBy { it.length }.toSortedMap()
        return grouped.entries.joinToString("\n\n") { (len, vals) ->
            "$len\n${vals.take(PAGINATION_RESULTS_LIMIT).joinToString(" ")}"
        }
    }

    private fun resolveSeveralWordsList(
        rawInput: String,
        task: (String, String) -> Boolean
    ): List<String> {
        val words = rawInput.trim().split("\\s+".toRegex()).filter { it.isNotBlank() }
        val answers = mutableListOf<String>()

        if (words.size == 1) {
            val text = normalize(words[0])
            repository.wordsForText(text).asSequence()
                .map(::normalize)
                .filter { task(it, text) }
                .distinct()
                .take(PAGINATION_RESULTS_LIMIT)
                .forEach { answers.add(it) }
            return answers
        }

        val word1Raw = words[0]
        val word2Raw = words[1]
        val source1: List<String>
        val source2: List<String>
        val formatter: (String, String) -> String

        if (word1Raw.endsWith("!")) {
            source1 = listOf(normalize(word1Raw.removeSuffix("!")))
            source2 = repository.associationsForWord(word2Raw)
            formatter = { _, b -> b }
        } else {
            source1 = repository.associationsForWord(word1Raw)
            source2 = repository.associationsForWord(word2Raw)
            formatter = { a, b -> "$a $b" }
        }

        source1.forEach { a ->
            source2.forEach { b ->
                if (task(a, b)) {
                    answers.add(formatter(a, b))
                }
            }
        }
        return answers.distinct().take(PAGINATION_RESULTS_LIMIT)
    }

    private fun isGapoifika(text: String, title: String): Boolean {
        val preparedName = title
            .lowercase(Locale.ROOT)
            .replace('ё', 'е')
            .replace(",", "")
            .replace(":", "")
            .replace(".", "")
            .replace("!", "")
            .split(" ")
            .filter { it.isNotBlank() }
        val oneLetter = preparedName.joinToString("") { it.take(1) }
        val twoLetters = preparedName.joinToString("") { it.take(2) }
        return text == oneLetter.lowercase(Locale.ROOT) || text == twoLetters.lowercase(Locale.ROOT)
    }

    private fun isBinary(text: String): Boolean = text.matches(Regex("^[01]+$"))

    private fun isDecimal(text: String): Boolean = text.matches(Regex("^[0-9]+$"))

    private fun isHex(text: String): Boolean = text.matches(Regex("^[0-9a-f]+$"))

    private fun normalizeWordLikeSlovogen(value: String): String {
        return value.lowercase(Locale.ROOT)
            .replace('ё', 'е')
            .filter { it.isLetter() }
    }

    private fun buildTrie(words: List<String>): TrieNode {
        val root = TrieNode()
        words.forEach { raw ->
            val word = normalizeWordLikeSlovogen(raw)
            if (word.isBlank()) return@forEach
            var node = root
            word.forEach { ch ->
                node = node.next.getOrPut(ch) { TrieNode() }
            }
            node.end = true
        }
        return root
    }

    private fun romanToArabic(roman: String): Int? {
        val map = mapOf(
            'i' to 1,
            'v' to 5,
            'x' to 10,
            'l' to 50,
            'c' to 100,
            'd' to 500,
            'm' to 1000
        )
        var arabic = 0
        for (i in roman.indices) {
            val current = map[roman[i]] ?: return null
            val next = roman.getOrNull(i + 1)?.let { map[it] }
            if (next != null && current < next) {
                arabic -= current
            } else {
                arabic += current
            }
        }
        return arabic
    }

    private fun arabicToRoman(numRaw: Int): String? {
        if (numRaw <= 0) return null
        var num = numRaw
        val values = listOf(
            1000 to "M",
            900 to "CM",
            500 to "D",
            400 to "CD",
            100 to "C",
            90 to "XC",
            50 to "L",
            40 to "XL",
            10 to "X",
            9 to "IX",
            5 to "V",
            4 to "IV",
            1 to "I"
        )
        val out = StringBuilder()
        values.forEach { (value, symbol) ->
            while (num >= value) {
                out.append(symbol)
                num -= value
            }
        }
        return out.toString()
    }

    private class TrieNode(
        var end: Boolean = false,
        val next: MutableMap<Char, TrieNode> = mutableMapOf()
    )

    private fun getWordsFromLine(text: String, wordsSet: Set<String>? = null): String {
        val words = findWordsByMask(text, wordsSet)
        if (words.isNotEmpty() && words.all { it == text }) {
            return "`$text` (!!!)"
        }
        return if (words.isEmpty()) {
            "`$text`"
        } else {
            "`$text` (${words.joinToString(" ")})"
        }
    }

    private fun findWordsByMask(text: String, wordsSet: Set<String>? = null): List<String> {
        val dict = (wordsSet ?: repository.wordsForText(text).map(::normalize).toSet())
        if (text.isBlank()) return emptyList()
        // Fast-path for exact words avoids full dictionary regex scan.
        if (!text.contains('?')) {
            val normalized = normalize(text)
            return if (dict.contains(normalized)) listOf(normalized) else emptyList()
        }
        val regex = Regex("^" + text.map { if (it == '?') "\\S" else Regex.escape(it.toString()) }.joinToString("") + "$")
        return dict.filter { regex.matches(it) }.take(50)
    }

    private fun getSborkaLadder(lines: List<String>): String =
        lines.mapIndexed { index, line -> line.getOrNull(index) ?: '?' }.joinToString("")

    private fun getSborkaLadderBack(lines: List<String>): String =
        lines.mapIndexed { index, line -> line.getOrNull(line.length - 1 - index) ?: '?' }.joinToString("")

    private fun getSborkaWatermelonBack(lines: List<String>): String =
        lines.map { line -> line.getOrNull(line.length - 1) ?: '?' }.joinToString("")

    private fun getSborkaWatermelon(lines: List<String>): String =
        lines.map { line -> line.getOrNull(0) ?: '?' }.joinToString("")

    private fun getSborkaByLineNumbersWatermelon(lines: List<String>, numbers: List<Int>): String =
        numbers.map { number -> lines.getOrNull(number - 1)?.getOrNull(0) ?: '?' }.joinToString("")

    private fun getSborkaByLineNumbersWatermelonBack(lines: List<String>, numbers: List<Int>): String =
        numbers.map { number ->
            val line = lines.getOrNull(number - 1)
            line?.getOrNull((line.length - 1).coerceAtLeast(0)) ?: '?'
        }.joinToString("")

    private fun getSborkaByLineNumbersLadder(lines: List<String>, numbers: List<Int>): String =
        numbers.mapIndexed { index, number ->
            lines.getOrNull(number - 1)?.getOrNull(index) ?: '?'
        }.joinToString("")

    private fun getSborkaByLineNumbersLadderBack(lines: List<String>, numbers: List<Int>): String =
        numbers.mapIndexed { index, number ->
            val line = lines.getOrNull(number - 1)
            line?.getOrNull(line.length - 1 - index) ?: '?'
        }.joinToString("")

    private fun getSborkaByNumbers(lines: List<String>, numbers: List<Int>): String =
        lines.mapIndexedNotNull { index, line ->
            val number = numbers.getOrNull(index) ?: return@mapIndexedNotNull null
            if (number == 0) return@mapIndexedNotNull null
            line.getOrNull(number - 1) ?: '?'
        }.joinToString("")

    private fun getSborkaLines(text: String): Pair<List<String>, List<Int>> {
        val rawLines = text.split('\n')
        if (rawLines.size == 1) {
            return getSborkaLineWords(text) to emptyList()
        }
        val lastLine = rawLines.last()
        val lastLineWords = lastLine.trim().split(" ").filter { it.isNotBlank() }
        val isLastLineNumbers = lastLineWords.isNotEmpty() && lastLineWords.all { it.matches(Regex("[0-9]+")) }
        if (rawLines.size == 2 && isLastLineNumbers) {
            return getSborkaLineWords(rawLines[0]) to lastLineWords.map { it.toInt() }
        }
        val lines = if (isLastLineNumbers) rawLines.dropLast(1) else rawLines
        val numbers = if (isLastLineNumbers) lastLineWords.map { it.toInt() } else emptyList()
        return prepareSborkaLines(lines) to numbers
    }

    private fun prepareSborkaLines(lines: List<String>): List<String> {
        return lines.map { line ->
            line.trim().replace(Regex("[^а-яА-Яa-zA-ZёЁ]"), "")
        }.filter { it.isNotBlank() }
    }

    private fun getSborkaLineWords(line: String): List<String> = prepareSborkaLines(line.split(" "))

    private fun vigenereTransform(text: String, keyRaw: String, encrypt: Boolean): String {
        val key = keyRaw.filter { charIndex(it) != -1 }
        if (key.isBlank()) return text
        val out = StringBuilder()
        var keyIndex = 0
        text.forEach { c ->
            val ci = charIndex(c)
            if (ci == -1) {
                out.append(c)
            } else {
                val ki = charIndex(key[keyIndex % key.length])
                val idx = if (encrypt) {
                    (ci + ki) % lettersRu.size
                } else {
                    (ci - ki + lettersRu.size) % lettersRu.size
                }
                val ch = if (c.isLowerCase()) lettersRu[idx] else lettersRu[idx].uppercaseChar()
                out.append(ch)
                keyIndex++
            }
        }
        return out.toString()
    }

    private fun charIndex(ch: Char): Int = lettersRu.indexOf(ch.lowercaseChar())

    private val lettersRu = listOf(
        'а', 'б', 'в', 'г', 'д', 'е', 'ё', 'ж', 'з', 'и', 'й', 'к', 'л', 'м', 'н', 'о', 'п',
        'р', 'с', 'т', 'у', 'ф', 'х', 'ц', 'ч', 'ш', 'щ', 'ъ', 'ы', 'ь', 'э', 'ю', 'я'
    )

    companion object {
        private const val PAGINATION_RESULTS_LIMIT = 1000
        private val RegexSpecial = setOf('.', '+', '(', ')', '[', ']', '{', '}', '^', '$', '|', '\\')
    }
}
