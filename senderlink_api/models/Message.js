const mongoose = require("mongoose");

const MessageSchema = new mongoose.Schema({
    chatId: {
        type: String,
        required: true,
        index: true
    },
    remitenteUid: {
        type: String,
        required: true,
        ref: 'User'
    },
    destinatarioUid: {
        type: String,
        required: true,
        ref: 'User'
    },
    texto: {
        type: String,
        required: true
    },
    leido: {
        type: Boolean,
        default: false
    },
    fechaLectura: {
        type: Date,
        default: null
    }
}, { timestamps: true });

// Índice compuesto para consultas eficientes
MessageSchema.index({ chatId: 1, createdAt: -1 });

module.exports = mongoose.model("Message", MessageSchema);