package com.senderlink.app.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.firebase.auth.FirebaseAuth
import com.senderlink.app.model.DirectMessage
import com.senderlink.app.repository.MessageRepository

class DirectChatViewModel : ViewModel() {

    private val repo = MessageRepository()
    private val auth = FirebaseAuth.getInstance()

    private val _messages = MutableLiveData<List<DirectMessage>>(emptyList())
    val messages: LiveData<List<DirectMessage>> = _messages

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _error = MutableLiveData<String?>(null)
    val error: LiveData<String?> = _error

    private var currentChatId: String? = null
    private var currentOtherUid: String? = null

    fun loadMessages(chatId: String) {
        currentChatId = chatId
        _isLoading.value = true
        _error.value = null

        repo.getMessages(
            chatId = chatId,
            onSuccess = { response ->
                _messages.value = response.messages
                _isLoading.value = false
            },
            onError = { msg ->
                _error.value = msg
                _isLoading.value = false
            }
        )
    }

    fun refresh() {
        val chatId = currentChatId ?: return
        loadMessages(chatId)
    }

    fun sendMessage(chatId: String, otherUid: String, text: String) {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return
        if (trimmed.length > 500) {
            _error.value = "Máximo 500 caracteres"
            return
        }

        val uid = auth.currentUser?.uid
        if (uid.isNullOrBlank()) {
            _error.value = "No estás autenticado"
            return
        }

        currentChatId = chatId
        currentOtherUid = otherUid
        _isLoading.value = true
        _error.value = null

        repo.sendMessage(
            chatId = chatId,
            remitenteUid = uid,
            destinatarioUid = otherUid,
            texto = trimmed,
            onSuccess = {
                loadMessages(chatId)
            },
            onError = { msg ->
                _error.value = msg
                _isLoading.value = false
            }
        )
    }

    fun clearError() {
        _error.value = null
    }
}
