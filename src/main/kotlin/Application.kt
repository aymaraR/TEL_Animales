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
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonIgnoreUnknownKeys
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonIgnoreUnknownKeys
data class Animal(val id: Int, val nombre: String, val especie: String, val domesticable: Boolean)

@Serializable
data class AnimalInput(val nombre: String, val especie: String, val domesticable: Boolean)

private val animales = mutableListOf(
    Animal(1, "Cholito", "Perro", true),
    Animal(2, "Rui", "Gato", true),
    Animal(3, "Nemo", "Pez payaso", false),
    Animal(4, "Rocky", "Toro", false),
    Animal(3, "Simba", "León", false),
    Animal(3, "Rosa", "Pantera", false),
)
private var currentId = animales.size
private val repoMutex = Mutex()

fun main() {
    embeddedServer(Netty, port = 8080, host = "127.0.0.1", module = Application::module)
        .start(wait = true)
}

fun Application.module() {
    install(ContentNegotiation) {
        json(Json {
            prettyPrint = true
            isLenient = true
            ignoreUnknownKeys = true
        })
    }

    routing {
        get("/") {
            call.respondText("¡Entregable certamen Aymara Rojas!", ContentType.Text.Plain)
        }

        route("/animales") {
            get {
                repoMutex.withLock {
                    call.respond(animales.toList())
                }
            }
            get("{id}") {
                val id = call.parameters["id"]?.toIntOrNull()
                if (id == null) {
                    call.respond(HttpStatusCode.BadRequest, "ID inválido")
                    return@get
                }
                val animal = repoMutex.withLock { animales.find { it.id == id } }
                if (animal != null) {
                    call.respond(animal)
                } else {
                    call.respond(HttpStatusCode.NotFound, "Animal no encontrado")
                }
            }
            post {
                val input = call.receive<AnimalInput>()
                val nuevo = repoMutex.withLock {
                    val animal = Animal(++currentId, input.nombre, input.especie, input.domesticable)
                    animales += animal
                    animal
                }
                call.respond(HttpStatusCode.Created, nuevo)
            }

            put("{id}") {
                val id = call.parameters["id"]?.toIntOrNull()
                if (id == null) {
                    call.respond(HttpStatusCode.BadRequest, "ID inválido")
                    return@put
                }
                val input = call.receive<AnimalInput>()
                val actualizado = repoMutex.withLock {
                    val idx = animales.indexOfFirst { it.id == id }
                    if (idx == -1) null
                    else {
                        val animal = Animal(id, input.nombre, input.especie, input.domesticable)
                        animales[idx] = animal
                        animal
                    }
                }
                if (actualizado != null) {
                    call.respond(actualizado)
                } else {
                    call.respond(HttpStatusCode.NotFound, "Animal no encontrado")
                }
            }

            delete("{id}") {
                val id = call.parameters["id"]?.toIntOrNull()
                if (id == null) {
                    call.respond(HttpStatusCode.BadRequest, "ID inválido")
                    return@delete
                }
                val removed = repoMutex.withLock { animales.removeIf { it.id == id } }
                if (removed) {
                    call.respond(HttpStatusCode.NoContent)
                } else {
                    call.respond(HttpStatusCode.NotFound, "Animal no encontrado")
                }
            }
        }
    }
}
