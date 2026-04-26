const { randomUUID } = require("crypto");
const admin = require("../middleware/firebaseAdmin");

/**
 * Sube un buffer a Firebase Storage y devuelve la URL de descarga con token.
 * La URL funciona permanentemente sin autenticación.
 *
 * @param {Buffer} buffer - contenido del archivo
 * @param {string} destPath - ruta en Storage, ej: "uploads/users/uid_123.jpg"
 * @param {string} contentType - MIME type, ej: "image/jpeg"
 * @returns {Promise<string>} URL de descarga de Firebase Storage
 */
async function uploadToFirebase(buffer, destPath, contentType) {
  const bucket = admin.storage().bucket();
  const file = bucket.file(destPath);
  const token = randomUUID();

  await file.save(buffer, {
    resumable: false,
    metadata: {
      contentType,
      metadata: {
        firebaseStorageDownloadTokens: token
      }
    }
  });

  const encodedPath = encodeURIComponent(destPath);
  return `https://firebasestorage.googleapis.com/v0/b/${bucket.name}/o/${encodedPath}?alt=media&token=${token}`;
}

module.exports = { uploadToFirebase };
