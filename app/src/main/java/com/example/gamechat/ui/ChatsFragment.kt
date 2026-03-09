package com.example.gamechat.ui

import android.app.AlertDialog
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.gamechat.R
import com.example.gamechat.data.ChatServerClient
import com.example.gamechat.data.ChatSocketClient
import com.example.gamechat.data.UserPreferences
import com.example.gamechat.ui.chat.ChatMessage
import com.example.gamechat.ui.chat.ChatMessageAdapter
import com.example.gamechat.ui.chat.DeliveryState
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ChatsFragment : Fragment(R.layout.fragment_chats) {
    private val messages = mutableListOf<ChatMessage>()
    private lateinit var adapter: ChatMessageAdapter
    private var isHistoryLoading = false
    private var pendingCameraUri: Uri? = null

    private val pickImageLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) {
                sendImageFromUri(uri)
            }
        }

    private val takePictureLauncher =
        registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            val uri = pendingCameraUri
            pendingCameraUri = null
            if (success && uri != null) {
                sendImageFromUri(uri)
            }
        }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        renderName(view)
        setupMessagesList(view)
        setupSend(view)
    }

    override fun onResume() {
        super.onResume()
        view?.let {
            renderName(it)
            loadHistory(showLoading = messages.isEmpty())
        }
        connectSocket()
    }

    override fun onPause() {
        super.onPause()
        ChatSocketClient.disconnect()
    }

    private fun renderName(root: View) {
        val name = UserPreferences.getChatNick(requireContext())
        val room = UserPreferences.getChatRoom(requireContext())
        root.findViewById<TextView>(R.id.chatDisplayName).text =
            getString(R.string.chat_display_name_with_room, name, room)
        activity?.title = "${getString(R.string.menu_chats)} • Комната $room • Вы $name"
    }

    private fun setupMessagesList(root: View) {
        val recycler = root.findViewById<RecyclerView>(R.id.messagesRecycler)
        adapter = ChatMessageAdapter(messages, { message ->
            showMessageActions(message)
        }, null)
        recycler.layoutManager = LinearLayoutManager(requireContext())
        recycler.adapter = adapter
    }

    private fun loadHistory(showLoading: Boolean) {
        if (!showLoading && messages.any { it.isOutgoing && it.deliveryState == DeliveryState.SENDING }) {
            return
        }
        if (isHistoryLoading) return
        isHistoryLoading = true

        val context = requireContext()
        val displayName = UserPreferences.getChatNick(context)
        val serverUrl = UserPreferences.getServerUrl(context)
        val room = UserPreferences.getChatRoom(context)

        if (showLoading) {
            replaceMessages(
                listOf(
                    ChatMessage(
                        text = getString(R.string.loading_history),
                        isOutgoing = false,
                        timeLabel = formatNowTime()
                    )
                )
            )
        }

        Thread {
            val result = ChatServerClient.loadHistory(
                serverBaseUrl = serverUrl,
                room = room,
                currentUser = displayName,
                limit = if (showLoading) 30 else null
            )
            activity?.runOnUiThread {
                if (!isAdded) return@runOnUiThread
                isHistoryLoading = false

                result.onSuccess { history ->
                    val serverRoom = history.activeRoom?.trim().orEmpty()
                    if (serverRoom.isNotEmpty() && !serverRoom.equals(room, ignoreCase = true)) {
                        UserPreferences.setChatRoom(requireContext(), serverRoom)
                        view?.let { renderName(it) }
                        Toast.makeText(
                            requireContext(),
                            getString(R.string.room_switched_notice, serverRoom),
                            Toast.LENGTH_SHORT
                        ).show()
                    }

                    if (history.messages.isEmpty()) {
                        replaceMessages(
                            listOf(
                                ChatMessage(
                                    text = getString(R.string.chat_welcome_message),
                                    isOutgoing = false,
                                    timeLabel = formatNowTime()
                                )
                            )
                        )
                    } else {
                        replaceMessages(
                            history.messages.map {
                                ChatMessage(
                                    id = it.id,
                                    senderName = it.senderName ?: if (it.isOutgoing) displayName else null,
                                    text = it.text,
                                    isOutgoing = it.isOutgoing,
                                    deliveryState = if (it.isOutgoing) {
                                        DeliveryState.SENT
                                    } else {
                                        DeliveryState.NONE
                                    },
                                    timeLabel = formatServerTime(it.timestamp),
                                    imageUrl = it.imageUrl?.let { url ->
                                        ChatServerClient.resolveServerMediaUrl(serverUrl, url)
                                    }
                                )
                            }
                        )
                    }
                }.onFailure { error ->
                    if (showLoading) {
                        replaceMessages(
                            listOf(
                                ChatMessage(
                                    text = getString(R.string.chat_welcome_message),
                                    isOutgoing = false
                                ),
                                ChatMessage(
                                    text = getString(
                                        R.string.history_load_error,
                                        error.message ?: getString(R.string.unknown_error)
                                    ),
                                    isOutgoing = false,
                                    timeLabel = formatNowTime()
                                )
                            )
                        )
                    }
                }
            }
        }.start()
    }

    private fun connectSocket() {
        val serverUrl = UserPreferences.getServerUrl(requireContext())
        ChatSocketClient.connect(serverUrl, object : ChatSocketClient.Listener {
            override fun onEvent(type: String, activeRoom: String?, levelNumber: String?) {
                activity?.runOnUiThread {
                    if (!isAdded) return@runOnUiThread
                    val currentRoom = UserPreferences.getChatRoom(requireContext())

                    when (type) {
                        "message" -> loadHistory(showLoading = false)
                        "message_deleted" -> loadHistory(showLoading = false)
                        "room_cleared" -> {
                            if (activeRoom.isNullOrBlank() || activeRoom.equals(currentRoom, true)) {
                                loadHistory(showLoading = false)
                            }
                        }

                        "room_switched", "connected" -> {
                            if (!activeRoom.isNullOrBlank() &&
                                !activeRoom.equals(currentRoom, ignoreCase = true)
                            ) {
                                UserPreferences.setChatRoom(requireContext(), activeRoom)
                                view?.let { renderName(it) }
                                Toast.makeText(
                                    requireContext(),
                                    getString(R.string.room_switched_notice, activeRoom),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                            loadHistory(showLoading = false)
                        }
                    }
                }
            }

            override fun onError(errorMessage: String) {
                // Silent: chat still works through manual refresh/send HTTP.
            }
        })
    }

    private fun setupSend(root: View) {
        val input = root.findViewById<EditText>(R.id.messageInput)
        val sendButton = root.findViewById<ImageButton>(R.id.sendButton)
        val attachButton = root.findViewById<ImageButton>(R.id.attachButton)

        fun sendCurrentMessage() {
            val message = input.text.toString().trim()
            if (message.isBlank()) {
                addIncomingMessage(getString(R.string.error_empty_message))
                return
            }

            val context = requireContext()
            val displayName = UserPreferences.getChatNick(context)
            val serverUrl = UserPreferences.getServerUrl(context)
            val room = UserPreferences.getChatRoom(context)

            val messageIndex = addOutgoingMessage(
                senderName = displayName,
                text = message,
                status = DeliveryState.SENDING,
                timeLabel = formatNowTime()
            )
            input.text?.clear()

            Thread {
                val result = ChatServerClient.sendMessage(
                    serverBaseUrl = serverUrl,
                    room = room,
                    userName = displayName,
                    message = message
                )
                activity?.runOnUiThread {
                    if (!isAdded) return@runOnUiThread

                    result.onSuccess {
                        updateOutgoingMessageStatus(messageIndex, DeliveryState.SENT)
                    }.onFailure {
                        updateOutgoingMessageStatus(messageIndex, DeliveryState.FAILED)
                    }
                }
            }.start()
        }

        sendButton.setOnClickListener { sendCurrentMessage() }
        attachButton.setOnClickListener { showImageSourceDialog() }
        input.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN) {
                sendCurrentMessage()
                true
            } else {
                false
            }
        }
    }

    private fun showImageSourceDialog() {
        val items = arrayOf(
            getString(R.string.chat_pick_image_gallery),
            getString(R.string.chat_pick_image_camera)
        )
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.chat_pick_image)
            .setItems(items) { _, which ->
                when (which) {
                    0 -> pickImageLauncher.launch("image/*")
                    1 -> launchCamera()
                }
            }
            .show()
    }

    private fun showMessageActions(message: ChatMessage) {
        if (message.id.isNullOrBlank()) return

        val items = arrayOf(getString(R.string.chat_action_delete))
        AlertDialog.Builder(requireContext())
            .setItems(items) { _, which ->
                if (which == 0) {
                    deleteMessage(message.id)
                }
            }
            .show()
    }

    private fun deleteMessage(messageId: String) {
        val serverUrl = UserPreferences.getServerUrl(requireContext())
        Thread {
            val result = ChatServerClient.deleteMessage(serverUrl, messageId)
            activity?.runOnUiThread {
                if (!isAdded) return@runOnUiThread
                result.onSuccess {
                    loadHistory(showLoading = false)
                }.onFailure { error ->
                    Toast.makeText(
                        requireContext(),
                        getString(
                            R.string.chat_delete_error,
                            error.message ?: getString(R.string.unknown_error)
                        ),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }.start()
    }

    private fun launchCamera() {
        val imagesDir = File(requireContext().cacheDir, "chat_images").apply { mkdirs() }
        val imageFile = File(imagesDir, "capture_${System.currentTimeMillis()}.jpg")
        val authority = "${requireContext().packageName}.fileprovider"
        pendingCameraUri = FileProvider.getUriForFile(requireContext(), authority, imageFile)
        takePictureLauncher.launch(pendingCameraUri)
    }

    private fun sendImageFromUri(imageUri: Uri) {
        val context = requireContext()
        val input = view?.findViewById<EditText>(R.id.messageInput)
        val caption = input?.text?.toString()?.trim().orEmpty()
        val displayName = UserPreferences.getChatNick(context)
        val serverUrl = UserPreferences.getServerUrl(context)
        val room = UserPreferences.getChatRoom(context)

        val messageIndex = addOutgoingMessage(
            senderName = displayName,
            text = caption,
            status = DeliveryState.SENDING,
            timeLabel = formatNowTime(),
            imageUrl = imageUri.toString()
        )
        input?.text?.clear()

        Thread {
            val uploadResult = runCatching { compressImageForUpload(imageUri) }
                .mapCatching { bytes -> ChatServerClient.uploadImage(serverUrl, bytes).getOrThrow() }

            val sendResult = uploadResult.mapCatching { uploadedImageUrl ->
                updateOutgoingMessageImage(messageIndex, uploadedImageUrl)
                ChatServerClient.sendMessage(
                    serverBaseUrl = serverUrl,
                    room = room,
                    userName = displayName,
                    message = caption,
                    imageUrl = uploadedImageUrl
                ).getOrThrow()
            }

            activity?.runOnUiThread {
                if (!isAdded) return@runOnUiThread
                sendResult.onSuccess {
                    updateOutgoingMessageStatus(messageIndex, DeliveryState.SENT)
                }.onFailure { error ->
                    updateOutgoingMessageStatus(messageIndex, DeliveryState.FAILED)
                    val text = error.message ?: getString(R.string.unknown_error)
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.chat_image_upload_error, text),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }.start()
    }

    private fun replaceMessages(newMessages: List<ChatMessage>) {
        val shouldStickToBottom = isNearBottom() || messages.isEmpty()
        messages.clear()
        messages.addAll(newMessages)
        adapter.notifyDataSetChanged()
        if (messages.isNotEmpty() && shouldStickToBottom) {
            view?.findViewById<RecyclerView>(R.id.messagesRecycler)?.scrollToPosition(messages.lastIndex)
        }
    }

    private fun addOutgoingMessage(
        senderName: String,
        text: String,
        status: DeliveryState,
        timeLabel: String,
        imageUrl: String? = null
    ): Int {
        messages.add(
            ChatMessage(
                senderName = senderName,
                text = text,
                isOutgoing = true,
                deliveryState = status,
                timeLabel = timeLabel,
                imageUrl = imageUrl
            )
        )
        notifyMessageInserted()
        return messages.lastIndex
    }

    private fun addIncomingMessage(text: String) {
        messages.add(ChatMessage(text = text, isOutgoing = false, timeLabel = formatNowTime()))
        notifyMessageInserted()
    }

    private fun updateOutgoingMessageStatus(index: Int, status: DeliveryState) {
        if (index !in messages.indices) return
        val current = messages[index]
        if (!current.isOutgoing) return

        messages[index] = current.copy(deliveryState = status)
        adapter.notifyItemChanged(index)
    }

    private fun updateOutgoingMessageImage(index: Int, imageUrl: String) {
        if (index !in messages.indices) return
        val current = messages[index]
        if (!current.isOutgoing) return

        messages[index] = current.copy(imageUrl = imageUrl)
        activity?.runOnUiThread { adapter.notifyItemChanged(index) }
    }

    private fun formatNowTime(): String {
        return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
    }

    private fun formatServerTime(timestamp: String?): String {
        val value = timestamp?.trim().orEmpty()
        if (value.isBlank()) return ""

        return try {
            val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSX", Locale.US)
            val parsed = parser.parse(value) ?: return ""
            SimpleDateFormat("HH:mm", Locale.getDefault()).format(parsed)
        } catch (_: Exception) {
            ""
        }
    }

    private fun notifyMessageInserted() {
        val position = messages.lastIndex
        adapter.notifyItemInserted(position)
        view?.findViewById<RecyclerView>(R.id.messagesRecycler)?.scrollToPosition(position)
    }

    private fun isNearBottom(): Boolean {
        val recycler = view?.findViewById<RecyclerView>(R.id.messagesRecycler) ?: return true
        val lm = recycler.layoutManager as? LinearLayoutManager ?: return true
        return lm.findLastVisibleItemPosition() >= messages.lastIndex - 2
    }

    private fun compressImageForUpload(uri: Uri): ByteArray {
        val resolver = requireContext().contentResolver

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri).use { stream ->
            BitmapFactory.decodeStream(stream, null, bounds)
        }

        var sampleSize = 1
        while (bounds.outWidth / sampleSize > 1600 || bounds.outHeight / sampleSize > 1600) {
            sampleSize *= 2
        }

        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val decoded = resolver.openInputStream(uri).use { stream ->
            BitmapFactory.decodeStream(stream, null, options)
        } ?: throw IllegalStateException("Unable to decode selected image")

        val scaled = scaleBitmapToMax(decoded, 1600)
        if (scaled !== decoded) {
            decoded.recycle()
        }

        val output = ByteArrayOutputStream()
        var quality = 82
        do {
            output.reset()
            scaled.compress(Bitmap.CompressFormat.JPEG, quality, output)
            quality -= 8
        } while (output.size() > 350 * 1024 && quality >= 50)

        scaled.recycle()
        return output.toByteArray()
    }

    private fun scaleBitmapToMax(source: Bitmap, maxSize: Int): Bitmap {
        val width = source.width
        val height = source.height
        val maxDimension = maxOf(width, height)
        if (maxDimension <= maxSize) return source

        val ratio = maxSize.toFloat() / maxDimension.toFloat()
        val targetWidth = (width * ratio).toInt().coerceAtLeast(1)
        val targetHeight = (height * ratio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(source, targetWidth, targetHeight, true)
    }
}
