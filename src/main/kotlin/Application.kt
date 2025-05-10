// src/main/kotlin/com/example/Application.kt
package com.example

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.routing.*
import io.ktor.server.response.*
import io.ktor.server.request.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class Animal(
    val id: Int,
    val nombre: String,
    val especie: String,
    val domesticable: Boolean,
    val desplazamientoId: Int
)

@Serializable
data class AnimalInput(
    val nombre: String,
    val especie: String,
    val domesticable: Boolean,
    val desplazamientoId: Int
)

@Serializable
data class Desplazamiento(
    val id: Int,
    val tipo: String,       // "Aéreo", "Acuático" o "Terrestre"
    val velocidad: Double   // en km/h
)

@Serializable
data class DesplazamientoInput(
    val tipo: String,
    val velocidad: Double
)

private val repoMutex = Mutex()

private val desplazamientos = mutableListOf(
    Desplazamiento(1, "Terrestre", 50.0),
    Desplazamiento(2, "Aéreo", 200.0),
    Desplazamiento(3, "Acuático", 30.0)
)
private var nextDesplId = desplazamientos.size

private val animales = mutableListOf(
    Animal(1, "Cholito", "Perro", true, desplazamientoId = 1),
    Animal(2, "Rui",     "Gato",  true, desplazamientoId = 1),
    Animal(3, "Nemo",    "Pez payaso", false, desplazamientoId = 3),
    Animal(4, "Pájaro",  "Loro",  true, desplazamientoId = 2)
)
private var nextAnimalId = animales.size


fun main() {
    embeddedServer(
        Netty,
        port = 8080,
        host = "127.0.0.1",
        module = Application::module
    ).start(wait = true)
}

fun Application.module() {
    install(ContentNegotiation) {
        json(Json { prettyPrint = true; isLenient = true; ignoreUnknownKeys = true })
    }

    routing {
        get("/") {
            call.respondText("API Animales + Desplazamientos OK!", ContentType.Text.Plain)
        }

        route("/animales") {
            get {
                call.respond(repoMutex.withLock { animales.toList() })
            }
            get("{id}") {
                val id = call.parameters["id"]?.toIntOrNull()
                    ?: return@get call.respond(HttpStatusCode.BadRequest, "ID inválido")
                repoMutex.withLock {
                    animales.find { it.id == id }
                        ?.let { call.respond(it) }
                        ?: call.respond(HttpStatusCode.NotFound, "Animal no encontrado")
                }
            }
            post {
                val input = call.receive<AnimalInput>()
                val nuevo = repoMutex.withLock {
                    Animal(++nextAnimalId, input.nombre, input.especie, input.domesticable, input.desplazamientoId)
                        .also { animales += it }
                }
                call.respond(HttpStatusCode.Created, nuevo)
            }
            put("{id}") {
                val id = call.parameters["id"]?.toIntOrNull()
                    ?: return@put call.respond(HttpStatusCode.BadRequest, "ID inválido")
                val input = call.receive<AnimalInput>()
                val actualizado = repoMutex.withLock {
                    animales.indexOfFirst { it.id == id }
                        .takeIf { it >= 0 }
                        ?.let { idx ->
                            val a = Animal(id, input.nombre, input.especie, input.domesticable, input.desplazamientoId)
                            animales[idx] = a
                            a
                        }
                }
                actualizado?.let { call.respond(it) }
                    ?: call.respond(HttpStatusCode.NotFound, "Animal no encontrado")
            }
            delete("{id}") {
                val id = call.parameters["id"]?.toIntOrNull()
                    ?: return@delete call.respond(HttpStatusCode.BadRequest, "ID inválido")
                val removed = repoMutex.withLock { animales.removeIf { it.id == id } }
                if (removed) call.respond(HttpStatusCode.NoContent)
                else call.respond(HttpStatusCode.NotFound, "Animal no encontrado")
            }
        }

        route("/desplazamientos") {
            get {
                call.respond(repoMutex.withLock { desplazamientos.toList() })
            }
            get("{id}") {
                val id = call.parameters["id"]?.toIntOrNull()
                    ?: return@get call.respond(HttpStatusCode.BadRequest, "ID inválido")
                repoMutex.withLock {
                    desplazamientos.find { it.id == id }
                        ?.let { call.respond(it) }
                        ?: call.respond(HttpStatusCode.NotFound, "Desplazamiento no encontrado")
                }
            }
            get("tipo/{tipo}") {
                val tipo = call.parameters["tipo"]
                    ?: return@get call.respond(HttpStatusCode.BadRequest, "Tipo no especificado")
                val list = repoMutex.withLock {
                    desplazamientos.filter { it.tipo.equals(tipo, ignoreCase = true) }
                }
                call.respond(list)
            }
            post {
                val input = call.receive<DesplazamientoInput>()
                val nuevo = repoMutex.withLock {
                    Desplazamiento(++nextDesplId, input.tipo, input.velocidad)
                        .also { desplazamientos += it }
                }
                call.respond(HttpStatusCode.Created, nuevo)
            }
            put("{id}") {
                val id = call.parameters["id"]?.toIntOrNull()
                    ?: return@put call.respond(HttpStatusCode.BadRequest, "ID inválido")
                val input = call.receive<DesplazamientoInput>()
                val actualizado = repoMutex.withLock {
                    desplazamientos.indexOfFirst { it.id == id }
                        .takeIf { it >= 0 }
                        ?.let { idx ->
                            val d = Desplazamiento(id, input.tipo, input.velocidad)
                            desplazamientos[idx] = d
                            d
                        }
                }
                actualizado?.let { call.respond(it) }
                    ?: call.respond(HttpStatusCode.NotFound, "Desplazamiento no encontrado")
            }
            delete("{id}") {
                val id = call.parameters["id"]?.toIntOrNull()
                    ?: return@delete call.respond(HttpStatusCode.BadRequest, "ID inválido")
                val removed = repoMutex.withLock { desplazamientos.removeIf { it.id == id } }
                if (removed) call.respond(HttpStatusCode.NoContent)
                else call.respond(HttpStatusCode.NotFound, "Desplazamiento no encontrado")
            }
        }

        get("/animales/desplazamientos/{nombreAnimal}") {
            val nombre = call.parameters["nombreAnimal"]
                ?: return@get call.respond(HttpStatusCode.BadRequest, "Nombre de animal no especificado")
            val result = repoMutex.withLock {
                animales.filter { it.nombre.equals(nombre, true) }
                    .mapNotNull { a -> desplazamientos.find { it.id == a.desplazamientoId } }
            }
            call.respond(result)
        }
    }
}
