package com.senderlink.app.viewmodel

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.senderlink.app.model.EventoGrupal
import com.senderlink.app.repository.EventRepository
import kotlinx.coroutines.launch

class RutasGrupalesViewModel : ViewModel() {

    private val repository = EventRepository()
    private val auth = FirebaseAuth.getInstance()

    private val TAG = "RutasGrupalesVM"

    private val _eventos = MutableLiveData<List<EventoGrupal>>(emptyList())
    val eventos: LiveData<List<EventoGrupal>> = _eventos

    private val _misEventos = MutableLiveData<List<EventoGrupal>>(emptyList())
    val misEventos: LiveData<List<EventoGrupal>> = _misEventos

    private val _eventosParticipando = MutableLiveData<List<EventoGrupal>>(emptyList())
    val eventosParticipando: LiveData<List<EventoGrupal>> = _eventosParticipando

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _error = MutableLiveData<String?>(null)
    val error: LiveData<String?> = _error

    private val _successMessage = MutableLiveData<String?>(null)
    val successMessage: LiveData<String?> = _successMessage

    // ==========================================
    // 🔧 FUNCIÓN AUXILIAR PARA OBTENER NOMBRE
    // ==========================================

    /**
     * Obtiene el nombre del usuario con múltiples alternativas
     * para garantizar que siempre haya un valor válido
     */
    private fun getUserName(user: com.google.firebase.auth.FirebaseUser): String {
        return when {
            !user.displayName.isNullOrBlank() -> user.displayName!!
            !user.email.isNullOrBlank() -> user.email!!.split("@")[0]
            else -> "Usuario_${user.uid.take(6)}"
        }
    }

    // ==========================================
    // LISTADOS
    // ==========================================

    fun loadEventosDisponibles() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            try {
                val uid = auth.currentUser?.uid
                Log.d(TAG, "Cargando eventos disponibles... uid=$uid")

                val response = repository.getEventos(
                    estado = null,
                    routeId = null,
                    uid = uid,
                    limit = 50,
                    skip = 0
                )

                if (response.ok) {
                    val list = response.data?.eventos ?: emptyList()
                    _eventos.value = list
                    Log.d(TAG, "✅ ${list.size} eventos cargados")
                } else {
                    _error.value = response.message ?: "Error al cargar eventos"
                }

            } catch (e: Exception) {
                Log.e(TAG, "❌ Error cargando eventos: ${e.message}", e)
                _error.value = "Error de conexión: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun loadMisEventos() {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            Log.w(TAG, "⚠️ Usuario no autenticado")
            _error.value = "Debes iniciar sesión"
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            try {
                Log.d(TAG, "Cargando eventos organizados por $uid...")

                val response = repository.getEventosByUser(uid)

                if (response.ok) {
                    _misEventos.value = response.data ?: emptyList()
                    Log.d(TAG, "✅ ${response.data.size} eventos organizados")
                } else {
                    _error.value = response.message ?: "Error al cargar mis eventos"
                }

            } catch (e: Exception) {
                Log.e(TAG, "❌ Error cargando mis eventos: ${e.message}", e)
                _error.value = "Error de conexión: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun loadEventosParticipando() {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            Log.w(TAG, "⚠️ Usuario no autenticado")
            _error.value = "Debes iniciar sesión"
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            try {
                Log.d(TAG, "Cargando eventos donde participa $uid...")

                val response = repository.getEventosParticipando(uid)

                if (response.ok) {
                    _eventosParticipando.value = response.data ?: emptyList()
                    Log.d(TAG, "✅ ${response.data.size} eventos participando")
                } else {
                    _error.value = response.message ?: "Error al cargar eventos"
                }

            } catch (e: Exception) {
                Log.e(TAG, "❌ Error cargando eventos participando: ${e.message}", e)
                _error.value = "Error de conexión: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * 🔍 Eventos por ruta (con uid para flags)
     */
    fun loadEventosPorRuta(routeId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            try {
                val uid = auth.currentUser?.uid
                Log.d(TAG, "Cargando eventos por ruta: routeId=$routeId uid=$uid")

                val response = repository.getEventos(
                    estado = null,
                    routeId = routeId,
                    uid = uid,
                    limit = 50,
                    skip = 0
                )

                if (response.ok) {
                    val list = response.data?.eventos ?: emptyList()
                    _eventos.value = list
                    Log.d(TAG, "✅ ${list.size} eventos de la ruta")
                } else {
                    _error.value = response.message ?: "Error al cargar eventos"
                }

            } catch (e: Exception) {
                Log.e(TAG, "❌ Error cargando eventos de ruta: ${e.message}", e)
                _error.value = "Error de conexión: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * ✅ Alias para dejar claro que trae flags (pero realmente ya lo hace)
     */
    fun loadEventosPorRutaForUser(routeId: String) = loadEventosPorRuta(routeId)

    // ==========================================
    // ACCIONES (create/join/leave/cancel)
    // ==========================================

    /**
     * ✅ CORREGIDA: Crear evento con nombre válido
     */
    fun createEvento(
        routeId: String,
        fecha: String,
        maxParticipantes: Int = 10,
        descripcion: String? = null,
        horaEncuentro: String = "09:00"
    ) {
        val user = auth.currentUser
        if (user == null) {
            _error.value = "Debes iniciar sesión para crear un evento"
            return
        }

        val uid = user.uid

        // ✅ Usar función auxiliar para obtener nombre con alternativas
        val nombre = getUserName(user)
        val foto = user.photoUrl?.toString()

        // 🔍 DEBUG: Verificar qué nombre se está usando
        Log.d(TAG, "📝 Creando evento:")
        Log.d(TAG, "   - Nombre: '$nombre'")
        Log.d(TAG, "   - displayName: '${user.displayName}'")
        Log.d(TAG, "   - email: '${user.email}'")
        Log.d(TAG, "   - uid: '${uid}'")

        _error.value = null

        val live = repository.createEvento(
            routeId = routeId,
            organizadorUid = uid,
            organizadorNombre = nombre,
            organizadorFoto = foto,
            fecha = fecha,
            maxParticipantes = maxParticipantes,
            descripcion = descripcion,
            horaEncuentro = horaEncuentro
        )

        val obs = object : Observer<EventRepository.Result<EventoGrupal>> {
            override fun onChanged(result: EventRepository.Result<EventoGrupal>) {
                when (result) {
                    is EventRepository.Result.Loading -> _isLoading.value = true
                    is EventRepository.Result.Success -> {
                        _isLoading.value = false
                        _successMessage.value = "Evento creado correctamente"
                        Log.d(TAG, "✅ Evento creado: ${result.data.id}")

                        loadEventosDisponibles()
                        loadMisEventos()

                        live.removeObserver(this)
                    }
                    is EventRepository.Result.Error -> {
                        _isLoading.value = false
                        _error.value = result.message
                        Log.e(TAG, "❌ Error creando evento: ${result.message}")

                        live.removeObserver(this)
                    }
                }
            }
        }

        live.observeForever(obs)
    }

    /**
     * ✅ CORREGIDA: Unirse a evento con nombre válido
     */
    fun joinEvento(evento: EventoGrupal) {
        val user = auth.currentUser
        if (user == null) {
            _error.value = "Debes iniciar sesión para unirte"
            return
        }

        val uid = user.uid

        // ✅ Usar función auxiliar para obtener nombre con alternativas
        val nombre = getUserName(user)
        val foto = user.photoUrl?.toString()

        if (!evento.canJoin(uid)) {
            _error.value = when {
                evento.isParticipante(uid) -> "Ya estás participando en este evento"
                evento.isFinalizado() -> "Este evento ya ha finalizado"
                evento.isCancelado() -> "Este evento ha sido cancelado"
                !evento.hasPlazasDisponibles() -> "No hay plazas disponibles"
                else -> "No puedes unirte a este evento"
            }
            return
        }

        _error.value = null

        val live = repository.joinEvento(
            eventoId = evento.id,
            uid = uid,
            nombre = nombre,
            foto = foto
        )

        val obs = object : Observer<EventRepository.Result<EventoGrupal>> {
            override fun onChanged(result: EventRepository.Result<EventoGrupal>) {
                when (result) {
                    is EventRepository.Result.Loading -> _isLoading.value = true
                    is EventRepository.Result.Success -> {
                        _isLoading.value = false
                        _successMessage.value = "Te has unido al evento"
                        Log.d(TAG, "✅ Usuario unido al evento: ${evento.id}")

                        loadEventosDisponibles()
                        loadEventosParticipando()

                        live.removeObserver(this)
                    }
                    is EventRepository.Result.Error -> {
                        _isLoading.value = false
                        _error.value = result.message
                        Log.e(TAG, "❌ Error uniéndose: ${result.message}")

                        live.removeObserver(this)
                    }
                }
            }
        }

        live.observeForever(obs)
    }

    /**
     * ✅ CORREGIDA: Salir de evento con nombre válido
     */
    fun leaveEvento(evento: EventoGrupal) {
        val user = auth.currentUser
        if (user == null) {
            _error.value = "Debes iniciar sesión"
            return
        }

        val uid = user.uid

        // ✅ Usar función auxiliar para obtener nombre con alternativas
        val nombre = getUserName(user)
        val foto = user.photoUrl?.toString()

        if (!evento.canLeave(uid)) {
            _error.value = when {
                !evento.isParticipante(uid) -> "No estás participando en este evento"
                evento.isOrganizador(uid) -> "El organizador no puede salir del evento"
                else -> "No puedes salir de este evento"
            }
            return
        }

        _error.value = null

        val live = repository.leaveEvento(
            eventoId = evento.id,
            uid = uid,
            nombre = nombre,
            foto = foto
        )

        val obs = object : Observer<EventRepository.Result<EventoGrupal>> {
            override fun onChanged(result: EventRepository.Result<EventoGrupal>) {
                when (result) {
                    is EventRepository.Result.Loading -> _isLoading.value = true
                    is EventRepository.Result.Success -> {
                        _isLoading.value = false
                        _successMessage.value = "Has salido del evento"
                        Log.d(TAG, "✅ Usuario salió del evento: ${evento.id}")

                        loadEventosDisponibles()
                        loadEventosParticipando()

                        live.removeObserver(this)
                    }
                    is EventRepository.Result.Error -> {
                        _isLoading.value = false
                        _error.value = result.message
                        Log.e(TAG, "❌ Error saliendo: ${result.message}")

                        live.removeObserver(this)
                    }
                }
            }
        }

        live.observeForever(obs)
    }

    fun cancelEvento(evento: EventoGrupal) {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            _error.value = "Debes iniciar sesión"
            return
        }

        if (!evento.isOrganizador(uid)) {
            _error.value = "Solo el organizador puede cancelar el evento"
            return
        }

        _error.value = null

        val live = repository.cancelEvento(
            eventoId = evento.id,
            uid = uid
        )

        val obs = object : Observer<EventRepository.Result<EventoGrupal>> {
            override fun onChanged(result: EventRepository.Result<EventoGrupal>) {
                when (result) {
                    is EventRepository.Result.Loading -> _isLoading.value = true
                    is EventRepository.Result.Success -> {
                        _isLoading.value = false
                        _successMessage.value = "Evento cancelado"
                        Log.d(TAG, "✅ Evento cancelado: ${evento.id}")

                        loadEventosDisponibles()
                        loadMisEventos()

                        live.removeObserver(this)
                    }
                    is EventRepository.Result.Error -> {
                        _isLoading.value = false
                        _error.value = result.message
                        Log.e(TAG, "❌ Error cancelando: ${result.message}")

                        live.removeObserver(this)
                    }
                }
            }
        }

        live.observeForever(obs)
    }

    // ==========================================
    // UTIL
    // ==========================================

    fun refresh() {
        loadEventosDisponibles()
    }

    fun clearMessages() {
        _error.value = null
        _successMessage.value = null
    }
}