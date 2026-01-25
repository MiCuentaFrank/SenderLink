package com.senderlink.app.viewmodel

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.senderlink.app.model.GroupMessage
import com.senderlink.app.model.Participante
import com.senderlink.app.repository.EventRepository
import com.senderlink.app.repository.GroupChatRepository
import kotlinx.coroutines.launch

/**
 * Datos de participantes (organizador + lista)
 */
data class ParticipantsData(
    val participantes: List<Participante>,
    val organizadorUid: String?
)

class GroupChatViewModel : ViewModel() {

    private val chatRepo = GroupChatRepository()
    private val eventRepo = EventRepository()  // ✅ AÑADIDO
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()

    private val TAG = "GroupChatViewModel"

    private val _messages = MutableLiveData<List<GroupMessage>>(emptyList())
    val messages: LiveData<List<GroupMessage>> = _messages

    // ✅ CAMBIADO: data class propia en lugar de importar de network
    private val _participantsData = MutableLiveData<ParticipantsData?>(null)
    val participantsData: LiveData<ParticipantsData?> = _participantsData

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _error = MutableLiveData<String?>(null)
    val error: LiveData<String?> = _error

    private var currentChatId: String? = null

    // loading counter para que no "parpadee" si cargas varias cosas
    private var loadingCount = 0
    private fun startLoading() {
        loadingCount++
        _isLoading.value = loadingCount > 0
    }
    private fun stopLoading() {
        loadingCount = (loadingCount - 1).coerceAtLeast(0)
        _isLoading.value = loadingCount > 0
    }

    fun clearError() {
        _error.value = null
    }

    fun loadMessages(chatId: String, limit: Int = 50) {
        currentChatId = chatId
        startLoading()
        _error.value = null

        chatRepo.getMessages(
            chatId = chatId,
            limit = limit,
            onSuccess = { list ->
                _messages.value = list
                Log.d(TAG, "✅ Mensajes cargados: ${list.size}")
                stopLoading()
            },
            onError = { msg ->
                _error.value = msg
                Log.e(TAG, "❌ Error cargando mensajes: $msg")
                stopLoading()
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

        val senderName = user.displayName ?: "Usuario"
        val senderPhoto = user.photoUrl?.toString()

        startLoading()
        _error.value = null
        currentChatId = chatId

        chatRepo.sendMessage(
            chatId = chatId,
            uid = uid,
            text = trimmed,
            senderName = senderName,
            senderPhoto = senderPhoto,
            onSuccess = { sentMessage ->
                val current = _messages.value ?: emptyList()
                _messages.value = current + sentMessage

                chatRepo.getMessages(
                    chatId = chatId,
                    limit = 50,
                    onSuccess = { list ->
                        _messages.value = list
                        stopLoading()
                    },
                    onError = { msg ->
                        _error.value = msg
                        stopLoading()
                    }
                )
            },
            onError = { msg ->
                _error.value = msg
                stopLoading()
            }
        )
    }

    // ✅ CORREGIDO: Cargar participantes usando EventRepository
    fun loadParticipants(eventoId: String) {
        Log.d(TAG, "➡️ loadParticipants(eventoId=$eventoId)")

        startLoading()
        _error.value = null

        viewModelScope.launch {
            try {
                val uid = auth.currentUser?.uid

                // ✅ Usar EventRepository.getEventoById() que YA existe
                val response = eventRepo.getEventoById(eventoId, uid)

                if (response.ok && response.data != null) {
                    val evento = response.data

                    // ✅ Extraer participantes y organizador del evento
                    val data = ParticipantsData(
                        participantes = evento.participantes,
                        organizadorUid = evento.organizadorUid
                    )

                    _participantsData.value = data

                    Log.d(TAG, "✅ Participantes cargados: ${evento.participantes.size} " +
                            "organizador=${evento.organizadorUid.take(8)}")
                } else {
                    _error.value = response.message ?: "No se pudo cargar información del evento"
                    Log.e(TAG, "❌ Error: ${response.message}")
                }
            } catch (e: Exception) {
                _error.value = "Error de conexión: ${e.message}"
                Log.e(TAG, "❌ Excepción: ${e.message}", e)
            } finally {
                stopLoading()
            }
        }
    }
}