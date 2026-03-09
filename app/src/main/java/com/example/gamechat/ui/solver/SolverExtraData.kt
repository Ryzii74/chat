package com.example.gamechat.ui.solver

data class RaschItem(
    val word: String,
    val count: Int,
    val options: Int
)

data class NoteMapping(
    val letter: String,
    val number: Int
)

fun invertBits(word: String): String {
    return word.replace('1', '2').replace('0', '1').replace('2', '0')
}

fun isValidAnswer(answer: String): Boolean {
    return answer.any { it != '&' && it != '?' }
}

fun getInvalidSymbol(word: String): String {
    val accepted = setOf("0000?", "1111?", "?")
    return if (accepted.contains(word)) "?" else "&"
}

fun getCombinations(optionsArray: List<Int>): List<List<Int>> {
    var optionsSum = 1L
    optionsArray.forEach { optionsSum *= it.toLong() }
    val dividers = mutableListOf<Int>()
    var start = 1
    optionsArray.forEach { option ->
        start *= option
        dividers.add(start)
    }

    val combinations = mutableListOf<List<Int>>()
    for (i in 0 until optionsSum) {
        val combination = mutableListOf((i % dividers[0]).toInt())
        for (j in 0 until optionsArray.size - 1) {
            combination.add(((i / dividers[j]) % optionsArray[j + 1]).toInt())
        }
        combinations.add(combination)
    }
    return combinations
}

fun findRaschTwoWords(words: List<String>, slovoforms: Set<String>): List<String> {
    return words.mapNotNull { word ->
        for (i in 1 until word.length) {
            val word1 = word.substring(0, i)
            val word2 = word.substring(i)
            if (slovoforms.contains(word1) && slovoforms.contains(word2)) {
                return@mapNotNull "$word1 $word2"
            }
        }
        null
    }
}

val lettersEn = listOf(
    'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j', 'k', 'l', 'm',
    'n', 'o', 'p', 'q', 'r', 's', 't', 'u', 'v', 'w', 'x', 'y', 'z'
)

val notesMap = mapOf(
    "до" to NoteMapping(letter = "c", number = 1),
    "ре" to NoteMapping(letter = "d", number = 2),
    "ми" to NoteMapping(letter = "e", number = 3),
    "фа" to NoteMapping(letter = "f", number = 4),
    "соль" to NoteMapping(letter = "g", number = 5),
    "ля" to NoteMapping(letter = "a", number = 6),
    "си" to NoteMapping(letter = "[hb]", number = 7)
)

val baconMap = mapOf(
    "00000" to "a", "00001" to "b", "00010" to "c", "00011" to "d",
    "00100" to "e", "00101" to "f", "00110" to "g", "00111" to "h",
    "01000" to "[ij]", "01001" to "k", "01010" to "l", "01011" to "m",
    "01100" to "n", "01101" to "o", "01110" to "p", "01111" to "q",
    "10000" to "r", "10001" to "s", "10010" to "t", "10011" to "[uv]",
    "10100" to "w", "10101" to "x", "10111" to "z"
)

val bodoMap = mapOf(
    "00100" to "a", "00110" to "e", "00010" to "e", "00011" to "i", "00111" to "o",
    "00101" to "u", "00001" to "y", "01001" to "b", "01101" to "c", "01111" to "d",
    "01011" to "f", "01010" to "g", "01110" to "h", "01100" to "j", "11100" to "k",
    "11110" to "l", "11010" to "m", "11011" to "n", "11111" to "p", "11101" to "q",
    "11001" to "r", "10001" to "s", "10101" to "t", "10111" to "v", "10011" to "w",
    "10010" to "x", "10110" to "z", "10100" to "-", "10000" to " ", "01000" to " "
)

val bodoDigits = mapOf(
    "00100" to "1", "00010" to "2", "00001" to "3", "00101" to "4", "00111" to "5",
    "00110" to "1/", "00011" to "3/", "01100" to "6", "01010" to "7", "01001" to "8",
    "01101" to "9", "01111" to "0", "01110" to "4/", "01011" to "5/", "10100" to ".",
    "10010" to "9/", "10001" to "7/", "10101" to "2/", "10111" to "'", "10110" to ":",
    "10011" to "?", "11100" to "(", "11010" to ")", "11001" to "-", "11101" to "/",
    "11111" to "+", "11110" to "=", "11011" to "£", "10000" to " ", "01000" to " "
)

val binaryRu = mapOf(
    "00001" to "а", "00010" to "б", "00011" to "в", "00100" to "г", "00101" to "д",
    "00110" to "е", "00111" to "ё", "01000" to "ж", "01001" to "з", "01010" to "и",
    "01011" to "й", "01100" to "к", "01101" to "л", "01110" to "м", "01111" to "н",
    "10000" to "о", "10001" to "п", "10010" to "р", "10011" to "с", "10100" to "т",
    "10101" to "у", "10110" to "ф", "10111" to "х", "11000" to "ц", "11001" to "ч",
    "11010" to "ш", "11011" to "щ", "11100" to "ъ", "11101" to "ы", "11110" to "ь",
    "11111" to "э", "100000" to "ю", "100001" to "я"
)

val binaryEn = mapOf(
    "00001" to "a", "00010" to "b", "00011" to "c", "00100" to "d", "00101" to "e",
    "00110" to "f", "00111" to "g", "01000" to "h", "01001" to "i", "01010" to "j",
    "01011" to "k", "01100" to "l", "01101" to "m", "01110" to "n", "01111" to "o",
    "10000" to "p", "10001" to "q", "10010" to "r", "10011" to "s", "10100" to "t",
    "10101" to "u", "10110" to "v", "10111" to "w", "11000" to "x", "11001" to "y",
    "11010" to "z"
)

val brailDigits = mapOf(
    "100000" to "1", "101000" to "2", "110000" to "3", "110100" to "4", "100100" to "5",
    "111000" to "6", "111100" to "7", "101100" to "8", "011000" to "9", "011100" to "0"
)

val brailRu = mapOf(
    "100000" to "а", "101000" to "б", "011101" to "в", "111100" to "г", "110100" to "д",
    "100100" to "е", "100001" to "ё", "011100" to "ж", "100111" to "з", "011000" to "и",
    "111011" to "й", "100010" to "к", "101010" to "л", "110010" to "м", "110110" to "н",
    "100110" to "о", "111010" to "п", "101110" to "р", "011010" to "с", "011110" to "т",
    "100011" to "у", "111000" to "ф", "101100" to "х", "110000" to "ц", "111110" to "ч",
    "100101" to "ш", "110011" to "щ", "101111" to "ъ", "011011" to "ы", "011111" to "ь",
    "011001" to "э", "101101" to "ю", "111001" to "я"
)

val brailEn = mapOf(
    "100000" to "a", "101000" to "b", "110000" to "c", "110100" to "d", "100100" to "e",
    "111000" to "f", "111100" to "g", "101100" to "h", "011000" to "i", "011100" to "j",
    "100010" to "k", "101010" to "l", "110010" to "m", "110110" to "n", "100110" to "o",
    "111010" to "p", "111110" to "q", "101110" to "r", "011010" to "s", "011110" to "t",
    "100011" to "u", "101011" to "v", "011101" to "w", "110011" to "x", "110111" to "y",
    "100111" to "z"
)

val morzeRuMap = mapOf(
    ".-" to "а", "-..." to "б", ".--" to "в", "--." to "г", "-.." to "д", "." to "е", "...-" to "ж",
    "--.." to "з", ".." to "и", ".---" to "й", "-.-" to "к", ".-.." to "л", "--" to "м", "-." to "н",
    "---" to "о", ".--." to "п", ".-." to "р", "..." to "с", "-" to "т", "..-" to "у", "..-." to "ф",
    "...." to "х", "-.-." to "ц", "---." to "ч", "----" to "ш", "--.-" to "щ", "--.--" to "ъ",
    "-.--" to "ы", "-..-" to "ь", "..-.." to "э", "..--" to "ю", ".-.-" to "я",
    ".----" to "1", "..---" to "2", "...--" to "3", "....-" to "4", "....." to "5",
    "-...." to "6", "--..." to "7", "---.." to "8", "----." to "9", "-----" to "0"
)

val morzeEnMap = mapOf(
    ".-" to "a", "-..." to "b", "-.-." to "c", "-.." to "d", "." to "e", "..-." to "f", "--." to "g",
    "...." to "h", ".." to "i", ".---" to "j", "-.-" to "k", ".-.." to "l", "--" to "m", "-." to "n",
    "---" to "o", ".--." to "p", "--.-" to "q", ".-." to "r", "..." to "s", "-" to "t", "..-" to "u",
    "...-" to "v", ".--" to "w", "-..-" to "x", "-.--" to "y", "--.." to "z",
    ".----" to "1", "..---" to "2", "...--" to "3", "....-" to "4", "....." to "5",
    "-...." to "6", "--..." to "7", "---.." to "8", "----." to "9", "-----" to "0"
)

val regionsMap = mapOf(
    "77" to "москва",
    "78" to "санкт-петербург",
    "97" to "москва",
    "98" to "санкт-петербург",
    "99" to "москва",
    "177" to "москва",
    "178" to "санкт-петербург",
    "197" to "москва",
    "198" to "санкт-петербург",
    "199" to "москва",
    "797" to "москва",
    "799" to "москва",
    "977" to "москва",
    "23" to "краснодарский К",
    "24" to "красноярский К",
    "61" to "ростовская О",
    "63" to "самарская О",
    "74" to "челябинская О",
    "50" to "московская О",
    "90" to "московская О",
    "150" to "московская О",
    "190" to "московская О",
    "250" to "московская О",
    "550" to "московская О",
    "750" to "московская О",
    "790" to "московская О",
    "1" to "адыгея Р",
    "2" to "башкортостан Р",
    "3" to "бурятия Р",
    "4" to "алтай Р",
    "5" to "дагестан Р",
    "6" to "ингушетия Р",
    "7" to "кабардино-балкарская Р",
    "8" to "калмыкия Р",
    "9" to "карачаево-черкесская Р",
    "10" to "карелия Р",
    "11" to "коми Р",
    "12" to "марий эл Р",
    "13" to "мордовия Р",
    "14" to "саха (якутия) Р",
    "15" to "северная осетия (алания) Р",
    "16" to "татарстан Р",
    "17" to "тыва Р",
    "18" to "удмуртская Р",
    "19" to "хакасия Р",
    "20" to "чеченская Р",
    "21" to "чувашская - чувашия Р",
    "22" to "алтайский К",
    "25" to "приморский К",
    "26" to "ставропольский К",
    "27" to "хабаровский К",
    "28" to "амурская О",
    "29" to "архангельская О",
    "30" to "астраханская О"
)
