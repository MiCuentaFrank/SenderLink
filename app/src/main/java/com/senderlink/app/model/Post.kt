package com.senderlink.app.model

import com.google.gson.annotations.SerializedName

data class Post(
    @SerializedName("_id")
    val id: String,
    val uid: String,
    val userName: String,
    val userPhoto: String? = null,
    val text: String,
    val image: String? = null,
    val routeId: String? = null,
    val likesCount: Int = 0,
    val commentsCount: Int = 0,
    val createdAt: String
)
