require("dotenv").config();
const mongoose = require("mongoose");
const Route = require("./models/Route");

// 🌄 Unsplash SOLO paisajes / outdoor
const UNSPLASH_OUTDOOR = [
  // Montaña
  "https://images.unsplash.com/photo-1501785888041-af3ef285b470?w=1200&q=80",
  "https://images.unsplash.com/photo-1464822759023-fed622ff2c3b?w=1200&q=80",
  "https://images.unsplash.com/photo-1506905925346-21bda4d32df4?w=1200&q=80",

  // Senderos / hiking
  "https://images.unsplash.com/photo-1447752875215-b2761acb3c5d?w=1200&q=80",
  "https://images.unsplash.com/photo-1470071459604-3b5ec3a7fe05?w=1200&q=80",

  // Naturaleza / bosque
  "https://images.unsplash.com/photo-1441974231531-c6227db76b6e?w=1200&q=80",
  "https://images.unsplash.com/photo-1426604966848-d7adac402bff?w=1200&q=80"
];

async function fixNonFeaturedImages() {
  await mongoose.connect(process.env.MONGO_URI);
  console.log("🟢 Conectado a MongoDB");

  const rutas = await Route.find({ featured: { $ne: true } });
  console.log(`🟡 Rutas no destacadas encontradas: ${rutas.length}`);

  let i = 0;

  for (const ruta of rutas) {
    const img = UNSPLASH_OUTDOOR[i % UNSPLASH_OUTDOOR.length];

    ruta.coverImage = img;
    ruta.images = [img];

    ruta.extraInfo = {
      ...ruta.extraInfo,
      imagenGenerica: true,
      avisoImagen:
        "Imagen genérica de paisaje. No representa la localización exacta de la ruta."
    };

    await ruta.save();
    i++;
  }

  console.log("✅ Imágenes outdoor aplicadas a rutas no destacadas");
  process.exit(0);
}

fixNonFeaturedImages().catch(err => {
  console.error("💥 ERROR:", err);
  process.exit(1);
});
