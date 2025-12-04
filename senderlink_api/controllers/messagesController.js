const Message = require("../models/Message");
const User = require("../models/User");

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

    const newMessage = await Message.create({
      chatId,
      remitenteUid,
      destinatarioUid,
      texto
    });

    res.status(201).json({
      ok: true,
      message: "Mensaje enviado",
      data: newMessage
    });

  } catch (err) {
    res.status(500).json({
      ok: false,
      message: err.message
    });
  }
}

// Obtener mensajes por chatId
async function getMessages(req, res) {
  try {
    const { chatId } = req.params;

    const messages = await Message.find({ chatId }).sort({ createdAt: 1 });

    res.json({
      ok: true,
      count: messages.length,
      messages
    });

  } catch (err) {
    res.status(500).json({
      ok: false,
      message: err.message
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

    message.leido = true;
    message.fechaLectura = new Date();

    await message.save();

    res.json({
      ok: true,
      message: "Mensaje marcado como leído",
      data: message
    });

  } catch (err) {
    res.status(500).json({
      ok: false,
      message: err.message
    });
  }
}

module.exports = {
  sendMessage,
  getMessages,
  markAsRead
};
