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
// ACTUALIZAR PERFIL (solo campos editables por el usuario)
async function updateUserProfile(req, res) {
  try {
    const { uid } = req.params;

    // ✅ Solo permitimos estos campos (whitelist)
    const allowedFields = [
      "nombre",
      "foto",
      "bio",
      "comunidad",
      "provincia",
      "preferencias"
      // Si luego añades privacidad: "privacy"
    ];

    // Construimos un objeto seguro con solo lo permitido
    const safeBody = {};
    for (const key of allowedFields) {
      if (req.body[key] !== undefined) safeBody[key] = req.body[key];
    }

    // 🔥 Calcula el % de perfil completado
    // (regla simple, puedes ajustarla)
    const completion = calculateProfileCompletion(safeBody);
    safeBody.profileCompletion = completion;

    const user = await User.findOneAndUpdate(
      { uid },
      { $set: safeBody },
      { new: true, runValidators: true }
    ).select("-__v");

    if (!user) {
      return res.status(404).json({ ok: false, message: "Usuario no encontrado" });
    }

    res.json({
      ok: true,
      message: "Perfil actualizado correctamente",
      user
    });

  } catch (err) {
    res.status(500).json({ ok: false, message: err.message });
  }
}

// Helper: calcula el % de perfil completado
function calculateProfileCompletion(data) {
  let score = 0;

  const nombre = (data.nombre || "").trim();
  const foto = (data.foto || "").trim();
  const bio = (data.bio || "").trim();
  const comunidad = (data.comunidad || "").trim();
  const provincia = (data.provincia || "").trim();

  // preferencias puede venir como objeto
  const preferencias = data.preferencias || {};
  const prefNivel = (preferencias.nivel || "").trim();
  const prefTipos = Array.isArray(preferencias.tipos) ? preferencias.tipos : [];
  const prefDist = Number(preferencias.distanciaKm || 0);

  if (nombre) score += 25;
  if (foto) score += 15;
  if (bio) score += 20;
  if (comunidad || provincia) score += 20;

  // Preferencias cuenta si hay algo
  if (prefNivel || prefTipos.length > 0 || prefDist > 0) score += 20;

  return Math.min(score, 100);
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
  updateUserProfile,
  deleteUser
};
