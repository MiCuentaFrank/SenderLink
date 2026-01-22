package com.senderlink.app.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.firebase.auth.FirebaseAuth
import com.senderlink.app.model.Comment
import com.senderlink.app.model.Post
import com.senderlink.app.repository.CommunityRepository
import com.senderlink.app.network.*
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class ComunidadViewModel : ViewModel() {

    private val repo = CommunityRepository()

    private val _posts = MutableLiveData<List<Post>>(emptyList())
    val posts: LiveData<List<Post>> = _posts

    private val _comments = MutableLiveData<List<Comment>>(emptyList())
    val comments: LiveData<List<Comment>> = _comments

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _error = MutableLiveData<String?>(null)
    val error: LiveData<String?> = _error

    fun loadPosts() {
        _isLoading.value = true
        repo.getPosts().enqueue(object : Callback<CommunityPostsResponse> {
            override fun onResponse(
                call: Call<CommunityPostsResponse>,
                response: Response<CommunityPostsResponse>
            ) {
                _isLoading.value = false
                if (response.isSuccessful && response.body()?.ok == true) {
                    _posts.value = response.body()?.data ?: emptyList()
                } else {
                    _error.value = "No se pudieron cargar los posts"
                }
            }

            override fun onFailure(call: Call<CommunityPostsResponse>, t: Throwable) {
                _isLoading.value = false
                _error.value = t.message ?: "Error de red"
            }
        })
    }

    /**
     * ✅ Crear post con imagen opcional
     * El backend saca userName/userPhoto desde Mongo (User.nombre / User.foto)
     */
    fun createPost(text: String, imageUrl: String? = null) {
        val user = FirebaseAuth.getInstance().currentUser
        val uid = user?.uid ?: run {
            _error.value = "No hay usuario autenticado"
            return
        }

        _isLoading.value = true
        repo.createPost(uid = uid, text = text, image = imageUrl)
            .enqueue(object : Callback<CreatePostResponse> {
                override fun onResponse(
                    call: Call<CreatePostResponse>,
                    response: Response<CreatePostResponse>
                ) {
                    _isLoading.value = false
                    if (response.isSuccessful && response.body()?.ok == true) {
                        loadPosts()
                    } else {
                        _error.value = "No se pudo publicar"
                    }
                }

                override fun onFailure(call: Call<CreatePostResponse>, t: Throwable) {
                    _isLoading.value = false
                    _error.value = t.message ?: "Error de red"
                }
            })
    }

    fun toggleLike(postId: String) {
        val user = FirebaseAuth.getInstance().currentUser
        val uid = user?.uid ?: run {
            _error.value = "No hay usuario autenticado"
            return
        }

        repo.toggleLike(postId, uid).enqueue(object : Callback<LikePostResponse> {
            override fun onResponse(
                call: Call<LikePostResponse>,
                response: Response<LikePostResponse>
            ) {
                if (response.isSuccessful && response.body()?.ok == true) {
                    loadPosts()
                } else {
                    _error.value = "No se pudo dar like"
                }
            }

            override fun onFailure(call: Call<LikePostResponse>, t: Throwable) {
                _error.value = t.message ?: "Error de red"
            }
        })
    }

    fun loadComments(postId: String) {
        repo.getComments(postId).enqueue(object : Callback<CommentsResponse> {
            override fun onResponse(
                call: Call<CommentsResponse>,
                response: Response<CommentsResponse>
            ) {
                if (response.isSuccessful && response.body()?.ok == true) {
                    _comments.value = response.body()?.data ?: emptyList()
                } else {
                    _error.value = "No se pudieron cargar comentarios"
                }
            }

            override fun onFailure(call: Call<CommentsResponse>, t: Throwable) {
                _error.value = t.message ?: "Error de red"
            }
        })
    }

    /**
     * ✅ Crear comentario
     * El backend saca userName/userPhoto desde Mongo (User.nombre / User.foto)
     */
    fun createComment(postId: String, text: String) {
        val user = FirebaseAuth.getInstance().currentUser
        val uid = user?.uid ?: run {
            _error.value = "No hay usuario autenticado"
            return
        }

        repo.createComment(postId = postId, uid = uid, text = text)
            .enqueue(object : Callback<CreateCommentResponse> {
                override fun onResponse(
                    call: Call<CreateCommentResponse>,
                    response: Response<CreateCommentResponse>
                ) {
                    if (response.isSuccessful && response.body()?.ok == true) {
                        loadComments(postId)
                        loadPosts() // refresca commentsCount
                    } else {
                        _error.value = "No se pudo comentar"
                    }
                }

                override fun onFailure(call: Call<CreateCommentResponse>, t: Throwable) {
                    _error.value = t.message ?: "Error de red"
                }
            })
    }

    fun clearError() {
        _error.value = null
    }
}
