const mongoose = require("mongoose");

const UserSchema = new mongoose.Schema({
  uid: { type: String, required: true, unique: true },
  email: { type: String, required: true },

  // Perfil básico
  nombre: { type: String, default: "" },
  foto: { type: String, default: "" },
  bio: { type: String, default: "" },

  // Ubicación (opcional)
  comunidad: { type: String, default: "" },
  provincia: { type: String, default: "" },

  // Preferencias (para personalizar rutas)
  preferencias: {
    nivel: {
      type: String,
      enum: ["BEGINNER", "INTERMEDIATE", "ADVANCED", "EXPERT", ""],
      default: ""
    },
    tipos: { type: [String], default: [] },     // ["MONTAÑA","COSTA","BOSQUE"...]
    distanciaKm: { type: Number, default: 0 }   // distancia típica (0 = sin definir)
  },

  // Progreso / ranking (gamificación)
  progreso: {
    level: { type: Number, default: 1 },
    xp: { type: Number, default: 0 },
    rankTitle: { type: String, default: "Explorer" }
  },

  // Logros (ids simples)
  badges: { type: [String], default: [] },

  // Stats
  stats: {
    routesPublished: { type: Number, default: 0 },
    routesCompleted: { type: Number, default: 0 },
    totalKm: { type: Number, default: 0 },
    streakDays: { type: Number, default: 0 }
  },

  // Para “Completa tu perfil”
  profileCompletion: { type: Number, default: 0 } // 0..100
}, { timestamps: true });

module.exports = mongoose.model("User", UserSchema);
