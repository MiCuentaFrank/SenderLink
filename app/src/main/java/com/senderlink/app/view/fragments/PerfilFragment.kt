package com.senderlink.app.view.fragments

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.firebase.auth.FirebaseAuth
import com.senderlink.app.R
import com.senderlink.app.databinding.FragmentPerfilBinding
import com.senderlink.app.model.Post
import com.senderlink.app.network.CommentsResponse
import com.senderlink.app.network.CreateCommentResponse
import com.senderlink.app.repository.CommunityRepository
import com.senderlink.app.view.LoginActivity
import com.senderlink.app.view.adapters.CommentAdapter
import com.senderlink.app.view.adapters.PostAdapter
import com.senderlink.app.viewmodel.PerfilPostsViewModel
import com.senderlink.app.viewmodel.PerfilViewModel
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

/**
 * Fragment de Perfil del usuario
 *
 * FUNCIONALIDAD:
 * 1. Mostrar datos del usuario
 * 2. Botón para editar perfil -> navega a EditProfileFragment
 * 3. Botón settings -> muestra dialog
 * 4. Logout
 * 5. ✅ Mis publicaciones (backend) con PostAdapter + BottomSheet comentarios (backend)
 */
class PerfilFragment : Fragment() {

    private var _binding: FragmentPerfilBinding? = null
    private val binding get() = _binding!!

    private val viewModel: PerfilViewModel by viewModels()
    private val postsViewModel: PerfilPostsViewModel by viewModels()

    // Repo comunidad para comments (y luego likes si quieres)
    private val communityRepo = CommunityRepository()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPerfilBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupViews()
        observeViewModel()

        // ✅ Monta adapter y observers primero
        setupMyPosts()

        // Cargar datos del usuario
        viewModel.loadUserData()

        // Cargar posts reales del usuario
        postsViewModel.loadMyPosts()

        // Setup demo de listas (opcional)
        setupDemoLists()
    }

    /**
     * Configura listeners
     */
    private fun setupViews() {

        // TOOLBAR - Menú de compartir
        binding.toolbarPerfil.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_share -> {
                    Toast.makeText(
                        requireContext(),
                        "Compartir perfil (pendiente)",
                        Toast.LENGTH_SHORT
                    ).show()
                    true
                }
                else -> false
            }
        }

        // BOTÓN EDITAR
        binding.btnEditar.setOnClickListener {
            findNavController().navigate(R.id.action_perfilFragment_to_editProfileFragment)
        }

        // BOTÓN SETTINGS
        binding.btnSettings.setOnClickListener {
            showSettingsDialog()
        }

        // BOTÓN CAMBIAR VISTA
        binding.btnCambiarVista.setOnClickListener {
            Toast.makeText(requireContext(), "Cambiar vista (pendiente)", Toast.LENGTH_SHORT).show()
        }

        // VER TODOS LOS LOGROS
        binding.txtVerTodosLogros.setOnClickListener {
            Toast.makeText(requireContext(), "Ver todos los logros (pendiente)", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Observa el PerfilViewModel (usuario)
     */
    private fun observeViewModel() {

        viewModel.userResult.observe(viewLifecycleOwner) { result ->
            viewModel.handleUserResult(result)
        }

        viewModel.userData.observe(viewLifecycleOwner) { user ->
            if (user != null) {
                binding.txtNombre.text =
                    if (user.nombre.isNotEmpty()) user.nombre else "Usuario sin nombre"

                val ubicacion = listOfNotNull(user.comunidad, user.provincia)
                    .joinToString(", ")
                    .ifBlank { "España" }

                binding.txtSubtitulo.text = "Explorador · $ubicacion"
            } else {
                binding.txtNombre.text = "Usuario"
                binding.txtSubtitulo.text = "Explorador · España"
            }
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.btnEditar.isEnabled = !isLoading
            binding.btnSettings.isEnabled = !isLoading
        }

        viewModel.errorMessage.observe(viewLifecycleOwner) { error ->
            if (error != null) {
                Toast.makeText(requireContext(), error, Toast.LENGTH_LONG).show()
                viewModel.clearError()
            }
        }
    }

    /**
     * ✅ Mis publicaciones (BACKEND)
     */
    private fun setupMyPosts() {

        val adapter = PostAdapter(
            onLike = {
                // ✅ Lo dejamos pendiente hasta el back de likes en perfil, o lo activamos luego
                Toast.makeText(requireContext(), "Like (pendiente)", Toast.LENGTH_SHORT).show()
            },
            onComments = { post ->
                showCommentsBottomSheet(post)
            }
        )

        binding.rvMisPublicaciones.layoutManager = LinearLayoutManager(requireContext())
        binding.rvMisPublicaciones.adapter = adapter

        postsViewModel.myPosts.observe(viewLifecycleOwner) { posts ->
            adapter.submitList(posts)
        }

        postsViewModel.error.observe(viewLifecycleOwner) { msg ->
            if (msg != null) {
                Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
                postsViewModel.clearError()
            }
        }

        binding.txtVerMisPublicaciones.setOnClickListener {
            Toast.makeText(requireContext(), "Ver todo (pendiente)", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * ✅ BottomSheet comentarios (BACKEND)
     */
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

        // 1) Cargar comentarios reales
        communityRepo.getComments(post.id).enqueue(object : Callback<CommentsResponse> {
            override fun onResponse(
                call: Call<CommentsResponse>,
                response: Response<CommentsResponse>
            ) {
                if (response.isSuccessful && response.body()?.ok == true) {
                    commentAdapter.submitList(response.body()?.data ?: emptyList())
                } else {
                    Toast.makeText(requireContext(), "No se pudieron cargar comentarios", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onFailure(call: Call<CommentsResponse>, t: Throwable) {
                Toast.makeText(requireContext(), t.message ?: "Error de red", Toast.LENGTH_SHORT).show()
            }
        })

        // 2) Enviar comentario real
        btn.setOnClickListener {
            val text = et.text.toString().trim()
            if (text.isEmpty()) return@setOnClickListener

            val user = FirebaseAuth.getInstance().currentUser
            val uid = user?.uid
            val userName = user?.displayName ?: "Yo"

            if (uid.isNullOrBlank()) {
                Toast.makeText(requireContext(), "No hay usuario autenticado", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            communityRepo.createComment(post.id, uid, text)

                .enqueue(object : Callback<CreateCommentResponse> {
                    override fun onResponse(
                        call: Call<CreateCommentResponse>,
                        response: Response<CreateCommentResponse>
                    ) {
                        if (response.isSuccessful && response.body()?.ok == true) {
                            et.setText("")

                            // Recargar comentarios
                            communityRepo.getComments(post.id).enqueue(object : Callback<CommentsResponse> {
                                override fun onResponse(
                                    call: Call<CommentsResponse>,
                                    response: Response<CommentsResponse>
                                ) {
                                    if (response.isSuccessful && response.body()?.ok == true) {
                                        commentAdapter.submitList(response.body()?.data ?: emptyList())
                                    }
                                }

                                override fun onFailure(call: Call<CommentsResponse>, t: Throwable) {}
                            })

                            // Recargar posts para refrescar commentsCount
                            postsViewModel.loadMyPosts()

                        } else {
                            Toast.makeText(requireContext(), "No se pudo comentar", Toast.LENGTH_SHORT).show()
                        }
                    }

                    override fun onFailure(call: Call<CreateCommentResponse>, t: Throwable) {
                        Toast.makeText(requireContext(), t.message ?: "Error de red", Toast.LENGTH_SHORT).show()
                    }
                })
        }

        dialog.show()
    }

    /**
     * Dialog settings
     */
    private fun showSettingsDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_settings, null)

        val dialog = androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .create()

        dialogView.findViewById<View>(R.id.optionEditProfile).setOnClickListener {
            dialog.dismiss()
            findNavController().navigate(R.id.action_perfilFragment_to_editProfileFragment)
        }

        dialogView.findViewById<View>(R.id.optionLogout).setOnClickListener {
            dialog.dismiss()
            confirmLogout()
        }

        dialog.show()
    }

    private fun confirmLogout() {
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Cerrar sesión")
            .setMessage("¿Seguro que quieres cerrar sesión?")
            .setPositiveButton("Sí") { _, _ -> logout() }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun logout() {
        FirebaseAuth.getInstance().signOut()
        val intent = Intent(requireContext(), LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        activity?.finish()
    }

    private fun setupDemoLists() {
        // Por ahora dejamos vacío
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
