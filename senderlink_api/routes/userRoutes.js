const express = require("express");
const router = express.Router();

const multer = require("multer");
const path = require("path");
const { uploadToFirebase } = require("../utils/firebaseStorage");

const {
  createUser,
  getUsers,
  getUserByUid,
  updateUser,
  updateUserProfile,
  uploadUserPhoto,
  deleteUser
} = require("../controllers/userController");

// Extensiones de imagen permitidas
const ALLOWED_EXTENSIONS = [".jpg", ".jpeg", ".png", ".gif", ".webp"];

// ===============================
// Multer config — memoria (Firebase Storage)
// ===============================
const upload = multer({
  storage: multer.memoryStorage(),
  limits: { fileSize: 5 * 1024 * 1024 },
  fileFilter: (req, file, cb) => {
    if (!file.mimetype || !file.mimetype.startsWith("image/")) {
      return cb(new Error("Solo se permiten imágenes"));
    }
    const ext = path.extname(file.originalname || "").toLowerCase();
    if (!ALLOWED_EXTENSIONS.includes(ext)) {
      return cb(new Error("Extensión de archivo no permitida"));
    }
    cb(null, true);
  }
});

// Middleware: sube el archivo a Firebase Storage y adjunta la URL al request
async function uploadPhotoToFirebase(req, res, next) {
  if (!req.file) return next();
  try {
    const ext = path.extname(req.file.originalname || ".jpg").toLowerCase() || ".jpg";
    const destPath = `uploads/users/${req.params.uid}_${Date.now()}${ext}`;
    const url = await uploadToFirebase(req.file.buffer, destPath, req.file.mimetype);
    req.firebasePhotoUrl = url;
    next();
  } catch (err) {
    console.error("Error subiendo foto a Firebase:", err.message);
    res.status(500).json({ ok: false, message: "Error al subir la imagen" });
  }
}

// ===============================
// Routes
// ===============================

// Crear usuario
router.post("/", createUser);

// Obtener todos los usuarios
router.get("/", getUsers);

// Obtener usuario por UID
router.get("/:uid", getUserByUid);

// Actualizar usuario
router.put("/:uid", updateUser);

// Actualizar SOLO perfil
router.put("/:uid/profile", updateUserProfile);

// Subir foto de perfil (multipart → Firebase Storage)
router.post("/:uid/photo", upload.single("photo"), uploadPhotoToFirebase, uploadUserPhoto);

// Eliminar usuario
router.delete("/:uid", deleteUser);

module.exports = router;
