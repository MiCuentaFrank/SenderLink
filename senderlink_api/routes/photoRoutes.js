const express = require("express");
const axios = require("axios");
const admin = require("../middleware/firebaseAdmin");

const router = express.Router();

// GET /api/photos/places?ref=XXXX&maxwidth=1200
router.get("/places", async (req, res) => {
  try {
    const ref = req.query.ref;
    const maxwidth = parseInt(req.query.maxwidth, 10) || 1200;

    if (!ref || typeof ref !== "string") {
      return res.status(400).json({ ok: false, message: "Falta ref" });
    }

    // Validar formato de photoreference (alfanumérico + algunos caracteres especiales, longitud razonable)
    if (ref.length > 500 || !/^[a-zA-Z0-9_\-]+$/.test(ref)) {
      return res.status(400).json({ ok: false, message: "Formato de ref inválido" });
    }

    // Validar maxwidth en rango razonable
    if (maxwidth < 1 || maxwidth > 4800) {
      return res.status(400).json({ ok: false, message: "maxwidth debe estar entre 1 y 4800" });
    }

    const key = process.env.GOOGLE_MAPS_KEY || process.env.GOOGLE_API_KEY;
    if (!key) {
      return res.status(500).json({ ok: false, message: "Configuración del servidor incompleta" });
    }

    const url =
      `https://maps.googleapis.com/maps/api/place/photo` +
      `?maxwidth=${encodeURIComponent(maxwidth)}` +
      `&photoreference=${encodeURIComponent(ref)}` +
      `&key=${encodeURIComponent(key)}`;

    const r = await axios.get(url, {
      responseType: "stream",
      timeout: 12000,
      maxRedirects: 2,
      maxContentLength: 10 * 1024 * 1024 // 10MB máximo
    });

    const contentType = r.headers["content-type"] || "";
    if (!contentType.startsWith("image/")) {
      return res.status(502).json({ ok: false, message: "Tipo de contenido inválido recibido del proveedor" });
    }
    res.setHeader("Content-Type", contentType);
    // Cache de imágenes por 1 hora
    res.setHeader("Cache-Control", "public, max-age=3600");

    r.data.pipe(res);
  } catch (err) {
    const status = err.response?.status || 500;
    console.error("Photo proxy error:", status);
    res.status(status).json({ ok: false, message: "No se pudo cargar la foto" });
  }
});

// GET /api/photos/storage?path=uploads/routes/filename.jpg
// Proxy para Firebase Storage — evita 403 por App Check / reglas de seguridad.
// El Admin SDK tiene acceso completo sin necesitar App Check.
router.get("/storage", async (req, res) => {
  const storagePath = req.query.path;

  if (!storagePath || typeof storagePath !== "string") {
    return res.status(400).json({ ok: false, message: "Falta path" });
  }

  // Validar: solo caracteres seguros, sin path traversal
  if (
    storagePath.length > 500 ||
    storagePath.includes("..") ||
    !/^[a-zA-Z0-9/_\-.]+$/.test(storagePath)
  ) {
    return res.status(400).json({ ok: false, message: "Path inválido" });
  }

  try {
    const bucket = admin.storage().bucket();
    const file = bucket.file(storagePath);

    const [exists] = await file.exists();
    if (!exists) {
      return res.status(404).json({ ok: false, message: "Archivo no encontrado" });
    }

    const [metadata] = await file.getMetadata();
    const contentType = metadata.contentType || "image/jpeg";

    if (!contentType.startsWith("image/")) {
      return res.status(403).json({ ok: false, message: "Solo se permiten imágenes" });
    }

    res.setHeader("Content-Type", contentType);
    res.setHeader("Cache-Control", "public, max-age=86400"); // 24h

    file.createReadStream()
      .on("error", (streamErr) => {
        console.error("Storage stream error:", streamErr.message);
        if (!res.headersSent) {
          res.status(500).json({ ok: false, message: "Error al cargar el archivo" });
        }
      })
      .pipe(res);
  } catch (err) {
    console.error("Storage proxy error:", err.message);
    res.status(500).json({ ok: false, message: "Error al cargar el archivo" });
  }
});

module.exports = router;
