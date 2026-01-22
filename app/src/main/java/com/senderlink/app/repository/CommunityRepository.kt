package com.senderlink.app.repository

import com.senderlink.app.network.CommunityService
import com.senderlink.app.network.RetrofitClient

class CommunityRepository {

    private val service = RetrofitClient.instance
        .create(CommunityService::class.java)

    fun getPosts() = service.getPosts()

    fun getPostsByUser(uid: String) =
        service.getPostsByUser(uid)

    /**
     * ✅ Crear post
     * Backend obtiene userName/userPhoto desde Mongo (User.nombre / User.foto)
     * Body: { uid, text, image? }
     */
    fun createPost(
        uid: String,
        text: String,
        image: String? = null
    ) = service.createPost(
        buildMap {
            put("uid", uid)
            put("text", text)
            if (!image.isNullOrBlank()) put("image", image)
        }
    )

    fun toggleLike(postId: String, uid: String) =
        service.toggleLike(
            postId,
            mapOf("uid" to uid)
        )

    fun getComments(postId: String) =
        service.getComments(postId)

    /**
     * ✅ Crear comentario
     * Backend obtiene userName/userPhoto desde Mongo (User.nombre / User.foto)
     * Body: { uid, text }
     */
    fun createComment(
        postId: String,
        uid: String,
        text: String
    ) = service.createComment(
        postId,
        mapOf(
            "uid" to uid,
            "text" to text
        )
    )
}
