package com.senderlink.app.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.firebase.auth.FirebaseAuth
import com.senderlink.app.model.GroupMessage
import com.senderlink.app.repository.GroupChatRepository

class GroupChatViewModel : ViewModel() {

    private val repo = GroupChatRepository()

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()

    // -----------------------------
    // LiveData
    // -----------------------------
    private val _messages = MutableLiveData<List<GroupMessage>>(emptyList())
    val messages: LiveData<List<GroupMessage>> = _messages

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _error = MutableLiveData<String?>(null)
    val error: LiveData<String?> = _error

    // Guardamos el chat actual para refrescar fácil
    private var currentChatId: String? = null

    // -----------------------------
    // Public API
    // -----------------------------
    fun clearError() {
        _error.value = null
    }

    fun loadMessages(chatId: String, limit: Int = 50) {
        currentChatId = chatId
        _isLoading.value = true
        _error.value = null

        repo.getMessages(
            chatId = chatId,
            limit = limit,
            onSuccess = { list ->
                _messages.value = list
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

    fun sendMessage(chatId: String, text: String) {
        val trimmed = text.trim()

        if (trimmed.isEmpty()) {
            _error.value = "No puedes enviar un mensaje vacío 👀"
            return
        }

        if (trimmed.length > 500) {
            _error.value = "Máximo 500 caracteres (ahora mismo llevas ${trimmed.length})"
            return
        }

        val user = auth.currentUser
        val uid = user?.uid

        if (uid.isNullOrBlank()) {
            _error.value = "No estás autenticado. Vuelve a iniciar sesión."
            return
        }

        // Info opcional (si backend lo ignora, no pasa nada)
        val senderName = user.displayName ?: "Usuario"
        val senderPhoto = user.photoUrl?.toString()

        _isLoading.value = true
        _error.value = null
        currentChatId = chatId

        repo.sendMessage(
            chatId = chatId,
            uid = uid,
            text = trimmed,
            senderName = senderName,
            senderPhoto = senderPhoto,
            onSuccess = { sentMessage ->
                // 1) Añadimos optimista (rápido)
                val current = _messages.value ?: emptyList()
                _messages.value = current + sentMessage

                // 2) y refrescamos para que quede igual que servidor
                repo.getMessages(
                    chatId = chatId,
                    limit = 50,
                    onSuccess = { list ->
                        _messages.value = list
                        _isLoading.value = false
                    },
                    onError = { msg ->
                        // Si falla el refresh, al menos el mensaje optimista queda en pantalla
                        _error.value = msg
                        _isLoading.value = false
                    }
                )
            },
            onError = { msg ->
                _error.value = msg
                _isLoading.value = false
            }
        )
    }
}
