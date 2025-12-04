const express = require("express");
const router = express.Router();

const {
  sendMessage,
  getMessages,
  markAsRead
} = require("../controllers/messageController");

// Enviar mensaje
router.post("/", sendMessage);

// Obtener mensajes de un chat
router.get("/:chatId", getMessages);

// Marcar como leído
router.put("/:id/read", markAsRead);

module.exports = router;
