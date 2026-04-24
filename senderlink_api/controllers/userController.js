const User = require("../models/User");
const { sanitizeText } = require("../utils/sanitize");

// CREAR USUARIO
async function createUser(req, res) {
  try {
    const { uid, email, nombre, foto } = req.body;

    if (!uid || !email) {
      return res.status(400).json({
        ok: false,
        message: "UID y email son obligatorios"
      });
    }

    const newUser = await User.create({
      uid,
      email,
      nombre: nombre ? sanitizeText(nombre, 100) : "",
      foto: foto || ""
    });

    res.status(201).json({
      ok: true,
      message: "Usuario creado correctamente",
      user: newUser
    });

  } catch (err) {
    if (err.code === 11000) {
      return res.status(400).json({
        ok: false,
        message: "Ese usuario ya existe (UID duplicado)"
      });
    }

    console.error("createUser error:", err.message);
    res.status(500).json({
      ok: false,
      message: "Error interno al crear usuario"
    });
  }
}

// OBTENER TODOS LOS USUARIOS
async function getUsers(req, res) {
  try {
    const users = await User.find().select("-__v").sort({ createdAt: -1 });

    res.json({
      ok: true,
      count: users.length,
      users
    });
  } catch (err) {
    console.error("getUsers error:", err.message);
    res.status(500).json({
      ok: false,
      message: "Error interno"
    });
  }
}

// OBTENER UN USUARIO POR UID
async function getUserByUid(req, res) {
  try {
    const { uid } = req.params;

    const user = await User.findOne({ uid }).select("-__v");

    if (!user) {
      return res.status(404).json({
        ok: false,
        message: "Usuario no encontrado"
      });
    }

    res.json({
      ok: true,
      user
    });
  } catch (err) {
    console.error("getUserByUid error:", err.message);
    res.status(500).json({
      ok: false,
      message: "Error interno"
    });
  }
}

// ACTUALIZAR USUARIO (filtrar campos permitidos)
async function updateUser(req, res) {
  try {
    const { uid } = req.params;

    // Auth: solo puedes actualizar tu propio usuario
    if (req.uid !== uid) {
      return res.status(403).json({
        ok: false,
        message: "No autorizado: no puedes modificar otro usuario"
      });
    }

    // Filtrar campos permitidos (evitar inyección de campos como role, isAdmin, etc.)
    const allowedFields = [
      "nombre", "foto", "bio", "comunidad", "provincia",
      "preferencias", "profileCompletion"
    ];
    const safeBody = {};
    for (const key of allowedFields) {
      if (req.body[key] !== undefined) safeBody[key] = req.body[key];
    }

    if (safeBody.nombre) safeBody.nombre = sanitizeText(safeBody.nombre, 100);
    if (safeBody.bio) safeBody.bio = sanitizeText(safeBody.bio, 500);

    const user = await User.findOneAndUpdate(
      { uid },
      safeBody,
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
    console.error("updateUser error:", err.message);
    res.status(500).json({
      ok: false,
      message: "Error interno al actualizar usuario"
    });
  }
}

// ACTUALIZAR PERFIL (solo campos editables por el usuario)
async function updateUserProfile(req, res) {
  try {
    const { uid } = req.params;

    // Auth: solo puedes actualizar tu propio perfil
    if (req.uid !== uid) {
      return res.status(403).json({
        ok: false,
        message: "No autorizado: no puedes modificar el perfil de otro usuario"
      });
    }

    const allowedFields = [
      "nombre",
      "foto",
      "bio",
      "comunidad",
      "provincia",
      "preferencias"
    ];

    const safeBody = {};
    for (const key of allowedFields) {
      if (req.body[key] !== undefined) safeBody[key] = req.body[key];
    }

    if (safeBody.nombre) safeBody.nombre = sanitizeText(safeBody.nombre, 100);
    if (safeBody.bio) safeBody.bio = sanitizeText(safeBody.bio, 500);

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
    console.error("updateUserProfile error:", err.message);
    res.status(500).json({ ok: false, message: "Error interno al actualizar perfil" });
  }
}

// SUBIR FOTO DE PERFIL (multipart)
async function uploadUserPhoto(req, res) {
  try {
    const { uid } = req.params;

    // Auth: solo puedes subir tu propia foto
    if (req.uid !== uid) {
      return res.status(403).json({ ok: false, message: "No autorizado" });
    }

    if (!req.file) {
      return res.status(400).json({ ok: false, message: "No se recibió ninguna imagen" });
    }

    // URL pública del archivo
    const photoUrl = `${req.protocol}://${req.get("host")}/uploads/users/${req.file.filename}`;

    const user = await User.findOneAndUpdate(
      { uid },
      { $set: { foto: photoUrl } },
      { new: true }
    ).select("-__v");

    if (!user) {
      return res.status(404).json({ ok: false, message: "Usuario no encontrado" });
    }

    res.json({
      ok: true,
      message: "Foto de perfil actualizada",
      photoUrl,
      user
    });

  } catch (err) {
    console.error("Error subiendo foto:", err.message);
    res.status(500).json({ ok: false, message: "Error interno al subir foto" });
  }
}

// AÑADIR XP y recalcular nivel (función interna, no expuesta como endpoint directo)
async function addXp(uid, amount) {
  try {
    const user = await User.findOne({ uid });
    if (!user) return;
    const newXp = ((user.progreso && user.progreso.xp) || 0) + amount;
    const newLevel = Math.floor(newXp / 100) + 1;
    await User.updateOne({ uid }, {
      $set: { "progreso.xp": newXp, "progreso.level": newLevel }
    });
  } catch (err) {
    console.error("addXp error:", err.message);
  }
}

// AÑADIR BADGE si el usuario no lo tiene ya (función interna)
async function addBadge(uid, badge) {
  try {
    await User.updateOne({ uid }, {
      $addToSet: { badges: badge }
    });
  } catch (err) {
    console.error("addBadge error:", err.message);
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

  const preferencias = data.preferencias || {};
  const prefNivel = (preferencias.nivel || "").trim();
  const prefTipos = Array.isArray(preferencias.tipos) ? preferencias.tipos : [];
  const prefDist = Number(preferencias.distanciaKm || 0);

  if (nombre) score += 25;
  if (foto) score += 15;
  if (bio) score += 20;
  if (comunidad || provincia) score += 20;
  if (prefNivel || prefTipos.length > 0 || prefDist > 0) score += 20;

  return Math.min(score, 100);
}

// ELIMINAR USUARIO
async function deleteUser(req, res) {
  try {
    const { uid } = req.params;

    // Auth: solo puedes eliminar tu propia cuenta
    if (req.uid !== uid) {
      return res.status(403).json({
        ok: false,
        message: "No autorizado: no puedes eliminar otro usuario"
      });
    }

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
    console.error("deleteUser error:", err.message);
    res.status(500).json({
      ok: false,
      message: "Error interno al eliminar usuario"
    });
  }
}

module.exports = {
  createUser,
  getUsers,
  getUserByUid,
  updateUser,
  updateUserProfile,
  uploadUserPhoto,
  deleteUser,
  addXp,
  addBadge
};
