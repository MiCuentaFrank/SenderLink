require("dotenv").config();
const mongoose = require("mongoose");
const Route = require("./models/Route");

function isMojibake(str) {
  return typeof str === "string" && /Ã/.test(str);
}

function fixMojibake(str) {
  if (!isMojibake(str)) return str;
  return Buffer.from(str, "latin1").toString("utf8");
}

async function fixEncoding() {
  await mongoose.connect(process.env.MONGO_URI);
  console.log("Conectado a MongoDB");

  const rutas = await Route.find({
    $or: [
      { name: { $regex: "Ã", $options: "i" } },
      { description: { $regex: "Ã", $options: "i" } }
    ]
  });

  console.log(`Rutas con encoding incorrecto: ${rutas.length}`);

  let corregidas = 0;
  let errores = 0;

  for (const ruta of rutas) {
    const originalName = ruta.name;
    const originalDesc = ruta.description;

    if (isMojibake(ruta.name)) ruta.name = fixMojibake(ruta.name);
    if (isMojibake(ruta.description)) ruta.description = fixMojibake(ruta.description);

    try {
      await ruta.save({ validateBeforeSave: false });
      corregidas++;
      console.log(`[OK] "${originalName}" → "${ruta.name}"`);
    } catch (err) {
      errores++;
      console.log(`[ERROR] "${originalName}": ${err.message}`);
    }
  }

  console.log("\n" + "=".repeat(60));
  console.log(`Corregidas: ${corregidas}`);
  console.log(`Errores:    ${errores}`);
  console.log("=".repeat(60));

  process.exit(0);
}

fixEncoding().catch((err) => {
  console.error("ERROR FATAL:", err);
  process.exit(1);
});