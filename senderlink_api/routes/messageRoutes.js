const express = require("express");
const router = express.Router();

const {
  sendMessage,
  getMessages,
  markAsRead,
  getConversations
} = require("../controllers/messageController");

// Enviar mensaje
router.post("/", sendMessage);

// Conversaciones de un usuario
router.get("/conversations/:uid", getConversations);

// Obtener mensajes de un chat
router.get("/:chatId", getMessages);

// Marcar como leído
router.put("/:id/read", markAsRead);

module.exports = router;
