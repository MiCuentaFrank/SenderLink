const mongoose = require("mongoose");

/**
 * ============================
 * GeoJSON Point Schema
 * ============================
 */
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
        validator: (v) => Array.isArray(v) && v.length === 2,
        message: "coordinates debe ser [lng, lat]"
      }
    }
  },
  { _id: false }
);

/**
 * ============================
 * Route Schema
 * ============================
 */
const RouteSchema = new mongoose.Schema(
  {
    // ============================
    // Autor (USER_ROUTE)
    // ============================
    uid: {
      type: String,
      index: true,
      default: null
    },

    // ============================
    // Identificación
    // ============================
    externalId: { type: String, index: true },
    code: { type: String },

    // ============================
    // Clasificación
    // ============================
    type: {
      type: String,
      enum: [
        "PR",
        "GR",
        "SL",
        "VIA_VERDE",
        "PARQUE_NACIONAL",
        "GPX_LIBRE",
        "USER_ROUTE"
      ],
      required: true
    },

    source: {
      type: String,
      enum: ["FEDME", "PARQUES_NACIONALES", "USER", "PROPIO"],
      required: true
    },

    // ============================
    // Info visible
    // ============================
    name: { type: String, required: true, trim: true },
    description: { type: String, required: true },

    // ============================
    // Imágenes
    // ============================
    coverImage: { type: String, required: true },

    images: {
      type: [String],
      required: true,
      validate: {
        validator: function (v) {
          if (this.featured) return v.length >= 3 && v.length <= 5;
          return v.length === 1;
        },
        message: "Las rutas destacadas requieren 3–5 imágenes; las demás solo 1"
      }
    },

    // ============================
    // Datos técnicos
    // ============================
    distanceKm: { type: Number, required: true, min: 0 },
    durationMin: { type: Number, min: 0 },

    difficulty: {
      type: String,
      enum: ["FACIL", "MODERADA", "DIFICIL"],
      required: true
    },

    // ============================
    // Geometría completa (LineString)
    // ============================
    geometry: {
      type: Object, // GeoJSON LineString
      required: true
    },

    // ============================
    // GEO Points (para búsquedas cercanas)
    // ============================
    startPointGeo: {
      type: GeoPointSchema,
      required: false
    },

    endPointGeo: {
      type: GeoPointSchema,
      required: false
    },

    // ============================
    // Localización
    // ============================
    startLocality: { type: String, required: true },
    comunidad: { type: String, required: true },
    provincia: { type: String },

    // ============================
    // Parque Nacional (opcional)
    // ============================
    parqueNacional: { type: String, default: null, index: true },

    // ============================
    // Destacadas
    // ============================
    featured: { type: Boolean, default: false, index: true },

    // ============================
    // Metadatos
    // ============================
    fechaEdicion: { type: Date },
    extraInfo: { type: Object, default: {} }
  },
  { timestamps: true }
);

/**
 * ============================
 * Índices normales
 * ============================
 */
RouteSchema.index({ provincia: 1 });
RouteSchema.index({ comunidad: 1 });
RouteSchema.index({ difficulty: 1 });
RouteSchema.index({ type: 1 });
RouteSchema.index({ distanceKm: 1 });

/**
 * ============================
 * Índices GEO (CLAVE)
 * ============================
 */
RouteSchema.index({ geometry: "2dsphere" });
RouteSchema.index({ startPointGeo: "2dsphere" });
RouteSchema.index({ endPointGeo: "2dsphere" });

module.exports = mongoose.model("Route", RouteSchema);
