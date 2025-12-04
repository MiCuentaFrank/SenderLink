const User = require("../models/User");

// CREAR USUARIO
async function createUser(req, res) {
  try {
    // req.body contiene los datos que envía el cliente (Android)
    const { uid, email, nombre, foto } = req.body;

    // 1. VALIDAR: Comprobar que tenemos lo mínimo necesario
    if (!uid || !email) {
      return res.status(400).json({
        ok: false,
        message: "UID y email son obligatorios"
      });
    }

    // 3. CREAR: Guardar el usuario en MongoDB
    const newUser = await User.create({
      uid,
      email,
      nombre: nombre || "",
      foto: foto || ""
    });

    // 4. RESPONDER: Enviar el usuario creado al cliente
    res.status(201).json({
      ok: true,
      message: "Usuario creado correctamente",
      user: newUser
    });

  } catch (err) {
    // Si el uid ya existe (error de duplicado)
    if (err.code === 11000) {
      return res.status(400).json({
        ok: false,
        message: "Ese usuario ya existe (UID duplicado)"
      });
    }

    // Cualquier otro error
    res.status(500).json({
      ok: false,
      message: err.message
    });
  }
}

// OBTENER TODOS LOS USUARIOS
async function getUsers(req, res) {
  try {
    // Buscar todos los usuarios en la base de datos
    const users = await User.find().select('-__v').sort({ createdAt: -1 });

    // Responder con la lista
    res.json({
      ok: true,
      count: users.length,
      users
    });
  } catch (err) {
    res.status(500).json({
      ok: false,
      message: err.message
    });
  }
}

// OBTENER UN USUARIO POR UID
async function getUserByUid(req, res) {
  try {
    // req.params contiene los parámetros de la URL
    // Por ejemplo: /api/users/abc123 → uid = "abc123"
    const { uid } = req.params;

    // Buscar el usuario
    const user = await User.findOne({ uid }).select('-__v');

    // Si no existe
    if (!user) {
      return res.status(404).json({
        ok: false,
        message: "Usuario no encontrado"
      });
    }

    // Si existe, devolverlo
    res.json({
      ok: true,
      user
    });
  } catch (err) {
    res.status(500).json({
      ok: false,
      message: err.message
    });
  }
}
// ACTUALIZAR USUARIO
async function updateUser(req, res) {
  try {
    const { uid } = req.params;

    // Buscar usuario
    const user = await User.findOneAndUpdate(
      { uid },
      req.body,
      { new: true, runValidators: true }
    );

    if (!user) {
      return res.status(404).json({
        ok: false,
        message: "Usuario no encontrado"
      });
    }

    res.json({
      ok: true,
      message: "Usuario actualizado correctamente",
      user
    });

  } catch (err) {
    res.status(500).json({
      ok: false,
      message: err.message
    });
  }
}
// ELIMINAR USUARIO
async function deleteUser(req, res) {
  try {
    const { uid } = req.params;

    const deleted = await User.findOneAndDelete({ uid });

    if (!deleted) {
      return res.status(404).json({
        ok: false,
        message: "Usuario no encontrado"
      });
    }

    res.json({
      ok: true,
      message: "Usuario eliminado correctamente"
    });

  } catch (err) {
    res.status(500).json({
      ok: false,
      message: err.message
    });
  }
}


// Exportar las funciones para usarlas en las rutas
module.exports = {
  createUser,
  getUsers,
  getUserByUid,
  updateUser,
  deleteUser
};
