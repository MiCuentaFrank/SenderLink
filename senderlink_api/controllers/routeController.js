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

    if (!uid || !name || !Array.isArray(points) || points.length < 2) {
      return res.status(400).json({
        ok: false,
        message: "Datos insuficientes para crear la ruta"
      });
    }

    const user = await User.findOne({ uid });
    if (!user) {
      return res.status(404).json({ ok: false, message: "Usuario no encontrado" });
    }

    // ✅ GeoJSON start/end para el esquema nuevo
    const startPointGeo = {
      type: "Point",
      coordinates: [Number(points[0].lng), Number(points[0].lat)]
    };

    const last = points[points.length - 1];
    const endPointGeo = {
      type: "Point",
      coordinates: [Number(last.lng), Number(last.lat)]
    };

    // Validación básica numérica (evita NaN silenciosos)
    const bad =
      startPointGeo.coordinates.some((n) => Number.isNaN(n)) ||
      endPointGeo.coordinates.some((n) => Number.isNaN(n));

    if (bad) {
      return res.status(400).json({
        ok: false,
        message: "Coordenadas inválidas en points (lat/lng deben ser números)"
      });
    }

    // GeoJSON LineString
    const geometry = {
      type: "LineString",
      coordinates: points.map((p) => [Number(p.lng), Number(p.lat)])
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

      // ✅ IMPORTANTÍSIMO
      startPointGeo,
      endPointGeo,

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
      localidad,
      difficulty,
      dificultad,
      type,
      featured,
      page = 1,
      limit = 20
    } = req.query;

    const filtro = {};

    if (parqueNacional) {
      filtro.parqueNacional = { $regex: parqueNacional, $options: "i" };
    }

    if (provincia) {
      filtro.provincia = { $regex: provincia, $options: "i" };
    }

    const loc = startLocality || localidad;
    if (loc) {
      filtro.startLocality = { $regex: loc, $options: "i" };
    }

    const diff = difficulty || dificultad;
    if (diff) {
      filtro.difficulty = diff;
    }

    if (type) filtro.type = type;

    if (featured !== undefined) {
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
// 2.c Obtener TODAS las rutas para el mapa (sin filtro featured)
// ==========================================
async function getAllRoutesForMap(req, res) {
  try {
    const { difficulty, page = 1, limit = 100 } = req.query;

    const filtro = {};
    if (difficulty) filtro.difficulty = difficulty;

    const pageFinal = parseInt(page, 10);
    const limitFinal = parseInt(limit, 10);
    const skip = (pageFinal - 1) * limitFinal;

    const routes = await Route.find(filtro)
      .sort({ createdAt: -1 })
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
    const routes = await Route.find({ uid: req.params.uid }).sort({ createdAt: -1 });
    res.json({ ok: true, count: routes.length, routes });
  } catch (err) {
    res.status(500).json({ ok: false, message: err.message });
  }
}

// ==========================================
// 5. Rutas cercanas (GEO 2dsphere)
// - Busca por startPointGeo (DB)
// - Responde con startPoint/endPoint (Android)
// - NO envía geometry (evita crash Gson + reduce payload)
// ==========================================
async function getRoutesNearMe(req, res) {
  try {
    const { lat, lng, radio = 50000, limit = 100 } = req.query;

    // 1) Validación básica
    if (lat == null || lng == null) {
      return res.status(400).json({
        ok: false,
        message: "lat y lng son obligatorios"
      });
    }

    const latNum = Number(lat);
    const lngNum = Number(lng);
    const radioFinal = parseInt(radio, 10);
    const limitFinal = parseInt(limit, 10) || 100;

    if (!Number.isFinite(latNum) || !Number.isFinite(lngNum) || !Number.isFinite(radioFinal)) {
      return res.status(400).json({
        ok: false,
        message: "lat/lng/radio inválidos (deben ser números)"
      });
    }

    console.log(
      `\n📍 Buscando rutas cercanas (startPointGeo): [${lngNum}, ${latNum}], radio=${radioFinal}m, limit=${limitFinal}`
    );

    // 2) Query GEO (usamos el campo indexado startPointGeo)
    const nearbyRoutes = await Route.find({
      startPointGeo: {
        $near: {
          $geometry: {
            type: "Point",
            coordinates: [lngNum, latNum] // [lng, lat]
          },
          $maxDistance: radioFinal
        }
      }
    })
      // ✅ NO mandamos geometry. Solo lo necesario para mapa/lista.
      .select(
        "_id type source name description coverImage images distanceKm durationMin difficulty startLocality comunidad provincia parqueNacional featured startPointGeo endPointGeo code externalId uid createdAt updatedAt"
      )
      .limit(limitFinal)
      .lean(); // ✅ para poder transformar objetos sin doc mongoose

    // 3) Transformación para Android: startPoint/endPoint (y eliminamos startPointGeo/endPointGeo)
    const routesForClient = nearbyRoutes.map((r) => {
      const out = { ...r };

      // Android espera startPoint/endPoint
      out.startPoint = out.startPointGeo || null;
      out.endPoint = out.endPointGeo || null;

      // No enviamos los campos nuevos al cliente
      delete out.startPointGeo;
      delete out.endPointGeo;

      return out;
    });

    console.log(`✅ Encontradas: ${routesForClient.length} rutas`);

    // 4) Respuesta
    return res.json({
      ok: true,
      count: routesForClient.length,
      routes: routesForClient
    });
  } catch (err) {
    console.error("❌ Error en getRoutesNearMe:", err);
    return res.status(500).json({ ok: false, message: err.message });
  }
}



// ==========================================
// 6. Obtener parques nacionales (DISTINCT)
// ==========================================
async function getParques(req, res) {
  try {
    const parques = await Route.distinct("parqueNacional");

    const parquesLimpios = parques
      .filter((p) => typeof p === "string" && p.trim().length > 0)
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
  getFeaturedRoutes,
  getAllRoutesForMap
};
