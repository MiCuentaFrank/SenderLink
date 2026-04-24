package com.senderlink.app.network

import okhttp3.MultipartBody
import retrofit2.Call
import retrofit2.http.*

interface CommunityService {

    // POSTS
    @GET("api/community/posts")
    fun getPosts(
        @Query("limit") limit: Int = 20,
        @Query("skip") skip: Int = 0
    ): Call<CommunityPostsResponse>

    @GET("api/community/posts/user/{uid}")
    fun getPostsByUser(
        @Path("uid") uid: String
    ): Call<UserPostsResponse>

    @Multipart
    @POST("api/community/posts/upload-image")
    fun uploadPostImage(
        @Part image: MultipartBody.Part
    ): Call<UploadPostImageResponse>

    @POST("api/community/posts")
    fun createPost(
        @Body body: Map<String, String>
    ): Call<CreatePostResponse>

    @DELETE("api/community/posts/{postId}")
    fun deletePost(
        @Path("postId") postId: String,
        @Body body: Map<String, String>
    ): Call<DeletePostResponse>

    @POST("api/community/posts/{postId}/like")
    fun toggleLike(
        @Path("postId") postId: String,
        @Body body: Map<String, String>
    ): Call<LikePostResponse>

    // COMMENTS
    @GET("api/community/posts/{postId}/comments")
    fun getComments(
        @Path("postId") postId: String
    ): Call<CommentsResponse>

    @POST("api/community/posts/{postId}/comments")
    fun createComment(
        @Path("postId") postId: String,
        @Body body: Map<String, String>
    ): Call<CreateCommentResponse>
}
