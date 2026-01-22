package com.senderlink.app.network

import retrofit2.Call
import retrofit2.http.*

interface CommunityService {

    // POSTS
    @GET("api/community/posts")
    fun getPosts(): Call<CommunityPostsResponse>

    @GET("api/community/posts/user/{uid}")
    fun getPostsByUser(
        @Path("uid") uid: String
    ): Call<UserPostsResponse>

    @POST("api/community/posts")
    fun createPost(
        @Body body: Map<String, String>
    ): Call<CreatePostResponse>

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
