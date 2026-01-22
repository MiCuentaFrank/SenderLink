package com.senderlink.app.network

import retrofit2.Call
import retrofit2.http.*

interface GroupChatService {

    @GET("api/group-chat/{chatId}/messages")
    fun getMessages(
        @Path("chatId") chatId: String,
        @Query("limit") limit: Int = 50
    ): Call<GroupMessagesResponse>

    @POST("api/group-chat/{chatId}/messages")
    fun sendMessage(
        @Path("chatId") chatId: String,
        @Body body: Map<String, String>
    ): Call<SendGroupMessageResponse>

    @GET("api/group-chat/{chatId}/stats")
    fun getChatStats(
        @Path("chatId") chatId: String
    ): Call<GroupChatStatsResponse>

    @DELETE("api/group-chat/{chatId}/messages/{messageId}")
    fun deleteMessage(
        @Path("chatId") chatId: String,
        @Path("messageId") messageId: String
    ): Call<DeleteGroupMessageResponse>
}
