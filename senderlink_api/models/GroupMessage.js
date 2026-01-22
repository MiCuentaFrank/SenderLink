const mongoose = require("mongoose");

/**
 * ===========================================
 * MODELO GROUP MESSAGE (Mensajes Grupales)
 * ===========================================
 *
 * Un mensaje grupal es un mensaje enviado dentro
 * del chat de un evento grupal.
 *
 * DIFERENCIAS CON Message.js (chat 1-a-1):
 * - No tiene destinatarioUid (es para todo el grupo)
 * - No tiene campo "leido" (todos leen el mismo mensaje)
 * - Incluye nombre y foto del remitente (para mostrar en UI)
 */

const GroupMessageSchema = new mongoose.Schema(
  {
    // 💬 ID DEL CHAT GRUPAL
    // Este chatId viene del campo chatId de EventoGrupal
    chatId: {
      type: String,
      required: true,
      index: true // Importante para consultas rápidas
    },

    // 👤 REMITENTE (quien envía el mensaje)
    senderUid: {
      type: String,
      required: true,
      index: true
    },
    senderName: {
      type: String,
      required: true
    },
    senderPhoto: {
      type: String,
      default: ""
    },

    // 📝 CONTENIDO DEL MENSAJE
    text: {
      type: String,
      required: true,
      trim: true,
      maxlength: 500 // Límite de caracteres
    },

    // 🏷️ TIPO DE MENSAJE (para futuras expansiones)
    type: {
      type: String,
      enum: ["TEXT", "SYSTEM", "IMAGE"],
      default: "TEXT"
    }
  },
  {
    timestamps: true // Crea automáticamente createdAt y updatedAt
  }
);

// ===========================================
// ÍNDICES PARA CONSULTAS EFICIENTES
// ===========================================

// Índice compuesto: permite buscar mensajes de un chat
// ordenados por fecha de forma muy rápida
GroupMessageSchema.index({ chatId: 1, createdAt: -1 });

// ===========================================
// EXPORTAR MODELO
// ===========================================

module.exports = mongoose.model("GroupMessage", GroupMessageSchema);