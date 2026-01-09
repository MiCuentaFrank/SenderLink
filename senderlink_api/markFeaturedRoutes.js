require("dotenv").config();
const mongoose = require("mongoose");
const Route = require("./models/Route");

const MAX_FEATURED = 100;

const KEYWORDS = [
  "cares", "ordesa", "aneto", "mulhac", "teide",
  "aigüestortes", "picos", "sierra", "camino",
  "santiago", "covadonga", "fuente", "doñana",
  "monte", "parque"
];

async function marcarFeatured() {
  await mongoose.connect(process.env.MONGO_URI);
  console.log("🟢 Conectado a MongoDB");

  // 1️⃣ Resetear featured (por si ya había algo)
  await Route.updateMany({}, { $set: { featured: false } });

  // 2️⃣ Preselección inteligente
  const candidatas = await Route.find({
    type: { $in: ["PARQUE_NACIONAL", "GR", "PR", "VIA_VERDE"] },

    distanceKm: { $gte: 5, $lte: 30 }
  });

  console.log(`📌 Candidatas encontradas: ${candidatas.length}`);

  let marcadas = 0;

  for (const ruta of candidatas) {
    if (marcadas >= MAX_FEATURED) break;

    const nombre = ruta.name.toLowerCase();
    const esFamosa = KEYWORDS.some(k => nombre.includes(k));

    if (!esFamosa) continue;

    ruta.featured = true;
    await ruta.save();

    marcadas++;
    console.log(`⭐ FEATURED (${marcadas}): ${ruta.name}`);
  }

  console.log("=".repeat(50));
  console.log(`✅ Total rutas destacadas: ${marcadas}`);
  console.log("=".repeat(50));

  process.exit(0);
}

marcarFeatured().catch(err => {
  console.error("❌ Error:", err);
  process.exit(1);
});
