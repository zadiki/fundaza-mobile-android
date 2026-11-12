package africa.fundaza.customer.data

import africa.fundaza.customer.core.network.ApiCaller
import africa.fundaza.customer.core.network.ApiError
import africa.fundaza.customer.core.network.ApiResult
import africa.fundaza.customer.data.remote.FundazaApi
import africa.fundaza.customer.data.repository.TransferRepositoryImpl
import africa.fundaza.customer.di.NetworkModule
import africa.fundaza.customer.domain.model.QuoteRequest
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.math.BigDecimal

/** The wire contract with fundaza-server's /api/v1, against a local fake server. */
class ApiContractTest {

    private val server = MockWebServer()
    private val json: Json = NetworkModule.json()
    private lateinit var repository: TransferRepositoryImpl

    @Before
    fun setUp() {
        server.start()
        val api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(FundazaApi::class.java)
        repository = TransferRepositoryImpl(api, ApiCaller(json))
    }

    @After
    fun tearDown() = server.shutdown()

    private val request = QuoteRequest("CN", "KE", "CNY", "KES", BigDecimal("25000.50"), "MOBILE_MONEY", "BANK_TRANSFER", false)

    @Test
    fun `sends the amount as a decimal string and reads amounts back exactly`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """
                {"data":{"reference":"QT-1","corridor_id":3,
                 "send_amount":{"amount":"25000.5000","currency":"CNY"},
                 "receive_amount":{"amount":"449790.0000","currency":"KES"},
                 "total_payable":{"amount":"25100.5000","currency":"CNY"},
                 "source_rate":"18.0000000000","markup_rate":"0.010000","customer_rate":"17.8200000000",
                 "charges":[{"type":"PROCESSING_FEE","label":"Processing Fee","amount":{"amount":"100.0000","currency":"CNY"}}],
                 "fast_arrival":{"chosen":false,"price":null},
                 "expires_at":"2026-11-11T10:15:00+00:00","some_future_field":true}}
                """.trimIndent(),
            ),
        )

        val result = repository.quote(request)

        val body = json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject
        assertTrue("send_amount must be a JSON string", body.getValue("send_amount").jsonPrimitive.isString)
        assertEquals("25000.5", body.getValue("send_amount").jsonPrimitive.content)

        val quote = (result as ApiResult.Success).value
        assertEquals(BigDecimal("449790.0000"), quote.receiveAmount.amount)
        assertEquals(BigDecimal("17.8200000000"), quote.customerRate)
        assertEquals("Processing Fee", quote.charges.single().label)
    }

    @Test
    fun `shows a refusal's message exactly as the server wrote it`() = runTest {
        val sentence = "Sending to this country needs a verified identity. Visit an agent to verify."
        server.enqueue(MockResponse().setResponseCode(422).setBody("""{"message":"$sentence","errors":{"send_amount":["Too much."]}}"""))

        val error = (repository.quote(request) as ApiResult.Failure).error

        assertTrue(error is ApiError.Validation)
        assertEquals(sentence, error.message)
        assertEquals("Too much.", (error as ApiError.Validation).firstErrorFor("send_amount"))
    }

    @Test
    fun `maps auth, permission and server failures`() = runTest {
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"message":"Unauthenticated."}"""))
        assertTrue((repository.transfers() as ApiResult.Failure).error is ApiError.Unauthenticated)

        server.enqueue(MockResponse().setResponseCode(403).setBody("""{"message":"Customers can't do that."}"""))
        assertEquals("Customers can't do that.", (repository.transfers() as ApiResult.Failure).error.message)

        server.enqueue(MockResponse().setResponseCode(500).setBody("<html>oops</html>"))
        assertTrue((repository.transfers() as ApiResult.Failure).error is ApiError.Server)
    }
}
