package com.senderlink.app.view.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.auth.FirebaseAuth
import com.senderlink.app.databinding.FragmentMisPublicacionesBinding
import com.senderlink.app.utils.UserManager
import com.senderlink.app.view.adapters.PostAdapter
import com.senderlink.app.viewmodel.PerfilPostsViewModel

class MisPublicacionesFragment : Fragment() {

    private var _binding: FragmentMisPublicacionesBinding? = null
    private val binding get() = _binding!!

    private val postsViewModel: PerfilPostsViewModel by viewModels()
    private lateinit var adapter: PostAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentMisPublicacionesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        requireActivity().title = "Mis publicaciones"

        adapter = PostAdapter(
            onLike = { post -> postsViewModel.toggleLike(post.id) },
            onComments = { post ->
                val sheet = CommentsBottomSheetFragment.newInstance(post.id)
                sheet.show(childFragmentManager, "CommentsSheet")
            },
            onDelete = { post ->
                AlertDialog.Builder(requireContext())
                    .setTitle("Eliminar publicación")
                    .setMessage("¿Seguro que quieres eliminar esta publicación?")
                    .setPositiveButton("Eliminar") { _, _ -> postsViewModel.deletePost(post.id) }
                    .setNegativeButton("Cancelar", null)
                    .show()
            }
        )

        binding.rvPublicaciones.layoutManager = LinearLayoutManager(requireContext())
        binding.rvPublicaciones.adapter = adapter

        postsViewModel.myPosts.observe(viewLifecycleOwner) { posts ->
            adapter.submitList(posts)
            binding.tvEmpty.visibility = if (posts.isEmpty()) View.VISIBLE else View.GONE
            binding.rvPublicaciones.visibility = if (posts.isEmpty()) View.GONE else View.VISIBLE
        }

        postsViewModel.error.observe(viewLifecycleOwner) { msg ->
            if (msg != null) {
                Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
                postsViewModel.clearError()
            }
        }

        postsViewModel.loadMyPosts()

        // Actualiza el avatar del adapter cuando cambia la foto del usuario
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid != null) {
            UserManager.getInstance().currentUser.observe(viewLifecycleOwner) { user ->
                adapter.setCurrentUserPhotoUrl(user?.foto, uid)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
