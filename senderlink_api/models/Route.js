const mongoose = require("mongoose");

const PointSchema = new mongoose.Schema(
  {
    lat: { type: Number, required: true },
    lng: { type: Number, required: true }
  },
  { _id: false }
);

const RouteSchema = new mongoose.Schema(
  {
    // 🔑 Identificación
    externalId: {
      type: String,
      index: true,
      unique: false
    },

    code: {
      type: String
    },

    // 🏷️ Clasificación
    type: {
      type: String,
      enum: ["PR", "GR", "SL", "VIA_VERDE", "PARQUE_NACIONAL", "GPX_LIBRE","USER_ROUTE"],
      required: true
    },

    source: {
      type: String,
      enum: ["FEDME", "PARQUES_NACIONALES", "USER","PROPIO"],
      required: true
    },

    // 📛 Información visible
    name: {
      type: String,
      required: true,
      trim: true
    },

    description: {
      type: String,
      required: true
    },

    // 🖼️ Imágenes
    coverImage: {
      type: String,
      required: true
    },

    images: {
      type: [String],
      validate: {
        validator: function (v) {
          // ⭐ Featured → 3 a 5
          if (this.featured) {
            return v.length >= 3 && v.length <= 5;
          }
          // 🟡 No featured → solo 1
          return v.length === 1;
        },
        message: "Las rutas destacadas requieren 3–5 imágenes; las demás solo 1"
      },
      required: true
    },


    // 📏 Datos técnicos
    distanceKm: {
      type: Number,
      required: true,
      min: 0
    },

    durationMin: {
      type: Number,
      min: 0
    },

    difficulty: {
      type: String,
      enum: ["FACIL", "MODERADA", "DIFICIL"],
      required: true
    },

    // 🗺️ Geografía
    geometry: {
      type: Object,
      required: true
    },

    startPoint: {
      type: PointSchema,
      required: true
    },

    endPoint: {
      type: PointSchema,
      required: true
    },

    startLocality: {
      type: String,
      required: true
    },

    comunidad: {
      type: String,
      required: true
    },

    provincia: {
      type: String
    },

    // ⭐ DESTACADAS (CLAVE PARA DEMO 2)
    featured: {
      type: Boolean,
      default: false,
      index: true
    },

    // 🧩 Metadatos
    fechaEdicion: {
      type: Date
    },

    extraInfo: {
      type: Object,
      default: {}
    }
  },
  {
    timestamps: true
  }
);

// 🔍 Índices
RouteSchema.index({ provincia: 1 });
RouteSchema.index({ comunidad: 1 });
RouteSchema.index({ difficulty: 1 });
RouteSchema.index({ type: 1 });
RouteSchema.index({ distanceKm: 1 });
RouteSchema.index({ geometry: "2dsphere" });

module.exports = mongoose.model("Route", RouteSchema);
