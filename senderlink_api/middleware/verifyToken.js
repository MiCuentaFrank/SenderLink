const admin = require("./firebaseAdmin");

const verifyToken = async (req, res, next) => {
  const authHeader = req.headers["authorization"];
  if (!authHeader || !authHeader.startsWith("Bearer ")) {
    return res.status(401).json({ ok: false, message: "Token requerido" });
  }

  const idToken = authHeader.split("Bearer ")[1];

  try {
    const decodedToken = await admin.auth().verifyIdToken(idToken);
    req.uid = decodedToken.uid;
    next();
  } catch (err) {
    console.error("Token inválido:", err.message);
    return res.status(401).json({ ok: false, message: "Token inválido o expirado" });
  }
};

module.exports = verifyToken;
