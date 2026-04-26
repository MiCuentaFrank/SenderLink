// controllers/routeController.js

const Route = require("../models/Route");
const User = require("../models/User");
const { escapeRegex } = require("../utils/sanitize");

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

  // 3) URL local del servidor (ruta relativa)
  if (img.startsWith("/uploads/")) {
    return `${buildBaseUrl(req)}${img}`;
  }

  // 4) URL http:// propia del servidor → forzar https://
  const host = req.get("host");
  if (img.startsWith(`http://${host}`)) {
    return img.replace("http://", "https://");
  }

  // 5) Cualquier otra URL (Unsplash, Firebase Storage, etc.)
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

    // Auth: solo puedes crear rutas con tu propio uid
    if (req.uid !== uid) {
      return res.status(403).json({
        ok: false,
        message: "No autorizado: no puedes crear rutas en nombre de otro usuario"
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

    // Validación numérica y de rango geográfico
    function isValidCoord(lng, lat) {
      return Number.isFinite(lng) && Number.isFinite(lat) &&
        lat >= -90 && lat <= 90 && lng >= -180 && lng <= 180;
    }

    const startValid = isValidCoord(startPointGeo.coordinates[0], startPointGeo.coordinates[1]);
    const endValid = isValidCoord(endPointGeo.coordinates[0], endPointGeo.coordinates[1]);

    if (!startValid || !endValid) {
      return res.status(400).json({
        ok: false,
        message: "Coordenadas inválidas (lat: -90 a 90, lng: -180 a 180)"
      });
    }

    // Limitar cantidad de puntos para evitar payloads excesivos
    if (points.length > 10000) {
      return res.status(400).json({
        ok: false,
        message: "Demasiados puntos en la ruta (máximo 10000)"
      });
    }

    // GeoJSON LineString con validación de cada punto
    const coordinates = [];
    for (const p of points) {
      const lng = Number(p.lng);
      const lat = Number(p.lat);
      if (!isValidCoord(lng, lat)) {
        return res.status(400).json({
          ok: false,
          message: "Coordenada inválida encontrada en la ruta"
        });
      }
      coordinates.push([lng, lat]);
    }

    const geometry = {
      type: "LineString",
      coordinates
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
    console.error(err.message);
    res.status(500).json({ ok: false, message: "Error interno" });
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
      filtro.parqueNacional = { $regex: escapeRegex(parqueNacional), $options: "i" };
    }

    if (provincia) {
      filtro.provincia = { $regex: escapeRegex(provincia), $options: "i" };
    }

    const loc = startLocality || localidad;
    if (loc) {
      filtro.startLocality = { $regex: escapeRegex(loc), $options: "i" };
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

    const pageFinal = Math.max(1, parseInt(page, 10) || 1);
    const limitFinal = Math.min(Math.max(1, parseInt(limit, 10) || 20), 200);
    const skip = (pageFinal - 1) * limitFinal;

    let routes = await Route.find(filtro)
      .select(
        "_id type source name description coverImage images distanceKm durationMin difficulty " +
        "startLocality comunidad provincia parqueNacional featured startPoint endPoint " +
        "startPointGeo endPointGeo code externalId uid createdAt updatedAt"
      )
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
    console.error(err.message);
    res.status(500).json({ ok: false, message: "Error interno" });
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
    console.error(err.message);
    res.status(500).json({
      ok: false,
      message: "Error interno"
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

    const pageFinal = Math.max(1, parseInt(page, 10) || 1);
    const limitFinal = Math.min(Math.max(1, parseInt(limit, 10) || 100), 200);
    const skip = (pageFinal - 1) * limitFinal;

    let routes = await Route.find(filtro)
      .select(
        "_id type source name description coverImage images distanceKm durationMin difficulty " +
        "startLocality comunidad provincia parqueNacional featured startPoint endPoint " +
        "startPointGeo endPointGeo code externalId uid createdAt updatedAt"
      )
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
    console.error(err.message);
    res.status(500).json({ ok: false, message: "Error interno" });
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
    console.error(err.message);
    res.status(500).json({ ok: false, message: "Error interno" });
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
    console.error(err.message);
    res.status(500).json({ ok: false, message: "Error interno" });
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
    console.error("Error en getRoutesNearMe:", err.message);
    return res.status(500).json({ ok: false, message: "Error interno" });
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
    console.error(err.message);
    res.status(500).json({ ok: false, message: "Error interno" });
  }
}

// ==========================================
// 7. Registrar compleción de ruta
// ==========================================
const XP_PER_ROUTE = 50;
const RANK_TITLES = ["Explorer", "Hiker", "Trekker", "Mountaineer", "Summit Master"];

function getRankTitle(level) {
  return RANK_TITLES[Math.min(level - 1, RANK_TITLES.length - 1)];
}

async function addCompletion(req, res) {
  try {
    const routeId = req.params.id;
    const { uid, durationMin } = req.body;

    if (!uid || !routeId) {
      return res.status(400).json({ ok: false, message: "uid y routeId son obligatorios" });
    }

    if (req.uid !== uid) {
      return res.status(403).json({ ok: false, message: "No autorizado" });
    }

    const user = await User.findOne({ uid });
    if (!user) {
      return res.status(404).json({ ok: false, message: "Usuario no encontrado" });
    }

    // Evitar duplicados en las últimas 24h
    const oneDayAgo = new Date(Date.now() - 24 * 60 * 60 * 1000);
    const alreadyDone = user.completedRoutes?.some(
      (c) => c.routeId === routeId && new Date(c.completedAt) > oneDayAgo
    );
    if (alreadyDone) {
      return res.json({
        ok: true,
        message: "Ruta ya completada hoy",
        xpGained: 0,
        newXp: user.progreso.xp,
        newLevel: user.progreso.level,
        newRankTitle: user.progreso.rankTitle
      });
    }

    // Sumar XP y calcular nivel
    const newXp = (user.progreso.xp || 0) + XP_PER_ROUTE;
    const newLevel = Math.floor(newXp / 100) + 1;
    const newRankTitle = getRankTitle(newLevel);

    user.completedRoutes.push({ routeId, durationMin: durationMin || 0, xpGained: XP_PER_ROUTE });
    user.progreso.xp = newXp;
    user.progreso.level = newLevel;
    user.progreso.rankTitle = newRankTitle;
    user.stats.routesCompleted = (user.stats.routesCompleted || 0) + 1;

    await user.save();

    res.json({
      ok: true,
      xpGained: XP_PER_ROUTE,
      newXp,
      newLevel,
      newRankTitle
    });
  } catch (err) {
    console.error(err.message);
    res.status(500).json({ ok: false, message: "Error interno" });
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
  getAllRoutesForMap,
  addCompletion
};
