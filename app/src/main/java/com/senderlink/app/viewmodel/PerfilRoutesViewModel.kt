package com.senderlink.app.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.senderlink.app.model.Route
import com.senderlink.app.repository.RouteRepository
import kotlinx.coroutines.launch

class PerfilRoutesViewModel : ViewModel() {

    private val repo = RouteRepository()

    private val _routes = MutableLiveData<List<Route>>(emptyList())
    val routes: LiveData<List<Route>> = _routes

    private val _error = MutableLiveData<String?>(null)
    val error: LiveData<String?> = _error

    fun loadMyRoutes() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid.isNullOrBlank()) {
            _error.value = "No hay usuario autenticado"
            return
        }

        viewModelScope.launch {
            try {
                val response = repo.getRoutesByUser(uid)
                _routes.value = response.routes
            } catch (e: Exception) {
                _error.value = e.message ?: "Error cargando rutas"
            }
        }
    }

    fun clearError() {
        _error.value = null
    }
}
