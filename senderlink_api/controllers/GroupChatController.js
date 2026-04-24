const GroupMessage = require("../models/GroupMessage");
const User = require("../models/User");
const EventoGrupal = require("../models/EventoGrupal");

/**
 * ===========================================
 * CONTROLADOR: CHAT GRUPAL
 * ===========================================
 *
 * Gestiona los mensajes de los chats grupales de eventos.
 */

// ========================================
// HELPERS: Respuestas estandarizadas
// ========================================
function ok(res, data, message = "OK") {
  return res.json({ ok: true, message, data });
}

function fail(res, status, message) {
  return res.status(status).json({ ok: false, message });
}

// ========================================
// 1. ENVIAR MENSAJE AL CHAT GRUPAL
// POST /api/group-chat/:chatId/messages
// Body: { uid, text }
// ========================================
async function sendMessage(req, res) {
  try {
    const { chatId } = req.params;
    const { uid, text } = req.body;

    // 1️⃣ VALIDACIONES BÁSICAS
    if (!chatId) {
      return fail(res, 400, "chatId es obligatorio");
    }

    if (!uid || !text) {
      return fail(res, 400, "uid y text son obligatorios");
    }

    // Auth: solo puedes enviar mensajes como tú mismo
    if (req.uid !== uid) {
      return fail(res, 403, "No autorizado: no puedes enviar mensajes en nombre de otro usuario");
    }

    const textTrimmed = text.trim();
    if (textTrimmed.length === 0) {
      return fail(res, 400, "El mensaje no puede estar vacío");
    }

    if (textTrimmed.length > 500) {
      return fail(res, 400, "El mensaje no puede superar 500 caracteres");
    }

    // 2️⃣ VERIFICAR QUE EL EVENTO (CHAT) EXISTE
    const evento = await EventoGrupal.findOne({ chatId });
    if (!evento) {
      return fail(res, 404, "Chat no encontrado");
    }

    // 3️⃣ VERIFICAR QUE EL EVENTO SIGUE ACTIVO
    if (evento.estado === "FINALIZADO" || evento.estado === "CANCELADO") {
      return fail(res, 403, "No se pueden enviar mensajes en un evento finalizado o cancelado");
    }

    // 4️⃣ VERIFICAR QUE EL USUARIO ES PARTICIPANTE O ORGANIZADOR
    const isParticipante = evento.isParticipante(uid);
    const isOrganizador = evento.isOrganizador(uid);

    if (!isParticipante && !isOrganizador) {
      return fail(res, 403, "Solo los participantes pueden enviar mensajes");
    }

    // 5️⃣ OBTENER INFORMACIÓN DEL USUARIO DESDE MONGODB
    const user = await User.findOne({ uid }).lean();
    if (!user) {
      return fail(res, 404, "Usuario no encontrado");
    }

    // 6️⃣ CREAR EL MENSAJE EN LA BASE DE DATOS
    const message = await GroupMessage.create({
      chatId,
      senderUid: uid,
      senderName: user.nombre || "Usuario",
      senderPhoto: user.foto || "",
      text: textTrimmed,
      type: "TEXT"
    });

    console.log(`Mensaje enviado al chat ${chatId}`);

    return ok(res, message, "Mensaje enviado correctamente");

  } catch (error) {
    console.error("Error en sendMessage:", error);
    return fail(res, 500, "Error interno al enviar mensaje");
  }
}

// ========================================
// 2. OBTENER MENSAJES DEL CHAT GRUPAL
// GET /api/group-chat/:chatId/messages?limit=50
// ========================================
async function getMessages(req, res) {
  try {
    const { chatId } = req.params;
    const limit = Math.min(parseInt(req.query.limit || "50", 10), 100);

    // 1️⃣ VALIDAR CHAT ID
    if (!chatId) {
      return fail(res, 400, "chatId es obligatorio");
    }

    // 2️⃣ VERIFICAR QUE EL EVENTO (CHAT) EXISTE
    const evento = await EventoGrupal.findOne({ chatId });
    if (!evento) {
      return fail(res, 404, "Chat no encontrado");
    }

    // 3️⃣ VERIFICAR QUE EL USUARIO ES PARTICIPANTE O ORGANIZADOR
    const isParticipante = (evento.participantes || []).some(p => p.uid === req.uid);
    const isOrganizador = evento.organizadorUid === req.uid;
    if (!isParticipante && !isOrganizador) {
      return fail(res, 403, "No autorizado: no perteneces a este chat");
    }

    // 4️⃣ OBTENER MENSAJES ORDENADOS POR FECHA (MÁS ANTIGUOS PRIMERO)
    const messages = await GroupMessage.find({ chatId })
      .sort({ createdAt: 1 }) // Orden cronológico ascendente
      .limit(limit)
      .lean();

    console.log(`Obtenidos ${messages.length} mensajes del chat ${chatId}`);

    return ok(res, messages);

  } catch (error) {
    console.error("Error en getMessages:", error);
    return fail(res, 500, "Error interno al obtener mensajes");
  }
}

// ========================================
// EXPORTAR FUNCIONES
// ========================================
module.exports = {
  sendMessage,
  getMessages
};