const mongoose = require("mongoose");

const PostSchema = new mongoose.Schema(
  {
    // 🔐 Usuario
    uid: { type: String, required: true, index: true },     // Firebase UID
    userName: { type: String, required: true },
    userPhoto: { type: String, default: "" },

    // 📝 Contenido
    text: { type: String, required: true, trim: true, maxlength: 800 },
    image: { type: String, default: "" },

    // 🗺️ Ruta asociada (opcional)
    routeId: {
      type: mongoose.Schema.Types.ObjectId,
      ref: "Route",
      default: null
    },

    // ❤️ Likes
    likedBy: { type: [String], default: [] },

    // 💬 CONTADOR DE COMENTARIOS (CLAVE)
    commentsCount: { type: Number, default: 0 }
  },
  { timestamps: true }
);

// 👍 Virtual para likes (no se guarda en BD)
PostSchema.virtual("likesCount").get(function () {
  return this.likedBy.length;
});

// Necesario para que salgan los virtuals en JSON
PostSchema.set("toJSON", { virtuals: true });
PostSchema.set("toObject", { virtuals: true });

module.exports = mongoose.model("Post", PostSchema);
