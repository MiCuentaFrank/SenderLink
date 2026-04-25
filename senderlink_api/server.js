// Forzar stdout/stderr sin buffer en Railway/Docker
if (process.stdout._handle) process.stdout._handle.setBlocking(true);
if (process.stderr._handle) process.stderr._handle.setBlocking(true);

// 1) Importar dependencias

const express = require("express");
const mongoose = require("mongoose");
const cors = require("cors");
const helmet = require("helmet");
const rateLimit = require("express-rate-limit");
require("dotenv").config();
const morgan = require("morgan");

const verifyToken = require("./middleware/verifyToken");


// 2) Inicializar express

const app = express();

const isProduction = process.env.NODE_ENV === "production";

// 3) Middlewares de seguridad

app.use(helmet({
  contentSecurityPolicy: {
    directives: {
      defaultSrc: ["'self'"],
      imgSrc: ["'self'", "data:", "https:"],
      styleSrc: ["'self'", "'unsafe-inline'"]
    }
  },
  hsts: { maxAge: 31536000, includeSubDomains: true, preload: true },
  referrerPolicy: { policy: "strict-origin-when-cross-origin" }
}));

// CORS: lista de orígenes permitidos (no fallback a "*")
const allowedOrigins = (process.env.CORS_ORIGIN || "").split(",").map(s => s.trim()).filter(Boolean);
app.use(cors({
  origin: allowedOrigins.length > 0 && allowedOrigins[0] !== "*" ? allowedOrigins : false
}));

// Body size limit para prevenir payloads excesivamente grandes
app.use(express.json({ limit: "10kb" }));

// Morgan: "combined" en producción, "dev" en desarrollo
app.use(morgan(isProduction ? "combined" : "dev"));

// Rate limiting global: 100 req / 15 min
const generalLimiter = rateLimit({
  windowMs: 15 * 60 * 1000,
  max: 100,
  standardHeaders: true,
  legacyHeaders: false,
  message: { ok: false, message: "Demasiadas peticiones. Intenta más tarde." }
});

// Rate limiting para escritura: 20 req / 15 min
const writeLimiter = rateLimit({
  windowMs: 15 * 60 * 1000,
  max: 20,
  standardHeaders: true,
  legacyHeaders: false,
  message: { ok: false, message: "Demasiadas peticiones de escritura. Intenta más tarde." }
});

// Rate limiting para registro: 5 registros / 15 min por IP
const registrationLimiter = rateLimit({
  windowMs: 15 * 60 * 1000,
  max: 5,
  standardHeaders: true,
  legacyHeaders: false,
  message: { ok: false, message: "Demasiados intentos de registro. Intenta más tarde." }
});

// Rate limiting para proxy de fotos: 60 req / min por IP
const photoProxyLimiter = rateLimit({
  windowMs: 60 * 1000,
  max: 60,
  standardHeaders: true,
  legacyHeaders: false,
  message: { ok: false, message: "Demasiadas peticiones de fotos. Intenta más tarde." }
});

app.use(generalLimiter);
app.use("/uploads", express.static("uploads"));


// 4) Helper: proteger solo operaciones de escritura (token + rate limit)
const protectWrites = (req, res, next) => {
  if (["POST", "PUT", "PATCH", "DELETE"].includes(req.method)) {
    return verifyToken(req, res, (err) => {
      if (err) return; // verifyToken ya respondió
      return writeLimiter(req, res, next);
    });
  }
  next();
};


// 5) Rutas

const userRoutes = require("./routes/userRoutes");
const routeRoutes = require("./routes/routeRoutes");
const messageRoutes = require("./routes/messageRoutes");
const photoRoutes = require("./routes/photoRoutes");
const communityRoutes = require("./routes/communityRoutes");
const eventRoutes = require("./routes/EventRoutes");
const groupChatRoutes = require("./routes/GroupChatRoutes");

// Comunidad: GETs públicos, escritura protegida (token + writeLimiter)
app.use("/api/community", protectWrites, communityRoutes);

// Usuarios: registro (POST /) con rate limit propio; GET y escrituras requieren token
app.use("/api/users", (req, res, next) => {
  // Registro público con rate limit específico
  if (req.method === "POST" && req.path === "/") {
    return registrationLimiter(req, res, next);
  }
  // Todos los demás requests (GET, PUT, DELETE) requieren token
  return verifyToken(req, res, (err) => {
    if (err) return;
    if (["POST", "PUT", "PATCH", "DELETE"].includes(req.method)) {
      return writeLimiter(req, res, next);
    }
    next();
  });
}, userRoutes);

// Rutas de senderismo: GETs públicos, escritura protegida
app.use("/api/routes", protectWrites, routeRoutes);

// Mensajes directos: 100% protegidos + writeLimiter en escritura
app.use("/api/messages", verifyToken, (req, res, next) => {
  if (["POST", "PUT", "PATCH", "DELETE"].includes(req.method)) {
    return writeLimiter(req, res, next);
  }
  next();
}, messageRoutes);

// Fotos: proxy público con rate limit específico
app.use("/api/photos", photoProxyLimiter, photoRoutes);

// Eventos: listado y detalle públicos; user/:uid, participating/:uid y escrituras protegidas
app.use("/api/events", (req, res, next) => {
  const isPublicGet =
    req.method === "GET" &&
    !req.path.startsWith("/user/") &&
    !req.path.startsWith("/participating/");
  if (isPublicGet) return next();
  return verifyToken(req, res, (err) => {
    if (err) return;
    if (["POST", "PUT", "PATCH", "DELETE"].includes(req.method)) {
      return writeLimiter(req, res, next);
    }
    next();
  });
}, eventRoutes);

// Chat grupal: 100% protegido + writeLimiter en escritura
app.use("/api/group-chat", verifyToken, (req, res, next) => {
  if (["POST", "PUT", "PATCH", "DELETE"].includes(req.method)) {
    return writeLimiter(req, res, next);
  }
  next();
}, groupChatRoutes);

// Health check
app.get("/api/test", (req, res) => {
  res.json({ message: "Servidor funcionando correctamente" });
});


// 6) Conexión a MongoDB

mongoose
  .connect(process.env.MONGO_URI)
  .then(() => {
    console.log("Conectado a MongoDB");

    const PORT = process.env.PORT || 3000;
    app.listen(PORT, () => {
      console.log(`Servidor Node corriendo en puerto ${PORT}`);
      console.log("DB conectada:", mongoose.connection.name);
    });
  })
  .catch((err) => {
    console.error("Error al conectar a MongoDB:", err);
    process.exit(1);
  });


// 7) Manejo de errores globales
app.use((err, req, res, next) => {
  console.error("Error interno:", err);
  res.status(500).json({
    ok: false,
    message: "Error interno del servidor"
  });
});
