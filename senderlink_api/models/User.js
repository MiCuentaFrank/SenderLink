const mongoose = require("mongoose");

const UserSchema = new mongoose.Schema({
    uid: {
        type: String,
        required: true,
        unique: true   // cada usuario tendrá un UID único generado por Firebase
    },
    email: {
        type: String,
        required: true
    },
    nombre: {
        type: String,
        default: ""
    },
    foto: {
        type: String,
        default: ""
    }
}, { timestamps: true }); // crea createdAt y updatedAt automáticamente

module.exports = mongoose.model("User", UserSchema);
