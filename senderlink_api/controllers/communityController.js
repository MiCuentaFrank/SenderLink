const Post = require("../models/Post");
const Comment = require("../models/Comment");
const User = require("../models/User");

// Helper: respuesta estándar
function ok(res, data, message = "OK") {
  return res.json({ ok: true, message, data });
}

function fail(res, status, message) {
  return res.status(status).json({ ok: false, message });
}

// Utilidad: sacar nombre/foto del user con fallbacks
function pickUserName(user) {
  return (
    user?.userName ||
    user?.name ||
    user?.username ||
    user?.displayName ||
    "Anónimo"
  );
}

function pickUserPhoto(user) {
  return (
    user?.photo ||
    user?.userPhoto ||
    user?.avatar ||
    user?.profilePhoto ||
    ""
  );
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

    // añadimos likesCount (porque virtual en lean no sale)
    const postsWithCounts = posts.map((p) => ({
      ...p,
      likesCount: (p.likedBy || []).length
    }));

    return ok(res, postsWithCounts);
  } catch (e) {
    console.error("listPosts error:", e);
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
    console.error("listPostsByUser error:", e);
    return fail(res, 500, "Error listando posts del usuario");
  }
}

async function createPost(req, res) {
  try {
    const { uid, text, image, routeId } = req.body;

    if (!uid || !text) {
      return fail(res, 400, "uid y text son obligatorios");
    }

    // ✅ 1) Buscar usuario real
    const user = await User.findOne({ uid }).lean();
    if (!user) return fail(res, 404, "Usuario no encontrado");

    // ✅ 2) Sacar datos del perfil
    const userNameFinal = user.nombre || "Anónimo";
    const userPhotoFinal = user.foto || "";

    // ✅ 3) Crear post
    const post = await Post.create({
      uid,
      userName: userNameFinal,
      userPhoto: userPhotoFinal,
      text,
      image: image || "",
      routeId: routeId || null,
      likedBy: []
    });

    return ok(res, post, "Post creado");
  } catch (e) {
    console.error("createPost error:", e);
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
    console.error("toggleLike error:", e);
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
    console.error("listComments error:", e);
    return fail(res, 500, "Error listando comentarios");
  }
}

async function createComment(req, res) {
  try {
    const { postId } = req.params;
    const { uid, text } = req.body;

    if (!uid || !text) {
      return res.status(400).json({
        ok: false,
        message: "uid y text son obligatorios"
      });
    }

    // 1️⃣ Comprobar que existe el post
    const post = await Post.findById(postId);
    if (!post) {
      return res.status(404).json({
        ok: false,
        message: "Post no encontrado"
      });
    }

    // ✅ 2) Buscar usuario real
    const user = await User.findOne({ uid }).lean();
    if (!user) {
      return res.status(404).json({
        ok: false,
        message: "Usuario no encontrado"
      });
    }

    const userNameFinal = user.nombre || "Anónimo";
    const userPhotoFinal = user.foto || "";

    // 3️⃣ Crear comentario
    const comment = await Comment.create({
      postId,
      uid,
      userName: userNameFinal,
      userPhoto: userPhotoFinal,
      text
    });

    // 4️⃣ Incrementar contador de comentarios
    await Post.findByIdAndUpdate(
      postId,
      { $inc: { commentsCount: 1 } },
      { new: true }
    );

    return res.json({
      ok: true,
      message: "Comentario creado",
      data: comment
    });

  } catch (error) {
    console.error("createComment error:", error);
    return res.status(500).json({
      ok: false,
      message: "Error creando comentario"
    });
  }
}


module.exports = {
  listPosts,
  listPostsByUser,
  createPost,
  toggleLike,
  listComments,
  createComment
};
