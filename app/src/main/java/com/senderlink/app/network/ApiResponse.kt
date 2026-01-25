package com.senderlink.app.network

import com.senderlink.app.model.Comment
import com.senderlink.app.model.EventoGrupal
import com.senderlink.app.model.GroupMessage
import com.senderlink.app.model.Post
import com.senderlink.app.model.Route
import com.senderlink.app.model.User

/**
 * 📦 Modelos de respuesta de la API
 */

// ========================================
// RESPUESTAS DE RUTAS
// ========================================

data class RouteResponse(
    val ok: Boolean,
    val page: Int,
    val limit: Int,
    val total: Int,
    val pages: Int,
    val routes: List<Route>
)

data class RouteDetailResponse(
    val ok: Boolean,
    val route: Route
)

data class FeaturedResponse(
    val ok: Boolean,
    val page: Int,
    val limit: Int,
    val total: Int,
    val pages: Int,
    val count: Int,
    val routes: List<Route>
)

data class RoutesNearResponse(
    val ok: Boolean,
    val count: Int,
    val routes: List<Route>
)

data class UserRoutesResponse(
    val ok: Boolean,
    val count: Int,
    val routes: List<Route>
)

// ========================================
// RESPUESTAS DE USUARIOS
// ========================================

data class UserResponse(
    val ok: Boolean,
    val message: String? = null,
    val user: User
)

data class CreateUserResponse(
    val ok: Boolean,
    val message: String,
    val user: User
)

data class UpdateUserResponse(
    val ok: Boolean,
    val message: String,
    val user: User
)

// ========================================
// RESPUESTAS DE COMUNIDAD
// ========================================

data class CommunityPostsResponse(
    val ok: Boolean,
    val message: String? = null,
    val data: List<Post>
)

data class UserPostsResponse(
    val ok: Boolean,
    val message: String? = null,
    val data: List<Post>
)

data class CreatePostResponse(
    val ok: Boolean,
    val message: String,
    val data: Post
)

data class LikePostResponse(
    val ok: Boolean,
    val message: String,
    val data: LikeResult
)

data class LikeResult(
    val postId: String,
    val liked: Boolean,
    val likesCount: Int
)

data class CommentsResponse(
    val ok: Boolean,
    val message: String? = null,
    val data: List<Comment>
)

data class CreateCommentResponse(
    val ok: Boolean,
    val message: String,
    val data: Comment
)

// ========================================
// RESPUESTAS DE EVENTOS (Rutas Grupales)
// ========================================

data class EventosResponse(
    val ok: Boolean,
    val message: String? = null,
    val data: EventosData? = null // ✅ nullable para evitar crash si backend manda null
)

data class EventosData(
    val eventos: List<EventoGrupal> = emptyList(),
    val total: Int = 0,
    val limit: Int = 20,
    val skip: Int = 0
)

data class EventoDetailResponse(
    val ok: Boolean,
    val message: String? = null,
    val data: EventoGrupal? = null
)

data class UserEventosResponse(
    val ok: Boolean,
    val message: String? = null,
    val data: List<EventoGrupal> = emptyList()
)

data class CreateEventoResponse(
    val ok: Boolean,
    val message: String,
    val data: EventoGrupal
)

data class JoinEventoResponse(
    val ok: Boolean,
    val message: String,
    val data: EventoGrupal
)

data class LeaveEventoResponse(
    val ok: Boolean,
    val message: String,
    val data: EventoGrupal
)

data class CancelEventoResponse(
    val ok: Boolean,
    val message: String,
    val data: EventoGrupal
)

data class FinishEventoResponse(
    val ok: Boolean,
    val message: String,
    val data: EventoGrupal
)

data class UpdateEventoResponse(
    val ok: Boolean,
    val message: String,
    val data: EventoGrupal
)

// ========================================
// RESPUESTAS DE CHAT GRUPAL
// ========================================

data class GroupMessagesResponse(
    val ok: Boolean,
    val message: String? = null,
    val data: List<GroupMessage>
)

data class SendGroupMessageResponse(
    val ok: Boolean,
    val message: String,
    val data: GroupMessage
)

data class GroupChatStatsResponse(
    val ok: Boolean,
    val message: String? = null,
    val data: GroupChatStats
)

data class GroupChatStats(
    val totalMessages: Int,
    val activeSenders: Int,
    val lastMessageAt: String?,
    val lastMessageText: String?
)

data class DeleteGroupMessageResponse(
    val ok: Boolean,
    val message: String,
    val data: DeletedMessageData
)

data class DeletedMessageData(
    val messageId: String
)
// ========================================
// RESPUESTAS DE PARTICIPANTES (CHAT GRUPAL)
// ========================================

data class GroupChatParticipantsResponse(
    val ok: Boolean,
    val message: String? = null,
    val data: GroupChatParticipantsData? = null
)

data class GroupChatParticipantsData(
    val organizadorUid: String? = null,
    val participantes: List<com.senderlink.app.model.Participante> = emptyList()
)
// ========================================
// RESPUESTA SUBIDA FOTO PERFIL
// ========================================

data class UploadUserPhotoResponse(
    val ok: Boolean,
    val message: String? = null,
    val user: User
)

