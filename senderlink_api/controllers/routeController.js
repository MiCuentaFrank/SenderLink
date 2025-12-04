const Route = require("../models/Route");
const User = require("../models/User");

// Crear ruta
async function createRoute(req, res) {
  try {
    const { uid, nombre, descripcion, dificultad, distancia, duracion, puntos } = req.body;

    // Validaciones
    if (!uid || !nombre || !distancia || !duracion || !puntos) {
      return res.status(400).json({
        ok: false,
        message: "Faltan campos obligatorios (uid, nombre, distancia, duracion, puntos)"
      });
    }

    // Verificar que el usuario existe
    const user = await User.findOne({ uid });
    if (!user) {
      return res.status(404).json({
        ok: false,
        message: "Usuario no encontrado"
      });
    }

    const newRoute = await Route.create(req.body);

    res.status(201).json({
      ok: true,
      message: "Ruta creada correctamente",
      route: newRoute
    });
  } catch (err) {
    res.status(500).json({ ok: false, message: err.message });
  }
}

// Obtener todas las rutas
async function getRoutes(req, res) {
  try {
    const { provincia, dificultad, activa } = req.query;

    let filtro = {};
    if (provincia) filtro.provincia = provincia;
    if (dificultad) filtro.dificultad = dificultad;
    if (activa !== undefined) filtro.activa = activa === 'true';

    const routes = await Route.find(filtro)
      .populate('uid', 'nombre foto email')
      .sort({ createdAt: -1 });

    res.json({
      ok: true,
      count: routes.length,
      routes
    });
  } catch (err) {
    res.status(500).json({ ok: false, message: err.message });
  }
}

// Obtener ruta por ID
async function getRouteById(req, res) {
  try {
    const { id } = req.params;

    const route = await Route.findById(id)
      .populate('uid', 'nombre foto email');

    if (!route) {
      return res.status(404).json({
        ok: false,
        message: "Ruta no encontrada"
      });
    }

    res.json({
      ok: true,
      route
    });
  } catch (err) {
    res.status(500).json({ ok: false, message: err.message });
  }
}

// Obtener rutas de un usuario
async function getRoutesByUser(req, res) {
  try {
    const { uid } = req.params;

    const routes = await Route.find({ uid })
      .sort({ createdAt: -1 });

    res.json({
      ok: true,
      count: routes.length,
      routes
    });
  } catch (err) {
    res.status(500).json({ ok: false, message: err.message });
  }
}

// Actualizar ruta
async function updateRoute(req, res) {
  try {
    const { id } = req.params;

    const updatedRoute = await Route.findByIdAndUpdate(
      id,
      req.body,
      { new: true, runValidators: true }
    );

    if (!updatedRoute) {
      return res.status(404).json({
        ok: false,
        message: "Ruta no encontrada"
      });
    }

    res.json({
      ok: true,
      message: "Ruta actualizada correctamente",
      route: updatedRoute
    });
  } catch (err) {
    res.status(500).json({ ok: false, message: err.message });
  }
}

// Valorar ruta
async function rateRoute(req, res) {
  try {
    const { id } = req.params;
    const { valoracion } = req.body;

    if (valoracion < 0 || valoracion > 5) {
      return res.status(400).json({
        ok: false,
        message: "La valoración debe estar entre 0 y 5"
      });
    }

    const route = await Route.findById(id);
    if (!route) {
      return res.status(404).json({
        ok: false,
        message: "Ruta no encontrada"
      });
    }

    // Calcular nueva valoración media
    const totalValoracion = (route.valoracion * route.numeroValoraciones) + valoracion;
    route.numeroValoraciones += 1;
    route.valoracion = totalValoracion / route.numeroValoraciones;

    await route.save();

    res.json({
      ok: true,
      message: "Valoración registrada",
      route
    });
  } catch (err) {
    res.status(500).json({ ok: false, message: err.message });
  }
}

// Eliminar ruta
async function deleteRoute(req, res) {
  try {
    const { id } = req.params;

    const deletedRoute = await Route.findByIdAndDelete(id);

    if (!deletedRoute) {
      return res.status(404).json({
        ok: false,
        message: "Ruta no encontrada"
      });
    }

    res.json({
      ok: true,
      message: "Ruta eliminada correctamente"
    });
  } catch (err) {
    res.status(500).json({ ok: false, message: err.message });
  }
}

module.exports = {
  createRoute,
  getRoutes,
  getRouteById,
  getRoutesByUser,
  updateRoute,
  rateRoute,
  deleteRoute
};