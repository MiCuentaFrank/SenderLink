const Route = require("../models/Route");
const User = require("../models/User");
const axios = require("axios");

// ==========================================
// Crear ruta con imagen automática de Google
// ==========================================

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

    let imagenPortada = "";

    // ============================================================
    // 1️⃣ Tomamos el primer punto de la ruta para buscar la foto
    // ============================================================
    if (puntos && puntos.length > 0) {
      const lat = puntos[0].latitud;
      const lng = puntos[0].longitud;

      try {
        // 2️⃣ Buscamos lugares naturales cercanos
        const searchUrl = `https://maps.googleapis.com/maps/api/place/nearbysearch/json?location=${lat},${lng}&radius=2000&type=tourist_attraction&key=${process.env.GOOGLE_MAPS_KEY}`;

        const searchResponse = await axios.get(searchUrl);

        if (searchResponse.data.results.length > 0) {
          const place = searchResponse.data.results[0];

          // 3️⃣ Si el lugar tiene fotos, usamos la primera
          if (place.photos && place.photos.length > 0) {
            const photoRef = place.photos[0].photo_reference;

            imagenPortada =
             imagenPortada = `https://maps.googleapis.com/maps/api/place/photo?maxwidth=800&photoreference=${photoRef}&key=${process.env.GOOGLE_MAPS_KEY}`;

          }
        }
      } catch (error) {
        console.log("⚠ Error obteniendo imagen de Google:", error.message);
      }
    }

    // Si no se obtuvo imagen, ponemos una por defecto
    if (!imagenPortada) {
      imagenPortada = "https://images.unsplash.com/photo-1501785888041-af3ef285b470";
    }

    // 4️⃣ Guardamos la ruta con la imagen ya añadida
    const newRoute = await Route.create({
      ...req.body,
      imagenPortada
    });

    res.status(201).json({
      ok: true,
      message: "Ruta creada correctamente con imagen automática",
      route: newRoute
    });

  } catch (err) {
    res.status(500).json({ ok: false, message: err.message });
  }
}


// =======================
// Obtener todas las rutas
// =======================
async function getRoutes(req, res) {
  try {
    const { provincia, dificultad, activa } = req.query;

    let filtro = {};
    if (provincia) filtro.provincia = provincia;
    if (dificultad) filtro.dificultad = dificultad;
    if (activa !== undefined) filtro.activa = activa === "true";

    const routes = await Route.find(filtro).sort({ createdAt: -1 });

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

    const route = await Route.findById(id);

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

    const routes = await Route.find({ uid }).sort({ createdAt: -1 });

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

    const updatedRoute = await Route.findByIdAndUpdate(id, req.body, {
      new: true,
      runValidators: true
    });

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
    const totalValoracion =
      route.valoracion * route.numeroValoraciones + valoracion;
    route.numeroValoraciones += 1;
    route.valoracion =
      totalValoracion / route.numeroValoraciones;

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
