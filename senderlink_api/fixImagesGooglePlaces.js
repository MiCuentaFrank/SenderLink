require("dotenv").config();
const mongoose = require("mongoose");
const axios = require("axios");
const Route = require("./models/Route");

const GOOGLE_API_KEY = process.env.GOOGLE_MAPS_KEY;

// 🔒 Límite
const MAX_GOOGLE = 100;

// ⏱️ Rate limit seguro
const DELAY_MS = 1500;

// 📍 Tipos de lugar
const PLACE_TYPES = [
  "tourist_attraction",
  "natural_feature",
  "park",
  "point_of_interest"
];

// 📍 Radios progresivos
const RADIOS = [5000, 10000];

// 🖼️ Fallback Unsplash
const UNSPLASH_FALLBACK = [
  "https://images.unsplash.com/photo-1506905925346-21bda4d32df4?w=800&q=80",
  "https://images.unsplash.com/photo-1464822759023-fed622ff2c3b?w=800&q=80",
  "https://images.unsplash.com/photo-1447752875215-b2761acb3c5d?w=800&q=80",
  "https://images.unsplash.com/photo-1470071459604-3b5ec3a7fe05?w=800&q=80",
  "https://images.unsplash.com/photo-1501785888041-af3ef285b470?w=800&q=80"
];

// --------------------------------------------------

function sleep(ms) {
  return new Promise(resolve => setTimeout(resolve, ms));
}

function buildPlacesUrl(lat, lng, radius) {
  const types = PLACE_TYPES.join("|");
  return `https://maps.googleapis.com/maps/api/place/nearbysearch/json?location=${lat},${lng}&radius=${radius}&type=${types}&key=${GOOGLE_API_KEY}`;
}

function buildPhotoUrl(photoRef, maxwidth = 1200) {
  return `https://maps.googleapis.com/maps/api/place/photo?maxwidth=${maxwidth}&photoreference=${photoRef}&key=${GOOGLE_API_KEY}`;
}

// --------------------------------------------------
// Buscar imágenes Google con radio progresivo
// --------------------------------------------------

async function obtenerImagenesGoogle(lat, lng) {
  let images = [];

  for (const radius of RADIOS) {
    try {
      const url = buildPlacesUrl(lat, lng, radius);
      const res = await axios.get(url, { timeout: 8000 });

      if (res.data.status !== "OK") continue;
      if (!res.data.results?.length) continue;

      for (const lugar of res.data.results) {
        if (!lugar.photos) continue;

        for (const foto of lugar.photos) {
          images.push(buildPhotoUrl(foto.photo_reference));
          if (images.length >= 5) break;
        }
        if (images.length >= 5) break;
      }

      // Si ya tenemos mínimo 3, no ampliamos más
      if (images.length >= 3) break;

    } catch {
      // ignoramos errores y seguimos
    }
  }

  if (images.length === 0) return null;

  return {
    coverImage: images[0],
    images
  };
}

// --------------------------------------------------
// MAIN
// --------------------------------------------------

async function fixImagesGooglePlaces() {
  await mongoose.connect(process.env.MONGO_URI);
  console.log("🟢 Conectado a MongoDB");

  const rutas = await Route.find({ featured: true }).limit(MAX_GOOGLE);

  console.log(`⭐ Rutas featured encontradas: ${rutas.length}`);
  console.log("🚀 Iniciando enriquecimiento con Google Places...\n");

  let procesadas = 0;
  let conGoogle = 0;
  let sinGoogle = 0;

  for (const ruta of rutas) {
    procesadas++;

    const { lat, lng } = ruta.startPoint;
    const googleImgs = await obtenerImagenesGoogle(lat, lng);

    let finalImages = [];

    if (googleImgs) {
      finalImages = [...googleImgs.images];
      conGoogle++;
      console.log(`🖼️ GOOGLE (${procesadas}): ${ruta.name}`);
    } else {
      sinGoogle++;
      console.log(`⚠️ SIN GOOGLE (${procesadas}): ${ruta.name}`);
    }

    // 🧩 Completar hasta mínimo 3 con Unsplash
    let i = 0;
    while (finalImages.length < 3) {
      finalImages.push(UNSPLASH_FALLBACK[i % UNSPLASH_FALLBACK.length]);
      i++;
    }

    ruta.coverImage = finalImages[0];
    ruta.images = finalImages.slice(0, 5);
    await ruta.save();

    await sleep(DELAY_MS);
  }

  console.log("\n" + "=".repeat(60));
  console.log("✅ GOOGLE PLACES FINALIZADO");
  console.log("=".repeat(60));
  console.log(`📸 Con aporte Google: ${conGoogle}`);
  console.log(`🖼️ Sin Google (fallback): ${sinGoogle}`);
  console.log(`🔢 Total procesadas: ${procesadas}`);
  console.log("=".repeat(60));

  process.exit(0);
}

fixImagesGooglePlaces().catch(err => {
  console.error("💥 ERROR FATAL:", err);
  process.exit(1);
});
