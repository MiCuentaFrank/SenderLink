const express = require("express");
const router = express.Router();

const {
  createUser,
  getUsers,
  getUserByUid,
  updateUser,
  updateUserProfile,
  deleteUser
} = require("../controllers/userController");

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

// Eliminar usuario
router.delete("/:uid", deleteUser);

module.exports = router;