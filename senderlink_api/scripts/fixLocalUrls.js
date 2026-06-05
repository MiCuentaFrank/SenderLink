/**
 * Migración: reemplaza URLs de servidor local (http://192.168.x.x:3000/...)
 * por las URLs equivalentes en Firebase Storage, o por "" si el archivo
 * ya no existe localmente.
 *
 * Colecciones afectadas:
 *   - users     → foto
 *   - posts     → image, userPhoto
 *   - comments  → userPhoto
 *   - eventogrupals → organizadorFoto, participantes[].foto
 *
 * Uso:
 *   node scripts/fixLocalUrls.js [--dry-run]
 *
 *   --dry-run  Solo muestra lo que haría, sin guardar cambios
 */

require("dotenv").config();
const fs        = require("fs");
const path      = require("path");
const mongoose  = require("mongoose");

require("../middleware/firebaseAdmin");
const { uploadToFirebase } = require("../utils/firebaseStorage");

const User         = require("../models/User");
const Post         = require("../models/Post");
const Comment      = require("../models/Comment");
const EventoGrupal = require("../models/EventoGrupal");

const DRY_RUN    = process.argv.includes("--dry-run");
const LOCAL_IP   = "192.168.";           // patrón que identifica URLs locales
const UPLOADS_DIR = path.join(__dirname, "..", "uploads");

let fixed = 0;
let skipped = 0;

// ─── helpers ────────────────────────────────────────────────────────────────

function isLocalUrl(url) {
  return typeof url === "string" && url.includes(LOCAL_IP);
}

function getContentType(filePath) {
  const ext = path.extname(filePath).toLowerCase();
  const map = {
    ".jpg": "image/jpeg", ".jpeg": "image/jpeg",
    ".png": "image/png",  ".gif": "image/gif",
    ".webp": "image/webp"
  };
  return map[ext] || "image/jpeg";
}

/**
 * Intenta subir el archivo local a Firebase Storage.
 * Si no existe localmente, devuelve "".
 */
async function resolveUrl(localUrl) {
  // Extraer la ruta relativa: /uploads/users/filename.jpg
  let urlPath;
  try {
    urlPath = new URL(localUrl).pathname; // → /uploads/users/filename.jpg
  } catch {
    return "";
  }

  const localFile = path.join(UPLOADS_DIR, ...urlPath.replace(/^\/uploads\//, "").split("/"));

  if (!fs.existsSync(localFile)) {
    console.log(`    ⚠️  Archivo no encontrado localmente: ${path.basename(localFile)} → ""`);
    skipped++;
    return "";
  }

  if (DRY_RUN) {
    console.log(`    🔍 [DRY] Subiría: ${localFile}`);
    return localUrl; // no cambia en dry-run
  }

  try {
    const buffer      = fs.readFileSync(localFile);
    const destPath    = urlPath.replace(/^\//, ""); // uploads/users/filename.jpg
    const contentType = getContentType(localFile);
    const url         = await uploadToFirebase(buffer, destPath, contentType);
    fixed++;
    return url;
  } catch (err) {
    console.error(`    ❌ Error subiendo ${localFile}: ${err.message}`);
    skipped++;
    return "";
  }
}

// ─── usuarios ────────────────────────────────────────────────────────────────

async function fixUsers() {
  const users = await User.find({ foto: { $regex: LOCAL_IP } });
  console.log(`\n👤 Usuarios con foto local: ${users.length}`);

  for (const user of users) {
    console.log(`  → uid: ${user.uid}  foto: ${user.foto}`);
    const newUrl = await resolveUrl(user.foto);
    if (!DRY_RUN) {
      await User.updateOne({ _id: user._id }, { foto: newUrl });
      console.log(`    ✅ Actualizado → ${newUrl || "(vacío)"}`);
    }
  }
}

// ─── posts ───────────────────────────────────────────────────────────────────

async function fixPosts() {
  const posts = await Post.find({
    $or: [
      { image:     { $regex: LOCAL_IP } },
      { userPhoto: { $regex: LOCAL_IP } }
    ]
  });
  console.log(`\n📝 Posts con URLs locales: ${posts.length}`);

  for (const post of posts) {
    const update = {};

    if (isLocalUrl(post.image)) {
      console.log(`  → post ${post._id}  image: ${post.image}`);
      update.image = await resolveUrl(post.image);
    }

    if (isLocalUrl(post.userPhoto)) {
      console.log(`  → post ${post._id}  userPhoto: ${post.userPhoto}`);
      update.userPhoto = await resolveUrl(post.userPhoto);
    }

    if (!DRY_RUN && Object.keys(update).length) {
      await Post.updateOne({ _id: post._id }, { $set: update });
      console.log(`    ✅ Post ${post._id} actualizado`);
    }
  }
}

// ─── comentarios ─────────────────────────────────────────────────────────────

async function fixComments() {
  const comments = await Comment.find({ userPhoto: { $regex: LOCAL_IP } });
  console.log(`\n💬 Comentarios con userPhoto local: ${comments.length}`);

  for (const comment of comments) {
    console.log(`  → comment ${comment._id}  userPhoto: ${comment.userPhoto}`);
    const newUrl = await resolveUrl(comment.userPhoto);
    if (!DRY_RUN) {
      await Comment.updateOne({ _id: comment._id }, { userPhoto: newUrl });
      console.log(`    ✅ Actualizado → ${newUrl || "(vacío)"}`);
    }
  }
}

// ─── eventos grupales ─────────────────────────────────────────────────────────

async function fixEventos() {
  const eventos = await EventoGrupal.find({
    $or: [
      { organizadorFoto:       { $regex: LOCAL_IP } },
      { "participantes.foto":  { $regex: LOCAL_IP } }
    ]
  });
  console.log(`\n🏕️  Eventos con fotos locales: ${eventos.length}`);

  for (const evento of eventos) {
    let changed = false;

    if (isLocalUrl(evento.organizadorFoto)) {
      console.log(`  → evento ${evento._id}  organizadorFoto: ${evento.organizadorFoto}`);
      if (!DRY_RUN) {
        evento.organizadorFoto = await resolveUrl(evento.organizadorFoto);
        changed = true;
      }
    }

    for (const p of evento.participantes || []) {
      if (isLocalUrl(p.foto)) {
        console.log(`  → evento ${evento._id} participante ${p.uid}  foto: ${p.foto}`);
        if (!DRY_RUN) {
          p.foto = await resolveUrl(p.foto);
          changed = true;
        }
      }
    }

    if (!DRY_RUN && changed) {
      // Usar updateOne con $set para evitar el pre-save hook de EventoGrupal
      await EventoGrupal.updateOne(
        { _id: evento._id },
        { $set: {
            organizadorFoto: evento.organizadorFoto,
            participantes:   evento.participantes
          }
        }
      );
      console.log(`    ✅ Evento ${evento._id} actualizado`);
    }
  }
}

// ─── main ────────────────────────────────────────────────────────────────────

async function main() {
  if (DRY_RUN) console.log("🔍 MODO DRY-RUN: no se guardarán cambios\n");

  console.log("🚀 Conectando a MongoDB...");
  await mongoose.connect(process.env.MONGO_URI);
  console.log("✅ MongoDB conectado");

  await fixUsers();
  await fixPosts();
  await fixComments();
  await fixEventos();

  console.log(`\n🎉 Migración completada`);
  if (!DRY_RUN) {
    console.log(`   ✅ Subidos/actualizados: ${fixed}`);
    console.log(`   ⚠️  Sin archivo local (dejados vacíos): ${skipped}`);
  }

  await mongoose.disconnect();
  process.exit(0);
}

main().catch(err => {
  console.error("❌ Error fatal:", err);
  process.exit(1);
});
