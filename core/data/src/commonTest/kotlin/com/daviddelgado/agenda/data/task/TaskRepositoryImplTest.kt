package com.daviddelgado.agenda.data.task

import com.daviddelgado.agenda.data.fake.FakePendingDeletionDao
import com.daviddelgado.agenda.data.fake.FakeTaskDao
import com.daviddelgado.agenda.data.fake.mockHttpClient
import com.daviddelgado.agenda.data.fake.respondJson
import com.daviddelgado.agenda.domain.model.Task
import com.daviddelgado.agenda.network.api.TaskApi
import com.daviddelgado.agenda.network.dto.TaskDto
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private val HOY = LocalDate(2026, 9, 13)
private val MANANA = LocalDate(2026, 9, 14)

private fun tarea(
    id: String,
    date: LocalDate = HOY,
    title: String = "Tarea $id",
) = Task(id = id, title = title, date = date)

class TaskRepositoryImplTest {
    private val llamadas = mutableListOf<String>()

    /** Backend simulado: devuelve [remotas] en GET /tasks y acepta el resto de llamadas. */
    private fun repositorio(
        dao: FakeTaskDao,
        pendingDeletionDao: FakePendingDeletionDao = FakePendingDeletionDao(),
        remotas: List<TaskDto> = emptyList(),
        fallar: Boolean = false,
    ): TaskRepositoryImpl {
        val client =
            mockHttpClient { request ->
                llamadas += "${request.method.value} /${request.url.encodedPath.trimStart('/')}"
                when {
                    fallar -> respondError(HttpStatusCode.InternalServerError)
                    request.url.encodedPath.endsWith("/tasks") && request.method.value == "GET" ->
                        respondJson(Json.encodeToString(remotas))
                    request.url.encodedPath.endsWith("/bulk-delete") -> respondJson("""{"deleted":2}""")
                    request.method.value == "DELETE" && request.url.encodedPath.endsWith("/tasks") ->
                        respondJson("""{"deleted":1}""")
                    request.method.value == "DELETE" -> respondJson("", HttpStatusCode.NoContent)
                    request.method.value == "PATCH" -> respondJson("", HttpStatusCode.NoContent)
                    else -> respondJson(Json.encodeToString(tarea("t-1").toDto()), HttpStatusCode.Created)
                }
            }
        return TaskRepositoryImpl(dao, pendingDeletionDao, TaskApi(client))
    }

    @Test
    fun leerTareasVieneSoloDeRoomYNoTocaLaRed() =
        runTest {
            val dao = FakeTaskDao(listOf(tarea("t-1").toEntity(), tarea("t-2", MANANA).toEntity()))
            val repository = repositorio(dao)

            val deHoy = repository.observeTasks(HOY).first()

            assertEquals(listOf("t-1"), deHoy.map { it.id })
            assertTrue(llamadas.isEmpty())
        }

    @Test
    fun guardarUnaTareaLaEscribeEnLocalYLaSubeAlServidor() =
        runTest {
            val dao = FakeTaskDao()
            val repository = repositorio(dao)

            repository.upsertTask(tarea("t-1"))

            assertEquals("POST /tasks", llamadas.single())
            assertFalse(dao.byId("t-1")!!.pendingSync)
        }

    @Test
    fun siElServidorFallaLaTareaSeGuardaEnLocalYQuedaPendiente() =
        runTest {
            val dao = FakeTaskDao()
            val repository = repositorio(dao, fallar = true)

            // No debe lanzar: la app funciona sin red (offline-first).
            repository.upsertTask(tarea("t-1"))

            assertEquals("Tarea t-1", dao.byId("t-1")?.title)
            assertTrue(dao.byId("t-1")!!.pendingSync)
        }

    @Test
    fun editarUnaTareaExistenteUsaPutEnLugarDePost() =
        runTest {
            val dao = FakeTaskDao(listOf(tarea("t-1").toEntity()))
            val repository = repositorio(dao)

            repository.upsertTask(tarea("t-1", title = "Titulo nuevo"))

            assertEquals("PUT /tasks/t-1", llamadas.single())
        }

    @Test
    fun sincronizarSubeLoPendienteYBajaLoDelServidor() =
        runTest {
            val dao =
                FakeTaskDao(
                    listOf(
                        tarea("pendiente").toEntity(pendingSync = true),
                        tarea("borrada-en-servidor").toEntity(),
                    ),
                )
            val remota = tarea("remota", title = "Viene del servidor").toDto()
            val repository = repositorio(dao, remotas = listOf(tarea("pendiente").toDto(), remota))

            val resultado = repository.syncTasks()

            assertTrue(resultado.isSuccess)
            assertEquals(listOf("POST /tasks", "GET /tasks"), llamadas)
            // Lo pendiente queda subido, lo del servidor baja y lo que el servidor ya no
            // tiene desaparece de local.
            assertEquals(setOf("pendiente", "remota"), dao.all.map { it.id }.toSet())
            assertFalse(dao.byId("pendiente")!!.pendingSync)
            assertEquals("Viene del servidor", dao.byId("remota")?.title)
        }

    @Test
    fun sincronizarDevuelveFailureSinRedYNoPierdeLoPendiente() =
        runTest {
            val dao = FakeTaskDao(listOf(tarea("pendiente").toEntity(pendingSync = true)))
            val repository = repositorio(dao, fallar = true)

            val resultado = repository.syncTasks()

            assertTrue(resultado.isFailure)
            assertTrue(dao.byId("pendiente")!!.pendingSync)
        }

    @Test
    fun borrarUnaTareaLaQuitaDeLocalYDelServidor() =
        runTest {
            val dao = FakeTaskDao(listOf(tarea("t-1").toEntity()))
            val repository = repositorio(dao)

            repository.deleteTask("t-1")

            assertTrue(dao.all.isEmpty())
            assertEquals("DELETE /tasks/t-1", llamadas.single())
        }

    @Test
    fun borradoConjuntoUsaUnaSolaLlamadaAlServidor() =
        runTest {
            val dao =
                FakeTaskDao(
                    listOf(tarea("t-1").toEntity(), tarea("t-2").toEntity(), tarea("t-3").toEntity()),
                )
            val repository = repositorio(dao)

            repository.deleteTasks(listOf("t-1", "t-3"))

            assertEquals(listOf("t-2"), dao.all.map { it.id })
            assertEquals("POST /tasks/bulk-delete", llamadas.single())
        }

    @Test
    fun borradoConjuntoTotalVaciaLocalYServidor() =
        runTest {
            val dao = FakeTaskDao(listOf(tarea("t-1").toEntity(), tarea("t-2").toEntity()))
            val repository = repositorio(dao)

            repository.deleteAllTasks()

            assertTrue(dao.all.isEmpty())
            assertEquals("DELETE /tasks", llamadas.single())
        }

    @Test
    fun alternarCompletadaCambiaLocalYAvisaAlServidor() =
        runTest {
            val dao = FakeTaskDao(listOf(tarea("t-1").toEntity()))
            val repository = repositorio(dao)

            repository.toggleCompleted("t-1")

            assertTrue(dao.byId("t-1")!!.isCompleted)
            assertFalse(dao.byId("t-1")!!.pendingSync)
            assertEquals("PATCH /tasks/t-1/toggle-completed", llamadas.single())
        }

    @Test
    fun alternarCompletadaSinRedDejaElCambioPendiente() =
        runTest {
            val dao = FakeTaskDao(listOf(tarea("t-1").toEntity()))
            val repository = repositorio(dao, fallar = true)

            repository.toggleCompleted("t-1")

            assertTrue(dao.byId("t-1")!!.isCompleted)
            assertTrue(dao.byId("t-1")!!.pendingSync)
        }

    @Test
    fun recuperarUnaTareaConcretaVieneDeLocal() =
        runTest {
            val dao = FakeTaskDao(listOf(tarea("t-1", title = "Comprar pan").toEntity()))
            val repository = repositorio(dao)

            assertEquals("Comprar pan", repository.getTask("t-1")?.title)
            assertEquals(null, repository.getTask("no-existe"))
        }

    @Test
    fun borrarSinRedDejaUnTombstoneQueSeSincronizaMasTarde() =
        runTest {
            val dao = FakeTaskDao(listOf(tarea("t-1").toEntity()))
            val pendingDeletionDao = FakePendingDeletionDao()
            val repository = repositorio(dao, pendingDeletionDao, fallar = true)

            repository.deleteTask("t-1")

            assertTrue(dao.all.isEmpty())
            assertEquals(setOf("t-1"), pendingDeletionDao.pendingIds)
        }

    @Test
    fun sincronizarReintentaLosBorradosPendientesYLimpiaElTombstone() =
        runTest {
            val dao = FakeTaskDao()
            val pendingDeletionDao = FakePendingDeletionDao(listOf("borrada-offline"))
            val repository = repositorio(dao, pendingDeletionDao)

            val resultado = repository.syncTasks()

            assertTrue(resultado.isSuccess)
            assertEquals(listOf("POST /tasks/bulk-delete", "GET /tasks"), llamadas)
            assertTrue(pendingDeletionDao.pendingIds.isEmpty())
        }

    @Test
    fun unaTareaBorradaOfflineNoReapareceSiElServidorTodaviaLaDevuelve() =
        runTest {
            // El borrado remoto de la ultima sesion fallo (el tombstone sigue ahi) y el
            // servidor, en este sync, no ha respondido todavia al bulk-delete: pullRemote
            // no debe ejecutarse porque pushPendingDeletions ya la habra retirado si tuvo
            // exito; si fallase, syncTasks() entero se para (runCatching) y no llega a bajar
            // la lista, así que la tarea jamas puede "revivir" en Room.
            val dao = FakeTaskDao()
            val pendingDeletionDao = FakePendingDeletionDao(listOf("borrada-offline"))
            val repository = repositorio(dao, pendingDeletionDao, fallar = true)

            val resultado = repository.syncTasks()

            assertTrue(resultado.isFailure)
            assertTrue(dao.all.isEmpty())
            assertEquals(setOf("borrada-offline"), pendingDeletionDao.pendingIds)
        }

    @Test
    fun borradoConjuntoSinRedDejaTombstonesDeTodasLasSeleccionadas() =
        runTest {
            val dao = FakeTaskDao(listOf(tarea("t-1").toEntity(), tarea("t-2").toEntity()))
            val pendingDeletionDao = FakePendingDeletionDao()
            val repository = repositorio(dao, pendingDeletionDao, fallar = true)

            repository.deleteTasks(listOf("t-1", "t-2"))

            assertEquals(setOf("t-1", "t-2"), pendingDeletionDao.pendingIds)
        }

    @Test
    fun borradoTotalSinRedDejaTombstonesDeTodo() =
        runTest {
            val dao = FakeTaskDao(listOf(tarea("t-1").toEntity(), tarea("t-2").toEntity()))
            val pendingDeletionDao = FakePendingDeletionDao()
            val repository = repositorio(dao, pendingDeletionDao, fallar = true)

            repository.deleteAllTasks()

            assertTrue(dao.all.isEmpty())
            assertEquals(setOf("t-1", "t-2"), pendingDeletionDao.pendingIds)
        }
}
