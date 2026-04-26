const express = require("express");
const router = express.Router();
const multer = require("multer");
const path = require("path");
const { uploadToFirebase } = require("../utils/firebaseStorage");

const {
  listPosts,
  listPostsByUser,
  createPost,
  deletePost,
  toggleLike,
  listComments,
  createComment,
  uploadPostImage
} = require("../controllers/communityController");

// Extensiones de imagen permitidas
const ALLOWED_EXTENSIONS = [".jpg", ".jpeg", ".png", ".gif", ".webp"];

// Multer — memoria (Firebase Storage)
const uploadPostMulter = multer({
  storage: multer.memoryStorage(),
  limits: { fileSize: 5 * 1024 * 1024 },
  fileFilter: (req, file, cb) => {
    if (!file.mimetype || !file.mimetype.startsWith("image/")) {
      return cb(new Error("Solo se permiten imágenes"));
    }
    const ext = path.extname(file.originalname || "").toLowerCase();
    if (!ALLOWED_EXTENSIONS.includes(ext)) {
      return cb(new Error("Extensión de archivo no permitida"));
    }
    cb(null, true);
  }
});

async function uploadPostToFirebase(req, res, next) {
  if (!req.file) return next();
  try {
    const ext = path.extname(req.file.originalname || ".jpg").toLowerCase() || ".jpg";
    const destPath = `uploads/posts/post_${Date.now()}${ext}`;
    const url = await uploadToFirebase(req.file.buffer, destPath, req.file.mimetype);
    req.firebaseImageUrl = url;
    next();
  } catch (err) {
    console.error("Error subiendo imagen de post a Firebase:", err.message);
    res.status(500).json({ ok: false, message: "Error al subir la imagen" });
  }
}

// Posts
router.get("/posts", listPosts);
router.get("/posts/user/:uid", listPostsByUser);
router.post("/posts/upload-image", uploadPostMulter.single("image"), uploadPostToFirebase, uploadPostImage);
router.post("/posts", createPost);
router.post("/posts/:postId/like", toggleLike);
router.delete("/posts/:postId", deletePost);

// Comments
router.get("/posts/:postId/comments", listComments);
router.post("/posts/:postId/comments", createComment);

module.exports = router;
