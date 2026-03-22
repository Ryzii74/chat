package com.example.gamechat.ui

import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.widget.PopupMenu
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.gamechat.MainActivity
import com.example.gamechat.R
import com.example.gamechat.data.UserPreferences
import com.example.gamechat.ui.EngineFragment
import com.example.gamechat.ui.chat.ChatMessage
import com.example.gamechat.ui.chat.ChatMessageAdapter
import com.example.gamechat.ui.chat.DeliveryState
import com.example.gamechat.ui.solver.SolverDataRepository
import com.example.gamechat.ui.solver.SolverEngine
import com.example.gamechat.ui.solver.SolverMode
import com.example.gamechat.ui.solver.SolverModes
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class SolverFragment : Fragment(R.layout.fragment_solver) {
    companion object {
        private const val RESULT_PAGE_SIZE = 50
        private const val LOAD_MORE_LABEL = "Показать еще 50"
        private const val LOAD_MORE_ID_PREFIX = "solver-loadmore-"
        private const val NEXT_PAGE_DIVIDER = "[──────── Следующие 50 ────────]"
    }

    private data class SolverReply(
        val sender: String,
        val text: String
    )

    private val messages = mutableListOf<ChatMessage>()
    private val pendingLinesByMessageId = mutableMapOf<String, MutableList<String>>()
    private lateinit var adapter: ChatMessageAdapter
    private var selectedMode: SolverMode = SolverModes.default()
    private var autoModeEnabled: Boolean = false
    private val solverEngine by lazy { SolverEngine(SolverDataRepository(requireContext())) }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadSavedHistory()
        setupMessages(view)
        setupComposer(view)
    }

    override fun onResume() {
        super.onResume()
        autoModeEnabled = UserPreferences.isSolverAutoEnabled(requireContext())
    }

    override fun onPause() {
        super.onPause()
        saveSolverHistory()
    }

    private fun setupMessages(root: View) {
        val recycler = root.findViewById<RecyclerView>(R.id.solverMessagesRecycler)
        adapter = ChatMessageAdapter(messages, { }, { message, answer ->
            if (answer == LOAD_MORE_LABEL && message.id?.startsWith(LOAD_MORE_ID_PREFIX) == true) {
                appendNextResultPage(message.id)
            } else {
                navigateToEngine(answer)
            }
        })
        recycler.layoutManager = LinearLayoutManager(requireContext())
        recycler.adapter = adapter
        
        // Если есть сообщения, прокручиваем вниз
        if (messages.isNotEmpty()) {
            recycler.scrollToPosition(messages.lastIndex)
        }
    }

    private fun setupComposer(root: View) {
        val input = root.findViewById<EditText>(R.id.solverMessageInput)
        val sendButton = root.findViewById<ImageButton>(R.id.solverSendButton)
        val modeButton = root.findViewById<Button>(R.id.solverModeButton)
        val loadMoreButton = root.findViewById<Button>(R.id.solverLoadMoreButton)
        selectedMode = SolverModes.byAlias(UserPreferences.getSolverModeAlias(requireContext()))
            ?.takeUnless { it.alias == "morze" || it.alias == "dick" }
            ?: SolverModes.default()
        autoModeEnabled = UserPreferences.isSolverAutoEnabled(requireContext())

        fun renderModeButton() {
            modeButton.text = selectedMode.title.take(6)
        }

        fun switchMode(mode: SolverMode) {
            if (mode == selectedMode) return
            selectedMode = mode
            UserPreferences.setSolverModeAlias(requireContext(), mode.alias)
            renderModeButton()
        }

        fun sendCurrentMessage() {
            val text = input.text?.toString().orEmpty().trim()
            if (text.isBlank()) return
            val modeCommand = parseModeCommand(text)
            if (modeCommand != null) {
                switchMode(modeCommand)
                input.text?.clear()
                hideKeyboard(input)
                return
            }
            messages.add(
                ChatMessage(
                    senderName = "Вы",
                    text = text,
                    isOutgoing = true,
                    deliveryState = DeliveryState.SENT,
                    timeLabel = formatNowTime()
                )
            )
            adapter.notifyItemInserted(messages.lastIndex)
            scrollToBottom(root)
            input.text?.clear()
            hideKeyboard(input)
            pendingLinesByMessageId.clear()
            updateLoadMoreButton(loadMoreButton)
            // Сохраняем историю после добавления пользовательского сообщения
            saveSolverHistory()
            Thread {
                val autoEnabledNow = UserPreferences.isSolverAutoEnabled(requireContext())
                autoModeEnabled = autoEnabledNow
                val runtimeModes = detectModesForMessage(text) ?: listOf(selectedMode)
                val replies = runtimeModes.flatMap { mode ->
                    buildRepliesForMode(mode, text)
                }
                activity?.runOnUiThread {
                    showRepliesWithPagination(replies, loadMoreButton)
                    scrollToBottom(root)
                }
            }.start()
        }

        modeButton.setOnClickListener { anchor ->
            val menu = PopupMenu(requireContext(), anchor)
            menu.menuInflater.inflate(R.menu.solver_mode_menu, menu.menu)
            menu.setOnMenuItemClickListener { item ->
                val mode = when (item.itemId) {
                    R.id.solverMode1 -> SolverModes.byId(1)
                    R.id.solverMode8 -> SolverModes.byId(8)
                    R.id.solverMode2 -> SolverModes.byId(2)
                    R.id.solverMode3 -> SolverModes.byId(3)
                    R.id.solverMode9 -> SolverModes.byId(9)
                    R.id.solverMode11 -> SolverModes.byId(11)
                    R.id.solverMode12 -> SolverModes.byId(12)
                    R.id.solverMode13 -> SolverModes.byId(13)
                    R.id.solverMode14 -> SolverModes.byId(14)
                    R.id.solverMode16 -> SolverModes.byId(16)
                    R.id.solverMode22 -> SolverModes.byId(22)
                    R.id.solverMode23 -> SolverModes.byId(23)
                    R.id.solverMode4 -> SolverModes.byId(4)
                    R.id.solverMode5 -> SolverModes.byId(5)
                    R.id.solverMode7 -> SolverModes.byId(7)
                    else -> null
                }
                if (mode != null) {
                    switchMode(mode)
                    true
                } else {
                    false
                }
            }
            menu.show()
        }

        sendButton.setOnClickListener {
            sendCurrentMessage()
        }

        input.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_ENTER) {
                sendCurrentMessage()
                true
            } else {
                false
            }
        }

        loadMoreButton.setOnClickListener {
            appendNextResultPage(loadMoreButton)
        }

        renderModeButton()
    }

    private fun detectModesForMessage(text: String): List<SolverMode>? {
        val autoEnabledNow = UserPreferences.isSolverAutoEnabled(requireContext())
        if (!autoEnabledNow) return null
        val selectedAlias = selectedMode.alias
        if (selectedAlias == "roman" || selectedAlias == "ss") return null

        val normalized = text.trim().lowercase(Locale.ROOT)
        if (normalized.isBlank()) return null
        val symbols = normalized.toList()
        val specialSymbols = setOf('?', '*', ' ')

        fun availableModes(vararg aliases: String): List<SolverMode>? {
            val modes = aliases.mapNotNull { alias -> SolverModes.byAlias(alias) }
            return if (modes.isEmpty()) null else modes
        }

        if (symbols.all { it == '.' || it == '-' || specialSymbols.contains(it) }) {
            return availableModes("morze")
        }

        if (symbols.all { it == '1' || it == '0' || specialSymbols.contains(it) }) {
            return availableModes("bacon", "bodo", "binary", "brail", "ss")
        }

        if (symbols.all { it.isDigit() || specialSymbols.contains(it) }) {
            return availableModes("alphabet", "tm", "regions", "ss")
        }

        if (isRaschlenenka(normalized)) {
            return availableModes("dick")
        }

        if (isRomanNumerals(normalized)) {
            return availableModes("roman")
        }

        if (isNotes(normalized)) {
            return availableModes("notes")
        }

        if (isTM(normalized)) {
            return availableModes("tm")
        }

        return null
    }

    private fun isRaschlenenka(text: String): Boolean {
        return Regex("^([а-яА-Яa-zA-Z]+\\d+\\s?)+$").matches(text)
    }

    private fun isRomanNumerals(text: String): Boolean {
        val roman = setOf('i', 'v', 'x', 'l', 'c', 'd', 'm')
        return text.isNotBlank() && text.all { roman.contains(it) }
    }

    private fun isNotes(text: String): Boolean {
        val notes = setOf("до", "ре", "ми", "фа", "соль", "ля", "си")
        return text.split(" ").filter { it.isNotBlank() }.all { notes.contains(it) }
    }

    private fun isTM(text: String): Boolean {
        val symbols = text.split(" ").filter { it.isNotBlank() }
        if (symbols.isEmpty()) return false
        return symbols.all { tmSymbols.contains(it.lowercase(Locale.ROOT)) }
    }

    private val tmSymbols = setOf(
        "h", "he", "li", "be", "b", "c", "n", "o", "f", "ne",
        "na", "mg", "al", "si", "p", "s", "cl", "ar", "k", "ca",
        "sc", "ti", "v", "cr", "mn", "fe", "co", "ni", "cu", "zn",
        "ga", "ge", "as", "se", "br", "kr", "rb", "sr", "y", "zr",
        "nb", "mo", "tc", "ru", "rh", "pd", "ag", "cd", "in", "sn",
        "sb", "te", "i", "xe", "cs", "ba", "la", "ce", "pr", "nd",
        "pm", "sm", "eu", "gd", "tb", "dy", "ho", "er", "tm", "yb",
        "lu", "hf", "ta", "w", "re", "os", "ir", "pt", "au", "hg",
        "tl", "pb", "bi", "po", "at", "rn", "fr", "ra", "ac", "th",
        "pa", "u", "np", "pu", "am", "cm", "bk", "cf", "es", "fm",
        "md", "no", "lr", "rf", "db", "sg", "bh", "hs", "mt", "ds",
        "rg", "cn", "nh", "fl", "mc", "lv", "ts", "og"
    )

    private fun parseModeCommand(input: String): SolverMode? {
        val trimmed = input.trim().lowercase(Locale.ROOT)
        if (!trimmed.startsWith("/")) return null
        val command = trimmed.removePrefix("/")
        if (command.isBlank()) return null
        val byAlias = SolverModes.byAlias(command)
        if (byAlias != null) return byAlias
        val byId = command.toIntOrNull()?.let { SolverModes.byId(it) }
        if (byId != null) return byId
        val parts = command.split("\\s+".toRegex())
        if (parts.isNotEmpty() && parts[0] == "mode" && parts.size >= 2) {
            val arg = parts[1]
            val mode = SolverModes.byAlias(arg) ?: arg.toIntOrNull()?.let { SolverModes.byId(it) }
            if (mode != null) return mode
        }
        Toast.makeText(requireContext(), getString(R.string.solver_mode_command_unknown), Toast.LENGTH_SHORT).show()
        return null
    }

    private fun addSystemMessage(sender: String, text: String, id: String? = null) {
        val formattedText = makeAnswersClickable(text)
        messages.add(
            ChatMessage(
                id = id,
                senderName = sender,
                text = formattedText,
                isOutgoing = false,
                deliveryState = DeliveryState.NONE,
                timeLabel = formatNowTime()
            )
        )
        adapter.notifyItemInserted(messages.lastIndex)
        // Автоматически сохраняем историю после каждого нового сообщения
        saveSolverHistory()
    }

    private fun showRepliesWithPagination(replies: List<SolverReply>, loadMoreButton: Button) {
        replies.forEach { reply ->
            addPaginatedReply(reply)
        }
        updateLoadMoreButton(loadMoreButton)
    }

    private fun addPaginatedReply(reply: SolverReply) {
        val lines = reply.text.split('\n')
            .map { it.trim() }
            .filter { it.isNotBlank() }

        if (lines.isEmpty()) {
            addSystemMessage(reply.sender, "ничего не найдено")
            return
        }

        if (lines.size <= RESULT_PAGE_SIZE || (lines.size == 1 && lines[0] == "ничего не найдено")) {
            addSystemMessage(reply.sender, lines.joinToString("\n"))
            return
        }

        val id = LOAD_MORE_ID_PREFIX + UUID.randomUUID().toString()
        val firstChunk = lines.take(RESULT_PAGE_SIZE)
        val rest = lines.drop(RESULT_PAGE_SIZE).toMutableList()
        pendingLinesByMessageId[id] = rest
        addSystemMessage(
            sender = reply.sender,
            text = firstChunk.joinToString("\n"),
            id = id
        )
    }

    private fun appendNextResultPage(messageId: String?) {
        if (messageId.isNullOrBlank()) return
        val pending = pendingLinesByMessageId[messageId] ?: return
        if (pending.isEmpty()) {
            pendingLinesByMessageId.remove(messageId)
            return
        }
        val next = pending.take(RESULT_PAGE_SIZE)
        repeat(next.size) { pending.removeAt(0) }
        if (pending.isEmpty()) {
            pendingLinesByMessageId.remove(messageId)
        }

        val index = messages.indexOfLast { it.id == messageId }
        if (index < 0) return
        val sender = messages[index].senderName.orEmpty()
        val pageText = "$NEXT_PAGE_DIVIDER\n${next.joinToString("\n")}"
        addSystemMessage(
            sender = sender,
            text = pageText,
            id = messageId
        )
        view?.findViewById<Button>(R.id.solverLoadMoreButton)?.let(::updateLoadMoreButton)
        view?.let { scrollToMessageTop(it, messages.lastIndex) }
    }

    private fun stripClickableMarkers(text: String): String {
        return text
            .replace("[CLICKABLE]", "")
            .replace("[/CLICKABLE]", "")
            .trim()
    }

    private fun appendNextResultPage(loadMoreButton: Button) {
        val messageId = findLatestPendingMessageId() ?: run {
            updateLoadMoreButton(loadMoreButton)
            return
        }
        appendNextResultPage(messageId)
        updateLoadMoreButton(loadMoreButton)
    }

    private fun buildRepliesForMode(mode: SolverMode, inputText: String): List<SolverReply> {
        if (mode.alias == "any") {
            return solverEngine.resolveAnyBreakdown(inputText).map { methodResult ->
                val text = if (methodResult.answers.isEmpty()) {
                    "ничего не найдено"
                } else {
                    methodResult.answers.joinToString("\n")
                }
                SolverReply(sender = methodResult.title, text = text)
            }
        }
        if (mode.alias == "brukva") {
            return solverEngine.resolveBrukvaBreakdown(inputText).map { result ->
                val text = if (result.answers.isEmpty()) {
                    "ничего не найдено"
                } else {
                    result.answers.joinToString("\n")
                }
                SolverReply(sender = result.title, text = text)
            }
        }
        if (mode.alias == "plus") {
            return solverEngine.resolvePlusBreakdown(inputText).map { result ->
                val text = if (result.answers.isEmpty()) {
                    "ничего не найдено"
                } else {
                    result.answers.joinToString("\n")
                }
                SolverReply(sender = result.title, text = text)
            }
        }
        val raw = solverEngine.resolve(mode, inputText)
        return listOf(buildReplyForMode(mode, raw))
    }

    private fun buildReplyForMode(mode: SolverMode, rawResult: String): SolverReply {
        val answers = extractAnswers(rawResult)
        val text = if (answers.isEmpty()) {
            "ничего не найдено"
        } else {
            answers.joinToString("\n")
        }
        return SolverReply(sender = mode.title, text = text)
    }

    private fun updateLoadMoreButton(button: Button) {
        val hasPending = findLatestPendingMessageId() != null
        button.visibility = if (hasPending) View.VISIBLE else View.GONE
    }

    private fun findLatestPendingMessageId(): String? {
        for (index in messages.indices.reversed()) {
            val id = messages[index].id ?: continue
            if (pendingLinesByMessageId[id]?.isNotEmpty() == true) {
                return id
            }
        }
        return null
    }

    private fun scrollToBottom(root: View) {
        root.findViewById<RecyclerView>(R.id.solverMessagesRecycler).scrollToPosition(messages.lastIndex)
    }

    private fun scrollToMessageTop(root: View, index: Int) {
        val recycler = root.findViewById<RecyclerView>(R.id.solverMessagesRecycler)
        val layoutManager = recycler.layoutManager as? LinearLayoutManager
        if (layoutManager != null) {
            recycler.post {
                layoutManager.scrollToPositionWithOffset(index, 0)
            }
        } else {
            recycler.scrollToPosition(index)
        }
    }

    private fun formatNowTime(): String {
        return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
    }

    private fun hideKeyboard(anchor: View) {
        val imm = context?.getSystemService(InputMethodManager::class.java) ?: return
        imm.hideSoftInputFromWindow(anchor.windowToken, 0)
    }

    private fun makeAnswersClickable(text: String): String {
        // Добавляем специальные маркеры для кликабельных строк
        val lines = text.split("\n")
        val clickableLines = mutableListOf<String>()
        
        for (line in lines) {
            val trimmedLine = line.trim()
            if (trimmedLine.isNotEmpty() && 
                !trimmedLine.startsWith("[") && 
                !trimmedLine.endsWith("]") &&
                !trimmedLine.contains(":") &&
                !isNonClickableStatusLine(trimmedLine)) {
                // Это потенциальный ответ - делаем его кликабельным
                clickableLines.add("[CLICKABLE]$trimmedLine[/CLICKABLE]")
            } else {
                clickableLines.add(line)
            }
        }
        return clickableLines.joinToString("\n")
    }

    private fun extractAnswers(rawText: String): List<String> {
        val noResultsRegex = Regex(
            "^(ничего не найдено|нет результатов|по маске ничего не найдено|ассоциации не найдены|общих ассоциаций не найдено|книги не найдены|фильмы не найдены|картины не найдены)\\.?$",
            RegexOption.IGNORE_CASE
        )
        val foundLine = Regex("^Найдено\\s*(\\(\\d+\\))?\\s*$", RegexOption.IGNORE_CASE)
        val foundPrefix = Regex("^Найдено\\s*(\\(\\d+\\))?\\s*:\\s*(.+)$", RegexOption.IGNORE_CASE)
        val sectionHeader = Regex("^[\\[\\(]?[А-ЯA-Z\\s\\-()]+[\\]\\)]?$")

        val values = rawText
            .split('\n')
            .mapNotNull { line ->
                val trimmed = line.trim()
                if (trimmed.isBlank()) return@mapNotNull null
                if (foundLine.matches(trimmed)) return@mapNotNull null
                if (noResultsRegex.matches(trimmed.lowercase(Locale.ROOT))) return@mapNotNull null
                if (trimmed.startsWith("[") && trimmed.endsWith("]")) return@mapNotNull null
                if (sectionHeader.matches(trimmed) && !trimmed.contains(":")) return@mapNotNull null
                if (trimmed.startsWith("*") && trimmed.endsWith("*")) return@mapNotNull null

                val prefixMatch = foundPrefix.matchEntire(trimmed)
                val prepared = if (prefixMatch != null) {
                    prefixMatch.groupValues[2].trim()
                } else if (trimmed.contains(":")) {
                    trimmed.substringAfter(":").trim()
                } else {
                    trimmed
                }

                prepared
                    .replace("`", "")
                    .split(",")
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
            }
            .flatten()

        return values.distinct()
    }

    private fun isNonClickableStatusLine(line: String): Boolean {
        val normalized = line
            .lowercase(Locale.ROOT)
            .replace(".", "")
            .replace("!", "")
            .replace("?", "")
            .trim()
        return normalized == "ничего не найдено"
    }

    private fun navigateToEngine(answer: String) {
        val activity = requireActivity() as? MainActivity
        if (activity != null) {
            // Переключаемся на экран движка с передачей ответа
            val engineFragment = EngineFragment.newInstanceWithPendingCode(answer)
            activity.openScreen(engineFragment, getString(R.string.menu_engine), R.id.engineFragment)
        }
    }

    private fun loadSavedHistory() {
        val savedMessages = UserPreferences.getSolverHistory(requireContext())
        messages.clear()
        messages.addAll(savedMessages)
    }

    private fun saveSolverHistory() {
        UserPreferences.saveSolverHistory(requireContext(), messages)
    }
}
