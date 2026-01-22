package com.senderlink.app.model

import com.google.gson.annotations.SerializedName

data class Comment(
    @SerializedName("_id")
    val id: String,
    val postId: String,
    val uid: String,
    val userName: String,
    val userPhoto: String? = null,
    val text: String,
    val createdAt: String
)
