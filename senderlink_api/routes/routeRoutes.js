const express = require("express");
const router = express.Router();

const {
  createRoute,
  getRoutes,
  getFeaturedRoutes,
  getAllRoutesForMap,
  getRouteById,
  getRoutesByUser,
  getRoutesNearMe,
  getParques
} = require("../controllers/routeController");

// ===============================
// POST
// ===============================
router.post("/", createRoute);

// ===============================
// GET ESPECÍFICAS
// ===============================

// ⭐ Rutas destacadas
router.get("/featured", getFeaturedRoutes);

// 🗺️ Todas las rutas para el mapa (SIN filtro featured)
router.get("/map", getAllRoutesForMap);

// 🌲 Parques nacionales
router.get("/parques", getParques);

// 📍 Rutas cercanas
router.get("/cerca", getRoutesNearMe);

// 👤 Rutas por usuario
router.get("/user/:uid", getRoutesByUser);

// ===============================
// GET GENERALES
// ===============================

// 📋 Todas las rutas (con filtros)
router.get("/", getRoutes);

// 🆔 Ruta por ID (SIEMPRE LA ÚLTIMA)
router.get("/:id", getRouteById);

module.exports = router;