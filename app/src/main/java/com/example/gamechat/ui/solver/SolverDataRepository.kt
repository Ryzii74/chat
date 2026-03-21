package com.example.gamechat.ui.solver

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL
import kotlin.collections.AbstractList
import java.util.Locale

class SolverDataRepository(context: Context) {
    private val appContext = context.applicationContext

    companion object {
        @Volatile
        private var cachedData: SolverData? = null
        @Volatile
        private var cachedRuWords: List<String>? = null
        @Volatile
        private var cachedEnWords: List<String>? = null
        @Volatile
        private var cachedRuWordsSet: Set<String>? = null
        @Volatile
        private var cachedEnWordsSet: Set<String>? = null
        @Volatile
        private var cachedSlovoformsSet: Set<String>? = null
        @Volatile
        private var cachedRaschCombinedSet: Set<String>? = null
        private val cacheLock = Any()
        private val kartaslovCache = mutableMapOf<String, List<String>>()
    }

    fun preloadWordDictionaries() {
        ensureRuWords()
        ensureEnWords()
    }

    fun wordsForText(text: String): List<String> {
        val hasRu = text.any { it in '\u0400'..'\u04FF' }
        val hasEn = text.any { it in 'a'..'z' || it in 'A'..'Z' }
        return when {
            hasRu && !hasEn -> ensureRuWords()
            hasEn && !hasRu -> ensureEnWords()
            else -> ensureRuWords() + ensureEnWords()
        }
    }

    fun associationsForWord(word: String): List<String> {
        val data = ensureData()
        val normalized = normalize(word)
        if (normalized.isBlank()) return emptyList()
        return data.associationsRu[normalized]
            ?: data.associationsEn[normalized]
            ?: emptyList()
    }

    fun wordExistsForText(text: String, word: String): Boolean {
        val normalized = normalize(word)
        if (normalized.isBlank()) return false
        val hasRu = text.any { it in '\u0400'..'\u04FF' }
        val hasEn = text.any { it in 'a'..'z' || it in 'A'..'Z' }
        return when {
            hasRu && !hasEn -> getRuWordsSet().contains(normalized)
            hasEn && !hasRu -> getEnWordsSet().contains(normalized)
            else -> getRuWordsSet().contains(normalized) || getEnWordsSet().contains(normalized)
        }
    }

    fun wordExistsRu(word: String): Boolean {
        val normalized = normalize(word)
        if (normalized.isBlank()) return false
        return getRuWordsSet().contains(normalized)
    }

    fun wordExistsEn(word: String): Boolean {
        val normalized = normalize(word)
        if (normalized.isBlank()) return false
        return getEnWordsSet().contains(normalized)
    }

    fun searchBooks(query: String): List<String> {
        return searchCatalog(query, ensureData().books)
    }

    fun searchFilms(query: String): List<String> {
        return searchCatalog(query, ensureData().films)
    }

    fun searchPaintings(query: String): List<String> {
        return searchCatalog(query, ensureData().paintings)
    }

    fun getBookTitles(): List<String> = ensureData().books.map { it.title }

    fun getFilmTitles(): List<String> = ensureData().films.map { it.title }

    fun getPaintingTitles(): List<String> = ensureData().paintings.map { it.title }

    fun getMendeleevElements(): List<MendeleevElement> = ensureData().mendeleev

    fun getSlovoformsSet(): Set<String> {
        cachedSlovoformsSet?.let { return it }
        synchronized(cacheLock) {
            cachedSlovoformsSet?.let { return it }
            val set = loadWords("solver/russian-with-slovoforms.txt").toHashSet()
            cachedSlovoformsSet = set
            return set
        }
    }

    fun isInRaschCombinedSources(word: String): Boolean {
        val normalized = normalizeForRaschObject(word)
        if (normalized.isBlank()) return false
        val data = ensureData()
        val set = getRaschCombinedSet(data)
        return set.contains(normalized)
    }

    fun loadAdjectives(word: String): List<String> {
        return loadKartaslovWords(
            section = "какой-бывает",
            word = word
        )
    }

    fun loadNouns(word: String): List<String> {
        return loadKartaslovWords(
            section = "что-или-кто-бывает",
            word = word
        )
    }

    fun searchPhraseologisms(inputRaw: String): PhraseSearchResult {
        val data = ensureData()
        val tokens = inputRaw.trim().split("\\s+".toRegex()).filter { it.isNotBlank() }
        if (tokens.isEmpty()) return PhraseSearchResult.empty()

        val firstToken = normalize(tokens.first())
        val isWordsCount = Regex("[1-9]").containsMatchIn(firstToken)
        val wordsCount = firstToken.toIntOrNull()
        val words = if (isWordsCount) {
            tokens.drop(1).map(::normalize).filter { it.isNotBlank() }
        } else {
            tokens.map(::normalize).filter { it.isNotBlank() }
        }

        val directPhrases = data.phrases.filter { phrase ->
            isPhraseMatchWordsCount(phrase.searchText, isWordsCount, wordsCount) &&
                words.all { word -> Regex("(^|\\s)${Regex.escape(word)}(\\s|$)").containsMatchIn(phrase.searchText) }
        }.map { it.original }

        val directSet = directPhrases.toSet()
        val allPhrases = data.phrases.filter { phrase ->
            isPhraseMatchWordsCount(phrase.searchText, isWordsCount, wordsCount) &&
                words.all { word -> !directSet.contains(phrase.original) && phrase.searchText.contains(word) }
        }.map { it.original }

        val wikiPhrases = data.wikislovar.filter { phrase ->
            isPhraseMatchWordsCount(phrase.searchText, isWordsCount, wordsCount) &&
                words.all { word -> phrase.searchText.contains(word) }
        }.map { it.original }

        val wikiPairs = data.wikislovarPairs.filter { phrase ->
            isPhraseMatchWordsCount(phrase.searchText, isWordsCount, wordsCount) &&
                words.all { word -> phrase.searchText.contains(word) }
        }.map { it.original }

        val pogovorkiPhrases = data.pogovorki.filter { phrase ->
            isPhraseMatchWordsCount(phrase.searchText, isWordsCount, wordsCount) &&
                words.all { word -> phrase.searchText.contains(word) }
        }.map { it.original }

        val dslovPhrases = data.dslov.filter { phrase ->
            isPhraseMatchWordsCount(phrase.searchText, isWordsCount, wordsCount) &&
                words.all { word -> phrase.searchText.contains(word) }
        }.map { it.original }

        return PhraseSearchResult(
            directPhrases = directPhrases,
            allPhrases = allPhrases,
            wikiPhrases = wikiPhrases,
            wikiPairs = wikiPairs,
            pogovorkiPhrases = pogovorkiPhrases,
            dslovPhrases = dslovPhrases
        )
    }

    private fun ensureData(): SolverData {
        cachedData?.let { return it }
        synchronized(cacheLock) {
            cachedData?.let { return it }
            val data = SolverData(
                wordsRu = ensureRuWords(),
                wordsEn = ensureEnWords(),
                associationsRu = loadAssociations("solver/associations_ru.json"),
                associationsEn = loadAssociations("solver/associations_en.json"),
                books = loadCatalogEntries("solver/books.txt"),
                films = loadCatalogEntries("solver/films.txt"),
                paintings = loadCatalogEntries("solver/paintings.txt"),
                phrases = loadPhraseEntries(
                    assetPath = "solver/phrases.txt",
                    splitByCarriageReturn = true,
                    removePunctuation = false,
                    toLowerCase = false
                ),
                pogovorki = loadPhraseEntries(
                    assetPath = "solver/pogovorki.txt",
                    splitByCarriageReturn = false,
                    removePunctuation = true,
                    toLowerCase = true
                ),
                wikislovar = loadPhraseJsonEntries("solver/wikislovar.json"),
                wikislovarPairs = loadPhraseJsonEntries("solver/wikislovarPairs.json"),
                dslov = loadPhraseJsonEntries("solver/dslov.json"),
                mendeleev = loadMendeleev("solver/mendeleev.json")
            )
            cachedData = data
            return data
        }
    }

    private fun ensureRuWords(): List<String> {
        cachedRuWords?.let { return it }
        synchronized(cacheLock) {
            cachedRuWords?.let { return it }
            val words = loadWords("solver/words-ru-merged.txt")
            cachedRuWords = words
            return words
        }
    }

    private fun ensureEnWords(): List<String> {
        cachedEnWords?.let { return it }
        synchronized(cacheLock) {
            cachedEnWords?.let { return it }
            val words = loadWords("solver/words-en-merged.txt")
            cachedEnWords = words
            return words
        }
    }

    private fun getRaschCombinedSet(data: SolverData): Set<String> {
        cachedRaschCombinedSet?.let { return it }
        synchronized(cacheLock) {
            cachedRaschCombinedSet?.let { return it }
            val set = linkedSetOf<String>()
            data.wordsRu.forEach { set.add(normalizeForRaschObject(it)) }
            data.wordsEn.forEach { set.add(normalizeForRaschObject(it)) }
            data.phrases.forEach { set.add(normalizeForRaschObject(it.original)) }
            data.pogovorki.forEach { set.add(normalizeForRaschObject(it.original)) }
            data.wikislovar.forEach { set.add(normalizeForRaschObject(it.original)) }
            data.dslov.forEach { set.add(normalizeForRaschObject(it.original)) }
            val filtered = set.filter { it.isNotBlank() }.toSet()
            cachedRaschCombinedSet = filtered
            return filtered
        }
    }

    private fun getRuWordsSet(): Set<String> {
        cachedRuWordsSet?.let { return it }
        synchronized(cacheLock) {
            cachedRuWordsSet?.let { return it }
            val set = ensureRuWords().toHashSet()
            cachedRuWordsSet = set
            return set
        }
    }

    private fun getEnWordsSet(): Set<String> {
        cachedEnWordsSet?.let { return it }
        synchronized(cacheLock) {
            cachedEnWordsSet?.let { return it }
            val set = ensureEnWords().toHashSet()
            cachedEnWordsSet = set
            return set
        }
    }

    private fun loadWords(assetPath: String): List<String> {
        val output = ByteArrayOutputStream()
        val offsets = IntAccumulator()
        offsets.add(0)

        appContext.assets.open(assetPath).bufferedReader().useLines { sequence ->
            sequence.forEach { raw ->
                val word = normalize(raw)
                if (word.isNotBlank()) {
                    output.write(word.toByteArray(Charsets.UTF_8))
                    output.write('\n'.code)
                    offsets.add(output.size())
                }
            }
        }
        return PackedWordList(
            data = output.toByteArray(),
            offsets = offsets.toIntArray()
        )
    }

    private fun loadAssociations(assetPath: String): Map<String, List<String>> {
        val json = appContext.assets.open(assetPath).bufferedReader().use { it.readText() }
        val objectJson = JSONObject(json)
        val result = mutableMapOf<String, List<String>>()
        val keys = objectJson.keys()
        while (keys.hasNext()) {
            val rawKey = keys.next()
            val key = normalize(rawKey)
            val list = objectJson.optJSONArray(rawKey) ?: continue
            val values = mutableListOf<String>()
            for (i in 0 until list.length()) {
                val value = normalize(list.optString(i))
                if (value.isNotBlank()) values.add(value)
            }
            if (key.isNotBlank() && values.isNotEmpty()) {
                result[key] = values.distinct()
            }
        }
        return result
    }

    private fun loadCatalogEntries(assetPath: String): List<CatalogEntry> {
        val result = mutableListOf<CatalogEntry>()
        appContext.assets.open(assetPath).bufferedReader().useLines { sequence ->
            sequence.forEach { raw ->
                val title = raw.trim()
                if (title.isBlank()) return@forEach
                result.add(
                    CatalogEntry(
                        title = title,
                        searchText = normalizeForCatalogSearch(title)
                    )
                )
            }
        }
        return result
    }

    private fun searchCatalog(query: String, catalog: List<CatalogEntry>): List<String> {
        val normalizedQuery = normalizeForCatalogSearch(query)
        if (normalizedQuery.isBlank()) return emptyList()
        return catalog.asSequence()
            .filter { it.searchText.contains(normalizedQuery) }
            .map { it.title }
            .take(60)
            .toList()
    }

    private fun loadKartaslovWords(section: String, word: String): List<String> {
        val normalizedWord = normalize(word)
        if (normalizedWord.isBlank()) return emptyList()
        val key = "$section::$normalizedWord"
        synchronized(kartaslovCache) {
            kartaslovCache[key]?.let { return it }
        }

        val encodedSection = URLEncoder.encode(section, Charsets.UTF_8.name())
        val encodedWord = URLEncoder.encode(normalizedWord, Charsets.UTF_8.name())
        val endpoint = "https://kartaslov.ru/$encodedSection/$encodedWord"

        val response = (URL(endpoint).openConnection() as HttpURLConnection).run {
            requestMethod = "GET"
            connectTimeout = 15000
            readTimeout = 15000
            setRequestProperty(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36"
            )
            setRequestProperty("Origin", "https://kartaslov.ru")
            setRequestProperty("Referrer", "https://kartaslov.ru/")
            val body = inputStream.bufferedReader().use { it.readText() }
            disconnect()
            body
        }

        val regex = Regex(
            "<a[^>]*class=\"[^\"]*wordLink[^\"]*\"[^>]*>(.*?)</a>",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )
        val list = regex.findAll(response)
            .map { match ->
                match.groupValues[1]
                    .replace(Regex("<[^>]+>"), "")
                    .replace("&nbsp;", " ")
                    .replace("&amp;", "&")
                    .replace("&quot;", "\"")
                    .replace("&#39;", "'")
                    .trim()
                    .lowercase(Locale.ROOT)
            }
            .filter { it.isNotBlank() }
            .toList()

        synchronized(kartaslovCache) {
            kartaslovCache[key] = list
        }
        return list
    }

    private fun loadPhraseEntries(
        assetPath: String,
        splitByCarriageReturn: Boolean,
        removePunctuation: Boolean,
        toLowerCase: Boolean
    ): List<PhraseEntry> {
        val raw = appContext.assets.open(assetPath).bufferedReader().use { it.readText() }
        val rows = if (splitByCarriageReturn) {
            raw.split('\r')
        } else {
            raw.split(Regex("\\r?\\n"))
        }
        return rows.mapNotNull { row ->
            var prepared = row.trim()
            if (removePunctuation) {
                prepared = prepared
                    .replace(",", "")
                    .replace("!", "")
                    .replace("?", "")
            }
            if (toLowerCase) {
                prepared = prepared.lowercase(Locale.ROOT)
            }
            val original = prepared.trim()
            if (original.isBlank()) return@mapNotNull null
            PhraseEntry(
                original = original,
                searchText = normalize(original)
            )
        }
    }

    private fun loadPhraseJsonEntries(assetPath: String): List<PhraseEntry> {
        val json = appContext.assets.open(assetPath).bufferedReader().use { it.readText() }
        val array = JSONArray(json)
        val result = mutableListOf<PhraseEntry>()
        for (i in 0 until array.length()) {
            var value = array.optString(i).trim()
            if (value.isBlank()) continue
            value = value
                .replace(",", "")
                .replace("!", "")
                .replace("?", "")
            if (value.isBlank()) continue
            result.add(
                PhraseEntry(
                    original = value,
                    searchText = normalize(value)
                )
            )
        }
        return result
    }

    private fun loadMendeleev(assetPath: String): List<MendeleevElement> {
        val json = appContext.assets.open(assetPath).bufferedReader().use { it.readText() }
        val array = JSONArray(json)
        val result = mutableListOf<MendeleevElement>()
        for (i in 0 until array.length()) {
            val element = array.optJSONObject(i) ?: continue
            result.add(
                MendeleevElement(
                    number = element.optInt("number"),
                    symbol = element.optString("symbol"),
                    name = element.optString("name"),
                    nameRu = element.optString("nameRu")
                )
            )
        }
        return result
    }

    private fun isPhraseMatchWordsCount(phrase: String, isWordsCount: Boolean, number: Int?): Boolean {
        if (!isWordsCount || number == null) return true
        val spacesCount = phrase.count { it == ' ' }
        return spacesCount + 1 == number
    }

    private fun normalize(value: String): String {
        return value.trim()
            .lowercase(Locale.ROOT)
            .replace('ё', 'е')
    }

    private fun normalizeForCatalogSearch(value: String): String {
        return normalize(value)
            .replace(",", "")
            .replace("!", "")
            .replace(":", "")
            .replace(".", "")
            .replace(" ", "")
    }

    private fun normalizeForRaschObject(value: String): String {
        return value
            .trim()
            .lowercase(Locale.ROOT)
            .replace(Regex("[ !,\\-]"), "")
            .replace('ё', 'e')
    }

    private data class SolverData(
        val wordsRu: List<String>,
        val wordsEn: List<String>,
        val associationsRu: Map<String, List<String>>,
        val associationsEn: Map<String, List<String>>,
        val books: List<CatalogEntry>,
        val films: List<CatalogEntry>,
        val paintings: List<CatalogEntry>,
        val phrases: List<PhraseEntry>,
        val pogovorki: List<PhraseEntry>,
        val wikislovar: List<PhraseEntry>,
        val wikislovarPairs: List<PhraseEntry>,
        val dslov: List<PhraseEntry>,
        val mendeleev: List<MendeleevElement>
    )

    private class PackedWordList(
        private val data: ByteArray,
        private val offsets: IntArray
    ) : AbstractList<String>() {
        override val size: Int
            get() = offsets.size - 1

        override fun get(index: Int): String {
            val start = offsets[index]
            val endExclusive = offsets[index + 1]
            val payloadEndExclusive = if (
                endExclusive > start && data[endExclusive - 1] == '\n'.code.toByte()
            ) {
                endExclusive - 1
            } else {
                endExclusive
            }
            return data.decodeToString(startIndex = start, endIndex = payloadEndExclusive)
        }
    }

    private class IntAccumulator(initialCapacity: Int = 1024) {
        private var values = IntArray(initialCapacity)
        private var size = 0

        fun add(value: Int) {
            if (size == values.size) {
                values = values.copyOf(values.size * 2)
            }
            values[size] = value
            size++
        }

        fun toIntArray(): IntArray = values.copyOf(size)
    }

    private data class CatalogEntry(
        val title: String,
        val searchText: String
    )

    private data class PhraseEntry(
        val original: String,
        val searchText: String
    )

    data class PhraseSearchResult(
        val directPhrases: List<String>,
        val allPhrases: List<String>,
        val wikiPhrases: List<String>,
        val wikiPairs: List<String>,
        val pogovorkiPhrases: List<String>,
        val dslovPhrases: List<String>
    ) {
        companion object {
            fun empty() = PhraseSearchResult(
                directPhrases = emptyList(),
                allPhrases = emptyList(),
                wikiPhrases = emptyList(),
                wikiPairs = emptyList(),
                pogovorkiPhrases = emptyList(),
                dslovPhrases = emptyList()
            )
        }
    }

    data class MendeleevElement(
        val number: Int,
        val symbol: String,
        val name: String,
        val nameRu: String
    )
}
