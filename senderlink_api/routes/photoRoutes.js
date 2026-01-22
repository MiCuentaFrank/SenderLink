const express = require("express");
const axios = require("axios");

const router = express.Router();

// GET /api/photos/places?ref=XXXX&maxwidth=1200
router.get("/places", async (req, res) => {
  try {
    const ref = req.query.ref;
    const maxwidth = req.query.maxwidth || 1200;

    if (!ref) {
      return res.status(400).json({ ok: false, message: "Falta ref" });
    }

    const key = process.env.GOOGLE_MAPS_KEY || process.env.GOOGLE_API_KEY;
    if (!key) {
      return res.status(500).json({ ok: false, message: "Falta GOOGLE_MAPS_KEY/GOOGLE_API_KEY en .env" });
    }

    const url =
      `https://maps.googleapis.com/maps/api/place/photo` +
      `?maxwidth=${encodeURIComponent(maxwidth)}` +
      `&photoreference=${encodeURIComponent(ref)}` +
      `&key=${encodeURIComponent(key)}`;

    const r = await axios.get(url, {
      responseType: "stream",
      timeout: 12000,
      maxRedirects: 5
    });

    if (r.headers["content-type"]) {
      res.setHeader("Content-Type", r.headers["content-type"]);
    }

    r.data.pipe(res);
  } catch (err) {
    const status = err.response?.status || 500;
    console.error("❌ places photo error:", status, err.message);
    res.status(status).json({ ok: false, message: "No se pudo cargar la foto" });
  }
});

module.exports = router;
