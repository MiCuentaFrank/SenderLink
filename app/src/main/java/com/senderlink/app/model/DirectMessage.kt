package com.senderlink.app.model

import com.google.gson.annotations.SerializedName

data class DirectMessage(
    @SerializedName("_id")
    val id: String = "",
    val chatId: String = "",
    val remitenteUid: String = "",
    val destinatarioUid: String = "",
    val texto: String = "",
    val leido: Boolean = false,
    val createdAt: String? = null
)

data class Conversation(
    val chatId: String = "",
    val lastMessage: String = "",
    val lastMessageAt: String? = null,
    val otherUid: String = "",
    val otherNombre: String = "",
    val otherFoto: String = ""
)