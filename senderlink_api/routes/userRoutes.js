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

// ===============================
// Multer config (subida de imágenes)
// ===============================
const storage = multer.diskStorage({
  destination: (req, file, cb) => {
    cb(null, "uploads/users");
  },
  filename: (req, file, cb) => {
    const ext = path.extname(file.originalname || "");
    cb(null, `${req.params.uid}_${Date.now()}${ext}`);
  }
});

const upload = multer({
  storage,
  fileFilter: (req, file, cb) => {
    if (!file.mimetype || !file.mimetype.startsWith("image/")) {
      return cb(new Error("Solo se permiten imágenes"));
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

// ✅ Subir foto de perfil (multipart)
// Form-data: key="photo" type=File
router.post("/:uid/photo", upload.single("photo"), uploadUserPhoto);

// Eliminar usuario
router.delete("/:uid", deleteUser);

module.exports = router;
