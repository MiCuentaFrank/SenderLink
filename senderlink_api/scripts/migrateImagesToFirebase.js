/**
 * Script de migración: sube imágenes locales a Firebase Storage
 * y actualiza las URLs en MongoDB.
 *
 * Uso: node scripts/migrateImagesToFirebase.js
 *
 * Requiere variables de entorno en .env:
 *   MONGO_URI, FIREBASE_PROJECT_ID, FIREBASE_CLIENT_EMAIL,
 *   FIREBASE_PRIVATE_KEY, FIREBASE_STORAGE_BUCKET
 */

require("dotenv").config();
const fs = require("fs");
const path = require("path");
const mongoose = require("mongoose");

// Inicializar Firebase Admin antes de cargar los modelos
require("../middleware/firebaseAdmin");
const { uploadToFirebase } = require("../utils/firebaseStorage");

const Route = require("../models/Route");
const User  = require("../models/User");
const Post  = require("../models/Post");

const UPLOADS_DIR = path.join(__dirname, "..", "uploads");

// ─── helpers ───────────────────────────────────────────────────────────────

function getContentType(filePath) {
  const ext = path.extname(filePath).toLowerCase();
  const map = { ".jpg": "image/jpeg", ".jpeg": "image/jpeg",
                ".png": "image/png",  ".gif": "image/gif",
                ".webp": "image/webp" };
  return map[ext] || "image/jpeg";
}

/**
 * Sube un archivo local y devuelve la URL pública en Firebase Storage.
 * Si ya falla, devuelve null.
 */
async function uploadFile(localPath, destPath) {
  try {
    const buffer = fs.readFileSync(localPath);
    const contentType = getContentType(localPath);
    const url = await uploadToFirebase(buffer, destPath, contentType);
    return url;
  } catch (err) {
    console.error(`  ❌ Error subiendo ${localPath}:`, err.message);
    return null;
  }
}

// ─── migrar fotos de usuarios ───────────────────────────────────────────────

async function migrateUserPhotos() {
  const dir = path.join(UPLOADS_DIR, "users");
  if (!fs.existsSync(dir)) return;

  const users = await User.find({ foto: { $regex: "/uploads/users/" } });
  console.log(`\n👤 Usuarios con fotos locales: ${users.length}`);

  for (const user of users) {
    const filename = path.basename(user.foto);
    const localPath = path.join(dir, filename);
    if (!fs.existsSync(localPath)) {
      console.log(`  ⚠️  Archivo no encontrado: ${filename}`);
      continue;
    }
    const destPath = `uploads/users/${filename}`;
    const url = await uploadFile(localPath, destPath);
    if (url) {
      await User.updateOne({ _id: user._id }, { foto: url });
      console.log(`  ✅ ${user.uid} → ${url}`);
    }
  }
}

// ─── migrar fotos de rutas ──────────────────────────────────────────────────

async function migrateRoutePhotos() {
  const dir = path.join(UPLOADS_DIR, "routes");
  if (!fs.existsSync(dir)) return;

  // Busca rutas con coverImage o images apuntando a /uploads/routes/
  const routes = await Route.find({
    $or: [
      { coverImage: { $regex: "/uploads/routes/" } },
      { images: { $elemMatch: { $regex: "/uploads/routes/" } } }
    ]
  });
  console.log(`\n🗺️  Rutas con fotos locales: ${routes.length}`);

  for (const route of routes) {
    let changed = false;

    // coverImage
    if (route.coverImage && route.coverImage.includes("/uploads/routes/")) {
      const filename = path.basename(route.coverImage.split("?")[0]);
      const localPath = path.join(dir, filename);
      if (fs.existsSync(localPath)) {
        const url = await uploadFile(localPath, `uploads/routes/${filename}`);
        if (url) { route.coverImage = url; changed = true; }
      }
    }

    // images array
    if (Array.isArray(route.images)) {
      const newImages = [];
      for (const img of route.images) {
        if (img && img.includes("/uploads/routes/")) {
          const filename = path.basename(img.split("?")[0]);
          const localPath = path.join(dir, filename);
          if (fs.existsSync(localPath)) {
            const url = await uploadFile(localPath, `uploads/routes/${filename}`);
            newImages.push(url || img);
            if (url) changed = true;
          } else {
            newImages.push(img);
          }
        } else {
          newImages.push(img);
        }
      }
      route.images = newImages;
    }

    if (changed) {
      await route.save();
      console.log(`  ✅ Ruta ${route._id} actualizada`);
    }
  }
}

// ─── migrar fotos de posts ──────────────────────────────────────────────────

async function migratePostPhotos() {
  const dir = path.join(UPLOADS_DIR, "posts");
  if (!fs.existsSync(dir)) return;

  let Post;
  try { Post = require("../models/Post"); } catch { return; }

  const posts = await Post.find({ imageUrl: { $regex: "/uploads/posts/" } });
  console.log(`\n📝 Posts con fotos locales: ${posts.length}`);

  for (const post of posts) {
    const filename = path.basename(post.imageUrl);
    const localPath = path.join(dir, filename);
    if (!fs.existsSync(localPath)) continue;
    const url = await uploadFile(localPath, `uploads/posts/${filename}`);
    if (url) {
      await Post.updateOne({ _id: post._id }, { imageUrl: url });
      console.log(`  ✅ Post ${post._id} → ${url}`);
    }
  }
}

// ─── main ───────────────────────────────────────────────────────────────────

async function main() {
  console.log("🚀 Conectando a MongoDB...");
  await mongoose.connect(process.env.MONGO_URI);
  console.log("✅ MongoDB conectado");

  await migrateUserPhotos();
  await migrateRoutePhotos();
  await migratePostPhotos();

  console.log("\n🎉 Migración completada");
  await mongoose.disconnect();
  process.exit(0);
}

main().catch(err => {
  console.error("❌ Error en migración:", err);
  process.exit(1);
});
