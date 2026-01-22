package com.senderlink.app.network

import com.senderlink.app.model.PuntoEncuentro

data class CreateEventoBody(
    val routeId: String,
    val organizadorUid: String,
    val organizadorNombre: String,
    val organizadorFoto: String? = null,
    val fecha: String,
    val maxParticipantes: Int? = null,
    val descripcion: String? = null,
    val nivelRecomendado: String? = null,
    val puntoEncuentro: PuntoEncuentro? = null,
    val horaEncuentro: String? = null
)

data class JoinLeaveEventoBody(
    val uid: String,
    val nombre: String,
    val foto: String? = null
)

data class SimpleUidBody(
    val uid: String
)

data class UpdateEventoBody(
    val uid: String,
    val fecha: String? = null,
    val maxParticipantes: Int? = null,
    val descripcion: String? = null,
    val nivelRecomendado: String? = null,
    val puntoEncuentro: PuntoEncuentro? = null,
    val horaEncuentro: String? = null
)
