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
import com.senderlink.app.view.adapters.AchievementAdapter
import com.google.android.material.bottomsheet.BottomSheetDialog
import android.app.Activity
import android.net.Uri
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage
import com.yalantis.ucrop.UCrop
import java.io.File
import com.senderlink.app.R
import com.senderlink.app.databinding.FragmentPerfilBinding
import com.senderlink.app.model.Post
import com.senderlink.app.network.CommentsResponse
import com.senderlink.app.network.CreateCommentResponse
import com.senderlink.app.repository.CommunityRepository
import com.senderlink.app.repository.UserRepository
import com.senderlink.app.view.LoginActivity
import com.senderlink.app.view.adapters.CommentAdapter
import androidx.recyclerview.widget.GridLayoutManager
import com.senderlink.app.view.adapters.PostAdapter
import com.senderlink.app.view.adapters.RouteAdapter
import com.senderlink.app.viewmodel.PerfilPostsViewModel
import com.senderlink.app.viewmodel.PerfilRoutesViewModel
import com.senderlink.app.viewmodel.PerfilViewModel
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class PerfilFragment : Fragment() {

    private var _binding: FragmentPerfilBinding? = null
    private val binding get() = _binding!!

    private val viewModel: PerfilViewModel by viewModels()
    private val postsViewModel: PerfilPostsViewModel by viewModels()
    private val routesViewModel: PerfilRoutesViewModel by viewModels()
    private lateinit var myPostsAdapter: PostAdapter
    private lateinit var myRoutesAdapter: RouteAdapter
    private var isRoutesGridMode = false

    private val communityRepo = CommunityRepository()
    private lateinit var achievementAdapter: AchievementAdapter

    // Recibe el resultado del crop y sube a Firebase Storage
    private val cropProfileLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val croppedUri = UCrop.getOutput(result.data ?: return@registerForActivityResult)
                    ?: return@registerForActivityResult
                Glide.with(this)
                    .load(croppedUri)
                    .placeholder(R.drawable.bg_avatar_circle)
                    .error(R.drawable.bg_avatar_circle)
                    .into(binding.imgFotoPerfil)
                uploadProfilePhotoToFirebase(croppedUri)
            }
        }

    // Abre galería → lanza uCrop (1:1)
    private val pickImageLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) {
                val destUri = Uri.fromFile(
                    File(requireContext().cacheDir, "crop_profile_${System.currentTimeMillis()}.jpg")
                )
                val intent = UCrop.of(uri, destUri)
                    .withAspectRatio(1f, 1f)
                    .withMaxResultSize(512, 512)
                    .getIntent(requireContext())
                cropProfileLauncher.launch(intent)
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
        setupLogros()
        observeViewModel()

        binding.btnEditFoto.setOnClickListener { pickImageLauncher.launch("image/*") }
        binding.imgFotoPerfil.setOnClickListener { pickImageLauncher.launch("image/*") }
        binding.btnGrabarRuta.setOnClickListener {
            findNavController().navigate(R.id.action_perfilFragment_to_createRouteFragment)
        }

        setupMyPosts()
        setupMyRoutes()

        viewModel.loadUserData()
        postsViewModel.loadMyPosts()
        routesViewModel.loadMyRoutes()
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
                        val user = viewModel.userData.value
                        val texto = "¡Sígueme en SenderLink! Soy ${user?.nombre ?: "un senderista"}, nivel ${user?.progreso?.level ?: 1} - ${user?.progreso?.rankTitle ?: "Explorer"}"
                        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, texto)
                        }, "Compartir perfil"))
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
            isRoutesGridMode = !isRoutesGridMode
            binding.btnCambiarVista.text = if (isRoutesGridMode) "☰" else "⊞"
            binding.rvRutasPublicadas.layoutManager = if (isRoutesGridMode)
                GridLayoutManager(requireContext(), 2)
            else
                LinearLayoutManager(requireContext())
        }

        binding.txtVerTodosLogros.setOnClickListener {
            showLogrosBottomSheet(viewModel.userData.value?.badges ?: emptyList())
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

                // XP / nivel
                val level = user.progreso?.level ?: 1
                val xp = user.progreso?.xp ?: 0
                val rank = user.progreso?.rankTitle ?: "Explorer"
                val xpEnNivel = xp % 100
                binding.txtNivel.text = "Nivel $level · $rank"
                binding.progressXp.progress = xpEnNivel
                binding.txtXp.text = "$xpEnNivel / 100 XP"

                // Perfil completado
                val completion = user.profileCompletion
                binding.txtProfileCompletion.text = "Perfil $completion%"
                binding.progressPerfil.progress = completion

                // Imagen de fondo del perfil (foto del usuario con alpha)
                if (user.foto.isNotBlank()) {
                    Glide.with(this)
                        .load(user.foto)
                        .centerCrop()
                        .into(binding.imgBackgroundProfile)
                }

                // Logros
                achievementAdapter.submitList(user.badges)

            } else {
                binding.txtNombre.text = "Usuario"
                binding.txtSubtitulo.text = "Explorador · España"
                binding.txtNivel.text = "Nivel 1 · Explorer"
                binding.progressXp.progress = 0
                binding.txtXp.text = "0 / 100 XP"

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
                    val uid = FirebaseAuth.getInstance().currentUser?.uid
                    myPostsAdapter.setCurrentUserPhotoUrl(result.data.foto, uid)

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

    private fun setupLogros() {
        achievementAdapter = AchievementAdapter()
        binding.rvLogros.layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        binding.rvLogros.adapter = achievementAdapter
    }

    private fun setupMyPosts() {
        myPostsAdapter = PostAdapter(
            onLike = { post -> postsViewModel.toggleLike(post.id) },
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
            findNavController().navigate(R.id.action_perfilFragment_to_misPublicacionesFragment)
        }
    }

    private fun setupMyRoutes() {
        myRoutesAdapter = RouteAdapter { route ->
            val action = PerfilFragmentDirections
                .actionPerfilFragmentToRouteDetailFragment(route.id)
            findNavController().navigate(action)
        }

        binding.rvRutasPublicadas.layoutManager = LinearLayoutManager(requireContext())
        binding.rvRutasPublicadas.adapter = myRoutesAdapter
        binding.rvRutasPublicadas.isNestedScrollingEnabled = false

        routesViewModel.routes.observe(viewLifecycleOwner) { routes ->
            myRoutesAdapter.submitList(routes)
        }

        routesViewModel.error.observe(viewLifecycleOwner) { msg ->
            if (msg != null) {
                Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
                routesViewModel.clearError()
            }
        }
    }

    private fun showLogrosBottomSheet(earnedBadges: List<String>) {
        val dialog = BottomSheetDialog(requireContext())
        val v = layoutInflater.inflate(R.layout.bottomsheet_logros, null)
        dialog.setContentView(v)

        val rv = v.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.rvTodosLogros)
        rv.layoutManager = androidx.recyclerview.widget.GridLayoutManager(requireContext(), 3)

        // Mostrar todos los badges posibles: desbloqueados en color, bloqueados en gris
        val allBadges = AchievementAdapter.ALL_BADGES
        val adapterDesbloqueados = AchievementAdapter(locked = false)
        val adapterBloqueados = AchievementAdapter(locked = true)

        // Combinar en un adapter que mezcle ambos estados
        val mixedAdapter = object : androidx.recyclerview.widget.RecyclerView.Adapter<androidx.recyclerview.widget.RecyclerView.ViewHolder>() {
            inner class VH(val binding: com.senderlink.app.databinding.ItemAchievementBinding) :
                androidx.recyclerview.widget.RecyclerView.ViewHolder(binding.root)

            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
                val binding = com.senderlink.app.databinding.ItemAchievementBinding.inflate(
                    layoutInflater, parent, false
                )
                return VH(binding)
            }

            override fun onBindViewHolder(holder: androidx.recyclerview.widget.RecyclerView.ViewHolder, position: Int) {
                val badge = allBadges[position]
                val unlocked = earnedBadges.contains(badge)
                val (iconRes, label) = AchievementAdapter.badgeInfo(badge)
                val vhTyped = holder as VH
                vhTyped.binding.imgAch.setImageResource(iconRes)
                vhTyped.binding.txtAch.text = label
                vhTyped.binding.imgAch.alpha = if (unlocked) 1f else 0.25f
                vhTyped.binding.txtAch.alpha = if (unlocked) 1f else 0.25f
            }

            override fun getItemCount() = allBadges.size
        }

        rv.adapter = mixedAdapter
        dialog.show()
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
                if (!isAdded) return
                if (response.isSuccessful && response.body()?.ok == true) {
                    commentAdapter.submitList(response.body()?.data ?: emptyList())
                } else {
                    Toast.makeText(requireContext(), "No se pudieron cargar comentarios", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onFailure(call: Call<CommentsResponse>, t: Throwable) {
                if (!isAdded) return
                Toast.makeText(requireContext(), "Error de conexión", Toast.LENGTH_SHORT).show()
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
                        if (!isAdded) return
                        if (response.isSuccessful && response.body()?.ok == true) {
                            et.setText("")
                            communityRepo.getComments(post.id)
                                .enqueue(object : Callback<CommentsResponse> {
                                    override fun onResponse(
                                        call: Call<CommentsResponse>,
                                        response: Response<CommentsResponse>
                                    ) {
                                        if (!isAdded) return
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
                        if (!isAdded) return
                        Toast.makeText(requireContext(), "Error de conexión", Toast.LENGTH_SHORT).show()
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

        dialogView.findViewById<View>(R.id.optionMessages).setOnClickListener {
            dialog.dismiss()
            findNavController().navigate(R.id.action_perfilFragment_to_conversationsFragment)
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
        com.senderlink.app.utils.UserManager.getInstance().clearCache()
        val intent = Intent(requireContext(), LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        activity?.finish()
    }

    private fun uploadProfilePhotoToFirebase(uri: android.net.Uri) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val storageRef = Firebase.storage.reference
        val fileRef = storageRef.child("profiles/$uid/photo_${System.currentTimeMillis()}.jpg")

        fileRef.putFile(uri)
            .continueWithTask { task ->
                if (!task.isSuccessful) throw task.exception ?: RuntimeException("Upload failed")
                fileRef.downloadUrl
            }
            .addOnSuccessListener { downloadUri ->
                viewModel.updateProfilePhotoUrl(downloadUri.toString())
            }
            .addOnFailureListener {
                if (!isAdded) return@addOnFailureListener
                Toast.makeText(requireContext(), "Error subiendo foto", Toast.LENGTH_LONG).show()
                viewModel.loadUserData() // revert preview
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
