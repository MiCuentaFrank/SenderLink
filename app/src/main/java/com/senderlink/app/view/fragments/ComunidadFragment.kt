package com.senderlink.app.view.fragments

import android.app.Activity
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
import com.yalantis.ucrop.UCrop
import java.io.File
import com.senderlink.app.R
import com.senderlink.app.databinding.FragmentComunidadBinding
import com.senderlink.app.model.Post
import com.senderlink.app.repository.UserRepository
import com.senderlink.app.utils.UserManager
import com.senderlink.app.view.adapters.CommentAdapter
import com.senderlink.app.view.adapters.PostAdapter
import com.senderlink.app.viewmodel.ComunidadViewModel

class ComunidadFragment : Fragment() {

    private var _binding: FragmentComunidadBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ComunidadViewModel by viewModels()
    private lateinit var adapter: PostAdapter

    // ✅ Foto seleccionada para el post
    private var selectedImageUri: Uri? = null

    // Callback para actualizar el preview del diálogo cuando se selecciona imagen
    private var onImagePicked: ((Uri?) -> Unit)? = null

    // Recibe el resultado del crop y actualiza el preview del diálogo
    private val cropPostLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val croppedUri = UCrop.getOutput(result.data ?: return@registerForActivityResult)
                    ?: return@registerForActivityResult
                selectedImageUri = croppedUri
                onImagePicked?.invoke(croppedUri)
            }
        }

    // Abre galería → lanza uCrop (libre, sin ratio fijo)
    private val pickImage =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) {
                val destUri = Uri.fromFile(
                    File(requireContext().cacheDir, "crop_post_${System.currentTimeMillis()}.jpg")
                )
                val intent = UCrop.of(uri, destUri)
                    .withMaxResultSize(1080, 1080)
                    .getIntent(requireContext())
                cropPostLauncher.launch(intent)
            }
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

        // ✅ 2) Aplica foto actual del usuario al adapter — usa caché de UserManager,
        //    se actualiza automáticamente si el usuario cambia su foto en otro fragment.
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid != null) {
            UserManager.getInstance().currentUser.observe(viewLifecycleOwner) { user ->
                adapter.setCurrentUserPhotoUrl(user?.foto, uid)
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

        // Callback que se invoca desde pickImage cuando el usuario selecciona imagen
        onImagePicked = { uri ->
            if (ivPreview != null) {
                if (uri != null) {
                    ivPreview.isVisible = true
                    ivPreview.setImageURI(uri)
                } else {
                    ivPreview.isVisible = false
                }
            }
        }

        dialog.setOnShowListener {
            val positiveBtn = dialog.getButton(AlertDialog.BUTTON_POSITIVE)

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
                        onError = {
                            positiveBtn.isEnabled = true
                            btnAddPhoto?.isEnabled = true
                            Toast.makeText(requireContext(), "Error subiendo foto", Toast.LENGTH_LONG).show()
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
            onImagePicked = null
        }
    }

    private fun uploadPostImageToFirebase(
        uid: String,
        imageUri: Uri,
        onSuccess: (String) -> Unit,
        onError: () -> Unit
    ) {
        val storageRef = Firebase.storage.reference
        val fileRef = storageRef.child("posts/$uid/${System.currentTimeMillis()}.jpg")

        fileRef.putFile(imageUri)
            .continueWithTask { task ->
                if (!task.isSuccessful) throw task.exception ?: RuntimeException("Upload failed")
                fileRef.downloadUrl
            }
            .addOnSuccessListener { downloadUri -> onSuccess(downloadUri.toString()) }
            .addOnFailureListener { onError() }
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
        onImagePicked = null
        _binding = null
    }
}
