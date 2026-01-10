require("dotenv").config();
const mongoose = require("mongoose");
const Route = require("./models/Route");

async function run() {
  await mongoose.connect(process.env.MONGO_URI);
  console.log("🟢 Conectado");
  console.log("DB:", mongoose.connection.name);
  console.log("Collection:", Route.collection.name);

  // Buscamos rutas que tengan lat/lng pero coordinates vacío (o no exista)
  const filter = {
    $or: [
      {
        "startPoint.lat": { $type: "number" },
        "startPoint.lng": { $type: "number" },
        $or: [
          { "startPoint.coordinates": { $exists: false } },
          { "startPoint.coordinates": { $size: 0 } },
        ],
      },
      {
        "endPoint.lat": { $type: "number" },
        "endPoint.lng": { $type: "number" },
        $or: [
          { "endPoint.coordinates": { $exists: false } },
          { "endPoint.coordinates": { $size: 0 } },
        ],
      },
    ],
  };

  const candidates = await Route.find(filter, {
    _id: 1,
    startPoint: 1,
    endPoint: 1,
  }).lean();

  console.log("Candidatas a actualizar:", candidates.length);

  if (candidates.length === 0) {
    console.log("✅ Nada que migrar.");
    await mongoose.disconnect();
    return;
  }

  const ops = [];

  for (const r of candidates) {
    const set = {};

    if (
      r.startPoint &&
      typeof r.startPoint.lat === "number" &&
      typeof r.startPoint.lng === "number" &&
      (!Array.isArray(r.startPoint.coordinates) || r.startPoint.coordinates.length !== 2)
    ) {
      set["startPoint.type"] = "Point";
      set["startPoint.coordinates"] = [r.startPoint.lng, r.startPoint.lat]; // [lng, lat]
    }

    if (
      r.endPoint &&
      typeof r.endPoint.lat === "number" &&
      typeof r.endPoint.lng === "number" &&
      (!Array.isArray(r.endPoint.coordinates) || r.endPoint.coordinates.length !== 2)
    ) {
      set["endPoint.type"] = "Point";
      set["endPoint.coordinates"] = [r.endPoint.lng, r.endPoint.lat];
    }

    if (Object.keys(set).length > 0) {
      ops.push({
        updateOne: {
          filter: { _id: r._id },
          update: { $set: set },
        },
      });
    }
  }

  if (ops.length === 0) {
    console.log("✅ Nada que actualizar tras comprobar campos.");
    await mongoose.disconnect();
    return;
  }

  const res = await Route.bulkWrite(ops, { ordered: false });
  console.log("✅ Bulk result:", res);
  console.log("✅ Rutas actualizadas:", res.modifiedCount);
const check = await Route.findOne(
  {},
  { startPoint: 1, endPoint: 1, name: 1 }
).lean();

console.log("🧪 CHECK RUTA:");
console.log(JSON.stringify(check, null, 2));

  await mongoose.disconnect();
  console.log("🔌 Desconectado");
}

run().catch((e) => {
  console.error("❌ Error:", e);
  process.exit(1);
});
