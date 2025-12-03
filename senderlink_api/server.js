// 1) Importar dependencias
const express = require("express");
const mongoose = require("mongoose");
const cors = require("cors");
require("dotenv").config();

// 2) Inicializar express
const app = express();

// 3) Middlewares
app.use(cors());            // permite peticiones desde Android
app.use(express.json());    // interpreta JSON recibido

// 4) Conexión a MongoDB
mongoose
  .connect(process.env.MONGO_URI)
  .then(() => console.log(" Conectado a MongoDB"))
  .catch((err) => console.error(" Error al conectar a MongoDB:", err));

// 5) Ruta de prueba (para Postman)
app.get("/api/test", (req, res) => {
  res.json({ message: "Servidor funcionando correctamente " });
});

// 6) Iniciar servidor
const PORT = process.env.PORT || 3000;
app.listen(PORT, () => {
  console.log(` Servidor Node corriendo en puerto ${PORT}`);
});
