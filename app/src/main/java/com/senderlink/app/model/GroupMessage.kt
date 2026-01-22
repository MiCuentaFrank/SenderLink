package com.senderlink.app.model

import com.google.gson.annotations.SerializedName

/**
 * Modelo de mensaje de chat grupal
 * Mapea la respuesta del backend MongoDB
 */
data class GroupMessage(
    @SerializedName("_id")
    val id: String,

    @SerializedName("chatId")
    val chatId: String,

    @SerializedName("senderUid")
    val senderUid: String,

    @SerializedName("senderName")
    val senderName: String,

    @SerializedName("senderPhoto")
    val senderPhoto: String? = null,

    @SerializedName("text")
    val text: String,

    @SerializedName("type")
    val type: String = "TEXT",

    @SerializedName("createdAt")
    val createdAt: String,

    @SerializedName("updatedAt")
    val updatedAt: String
) {
    fun isMine(currentUid: String): Boolean = senderUid == currentUid

    fun isSystemMessage(): Boolean = type == "SYSTEM"

    fun getFormattedTime(): String {
        return try {
            createdAt.substring(11, 16) // "2026-01-21T14:30:00.000Z" → "14:30"
        } catch (e: Exception) {
            "00:00"
        }
    }

    fun hasSenderPhoto(): Boolean {
        return !senderPhoto.isNullOrBlank() &&
                senderPhoto != "null" &&
                senderPhoto != ""
    }
}