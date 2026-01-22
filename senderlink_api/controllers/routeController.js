// controllers/routeController.js

const Route = require("../models/Route");
const User = require("../models/User");

// ======================================================
// ✅ IMÁGENES: normalizar a proxy (/api/photos/places)
// Soporta:
//  - "gplaces:PHOTO_REF"
//  - "https://maps.googleapis.com/maps/api/place/photo?...photoreference=PHOTO_REF&key=..."
//  - URLs normales (Unsplash, etc.) -> se dejan tal cual
// ======================================================

function buildBaseUrl(req) {
  return `${req.protocol}://${req.get("host")}`;
}

function toProxyUrl(req, photoRef, maxwidth = 1200) {
  const base = buildBaseUrl(req);
  return `${base}/api/photos/places?ref=${encodeURIComponent(photoRef)}&maxwidth=${maxwidth}`;
}

function extractPhotoRefFromGoogleUrl(url) {
  try {
    // Puede venir con https://maps.googleapis.com/...
    const u = new URL(url);
    const ref = u.searchParams.get("photoreference");
    return ref || null;
  } catch {
    return null;
  }
}

function resolveImageUrl(req, img) {
  if (!img || typeof img !== "string") return img;

  // 1) Nuevo formato seguro: gplaces:XXXX
  if (img.startsWith("gplaces:")) {
    const ref = img.replace("gplaces:", "").trim();
    if (!ref) return img;
    return toProxyUrl(req, ref, 1200);
  }

  // 2) Formato antiguo "contaminado": URL directa Google Places Photo
  //    (incluye API key en query normalmente)
  if (img.includes("maps.googleapis.com/maps/api/place/photo")) {
    const ref = extractPhotoRefFromGoogleUrl(img);
    if (!ref) return img; // si no podemos extraer, lo dejamos
    return toProxyUrl(req, ref, 1200);
  }

  // 3) Cualquier otra URL (Unsplash, etc.)
  return img;
}

function resolveRouteImages(req, routeObj) {
  if (!routeObj) return routeObj;

  routeObj.coverImage = resolveImageUrl(req, routeObj.coverImage);

  if (Array.isArray(routeObj.images)) {
    routeObj.images = routeObj.images.map((img) => resolveImageUrl(req, img));
  }

  return routeObj;
}

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
// 2. Obtener rutas (con filtros + paginación)
// ✅ Resuelve imágenes a proxy
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

    let routes = await Route.find(filtro)
      .sort({ featured: -1, createdAt: -1 })
      .skip(skip)
      .limit(limitFinal)
      .lean();

    // ✅ Resolver imágenes aquí
    routes = routes.map((r) => resolveRouteImages(req, r));

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
// 2.b Featured paginado
// ✅ Resuelve imágenes a proxy
// ==========================================
async function getFeaturedRoutes(req, res) {
  try {
    const page = parseInt(req.query.page, 10) || 1;
    const limit = parseInt(req.query.limit, 10) || 20;
    const skip = (page - 1) * limit;

    let routes = await Route.find({ featured: true })
      .select(
        "_id type source name coverImage images distanceKm difficulty " +
        "startLocality comunidad provincia featured createdAt"
      )
      .sort({ createdAt: -1 })
      .skip(skip)
      .limit(limit)
      .lean();

    routes = routes.map((r) => resolveRouteImages(req, r));

    const total = await Route.countDocuments({ featured: true });

    res.json({
      ok: true,
      page,
      limit,
      total,
      pages: Math.ceil(total / limit),
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
// 2.c Obtener TODAS las rutas para el mapa
// ✅ Resuelve imágenes a proxy (por si el mapa usa coverImage)
// ==========================================
async function getAllRoutesForMap(req, res) {
  try {
    const { difficulty, page = 1, limit = 100 } = req.query;

    const filtro = {};
    if (difficulty) filtro.difficulty = difficulty;

    const pageFinal = parseInt(page, 10);
    const limitFinal = parseInt(limit, 10);
    const skip = (pageFinal - 1) * limitFinal;

    let routes = await Route.find(filtro)
      .sort({ createdAt: -1 })
      .skip(skip)
      .limit(limitFinal)
      .lean();

    routes = routes.map((r) => resolveRouteImages(req, r));

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
// ✅ Resuelve imágenes a proxy
// ==========================================
async function getRouteById(req, res) {
  try {
    const routeDoc = await Route.findById(req.params.id);
    if (!routeDoc) {
      return res.status(404).json({ ok: false, message: "Ruta no encontrada" });
    }

    const route = routeDoc.toObject();
    resolveRouteImages(req, route);

    res.json({ ok: true, route });
  } catch (err) {
    res.status(500).json({ ok: false, message: err.message });
  }
}

// ==========================================
// 4. Rutas por usuario
// ✅ Resuelve imágenes a proxy
// ==========================================
async function getRoutesByUser(req, res) {
  try {
    let routes = await Route.find({ uid: req.params.uid })
      .sort({ createdAt: -1 })
      .lean();

    routes = routes.map((r) => resolveRouteImages(req, r));

    res.json({ ok: true, count: routes.length, routes });
  } catch (err) {
    res.status(500).json({ ok: false, message: err.message });
  }
}

// ==========================================
// 5. Rutas cercanas (GEO 2dsphere)
// ✅ Resuelve imágenes a proxy
// ==========================================
async function getRoutesNearMe(req, res) {
  try {
    const { lat, lng, radio = 50000, limit = 100 } = req.query;

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

    const nearbyRoutes = await Route.find({
      startPointGeo: {
        $near: {
          $geometry: {
            type: "Point",
            coordinates: [lngNum, latNum]
          },
          $maxDistance: radioFinal
        }
      }
    })
      .select(
        "_id type source name description coverImage images distanceKm durationMin difficulty startLocality comunidad provincia parqueNacional featured startPointGeo endPointGeo code externalId uid createdAt updatedAt"
      )
      .limit(limitFinal)
      .lean();

    const routesForClient = nearbyRoutes.map((r) => {
      const out = { ...r };

      // Android espera startPoint/endPoint
      out.startPoint = out.startPointGeo || null;
      out.endPoint = out.endPointGeo || null;

      delete out.startPointGeo;
      delete out.endPointGeo;

      // ✅ Resolver imágenes también aquí
      resolveRouteImages(req, out);

      return out;
    });

    console.log(`✅ Encontradas: ${routesForClient.length} rutas`);

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
