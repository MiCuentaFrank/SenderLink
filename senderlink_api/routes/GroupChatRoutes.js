const express = require("express");
const router = express.Router();

const {
  sendMessage,
  getMessages
} = require("../controllers/groupChatController");

/**
 * ===========================================
 * RUTAS: CHAT GRUPAL
 * ===========================================
 *
 * BASE URL: /api/group-chat
 */

// ========================================
// GET - Obtener mensajes del chat
// ========================================
// Endpoint: GET /api/group-chat/:chatId/messages?limit=50
// Ejemplo: GET /api/group-chat/evento_123_456/messages
router.get("/:chatId/messages", getMessages);

// ========================================
// POST - Enviar mensaje al chat
// ========================================
// Endpoint: POST /api/group-chat/:chatId/messages
// Body: { uid: "user123", text: "Hola a todos!" }
// Ejemplo: POST /api/group-chat/evento_123_456/messages
router.post("/:chatId/messages", sendMessage);

module.exports = router;