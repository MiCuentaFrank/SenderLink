const express = require("express");
const router = express.Router();

const multer = require("multer");
const path = require("path");

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
// Multer config (subida de imágenes)
// ===============================
const storage = multer.diskStorage({
  destination: (req, file, cb) => {
    cb(null, "uploads/users");
  },
  filename: (req, file, cb) => {
    const ext = path.extname(file.originalname || "").toLowerCase();
    cb(null, `${req.params.uid}_${Date.now()}${ext}`);
  }
});

const upload = multer({
  storage,
  limits: { fileSize: 5 * 1024 * 1024 }, // 5MB máximo
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

// Subir foto de perfil (multipart)
router.post("/:uid/photo", upload.single("photo"), uploadUserPhoto);

// Eliminar usuario
router.delete("/:uid", deleteUser);

module.exports = router;
