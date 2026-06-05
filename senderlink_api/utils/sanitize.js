/**
 * Helpers de validación y sanitización de inputs.
 */

// Escapa caracteres especiales para uso seguro en $regex de MongoDB
function escapeRegex(str) {
  if (typeof str !== "string") return "";
  return str.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

// Limita longitud y elimina caracteres de control
function sanitizeText(str, maxLength = 5000) {
  if (typeof str !== "string") return "";
  // Eliminar caracteres de control excepto newline y tab
  const cleaned = str.replace(/[\x00-\x08\x0B\x0C\x0E-\x1F\x7F]/g, "");
  return cleaned.slice(0, maxLength);
}

// Valida formato de UID de Firebase (alfanumérico, típicamente 28 chars)
function isValidUid(uid) {
  if (typeof uid !== "string") return false;
  return /^[a-zA-Z0-9]{1,128}$/.test(uid);
}

module.exports = { escapeRegex, sanitizeText, isValidUid };
