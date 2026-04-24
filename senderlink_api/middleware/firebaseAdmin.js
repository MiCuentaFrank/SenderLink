const admin = require("firebase-admin");

if (!admin.apps.length) {
  const serviceAccountB64 = process.env.FIREBASE_SERVICE_ACCOUNT_B64;

  if (!serviceAccountB64) {
    console.warn("⚠️  FIREBASE_SERVICE_ACCOUNT_B64 no configurado — verifyToken no funcionará");
  } else {
    try {
      const serviceAccount = JSON.parse(
        Buffer.from(serviceAccountB64, "base64").toString("utf8")
      );
      admin.initializeApp({
        credential: admin.credential.cert(serviceAccount)
      });
      console.log("✅ Firebase Admin inicializado");
    } catch (err) {
      console.error("❌ Error inicializando Firebase Admin:", err.message);
    }
  }
}

module.exports = admin;
