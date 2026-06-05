package com.senderlink.app.viewmodel

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.senderlink.app.model.EventoGrupal
import com.senderlink.app.repository.EventRepository
import com.senderlink.app.utils.UserManager
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class RutasGrupalesViewModel : ViewModel() {

    private val repository = EventRepository()
    private val auth = FirebaseAuth.getInstance()
    private val userManager = UserManager.getInstance()

    private val TAG = "RutasGrupalesVM"

    private val _eventos = MutableLiveData<List<EventoGrupal>>(emptyList())
    val eventos: LiveData<List<EventoGrupal>> = _eventos

    private val _eventosRuta = MutableLiveData<List<EventoGrupal>>(emptyList())
    val eventosRuta: LiveData<List<EventoGrupal>> = _eventosRuta

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

    private var lastRouteIdRequested: String? = null

    private val _routeFilterId = MutableLiveData<String?>(null)
    val routeFilterId: LiveData<String?> = _routeFilterId

    private val _showAllDisponibles = MutableLiveData(false)
    val showAllDisponibles: LiveData<Boolean> = _showAllDisponibles

    private val _navPreferredTab = MutableLiveData<Int?>(null)
    val navPreferredTab: LiveData<Int?> = _navPreferredTab

    private val _selectedEventId = MutableLiveData<String?>(null)
    val selectedEventId: LiveData<String?> = _selectedEventId

    init {
        userManager.loadCurrentUser()
    }

    // Parsea fechas ISO 8601 con o sin milisegundos (2025-04-15T09:00:00.000Z o 2025-04-15T09:00:00Z)
    private fun parseIsoDate(dateStr: String?): Date? {
        if (dateStr.isNullOrBlank()) return null
        val formats = listOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mm:ss"
        )
        for (fmt in formats) {
            try {
                val sdf = SimpleDateFormat(fmt, Locale.US)
                sdf.timeZone = TimeZone.getTimeZone("UTC")
                return sdf.parse(dateStr)
            } catch (_: Exception) { }
        }
        return null
    }

    // ✅ Filtrar eventos cancelados con límite de 7 días
    private fun shouldShowCancelledEvent(evento: EventoGrupal): Boolean {
        if (!evento.isCancelado()) return true

        return try {
            val fechaEvento = parseIsoDate(evento.fecha) ?: return true
            val hoy = Date()

            val fechaCancelacion = if (!evento.updatedAt.isNullOrBlank()) {
                parseIsoDate(evento.updatedAt) ?: fechaEvento
            } else {
                fechaEvento
            }

            val calendar = Calendar.getInstance()

            calendar.time = fechaEvento
            calendar.add(Calendar.DAY_OF_YEAR, 7)
            val limiteDesdeEvento = calendar.time

            calendar.time = fechaCancelacion
            calendar.add(Calendar.DAY_OF_YEAR, 7)
            val limiteDesdeCancelacion = calendar.time

            val fechaLimite = if (limiteDesdeCancelacion.before(limiteDesdeEvento)) {
                limiteDesdeCancelacion
            } else {
                limiteDesdeEvento
            }

            val mostrar = hoy.before(fechaLimite)

            if (!mostrar) {
                Log.d(TAG, "🗑️ Evento cancelado oculto: ${evento.id.take(8)}")
            }

            mostrar
        } catch (e: Exception) {
            Log.e(TAG, "Error parseando fecha: ${evento.fecha}", e)
            true
        }
    }

    fun setRouteFilter(routeId: String?) {
        _routeFilterId.value = routeId
        lastRouteIdRequested = routeId
        Log.d(TAG, "setRouteFilter(routeId=$routeId)")
    }

    fun setShowAllDisponibles(showAll: Boolean) {
        _showAllDisponibles.value = showAll
        lastRouteIdRequested = if (showAll) null else _routeFilterId.value
        Log.d(TAG, "setShowAllDisponibles($showAll) routeFilterId=${_routeFilterId.value}")
    }

    private fun getUid(): String? {
        val uidFromManager = userManager.getUserUid()
        if (!uidFromManager.isNullOrBlank()) return uidFromManager

        val uidFromAuth = auth.currentUser?.uid
        if (!uidFromAuth.isNullOrBlank()) return uidFromAuth

        return null
    }

    fun getUidForUi(): String? = getUid()

    private fun requireUidOrError(): String? {
        val uid = getUid()
        if (uid.isNullOrBlank()) {
            _error.value = "Debes iniciar sesión"
            return null
        }
        return uid
    }

    fun clearNavigation() {
        _navPreferredTab.value = null
        _selectedEventId.value = null
    }

    fun loadEventosDisponibles() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            try {
                val uid = getUid()
                val response = repository.getEventos(
                    estado = "ABIERTO",
                    routeId = null,
                    uid = uid,
                    limit = 50,
                    skip = 0
                )

                if (response.ok) {
                    val list = response.data?.eventos ?: emptyList()
                    val disponibles = list.filter { ev ->
                        ev.isOrganizer != true && ev.isParticipant != true
                    }
                    _eventos.value = disponibles
                    Log.d(TAG, "✅ Disponibles globales: ${disponibles.size}")
                } else {
                    _error.value = response.message ?: "Error al cargar eventos"
                }

            } catch (e: Exception) {
                _error.value = "Error de conexión: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun loadEventosPorRuta(routeId: String) {
        lastRouteIdRequested = routeId

        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            try {
                val uid = getUid()
                val response = repository.getEventos(
                    estado = null,
                    routeId = routeId,
                    uid = uid,
                    limit = 50,
                    skip = 0
                )

                if (response.ok) {
                    val list = response.data?.eventos ?: emptyList()
                    _eventosRuta.value = list
                    Log.d(TAG, "✅ Eventos por ruta ($routeId): ${list.size}")
                } else {
                    _error.value = response.message ?: "Error al cargar eventos"
                    _eventosRuta.value = emptyList()
                }

            } catch (e: Exception) {
                _error.value = "Error de conexión: ${e.message}"
                _eventosRuta.value = emptyList()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun loadMisEventos() {
        val uid = requireUidOrError() ?: return

        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            try {
                val response = repository.getEventosByUser(uid)
                if (response.ok) {
                    val todosLosEventos = response.data ?: emptyList()
                    val eventosFiltrados = todosLosEventos.filter { shouldShowCancelledEvent(it) }
                    _misEventos.value = eventosFiltrados

                    val canceladosOcultos = todosLosEventos.size - eventosFiltrados.size
                    if (canceladosOcultos > 0) {
                        Log.d(TAG, "✅ Mis eventos: ${eventosFiltrados.size} " +
                                "(🗑️ ${canceladosOcultos} cancelados antiguos ocultados)")
                    } else {
                        Log.d(TAG, "✅ Mis eventos: ${eventosFiltrados.size}")
                    }
                } else {
                    _error.value = response.message ?: "Error al cargar mis eventos"
                }
            } catch (e: Exception) {
                _error.value = "Error de conexión: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun loadEventosParticipando() {
        val uid = requireUidOrError() ?: return

        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            try {
                val response = repository.getEventosParticipando(uid)
                if (response.ok) {
                    val todosLosEventos = response.data ?: emptyList()
                    val eventosFiltrados = todosLosEventos.filter { shouldShowCancelledEvent(it) }
                    _eventosParticipando.value = eventosFiltrados

                    val canceladosOcultos = todosLosEventos.size - eventosFiltrados.size
                    if (canceladosOcultos > 0) {
                        Log.d(TAG, "✅ Participo en: ${eventosFiltrados.size} " +
                                "(🗑️ ${canceladosOcultos} cancelados antiguos ocultados)")
                    } else {
                        Log.d(TAG, "✅ Participo en: ${eventosFiltrados.size}")
                    }
                } else {
                    _error.value = response.message ?: "Error al cargar eventos"
                }
            } catch (e: Exception) {
                _error.value = "Error de conexión: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun openFromRouteDetail(routeId: String, eventId: String? = null) {
        _routeFilterId.value = routeId
        _showAllDisponibles.value = false
        _selectedEventId.value = eventId
        lastRouteIdRequested = routeId

        viewModelScope.launch {
            val uid = getUid()

            if (uid.isNullOrBlank()) {
                _navPreferredTab.value = 0
                loadEventosPorRuta(routeId)
                return@launch
            }

            try {
                val response = repository.getEventos(
                    estado = null,
                    routeId = routeId,
                    uid = uid,
                    limit = 50,
                    skip = 0
                )

                if (response.ok) {
                    val eventos = response.data?.eventos ?: emptyList()
                    _eventosRuta.value = eventos

                    val soyOrganizador = eventos.any { it.isOrganizer == true }
                    val participo = eventos.any { it.isParticipant == true }

                    _navPreferredTab.value = when {
                        soyOrganizador -> 1
                        participo -> 2
                        else -> 0
                    }

                    Log.d(TAG, "✅ openFromRouteDetail: tab=${_navPreferredTab.value}")
                } else {
                    _navPreferredTab.value = 0
                    loadEventosPorRuta(routeId)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error en openFromRouteDetail", e)
                _navPreferredTab.value = 0
                loadEventosPorRuta(routeId)
            }
        }
    }

    fun openFromNavComunidad() {
        _routeFilterId.value = null
        _showAllDisponibles.value = true
        _navPreferredTab.value = null
        _selectedEventId.value = null
        lastRouteIdRequested = null
        Log.d(TAG, "✅ openFromNavComunidad: showAll=true")
    }

    private fun refreshAfterAction() {
        loadEventosParticipando()
        loadMisEventos()

        val routeId = lastRouteIdRequested
        if (!routeId.isNullOrBlank()) {
            loadEventosPorRuta(routeId)
        } else {
            loadEventosDisponibles()
        }
    }

    // ========================================
    // ACCIONES — coroutines limpias sin polling
    // ========================================

    fun joinEvento(evento: EventoGrupal) {
        val uid = requireUidOrError() ?: return
        val nombre = userManager.getUserName()
        val foto = userManager.getUserPhoto()

        viewModelScope.launch {
            _isLoading.value = true
            try {
                val response = repository.joinEvento(evento.id, uid, nombre, foto)
                if (response.ok) {
                    _successMessage.value = "Te has unido al evento"
                    refreshAfterAction()
                } else {
                    _error.value = response.message ?: "Error al unirse al evento"
                }
            } catch (e: Exception) {
                Log.e(TAG, "joinEvento error", e)
                _error.value = e.message ?: "Error de conexión"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun leaveEvento(evento: EventoGrupal) {
        val uid = requireUidOrError() ?: return
        val nombre = userManager.getUserName()
        val foto = userManager.getUserPhoto()

        viewModelScope.launch {
            _isLoading.value = true
            try {
                val response = repository.leaveEvento(evento.id, uid, nombre, foto)
                if (response.ok) {
                    _successMessage.value = "Has salido del evento"
                    refreshAfterAction()
                } else {
                    _error.value = response.message ?: "Error al salir del evento"
                }
            } catch (e: Exception) {
                Log.e(TAG, "leaveEvento error", e)
                _error.value = e.message ?: "Error de conexión"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun cancelEvento(evento: EventoGrupal) {
        val uid = requireUidOrError() ?: return

        viewModelScope.launch {
            _isLoading.value = true
            try {
                val response = repository.cancelEvento(evento.id, uid)
                if (response.ok) {
                    _successMessage.value = "Evento cancelado"
                    refreshAfterAction()
                } else {
                    _error.value = response.message ?: "Error al cancelar el evento"
                }
            } catch (e: Exception) {
                Log.e(TAG, "cancelEvento error", e)
                _error.value = e.message ?: "Error de conexión"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun createEvento(
        routeId: String,
        fecha: String,
        maxParticipantes: Int = 10,
        descripcion: String? = null,
        nivelRecomendado: String? = null,
        horaEncuentro: String = "09:00"
    ) {
        val uid = requireUidOrError() ?: return
        val nombre = userManager.getUserName()
        val foto = userManager.getUserPhoto()

        viewModelScope.launch {
            _isLoading.value = true
            try {
                val response = repository.createEvento(
                    routeId = routeId,
                    organizadorUid = uid,
                    organizadorNombre = nombre,
                    organizadorFoto = foto,
                    fecha = fecha,
                    maxParticipantes = maxParticipantes,
                    descripcion = descripcion,
                    nivelRecomendado = nivelRecomendado,
                    horaEncuentro = horaEncuentro
                )
                if (response.ok) {
                    _successMessage.value = "Evento creado correctamente"
                    lastRouteIdRequested = routeId
                    refreshAfterAction()
                } else {
                    _error.value = response.message ?: "Error al crear el evento"
                }
            } catch (e: Exception) {
                Log.e(TAG, "createEvento error", e)
                _error.value = e.message ?: "Error de conexión"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun clearMessages() {
        _error.value = null
        _successMessage.value = null
    }
}