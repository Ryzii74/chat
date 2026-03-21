package com.example.gamechat.ui.solver

import android.content.Context
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL
import kotlin.collections.AbstractList
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

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
        @Volatile
        private var cachedBooks: PackedCatalog? = null
        @Volatile
        private var cachedFilms: PackedCatalog? = null
        @Volatile
        private var cachedPaintings: PackedCatalog? = null
        @Volatile
        private var cachedMendeleev: List<MendeleevElement>? = null
        @Volatile
        private var cachedPhrases: PackedPhraseList? = null
        @Volatile
        private var cachedPogovorki: PackedPhraseList? = null
        @Volatile
        private var cachedWikislovar: PackedPhraseList? = null
        @Volatile
        private var cachedWikislovarPairs: PackedPhraseList? = null
        @Volatile
        private var cachedAssociationsRu: Map<String, List<String>>? = null
        @Volatile
        private var cachedAssociationsEn: Map<String, List<String>>? = null
        private val cacheLock = Any()
        private val kartaslovCache = mutableMapOf<String, List<String>>()
        private val associationRemoteCache = ConcurrentHashMap<String, List<String>>()
        private val associationHttpClient: OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .writeTimeout(8, TimeUnit.SECONDS)
            .build()
    }

    fun preloadWordDictionaries() {
        ensureRuWords()
        ensureEnWords()
    }

    fun preloadCatalogDictionaries() {
        ensureBooks()
        ensureFilms()
        ensurePaintings()
    }

    fun preloadMendeleevDictionary() {
        ensureMendeleev()
    }

    fun preloadPhraseDictionaries() {
        ensurePhrases()
        ensurePogovorki()
        ensureWikislovar()
        ensureWikislovarPairs()
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
        val normalized = normalize(word)
        if (normalized.isBlank()) return emptyList()
        fetchAssociationsRemote(normalized)?.let { remote ->
            if (remote.isNotEmpty()) return remote
        }
        return ensureAssociationsRu()[normalized]
            ?: ensureAssociationsEn()[normalized]
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
        return ensureBooks().search(normalizeForCatalogSearch(query))
    }

    fun searchFilms(query: String): List<String> {
        return ensureFilms().search(normalizeForCatalogSearch(query))
    }

    fun searchPaintings(query: String): List<String> {
        return ensurePaintings().search(normalizeForCatalogSearch(query))
    }

    fun getBookTitles(): List<String> = ensureBooks().titles()

    fun getFilmTitles(): List<String> = ensureFilms().titles()

    fun getPaintingTitles(): List<String> = ensurePaintings().titles()

    fun getMendeleevElements(): List<MendeleevElement> = ensureMendeleev()

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

        val directPhrases = data.phrases.filterOriginals { searchText, original ->
            isPhraseMatchWordsCount(searchText, isWordsCount, wordsCount) &&
                words.all { word -> containsWholeWord(searchText, word) }
        }

        val directSet = directPhrases.toSet()
        val allPhrases = data.phrases.filterOriginals { searchText, original ->
            isPhraseMatchWordsCount(searchText, isWordsCount, wordsCount) &&
                !directSet.contains(original) &&
                words.all { word -> searchText.contains(word) }
        }

        val wikiPhrases = data.wikislovar.filterOriginals { searchText, _ ->
            isPhraseMatchWordsCount(searchText, isWordsCount, wordsCount) &&
                words.all { word -> searchText.contains(word) }
        }

        val wikiPairs = data.wikislovarPairs.filterOriginals { searchText, _ ->
            isPhraseMatchWordsCount(searchText, isWordsCount, wordsCount) &&
                words.all { word -> searchText.contains(word) }
        }

        val pogovorkiPhrases = data.pogovorki.filterOriginals { searchText, _ ->
            isPhraseMatchWordsCount(searchText, isWordsCount, wordsCount) &&
                words.all { word -> searchText.contains(word) }
        }

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
                phrases = ensurePhrases(),
                pogovorki = ensurePogovorki(),
                wikislovar = ensureWikislovar(),
                wikislovarPairs = ensureWikislovarPairs(),
                dslov = loadPhraseJsonEntries("solver/dslov.json"),
                mendeleev = ensureMendeleev()
            )
            cachedData = data
            return data
        }
    }

    private fun ensureAssociationsRu(): Map<String, List<String>> {
        cachedAssociationsRu?.let { return it }
        synchronized(cacheLock) {
            cachedAssociationsRu?.let { return it }
            val values = loadAssociations("solver/associations_ru.json")
            cachedAssociationsRu = values
            return values
        }
    }

    private fun ensureAssociationsEn(): Map<String, List<String>> {
        cachedAssociationsEn?.let { return it }
        synchronized(cacheLock) {
            cachedAssociationsEn?.let { return it }
            val values = loadAssociations("solver/associations_en.json")
            cachedAssociationsEn = values
            return values
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

    private fun ensureBooks(): PackedCatalog {
        cachedBooks?.let { return it }
        synchronized(cacheLock) {
            cachedBooks?.let { return it }
            val books = loadPackedCatalog("solver/books.txt")
            cachedBooks = books
            return books
        }
    }

    private fun ensureFilms(): PackedCatalog {
        cachedFilms?.let { return it }
        synchronized(cacheLock) {
            cachedFilms?.let { return it }
            val films = loadPackedCatalog("solver/films.txt")
            cachedFilms = films
            return films
        }
    }

    private fun ensurePaintings(): PackedCatalog {
        cachedPaintings?.let { return it }
        synchronized(cacheLock) {
            cachedPaintings?.let { return it }
            val paintings = loadPackedCatalog("solver/paintings.txt")
            cachedPaintings = paintings
            return paintings
        }
    }

    private fun ensureMendeleev(): List<MendeleevElement> {
        cachedMendeleev?.let { return it }
        synchronized(cacheLock) {
            cachedMendeleev?.let { return it }
            val elements = loadMendeleev("solver/mendeleev.json")
            cachedMendeleev = elements
            return elements
        }
    }

    private fun ensurePhrases(): PackedPhraseList {
        cachedPhrases?.let { return it }
        synchronized(cacheLock) {
            cachedPhrases?.let { return it }
            val phrases = loadPackedPhraseEntries(
                assetPath = "solver/phrases.txt",
                splitByCarriageReturn = true,
                removePunctuation = false,
                toLowerCase = false
            )
            cachedPhrases = phrases
            return phrases
        }
    }

    private fun ensurePogovorki(): PackedPhraseList {
        cachedPogovorki?.let { return it }
        synchronized(cacheLock) {
            cachedPogovorki?.let { return it }
            val phrases = loadPackedPhraseEntries(
                assetPath = "solver/pogovorki.txt",
                splitByCarriageReturn = false,
                removePunctuation = true,
                toLowerCase = true
            )
            cachedPogovorki = phrases
            return phrases
        }
    }

    private fun ensureWikislovar(): PackedPhraseList {
        cachedWikislovar?.let { return it }
        synchronized(cacheLock) {
            cachedWikislovar?.let { return it }
            val phrases = loadPackedPhraseJsonEntries("solver/wikislovar.json")
            cachedWikislovar = phrases
            return phrases
        }
    }

    private fun ensureWikislovarPairs(): PackedPhraseList {
        cachedWikislovarPairs?.let { return it }
        synchronized(cacheLock) {
            cachedWikislovarPairs?.let { return it }
            val phrases = loadPackedPhraseJsonEntries("solver/wikislovarPairs.json")
            cachedWikislovarPairs = phrases
            return phrases
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
            data.phrases.forEachOriginal { original -> set.add(normalizeForRaschObject(original)) }
            data.pogovorki.forEachOriginal { original -> set.add(normalizeForRaschObject(original)) }
            data.wikislovar.forEachOriginal { original -> set.add(normalizeForRaschObject(original)) }
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

    private fun fetchAssociationsRemote(word: String): List<String>? {
        associationRemoteCache[word]?.let { return it }
        val requestBody = FormBody.Builder()
            .add("word", word)
            .add("max_count", "100")
            .build()
        val request = Request.Builder()
            .url("https://sociation.org/ajax/word_associations/")
            .post(requestBody)
            .header(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36"
            )
            .header("Origin", "https://sociation.org")
            .header("Referer", "https://sociation.org/")
            .header("X-Requested-With", "XMLHttpRequest")
            .build()
        return try {
            associationHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body?.string().orEmpty()
                if (body.isBlank()) return null
                val root = JSONObject(body)
                val error = root.optJSONObject("error")
                if (error != null) return null
                val raw = root.optJSONArray("associations") ?: return null
                val values = mutableListOf<String>()
                for (index in 0 until raw.length()) {
                    val row = raw.optJSONObject(index) ?: continue
                    val name = normalize(row.optString("name"))
                    if (name.isNotBlank()) values.add(name)
                }
                val deduped = values.distinct()
                if (deduped.isNotEmpty()) {
                    associationRemoteCache[word] = deduped
                }
                deduped
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun loadPackedCatalog(assetPath: String): PackedCatalog {
        val titlesOutput = ByteArrayOutputStream()
        val titlesOffsets = IntAccumulator()
        val searchOutput = ByteArrayOutputStream()
        val searchOffsets = IntAccumulator()
        titlesOffsets.add(0)
        searchOffsets.add(0)

        appContext.assets.open(assetPath).bufferedReader().useLines { sequence ->
            sequence.forEach { raw ->
                val title = raw.trim()
                if (title.isBlank()) return@forEach
                val searchText = normalizeForCatalogSearch(title)

                val titleBytes = title.toByteArray(Charsets.UTF_8)
                titlesOutput.write(titleBytes)
                titlesOutput.write('\n'.code)
                titlesOffsets.add(titlesOutput.size())

                val searchBytes = searchText.toByteArray(Charsets.UTF_8)
                searchOutput.write(searchBytes)
                searchOutput.write('\n'.code)
                searchOffsets.add(searchOutput.size())
            }
        }
        return PackedCatalog(
            titlesData = titlesOutput.toByteArray(),
            titlesOffsets = titlesOffsets.toIntArray(),
            searchData = searchOutput.toByteArray(),
            searchOffsets = searchOffsets.toIntArray()
        )
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

    private fun loadPackedPhraseEntries(
        assetPath: String,
        splitByCarriageReturn: Boolean,
        removePunctuation: Boolean,
        toLowerCase: Boolean
    ): PackedPhraseList {
        val raw = appContext.assets.open(assetPath).bufferedReader().use { it.readText() }
        val rows = if (splitByCarriageReturn) {
            raw.split('\r')
        } else {
            raw.split(Regex("\\r?\\n"))
        }
        val phraseRows = rows.mapNotNull { row ->
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
        return packPhraseEntries(phraseRows)
    }

    private fun loadPackedPhraseJsonEntries(assetPath: String): PackedPhraseList {
        return packPhraseEntries(loadPhraseJsonEntries(assetPath))
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

    private fun packPhraseEntries(entries: List<PhraseEntry>): PackedPhraseList {
        val originalsOutput = ByteArrayOutputStream()
        val originalsOffsets = IntAccumulator()
        val searchOutput = ByteArrayOutputStream()
        val searchOffsets = IntAccumulator()
        originalsOffsets.add(0)
        searchOffsets.add(0)

        entries.forEach { entry ->
            originalsOutput.write(entry.original.toByteArray(Charsets.UTF_8))
            originalsOutput.write('\n'.code)
            originalsOffsets.add(originalsOutput.size())

            searchOutput.write(entry.searchText.toByteArray(Charsets.UTF_8))
            searchOutput.write('\n'.code)
            searchOffsets.add(searchOutput.size())
        }

        return PackedPhraseList(
            originalsData = originalsOutput.toByteArray(),
            originalsOffsets = originalsOffsets.toIntArray(),
            searchData = searchOutput.toByteArray(),
            searchOffsets = searchOffsets.toIntArray()
        )
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

    private fun containsWholeWord(text: String, word: String): Boolean {
        if (word.isBlank()) return false
        var index = text.indexOf(word)
        while (index >= 0) {
            val startOk = index == 0 || text[index - 1] == ' '
            val endIndex = index + word.length
            val endOk = endIndex == text.length || text[endIndex] == ' '
            if (startOk && endOk) return true
            index = text.indexOf(word, index + 1)
        }
        return false
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
        val phrases: PackedPhraseList,
        val pogovorki: PackedPhraseList,
        val wikislovar: PackedPhraseList,
        val wikislovarPairs: PackedPhraseList,
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

    private class PackedCatalog(
        private val titlesData: ByteArray,
        private val titlesOffsets: IntArray,
        private val searchData: ByteArray,
        private val searchOffsets: IntArray
    ) {
        fun search(normalizedQuery: String): List<String> {
            if (normalizedQuery.isBlank()) return emptyList()
            val queryBytes = normalizedQuery.toByteArray(Charsets.UTF_8)
            val result = ArrayList<String>(60)

            for (index in 0 until size()) {
                if (containsBytes(searchData, searchOffsets[index], searchOffsets[index + 1], queryBytes)) {
                    result.add(readEntry(titlesData, titlesOffsets[index], titlesOffsets[index + 1]))
                    if (result.size == 60) break
                }
            }
            return result
        }

        fun titles(): List<String> {
            val result = ArrayList<String>(size())
            for (index in 0 until size()) {
                result.add(readEntry(titlesData, titlesOffsets[index], titlesOffsets[index + 1]))
            }
            return result
        }

        private fun size(): Int = titlesOffsets.size - 1

        private fun readEntry(data: ByteArray, start: Int, endExclusive: Int): String {
            val payloadEndExclusive = if (
                endExclusive > start && data[endExclusive - 1] == '\n'.code.toByte()
            ) {
                endExclusive - 1
            } else {
                endExclusive
            }
            return data.decodeToString(startIndex = start, endIndex = payloadEndExclusive)
        }

        private fun containsBytes(
            data: ByteArray,
            start: Int,
            endExclusive: Int,
            needle: ByteArray
        ): Boolean {
            if (needle.isEmpty()) return true
            val dataEndExclusive = if (
                endExclusive > start && data[endExclusive - 1] == '\n'.code.toByte()
            ) {
                endExclusive - 1
            } else {
                endExclusive
            }
            val haystackLength = dataEndExclusive - start
            if (haystackLength < needle.size) return false

            val maxStart = dataEndExclusive - needle.size
            var i = start
            while (i <= maxStart) {
                var j = 0
                while (j < needle.size && data[i + j] == needle[j]) {
                    j++
                }
                if (j == needle.size) return true
                i++
            }
            return false
        }
    }

    private class PackedPhraseList(
        private val originalsData: ByteArray,
        private val originalsOffsets: IntArray,
        private val searchData: ByteArray,
        private val searchOffsets: IntArray
    ) {
        fun filterOriginals(predicate: (searchText: String, original: String) -> Boolean): List<String> {
            val result = mutableListOf<String>()
            for (index in 0 until size()) {
                val searchText = readEntry(searchData, searchOffsets[index], searchOffsets[index + 1])
                val original = readEntry(originalsData, originalsOffsets[index], originalsOffsets[index + 1])
                if (predicate(searchText, original)) {
                    result.add(original)
                }
            }
            return result
        }

        fun forEachOriginal(action: (String) -> Unit) {
            for (index in 0 until size()) {
                action(readEntry(originalsData, originalsOffsets[index], originalsOffsets[index + 1]))
            }
        }

        private fun size(): Int = originalsOffsets.size - 1

        private fun readEntry(data: ByteArray, start: Int, endExclusive: Int): String {
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
