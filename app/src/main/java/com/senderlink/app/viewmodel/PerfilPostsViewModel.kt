package com.senderlink.app.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.firebase.auth.FirebaseAuth
import com.senderlink.app.model.Post
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
                _error.value = t.message ?: "Error de red"
            }
        })
    }

    fun clearError() {
        _error.value = null
    }
}
