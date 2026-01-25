package com.senderlink.app.view.fragments

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage
import com.senderlink.app.R
import com.senderlink.app.databinding.FragmentComunidadBinding
import com.senderlink.app.model.Post
import com.senderlink.app.repository.UserRepository
import com.senderlink.app.view.adapters.CommentAdapter
import com.senderlink.app.view.adapters.PostAdapter
import com.senderlink.app.viewmodel.ComunidadViewModel

class ComunidadFragment : Fragment() {

    private var _binding: FragmentComunidadBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ComunidadViewModel by viewModels()
    private lateinit var adapter: PostAdapter

    // ✅ Para refrescar avatar del usuario actual
    private val userRepo = UserRepository()

    // ✅ Foto seleccionada para el post
    private var selectedImageUri: Uri? = null

    // ✅ Selector de imagen (galería)
    private val pickImage =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            selectedImageUri = uri
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentComunidadBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = PostAdapter(
            onLike = { post -> viewModel.toggleLike(post.id) },
            onComments = { post -> showCommentsBottomSheet(post) }
        )

        binding.rvPosts.layoutManager = LinearLayoutManager(requireContext())
        binding.rvPosts.adapter = adapter

        viewModel.posts.observe(viewLifecycleOwner) { posts ->
            adapter.submitList(posts)
        }

        viewModel.error.observe(viewLifecycleOwner) { msg ->
            if (msg != null) {
                Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
                viewModel.clearError()
            }
        }

        binding.fabCreatePost.setOnClickListener {
            showCreatePostDialog()
        }

        // ✅ 1) Carga posts
        viewModel.loadPosts()

        // ✅ 2) Aplica foto actual del usuario al adapter (para refrescar cards)
        refreshCurrentUserAvatar()
    }

    override fun onResume() {
        super.onResume()
        // ✅ Cuando vuelves desde Perfil tras cambiar foto, aquí se refresca
        refreshCurrentUserAvatar()
    }

    /**
     * ✅ Pide el usuario actual al backend y fuerza el avatar en las cards de comunidad
     */
    private fun refreshCurrentUserAvatar() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return

        userRepo.getUserByUid(uid).observe(viewLifecycleOwner) { result ->
            when (result) {
                is UserRepository.Result.Success -> {
                    val photoUrl = result.data.foto
                    // ✅ esto hace que todas las cards usen la foto nueva
                    adapter.setCurrentUserPhotoUrl(photoUrl)
                }
                is UserRepository.Result.Error -> {
                    // Silencioso para no molestar; si quieres lo mostramos.
                }
                is UserRepository.Result.Loading -> { }
            }
        }
    }

    private fun showCreatePostDialog() {
        selectedImageUri = null

        val dialogView = layoutInflater.inflate(R.layout.dialog_create_post, null)
        val et = dialogView.findViewById<EditText>(R.id.etPostText)
        val btnAddPhoto = dialogView.findViewById<TextView>(R.id.btnAddPhoto)
        val ivPreview = dialogView.findViewById<ImageView>(R.id.ivPostPreview)

        ivPreview?.isVisible = false

        btnAddPhoto?.setOnClickListener {
            pickImage.launch("image/*")
        }

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Publicar", null)
            .create()

        dialog.setOnShowListener {
            val positiveBtn = dialog.getButton(AlertDialog.BUTTON_POSITIVE)

            fun refreshPreview() {
                val uri = selectedImageUri
                if (ivPreview != null) {
                    if (uri != null) {
                        ivPreview.isVisible = true
                        ivPreview.setImageURI(uri)
                    } else {
                        ivPreview.isVisible = false
                    }
                }
            }

            refreshPreview()
            dialog.window?.decorView?.postDelayed({ refreshPreview() }, 200)

            positiveBtn.setOnClickListener {
                val text = et.text.toString().trim()
                if (text.isEmpty()) {
                    Toast.makeText(requireContext(), "Escribe algo primero 😉", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                val uid = FirebaseAuth.getInstance().currentUser?.uid
                if (uid.isNullOrBlank()) {
                    Toast.makeText(requireContext(), "No hay usuario autenticado", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                val uri = selectedImageUri
                if (uri != null) {
                    positiveBtn.isEnabled = false
                    btnAddPhoto?.isEnabled = false

                    uploadPostImageToFirebase(
                        uid = uid,
                        imageUri = uri,
                        onSuccess = { imageUrl ->
                            viewModel.createPost(text, imageUrl)
                            dialog.dismiss()
                        },
                        onError = { e ->
                            positiveBtn.isEnabled = true
                            btnAddPhoto?.isEnabled = true
                            Toast.makeText(
                                requireContext(),
                                "Error subiendo foto: ${e.message}",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    )
                } else {
                    viewModel.createPost(text, null)
                    dialog.dismiss()
                }
            }
        }

        dialog.show()

        dialog.setOnDismissListener {
            selectedImageUri = null
        }

        dialog.window?.decorView?.postDelayed(object : Runnable {
            override fun run() {
                val iv = dialogView.findViewById<ImageView>(R.id.ivPostPreview)
                val uri = selectedImageUri
                if (iv != null) {
                    if (uri != null && !iv.isVisible) {
                        iv.isVisible = true
                        iv.setImageURI(uri)
                    } else if (uri != null) {
                        iv.setImageURI(uri)
                    }
                }
                if (dialog.isShowing) {
                    dialog.window?.decorView?.postDelayed(this, 350)
                }
            }
        }, 350)
    }

    private fun uploadPostImageToFirebase(
        uid: String,
        imageUri: Uri,
        onSuccess: (String) -> Unit,
        onError: (Exception) -> Unit
    ) {
        val storageRef = Firebase.storage.reference
        val filePath = "posts/$uid/${System.currentTimeMillis()}.jpg"
        val fileRef = storageRef.child(filePath)

        fileRef.putFile(imageUri)
            .continueWithTask { task ->
                if (!task.isSuccessful) {
                    throw task.exception ?: RuntimeException("Upload failed")
                }
                fileRef.downloadUrl
            }
            .addOnSuccessListener { downloadUri ->
                onSuccess(downloadUri.toString())
            }
            .addOnFailureListener { e ->
                onError(e)
            }
    }

    private fun showCommentsBottomSheet(post: Post) {
        val dialog = BottomSheetDialog(requireContext())
        val v = layoutInflater.inflate(R.layout.bottomsheet_comments, null)
        dialog.setContentView(v)

        val rv = v.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.rvComments)
        val et = v.findViewById<EditText>(R.id.etComment)
        val btn = v.findViewById<TextView>(R.id.btnSend)

        val commentAdapter = CommentAdapter()
        rv.layoutManager = LinearLayoutManager(requireContext())
        rv.adapter = commentAdapter

        viewModel.comments.observe(viewLifecycleOwner) { comments ->
            commentAdapter.submitList(comments)
        }

        viewModel.loadComments(post.id)

        btn.setOnClickListener {
            val text = et.text.toString().trim()
            if (text.isEmpty()) return@setOnClickListener
            viewModel.createComment(post.id, text)
            et.setText("")
        }

        dialog.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
