require("dotenv").config();
const mongoose = require("mongoose");
const Route = require("./models/Route");

async function fixCoordinates() {
  try {
    await mongoose.connect(process.env.MONGO_URI);
    console.log("🟢 Conectado a MongoDB");

    const routes = await Route.find({
      "startPoint.coordinates": { $exists: true }
    });

    console.log(`📊 Total rutas a revisar: ${routes.length}`);

    let fixed = 0;
    let alreadyCorrect = 0;

    for (const route of routes) {
      const coords = route.startPoint.coordinates;

      // Verificar si las coordenadas están invertidas
      // Longitud válida para España: entre -18 y 4
      // Latitud válida para España: entre 27 y 44
      const isInverted =
        coords[0] >= 27 && coords[0] <= 44 &&  // Primera coordenada parece latitud
        coords[1] >= -18 && coords[1] <= 4;     // Segunda coordenada parece longitud

      if (isInverted) {
        console.log(`🔄 Corrigiendo: ${route.name}`);
        console.log(`   Antes: [${coords[0]}, ${coords[1]}]`);

        // Intercambiar coordenadas
        route.startPoint.coordinates = [coords[1], coords[0]];

        if (route.endPoint && route.endPoint.coordinates) {
          const endCoords = route.endPoint.coordinates;
          route.endPoint.coordinates = [endCoords[1], endCoords[0]];
        }

        // Arreglar geometry si existe
        if (route.geometry && route.geometry.coordinates) {
          if (route.geometry.type === "LineString") {
            route.geometry.coordinates = route.geometry.coordinates.map(coord => [coord[1], coord[0]]);
          }
        }

        await route.save();
        console.log(`   Después: [${route.startPoint.coordinates[0]}, ${route.startPoint.coordinates[1]}]`);
        fixed++;
      } else {
        alreadyCorrect++;
      }
    }

    console.log(`\n✅ Proceso completado:`);
    console.log(`   Rutas corregidas: ${fixed}`);
    console.log(`   Rutas ya correctas: ${alreadyCorrect}`);
    console.log(`   Total procesadas: ${routes.length}`);

  } catch (err) {
    console.error("❌ Error:", err);
  } finally {
    await mongoose.disconnect();
    console.log("🔌 Desconectado");
  }
}

fixCoordinates();