require("dotenv").config();
const mongoose = require("mongoose");
const axios = require("axios");
const fs = require("fs");
const path = require("path");
const Route = require("./models/Route");

const GOOGLE_API_KEY = process.env.GOOGLE_MAPS_KEY || process.env.GOOGLE_API_KEY;

const MAX_ROUTES = 100;
const DELAY_MS = 1500;
const IMAGES_DIR = path.join(__dirname, "uploads", "routes");

const PLACE_TYPES = ["tourist_attraction", "natural_feature", "park", "point_of_interest"];
const RADIOS = [5000, 10000];
const RADIOS_AMPLIADOS = [25000, 50000, 100000];

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

function buildPlacesUrl(lat, lng, radius) {
  const types = PLACE_TYPES.join("|");
  return `https://maps.googleapis.com/maps/api/place/nearbysearch/json?location=${lat},${lng}&radius=${radius}&type=${types}&key=${GOOGLE_API_KEY}`;
}

function buildPhotoUrl(photoReference, maxwidth = 1200) {
  return `https://maps.googleapis.com/maps/api/place/photo?maxwidth=${maxwidth}&photoreference=${photoReference}&key=${GOOGLE_API_KEY}`;
}

function getLatLng(ruta) {
  if (ruta.startPoint && typeof ruta.startPoint.lat === "number" && typeof ruta.startPoint.lng === "number") {
    return { lat: ruta.startPoint.lat, lng: ruta.startPoint.lng };
  }
  const coords = ruta.startPointGeo?.coordinates;
  if (Array.isArray(coords) && coords.length === 2) {
    const [lng, lat] = coords;
    if (Number.isFinite(lat) && Number.isFinite(lng)) return { lat, lng };
  }
  return null;
}

function extractPhotoRef(img) {
  if (!img || typeof img !== "string") return null;
  if (img.startsWith("gplaces:")) {
    return img.replace("gplaces:", "").trim();
  }
  if (img.includes("maps.googleapis.com/maps/api/place/photo")) {
    try {
      const u = new URL(img);
      return u.searchParams.get("photoreference") || null;
    } catch {
      return null;
    }
  }
  return null;
}

function isLocalUrl(img) {
  return typeof img === "string" && img.startsWith("/uploads/");
}

function isUnsplashUrl(img) {
  return typeof img === "string" && img.includes("unsplash.com");
}

async function obtenerRefsGoogle(lat, lng, radios = RADIOS) {
  let refs = [];

  for (const radius of radios) {
    try {
      const url = buildPlacesUrl(lat, lng, radius);
      const res = await axios.get(url, { timeout: 8000 });

      if (res.data.status !== "OK") continue;
      if (!res.data.results?.length) continue;

      for (const lugar of res.data.results) {
        if (!lugar.photos) continue;
        for (const foto of lugar.photos) {
          if (!foto.photo_reference) continue;
          refs.push(foto.photo_reference);
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

async function downloadPhoto(photoReference, filePath) {
  const url = buildPhotoUrl(photoReference);
  const response = await axios.get(url, {
    responseType: "stream",
    timeout: 15000,
    maxRedirects: 5
  });

  const writer = fs.createWriteStream(filePath);
  response.data.pipe(writer);

  return new Promise((resolve, reject) => {
    writer.on("finish", resolve);
    writer.on("error", reject);
  });
}

async function downloadFeaturedImages() {
  if (!GOOGLE_API_KEY) {
    console.error("Falta GOOGLE_MAPS_KEY o GOOGLE_API_KEY en el .env");
    process.exit(1);
  }

  // Crear directorio si no existe
  if (!fs.existsSync(IMAGES_DIR)) {
    fs.mkdirSync(IMAGES_DIR, { recursive: true });
    console.log(`Directorio creado: ${IMAGES_DIR}`);
  }

  await mongoose.connect(process.env.MONGO_URI);
  console.log("Conectado a MongoDB");

  const rutas = await Route.find({ featured: true }).limit(MAX_ROUTES);
  console.log(`Rutas featured encontradas: ${rutas.length}`);

  let descargadas = 0;
  let yaLocales = 0;
  let sinCoords = 0;
  let errores = 0;

  for (const ruta of rutas) {
    const routeId = ruta._id.toString();

    // Verificar si ya tiene imagenes locales
    if (isLocalUrl(ruta.coverImage)) {
      yaLocales++;
      continue;
    }

    // Si es Unsplash, dejar tal cual
    if (isUnsplashUrl(ruta.coverImage)) {
      console.log(`[UNSPLASH] ${ruta.name} - se deja como esta`);
      continue;
    }

    // Extraer photo refs existentes del documento
    let existingRefs = [];
    const coverRef = extractPhotoRef(ruta.coverImage);
    if (coverRef) existingRefs.push(coverRef);

    if (Array.isArray(ruta.images)) {
      for (const img of ruta.images) {
        const ref = extractPhotoRef(img);
        if (ref && !existingRefs.includes(ref)) {
          existingRefs.push(ref);
        }
      }
    }

    // Si no hay refs existentes, buscar nuevas via Google Nearby Search
    let photoRefs = existingRefs;
    if (photoRefs.length === 0) {
      const latlng = getLatLng(ruta);
      if (!latlng) {
        console.log(`[SIN COORDS] ${ruta.name}`);
        sinCoords++;
        continue;
      }

      console.log(`[BUSCANDO] ${ruta.name} en Google Nearby...`);
      const newRefs = await obtenerRefsGoogle(latlng.lat, latlng.lng);
      if (!newRefs) {
        console.log(`[SIN RESULTADOS GOOGLE] ${ruta.name}`);
        errores++;
        continue;
      }
      photoRefs = newRefs;
      await sleep(DELAY_MS);
    }

    // Descargar cada imagen
    const localPaths = [];
    const usedExistingRefs = photoRefs === existingRefs && existingRefs.length > 0;

    async function tryDownloadRefs(refs, label = "") {
      for (let i = 0; i < refs.length && i < 5; i++) {
        const ref = refs[i];
        const fileName = `${routeId}_${i}.jpg`;
        const filePath = path.join(IMAGES_DIR, fileName);
        const localUrl = `/uploads/routes/${fileName}`;

        if (fs.existsSync(filePath)) {
          localPaths.push(localUrl);
          continue;
        }

        try {
          await downloadPhoto(ref, filePath);
          localPaths.push(localUrl);
          console.log(`  [OK${label}] ${fileName}`);
          await sleep(300);
        } catch (err) {
          if (err.response?.status === 400) {
            console.log(`  [REFS EXPIRADAS${label}] ${ruta.name} - saltando al fallback`);
            return true; // expirados, cortar inmediatamente
          }
          console.log(`  [ERROR${label}] ${fileName}: ${err.message}`);
        }
      }
      return false;
    }

    const refsExpired = await tryDownloadRefs(photoRefs);

    // Si los refs almacenados fallaron o expiraron, reintentar con búsqueda fresca
    if (localPaths.length === 0 && (refsExpired || usedExistingRefs)) {
      const latlng = getLatLng(ruta);
      if (latlng) {
        console.log(`  [REINTENTANDO con Google Nearby] ${ruta.name}`);
        const freshRefs = await obtenerRefsGoogle(latlng.lat, latlng.lng);
        if (freshRefs) {
          await tryDownloadRefs(freshRefs, "-fallback");
          await sleep(DELAY_MS);
        }
      }
    }

    if (localPaths.length === 0) {
      console.log(`[SIN DESCARGAS] ${ruta.name}`);
      errores++;
      continue;
    }

    if (ruta.featured && localPaths.length < 3) {
      const latlng = getLatLng(ruta);
      if (latlng) {
        console.log(`  [AMPLIANDO RADIO] ${ruta.name} -> solo ${localPaths.length} imagen(es), buscando más lejos...`);
        const wideRefs = await obtenerRefsGoogle(latlng.lat, latlng.lng, RADIOS_AMPLIADOS);
        if (wideRefs) {
          await tryDownloadRefs(wideRefs, "-wide");
          await sleep(DELAY_MS);
        }
      }
    }

    const uniquePaths = [...new Set(localPaths)].slice(0, 5);

    if (ruta.featured && uniquePaths.length < 3) {
      console.log(`[INSUFICIENTES] ${ruta.name} -> ${uniquePaths.length} imagen(es) tras búsqueda ampliada`);
      errores++;
      continue;
    }

    // Actualizar MongoDB
    ruta.coverImage = uniquePaths[0];
    ruta.images = uniquePaths;
    await ruta.save();

    descargadas++;
    console.log(`[DESCARGADA] ${ruta.name} -> ${uniquePaths.length} imagenes`);
    await sleep(DELAY_MS);
  }

  console.log("\n" + "=".repeat(60));
  console.log("DESCARGA DE IMAGENES FINALIZADA");
  console.log("=".repeat(60));
  console.log(`Total rutas: ${rutas.length}`);
  console.log(`Descargadas: ${descargadas}`);
  console.log(`Ya locales: ${yaLocales}`);
  console.log(`Sin coordenadas: ${sinCoords}`);
  console.log(`Errores: ${errores}`);
  console.log("=".repeat(60));

  process.exit(0);
}

downloadFeaturedImages().catch((err) => {
  console.error("ERROR FATAL:", err);
  process.exit(1);
});
