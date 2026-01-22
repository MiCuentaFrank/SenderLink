package com.senderlink.app.repository

import com.senderlink.app.model.GroupMessage
import com.senderlink.app.network.GroupChatService
import com.senderlink.app.network.GroupMessagesResponse
import com.senderlink.app.network.SendGroupMessageResponse
import com.senderlink.app.network.RetrofitClient
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class GroupChatRepository {

    private val service: GroupChatService =
        RetrofitClient.instance.create(GroupChatService::class.java)

    fun getMessages(
        chatId: String,
        limit: Int = 50,
        onSuccess: (List<GroupMessage>) -> Unit,
        onError: (String) -> Unit
    ) {
        service.getMessages(chatId, limit).enqueue(object : Callback<GroupMessagesResponse> {
            override fun onResponse(
                call: Call<GroupMessagesResponse>,
                response: Response<GroupMessagesResponse>
            ) {
                val body = response.body()

                if (response.isSuccessful && body != null && body.ok) {
                    onSuccess(body.data) // <- en tu API es NO nullable
                } else {
                    val msg = body?.message ?: "Error al cargar mensajes (${response.code()})"
                    onError(msg)
                }
            }

            override fun onFailure(call: Call<GroupMessagesResponse>, t: Throwable) {
                onError(t.message ?: "Error de conexión al cargar mensajes")
            }
        })
    }

    fun sendMessage(
        chatId: String,
        uid: String,
        text: String,
        senderName: String? = null,
        senderPhoto: String? = null,
        onSuccess: (GroupMessage) -> Unit,
        onError: (String) -> Unit
    ) {
        val body = mutableMapOf(
            "uid" to uid,
            "text" to text
        )

        // opcionales (si backend los ignora no pasa nada)
        if (!senderName.isNullOrBlank()) body["senderName"] = senderName
        if (!senderPhoto.isNullOrBlank()) body["senderPhoto"] = senderPhoto

        service.sendMessage(chatId, body).enqueue(object : Callback<SendGroupMessageResponse> {
            override fun onResponse(
                call: Call<SendGroupMessageResponse>,
                response: Response<SendGroupMessageResponse>
            ) {
                val res = response.body()

                if (response.isSuccessful && res != null && res.ok) {
                    onSuccess(res.data) // <- en tu API es NO nullable
                } else {
                    val msg = res?.message ?: "Error al enviar mensaje (${response.code()})"
                    onError(msg)
                }
            }

            override fun onFailure(call: Call<SendGroupMessageResponse>, t: Throwable) {
                onError(t.message ?: "Error de conexión al enviar mensaje")
            }
        })
    }
}
