const User = require("../models/User");

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
      nombre: nombre || "",
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

    res.status(500).json({
      ok: false,
      message: err.message
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
    res.status(500).json({
      ok: false,
      message: err.message
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

// ✅ NUEVO: SUBIR FOTO DE PERFIL (multipart)
async function uploadUserPhoto(req, res) {
  try {
    const { uid } = req.params;

    if (!req.file) {
      return res.status(400).json({ ok: false, message: "No se recibió ninguna imagen" });
    }

    // URL pública del archivo (requiere app.use("/uploads", express.static("uploads")) en server.js)
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
    console.error("❌ Error subiendo foto:", err.message);
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

module.exports = {
  createUser,
  getUsers,
  getUserByUid,
  updateUser,
  updateUserProfile,
  uploadUserPhoto,
  deleteUser
};
