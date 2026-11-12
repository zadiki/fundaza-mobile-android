package africa.fundaza.customer.data

import africa.fundaza.customer.core.network.ApiCaller
import africa.fundaza.customer.core.network.ApiResult
import africa.fundaza.customer.data.local.CorridorSnapshot
import africa.fundaza.customer.data.local.CorridorStore
import africa.fundaza.customer.data.remote.FundazaApi
import africa.fundaza.customer.data.repository.CorridorRepositoryImpl
import africa.fundaza.customer.di.NetworkModule
import africa.fundaza.customer.testing.MutableClock
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.time.Duration

/** Routes are kept on the device for three hours, then pulled again. */
class CorridorCacheTest {

    private val server = MockWebServer()
    private val json = NetworkModule.json()
    private val clock = MutableClock()
    private val store = MemoryStore()
    private var serverUp = true
    private var routeName = "Kenya"

    private class MemoryStore : CorridorStore {
        var saved: CorridorSnapshot? = null
        override suspend fun read() = saved
        override suspend fun write(snapshot: CorridorSnapshot) { saved = snapshot }
        override suspend fun clear() { saved = null }
    }

    init {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                if (!serverUp) return MockResponse().setResponseCode(503)
                return when (request.path) {
                    "/api/v1/corridors" -> MockResponse().setBody(
                        """{"data":[{"id":1,"code":"CN-KE","origin":{"iso2":"CN","name":"China"},
                           "destination":{"iso2":"KE","name":"$routeName"},
                           "send_currency_code":"CNY","receive_currency_code":"KES"}]}""",
                    )
                    "/api/v1/corridors/1/methods" -> MockResponse().setBody(
                        """{"data":{"payment_methods":[{"code":"BANK_TRANSFER","name":"Bank Transfer"}],
                           "delivery_methods":[{"code":"MOBILE_MONEY","name":"Mobile Money"}]}}""",
                    )
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        server.start()
    }

    @After
    fun tearDown() = server.shutdown()

    /** A new repository each time stands in for the app being reopened. */
    private fun repository(): CorridorRepositoryImpl {
        val api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(FundazaApi::class.java)
        return CorridorRepositoryImpl(api, ApiCaller(json), store, clock)
    }

    private suspend fun CorridorRepositoryImpl.names(): List<String> =
        (corridors() as ApiResult.Success).value.map { it.destination.name }

    @Test
    fun `one pull brings routes and their methods, then the device copy is used`() = runTest {
        val repo = repository()
        repo.corridors()
        assertEquals(2, server.requestCount) // routes + methods for the one route

        repo.corridors()
        repo.methods(1)
        repository().corridors() // app reopened
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `after three hours the routes are pulled again`() = runTest {
        repository().corridors()
        routeName = "Kenya (renamed)"

        clock.now = clock.now.plus(Duration.ofHours(3).minusMinutes(1))
        assertEquals(listOf("Kenya"), repository().names())

        clock.now = clock.now.plus(Duration.ofMinutes(2))
        assertEquals(listOf("Kenya (renamed)"), repository().names())
        assertEquals(Duration.ofHours(3), repository().timeUntilStale())
    }

    @Test
    fun `a stale copy still works when the server can't be reached`() = runTest {
        repository().corridors()
        clock.now = clock.now.plus(Duration.ofHours(5))
        serverUp = false

        assertEquals(listOf("Kenya"), repository().names())
        assertTrue(repository().methods(1) is ApiResult.Success)
    }

    @Test
    fun `signing out forgets the routes`() = runTest {
        val repo = repository()
        repo.corridors()
        repo.clear()

        assertNull(store.saved)
        assertEquals(Duration.ZERO, repo.timeUntilStale())
    }
}
