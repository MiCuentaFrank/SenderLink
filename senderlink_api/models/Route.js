const mongoose = require("mongoose");

const RouteSchema = new mongoose.Schema({
    uid: {
        type: String,
        required: true,

    },
    nombre: {
        type: String,
        required: true
    },
    descripcion: {
        type: String,
        default: ""
    },
    dificultad: {
        type: String,
        enum: ['Fácil', 'Moderada', 'Difícil', 'Muy difícil'],
        default: 'Moderada'
    },
    distancia: {
        type: Number,  // en kilómetros
        required: true
    },
    duracion: {
        type: Number,  // en minutos
        required: true
    },
    desnivel: {
        type: Number,  // en metros
        default: 0
    },
    puntos: [{
        latitud: { type: Number, required: true },
        longitud: { type: Number, required: true },
        orden: { type: Number, required: true }
    }],
    imagenPortada: {
        type: String,
        default: "",
    },
     imagenes: {
          type: [String],
          default: [],
        },
    valoracion: {
        type: Number,
        min: 0,
        max: 5,
        default: 0
    },
    numeroValoraciones: {
        type: Number,
        default: 0
    },
    provincia: {
        type: String,
        default: ""
    },
    localidad: {
        type: String,
        default: ""
    },
    activa: {
        type: Boolean,
        default: true
    }
}, { timestamps: true });

module.exports = mongoose.model("Route", RouteSchema);