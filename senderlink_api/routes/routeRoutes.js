const express= require("express");
const router = express.Router();

const {
createRoute,
getRoutes,
getRouteById,
getRoutesByUser,
updateRoute,
rateRoute,
deleteRoute
}=require("../controllers/routeController");

// Crear ruta
router.post("/", createRoute);

// Obtener todas las rutas
router.get("/", getRoutes);

// Obtener ruta por ID
router.get("/:id", getRouteById);

// Obtener rutas de un usuario
router.get("/user/:uid", getRoutesByUser);

// Actualizar ruta
router.put("/:id", updateRoute);

// Valorar ruta
router.put("/:id/rate", rateRoute);


// Eliminar
router.delete("/:id", deleteRoute);

module.exports = router;