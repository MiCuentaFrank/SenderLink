const EventoGrupal = require("../models/EventoGrupal");
const Route = require("../models/Route");
const mongoose = require("mongoose");
const { addXp, addBadge } = require("./userController");
const { sanitizeText } = require("../utils/sanitize");


/**
 * ===========================================
 * CONTROLLER: EVENTOS GRUPALES (Rutas Grupales)
 * ===========================================
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

    // Auth: solo puedes crear eventos como tú mismo
    if (req.uid !== organizadorUid) {
      return fail(res, 403, "No autorizado: no puedes crear eventos en nombre de otro usuario");
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

    const descripcionSanitizada = descripcion ? sanitizeText(descripcion, 2000) : "";

    // Crear el evento
    const evento = new EventoGrupal({
      routeId,
      organizadorUid,
      organizadorNombre,
      organizadorFoto: organizadorFoto || "",
      fecha: fechaEvento,
      maxParticipantes: maxParticipantes || 10,
      descripcion: descripcionSanitizada,
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

    // Gamificación
    const eventoCount = await EventoGrupal.countDocuments({ organizadorUid });
    await addXp(organizadorUid, 30);
    if (eventoCount === 1) {
      await addBadge(organizadorUid, "FIRST_EVENT");
    }

    const eventoEnriquecido = enrichEventWithFlags(evento.toObject(), organizadorUid);

    return ok(res, eventoEnriquecido, "Evento creado correctamente");

  } catch (error) {
    console.error("createEvento error:", error.message);
    return fail(res, 500, "Error creando evento");
  }
}

// ========================================
// 2. LISTAR EVENTOS (con filtros)
// GET /api/events?estado=ABIERTO&limit=20&skip=0
// ========================================
async function listEventos(req, res) {
  try {
    const { estado, routeId, limit = 20, skip = 0 } = req.query;
    // Usar req.uid del token en vez de req.query.uid
    const uid = req.uid || null;

    const filtro = {};

    if (estado) filtro.estado = estado;
    else filtro.estado = { $in: ["ABIERTO", "COMPLETO"] };

    if (routeId) {
      if (!mongoose.Types.ObjectId.isValid(routeId)) {
        return fail(res, 400, "routeId inválido");
      }
      filtro.routeId = routeId;
    }

    filtro.fecha = { $gte: new Date() };

    const limitFinal = Math.min(parseInt(limit, 10), 50);
    const skipFinal = parseInt(skip, 10);

    const eventos = await EventoGrupal.find(filtro)
      .populate("routeId", "name coverImage distanceKm difficulty startLocality provincia")
      .sort({ fecha: 1 })
      .skip(skipFinal)
      .limit(limitFinal)
      .lean();

    const eventosEnriquecidos = eventos.map((ev) => enrichEventWithFlags(ev, uid));

    const total = await EventoGrupal.countDocuments(filtro);

    return ok(res, {
      eventos: eventosEnriquecidos,
      total,
      limit: limitFinal,
      skip: skipFinal
    });
  } catch (error) {
    console.error("listEventos error:", error.message);
    return fail(res, 500, "Error listando eventos");
  }
}

// ========================================
// 3. OBTENER EVENTO POR ID
// GET /api/events/:id
// ========================================
async function getEventoById(req, res) {
  try {
    const { id } = req.params;
    // Usar req.uid del token en vez de req.query.uid
    const uid = req.uid || null;

    const evento = await EventoGrupal.findById(id)
      .populate("routeId")
      .lean();

    if (!evento) {
      return fail(res, 404, "Evento no encontrado");
    }

    const eventoEnriquecido = enrichEventWithFlags(evento, uid);

    return ok(res, eventoEnriquecido);
  } catch (error) {
    console.error("getEventoById error:", error.message);
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

    const eventosEnriquecidos = eventos.map((ev) => ({
      ...ev,
      isOrganizer: true,
      isParticipant: false
    }));

    return ok(res, eventosEnriquecidos);
  } catch (error) {
    console.error("getEventosByUser error:", error.message);
    return fail(res, 500, "Error obteniendo eventos del usuario");
  }
}

// ========================================
// 5. EVENTOS EN LOS QUE PARTICIPA UN USUARIO
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
      organizadorUid: { $ne: uid }
    })
      .populate("routeId", "name coverImage distanceKm difficulty startLocality")
      .sort({ fecha: 1 })
      .lean();

    const eventosEnriquecidos = eventos.map((ev) => ({
      ...ev,
      isParticipant: true,
      isOrganizer: false
    }));

    return ok(res, eventosEnriquecidos);
  } catch (error) {
    console.error("getEventosParticipando error:", error.message);
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

    // Auth: solo puedes unirte como tú mismo
    if (req.uid !== uid) {
      return fail(res, 403, "No autorizado: no puedes unirte en nombre de otro usuario");
    }

    const eventoExistente = await EventoGrupal.findById(id);
    if (!eventoExistente) {
      return fail(res, 404, "Evento no encontrado");
    }
    if (eventoExistente.estado === "FINALIZADO" || eventoExistente.estado === "CANCELADO") {
      return fail(res, 400, "Este evento ya no está disponible");
    }
    if (eventoExistente.participantes.some((p) => p.uid === uid)) {
      return fail(res, 400, "Ya estás participando en este evento");
    }

    const eventoActualizado = await EventoGrupal.findOneAndUpdate(
      {
        _id: id,
        estado: "ABIERTO",
        $expr: { $lt: [{ $size: "$participantes" }, "$maxParticipantes"] },
        "participantes.uid": { $ne: uid }
      },
      {
        $push: { participantes: { uid, nombre, foto: foto || "", fechaUnion: new Date() } }
      },
      { new: true }
    );

    if (!eventoActualizado) {
      return fail(res, 400, "El evento está completo");
    }

    if (eventoActualizado.participantes.length >= eventoActualizado.maxParticipantes) {
      await EventoGrupal.updateOne(
        { _id: id, estado: "ABIERTO" },
        { $set: { estado: "COMPLETO" } }
      );
      eventoActualizado.estado = "COMPLETO";
    }

    await addXp(uid, 20);
    await addBadge(uid, "TEAM_PLAYER");

    const eventoEnriquecido = enrichEventWithFlags(eventoActualizado.toObject(), uid);

    return ok(res, eventoEnriquecido, "Te has unido al evento");
  } catch (error) {
    console.error("joinEvento error:", error.message);
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

    // Auth: solo puedes salir tú mismo
    if (req.uid !== uid) {
      return fail(res, 403, "No autorizado");
    }

    const evento = await EventoGrupal.findById(id);
    if (!evento) {
      return fail(res, 404, "Evento no encontrado");
    }

    const result = evento.removeParticipante(uid);

    if (!result.success) {
      return fail(res, 400, result.message);
    }

    await evento.save();

    const eventoEnriquecido = enrichEventWithFlags(evento.toObject(), uid);

    return ok(res, eventoEnriquecido, result.message);
  } catch (error) {
    console.error("leaveEvento error:", error.message);
    return fail(res, 500, "Error saliendo del evento");
  }
}

// ========================================
// 8. CANCELAR EVENTO (solo organizador)
// POST /api/events/:id/cancel
// ========================================
async function cancelEvento(req, res) {
  try {
    const { id } = req.params;

    const evento = await EventoGrupal.findById(id);
    if (!evento) {
      return fail(res, 404, "Evento no encontrado");
    }

    // Auth: verificar con el token, no con el body
    if (req.uid !== evento.organizadorUid) {
      return fail(res, 403, "Solo el organizador puede cancelar el evento");
    }

    const result = evento.cancelar();
    await evento.save();

    const eventoEnriquecido = enrichEventWithFlags(evento.toObject(), req.uid);

    return ok(res, eventoEnriquecido, result.message);
  } catch (error) {
    console.error("cancelEvento error:", error.message);
    return fail(res, 500, "Error cancelando evento");
  }
}

// ========================================
// 9. FINALIZAR EVENTO (solo organizador)
// POST /api/events/:id/finish
// ========================================
async function finishEvento(req, res) {
  try {
    const { id } = req.params;

    const evento = await EventoGrupal.findById(id);
    if (!evento) {
      return fail(res, 404, "Evento no encontrado");
    }

    // Auth: verificar con el token, no con el body
    if (req.uid !== evento.organizadorUid) {
      return fail(res, 403, "Solo el organizador puede finalizar el evento");
    }

    if (new Date(evento.fecha) > new Date()) {
      return fail(res, 400, "Solo se puede finalizar un evento después de su fecha");
    }

    const result = evento.finalizar();
    await evento.save();

    const eventoEnriquecido = enrichEventWithFlags(evento.toObject(), req.uid);

    return ok(res, eventoEnriquecido, result.message);
  } catch (error) {
    console.error("finishEvento error:", error.message);
    return fail(res, 500, "Error finalizando evento");
  }
}

// ========================================
// 10. ACTUALIZAR EVENTO (solo organizador)
// PUT /api/events/:id
// ========================================
async function updateEvento(req, res) {
  try {
    const { id } = req.params;
    const { ...updateData } = req.body;

    const evento = await EventoGrupal.findById(id);
    if (!evento) {
      return fail(res, 404, "Evento no encontrado");
    }

    // Auth: verificar con el token, no con el body
    if (req.uid !== evento.organizadorUid) {
      return fail(res, 403, "Solo el organizador puede actualizar el evento");
    }

    const allowedFields = [
      "fecha",
      "maxParticipantes",
      "descripcion",
      "nivelRecomendado",
      "puntoEncuentro",
      "horaEncuentro"
    ];

    for (const field of allowedFields) {
      if (updateData[field] !== undefined) {
        if (field === "descripcion") {
          evento[field] = sanitizeText(updateData[field], 2000);
        } else {
          evento[field] = updateData[field];
        }
      }
    }

    await evento.save();

    const eventoEnriquecido = enrichEventWithFlags(evento.toObject(), req.uid);

    return ok(res, eventoEnriquecido, "Evento actualizado correctamente");
  } catch (error) {
    console.error("updateEvento error:", error.message);
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
