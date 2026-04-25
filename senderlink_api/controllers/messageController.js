const Message = require("../models/Message");
const User = require("../models/User");
const { sanitizeText } = require("../utils/sanitize");

// Enviar mensaje
async function sendMessage(req, res) {
  try {
    const { chatId, remitenteUid, destinatarioUid, texto } = req.body;

    if (!chatId || !remitenteUid || !destinatarioUid || !texto) {
      return res.status(400).json({
        ok: false,
        message: "Faltan campos obligatorios"
      });
    }

    // Auth: solo el remitente puede enviar mensajes en su nombre
    if (req.uid !== remitenteUid) {
      return res.status(403).json({
        ok: false,
        message: "No autorizado: no puedes enviar mensajes en nombre de otro usuario"
      });
    }

    const textoSanitizado = sanitizeText(texto, 2000);
    if (textoSanitizado.trim().length === 0) {
      return res.status(400).json({ ok: false, message: "El mensaje no puede estar vacío" });
    }

    const newMessage = await Message.create({
      chatId,
      remitenteUid,
      destinatarioUid,
      texto: textoSanitizado
    });

    res.status(201).json({
      ok: true,
      message: "Mensaje enviado",
      data: newMessage
    });

  } catch (err) {
    console.error("sendMessage error:", err.message);
    res.status(500).json({
      ok: false,
      message: "Error interno al enviar mensaje"
    });
  }
}

// Obtener mensajes por chatId
async function getMessages(req, res) {
  try {
    const { chatId } = req.params;

    // Auth: verificar que el usuario pertenece al chat por el formato del chatId (uid1_uid2)
    const parts = chatId.split("_");
    if (!parts.includes(req.uid)) {
      return res.status(403).json({
        ok: false,
        message: "No autorizado: no perteneces a este chat"
      });
    }

    const messages = await Message.find({ chatId }).sort({ createdAt: 1 });

    res.json({
      ok: true,
      count: messages.length,
      messages
    });

  } catch (err) {
    console.error("getMessages error:", err.message);
    res.status(500).json({
      ok: false,
      message: "Error interno al obtener mensajes"
    });
  }
}

// Marcar como leído
async function markAsRead(req, res) {
  try {
    const { id } = req.params;

    const message = await Message.findById(id);

    if (!message) {
      return res.status(404).json({
        ok: false,
        message: "Mensaje no encontrado"
      });
    }

    // Auth: solo el destinatario puede marcar como leído
    if (req.uid !== message.destinatarioUid) {
      return res.status(403).json({
        ok: false,
        message: "No autorizado"
      });
    }

    message.leido = true;
    message.fechaLectura = new Date();

    await message.save();

    res.json({
      ok: true,
      message: "Mensaje marcado como leído",
      data: message
    });

  } catch (err) {
    console.error("markAsRead error:", err.message);
    res.status(500).json({
      ok: false,
      message: "Error interno"
    });
  }
}

// Obtener conversaciones de un usuario (último mensaje por chatId)
async function getConversations(req, res) {
  try {
    const { uid } = req.params;

    if (!uid) {
      return res.status(400).json({ ok: false, message: "uid requerido" });
    }

    // Auth: solo puedes ver tus propias conversaciones
    if (req.uid !== uid) {
      return res.status(403).json({
        ok: false,
        message: "No autorizado: no puedes ver conversaciones de otro usuario"
      });
    }

    // Aggregation: obtener solo el último mensaje por chatId (evita cargar todos en memoria)
    const latestMessages = await Message.aggregate([
      { $match: { $or: [{ remitenteUid: uid }, { destinatarioUid: uid }] } },
      { $sort: { createdAt: -1 } },
      { $group: { _id: "$chatId", msg: { $first: "$$ROOT" } } },
      { $replaceRoot: { newRoot: "$msg" } },
      { $sort: { createdAt: -1 } }
    ]);

    // Obtener todos los otros UIDs de una vez (evita N+1 queries)
    const otherUids = latestMessages.map(msg =>
      msg.remitenteUid === uid ? msg.destinatarioUid : msg.remitenteUid
    );
    const otherUsers = await User.find({ uid: { $in: otherUids } })
      .select("uid nombre foto").lean();
    const usersMap = {};
    for (const u of otherUsers) { usersMap[u.uid] = u; }

    const conversations = latestMessages.map(msg => {
      const otherUid = msg.remitenteUid === uid ? msg.destinatarioUid : msg.remitenteUid;
      const otherUser = usersMap[otherUid];
      return {
        chatId: msg.chatId,
        lastMessage: msg.texto,
        lastMessageAt: msg.createdAt,
        otherUid,
        otherNombre: otherUser?.nombre || "Usuario",
        otherFoto: otherUser?.foto || ""
      };
    });

    // Ordenar por más reciente
    conversations.sort((a, b) => new Date(b.lastMessageAt) - new Date(a.lastMessageAt));

    res.json({ ok: true, data: conversations });

  } catch (err) {
    console.error("getConversations error:", err.message);
    res.status(500).json({ ok: false, message: "Error interno" });
  }
}

module.exports = {
  sendMessage,
  getMessages,
  markAsRead,
  getConversations
};
