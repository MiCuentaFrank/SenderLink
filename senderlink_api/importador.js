global.self = global;

require("dotenv").config();
const mongoose = require("mongoose");
const fs = require("fs");
const path = require("path");
const shapefile = require("shapefile");
const gpxParse = require("gpx-parse");
const axios = require("axios");

const Route = require("./models/Route");

const MONGO_URI = process.env.MONGO_URI;
const GOOGLE_API_KEY = process.env.GOOGLE_MAPS_KEY;
const RUTAS_BASE = path.join(__dirname, "rutas_gpx");

// ===================================================
// UTILIDADES DE CÁLCULO
// ===================================================

function calcularDistancia(lat1, lon1, lat2, lon2) {
  const R = 6371;
  const dLat = ((lat2 - lat1) * Math.PI) / 180;
  const dLon = ((lon2 - lon1) * Math.PI) / 180;
  const a =
    Math.sin(dLat / 2) * Math.sin(dLat / 2) +
    Math.cos((lat1 * Math.PI) / 180) *
      Math.cos((lat2 * Math.PI) / 180) *
      Math.sin(dLon / 2) *
      Math.sin(dLon / 2);
  const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
  return R * c;
}

function calcularDistanciaTotal(coords) {
  let total = 0;
  for (let i = 1; i < coords.length; i++) {
    total += calcularDistancia(
      coords[i - 1][1],
      coords[i - 1][0],
      coords[i][1],
      coords[i][0]
    );
  }
  return total;
}

function calcularDesniveles(elevaciones) {
  let desnivelPositivo = 0;
  let desnivelNegativo = 0;
  let eleMin = elevaciones[0];
  let eleMax = elevaciones[0];

  for (let i = 1; i < elevaciones.length; i++) {
    const diff = elevaciones[i] - elevaciones[i - 1];
    if (diff > 0) desnivelPositivo += diff;
    if (diff < 0) desnivelNegativo += Math.abs(diff);
    
    eleMin = Math.min(eleMin, elevaciones[i]);
    eleMax = Math.max(eleMax, elevaciones[i]);
  }

  return {
    desnivelPositivo: Math.round(desnivelPositivo),
    desnivelNegativo: Math.round(desnivelNegativo),
    altitudMin: Math.round(eleMin),
    altitudMax: Math.round(eleMax)
  };
}

function esRutaCircular(startPoint, endPoint, distanceKm) {
  const distancia = calcularDistancia(
    startPoint.lat,
    startPoint.lng,
    endPoint.lat,
    endPoint.lng
  );
  return distancia < (distanceKm * 0.05);
}

// ===================================================
// DEDUPLICACIÓN
// ===================================================

function normalizarNombre(nombre) {
  return nombre
    .toLowerCase()
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .replace(/[^a-z0-9]/g, "")
    .trim();
}

function extraerCodigoSendero(nombre) {
  const patterns = [
    /\b(GR|PR|SL)[-\s]*([A-Z]{1,2})?\s*[-\s]*(\d+)/i,
    /\b(GR|PR|SL)[-\s]*(\d+)/i
  ];

  for (const pattern of patterns) {
    const match = nombre.match(pattern);
    if (match) {
      const tipo = match[1].toUpperCase();
      const provincia = match[2] ? match[2].toUpperCase() : "";
      const numero = match[3] || match[2];
      return `${tipo}${provincia}${numero}`;
    }
  }

  return null;
}

function calcularSimilitudNombre(str1, str2) {
  const bigrams1 = obtenerBigrams(str1);
  const bigrams2 = obtenerBigrams(str2);

  const intersection = bigrams1.filter(b => bigrams2.includes(b));

  return (2.0 * intersection.length) / (bigrams1.length + bigrams2.length);
}

function obtenerBigrams(str) {
  const bigrams = [];
  for (let i = 0; i < str.length - 1; i++) {
    bigrams.push(str.substring(i, i + 2));
  }
  return bigrams;
}

function sonRutasDuplicadas(ruta1, ruta2) {
  if (ruta1.externalId === ruta2.externalId) {
    return true;
  }

  const codigo1 = extraerCodigoSendero(ruta1.name);
  const codigo2 = extraerCodigoSendero(ruta2.name);

  if (codigo1 && codigo2 && codigo1 === codigo2) {
    const diffDistancia = Math.abs(ruta1.distanceKm - ruta2.distanceKm);
    const tolerancia = Math.max(ruta1.distanceKm, ruta2.distanceKm) * 0.1;

    if (diffDistancia <= tolerancia) {
      return true;
    }
  }

  const nombre1Norm = normalizarNombre(ruta1.name);
  const nombre2Norm = normalizarNombre(ruta2.name);

  const similaridadNombre = calcularSimilitudNombre(nombre1Norm, nombre2Norm);

  if (similaridadNombre > 0.8) {
    const diffDist = Math.abs(ruta1.distanceKm - ruta2.distanceKm);
    const toleranciaDist = Math.max(ruta1.distanceKm, ruta2.distanceKm) * 0.05;

    const distInicio = calcularDistancia(
      ruta1.startPoint.lat,
      ruta1.startPoint.lng,
      ruta2.startPoint.lat,
      ruta2.startPoint.lng
    );

    if (diffDist <= toleranciaDist && distInicio <= 1) {
      return true;
    }
  }

  return false;
}

function seleccionarMejorRuta(ruta1, ruta2) {
  const prioridadFuente = {
    'FEDME': 3,
    'PARQUES_NACIONALES': 2,
    'PROPIO': 1
  };

  const prioridad1 = prioridadFuente[ruta1.source] || 0;
  const prioridad2 = prioridadFuente[ruta2.source] || 0;

  if (prioridad1 !== prioridad2) {
    return prioridad1 > prioridad2 ? ruta1 : ruta2;
  }

  const score1 = calcularScoreCompletitud(ruta1);
  const score2 = calcularScoreCompletitud(ruta2);

  return score1 >= score2 ? ruta1 : ruta2;
}

function calcularScoreCompletitud(ruta) {
  let score = 0;

  if (ruta.description && ruta.description.length > 100) score += 3;
  if (ruta.extraInfo?.desnivelPositivo) score += 2;
  if (ruta.extraInfo?.altitudMin && ruta.extraInfo?.altitudMax) score += 2;
  if (ruta.provincia) score += 1;
  if (ruta.fechaEdicion) score += 1;
  if (ruta.extraInfo?.homologado) score += 1;

  return score;
}

// ===================================================
// CONEXIÓN Y GEOCODING
// ===================================================

async function connectDB() {
  await mongoose.connect(MONGO_URI);
  console.log("🟢 Conectado a MongoDB");
}

function getStartAndEndPoints(geometry) {
  let coords = geometry.coordinates;

  if (geometry.type === "MultiLineString") {
    coords = coords[0];
  }

  const start = coords[0];
  const end = coords[coords.length - 1];

  return {
    startPoint: {
      lat: Number(start[1]),
      lng: Number(start[0])
    },
    endPoint: {
      lat: Number(end[1]),
      lng: Number(end[0])
    }
  };
}

const localityCache = new Map();

async function getLocalityFromCoords(lat, lng) {
  const key = `${lat.toFixed(3)},${lng.toFixed(3)}`;

  if (localityCache.has(key)) {
    return localityCache.get(key);
  }

  try {
    const url = `https://maps.googleapis.com/maps/api/geocode/json?latlng=${lat},${lng}&language=es&key=${GOOGLE_API_KEY}`;
    const res = await axios.get(url);

    const components = res.data.results[0]?.address_components || [];

    const locality =
      components.find(c => c.types.includes("locality"))?.long_name ||
      components.find(c => c.types.includes("administrative_area_level_2"))?.long_name ||
      components.find(c => c.types.includes("administrative_area_level_3"))?.long_name ||
      "Localidad desconocida";

    const provincia = components.find(c =>
      c.types.includes("administrative_area_level_2")
    )?.long_name;

    const comunidad = components.find(c =>
      c.types.includes("administrative_area_level_1")
    )?.long_name;

    const result = { locality, provincia, comunidad };
    localityCache.set(key, result);

    await new Promise(resolve => setTimeout(resolve, 100));

    return result;
  } catch (error) {
    console.error(`Error obteniendo localidad para ${lat},${lng}:`, error.message);
    return {
      locality: "Localidad desconocida",
      provincia: null,
      comunidad: null
    };
  }
}

// ===================================================
// CÁLCULOS Y GENERACIÓN
// ===================================================

function calcularDificultad({ distanceKm, durationMin, type, desnivelPositivo }) {
  let puntos = 0;

  if (distanceKm <= 5) puntos += 1;
  else if (distanceKm <= 10) puntos += 2;
  else if (distanceKm <= 20) puntos += 3;
  else puntos += 4;

  if (desnivelPositivo) {
    if (desnivelPositivo <= 200) puntos += 1;
    else if (desnivelPositivo <= 500) puntos += 2;
    else if (desnivelPositivo <= 1000) puntos += 3;
    else puntos += 4;
  }

  if (durationMin) {
    if (durationMin <= 90) puntos += 1;
    else if (durationMin <= 180) puntos += 2;
    else if (durationMin <= 300) puntos += 3;
    else puntos += 4;
  }

  if (type === "VIA_VERDE") puntos -= 2;
  if (type === "GR") puntos += 1;
  if (type === "PARQUE_NACIONAL") puntos += 1;

  if (puntos <= 3) return "FACIL";
  if (puntos <= 6) return "MODERADA";
  return "DIFICIL";
}

function generarDescripcion(route) {
  const partes = [];

  const tipoTexto = {
    'PR': 'Sendero de Pequeño Recorrido (PR)',
    'GR': 'Sendero de Gran Recorrido (GR)',
    'SL': 'Sendero Local (SL)',
    'VIA_VERDE': 'Vía Verde',
    'PARQUE_NACIONAL': 'Ruta de Parque Nacional'
  };

  partes.push(`${tipoTexto[route.type] || 'Ruta'} que ${route.esCircular ? 'realiza un recorrido circular' : 'discurre'} por ${route.startLocality}`);

  if (route.provincia && route.provincia !== route.startLocality) {
    partes.push(`en la provincia de ${route.provincia}`);
  }

  const tecnico = [];
  tecnico.push(`${route.distanceKm.toFixed(1)} km de longitud`);

  if (route.durationMin) {
    const horas = Math.floor(route.durationMin / 60);
    const minutos = route.durationMin % 60;
    tecnico.push(`${horas}h${minutos > 0 ? ` ${minutos}min` : ''} de duración estimada`);
  }

  if (route.extraInfo?.desnivelPositivo) {
    tecnico.push(`${route.extraInfo.desnivelPositivo}m de desnivel positivo`);
  }

  partes.push(`Con ${tecnico.join(', ')}`);

  const dificultadTexto = {
    'FACIL': 'apta para todos los públicos',
    'MODERADA': 'de dificultad moderada, requiere cierta preparación física',
    'DIFICIL': 'de alta dificultad, recomendada para senderistas experimentados'
  };

  partes.push(`esta ruta es ${dificultadTexto[route.difficulty]}`);

  if (route.extraInfo?.parqueNacional) {
    partes.push(`Situada en el Parque Nacional de ${route.extraInfo.parqueNacional}`);
  }

  return partes.join('. ') + '.';
}

function extraerMetadataGPX(gpxContent) {
  const metadata = {};

  const nameMatch = gpxContent.match(/<name>(.*?)<\/name>/);
  if (nameMatch) metadata.name = nameMatch[1].trim();

  const descMatch = gpxContent.match(/<desc>(.*?)<\/desc>/s);
  if (descMatch) metadata.description = descMatch[1].trim();

  const boundsMatch = gpxContent.match(/minlat="([^"]+)" minlon="([^"]+)" maxlat="([^"]+)" maxlon="([^"]+)"/);
  if (boundsMatch) {
    metadata.bounds = {
      minLat: parseFloat(boundsMatch[1]),
      minLon: parseFloat(boundsMatch[2]),
      maxLat: parseFloat(boundsMatch[3]),
      maxLon: parseFloat(boundsMatch[4])
    };
  }

  return metadata;
}

function generarImagenes(nombre) {
  const baseUrl = "https://via.placeholder.com";
  return {
    coverImage: `${baseUrl}/1200x600/4CAF50/ffffff?text=${encodeURIComponent(nombre)}`,
    images: [
      `${baseUrl}/800x600/2196F3/ffffff?text=Inicio`,
      `${baseUrl}/800x600/FF9800/ffffff?text=Durante`,
      `${baseUrl}/800x600/F44336/ffffff?text=Final`
    ]
  };
}

function extraerInfoDelCodigo(codigo) {
  const info = {
    provincia: null,
    numero: null
  };

  if (!codigo) return info;

  const match = codigo.match(/^[A-Z]+-([A-Z]+)-(.+)$/);
  if (match) {
    const codigoProv = match[1];
    info.numero = match[2];

    const provincias = {
      'C': 'Barcelona', 'GI': 'Girona', 'L': 'Lleida', 'T': 'Tarragona',
      'Z': 'Zaragoza', 'HU': 'Huesca', 'TE': 'Teruel',
      'NA': 'Navarra', 'SS': 'Gipuzkoa', 'BI': 'Bizkaia', 'VI': 'Álava',
      'M': 'Madrid', 'TO': 'Toledo', 'CU': 'Cuenca', 'GU': 'Guadalajara',
      'AB': 'Albacete', 'CR': 'Ciudad Real',
      'A': 'Alicante', 'V': 'Valencia', 'CS': 'Castellón',
      'BA': 'Badajoz', 'CC': 'Cáceres',
      'SE': 'Sevilla', 'MA': 'Málaga', 'CO': 'Córdoba', 'CA': 'Cádiz',
      'GR': 'Granada', 'AL': 'Almería', 'JA': 'Jaén', 'H': 'Huelva',
      'MU': 'Murcia',
      'VA': 'Valladolid', 'LE': 'León', 'ZA': 'Zamora', 'SA': 'Salamanca',
      'P': 'Palencia', 'BU': 'Burgos', 'SO': 'Soria', 'SG': 'Segovia', 'AV': 'Ávila',
      'LO': 'La Rioja',
      'PM': 'Baleares', 'TF': 'Santa Cruz de Tenerife', 'GC': 'Las Palmas',
      'O': 'Asturias', 'S': 'Cantabria', 'LU': 'Lugo', 'OR': 'Ourense',
      'PO': 'Pontevedra', 'CO': 'A Coruña'
    };

    info.provincia = provincias[codigoProv] || null;
  }

  return info;
}

function parsearFechaFEDME(fechaStr) {
  if (!fechaStr) return null;

  try {
    const str = String(fechaStr);
    if (str.length === 8) {
      const year = str.substring(0, 4);
      const month = str.substring(4, 6);
      const day = str.substring(6, 8);
      return new Date(`${year}-${month}-${day}`);
    }
    return null;
  } catch {
    return null;
  }
}

function getComunidadPorProvincia(provincia) {
  if (!provincia) return null;

  const mapa = {
    'Barcelona': 'Cataluña', 'Girona': 'Cataluña', 'Lleida': 'Cataluña', 'Tarragona': 'Cataluña',
    'Zaragoza': 'Aragón', 'Huesca': 'Aragón', 'Teruel': 'Aragón',
    'Gipuzkoa': 'País Vasco', 'Bizkaia': 'País Vasco', 'Álava': 'País Vasco',
    'Navarra': 'Navarra',
    'Alicante': 'Comunidad Valenciana', 'Valencia': 'Comunidad Valenciana', 'Castellón': 'Comunidad Valenciana',
    'Badajoz': 'Extremadura', 'Cáceres': 'Extremadura',
    'Sevilla': 'Andalucía', 'Málaga': 'Andalucía', 'Córdoba': 'Andalucía', 'Cádiz': 'Andalucía',
    'Granada': 'Andalucía', 'Almería': 'Andalucía', 'Jaén': 'Andalucía', 'Huelva': 'Andalucía',
    'Murcia': 'Región de Murcia',
    'Valladolid': 'Castilla y León', 'León': 'Castilla y León', 'Zamora': 'Castilla y León',
    'Salamanca': 'Castilla y León', 'Palencia': 'Castilla y León', 'Burgos': 'Castilla y León',
    'Soria': 'Castilla y León', 'Segovia': 'Castilla y León', 'Ávila': 'Castilla y León',
    'Toledo': 'Castilla-La Mancha', 'Cuenca': 'Castilla-La Mancha', 'Guadalajara': 'Castilla-La Mancha',
    'Albacete': 'Castilla-La Mancha', 'Ciudad Real': 'Castilla-La Mancha',
    'Madrid': 'Comunidad de Madrid',
    'La Rioja': 'La Rioja',
    'Baleares': 'Islas Baleares',
    'Santa Cruz de Tenerife': 'Canarias', 'Las Palmas': 'Canarias',
    'Asturias': 'Principado de Asturias',
    'Cantabria': 'Cantabria',
    'Lugo': 'Galicia', 'Ourense': 'Galicia', 'Pontevedra': 'Galicia', 'A Coruña': 'Galicia'
  };

  return mapa[provincia] || null;
}

// ===================================================
// CONSTRUIR ROUTE CON DEDUPLICACIÓN
// ===================================================

async function construirRoute(data) {
  if (!Number.isFinite(data.distanceKm) || data.distanceKm <= 0) {
    console.warn(`⚠️ Distancia inválida para ${data.name}, calculando...`);

    let coords = data.geometry.coordinates;
    if (data.geometry.type === "MultiLineString") {
      coords = coords[0];
    }

    data.distanceKm = calcularDistanciaTotal(coords);
  }

  const points = getStartAndEndPoints(data.geometry);
  const startPoint = points.startPoint;
  const endPoint = points.endPoint;

  const esCircular = esRutaCircular(startPoint, endPoint, data.distanceKm);

  const locationInfo = await getLocalityFromCoords(startPoint.lat, startPoint.lng);

  if (!data.provincia && locationInfo.provincia) {
    data.provincia = locationInfo.provincia;
  }
  if (!data.comunidad && locationInfo.comunidad) {
    data.comunidad = locationInfo.comunidad;
  }

  if (data.elevaciones && data.elevaciones.length > 0) {
    const desniveles = calcularDesniveles(data.elevaciones);
    data.extraInfo = {
      ...data.extraInfo,
      ...desniveles
    };
  }

  if (!data.durationMin || data.durationMin <= 0) {
    let duracion = (data.distanceKm / 3) * 60;
    if (data.extraInfo?.desnivelPositivo) {
      duracion += (data.extraInfo.desnivelPositivo / 100) * 10;
    }
    data.durationMin = Math.round(duracion);
  }

  const difficulty = calcularDificultad({
    ...data,
    desnivelPositivo: data.extraInfo?.desnivelPositivo
  });

  const imgs = generarImagenes(data.name);

  const routeData = {
    externalId: data.externalId,
    code: data.code,
    name: data.name,
    type: data.type,
    source: data.source,
    distanceKm: Math.round(data.distanceKm * 100) / 100,
    durationMin: data.durationMin,
    difficulty,
    description: data.description || generarDescripcion({
      ...data,
      difficulty,
      startLocality: locationInfo.locality,
      esCircular
    }),
    coverImage: imgs.coverImage,
    images: imgs.images,
    geometry: data.geometry,
    startPoint,
    endPoint,
    startLocality: locationInfo.locality,
    comunidad: data.comunidad || locationInfo.comunidad || "Desconocida",
    provincia: data.provincia || locationInfo.provincia,
    fechaEdicion: data.fechaEdicion,
    extraInfo: {
      ...data.extraInfo,
      esCircular,
      parqueNacional: data.extraInfo?.parqueNacional
    }
  };

  // ===== DEDUPLICACIÓN =====

  let existente = await Route.findOne({ externalId: routeData.externalId });

  if (existente) {
    console.log(`⏭️  Ya existe (externalId): ${routeData.code}`);
    return null;
  }

  const codigoSendero = extraerCodigoSendero(routeData.name);
  if (codigoSendero) {
    const candidatas = await Route.find({
      name: new RegExp(codigoSendero.replace(/(\d+)/, '[-\\s]*$1'), 'i')
    }).limit(10);

    for (const candidata of candidatas) {
      if (sonRutasDuplicadas(routeData, candidata.toObject())) {
        const mejor = seleccionarMejorRuta(routeData, candidata.toObject());

        if (mejor === routeData) {
          await Route.findByIdAndUpdate(candidata._id, routeData);
          console.log(`🔄 Actualizada: ${routeData.code} (era: ${candidata.source}, ahora: ${routeData.source})`);
          return null;
        } else {
          console.log(`⏭️  Ya existe (duplicado): ${routeData.code} (manteniendo: ${candidata.source})`);
          return null;
        }
      }
    }
  }

  return routeData;
}

// ===================================================
// IMPORTADORES
// ===================================================

async function importarGpx(carpetaParques) {
  if (!fs.existsSync(carpetaParques)) {
    console.warn(`⚠️ No existe la carpeta: ${carpetaParques}`);
    return;
  }

  const carpetas = fs.readdirSync(carpetaParques, { withFileTypes: true })
    .filter(d => d.isDirectory())
    .map(d => path.join(carpetaParques, d.name));

  let totalImportadas = 0;

  for (const carpeta of carpetas) {
    const nombreParque = path.basename(carpeta);
    const files = fs.readdirSync(carpeta).filter(f => f.endsWith(".gpx"));

    console.log(`\n📂 Procesando parque: ${nombreParque} (${files.length} archivos)`);

    for (const file of files) {
      const filePath = path.join(carpeta, file);
      const gpxContent = fs.readFileSync(filePath, "utf8");

      try {
        const metadata = extraerMetadataGPX(gpxContent);

        const data = await new Promise((res, rej) =>
          gpxParse.parseGpx(gpxContent, (e, d) => e ? rej(e) : res(d))
        );

        for (let i = 0; i < data.tracks.length; i++) {
          const track = data.tracks[i];
          if (!track.segments?.length) continue;

          const puntos = track.segments.flat();
          const coords = puntos.map(p => [Number(p.lon), Number(p.lat)]);
          const elevaciones = puntos
            .map(p => p.elevation)
            .filter(e => e !== undefined && e !== null);

          if (coords.length < 2) continue;

          const distanceKm = calcularDistanciaTotal(coords);

          let desnivelInfo = null;
          if (elevaciones.length > 0) {
            desnivelInfo = calcularDesniveles(elevaciones);
          }

          let durationMin = null;
          if (track.time) {
            durationMin = Math.round(track.time / 60);
          } else {
            durationMin = Math.round((distanceKm / 3) * 60);
            if (desnivelInfo?.desnivelPositivo) {
              durationMin += Math.round((desnivelInfo.desnivelPositivo / 100) * 10);
            }
          }

          const nombreRuta = track.name || metadata.name || file.replace('.gpx', '');

          const routeData = {
            externalId: `PN_${nombreParque}_${file}_${i}`.replace(/\s+/g, "_"),
            code: nombreRuta,
            name: nombreRuta,
            type: "PARQUE_NACIONAL",
            source: "PARQUES_NACIONALES",
            distanceKm,
            durationMin,
            description: metadata.description,
            geometry: { type: "LineString", coordinates: coords },
            elevaciones: elevaciones.length > 0 ? elevaciones : null,
            comunidad: "Parques Nacionales",
            extraInfo: {
              parqueNacional: nombreParque,
              bounds: metadata.bounds,
              ...(desnivelInfo || {})
            }
          };

          const route = await construirRoute(routeData);

          if (route) {
            await Route.create(route);
            totalImportadas++;
            console.log(`✅ ${route.name} | ${route.distanceKm}km | ${route.difficulty}`);
          }
        }
      } catch (e) {
        console.error(`❌ Error en ${file}:`, e.message);
      }
    }
  }

  console.log(`\n🎉 Total rutas importadas (Parques): ${totalImportadas}`);
}

async function importarShapefile(carpeta, type) {
  if (!fs.existsSync(carpeta)) {
    console.warn(`⚠️ No existe la carpeta: ${carpeta}`);
    return;
  }

  const shpFile = fs.readdirSync(carpeta).find(f => f.endsWith(".shp"));
  if (!shpFile) {
    console.warn(`⚠️ No se encontró archivo .shp en ${carpeta}`);
    return;
  }

  console.log(`\n📊 Procesando shapefile: ${type}`);

  const source = await shapefile.open(path.join(carpeta, shpFile));
  let totalImportadas = 0;
  let errores = 0;

  while (true) {
    const result = await source.read();
    if (result.done) break;

    try {
      const p = result.value.properties;

      const codigoInfo = extraerInfoDelCodigo(p.id);

      const nombre = (p.nombre || p.name || p.id || 'Sendero sin nombre').trim();

      let distanceKm = Number(String(p.longitud || p.length || p.distance || "").replace(",", "."));

      if (!Number.isFinite(distanceKm) || distanceKm <= 0) {
        let coords = result.value.geometry.coordinates;
        if (result.value.geometry.type === "MultiLineString") {
          coords = coords[0];
        }
        distanceKm = calcularDistanciaTotal(coords);
      }

      const fechaEdicion = parsearFechaFEDME(p.fecha_edi);

      const provincia = codigoInfo.provincia;
      const comunidad = provincia ? getComunidadPorProvincia(provincia) : null;

      const routeData = {
        externalId: p.id || `${type}_${Date.now()}_${Math.random()}`,
        code: p.id,
        name: nombre,
        type,
        source: "FEDME",
        distanceKm,
        geometry: result.value.geometry,
        comunidad: comunidad || "España",
        provincia: provincia,
        fechaEdicion,
        extraInfo: {
          numeroSendero: codigoInfo.numero,
          homologado: true,
          federacion: "FEDME"
        }
      };

      const route = await construirRoute(routeData);

      if (route) {
        await Route.create(route);
        totalImportadas++;

        const provinciaStr = provincia ? ` | ${provincia}` : '';
        console.log(`✅ ${route.code} | ${route.distanceKm.toFixed(1)}km | ${route.difficulty}${provinciaStr}`);
      }

    } catch (error) {
      errores++;
      console.error(`❌ Error procesando registro:`, error.message);
    }
  }

  console.log(`\n${'='.repeat(60)}`);
  console.log(`🎉 Importación ${type} completada`);
  console.log(`   ✅ Importadas: ${totalImportadas}`);
  if (errores > 0) console.log(`   ❌ Errores: ${errores}`);
  console.log(`${'='.repeat(60)}`);
}

async function mostrarEstadisticas() {
  const stats = await Route.aggregate([
    {
      $group: {
        _id: {
          type: "$type",
          source: "$source"
        },
        count: { $sum: 1 }
      }
    },
    {
      $sort: { "_id.type": 1, "_id.source": 1 }
    }
  ]);

  console.log("\n📊 ESTADÍSTICAS POR FUENTE:");
  console.log("=".repeat(60));

  const totalesPorTipo = {};

  stats.forEach(stat => {
    const tipo = stat._id.type;
    const fuente = stat._id.source;
    const count = stat.count;

    totalesPorTipo[tipo] = (totalesPorTipo[tipo] || 0) + count;

    console.log(`${tipo.padEnd(20)} | ${fuente.padEnd(25)} | ${count}`);
  });

  console.log("=".repeat(60));
  console.log("TOTALES POR TIPO:");
  Object.entries(totalesPorTipo).forEach(([tipo, count]) => {
    console.log(`${tipo.padEnd(20)} | ${count}`);
  });

  const total = await Route.countDocuments();
  console.log("=".repeat(60));
  console.log(`TOTAL DE RUTAS: ${total}`);
  console.log("=".repeat(60));
}

async function importar() {
  try {
    await connectDB();
    console.log("🚀 Importación iniciada con deduplicación\n");

    const inicio = Date.now();

    // ORDEN IMPORTANTE: Primero FEDME (oficial)
    await importarShapefile(path.join(RUTAS_BASE, "SenderosFEDME_GR"), "GR");
    await importarShapefile(path.join(RUTAS_BASE, "SenderosFEDME_PR"), "PR");
    await importarShapefile(path.join(RUTAS_BASE, "SenderosFEDME_SL"), "SL");
    await importarShapefile(path.join(RUTAS_BASE, "ViasVerdes"), "VIA_VERDE");

    // Después Parques Nacionales
    await importarGpx(path.join(RUTAS_BASE, "parques_nacionales"));

    const duracion = ((Date.now() - inicio) / 1000).toFixed(2);

    await mostrarEstadisticas();

    console.log("\n" + "=".repeat(60));
    console.log("✅ IMPORTACIÓN FINALIZADA");
    console.log("=".repeat(60));
    console.log(`⏱️  Tiempo total: ${duracion}s`);
    console.log("=".repeat(60));

    process.exit(0);

  } catch (e) {
    console.error("\n❌ Error en importador:", e);
    process.exit(1);
  }
}

importar();