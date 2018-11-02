package io.ktor.client.tests

import io.ktor.application.*
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.*
import io.ktor.client.response.*
import io.ktor.client.tests.utils.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.engine.*
import io.ktor.server.jetty.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.selects.*
import kotlin.test.*

open class ConnectionTest(val factory: HttpClientEngineFactory<*>) : TestWithKtor() {
    private val testContent = buildString {
        append("x".repeat(100))
    }

    override val server: ApplicationEngine = embeddedServer(Jetty, serverPort) {
        routing {
            head("/emptyHead") {
                call.respond(object : OutgoingContent.NoContent() {
                    override val contentLength: Long = 150
                })
            }
            get("/ok") {
                call.respondText(testContent)
            }
            get("/hang") {
                delay(Long.MAX_VALUE)
            }
        }
    }

    @Test
    fun contentLengthWithEmptyBodyTest() = clientTest(factory) {
        test { client ->
            repeat(10) {
                val response = client.call {
                    url {
                        method = HttpMethod.Head
                        port = serverPort
                        encodedPath = "/emptyHead"
                    }
                }.response

                assert(response.status.isSuccess())
                assert(response.readBytes().isEmpty())
            }
        }
    }

    @Test
    fun closeResponseWithConnectionPipelineTest() = clientTest(factory) {
        suspend fun HttpClient.receive(): HttpClientCall = call {
            url {
                port = serverPort
                encodedPath = "/ok"
            }
        }

        test { client ->
            client.receive().close()
            assertEquals(testContent, client.receive().response.readText())
        }
    }

    @Test
    fun cancellationTest() = clientTest(factory) {

        suspend fun CoroutineScope.duringMonitoringAllThreads(block: suspend CoroutineScope.() -> Unit) {
            val failure = Channel<Throwable>()
            val predecessor = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler { _, e -> failure.offer(e) }
            try {
                select<Unit> {
                    launch { block() }.onJoin {}
                    failure.onReceive { ex ->
                        fail("A thread aborts: $ex")
                    }
                }
            } finally {
                Thread.setDefaultUncaughtExceptionHandler(predecessor)
            }
        }

        suspend fun HttpClient.hangRequest() = call {
            url {
                port = serverPort
                encodedPath = "/hang"
            }
        }

        suspend fun CoroutineScope.jobToCancel(client: HttpClient) = launch {
            try {
                client.hangRequest()
                fail("Expected exception is not thrown")
            } catch (e: Exception) {
                assert(e is CancellationException)
            }
        }

        test { client ->
            withTimeout(1000) {
                duringMonitoringAllThreads {
                    val job = jobToCancel(client)
                    delay(200)
                    job.cancelAndJoin()
                    assert(job.isCompleted)
                }
            }
        }
    }
}
