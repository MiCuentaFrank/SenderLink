const mongoose = require("mongoose");

/**
 * ===========================================
 * MODELO EVENTO GRUPAL (Rutas Grupales)
 * ===========================================
 *
 * Un evento grupal es una quedada organizada
 * por un usuario para realizar una ruta específica
 * en una fecha determinada.
 *
 * CARACTERÍSTICAS:
 * - Siempre asociado a una ruta (obligatorio)
 * - Tiene organizador y participantes
 * - Estados: abierto, completo, finalizado
 * - Chat integrado (reutiliza sistema de mensajes)
 */

const EventoGrupalSchema = new mongoose.Schema(
  {
    // 🗺️ RUTA ASOCIADA (OBLIGATORIA)
    routeId: {
      type: mongoose.Schema.Types.ObjectId,
      ref: "Route",
      required: true,
      index: true
    },

    // 👤 ORGANIZADOR
    organizadorUid: {
      type: String,
      required: true,
      index: true
    },
    organizadorNombre: {
      type: String,
      required: true
    },
    organizadorFoto: {
      type: String,
      default: ""
    },

    // 📅 FECHA Y HORA DEL EVENTO
    fecha: {
      type: Date,
      required: true,
      index: true
    },

    // 👥 PARTICIPANTES
    participantes: [
      {
        uid: { type: String, required: true },
        nombre: { type: String, required: true },
        foto: { type: String, default: "" },
        fechaUnion: { type: Date, default: Date.now }
      }
    ],

    // 🔢 LÍMITE DE PARTICIPANTES
    maxParticipantes: {
      type: Number,
      default: 10,
      min: 2,
      max: 50
    },

    // 📊 ESTADO DEL EVENTO
    estado: {
      type: String,
      enum: ["ABIERTO", "COMPLETO", "FINALIZADO", "CANCELADO"],
      default: "ABIERTO",
      index: true
    },

    // 💬 CHAT ASOCIADO (reutiliza infraestructura de mensajes)
    chatId: {
      type: String,
      unique: true,
      index: true
    },

    // 📝 INFORMACIÓN ADICIONAL
    descripcion: {
      type: String,
      default: "",
      maxlength: 500
    },

    // 🎯 NIVEL RECOMENDADO (heredado de la ruta, pero puede personalizarse)
    nivelRecomendado: {
      type: String,
      enum: ["BEGINNER", "INTERMEDIATE", "ADVANCED", "EXPERT", ""],
      default: ""
    },

    // 📌 PUNTO DE ENCUENTRO (opcional, coordenadas)
    puntoEncuentro: {
      nombre: { type: String, default: "" },
      lat: { type: Number },
      lng: { type: Number }
    },

    // ⏰ HORA DE ENCUENTRO (puede ser diferente a la fecha del evento)
    horaEncuentro: {
      type: String, // formato "HH:mm"
      default: "09:00"
    }
  },
  { timestamps: true }
);

// ===========================================
// ÍNDICES
// ===========================================
EventoGrupalSchema.index({ organizadorUid: 1, createdAt: -1 });
EventoGrupalSchema.index({ estado: 1, fecha: -1 });
EventoGrupalSchema.index({ routeId: 1, fecha: -1 });

// ===========================================
// MIDDLEWARE: Generar chatId automático
// ===========================================
EventoGrupalSchema.pre("save", function () {
  if (!this.chatId) {
    this.chatId = `evento_${this._id}_${Date.now()}`;
  }
});


// ===========================================
// MÉTODOS DEL MODELO
// ===========================================

/**
 * Añadir participante al evento
 * Retorna { success: boolean, message: string }
 */
EventoGrupalSchema.methods.addParticipante = function (uid, nombre, foto = "") {
  // Verificar si ya está participando
  const yaParticipa = this.participantes.some((p) => p.uid === uid);
  if (yaParticipa) {
    return { success: false, message: "Ya estás participando en este evento" };
  }

  // Verificar si está completo
  if (this.participantes.length >= this.maxParticipantes) {
    return { success: false, message: "El evento está completo" };
  }

  // Verificar si está finalizado o cancelado
  if (this.estado === "FINALIZADO" || this.estado === "CANCELADO") {
    return { success: false, message: "Este evento ya no está disponible" };
  }

  // Añadir participante
  this.participantes.push({
    uid,
    nombre,
    foto,
    fechaUnion: new Date()
  });

  // Actualizar estado si se llenó
  if (this.participantes.length >= this.maxParticipantes) {
    this.estado = "COMPLETO";
  }

  return { success: true, message: "Te has unido al evento" };
};

/**
 * Eliminar participante del evento
 * Retorna { success: boolean, message: string }
 */
EventoGrupalSchema.methods.removeParticipante = function (uid) {
  const index = this.participantes.findIndex((p) => p.uid === uid);

  if (index === -1) {
    return { success: false, message: "No estás participando en este evento" };
  }

  // No permitir que el organizador se salga
  if (uid === this.organizadorUid) {
    return {
      success: false,
      message: "El organizador no puede salir del evento. Cancela el evento si lo deseas."
    };
  }

  // Eliminar participante
  this.participantes.splice(index, 1);

  // Si estaba completo, volver a abierto
  if (this.estado === "COMPLETO") {
    this.estado = "ABIERTO";
  }

  return { success: true, message: "Has salido del evento" };
};

/**
 * Verificar si un usuario está participando
 */
EventoGrupalSchema.methods.isParticipante = function (uid) {
  return this.participantes.some((p) => p.uid === uid);
};

/**
 * Verificar si un usuario es el organizador
 */
EventoGrupalSchema.methods.isOrganizador = function (uid) {
  return this.organizadorUid === uid;
};

/**
 * Obtener número de participantes actual
 */
EventoGrupalSchema.methods.getNumParticipantes = function () {
  return this.participantes.length;
};

/**
 * Verificar si hay plazas disponibles
 */
EventoGrupalSchema.methods.hasPlazasDisponibles = function () {
  return this.participantes.length < this.maxParticipantes;
};

/**
 * Cancelar evento (solo organizador)
 */
EventoGrupalSchema.methods.cancelar = function () {
  this.estado = "CANCELADO";
  return { success: true, message: "Evento cancelado" };
};

/**
 * Finalizar evento (solo organizador, después de la fecha)
 */
EventoGrupalSchema.methods.finalizar = function () {
  this.estado = "FINALIZADO";
  return { success: true, message: "Evento finalizado" };
};

// ===========================================
// VIRTUALS
// ===========================================

// Número de participantes (virtual para facilitar consultas)
EventoGrupalSchema.virtual("numParticipantes").get(function () {
  return this.participantes.length;
});

// Plazas disponibles
EventoGrupalSchema.virtual("plazasDisponibles").get(function () {
  return this.maxParticipantes - this.participantes.length;
});

// Configurar para que los virtuals salgan en JSON
EventoGrupalSchema.set("toJSON", { virtuals: true });
EventoGrupalSchema.set("toObject", { virtuals: true });

module.exports = mongoose.model("EventoGrupal", EventoGrupalSchema);