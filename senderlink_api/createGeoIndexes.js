require("dotenv").config();
const mongoose = require("mongoose");
const Route = require("./models/Route");

async function run() {
  try {
    await mongoose.connect(process.env.MONGO_URI);
    console.log("🟢 Conectado:", mongoose.connection.name);
    console.log("📦 Collection:", Route.collection.name);

    // 1) Índice para buscar rutas por cercanía al punto de inicio
    console.log("⏳ Creando índice 2dsphere en startPoint...");
    await Route.collection.createIndex({ startPoint: "2dsphere" }, { name: "startPoint_2dsphere" });

    // 2) Índice para buscar rutas por cercanía al punto final (opcional pero útil)
    console.log("⏳ Creando índice 2dsphere en endPoint...");
    await Route.collection.createIndex({ endPoint: "2dsphere" }, { name: "endPoint_2dsphere" });

    // 3) Índice para búsquedas geoespaciales sobre toda la geometría (LineString)
    //    (útil si algún día haces "rutas que pasan cerca de este punto")
    console.log("⏳ Creando índice 2dsphere en geometry...");
    await Route.collection.createIndex({ geometry: "2dsphere" }, { name: "geometry_2dsphere" });

    // Verificación
    const indexes = await Route.collection.indexes();
    console.log("✅ Índices actuales:");
    console.log(indexes.map(i => i.name));

    console.log("🎉 Listo. Índices geoespaciales creados.");
  } catch (e) {
    console.error("❌ Error creando índices:", e);
    process.exit(1);
  } finally {
    await mongoose.disconnect();
    console.log("🔌 Desconectado");
  }
}

run();
