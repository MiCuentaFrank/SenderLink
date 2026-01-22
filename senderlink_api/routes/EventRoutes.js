const express = require("express");
const router = express.Router();

const {
  createEvento,
  listEventos,
  getEventoById,
  getEventosByUser,
  getEventosParticipando,
  joinEvento,
  leaveEvento,
  cancelEvento,
  finishEvento,
  updateEvento
} = require("../controllers/eventController");

// ===============================
// POST - Crear evento
// ===============================
router.post("/", createEvento);

// ===============================
// GET ESPECÍFICAS
// ===============================

// 👤 Eventos organizados por un usuario
router.get("/user/:uid", getEventosByUser);

// 👥 Eventos en los que participa un usuario
router.get("/participating/:uid", getEventosParticipando);

// ===============================
// GET GENERALES
// ===============================

// 📋 Listar todos los eventos (con filtros)
router.get("/", listEventos);

// 🆔 Evento por ID (SIEMPRE LA ÚLTIMA GET)
router.get("/:id", getEventoById);

// ===============================
// POST - Acciones sobre eventos
// ===============================

// 👍 Unirse a un evento
router.post("/:id/join", joinEvento);

// 👎 Salir de un evento
router.post("/:id/leave", leaveEvento);

// ❌ Cancelar evento (solo organizador)
router.post("/:id/cancel", cancelEvento);

// ✅ Finalizar evento (solo organizador)
router.post("/:id/finish", finishEvento);

// ===============================
// PUT - Actualizar evento
// ===============================

// ✏️ Actualizar evento (solo organizador)
router.put("/:id", updateEvento);

module.exports = router;