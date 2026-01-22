const express = require("express");
const router = express.Router();

const {
  listPosts,
  listPostsByUser,
  createPost,
  toggleLike,
  listComments,
  createComment
} = require("../controllers/communityController");

// Posts
router.get("/posts", listPosts);
router.get("/posts/user/:uid", listPostsByUser);
router.post("/posts", createPost);
router.post("/posts/:postId/like", toggleLike);

// Comments
router.get("/posts/:postId/comments", listComments);
router.post("/posts/:postId/comments", createComment);

module.exports = router;
