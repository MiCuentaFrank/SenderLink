const admin = require("firebase-admin");

if (!admin.apps.length) {
  try {
    // Método 1: JSON completo en Base64 (limpia espacios por si acaso)
    const b64 = process.env.FIREBASE_SERVICE_ACCOUNT_B64;

    // Método 2: Variables individuales (más fácil en Railway)
    const projectId   = process.env.FIREBASE_PROJECT_ID;
    const clientEmail = process.env.FIREBASE_CLIENT_EMAIL;
    const privateKey  = process.env.FIREBASE_PRIVATE_KEY;

    if (b64) {
      const serviceAccount = JSON.parse(
        Buffer.from(b64.replace(/\s+/g, ""), "base64").toString("utf8")
      );
      admin.initializeApp({ credential: admin.credential.cert(serviceAccount) });
      console.log("✅ Firebase Admin inicializado (B64)");

    } else if (projectId && clientEmail && privateKey) {
      admin.initializeApp({
        credential: admin.credential.cert({
          projectId,
          clientEmail,
          // Railway escapa los \n como \\n — los restauramos
          privateKey: privateKey.replace(/\\n/g, "\n")
        })
      });
      console.log("✅ Firebase Admin inicializado (variables individuales)");

    } else {
      console.warn("⚠️  Firebase Admin no configurado — verifyToken no funcionará");
    }
  } catch (err) {
    console.error("❌ Error inicializando Firebase Admin:", err.message);
  }
}

module.exports = admin;
