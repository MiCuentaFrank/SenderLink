package com.senderlink.app.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.firebase.auth.FirebaseAuth
import com.senderlink.app.model.Conversation
import com.senderlink.app.repository.MessageRepository

class ConversationsViewModel : ViewModel() {

    private val repo = MessageRepository()

    private val _conversations = MutableLiveData<List<Conversation>>(emptyList())
    val conversations: LiveData<List<Conversation>> = _conversations

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _error = MutableLiveData<String?>(null)
    val error: LiveData<String?> = _error

    fun load() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: run {
            _error.value = "No hay usuario autenticado"
            return
        }

        _isLoading.value = true
        _error.value = null

        repo.getConversations(
            uid = uid,
            onSuccess = { response ->
                _conversations.value = response.data
                _isLoading.value = false
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
