// 1) Importar dependencias

const express = require("express");
const mongoose = require("mongoose");
const cors = require("cors");
require("dotenv").config();
const morgan = require("morgan");


// 2) Inicializar express

const app = express();


// 3) Middlewares

app.use(cors());
app.use(express.json());
app.use(morgan("dev")); // mostrar cada petición en consola


// 4) Rutas

const userRoutes = require("./routes/userRoutes");
const routeRoutes = require("./routes/routeRoutes");
const messageRoutes = require("./routes/messageRoutes");
const photoRoutes = require("./routes/photoRoutes");
const communityRoutes = require("./routes/communityRoutes");
const eventRoutes = require("./routes/eventRoutes");
const groupChatRoutes = require("./routes/groupChatRoutes");

app.use("/api/community", communityRoutes);
app.use("/api/users", userRoutes);
app.use("/api/routes", routeRoutes);
app.use("/api/messages", messageRoutes);
app.use("/api/photos", photoRoutes);
app.use("/api/events", eventRoutes);
app.use("/api/group-chat", groupChatRoutes);
// 5) Ruta de prueba

app.get("/api/test", (req, res) => {
  res.json({ message: "Servidor funcionando correctamente" });
});


// 6) Conexión a MongoDB
mongoose
  .connect(process.env.MONGO_URI)
  .then(() => {
    console.log("✅ Conectado a MongoDB");

    // Iniciar servidor SOLO cuando BD esté lista
    const PORT = process.env.PORT || 3000;
    app.listen(PORT, () => {
      console.log(`🚀 Servidor Node corriendo en puerto ${PORT}`);
    });
  })
  .catch((err) => {
    console.error("❌ Error al conectar a MongoDB:", err);
    process.exit(1); // Evita iniciar servidor sin BD
  });


// 7) Manejo de errores globales
app.use((err, req, res, next) => {
  console.error("❌ Error interno:", err);
  res.status(500).json({
    ok: false,
    message: "Error interno del servidor",
    error: err.message
  });
});