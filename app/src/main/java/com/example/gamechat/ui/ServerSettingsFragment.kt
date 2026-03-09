package com.example.gamechat.ui

import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.gamechat.R
import com.example.gamechat.data.ChatServerClient
import com.example.gamechat.data.UserPreferences
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

class ServerSettingsFragment : Fragment(R.layout.fragment_server_settings) {
    private val allowedNicks = mutableListOf<String>()
    private val roomsWithHistory = mutableListOf<String>()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val accessDeniedText = view.findViewById<TextView>(R.id.serverSettingsAccessDenied)
        val serverUrlLayout = view.findViewById<TextInputLayout>(R.id.serverUrlLayout)
        val chatRoomLayout = view.findViewById<TextInputLayout>(R.id.chatRoomLayout)
        val encGameIdLayout = view.findViewById<TextInputLayout>(R.id.encGameIdLayout)
        val allowedNicksTitle = view.findViewById<TextView>(R.id.allowedNicksTitle)
        val allowedNickInput = view.findViewById<TextInputEditText>(R.id.allowedNickInput)
        val addAllowedNickButton = view.findViewById<Button>(R.id.addAllowedNickButton)
        val allowedNicksContainer = view.findViewById<LinearLayout>(R.id.allowedNicksContainer)
        val serverUrlInput = view.findViewById<TextInputEditText>(R.id.serverUrlInput)
        val chatRoomInput = view.findViewById<TextInputEditText>(R.id.chatRoomInput)
        val encGameIdInput = view.findViewById<TextInputEditText>(R.id.encGameIdInput)
        val clearRoomSelectorLayout = view.findViewById<TextInputLayout>(R.id.clearRoomSelectorLayout)
        val clearRoomSelectorInput = view.findViewById<AutoCompleteTextView>(R.id.clearRoomSelectorInput)
        val saveButton = view.findViewById<Button>(R.id.saveServerSettingsButton)
        val clearButton = view.findViewById<Button>(R.id.clearRoomHistoryButton)

        val isAdmin = UserPreferences.isAdmin(requireContext())
        if (!isAdmin) {
            accessDeniedText.visibility = View.VISIBLE
            serverUrlLayout.visibility = View.GONE
            chatRoomLayout.visibility = View.GONE
            allowedNicksTitle.visibility = View.GONE
            addAllowedNickButton.visibility = View.GONE
            allowedNickInput.visibility = View.GONE
            allowedNicksContainer.visibility = View.GONE
            clearRoomSelectorLayout.visibility = View.GONE
            clearRoomSelectorInput.visibility = View.GONE
            saveButton.visibility = View.GONE
            clearButton.visibility = View.GONE
            
            // ID игры остается видимым но неактивным для не-админов
            encGameIdInput.isEnabled = false
            encGameIdInput.setText(UserPreferences.getEncounterGameId(requireContext()))
            loadGameIdFromServer(encGameIdInput)
            return
        }

        accessDeniedText.visibility = View.GONE
        serverUrlInput.setText(UserPreferences.getServerUrl(requireContext()))
        chatRoomInput.setText(UserPreferences.getChatRoom(requireContext()))
        encGameIdInput.setText(UserPreferences.getEncounterGameId(requireContext()))

        fun persistAllowedNicks() {
            val serverUrl = UserPreferences.getServerUrl(requireContext())
            val token = UserPreferences.getAdminToken(requireContext())
            addAllowedNickButton.isEnabled = false
            Thread {
                val result = ChatServerClient.setAllowedNicks(serverUrl, allowedNicks, token)
                activity?.runOnUiThread {
                    if (!isAdded) return@runOnUiThread
                    addAllowedNickButton.isEnabled = true
                    result.onFailure { error ->
                        Toast.makeText(
                            requireContext(),
                            getString(
                                R.string.server_settings_save_error,
                                error.message ?: getString(R.string.unknown_error)
                            ),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }.start()
        }

        fun renderAllowedNicks() {
            allowedNicksContainer.removeAllViews()
            if (allowedNicks.isEmpty()) {
                val emptyText = TextView(requireContext()).apply {
                    text = getString(R.string.settings_allowed_nicks_empty)
                    setTextColor(0xFF666666.toInt())
                }
                allowedNicksContainer.addView(emptyText)
                return
            }

            allowedNicks.forEach { nick ->
                val row = LinearLayout(requireContext()).apply {
                    orientation = LinearLayout.HORIZONTAL
                    val params = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    params.topMargin = 6
                    layoutParams = params
                }

                val nickText = TextView(requireContext()).apply {
                    text = nick
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }

                val removeButton = Button(requireContext()).apply {
                    text = getString(R.string.settings_allowed_nick_remove)
                    setOnClickListener {
                        allowedNicks.remove(nick)
                        renderAllowedNicks()
                        persistAllowedNicks()
                    }
                }

                row.addView(nickText)
                row.addView(removeButton)
                allowedNicksContainer.addView(row)
            }
        }

        fun renderRoomsWithHistory() {
            val adapter = ArrayAdapter(
                requireContext(),
                R.layout.item_room_dropdown,
                roomsWithHistory
            )
            clearRoomSelectorInput.setAdapter(adapter)
            val selected = clearRoomSelectorInput.text?.toString().orEmpty().trim()
            val nextSelected = when {
                selected.isNotBlank() && roomsWithHistory.contains(selected) -> selected
                roomsWithHistory.isNotEmpty() -> roomsWithHistory.first()
                else -> ""
            }
            clearRoomSelectorInput.setText(nextSelected, false)
            clearButton.isEnabled = roomsWithHistory.isNotEmpty()
        }

        fun applyRoomsFallback() {
            val fromInput = chatRoomInput.text?.toString().orEmpty().trim()
            val currentRoom = fromInput.ifBlank { UserPreferences.getChatRoom(requireContext()) }
            roomsWithHistory.clear()
            if (currentRoom.isNotBlank()) {
                roomsWithHistory.add(currentRoom)
            }
            renderRoomsWithHistory()
        }

        clearRoomSelectorInput.setOnClickListener {
            clearRoomSelectorInput.showDropDown()
        }
        clearRoomSelectorInput.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                clearRoomSelectorInput.showDropDown()
            }
        }

        addAllowedNickButton.setOnClickListener {
            val nick = allowedNickInput.text?.toString().orEmpty().trim().lowercase()
            if (nick.isBlank()) return@setOnClickListener
            if (!allowedNicks.contains(nick)) {
                allowedNicks.add(nick)
                allowedNicks.sort()
                renderAllowedNicks()
                persistAllowedNicks()
            }
            allowedNickInput.text?.clear()
        }

        val normalizedServerUrl = UserPreferences.getServerUrl(requireContext())
        val adminToken = UserPreferences.getAdminToken(requireContext())

        Thread {
            val listResult = ChatServerClient.getAllowedNicks(normalizedServerUrl, adminToken)
            val roomsResult = ChatServerClient.getRoomsWithHistory(normalizedServerUrl, adminToken)
            activity?.runOnUiThread {
                if (!isAdded) return@runOnUiThread
                listResult.onSuccess { list ->
                    allowedNicks.clear()
                    allowedNicks.addAll(list.map { it.lowercase() }.distinct().sorted())
                    renderAllowedNicks()
                }.onFailure {
                    renderAllowedNicks()
                }
                roomsResult.onSuccess { rooms ->
                    roomsWithHistory.clear()
                    roomsWithHistory.addAll(rooms.distinct())
                    renderRoomsWithHistory()
                }.onFailure {
                    applyRoomsFallback()
                }
            }
        }.start()

        saveButton.setOnClickListener {
            val enteredServerUrl = serverUrlInput.text?.toString().orEmpty()
            val enteredRoom = chatRoomInput.text?.toString().orEmpty()
            val enteredGameId = encGameIdInput.text?.toString().orEmpty()

            UserPreferences.setServerUrl(requireContext(), enteredServerUrl)
            UserPreferences.setChatRoom(requireContext(), enteredRoom)
            UserPreferences.setEncounterGameId(requireContext(), enteredGameId)

            serverUrlInput.setText(UserPreferences.getServerUrl(requireContext()))
            chatRoomInput.setText(UserPreferences.getChatRoom(requireContext()))
            encGameIdInput.setText(UserPreferences.getEncounterGameId(requireContext()))

            val serverUrl = UserPreferences.getServerUrl(requireContext())
            val room = UserPreferences.getChatRoom(requireContext())
            val token = UserPreferences.getAdminToken(requireContext())

            saveButton.isEnabled = false
            Thread {
                val switchRoomResult = ChatServerClient.switchActiveRoom(serverUrl, room, token)

                activity?.runOnUiThread {
                    if (!isAdded) return@runOnUiThread
                    saveButton.isEnabled = true

                    if (switchRoomResult.isSuccess) {
                        Toast.makeText(
                            requireContext(),
                            getString(R.string.server_settings_saved_room, room),
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        val error = switchRoomResult.exceptionOrNull()
                        Toast.makeText(
                            requireContext(),
                            getString(
                                R.string.server_settings_save_error,
                                error?.message ?: getString(R.string.unknown_error)
                            ),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }.start()
        }

        clearButton.setOnClickListener {
            val serverUrl = UserPreferences.getServerUrl(requireContext())
            val targetRoom = clearRoomSelectorInput.text?.toString().orEmpty().trim()
            if (targetRoom.isBlank()) {
                Toast.makeText(requireContext(), R.string.room_history_room_not_selected, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val token = UserPreferences.getAdminToken(requireContext())

            clearButton.isEnabled = false
            Thread {
                val result = ChatServerClient.clearRoomHistory(
                    serverBaseUrl = serverUrl,
                    room = targetRoom,
                    adminToken = token
                )
                activity?.runOnUiThread {
                    if (!isAdded) return@runOnUiThread
                    clearButton.isEnabled = true

                    result.onSuccess {
                        Toast.makeText(
                            requireContext(),
                            getString(R.string.room_history_cleared, targetRoom),
                            Toast.LENGTH_SHORT
                        ).show()
                        val roomsResult = ChatServerClient.getRoomsWithHistory(serverUrl, token)
                        roomsResult.onSuccess { rooms ->
                            roomsWithHistory.clear()
                            roomsWithHistory.addAll(rooms.distinct())
                            renderRoomsWithHistory()
                        }.onFailure {
                            applyRoomsFallback()
                        }
                    }.onFailure { error ->
                        Toast.makeText(
                            requireContext(),
                            getString(
                                R.string.room_history_clear_error,
                                error.message ?: getString(R.string.unknown_error)
                            ),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    if (roomsWithHistory.isEmpty()) {
                        clearButton.isEnabled = false
                    }
                }
            }.start()
        }
    }

    private fun loadGameIdFromServer(encGameIdInput: TextInputEditText) {
        Thread {
            // Синхронизируем gameId с сервера
            UserPreferences.syncGameIdFromServer(requireContext())
            
            activity?.runOnUiThread {
                if (!isAdded) return@runOnUiThread
                // Обновляем поле после загрузки
                val gameId = UserPreferences.getEncounterGameId(requireContext())
                encGameIdInput.setText(gameId)
            }
        }.start()
    }
}
