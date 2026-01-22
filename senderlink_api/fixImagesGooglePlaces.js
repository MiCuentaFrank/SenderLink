require("dotenv").config();
const mongoose = require("mongoose");
const axios = require("axios");
const Route = require("./models/Route");

const GOOGLE_API_KEY = process.env.GOOGLE_MAPS_KEY || process.env.GOOGLE_API_KEY;

const MAX_GOOGLE = 100;
const DELAY_MS = 1500;

const PLACE_TYPES = ["tourist_attraction", "natural_feature", "park", "point_of_interest"];
const RADIOS = [5000, 10000];

const UNSPLASH_FALLBACK = [
  "https://images.unsplash.com/photo-1506905925346-21bda4d32df4?w=800&q=80",
  "https://images.unsplash.com/photo-1464822759023-fed622ff2c3b?w=800&q=80",
  "https://images.unsplash.com/photo-1447752875215-b2761acb3c5d?w=800&q=80",
  "https://images.unsplash.com/photo-1470071459604-3b5ec3a7fe05?w=800&q=80",
  "https://images.unsplash.com/photo-1501785888041-af3ef285b470?w=800&q=80"
];

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

function buildPlacesUrl(lat, lng, radius) {
  const types = PLACE_TYPES.join("|");
  return `https://maps.googleapis.com/maps/api/place/nearbysearch/json?location=${lat},${lng}&radius=${radius}&type=${types}&key=${GOOGLE_API_KEY}`;
}

// ✅ Detecta URLs Google Places Photo en varias formas (más robusto)
const isContaminatedUrl = (u) =>
  typeof u === "string" &&
  (
    u.includes("maps.googleapis.com/maps/api/place/photo") ||
    u.includes("photoreference=") ||
    u.includes("&key=")
  );

const isGplacesRef = (u) => typeof u === "string" && u.startsWith("gplaces:");

function getLatLng(ruta) {
  // Caso 1: startPoint clásico
  if (ruta.startPoint && typeof ruta.startPoint.lat === "number" && typeof ruta.startPoint.lng === "number") {
    return { lat: ruta.startPoint.lat, lng: ruta.startPoint.lng };
  }

  // Caso 2: startPointGeo GeoJSON -> coordinates [lng, lat]
  const coords = ruta.startPointGeo?.coordinates;
  if (Array.isArray(coords) && coords.length === 2) {
    const [lng, lat] = coords;
    if (Number.isFinite(lat) && Number.isFinite(lng)) return { lat, lng };
  }

  return null;
}

async function obtenerRefsGoogle(lat, lng) {
  let refs = [];

  for (const radius of RADIOS) {
    try {
      const url = buildPlacesUrl(lat, lng, radius);
      const res = await axios.get(url, { timeout: 8000 });

      if (res.data.status !== "OK") continue;
      if (!res.data.results?.length) continue;

      for (const lugar of res.data.results) {
        if (!lugar.photos) continue;

        for (const foto of lugar.photos) {
          if (!foto.photo_reference) continue;
          refs.push(`gplaces:${foto.photo_reference}`);
          if (refs.length >= 5) break;
        }
        if (refs.length >= 5) break;
      }

      if (refs.length >= 3) break;
    } catch {
      // ignorar
    }
  }

  return refs.length ? refs : null;
}

async function fixImagesGooglePlaces() {
  if (!GOOGLE_API_KEY) {
    console.error("❌ Falta GOOGLE_MAPS_KEY o GOOGLE_API_KEY en el .env");
    process.exit(1);
  }

  await mongoose.connect(process.env.MONGO_URI);
  console.log("🟢 Conectado a MongoDB");

  const rutas = await Route.find({ featured: true }).limit(MAX_GOOGLE);

  console.log(`⭐ Rutas featured encontradas: ${rutas.length}`);
  console.log("🚀 Iniciando FIX seguro (solo contaminadas)...\n");

  // 🔎 DEBUG: muestra 1 ejemplo para ver estructura real
  if (rutas.length) {
    console.log("🔎 Ejemplo ruta[0]:", {
      name: rutas[0].name,
      coverImage: rutas[0].coverImage,
      images0: rutas[0].images?.[0],
      startPoint: rutas[0].startPoint,
      startPointGeo: rutas[0].startPointGeo
    });
  }

  let revisadas = 0;
  let actualizadas = 0;
  let saltadas = 0;
  let sinCoords = 0;
  let conGoogle = 0;
  let sinGoogle = 0;

  for (const ruta of rutas) {
    revisadas++;

    const coverIsBad = isContaminatedUrl(ruta.coverImage);
    const imagesIsBad = Array.isArray(ruta.images) && ruta.images.some(isContaminatedUrl);

    if (!coverIsBad && !imagesIsBad) {
      // ya está limpia o usa unsplash o ya es gplaces
      saltadas++;
      continue;
    }

    // Si ya está en gplaces, no tocar
    if (isGplacesRef(ruta.coverImage)) {
      saltadas++;
      continue;
    }

    const latlng = getLatLng(ruta);
    if (!latlng) {
      console.log(`⚠️ SALTADA (sin coords startPoint/startPointGeo): ${ruta.name}`);
      sinCoords++;
      continue;
    }

    const refs = await obtenerRefsGoogle(latlng.lat, latlng.lng);

    let finalImages = [];
    if (refs) {
      finalImages = [...refs];
      conGoogle++;
      console.log(`🖼️ GOOGLE: ${ruta.name}`);
    } else {
      sinGoogle++;
      console.log(`⚠️ SIN GOOGLE (fallback): ${ruta.name}`);
    }

    // completar mínimo 3
    let i = 0;
    while (finalImages.length < 3) {
      finalImages.push(UNSPLASH_FALLBACK[i % UNSPLASH_FALLBACK.length]);
      i++;
    }

    ruta.coverImage = finalImages[0];
    ruta.images = finalImages.slice(0, 5);
    await ruta.save();

    actualizadas++;
    await sleep(DELAY_MS);
  }

  console.log("\n" + "=".repeat(60));
  console.log("✅ FIX GOOGLE PLACES (SEGURO) FINALIZADO");
  console.log("=".repeat(60));
  console.log(`🔍 Revisadas: ${revisadas}`);
  console.log(`🛠️ Actualizadas (contaminadas): ${actualizadas}`);
  console.log(`✅ Saltadas (ya limpias): ${saltadas}`);
  console.log(`🧭 Sin coordenadas: ${sinCoords}`);
  console.log(`📸 Con refs Google: ${conGoogle}`);
  console.log(`🖼️ Solo fallback: ${sinGoogle}`);
  console.log("=".repeat(60));

  process.exit(0);
}

fixImagesGooglePlaces().catch((err) => {
  console.error("💥 ERROR FATAL:", err);
  process.exit(1);
});
