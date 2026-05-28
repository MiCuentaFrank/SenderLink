package com.senderlink.app.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.firebase.auth.FirebaseAuth
import com.senderlink.app.model.Post
import com.senderlink.app.network.DeletePostResponse
import com.senderlink.app.network.LikePostResponse
import com.senderlink.app.network.UserPostsResponse
import com.senderlink.app.repository.CommunityRepository
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class PerfilPostsViewModel : ViewModel() {

    private val repo = CommunityRepository()

    private val _myPosts = MutableLiveData<List<Post>>(emptyList())
    val myPosts: LiveData<List<Post>> = _myPosts

    private val _error = MutableLiveData<String?>(null)
    val error: LiveData<String?> = _error

    fun loadMyPosts() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid.isNullOrBlank()) {
            _error.value = "No hay usuario autenticado"
            return
        }

        repo.getPostsByUser(uid).enqueue(object : Callback<UserPostsResponse> {
            override fun onResponse(
                call: Call<UserPostsResponse>,
                response: Response<UserPostsResponse>
            ) {
                if (response.isSuccessful && response.body()?.ok == true) {
                    _myPosts.value = response.body()?.data ?: emptyList()
                } else {
                    _error.value = "No se pudieron cargar tus publicaciones"
                }
            }

            override fun onFailure(call: Call<UserPostsResponse>, t: Throwable) {
                _error.value = "Error de conexión. Intenta de nuevo."
            }
        })
    }

    fun toggleLike(postId: String) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid.isNullOrBlank()) {
            _error.value = "No hay usuario autenticado"
            return
        }

        repo.toggleLike(postId, uid).enqueue(object : Callback<LikePostResponse> {
            override fun onResponse(
                call: Call<LikePostResponse>,
                response: Response<LikePostResponse>
            ) {
                if (response.isSuccessful && response.body()?.ok == true) {
                    loadMyPosts()
                } else {
                    _error.value = "No se pudo actualizar el like"
                }
            }

            override fun onFailure(call: Call<LikePostResponse>, t: Throwable) {
                _error.value = "Error de conexión. Intenta de nuevo."
            }
        })
    }

    fun deletePost(postId: String) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid.isNullOrBlank()) {
            _error.value = "No hay usuario autenticado"
            return
        }

        repo.deletePost(postId, uid).enqueue(object : Callback<DeletePostResponse> {
            override fun onResponse(
                call: Call<DeletePostResponse>,
                response: Response<DeletePostResponse>
            ) {
                if (response.isSuccessful && response.body()?.ok == true) {
                    loadMyPosts()
                } else {
                    _error.value = "No se pudo eliminar la publicación"
                }
            }

            override fun onFailure(call: Call<DeletePostResponse>, t: Throwable) {
                _error.value = "Error de conexión. Intenta de nuevo."
            }
        })
    }

    fun clearError() {
        _error.value = null
    }
}
