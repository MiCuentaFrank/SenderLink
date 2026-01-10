const Route = require("../models/Route");
const User = require("../models/User");
const axios = require("axios");


// ==========================================
// 1. Crear ruta (USER_ROUTE)
// ==========================================
async function createRoute(req, res) {
  try {
    const {
      uid,
      name,
      points,
      distanceKm,
      difficulty,
      comunidad,
      provincia,
      startLocality
    } = req.body;

    if (!uid || !name || !points || points.length < 2) {
      return res.status(400).json({ ok: false, message: "Datos insuficientes para crear la ruta" });
    }

    const user = await User.findOne({ uid });
    if (!user) {
      return res.status(404).json({ ok: false, message: "Usuario no encontrado" });
    }

    // GeoJSON start/end
    const startPoint = {
      type: "Point",
      coordinates: [points[0].lng, points[0].lat]
    };

    const endPoint = {
      type: "Point",
      coordinates: [points[points.length - 1].lng, points[points.length - 1].lat]
    };

    // GeoJSON LineString
    const geometry = {
      type: "LineString",
      coordinates: points.map(p => [p.lng, p.lat])
    };

    const coverImage =
      "https://images.unsplash.com/photo-1500530855697-b586d89ba3ee?w=1200&q=80";

    const newRoute = await Route.create({
      uid,

      type: "USER_ROUTE",
      source: "USER",

      name,
      description:
        "Ruta creada por un usuario. La imagen es genérica y no representa necesariamente la localización exacta.",

      coverImage,
      images: [coverImage],

      distanceKm,
      difficulty,

      geometry,
      startPoint,
      endPoint,

      startLocality,
      comunidad,
      provincia,

      featured: false
    });

    res.status(201).json({ ok: true, route: newRoute });
  } catch (err) {
    res.status(500).json({ ok: false, message: err.message });
  }
}





// ==========================================
// 2. Obtener rutas (con filtros + featured + paginación)
// ==========================================
async function getRoutes(req, res) {
  try {
    const {
      parqueNacional,
      provincia,
      startLocality,
      localidad,     // compat
      difficulty,
      dificultad,    // compat
      type,
      featured,
      page = 1,
      limit = 20
    } = req.query;

    const filtro = {};

    // Parque nacional (si lo usas)
    if (parqueNacional) {
      filtro.parqueNacional = { $regex: parqueNacional, $options: "i" };
    }

    if (provincia) {
      filtro.provincia = { $regex: provincia, $options: "i" };
    }

    // Localidad real: startLocality (pero aceptamos "localidad" por compat)
    const loc = startLocality || localidad;
    if (loc) {
      filtro.startLocality = { $regex: loc, $options: "i" };
    }

    // Dificultad real: difficulty (pero aceptamos "dificultad")
    const diff = difficulty || dificultad;
    if (diff) {
      filtro.difficulty = diff;
    }

    if (type) filtro.type = type;

    if (featured !== undefined) {
      // featured="true"/"false"
      if (featured === "true") filtro.featured = true;
      if (featured === "false") filtro.featured = false;
    }

    const pageFinal = parseInt(page, 10);
    const limitFinal = parseInt(limit, 10);
    const skip = (pageFinal - 1) * limitFinal;

    const routes = await Route.find(filtro)
      .sort({ featured: -1, createdAt: -1 })
      .skip(skip)
      .limit(limitFinal);

    const total = await Route.countDocuments(filtro);

    res.json({
      ok: true,
      page: pageFinal,
      limit: limitFinal,
      total,
      pages: Math.ceil(total / limitFinal),
      routes
    });
  } catch (err) {
    res.status(500).json({ ok: false, message: err.message });
  }
}

// ==========================================
// 2.b Obtener rutas destacadas (FEATURED)
// ==========================================
async function getFeaturedRoutes(req, res) {
  try {
    const limit = parseInt(req.query.limit, 10) || 10;

    const routes = await Route.find({ featured: true })
      .sort({ createdAt: -1 })
      .limit(limit);

    res.json({
      ok: true,
      count: routes.length,
      routes
    });

  } catch (err) {
    res.status(500).json({
      ok: false,
      message: err.message
    });
  }
}



// ==========================================
// 3. Obtener ruta por ID
// ==========================================
async function getRouteById(req, res) {
  try {
    const route = await Route.findById(req.params.id);

    if (!route) {
      return res.status(404).json({ ok: false, message: "Ruta no encontrada" });
    }

    res.json({ ok: true, route });
  } catch (err) {
    res.status(500).json({ ok: false, message: err.message });
  }
}

// ==========================================
// 4. Rutas por usuario
// ==========================================
async function getRoutesByUser(req, res) {
  try {
    const routes = await Route.find({ uid: req.params.uid })
      .sort({ createdAt: -1 });

    res.json({ ok: true, count: routes.length, routes });
  } catch (err) {
    res.status(500).json({ ok: false, message: err.message });
  }
}

// ==========================================
// 5. Buscar rutas cercanas
// ==========================================
async function getRoutesNearMe(req, res) {
  try {
    const { lat, lng, radio = 50000, limit } = req.query;

    if (!lat || !lng) {
      return res.status(400).json({
        ok: false,
        message: "lat y lng son obligatorios"
      });
    }

    const limitFinal = parseInt(limit, 10) || 20;

    const routes = await Route.find({
      startPoint: {
        $nearSphere: {
          $geometry: {
            type: "Point",
            coordinates: [Number(lng), Number(lat)]
          },
          $maxDistance: Number(radio)
        }
      }
    }).limit(limitFinal);

    res.json({ ok: true, count: routes.length, routes });

  } catch (err) {
    res.status(500).json({ ok: false, message: err.message });
  }
}

// ==========================================
// 6. Obtener parques nacionales (DISTINCT)
// ==========================================
async function getParques(req, res) {
  try {
    const parques = await Route.distinct("parqueNacional");

    // Limpieza: quitar null/"" y ordenar
    const parquesLimpios = parques
      .filter(p => typeof p === "string" && p.trim().length > 0)
      .sort((a, b) => a.localeCompare(b, "es"));

    res.json({
      ok: true,
      count: parquesLimpios.length,
      parques: parquesLimpios
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
  getRoutesNearMe,
  getParques,
  getFeaturedRoutes
};
