/**
 * Migración: corrige el campo `comunidad` de las rutas usando `provincia` como referencia.
 *
 * Para cada ruta que tenga `provincia` definida, busca la comunidad autónoma
 * correcta en el mapeo y actualiza el campo si no coincide.
 *
 * Uso:
 *   node scripts/fixComunidades.js [--dry-run]
 *
 *   --dry-run  Muestra qué cambiaría sin guardar nada en la base de datos
 */

require("dotenv").config();
const mongoose = require("mongoose");
const Route    = require("../models/Route");

const DRY_RUN = process.argv.includes("--dry-run");

// ─── Mapeo provincia → comunidad autónoma ────────────────────────────────────

const PROVINCIA_A_COMUNIDAD = {
  // Andalucía
  "Almería":                "Andalucía",
  "Cádiz":                  "Andalucía",
  "Córdoba":                "Andalucía",
  "Granada":                "Andalucía",
  "Huelva":                 "Andalucía",
  "Jaén":                   "Andalucía",
  "Málaga":                 "Andalucía",
  "Sevilla":                "Andalucía",
  // Aragón
  "Huesca":                 "Aragón",
  "Teruel":                 "Aragón",
  "Zaragoza":               "Aragón",
  // Asturias
  "Asturias":               "Asturias",
  // Islas Baleares
  "Illes Balears":          "Islas Baleares",
  "Baleares":               "Islas Baleares",
  "Islas Baleares":         "Islas Baleares",
  // Canarias
  "Las Palmas":             "Canarias",
  "Santa Cruz de Tenerife": "Canarias",
  // Cantabria
  "Cantabria":              "Cantabria",
  // Castilla-La Mancha
  "Albacete":               "Castilla-La Mancha",
  "Ciudad Real":            "Castilla-La Mancha",
  "Cuenca":                 "Castilla-La Mancha",
  "Guadalajara":            "Castilla-La Mancha",
  "Toledo":                 "Castilla-La Mancha",
  // Castilla y León
  "Ávila":                  "Castilla y León",
  "Avila":                  "Castilla y León",
  "Burgos":                 "Castilla y León",
  "León":                   "Castilla y León",
  "Leon":                   "Castilla y León",
  "Palencia":               "Castilla y León",
  "Salamanca":              "Castilla y León",
  "Segovia":                "Castilla y León",
  "Soria":                  "Castilla y León",
  "Valladolid":             "Castilla y León",
  "Zamora":                 "Castilla y León",
  // Cataluña
  "Barcelona":              "Cataluña",
  "Girona":                 "Cataluña",
  "Gerona":                 "Cataluña",
  "Lleida":                 "Cataluña",
  "Lérida":                 "Cataluña",
  "Tarragona":              "Cataluña",
  // Extremadura
  "Badajoz":                "Extremadura",
  "Cáceres":                "Extremadura",
  "Caceres":                "Extremadura",
  // Galicia
  "A Coruña":               "Galicia",
  "La Coruña":              "Galicia",
  "Coruña":                 "Galicia",
  "Lugo":                   "Galicia",
  "Ourense":                "Galicia",
  "Orense":                 "Galicia",
  "Pontevedra":             "Galicia",
  // La Rioja
  "La Rioja":               "La Rioja",
  "Rioja":                  "La Rioja",
  // Madrid
  "Madrid":                 "Comunidad de Madrid",
  // Murcia
  "Murcia":                 "Región de Murcia",
  // Navarra
  "Navarra":                "Comunidad Foral de Navarra",
  // País Vasco
  "Álava":                  "País Vasco",
  "Alava":                  "País Vasco",
  "Gipuzkoa":               "País Vasco",
  "Guipúzcoa":              "País Vasco",
  "Guipuzcoa":              "País Vasco",
  "Bizkaia":                "País Vasco",
  "Vizcaya":                "País Vasco",
  // Comunidad Valenciana
  "Alicante":               "Comunidad Valenciana",
  "Alacant":                "Comunidad Valenciana",
  "Castellón":              "Comunidad Valenciana",
  "Castelló":               "Comunidad Valenciana",
  "Valencia":               "Comunidad Valenciana",
  "València":               "Comunidad Valenciana",
  // Ciudades autónomas
  "Ceuta":                  "Ceuta",
  "Melilla":                "Melilla",
  // Nombres alternativos en euskera / inglés
  "Araba":                  "País Vasco",
  "Biscay":                 "País Vasco",
  "Gipuzkoa":               "País Vasco",
  // Rutas transfronterizas
  "Pyrénées-Atlantiques":   "Francia",
  "Pyrénées-Orientales":    "Francia",
  "Almeida":                "Portugal"
};

// ─── helpers ─────────────────────────────────────────────────────────────────

function getComunidad(provincia) {
  if (!provincia) return null;
  const normalizada = provincia.trim();
  // Ignorar valores nulos literales almacenados como string
  if (normalizada.toLowerCase() === "null" || normalizada === "") return null;
  // Buscar exacta primero
  if (PROVINCIA_A_COMUNIDAD[normalizada]) return PROVINCIA_A_COMUNIDAD[normalizada];
  // Buscar case-insensitive
  const clave = Object.keys(PROVINCIA_A_COMUNIDAD)
    .find(k => k.toLowerCase() === normalizada.toLowerCase());
  return clave ? PROVINCIA_A_COMUNIDAD[clave] : null;
}

// ─── main ────────────────────────────────────────────────────────────────────

async function main() {
  if (DRY_RUN) console.log("🔍 MODO DRY-RUN: no se guardarán cambios\n");

  console.log("🚀 Conectando a MongoDB...");
  await mongoose.connect(process.env.MONGO_URI);
  console.log("✅ MongoDB conectado\n");

  // Solo rutas que tienen provincia definida
  const rutas = await Route.find({ provincia: { $exists: true, $ne: "" } })
    .select("name provincia comunidad");

  console.log(`📍 Total rutas con provincia: ${rutas.length}\n`);

  let correctas  = 0;
  let corregidas = 0;
  let sinMapeo   = 0;

  for (const ruta of rutas) {
    const comunidadEsperada = getComunidad(ruta.provincia);

    if (!comunidadEsperada) {
      console.log(`⚠️  Provincia no reconocida: "${ruta.provincia}" (ruta: ${ruta.name})`);
      sinMapeo++;
      continue;
    }

    if (ruta.comunidad === comunidadEsperada) {
      correctas++;
      continue;
    }

    console.log(`🔧 "${ruta.name}"`);
    console.log(`     provincia : ${ruta.provincia}`);
    console.log(`     comunidad actual   : "${ruta.comunidad}"`);
    console.log(`     comunidad correcta : "${comunidadEsperada}"`);

    if (!DRY_RUN) {
      await Route.updateOne(
        { _id: ruta._id },
        { $set: { comunidad: comunidadEsperada } }
      );
      console.log(`     ✅ Actualizada`);
    }

    corregidas++;
  }

  console.log(`\n🎉 Resumen:`);
  console.log(`   ✅ Correctas sin cambios : ${correctas}`);
  console.log(`   🔧 Corregidas           : ${corregidas}`);
  console.log(`   ⚠️  Provincia desconocida : ${sinMapeo}`);

  await mongoose.disconnect();
  process.exit(0);
}

main().catch(err => {
  console.error("❌ Error fatal:", err);
  process.exit(1);
});
