package com.example.gamechat.ui

import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.widget.PopupMenu
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.gamechat.R
import com.example.gamechat.data.UserPreferences
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

class SolverFragment : Fragment(R.layout.fragment_solver) {
    companion object {
        private const val RESULT_PAGE_SIZE = 50
    }

    private val messages = mutableListOf<ChatMessage>()
    private val pendingResultLines = mutableListOf<String>()
    private lateinit var adapter: ChatMessageAdapter
    private var selectedMode: SolverMode = SolverModes.default()
    private var autoModeEnabled: Boolean = false
    private val solverEngine by lazy { SolverEngine(SolverDataRepository(requireContext())) }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupMessages(view)
        setupComposer(view)
        addSystemMessage(getString(R.string.solver_welcome_message))
    }

    override fun onResume() {
        super.onResume()
        autoModeEnabled = UserPreferences.isSolverAutoEnabled(requireContext())
    }

    private fun setupMessages(root: View) {
        val recycler = root.findViewById<RecyclerView>(R.id.solverMessagesRecycler)
        adapter = ChatMessageAdapter(messages) { }
        recycler.layoutManager = LinearLayoutManager(requireContext())
        recycler.adapter = adapter
    }

    private fun setupComposer(root: View) {
        val input = root.findViewById<EditText>(R.id.solverMessageInput)
        val sendButton = root.findViewById<ImageButton>(R.id.solverSendButton)
        val modeButton = root.findViewById<Button>(R.id.solverModeButton)
        val loadMoreButton = root.findViewById<Button>(R.id.solverLoadMoreButton)
        selectedMode = SolverModes.byAlias(UserPreferences.getSolverModeAlias(requireContext()))
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
            pendingResultLines.clear()
            updateLoadMoreButton(loadMoreButton)
            Thread {
                val runtimeModes = detectModesForMessage(text) ?: listOf(selectedMode)
                val autoDetected = autoModeEnabled && runtimeModes != listOf(selectedMode)
                val result = if (runtimeModes.size == 1) {
                    solverEngine.resolve(runtimeModes.first(), text)
                } else {
                    runtimeModes.joinToString("\n\n") { mode ->
                        "[${mode.title}]\n${solverEngine.resolve(mode, text)}"
                    }
                }
                activity?.runOnUiThread {
                    val decoratedResult = if (autoDetected) {
                        if (runtimeModes.size == 1) {
                            "[AUTO: ${runtimeModes.first().title}]\n$result"
                        } else {
                            "[AUTO: несколько режимов]\n$result"
                        }
                    } else {
                        result
                    }
                    showResultWithPagination(decoratedResult, loadMoreButton)
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
                    R.id.solverMode2 -> SolverModes.byId(2)
                    R.id.solverMode3 -> SolverModes.byId(3)
                    R.id.solverMode11 -> SolverModes.byId(11)
                    R.id.solverMode12 -> SolverModes.byId(12)
                    R.id.solverMode13 -> SolverModes.byId(13)
                    R.id.solverMode14 -> SolverModes.byId(14)
                    R.id.solverMode16 -> SolverModes.byId(16)
                    R.id.solverMode22 -> SolverModes.byId(22)
                    R.id.solverMode23 -> SolverModes.byId(23)
                    R.id.solverMode28 -> SolverModes.byId(28)
                    R.id.solverMode36 -> SolverModes.byId(36)
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
            scrollToBottom(root)
        }

        renderModeButton()
    }

    private fun detectModesForMessage(text: String): List<SolverMode>? {
        if (!autoModeEnabled) return null
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

    private fun addSystemMessage(text: String) {
        messages.add(
            ChatMessage(
                senderName = "Решатель",
                text = text,
                isOutgoing = false,
                deliveryState = DeliveryState.NONE,
                timeLabel = formatNowTime()
            )
        )
        adapter.notifyItemInserted(messages.lastIndex)
    }

    private fun showResultWithPagination(rawResult: String, loadMoreButton: Button) {
        val lines = normalizeResultLines(rawResult)
        if (lines.isEmpty()) {
            addSystemMessage(rawResult)
            pendingResultLines.clear()
            updateLoadMoreButton(loadMoreButton)
            return
        }
        val firstPage = lines.take(RESULT_PAGE_SIZE)
        addSystemMessage(firstPage.joinToString("\n"))
        pendingResultLines.clear()
        pendingResultLines.addAll(lines.drop(RESULT_PAGE_SIZE))
        updateLoadMoreButton(loadMoreButton)
    }

    private fun appendNextResultPage(loadMoreButton: Button) {
        if (pendingResultLines.isEmpty()) {
            updateLoadMoreButton(loadMoreButton)
            return
        }
        val next = pendingResultLines.take(RESULT_PAGE_SIZE)
        addSystemMessage(next.joinToString("\n"))
        repeat(next.size) { pendingResultLines.removeAt(0) }
        updateLoadMoreButton(loadMoreButton)
    }

    private fun normalizeResultLines(rawResult: String): List<String> {
        val preparedLines = rawResult.split('\n')
            .map { it.trim() }
            .filter { it.isNotBlank() }
        if (preparedLines.isEmpty()) return emptyList()

        return preparedLines.flatMap { line ->
            val index = line.indexOf(": ")
            if (index > 0) {
                val tail = line.substring(index + 2)
                if (tail.contains(", ")) {
                    val header = line.substring(0, index + 1)
                    val values = tail.split(", ")
                        .map { it.trim() }
                        .filter { it.isNotBlank() }
                    if (values.isNotEmpty()) {
                        return@flatMap listOf(header) + values
                    }
                }
            }
            listOf(line)
        }
    }

    private fun updateLoadMoreButton(button: Button) {
        button.visibility = if (pendingResultLines.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun scrollToBottom(root: View) {
        root.findViewById<RecyclerView>(R.id.solverMessagesRecycler).scrollToPosition(messages.lastIndex)
    }

    private fun formatNowTime(): String {
        return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
    }
}
