package com.senderlink.app.view.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.firebase.auth.FirebaseAuth
import com.senderlink.app.R
import com.senderlink.app.network.CommentsResponse
import com.senderlink.app.network.CreateCommentResponse
import com.senderlink.app.repository.CommunityRepository
import com.senderlink.app.view.adapters.CommentAdapter
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class CommentsBottomSheetFragment : BottomSheetDialogFragment() {

    private val communityRepo = CommunityRepository()

    companion object {
        private const val ARG_POST_ID = "postId"

        fun newInstance(postId: String) = CommentsBottomSheetFragment().apply {
            arguments = Bundle().apply { putString(ARG_POST_ID, postId) }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.bottomsheet_comments, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val postId = arguments?.getString(ARG_POST_ID) ?: return

        val rv = view.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.rvComments)
        val et = view.findViewById<EditText>(R.id.etComment)
        val btn = view.findViewById<TextView>(R.id.btnSend)

        val commentAdapter = CommentAdapter()
        rv.layoutManager = LinearLayoutManager(requireContext())
        rv.adapter = commentAdapter

        fun loadComments() {
            communityRepo.getComments(postId).enqueue(object : Callback<CommentsResponse> {
                override fun onResponse(call: Call<CommentsResponse>, response: Response<CommentsResponse>) {
                    if (response.isSuccessful && response.body()?.ok == true)
                        commentAdapter.submitList(response.body()?.data ?: emptyList())
                }
                override fun onFailure(call: Call<CommentsResponse>, t: Throwable) {
                    if (isAdded) {
                        Toast.makeText(requireContext(), "Error al cargar comentarios", Toast.LENGTH_SHORT).show()
                    }
                }
            })
        }

        loadComments()

        btn.setOnClickListener {
            val text = et.text.toString().trim()
            if (text.isEmpty()) return@setOnClickListener

            val uid = FirebaseAuth.getInstance().currentUser?.uid
            if (uid.isNullOrBlank()) {
                Toast.makeText(requireContext(), "No autenticado", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            communityRepo.createComment(postId, uid, text)
                .enqueue(object : Callback<CreateCommentResponse> {
                    override fun onResponse(call: Call<CreateCommentResponse>, response: Response<CreateCommentResponse>) {
                        if (!isAdded) return
                        if (response.isSuccessful && response.body()?.ok == true) {
                            et.setText("")
                            loadComments()
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
    }
}
