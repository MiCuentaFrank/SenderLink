package com.senderlink.app.repository

import com.senderlink.app.network.ConversationsResponse
import com.senderlink.app.network.DirectMessagesResponse
import com.senderlink.app.network.MessageService
import com.senderlink.app.network.RetrofitClient
import com.senderlink.app.network.SendDirectMessageResponse
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class MessageRepository {

    private val service = RetrofitClient.instance.create(MessageService::class.java)

    fun getConversations(
        uid: String,
        onSuccess: (ConversationsResponse) -> Unit,
        onError: (String) -> Unit
    ) {
        service.getConversations(uid).enqueue(object : Callback<ConversationsResponse> {
            override fun onResponse(call: Call<ConversationsResponse>, response: Response<ConversationsResponse>) {
                val body = response.body()
                if (response.isSuccessful && body != null && body.ok) {
                    onSuccess(body)
                } else {
                    onError(body?.message ?: "Error al cargar conversaciones (${response.code()})")
                }
            }
            override fun onFailure(call: Call<ConversationsResponse>, t: Throwable) {
                onError(t.message ?: "Error de conexión")
            }
        })
    }

    fun getMessages(
        chatId: String,
        onSuccess: (DirectMessagesResponse) -> Unit,
        onError: (String) -> Unit
    ) {
        service.getMessages(chatId).enqueue(object : Callback<DirectMessagesResponse> {
            override fun onResponse(call: Call<DirectMessagesResponse>, response: Response<DirectMessagesResponse>) {
                val body = response.body()
                if (response.isSuccessful && body != null && body.ok) {
                    onSuccess(body)
                } else {
                    onError(body?.message ?: "Error al cargar mensajes (${response.code()})")
                }
            }
            override fun onFailure(call: Call<DirectMessagesResponse>, t: Throwable) {
                onError(t.message ?: "Error de conexión")
            }
        })
    }

    fun sendMessage(
        chatId: String,
        remitenteUid: String,
        destinatarioUid: String,
        texto: String,
        onSuccess: (SendDirectMessageResponse) -> Unit,
        onError: (String) -> Unit
    ) {
        val body = mapOf(
            "chatId" to chatId,
            "remitenteUid" to remitenteUid,
            "destinatarioUid" to destinatarioUid,
            "texto" to texto
        )
        service.sendMessage(body).enqueue(object : Callback<SendDirectMessageResponse> {
            override fun onResponse(call: Call<SendDirectMessageResponse>, response: Response<SendDirectMessageResponse>) {
                val res = response.body()
                if (response.isSuccessful && res != null && res.ok) {
                    onSuccess(res)
                } else {
                    onError(res?.message ?: "Error al enviar mensaje (${response.code()})")
                }
            }
            override fun onFailure(call: Call<SendDirectMessageResponse>, t: Throwable) {
                onError(t.message ?: "Error de conexión")
            }
        })
    }
}
