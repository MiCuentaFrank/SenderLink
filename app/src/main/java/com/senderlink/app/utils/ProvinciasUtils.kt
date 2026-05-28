package com.senderlink.app.utils

object ProvinciasUtils {

    val provinciaToComunidad: Map<String, String> = mapOf(
        // Andalucía
        "Almería"               to "Andalucía",
        "Cádiz"                 to "Andalucía",
        "Córdoba"               to "Andalucía",
        "Granada"               to "Andalucía",
        "Huelva"                to "Andalucía",
        "Jaén"                  to "Andalucía",
        "Málaga"                to "Andalucía",
        "Sevilla"               to "Andalucía",
        // Aragón
        "Huesca"                to "Aragón",
        "Teruel"                to "Aragón",
        "Zaragoza"              to "Aragón",
        // Asturias
        "Asturias"              to "Asturias",
        // Islas Baleares
        "Illes Balears"         to "Islas Baleares",
        // Canarias
        "Las Palmas"            to "Canarias",
        "Santa Cruz de Tenerife" to "Canarias",
        // Cantabria
        "Cantabria"             to "Cantabria",
        // Castilla-La Mancha
        "Albacete"              to "Castilla-La Mancha",
        "Ciudad Real"           to "Castilla-La Mancha",
        "Cuenca"                to "Castilla-La Mancha",
        "Guadalajara"           to "Castilla-La Mancha",
        "Toledo"                to "Castilla-La Mancha",
        // Castilla y León
        "Ávila"                 to "Castilla y León",
        "Burgos"                to "Castilla y León",
        "León"                  to "Castilla y León",
        "Palencia"              to "Castilla y León",
        "Salamanca"             to "Castilla y León",
        "Segovia"               to "Castilla y León",
        "Soria"                 to "Castilla y León",
        "Valladolid"            to "Castilla y León",
        "Zamora"                to "Castilla y León",
        // Cataluña
        "Barcelona"             to "Cataluña",
        "Girona"                to "Cataluña",
        "Lleida"                to "Cataluña",
        "Tarragona"             to "Cataluña",
        // Extremadura
        "Badajoz"               to "Extremadura",
        "Cáceres"               to "Extremadura",
        // Galicia
        "A Coruña"              to "Galicia",
        "Lugo"                  to "Galicia",
        "Ourense"               to "Galicia",
        "Pontevedra"            to "Galicia",
        // La Rioja
        "La Rioja"              to "La Rioja",
        // Madrid
        "Madrid"                to "Comunidad de Madrid",
        // Murcia
        "Murcia"                to "Región de Murcia",
        // Navarra
        "Navarra"               to "Comunidad Foral de Navarra",
        // País Vasco
        "Álava"                 to "País Vasco",
        "Gipuzkoa"              to "País Vasco",
        "Bizkaia"               to "País Vasco",
        // Comunidad Valenciana
        "Alicante"              to "Comunidad Valenciana",
        "Castellón"             to "Comunidad Valenciana",
        "Valencia"              to "Comunidad Valenciana",
        // Ciudades autónomas
        "Ceuta"                 to "Ceuta",
        "Melilla"               to "Melilla"
    )

    val provincias: List<String> = provinciaToComunidad.keys.sorted()

    fun getComunidad(provincia: String): String? =
        provinciaToComunidad.entries
            .firstOrNull { it.key.equals(provincia.trim(), ignoreCase = true) }
            ?.value
}
