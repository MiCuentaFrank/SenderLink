require("dotenv").config();
const mongoose = require("mongoose");
const Route = require("./models/Route");
const axios = require("axios");

async function fixImages() {
  try {
    await mongoose.connect(process.env.MONGO_URI);
    console.log("🔥 Conectado a MongoDB");

    let rutas = await Route.find({});
    console.log(`📌 Encontradas ${rutas.length} rutas`);

    for (let ruta of rutas) {
      console.log(`\n🔍 Procesando ruta: ${ruta.nombre}`);

      const primerPunto = ruta.puntos?.[0];
      if (!primerPunto) {
        console.log("⛔ La ruta no tiene puntos → No se pueden generar imágenes");
        continue;
      }

      const { latitud, longitud } = primerPunto;

      const url =
        `https://maps.googleapis.com/maps/api/place/nearbysearch/json?location=${latitud},${longitud}&radius=5000&type=tourist_attraction&key=${process.env.GOOGLE_MAPS_KEY}`;

      const resp = await axios.get(url);
      const lugares = resp.data.results;

      if (!lugares || lugares.length === 0) {
        console.log("❌ No se encontraron lugares cercanos → usando imagen por defecto");
        ruta.imagenPortada = "https://images.unsplash.com/photo-1501785888041-af3ef285b470";
        ruta.imagenes = [
          "https://images.unsplash.com/photo-1501785888041-af3ef285b470"
        ];
        await ruta.save();
        continue;
      }

      console.log(`📸 Encontrados ${lugares.length} lugares → Generando fotos...`);

      // --- Foto principal (STRING) ---
      const place = lugares[0];
      if (place.photos && place.photos.length > 0) {
        const photoRef = place.photos[0].photo_reference;
        ruta.imagenPortada =
          `https://maps.googleapis.com/maps/api/place/photo?maxwidth=800&photoreference=${photoRef}&key=${process.env.GOOGLE_MAPS_KEY}`;
      } else {
        ruta.imagenPortada = "https://images.unsplash.com/photo-1501785888041-af3ef285b470";
      }

      // --- Galería de imágenes (ARRAY) ---
      ruta.imagenes = [];

      for (let lugar of lugares.slice(0, 5)) {
        if (lugar.photos && lugar.photos.length > 0) {
          const ref = lugar.photos[0].photo_reference;

          ruta.imagenes.push(
            `https://maps.googleapis.com/maps/api/place/photo?maxwidth=800&photoreference=${ref}&key=${process.env.GOOGLE_MAPS_KEY}`
          );
        }
      }

      if (ruta.imagenes.length === 0) {
        ruta.imagenes = [ruta.imagenPortada];
      }

      await ruta.save();
      console.log(`✨ Imágenes generadas: ${ruta.imagenes.length}`);
      console.log(`✔ imagenPortada asignada`);
    }

    console.log("\n🎉 TODAS LAS RUTAS HAN SIDO PROCESADAS EXITOSAMENTE");
    process.exit();
  } catch (err) {
    console.error("💥 ERROR:", err);
  }
}

fixImages();
