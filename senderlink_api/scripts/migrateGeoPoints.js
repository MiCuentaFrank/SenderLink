require("dotenv").config();
const mongoose = require("mongoose");
const Route = require("./models/Route"); // ajusta si hace falta

function toGeoFromLegacy(point) {
  if (!point) return null;

  // Caso: { coordinates: [lng, lat] }
  if (Array.isArray(point.coordinates) && point.coordinates.length === 2) {
    const lng = Number(point.coordinates[0]);
    const lat = Number(point.coordinates[1]);
    if (Number.isFinite(lng) && Number.isFinite(lat)) {
      return { type: "Point", coordinates: [lng, lat] };
    }
  }

  // Caso: { lng, lat }
  if (point.lng != null && point.lat != null) {
    const lng = Number(point.lng);
    const lat = Number(point.lat);
    if (Number.isFinite(lng) && Number.isFinite(lat)) {
      return { type: "Point", coordinates: [lng, lat] };
    }
  }

  return null;
}

function toGeoFromGeometryStart(route) {
  const coords = route?.geometry?.coordinates;
  if (!Array.isArray(coords) || coords.length < 2) return null;

  const first = coords[0];
  if (!Array.isArray(first) || first.length < 2) return null;

  const lng = Number(first[0]);
  const lat = Number(first[1]);
  if (!Number.isFinite(lng) || !Number.isFinite(lat)) return null;

  return { type: "Point", coordinates: [lng, lat] };
}

function toGeoFromGeometryEnd(route) {
  const coords = route?.geometry?.coordinates;
  if (!Array.isArray(coords) || coords.length < 2) return null;

  const last = coords[coords.length - 1];
  if (!Array.isArray(last) || last.length < 2) return null;

  const lng = Number(last[0]);
  const lat = Number(last[1]);
  if (!Number.isFinite(lng) || !Number.isFinite(lat)) return null;

  return { type: "Point", coordinates: [lng, lat] };
}

async function run() {
  await mongoose.connect(process.env.MONGO_URI);
  console.log("🟢 Conectado a MongoDB");

  // Solo traemos lo mínimo necesario
  const cursor = Route.find(
    {
      $or: [
        { startPointGeo: { $exists: false } },
        { endPointGeo: { $exists: false } }
      ]
    },
    {
      startPoint: 1,
      endPoint: 1,
      geometry: 1,
      startPointGeo: 1,
      endPointGeo: 1
    }
  ).lean().cursor();

  let updated = 0;
  let skipped = 0;
  let batch = [];
  const BATCH_SIZE = 500;

  for (let doc = await cursor.next(); doc != null; doc = await cursor.next()) {
    // Si ya tiene ambos, saltamos
    const hasStart = doc.startPointGeo?.coordinates?.length === 2;
    const hasEnd = doc.endPointGeo?.coordinates?.length === 2;
    if (hasStart && hasEnd) {
      skipped++;
      continue;
    }

    // Intentamos sacar start/end de legacy o geometry
    const startGeo =
      toGeoFromLegacy(doc.startPoint) || toGeoFromGeometryStart(doc);

    const endGeo =
      toGeoFromLegacy(doc.endPoint) || toGeoFromGeometryEnd(doc);

    // Si no podemos obtener ninguno, saltamos
    if (!startGeo && !endGeo) {
      skipped++;
      continue;
    }

    const $set = {};
    if (!hasStart && startGeo) $set.startPointGeo = startGeo;
    if (!hasEnd && endGeo) $set.endPointGeo = endGeo;

    if (Object.keys($set).length === 0) {
      skipped++;
      continue;
    }

    batch.push({
      updateOne: {
        filter: { _id: doc._id },
        update: { $set }
      }
    });

    if (batch.length >= BATCH_SIZE) {
      const res = await Route.bulkWrite(batch, { ordered: false });
      updated += res.modifiedCount || 0;
      console.log(`🧩 Batch aplicado. Total modificadas: ${updated}`);
      batch = [];
    }
  }

  if (batch.length > 0) {
    const res = await Route.bulkWrite(batch, { ordered: false });
    updated += res.modifiedCount || 0;
  }

  console.log(`✅ Migración terminada. Modificadas: ${updated} | Saltadas: ${skipped}`);

  // Crear índices GEO (si ya existen, normalmente no pasa nada)
  await Route.collection.createIndex({ startPointGeo: "2dsphere" });
  await Route.collection.createIndex({ endPointGeo: "2dsphere" });
  console.log("✅ Índices GEO listos (startPointGeo/endPointGeo)");

  await mongoose.disconnect();
  console.log("🔌 Desconectado");
}

run().catch((e) => {
  console.error("❌ Error:", e);
  process.exit(1);
});
