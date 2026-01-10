/**
 * 🔍 verifySetup.js - Script de verificación del proyecto
 * 
 * Ejecuta: node verifySetup.js
 * 
 * Este script comprueba:
 * 1. Conexión a MongoDB
 * 2. Formato de los datos (GeoJSON vs Legacy)
 * 3. Índices geoespaciales
 * 4. Endpoints de la API
 */

require("dotenv").config();
const mongoose = require("mongoose");
const Route = require("./models/Route");

// Colores para la consola
const GREEN = "\x1b[32m";
const RED = "\x1b[31m";
const YELLOW = "\x1b[33m";
const RESET = "\x1b[0m";

function ok(msg) { console.log(`${GREEN}✅ ${msg}${RESET}`); }
function fail(msg) { console.log(`${RED}❌ ${msg}${RESET}`); }
function warn(msg) { console.log(`${YELLOW}⚠️  ${msg}${RESET}`); }
function info(msg) { console.log(`   ${msg}`); }

async function run() {
  console.log("\n" + "=".repeat(50));
  console.log("🔍 VERIFICACIÓN DEL PROYECTO SENDERLINK");
  console.log("=".repeat(50) + "\n");

  // ==========================================
  // 1. CONEXIÓN A MONGODB
  // ==========================================
  console.log("📡 1. CONEXIÓN A MONGODB\n");
  
  try {
    await mongoose.connect(process.env.MONGO_URI);
    ok(`Conectado a: ${mongoose.connection.name}`);
  } catch (e) {
    fail(`No se pudo conectar: ${e.message}`);
    process.exit(1);
  }

  // ==========================================
  // 2. VERIFICAR DATOS
  // ==========================================
  console.log("\n📊 2. VERIFICACIÓN DE DATOS\n");

  const totalRoutes = await Route.countDocuments();
  info(`Total de rutas: ${totalRoutes}`);

  if (totalRoutes === 0) {
    warn("No hay rutas en la base de datos");
  } else {
    // Verificar formato de startPoint
    const withGeoJSON = await Route.countDocuments({
      "startPoint.coordinates": { $exists: true, $ne: [] }
    });

    const withLegacy = await Route.countDocuments({
      "startPoint.lat": { $exists: true },
      "startPoint.coordinates": { $exists: false }
    });

    const withBoth = await Route.countDocuments({
      "startPoint.lat": { $exists: true },
      "startPoint.coordinates": { $exists: true, $ne: [] }
    });

    info(`Rutas con formato GeoJSON: ${withGeoJSON}`);
    info(`Rutas con formato Legacy (lat/lng): ${withLegacy}`);
    info(`Rutas con ambos formatos: ${withBoth}`);

    if (withLegacy > 0 && withGeoJSON === 0) {
      fail("Todas las rutas usan formato Legacy. Ejecuta: node migrateStartEndToGeoJSON.js");
    } else if (withGeoJSON > 0 && withLegacy === 0) {
      ok("Todas las rutas usan formato GeoJSON");
    } else if (withBoth > 0) {
      ok("Las rutas tienen ambos formatos (compatibilidad OK)");
    }

    // Mostrar ejemplo
    const sample = await Route.findOne({}, { 
      name: 1, 
      startPoint: 1, 
      endPoint: 1 
    }).lean();

    if (sample) {
      console.log("\n   Ejemplo de ruta:");
      console.log(`   - Nombre: ${sample.name}`);
      console.log(`   - startPoint: ${JSON.stringify(sample.startPoint)}`);
    }
  }

  // ==========================================
  // 3. VERIFICAR ÍNDICES
  // ==========================================
  console.log("\n📑 3. VERIFICACIÓN DE ÍNDICES\n");

  const indexes = await Route.collection.indexes();
  const indexNames = indexes.map(i => i.name);

  info(`Índices encontrados: ${indexNames.length}`);

  const requiredIndexes = [
    { name: "startPoint_2dsphere", desc: "Índice geo en startPoint" },
    { name: "endPoint_2dsphere", desc: "Índice geo en endPoint" },
    { name: "geometry_2dsphere", desc: "Índice geo en geometry" }
  ];

  for (const idx of requiredIndexes) {
    if (indexNames.includes(idx.name)) {
      ok(idx.desc);
    } else {
      fail(`${idx.desc} - NO EXISTE. Ejecuta: node createGeoIndexes.js`);
    }
  }

  // ==========================================
  // 4. PROBAR CONSULTA GEOESPACIAL
  // ==========================================
  console.log("\n🗺️  4. PRUEBA DE CONSULTA GEOESPACIAL\n");

  try {
    // Centro de Madrid
    const testLat = 40.4168;
    const testLng = -3.7038;
    const testRadio = 100000; // 100km

    const nearbyRoutes = await Route.find({
      startPoint: {
        $nearSphere: {
          $geometry: {
            type: "Point",
            coordinates: [testLng, testLat]
          },
          $maxDistance: testRadio
        }
      }
    }).limit(5);

    if (nearbyRoutes.length > 0) {
      ok(`Consulta $nearSphere funciona. Encontradas ${nearbyRoutes.length} rutas cerca de Madrid`);
      nearbyRoutes.forEach(r => {
        info(`  - ${r.name}`);
      });
    } else {
      warn("La consulta funciona pero no hay rutas cerca de Madrid (40.41, -3.70)");
    }

  } catch (e) {
    fail(`Error en consulta geoespacial: ${e.message}`);
    info("Esto puede significar que los índices no están creados o los datos no son GeoJSON válido");
  }

  // ==========================================
  // 5. VERIFICAR RUTAS DESTACADAS
  // ==========================================
  console.log("\n⭐ 5. RUTAS DESTACADAS\n");

  const featuredCount = await Route.countDocuments({ featured: true });
  info(`Rutas destacadas: ${featuredCount}`);

  if (featuredCount === 0) {
    warn("No hay rutas destacadas. El carrusel de Home estará vacío.");
  } else {
    ok(`Hay ${featuredCount} rutas destacadas disponibles`);
  }

  // ==========================================
  // RESUMEN
  // ==========================================
  console.log("\n" + "=".repeat(50));
  console.log("📋 RESUMEN");
  console.log("=".repeat(50));
  console.log(`
  Total rutas:        ${totalRoutes}
  Rutas destacadas:   ${featuredCount}
  Índices geo:        ${indexNames.filter(n => n.includes("2dsphere")).length}/3
  `);

  await mongoose.disconnect();
  console.log("🔌 Desconectado\n");
}

run().catch(e => {
  console.error(RED + "❌ Error fatal:" + RESET, e);
  process.exit(1);
});
