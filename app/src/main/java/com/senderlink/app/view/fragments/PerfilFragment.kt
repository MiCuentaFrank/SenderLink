package com.senderlink.app.view.fragments

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.firebase.auth.FirebaseAuth
import com.senderlink.app.R
import com.senderlink.app.databinding.FragmentPerfilBinding
import com.senderlink.app.model.Post
import com.senderlink.app.network.CommentsResponse
import com.senderlink.app.network.CreateCommentResponse
import com.senderlink.app.repository.CommunityRepository
import com.senderlink.app.repository.UserRepository
import com.senderlink.app.view.LoginActivity
import com.senderlink.app.view.adapters.CommentAdapter
import com.senderlink.app.view.adapters.PostAdapter
import com.senderlink.app.viewmodel.PerfilPostsViewModel
import com.senderlink.app.viewmodel.PerfilViewModel
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class PerfilFragment : Fragment() {

    private var _binding: FragmentPerfilBinding? = null
    private val binding get() = _binding!!

    private val viewModel: PerfilViewModel by viewModels()
    private val postsViewModel: PerfilPostsViewModel by viewModels()
    private lateinit var myPostsAdapter: PostAdapter

    private val communityRepo = CommunityRepository()

    private val pickImageLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) {
                // ✅ 1) Preview inmediato
                Glide.with(this)
                    .load(uri)
                    .placeholder(R.drawable.bg_avatar_circle)
                    .error(R.drawable.bg_avatar_circle)
                    .into(binding.imgFotoPerfil)

                // ✅ 2) Subir al backend
                viewModel.updateProfilePhoto(requireContext(), uri)
            }
        }

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

        // ✅ Si no hay toolbar en el fragment, al menos ponemos título en el Activity
        requireActivity().title = "Perfil"

        setupMenu()      // ✅ reemplaza a toolbarPerfil menu
        setupViews()
        observeViewModel()

        binding.btnEditFoto.setOnClickListener { pickImageLauncher.launch("image/*") }
        binding.imgFotoPerfil.setOnClickListener { pickImageLauncher.launch("image/*") }

        setupMyPosts()

        viewModel.loadUserData()
        postsViewModel.loadMyPosts()

        setupDemoLists()
    }

    /**
     * ✅ Menú del fragment sin Toolbar propia
     * (aparece en la TopBar/ActionBar del Activity si existe)
     */
    private fun setupMenu() {
        val menuHost: MenuHost = requireActivity()

        menuHost.addMenuProvider(object : MenuProvider {

            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menu.clear()
                // OJO: usa tu menú real de perfil (si se llama distinto, cámbialo aquí)
                menuInflater.inflate(R.menu.menu_perfil, menu)
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                return when (menuItem.itemId) {
                    R.id.action_share -> {
                        Toast.makeText(requireContext(), "Compartir perfil (pendiente)", Toast.LENGTH_SHORT).show()
                        true
                    }
                    else -> false
                }
            }

        }, viewLifecycleOwner, Lifecycle.State.RESUMED)
    }

    private fun setupViews() {
        binding.btnEditar.setOnClickListener {
            findNavController().navigate(R.id.action_perfilFragment_to_editProfileFragment)
        }

        binding.btnSettings.setOnClickListener {
            showSettingsDialog()
        }

        binding.btnCambiarVista.setOnClickListener {
            Toast.makeText(requireContext(), "Cambiar vista (pendiente)", Toast.LENGTH_SHORT).show()
        }

        binding.txtVerTodosLogros.setOnClickListener {
            Toast.makeText(requireContext(), "Ver todos los logros (pendiente)", Toast.LENGTH_SHORT).show()
        }
    }

    private fun observeViewModel() {
        viewModel.userResult.observe(viewLifecycleOwner) { result ->
            viewModel.handleUserResult(result)
        }

        viewModel.userData.observe(viewLifecycleOwner) { user ->
            if (user != null) {
                binding.txtNombre.text = if (user.nombre.isNotEmpty()) user.nombre else "Usuario sin nombre"

                val ubicacion = listOfNotNull(user.comunidad, user.provincia)
                    .joinToString(", ")
                    .ifBlank { "España" }

                binding.txtSubtitulo.text = "Explorador · $ubicacion"

                Glide.with(this)
                    .load(user.foto)
                    .placeholder(R.drawable.bg_avatar_circle)
                    .error(R.drawable.bg_avatar_circle)
                    .into(binding.imgFotoPerfil)

            } else {
                binding.txtNombre.text = "Usuario"
                binding.txtSubtitulo.text = "Explorador · España"

                Glide.with(this)
                    .load(null as String?)
                    .placeholder(R.drawable.bg_avatar_circle)
                    .error(R.drawable.bg_avatar_circle)
                    .into(binding.imgFotoPerfil)
            }
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.btnEditar.isEnabled = !isLoading
            binding.btnSettings.isEnabled = !isLoading
            binding.btnEditFoto.isEnabled = !isLoading
            binding.imgFotoPerfil.isEnabled = !isLoading
        }

        viewModel.errorMessage.observe(viewLifecycleOwner) { error ->
            if (error != null) {
                Toast.makeText(requireContext(), error, Toast.LENGTH_LONG).show()
                viewModel.clearError()
            }
        }

        // ✅ Resultado subida foto
        viewModel.updatePhotoResult.observe(viewLifecycleOwner) { result ->
            when (result) {
                is UserRepository.Result.Success -> {
                    Toast.makeText(requireContext(), "Foto actualizada ✅", Toast.LENGTH_SHORT).show()

                    viewModel.handleUpdatePhotoResult(result)

                    // ✅ refresca cards inmediatamente
                    myPostsAdapter.setCurrentUserPhotoUrl(result.data.foto)

                    // ✅ refresca posts desde backend
                    postsViewModel.loadMyPosts()
                }

                is UserRepository.Result.Error -> {
                    Toast.makeText(requireContext(), result.message, Toast.LENGTH_SHORT).show()
                    viewModel.handleUpdatePhotoResult(result)
                }

                is UserRepository.Result.Loading -> {
                    viewModel.handleUpdatePhotoResult(result)
                }
            }
        }
    }

    private fun setupMyPosts() {
        myPostsAdapter = PostAdapter(
            onLike = {
                Toast.makeText(requireContext(), "Like (pendiente)", Toast.LENGTH_SHORT).show()
            },
            onComments = { post -> showCommentsBottomSheet(post) }
        )

        binding.rvMisPublicaciones.layoutManager = LinearLayoutManager(requireContext())
        binding.rvMisPublicaciones.adapter = myPostsAdapter

        postsViewModel.myPosts.observe(viewLifecycleOwner) { posts ->
            myPostsAdapter.submitList(posts)
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

        communityRepo.getComments(post.id).enqueue(object : Callback<CommentsResponse> {
            override fun onResponse(call: Call<CommentsResponse>, response: Response<CommentsResponse>) {
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

        btn.setOnClickListener {
            val text = et.text.toString().trim()
            if (text.isEmpty()) return@setOnClickListener

            val uid = FirebaseAuth.getInstance().currentUser?.uid
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
                            communityRepo.getComments(post.id)
                                .enqueue(object : Callback<CommentsResponse> {
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

    private fun setupDemoLists() { }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
