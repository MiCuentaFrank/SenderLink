const express = require("express");
const router = express.Router();

const {
  createUser,
  getUsers,
  getUserByUid,
  updateUser,
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

// Eliminar usuario
router.delete("/:uid", deleteUser);

module.exports = router;