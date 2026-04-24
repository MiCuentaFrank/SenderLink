const Post = require("../models/Post");
const Comment = require("../models/Comment");
const User = require("../models/User");
const { addXp, addBadge } = require("./userController");
const { sanitizeText } = require("../utils/sanitize");

// Helper: respuesta estándar
function ok(res, data, message = "OK") {
  return res.json({ ok: true, message, data });
}

function fail(res, status, message) {
  return res.status(status).json({ ok: false, message });
}

// GET /api/community/posts?limit=20&skip=0
async function listPosts(req, res) {
  try {
    const limit = Math.min(parseInt(req.query.limit || "20", 10), 50);
    const skip = parseInt(req.query.skip || "0", 10);

    const posts = await Post.find()
      .sort({ createdAt: -1 })
      .skip(skip)
      .limit(limit)
      .lean();

    const postsWithCounts = posts.map((p) => ({
      ...p,
      likesCount: (p.likedBy || []).length
    }));

    return ok(res, postsWithCounts);
  } catch (e) {
    console.error("listPosts error:", e.message);
    return fail(res, 500, "Error listando posts");
  }
}

// GET /api/community/posts/user/:uid
async function listPostsByUser(req, res) {
  try {
    const { uid } = req.params;
    if (!uid) return fail(res, 400, "uid requerido");

    const posts = await Post.find({ uid })
      .sort({ createdAt: -1 })
      .lean();

    const postsWithCounts = posts.map((p) => ({
      ...p,
      likesCount: (p.likedBy || []).length
    }));

    return ok(res, postsWithCounts);
  } catch (e) {
    console.error("listPostsByUser error:", e.message);
    return fail(res, 500, "Error listando posts del usuario");
  }
}

async function createPost(req, res) {
  try {
    const { uid, text, image, routeId } = req.body;

    if (!uid || !text) {
      return fail(res, 400, "uid y text son obligatorios");
    }

    // Auth: solo puedes crear posts como tú mismo
    if (req.uid !== uid) {
      return fail(res, 403, "No autorizado: no puedes crear posts en nombre de otro usuario");
    }

    const textSanitizado = sanitizeText(text, 5000);
    if (textSanitizado.trim().length === 0) {
      return fail(res, 400, "El texto del post no puede estar vacío");
    }

    const user = await User.findOne({ uid }).lean();
    if (!user) return fail(res, 404, "Usuario no encontrado");

    const userNameFinal = user.nombre || "Anónimo";
    const userPhotoFinal = user.foto || "";

    const post = await Post.create({
      uid,
      userName: userNameFinal,
      userPhoto: userPhotoFinal,
      text: textSanitizado,
      image: image || "",
      routeId: routeId || null,
      likedBy: []
    });

    const postCount = await Post.countDocuments({ uid });
    await addXp(uid, 10);
    if (postCount === 1) {
      await addBadge(uid, "FIRST_POST");
    }

    return ok(res, post, "Post creado");
  } catch (e) {
    console.error("createPost error:", e.message);
    return fail(res, 500, "Error creando post");
  }
}


// POST /api/community/posts/:postId/like
// body: { uid }  -> toggle like
async function toggleLike(req, res) {
  try {
    const { postId } = req.params;
    const { uid } = req.body;

    if (!uid) return fail(res, 400, "uid requerido");

    // Auth: solo puedes dar like como tú mismo
    if (req.uid !== uid) {
      return fail(res, 403, "No autorizado");
    }

    const post = await Post.findById(postId);
    if (!post) return fail(res, 404, "Post no encontrado");

    const idx = post.likedBy.indexOf(uid);
    let liked;

    if (idx >= 0) {
      post.likedBy.splice(idx, 1);
      liked = false;
    } else {
      post.likedBy.push(uid);
      liked = true;
    }

    await post.save();

    return ok(
      res,
      { postId, liked, likesCount: post.likedBy.length },
      "Like actualizado"
    );
  } catch (e) {
    console.error("toggleLike error:", e.message);
    return fail(res, 500, "Error en like");
  }
}

// GET /api/community/posts/:postId/comments
async function listComments(req, res) {
  try {
    const { postId } = req.params;

    const comments = await Comment.find({ postId })
      .sort({ createdAt: -1 })
      .lean();

    return ok(res, comments);
  } catch (e) {
    console.error("listComments error:", e.message);
    return fail(res, 500, "Error listando comentarios");
  }
}

async function createComment(req, res) {
  try {
    const { postId } = req.params;
    const { uid, text } = req.body;

    if (!uid || !text) {
      return fail(res, 400, "uid y text son obligatorios");
    }

    // Auth: solo puedes comentar como tú mismo
    if (req.uid !== uid) {
      return fail(res, 403, "No autorizado: no puedes comentar en nombre de otro usuario");
    }

    const textSanitizado = sanitizeText(text, 1000);
    if (textSanitizado.trim().length === 0) {
      return fail(res, 400, "El comentario no puede estar vacío");
    }

    const post = await Post.findById(postId);
    if (!post) {
      return fail(res, 404, "Post no encontrado");
    }

    const user = await User.findOne({ uid }).lean();
    if (!user) {
      return fail(res, 404, "Usuario no encontrado");
    }

    const userNameFinal = user.nombre || "Anónimo";
    const userPhotoFinal = user.foto || "";

    const comment = await Comment.create({
      postId,
      uid,
      userName: userNameFinal,
      userPhoto: userPhotoFinal,
      text: textSanitizado
    });

    await Post.findByIdAndUpdate(
      postId,
      { $inc: { commentsCount: 1 } },
      { new: true }
    );

    return ok(res, comment, "Comentario creado");

  } catch (error) {
    console.error("createComment error:", error.message);
    return fail(res, 500, "Error creando comentario");
  }
}


// DELETE /api/community/posts/:postId
// body: { uid }  -> solo el autor puede borrar su post
async function deletePost(req, res) {
  try {
    const { postId } = req.params;
    const { uid } = req.body;

    if (!uid) return fail(res, 400, "uid requerido");

    // Auth: verificar con el token
    if (req.uid !== uid) {
      return fail(res, 403, "No autorizado");
    }

    const post = await Post.findById(postId);
    if (!post) return fail(res, 404, "Post no encontrado");
    if (post.uid !== uid) return fail(res, 403, "No autorizado");

    await Comment.deleteMany({ postId });
    await post.deleteOne();

    return ok(res, { postId }, "Post eliminado");
  } catch (e) {
    console.error("deletePost error:", e.message);
    return fail(res, 500, "Error eliminando post");
  }
}

// POST /api/community/posts/upload-image  (multipart: field "image")
async function uploadPostImage(req, res) {
  try {
    if (!req.file) return fail(res, 400, "No se recibió ninguna imagen");
    const imageUrl = `${req.protocol}://${req.get("host")}/uploads/posts/${req.file.filename}`;
    return ok(res, { imageUrl }, "Imagen subida");
  } catch (e) {
    console.error("uploadPostImage error:", e.message);
    return fail(res, 500, "Error subiendo imagen");
  }
}

module.exports = {
  listPosts,
  listPostsByUser,
  createPost,
  deletePost,
  toggleLike,
  listComments,
  createComment,
  uploadPostImage
};
