package com.senderlink.app.model

import android.os.Parcelable
import com.google.gson.annotations.SerializedName
import kotlinx.parcelize.Parcelize

@Parcelize
data class Participante(
    @SerializedName("uid")
    val uid: String,

    @SerializedName("nombre")
    val nombre: String? = null,

    @SerializedName("foto")
    val foto: String? = null,

    @SerializedName("fechaUnion")
    val fechaUnion: String? = null
) : Parcelable
