package com.example.gamechat.ui

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.URLSpan
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.text.HtmlCompat
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import com.example.gamechat.R
import com.example.gamechat.data.ChatServerClient
import com.example.gamechat.data.ChatSocketClient
import com.example.gamechat.data.EncounterApiClient
import com.example.gamechat.data.EncounterUserAgentProvider
import com.example.gamechat.data.UserPreferences
import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class EngineFragment : Fragment(R.layout.fragment_engine) {
    interface Host {
        fun openEncounterAuthFromEngine()
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var lastState: EncounterApiClient.EngineLevelState? = null
    private var hintsBaseSeconds: List<Int?> = emptyList()
    private var hintsBaseTimestampMillis: Long = 0L
    private var sectorsExpanded: Boolean = true
    private var mixedActionsExpanded: Boolean = false
    private val expandedBonuses = mutableSetOf<Int>()
    private val pendingCodes = ArrayDeque<PendingCode>()
    private var isSendingPendingCode: Boolean = false
    private var pendingRetryScheduled: Boolean = false
    private var suppressNextLevelBroadcast = false
    private var host: Host? = null

    private data class PendingCode(
        val code: String,
        val queuedAtMillis: Long
    )

    private val hintsTimer = object : Runnable {
        override fun run() {
            renderHints(lastState?.hints.orEmpty())
            if (view != null && hintsBaseSeconds.any { (it ?: 0) > 0 }) {
                mainHandler.postDelayed(this, 1000)
            }
        }
    }

    private val pendingRetryTimer = object : Runnable {
        override fun run() {
            pendingRetryScheduled = false
            processPendingCodesQueue()
        }
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        host = context as? Host
    }

    override fun onDetach() {
        host = null
        super.onDetach()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupToolbarRefreshAction()

        val codeInput = view.findViewById<EditText>(R.id.engineCodeInput)
        val sendCodeButton = view.findViewById<ImageButton>(R.id.engineSendCodeButton)
        val mixedActionsScroll = view.findViewById<ScrollView>(R.id.engineMixedActionsScroll)
        val mixedActionsExpandButton = view.findViewById<ImageButton>(R.id.engineMixedActionsExpandButton)
        mixedActionsExpandButton.setOnClickListener {
            mixedActionsExpanded = !mixedActionsExpanded
            applyMixedActionsHeight(mixedActionsScroll, mixedActionsExpandButton)
        }
        view.post {
            applyMixedActionsHeight(mixedActionsScroll, mixedActionsExpandButton)
        }

        sendCodeButton.setOnClickListener {
            val code = codeInput.text?.toString().orEmpty().trim()
            if (code.isBlank()) {
                Toast.makeText(requireContext(), R.string.engine_code_empty, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            enqueuePendingCode(code)
            codeInput.text?.clear()
        }

        loadLevel()
    }

    override fun onDestroyView() {
        mainHandler.removeCallbacks(hintsTimer)
        mainHandler.removeCallbacks(pendingRetryTimer)
        super.onDestroyView()
    }

    override fun onResume() {
        super.onResume()
        connectEngineSocket()
        processPendingCodesQueue()
    }

    override fun onPause() {
        super.onPause()
        ChatSocketClient.disconnect()
        mainHandler.removeCallbacks(pendingRetryTimer)
        pendingRetryScheduled = false
    }

    private fun loadLevel() {
        val view = view ?: return
        val sendCodeButton = view.findViewById<ImageButton>(R.id.engineSendCodeButton)

        val session = UserPreferences.getEncounterSession(requireContext())
        val gameId = UserPreferences.getEncounterGameId(requireContext())

        if (session.site.isBlank() || session.login.isBlank()) {
            showStateMessage(getString(R.string.engine_need_auth))
            return
        }
        if (gameId.isBlank()) {
            showStateMessage(getString(R.string.engine_need_game_id))
            return
        }

        sendCodeButton.isEnabled = false
        if (lastState == null) {
            showStateMessage(getString(R.string.engine_loading))
        }
        val defaultWebViewUa = EncounterUserAgentProvider.get(requireContext())

        Thread {
            val result = EncounterApiClient.loadEngineState(
                siteBaseUrl = session.site,
                gameIdRaw = gameId,
                guid = session.guid,
                stoken = session.stoken,
                atoken = session.atoken,
                userAgent = defaultWebViewUa
            )

            activity?.runOnUiThread {
                if (!isAdded) return@runOnUiThread
                sendCodeButton.isEnabled = true

                result.onSuccess { state ->
                    renderState(state)
                }.onFailure { error ->
                    if (isSessionExpired(error)) {
                        showAuthExpiredMessage()
                    } else {
                        showStateMessage(
                            getString(
                                R.string.engine_level_error,
                                error.message ?: getString(R.string.unknown_error)
                            )
                        )
                    }
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.engine_level_error, error.message ?: getString(R.string.unknown_error)),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }.start()
    }

    private fun enqueuePendingCode(code: String) {
        pendingCodes.addLast(
            PendingCode(
                code = code,
                queuedAtMillis = System.currentTimeMillis()
            )
        )
        refreshMixedActions()
        processPendingCodesQueue()
    }

    private fun processPendingCodesQueue() {
        if (isSendingPendingCode) return
        if (pendingCodes.isEmpty()) return
        val pending = pendingCodes.first()

        val session = UserPreferences.getEncounterSession(requireContext())
        val gameId = UserPreferences.getEncounterGameId(requireContext())
        if (session.site.isBlank() || session.login.isBlank() || gameId.isBlank()) {
            schedulePendingRetry()
            refreshMixedActions()
            return
        }

        val state = lastState
        val defaultWebViewUa = EncounterUserAgentProvider.get(requireContext())
        isSendingPendingCode = true

        Thread {
            val result = EncounterApiClient.submitCode(
                siteBaseUrl = session.site,
                gameIdRaw = gameId,
                levelId = state?.levelId,
                levelNumber = state?.levelNumber,
                code = pending.code,
                guid = session.guid,
                stoken = session.stoken,
                atoken = session.atoken,
                userAgent = defaultWebViewUa
            )

            activity?.runOnUiThread {
                if (!isAdded) return@runOnUiThread
                isSendingPendingCode = false

                result.onSuccess { submitResult ->
                    if (pendingCodes.isNotEmpty()) {
                        pendingCodes.removeFirst()
                    }
                    refreshMixedActions()
                    if (submitResult.state != null) {
                        renderState(submitResult.state)
                    } else {
                        loadLevel()
                    }
                    processPendingCodesQueue()
                }.onFailure { error ->
                    if (isSessionExpired(error)) {
                        showAuthExpiredMessage()
                        Toast.makeText(
                            requireContext(),
                            getString(R.string.engine_code_send_error, error.message ?: getString(R.string.unknown_error)),
                            Toast.LENGTH_LONG
                        ).show()
                        return@onFailure
                    }

                    if (isLikelyNetworkIssue(error)) {
                        schedulePendingRetry()
                        refreshMixedActions()
                        return@onFailure
                    }

                    if (pendingCodes.isNotEmpty()) {
                        pendingCodes.removeFirst()
                    }
                    refreshMixedActions()
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.engine_code_send_error, error.message ?: getString(R.string.unknown_error)),
                        Toast.LENGTH_LONG
                    ).show()
                    processPendingCodesQueue()
                }
            }
        }.start()
    }

    private fun schedulePendingRetry() {
        if (pendingRetryScheduled) return
        pendingRetryScheduled = true
        mainHandler.postDelayed(pendingRetryTimer, 5000)
    }

    private fun renderState(state: EncounterApiClient.EngineLevelState) {
        val previousLevelNumber = lastState?.levelNumber?.trim().orEmpty()
        lastState = state
        expandedBonuses.removeAll { it !in state.bonuses.indices }
        val root = view ?: return

        val header = root.findViewById<TextView>(R.id.engineLevelHeader)
        val sectorsTitle = root.findViewById<TextView>(R.id.engineSectorsTitle)
        val sectorsContainer = root.findViewById<LinearLayout>(R.id.engineSectorsContainer)
        val taskText = root.findViewById<TextView>(R.id.engineTaskText)
        val bonusesContainer = root.findViewById<LinearLayout>(R.id.engineBonusesContainer)
        val mixedActionsContainer = root.findViewById<LinearLayout>(R.id.engineMixedActionsContainer)
        val codeInput = root.findViewById<EditText>(R.id.engineCodeInput)

        val levelNum = state.levelNumber ?: "?"
        val totalLevels = state.totalLevels?.toString() ?: "?"
        val levelName = state.levelName.orEmpty().trim()
        header.text = if (levelName.isNotEmpty()) {
            getString(R.string.engine_level_header_template, levelNum, totalLevels, levelName)
        } else {
            getString(R.string.engine_level_header_no_name_template, levelNum, totalLevels)
        }

        val required = state.requiredSectorsCount?.toString() ?: "?"
        val total = state.totalSectorsCount.toString()
        val left = state.sectorsLeftToClose?.toString() ?: "?"
        val baseSectorsTitle = getString(R.string.engine_sectors_title_with_stats, required, total, left)
        sectorsTitle.text = "${if (sectorsExpanded) "\u25BC" else "\u25B6"} $baseSectorsTitle"
        sectorsTitle.setOnClickListener {
            sectorsExpanded = !sectorsExpanded
            sectorsTitle.text = "${if (sectorsExpanded) "\u25BC" else "\u25B6"} $baseSectorsTitle"
            sectorsContainer.visibility = if (sectorsExpanded) View.VISIBLE else View.GONE
        }
        sectorsContainer.visibility = if (sectorsExpanded) View.VISIBLE else View.GONE

        renderSectors(state.sectors, sectorsContainer)
        taskText.text = buildTaskTexts(state.taskTexts)

        hintsBaseSeconds = state.hints.map { it.remainingSeconds }
        hintsBaseTimestampMillis = System.currentTimeMillis()
        renderHints(state.hints)

        renderBonuses(state.bonuses, bonusesContainer)
        renderMixedActions(state.mixedActions, mixedActionsContainer, codeInput)

        val currentLevelNumber = state.levelNumber?.trim().orEmpty()
        if (
            previousLevelNumber.isNotBlank() &&
            currentLevelNumber.isNotBlank() &&
            previousLevelNumber != currentLevelNumber
        ) {
            if (!suppressNextLevelBroadcast) {
                notifyLevelChanged(currentLevelNumber)
            }
        }
        suppressNextLevelBroadcast = false

        mainHandler.removeCallbacks(hintsTimer)
        if (hintsBaseSeconds.any { (it ?: 0) > 0 }) {
            mainHandler.postDelayed(hintsTimer, 1000)
        }
    }

    private fun renderSectors(items: List<EncounterApiClient.EngineSector>, container: LinearLayout) {
        container.removeAllViews()
        if (items.isEmpty()) {
            container.addView(buildRow(getString(R.string.engine_empty_sectors)))
            return
        }
        items.sortedWith(compareBy<EncounterApiClient.EngineSector> { it.order }.thenBy { it.name })
            .forEach { sector ->
                val text = if (sector.isCompleted) {
                    val answer = sector.answerCode.orEmpty().ifBlank { "?" }
                    val time = formatBonusCompletionTime(sector.answerTimestamp).orEmpty().ifBlank { "??:??:??" }
                    val login = sector.answerLogin.orEmpty().ifBlank { "?" }
                    "${sector.name}: $answer ($time  $login)"
                } else {
                    "${sector.name}: —"
                }
                val color = if (sector.isCompleted) 0xFF66BB6A.toInt() else null
                container.addView(buildRow(text, textColor = color))
        }
    }

    private fun renderHints(items: List<EncounterApiClient.EngineHint>) {
        val root = view ?: return
        val container = root.findViewById<LinearLayout>(R.id.engineHintsContainer)
        container.removeAllViews()

        if (items.isEmpty()) {
            container.addView(buildRow(getString(R.string.engine_empty_hints)))
            return
        }

        val elapsed = ((System.currentTimeMillis() - hintsBaseTimestampMillis) / 1000L).toInt().coerceAtLeast(0)

        items.forEachIndexed { index, hint ->
            val base = hintsBaseSeconds.getOrNull(index)
            val remain = if (base == null) null else (base - elapsed).coerceAtLeast(0)

            val title = if (remain != null && remain > 0) {
                "${hint.title} (${getString(R.string.engine_hint_timer_template, remain.toString())})"
            } else {
                hint.title
            }
            container.addView(buildRow(title, isTitle = true))

            val text = when {
                remain != null && remain > 0 -> getString(R.string.engine_hint_locked)
                !hint.text.isNullOrBlank() -> hint.text
                else -> getString(R.string.engine_empty_hint_text)
            }
            container.addView(buildRow(toRichText(text), topMarginDp = 2, bottomMarginDp = 10))
        }
    }

    private fun renderBonuses(items: List<EncounterApiClient.EngineBonus>, container: LinearLayout) {
        container.removeAllViews()
        if (items.isEmpty()) {
            container.addView(buildRow(getString(R.string.engine_empty_bonuses)))
            return
        }
        items.forEachIndexed { index, bonus ->
            val hasTask = !bonus.text.isNullOrBlank()
            val bonusNumber = (index + 1).toString()
            val fallbackTitle = "Бонус $bonusNumber"
            val normalizedTitle = bonus.title.trim()
            val header = if (normalizedTitle.equals(fallbackTitle, ignoreCase = true)) {
                fallbackTitle
            } else {
                getString(
                    R.string.engine_bonus_header_template,
                    bonusNumber,
                    bonus.title
                )
            }
            val headerColor = 0xFF00D8FF.toInt()
            if (bonus.isCompleted && hasTask) {
                val isExpanded = expandedBonuses.contains(index)
                val expandIcon = if (isExpanded) "\u25BC" else "\u25B6"
                val headerView = buildRow("$expandIcon $header", isTitle = true, textColor = headerColor)
                headerView.movementMethod = null
                headerView.setOnClickListener {
                    if (expandedBonuses.contains(index)) {
                        expandedBonuses.remove(index)
                    } else {
                        expandedBonuses.add(index)
                    }
                    renderBonuses(items, container)
                }
                container.addView(headerView)
            } else {
                container.addView(buildRow(header, isTitle = true, textColor = headerColor))
            }

            if (!bonus.isCompleted || (bonus.isCompleted && hasTask && expandedBonuses.contains(index))) {
                container.addView(
                    buildRow(
                        toRichText(bonus.text ?: getString(R.string.engine_empty_bonus_text)),
                        topMarginDp = 2,
                        bottomMarginDp = 10
                    )
                )
            }

            if (bonus.isCompleted) {
                val completedLine = buildBonusCompletedLine(bonus)
                if (completedLine.isNotBlank()) {
                    container.addView(
                        buildRow(
                            completedLine,
                            textColor = 0xFF9EE7A9.toInt(),
                            topMarginDp = 2,
                            bottomMarginDp = 8
                        )
                    )
                }
            }
            if (bonus.isCompleted && !bonus.hint.isNullOrBlank()) {
                val hintHeader = getString(R.string.engine_bonus_hint_title)
                container.addView(buildRow(hintHeader, isTitle = true, textColor = 0xFF90CAF9.toInt(), topMarginDp = 2))
                container.addView(
                    buildRow(
                        toRichText(bonus.hint),
                        topMarginDp = 2,
                        bottomMarginDp = 12
                    )
                )
            }
        }
    }

    private fun showStateMessage(message: String) {
        val root = view ?: return
        root.findViewById<TextView>(R.id.engineLevelHeader).text = getString(R.string.engine_current_level)
        val sectorsTitle = root.findViewById<TextView>(R.id.engineSectorsTitle)
        val sectorsContainer = root.findViewById<LinearLayout>(R.id.engineSectorsContainer)
        sectorsTitle.text = "${if (sectorsExpanded) "\u25BC" else "\u25B6"} ${getString(R.string.engine_sectors_title)}"
        sectorsTitle.setOnClickListener {
            sectorsExpanded = !sectorsExpanded
            sectorsTitle.text = "${if (sectorsExpanded) "\u25BC" else "\u25B6"} ${getString(R.string.engine_sectors_title)}"
            sectorsContainer.visibility = if (sectorsExpanded) View.VISIBLE else View.GONE
        }
        sectorsContainer.apply {
            removeAllViews()
            visibility = if (sectorsExpanded) View.VISIBLE else View.GONE
            addView(buildRow(message))
        }
        root.findViewById<TextView>(R.id.engineTaskText).text = ""
        root.findViewById<LinearLayout>(R.id.engineHintsContainer).removeAllViews()
        root.findViewById<LinearLayout>(R.id.engineBonusesContainer).removeAllViews()
        refreshMixedActions()
        mainHandler.removeCallbacks(hintsTimer)
    }

    private fun showAuthExpiredMessage() {
        val message = getString(R.string.engine_auth_expired_message_with_link)
        showStateMessage(message)
    }

    private fun buildRow(
        text: String,
        isTitle: Boolean = false,
        textColor: Int? = null,
        topMarginDp: Int = 0,
        bottomMarginDp: Int = 6
    ): TextView {
        return buildRow(toRichText(text), isTitle, textColor, topMarginDp, bottomMarginDp)
    }

    private fun buildRow(
        text: Spanned,
        isTitle: Boolean = false,
        textColor: Int? = null,
        topMarginDp: Int = 0,
        bottomMarginDp: Int = 6
    ): TextView {
        val tv = TextView(requireContext())
        tv.text = makeClickableAuthLink(text)
        tv.textSize = if (isTitle) 16f else 15f
        tv.setTextColor(textColor ?: if (isTitle) 0xFFEAF0FF.toInt() else 0xFFC7D0E4.toInt())
        tv.setLinkTextColor(0xFF90CAF9.toInt())
        tv.movementMethod = LinkMovementMethod.getInstance()
        tv.highlightColor = 0

        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        params.topMargin = dp(topMarginDp)
        params.bottomMargin = dp(bottomMarginDp)
        tv.layoutParams = params
        return tv
    }

    private fun toRichText(raw: String): Spanned {
        return HtmlCompat.fromHtml(raw, HtmlCompat.FROM_HTML_MODE_LEGACY)
    }

    private fun buildTaskTexts(taskTexts: List<String>): Spanned {
        if (taskTexts.isEmpty()) {
            return toRichText(getString(R.string.engine_empty_task))
        }
        val builder = SpannableStringBuilder()
        taskTexts.forEachIndexed { index, task ->
            if (index > 0) {
                builder.append("\n\n")
            }
            builder.append(toRichText(task))
        }
        return builder
    }

    private fun renderMixedActions(
        items: List<EncounterApiClient.EngineMixedAction>,
        container: LinearLayout,
        codeInput: EditText
    ) {
        container.removeAllViews()
        if (pendingCodes.isNotEmpty()) {
            pendingCodes.forEachIndexed { index, pending ->
                val status = if (index == 0 && pendingRetryScheduled) {
                    getString(
                        R.string.engine_pending_code_retry_template,
                        formatTimeForPendingCode(pending.queuedAtMillis),
                        pending.code
                    )
                } else {
                    getString(
                        R.string.engine_pending_code_waiting_template,
                        formatTimeForPendingCode(pending.queuedAtMillis),
                        pending.code
                    )
                }
                container.addView(
                    buildMixedActionRow(
                        text = status,
                        answerToFill = pending.code,
                        codeInput = codeInput,
                        textColor = 0xFF8E8E93.toInt()
                    )
                )
            }
        }

        if (items.isEmpty() && pendingCodes.isEmpty()) {
            container.addView(buildMixedActionRow(getString(R.string.engine_mixed_actions_empty), null, null))
            return
        }
        items.forEach { action ->
            val line = "${action.timeLabel} ${action.login}: ${action.answer}"
            val color = when {
                action.isCorrect && action.kind == 1 -> 0xFF66BB6A.toInt()
                action.isCorrect && action.kind == 2 -> 0xFF00D8FF.toInt()
                else -> null
            }
            container.addView(buildMixedActionRow(line, action.answer, codeInput, color))
        }
    }

    private fun buildMixedActionRow(
        text: String,
        answerToFill: String?,
        codeInput: EditText?,
        textColor: Int? = null
    ): TextView {
        val row = TextView(requireContext())
        row.text = toRichText(text)
        row.textSize = 14f
        row.setTextColor(textColor ?: 0xFFC7D0E4.toInt())
        row.setLineSpacing(dp(4).toFloat(), 1.0f)
        row.setPadding(0, dp(2), 0, dp(6))

        if (!answerToFill.isNullOrBlank() && codeInput != null) {
            row.isClickable = true
            row.isFocusable = true
            row.setOnClickListener {
                codeInput.setText(answerToFill)
                codeInput.requestFocus()
                codeInput.setSelection(codeInput.text?.length ?: 0)
            }
        }
        return row
    }

    private fun refreshMixedActions() {
        val root = view ?: return
        val mixedActionsContainer = root.findViewById<LinearLayout>(R.id.engineMixedActionsContainer)
        val codeInput = root.findViewById<EditText>(R.id.engineCodeInput)
        renderMixedActions(lastState?.mixedActions.orEmpty(), mixedActionsContainer, codeInput)
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    private fun formatTimeForPendingCode(millis: Long): String {
        return SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(millis))
    }

    private fun buildBonusCompletedLine(bonus: EncounterApiClient.EngineBonus): String {
        val who = bonus.completedBy.orEmpty().trim().ifBlank { "?" }
        val whenText = formatBonusCompletionTime(bonus.completedAt).orEmpty().ifBlank { "??:??:??" }
        val code = bonus.completedCode.orEmpty().trim().ifBlank { "?" }
        return "✅ $whenText $who [ $code ]"
    }

    private fun formatBonusCompletionTime(raw: String?): String? {
        val value = raw?.trim().orEmpty()
        if (value.isBlank()) return null

        value.toLongOrNull()?.let { numeric ->
            val millis = when {
                numeric > 999_999_999_999L -> numeric
                numeric > 0 -> numeric * 1000L
                else -> 0L
            }
            if (millis > 0L) {
                return SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(java.util.Date(millis))
            }
        }

        val parsers = listOf(
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSX", Locale.US),
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssX", Locale.US),
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        )
        parsers.forEach { parser ->
            runCatching { parser.parse(value) }.getOrNull()?.let { date ->
                return SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(date)
            }
        }
        return value.takeLast(8).takeIf { it.matches("\\d{2}:\\d{2}:\\d{2}".toRegex()) } ?: value
    }

    private fun isSessionExpired(error: Throwable): Boolean {
        val message = error.message.orEmpty()
        return message.contains("Сессия Encounter истекла", ignoreCase = true)
    }

    private fun isLikelyNetworkIssue(error: Throwable): Boolean {
        val root = generateSequence(error) { it.cause }.lastOrNull() ?: error
        if (
            root is UnknownHostException ||
            root is SocketTimeoutException ||
            root is ConnectException ||
            root is NoRouteToHostException ||
            root is SocketException ||
            root is IOException
        ) {
            return true
        }
        val message = root.message.orEmpty().lowercase(Locale.getDefault())
        return message.contains("network") ||
            message.contains("timeout") ||
            message.contains("timed out") ||
            message.contains("unable to resolve host") ||
            message.contains("failed to connect") ||
            message.contains("connection refused")
    }

    private fun makeClickableAuthLink(text: Spanned): Spanned {
        val builder = SpannableStringBuilder(text)
        val spans = builder.getSpans(0, builder.length, URLSpan::class.java)
        spans.forEach { span ->
            if (span.url == "app://enc-auth") {
                val start = builder.getSpanStart(span)
                val end = builder.getSpanEnd(span)
                val flags = builder.getSpanFlags(span)
                builder.removeSpan(span)
                builder.setSpan(
                    object : ClickableSpan() {
                        override fun onClick(widget: View) {
                            host?.openEncounterAuthFromEngine()
                        }

                        override fun updateDrawState(ds: TextPaint) {
                            super.updateDrawState(ds)
                            ds.isUnderlineText = true
                            ds.color = 0xFF90CAF9.toInt()
                        }
                    },
                    start,
                    end,
                    flags
                )
            }
        }
        return builder
    }

    private fun setupToolbarRefreshAction() {
        val menuProvider = object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menuInflater.inflate(R.menu.engine_actions_menu, menu)
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                return when (menuItem.itemId) {
                    R.id.action_engine_refresh -> {
                        loadLevel()
                        true
                    }

                    else -> false
                }
            }
        }
        requireActivity().addMenuProvider(menuProvider, viewLifecycleOwner, Lifecycle.State.RESUMED)
    }

    private fun connectEngineSocket() {
        val serverUrl = UserPreferences.getServerUrl(requireContext())
        ChatSocketClient.connect(serverUrl, object : ChatSocketClient.Listener {
            override fun onEvent(type: String, activeRoom: String?, levelNumber: String?) {
                if (type != "engine_level_changed") return
                activity?.runOnUiThread {
                    if (!isAdded) return@runOnUiThread
                    val incomingLevel = levelNumber?.trim().orEmpty()
                    val currentLevel = lastState?.levelNumber?.trim().orEmpty()
                    if (incomingLevel.isBlank() || currentLevel.isBlank()) return@runOnUiThread
                    if (incomingLevel != currentLevel) {
                        suppressNextLevelBroadcast = true
                        playLevelChangedNotification()
                        loadLevel()
                    }
                }
            }

            override fun onError(errorMessage: String) {
                // Ignore background socket errors in engine view.
            }
        })
    }

    private fun notifyLevelChanged(levelNumber: String) {
        val serverUrl = UserPreferences.getServerUrl(requireContext())
        Thread {
            ChatServerClient.notifyEngineLevelChanged(serverUrl, levelNumber)
        }.start()
    }

    private fun playLevelChangedNotification() {
        runCatching {
            val tone = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100)
            tone.startTone(ToneGenerator.TONE_PROP_BEEP, 250)
            mainHandler.postDelayed({ tone.release() }, 300)
        }
    }

    private fun applyMixedActionsHeight(scroll: ScrollView, button: ImageButton) {
        val collapsed = dp(96)
        val rootHeight = view?.height ?: resources.displayMetrics.heightPixels
        val expanded = (rootHeight * 0.5f).toInt().coerceAtLeast(collapsed)
        scroll.layoutParams = scroll.layoutParams.apply {
            height = if (mixedActionsExpanded) expanded else collapsed
        }
        scroll.requestLayout()

        if (mixedActionsExpanded) {
            button.setImageResource(android.R.drawable.arrow_down_float)
            button.contentDescription = getString(R.string.engine_mixed_actions_collapse)
        } else {
            button.setImageResource(android.R.drawable.arrow_up_float)
            button.contentDescription = getString(R.string.engine_mixed_actions_expand)
        }
    }
}
