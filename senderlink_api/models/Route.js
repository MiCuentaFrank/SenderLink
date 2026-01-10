const mongoose = require("mongoose");

// GeoJSON Point schema
const GeoPointSchema = new mongoose.Schema(
  {
    type: {
      type: String,
      enum: ["Point"],
      default: "Point",
      required: true
    },
    coordinates: {
      type: [Number], // [lng, lat]
      required: true,
      validate: {
        validator: function (v) {
          return Array.isArray(v) && v.length === 2;
        },
        message: "coordinates debe ser [lng, lat]"
      }
    }
  },
  { _id: false }
);

const RouteSchema = new mongoose.Schema(
  {
    //  Autor (solo para USER_ROUTE; en otras puede ir vacío)
    uid: {
      type: String,
      index: true,
      default: null
    },

    //  Identificación
    externalId: { type: String, index: true, unique: false },
    code: { type: String },

    // Clasificación
    type: {
      type: String,
      enum: ["PR", "GR", "SL", "VIA_VERDE", "PARQUE_NACIONAL", "GPX_LIBRE", "USER_ROUTE"],
      required: true
    },
    source: {
      type: String,
      enum: ["FEDME", "PARQUES_NACIONALES", "USER", "PROPIO"],
      required: true
    },

    //  Info visible
    name: { type: String, required: true, trim: true },
    description: { type: String, required: true },

    //  Imágenes
    coverImage: { type: String, required: true },
    images: {
      type: [String],
      validate: {
        validator: function (v) {
          if (this.featured) return v.length >= 3 && v.length <= 5;
          return v.length === 1;
        },
        message: "Las rutas destacadas requieren 3–5 imágenes; las demás solo 1"
      },
      required: true
    },

    //  Datos técnicos
    distanceKm: { type: Number, required: true, min: 0 },
    durationMin: { type: Number, min: 0 },
    difficulty: {
      type: String,
      enum: ["FACIL", "MODERADA", "DIFICIL"],
      required: true
    },

    //  Geometría de la ruta (GeoJSON)
    geometry: {
      type: Object,
      required: true
    },

    //  GeoJSON Points (para $nearSphere)
    startPoint: { type: GeoPointSchema, required: true },
    endPoint: { type: GeoPointSchema, required: true },

    //  Localización
    startLocality: { type: String, required: true },
    comunidad: { type: String, required: true },
    provincia: { type: String },

    //  (Para /parques si quieres mantenerlo)
    parqueNacional: { type: String, default: null, index: true },

    // Destacadas
    featured: { type: Boolean, default: false, index: true },

    //  Metadatos
    fechaEdicion: { type: Date },
    extraInfo: { type: Object, default: {} }
  },
  { timestamps: true }
);

// Índices
RouteSchema.index({ provincia: 1 });
RouteSchema.index({ comunidad: 1 });
RouteSchema.index({ difficulty: 1 });
RouteSchema.index({ type: 1 });
RouteSchema.index({ distanceKm: 1 });

// Geo indices
RouteSchema.index({ geometry: "2dsphere" });
RouteSchema.index({ startPoint: "2dsphere" });

module.exports = mongoose.model("Route", RouteSchema);
