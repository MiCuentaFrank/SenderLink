require("dotenv").config();
const mongoose = require("mongoose");
const Route = require("./models/Route");

async function run() {
  await mongoose.connect(process.env.MONGO_URI);
  console.log("✅ Conectado");

  const total = await Route.countDocuments();
  const withStartCoords = await Route.countDocuments({ "startPoint.coordinates.1": { $exists: true } });
  const withEndCoords = await Route.countDocuments({ "endPoint.coordinates.1": { $exists: true } });

  console.log("Total:", total);
  console.log("startPoint coords OK:", withStartCoords);
  console.log("endPoint coords OK:", withEndCoords);

  const oneBad = await Route.findOne({
    $or: [
      { "startPoint.coordinates.1": { $exists: false } },
      { "endPoint.coordinates.1": { $exists: false } }
    ]
  }, { name: 1, startPoint: 1, endPoint: 1 }).lean();

  console.log("Ejemplo BAD:", JSON.stringify(oneBad, null, 2));

  const oneGood = await Route.findOne(
    { "startPoint.coordinates.1": { $exists: true } },
    { name: 1, startPoint: 1 }
  ).lean();

  console.log("Ejemplo GOOD:", JSON.stringify(oneGood, null, 2));

  await mongoose.disconnect();
  console.log("🔌 Desconectado");
}

run().catch(e => {
  console.error("❌", e);
  process.exit(1);
});
