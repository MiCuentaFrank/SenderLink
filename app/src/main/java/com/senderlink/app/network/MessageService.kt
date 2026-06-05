package com.senderlink.app.network

import retrofit2.Call
import retrofit2.http.*

interface MessageService {

    @GET("api/messages/conversations/{uid}")
    fun getConversations(
        @Path("uid") uid: String
    ): Call<ConversationsResponse>

    @GET("api/messages/{chatId}")
    fun getMessages(
        @Path("chatId") chatId: String
    ): Call<DirectMessagesResponse>

    @POST("api/messages")
    fun sendMessage(
        @Body body: Map<String, String>
    ): Call<SendDirectMessageResponse>
}
