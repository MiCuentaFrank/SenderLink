const EventoGrupal = require("../models/EventoGrupal");
const Route = require("../models/Route");
const mongoose = require("mongoose");


/**
 * ===========================================
 * CONTROLLER: EVENTOS GRUPALES (Rutas Grupales)
 * ===========================================
 *
 * Funciones para gestionar quedadas grupales
 * organizadas por usuarios para realizar rutas.
 */

// ========================================
// HELPER: Respuestas estandarizadas
// ========================================
function ok(res, data, message = "OK") {
  return res.json({ ok: true, message, data });
}

function fail(res, status, message) {
  return res.status(status).json({ ok: false, message });
}

// ========================================
// HELPER: Agregar flags isParticipant e isOrganizer
// ========================================
function enrichEventWithFlags(evento, uid) {
  if (!uid) {
    return {
      ...evento,
      isParticipant: false,
      isOrganizer: false
    };
  }

  const isParticipant = (evento.participantes || []).some((p) => p.uid === uid);
  const isOrganizer = evento.organizadorUid === uid;

  return {
    ...evento,
    isParticipant,
    isOrganizer
  };
}

// ========================================
// 1. CREAR EVENTO GRUPAL
// POST /api/events
// ========================================
async function createEvento(req, res) {
  try {
    const {
      routeId,
      organizadorUid,
      organizadorNombre,
      organizadorFoto,
      fecha,
      maxParticipantes,
      descripcion,
      nivelRecomendado,
      puntoEncuentro,
      horaEncuentro
    } = req.body;

    // Validaciones básicas
    if (!routeId || !organizadorUid || !organizadorNombre || !fecha) {
      return fail(res, 400, "routeId, organizadorUid, organizadorNombre y fecha son obligatorios");
    }

    // Verificar que la ruta existe
    const route = await Route.findById(routeId);
    if (!route) {
      return fail(res, 404, "Ruta no encontrada");
    }

    // Verificar que la fecha sea futura
    const fechaEvento = new Date(fecha);
    if (fechaEvento <= new Date()) {
      return fail(res, 400, "La fecha del evento debe ser futura");
    }

    // Crear el evento (guardando para garantizar chatId)
    const evento = new EventoGrupal({
      routeId,
      organizadorUid,
      organizadorNombre,
      organizadorFoto: organizadorFoto || "",
      fecha: fechaEvento,
      maxParticipantes: maxParticipantes || 10,
      descripcion: descripcion || "",
      nivelRecomendado: nivelRecomendado || "",
      puntoEncuentro: puntoEncuentro || {},
      horaEncuentro: horaEncuentro || "09:00",
      participantes: [
        {
          uid: organizadorUid,
          nombre: organizadorNombre,
          foto: organizadorFoto || "",
          fechaUnion: new Date()
        }
      ],
      estado: "ABIERTO"
    });

    await evento.save();

    // ✅ Enriquecer con flags
    const eventoEnriquecido = enrichEventWithFlags(evento.toObject(), organizadorUid);

    return ok(res, eventoEnriquecido, "Evento creado correctamente");

  } catch (error) {
    console.error("createEvento error FULL:", error);

    return res.status(500).json({
      ok: false,
      message: "Error creando evento",
      debug: error.message,
      code: error.code,
      name: error.name
    });
  }
}

// ========================================
// 2. LISTAR EVENTOS (con filtros)
// GET /api/events?estado=ABIERTO&limit=20&skip=0
// ========================================
async function listEventos(req, res) {
  try {
    const { estado, routeId, uid, limit = 20, skip = 0 } = req.query;

    const filtro = {};

    if (estado) filtro.estado = estado;
    else filtro.estado = { $in: ["ABIERTO", "COMPLETO"] };

    // Filtrar por ruta específica (validando ObjectId)
    if (routeId) {
      if (!mongoose.Types.ObjectId.isValid(routeId)) {
        return fail(res, 400, "routeId inválido");
      }
      filtro.routeId = routeId;
    }

    // Solo eventos futuros
    filtro.fecha = { $gte: new Date() };

    const limitFinal = Math.min(parseInt(limit, 10), 50);
    const skipFinal = parseInt(skip, 10);

    const eventos = await EventoGrupal.find(filtro)
      .populate("routeId", "name coverImage distanceKm difficulty startLocality provincia")
      .sort({ fecha: 1 })
      .skip(skipFinal)
      .limit(limitFinal)
      .lean();

    // ✅ Enriquecer todos los eventos con flags
    const eventosEnriquecidos = eventos.map((ev) => enrichEventWithFlags(ev, uid));

    const total = await EventoGrupal.countDocuments(filtro);

    return ok(res, {
      eventos: eventosEnriquecidos,
      total,
      limit: limitFinal,
      skip: skipFinal
    });
  } catch (error) {
    console.error("listEventos error:", error);
    return fail(res, 500, "Error listando eventos");
  }
}

// ========================================
// 3. OBTENER EVENTO POR ID
// GET /api/events/:id?uid=xxx (uid opcional en query)
// ========================================
async function getEventoById(req, res) {
  try {
    const { id } = req.params;
    const { uid } = req.query; // ✅ Ahora acepta uid en query

    const evento = await EventoGrupal.findById(id)
      .populate("routeId")
      .lean();

    if (!evento) {
      return fail(res, 404, "Evento no encontrado");
    }

    // ✅ Enriquecer con flags
    const eventoEnriquecido = enrichEventWithFlags(evento, uid);

    return ok(res, eventoEnriquecido);
  } catch (error) {
    console.error("getEventoById error:", error);
    return fail(res, 500, "Error obteniendo evento");
  }
}

// ========================================
// 4. EVENTOS POR USUARIO (organizados)
// GET /api/events/user/:uid
// ========================================
async function getEventosByUser(req, res) {
  try {
    const { uid } = req.params;

    if (!uid) {
      return fail(res, 400, "uid requerido");
    }

    const eventos = await EventoGrupal.find({ organizadorUid: uid })
      .populate("routeId", "name coverImage distanceKm difficulty startLocality")
      .sort({ fecha: -1 })
      .lean();

    // ✅ Enriquecer TODOS con flags (isOrganizer = true, isParticipant = false)
    const eventosEnriquecidos = eventos.map((ev) => ({
      ...ev,
      isOrganizer: true,    // ← Siempre true (el usuario organiza estos eventos)
      isParticipant: false  // ← Siempre false (no eres "participante" de tus propios eventos)
    }));

    return ok(res, eventosEnriquecidos);
  } catch (error) {
    console.error("getEventosByUser error:", error);
    return fail(res, 500, "Error obteniendo eventos del usuario");
  }
}

// ========================================
// 5. EVENTOS EN LOS QUE PARTICIPA UN USUARIO (NO organizados por él)
// GET /api/events/participating/:uid
// ========================================
async function getEventosParticipando(req, res) {
  try {
    const { uid } = req.params;

    if (!uid) {
      return fail(res, 400, "uid requerido");
    }

    const eventos = await EventoGrupal.find({
      "participantes.uid": uid,
      organizadorUid: { $ne: uid } // 👈 clave: NO organizados por él
    })
      .populate("routeId", "name coverImage distanceKm difficulty startLocality")
      .sort({ fecha: 1 })
      .lean();

    // ✅ Enriquecer TODOS con flags (isParticipant = true, isOrganizer = false)
    const eventosEnriquecidos = eventos.map((ev) => ({
      ...ev,
      isParticipant: true,  // ← Siempre true (estás participando)
      isOrganizer: false    // ← Siempre false (no organizas estos eventos)
    }));

    return ok(res, eventosEnriquecidos);
  } catch (error) {
    console.error("getEventosParticipando error:", error);
    return fail(res, 500, "Error obteniendo eventos donde participas");
  }
}

// ========================================
// 6. UNIRSE A UN EVENTO
// POST /api/events/:id/join
// Body: { uid, nombre, foto? }
// ========================================
async function joinEvento(req, res) {
  try {
    const { id } = req.params;
    const { uid, nombre, foto } = req.body;

    if (!uid || !nombre) {
      return fail(res, 400, "uid y nombre son obligatorios");
    }

    const evento = await EventoGrupal.findById(id);
    if (!evento) {
      return fail(res, 404, "Evento no encontrado");
    }

    // Intentar añadir participante
    const result = evento.addParticipante(uid, nombre, foto || "");

    if (!result.success) {
      return fail(res, 400, result.message);
    }

    // Guardar cambios
    await evento.save();

    // ✅ Enriquecer con flags antes de devolver
    const eventoEnriquecido = enrichEventWithFlags(evento.toObject(), uid);

    return ok(res, eventoEnriquecido, result.message);
  } catch (error) {
    console.error("joinEvento error:", error);
    return fail(res, 500, "Error uniéndose al evento");
  }
}

// ========================================
// 7. SALIR DE UN EVENTO
// POST /api/events/:id/leave
// Body: { uid }
// ========================================
async function leaveEvento(req, res) {
  try {
    const { id } = req.params;
    const { uid } = req.body;

    if (!uid) {
      return fail(res, 400, "uid requerido");
    }

    const evento = await EventoGrupal.findById(id);
    if (!evento) {
      return fail(res, 404, "Evento no encontrado");
    }

    // Intentar eliminar participante
    const result = evento.removeParticipante(uid);

    if (!result.success) {
      return fail(res, 400, result.message);
    }

    // Guardar cambios
    await evento.save();

    // ✅ Enriquecer con flags antes de devolver
    const eventoEnriquecido = enrichEventWithFlags(evento.toObject(), uid);

    return ok(res, eventoEnriquecido, result.message);
  } catch (error) {
    console.error("leaveEvento error:", error);
    return fail(res, 500, "Error saliendo del evento");
  }
}

// ========================================
// 8. CANCELAR EVENTO (solo organizador)
// POST /api/events/:id/cancel
// Body: { uid }
// ========================================
async function cancelEvento(req, res) {
  try {
    const { id } = req.params;
    const { uid } = req.body;

    if (!uid) {
      return fail(res, 400, "uid requerido");
    }

    const evento = await EventoGrupal.findById(id);
    if (!evento) {
      return fail(res, 404, "Evento no encontrado");
    }

    // Verificar que es el organizador
    if (!evento.isOrganizador(uid)) {
      return fail(res, 403, "Solo el organizador puede cancelar el evento");
    }

    // Cancelar evento
    const result = evento.cancelar();
    await evento.save();

    // ✅ Enriquecer con flags antes de devolver
    const eventoEnriquecido = enrichEventWithFlags(evento.toObject(), uid);

    return ok(res, eventoEnriquecido, result.message);
  } catch (error) {
    console.error("cancelEvento error:", error);
    return fail(res, 500, "Error cancelando evento");
  }
}

// ========================================
// 9. FINALIZAR EVENTO (solo organizador)
// POST /api/events/:id/finish
// Body: { uid }
// ========================================
async function finishEvento(req, res) {
  try {
    const { id } = req.params;
    const { uid } = req.body;

    if (!uid) {
      return fail(res, 400, "uid requerido");
    }

    const evento = await EventoGrupal.findById(id);
    if (!evento) {
      return fail(res, 404, "Evento no encontrado");
    }

    // Verificar que es el organizador
    if (!evento.isOrganizador(uid)) {
      return fail(res, 403, "Solo el organizador puede finalizar el evento");
    }

    // Verificar que la fecha del evento ya pasó
    if (new Date(evento.fecha) > new Date()) {
      return fail(res, 400, "Solo se puede finalizar un evento después de su fecha");
    }

    // Finalizar evento
    const result = evento.finalizar();
    await evento.save();

    // ✅ Enriquecer con flags antes de devolver
    const eventoEnriquecido = enrichEventWithFlags(evento.toObject(), uid);

    return ok(res, eventoEnriquecido, result.message);
  } catch (error) {
    console.error("finishEvento error:", error);
    return fail(res, 500, "Error finalizando evento");
  }
}

// ========================================
// 10. ACTUALIZAR EVENTO (solo organizador)
// PUT /api/events/:id
// Body: { uid, ...campos a actualizar }
// ========================================
async function updateEvento(req, res) {
  try {
    const { id } = req.params;
    const { uid, ...updateData } = req.body;

    if (!uid) {
      return fail(res, 400, "uid requerido");
    }

    const evento = await EventoGrupal.findById(id);
    if (!evento) {
      return fail(res, 404, "Evento no encontrado");
    }

    // Verificar que es el organizador
    if (!evento.isOrganizador(uid)) {
      return fail(res, 403, "Solo el organizador puede actualizar el evento");
    }

    // Campos permitidos para actualizar
    const allowedFields = [
      "fecha",
      "maxParticipantes",
      "descripcion",
      "nivelRecomendado",
      "puntoEncuentro",
      "horaEncuentro"
    ];

    // Actualizar solo campos permitidos
    for (const field of allowedFields) {
      if (updateData[field] !== undefined) {
        evento[field] = updateData[field];
      }
    }

    await evento.save();

    // ✅ Enriquecer con flags antes de devolver
    const eventoEnriquecido = enrichEventWithFlags(evento.toObject(), uid);

    return ok(res, eventoEnriquecido, "Evento actualizado correctamente");
  } catch (error) {
    console.error("updateEvento error:", error);
    return fail(res, 500, "Error actualizando evento");
  }
}

// ========================================
// EXPORTAR FUNCIONES
// ========================================
module.exports = {
  createEvento,
  listEventos,
  getEventoById,
  getEventosByUser,
  getEventosParticipando,
  joinEvento,
  leaveEvento,
  cancelEvento,
  finishEvento,
  updateEvento
};