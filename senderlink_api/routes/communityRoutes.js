const express = require("express");
const router = express.Router();
const multer = require("multer");
const path = require("path");

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

// Multer para fotos de posts
const postImageStorage = multer.diskStorage({
  destination: (req, file, cb) => cb(null, "uploads/posts"),
  filename: (req, file, cb) => {
    const ext = path.extname(file.originalname || ".jpg").toLowerCase();
    cb(null, `post_${Date.now()}${ext}`);
  }
});
const uploadPostMulter = multer({
  storage: postImageStorage,
  limits: { fileSize: 5 * 1024 * 1024 }, // 5MB máximo
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

// Posts
router.get("/posts", listPosts);
router.get("/posts/user/:uid", listPostsByUser);
router.post("/posts/upload-image", uploadPostMulter.single("image"), uploadPostImage);
router.post("/posts", createPost);
router.post("/posts/:postId/like", toggleLike);
router.delete("/posts/:postId", deletePost);

// Comments
router.get("/posts/:postId/comments", listComments);
router.post("/posts/:postId/comments", createComment);

module.exports = router;
