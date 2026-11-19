package africa.fundaza.customer.data.remote

import africa.fundaza.customer.data.remote.dto.BeneficiaryDto
import africa.fundaza.customer.data.remote.dto.BeneficiaryFieldDto
import africa.fundaza.customer.data.remote.dto.CorridorDto
import africa.fundaza.customer.data.remote.dto.CorridorMethodsDto
import africa.fundaza.customer.data.remote.dto.CreateBeneficiaryDto
import africa.fundaza.customer.data.remote.dto.CreateTransactionDto
import africa.fundaza.customer.data.remote.dto.CurrentUserDto
import africa.fundaza.customer.data.remote.dto.CustomerDto
import africa.fundaza.customer.data.remote.dto.DeviceRequest
import africa.fundaza.customer.data.remote.dto.Envelope
import africa.fundaza.customer.data.remote.dto.GoodsRequirementDto
import africa.fundaza.customer.data.remote.dto.InvoiceGoodsDto
import africa.fundaza.customer.data.remote.dto.MpesaRequest
import africa.fundaza.customer.data.remote.dto.MpesaSessionDto
import africa.fundaza.customer.data.remote.dto.QuoteDto
import africa.fundaza.customer.data.remote.dto.QuoteRequestDto
import africa.fundaza.customer.data.remote.dto.SaveInstrumentDto
import africa.fundaza.customer.data.remote.dto.TokenDto
import africa.fundaza.customer.data.remote.dto.TokenRequest
import africa.fundaza.customer.data.remote.dto.TransactionDto
import africa.fundaza.customer.data.remote.dto.TransferDocumentDto
import africa.fundaza.customer.data.remote.dto.WalletDto
import africa.fundaza.customer.data.remote.dto.CountryOptionDto
import africa.fundaza.customer.data.remote.dto.IndividualDetailsRequest
import africa.fundaza.customer.data.remote.dto.KycSessionDto
import africa.fundaza.customer.data.remote.dto.KycStateDto
import africa.fundaza.customer.data.remote.dto.PhoneCodeRequest
import africa.fundaza.customer.data.remote.dto.SignUpDto
import africa.fundaza.customer.data.remote.dto.SignUpRequest
import africa.fundaza.customer.data.remote.dto.SignedUpDto
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

/** The Fundaza mobile API, v1. See fundaza-server docs/mobile-api.md and `/api/documentation`. */
interface FundazaApi {

    @POST("api/v1/auth/tokens")
    suspend fun issueToken(@Body body: TokenRequest): Envelope<TokenDto>

    @DELETE("api/v1/auth/tokens/current")
    suspend fun revokeCurrentToken(): Response<Unit>

    @GET("api/v1/me")
    suspend fun me(): Envelope<CurrentUserDto>

    @GET("api/v1/customers/me")
    suspend fun customer(): Envelope<CustomerDto>

    @GET("api/v1/corridors")
    suspend fun corridors(): Envelope<List<CorridorDto>>

    @GET("api/v1/corridors/{corridor}/methods")
    suspend fun corridorMethods(@Path("corridor") corridorId: Long): Envelope<CorridorMethodsDto>

    @GET("api/v1/beneficiaries/fields")
    suspend fun beneficiaryFields(
        @Query("country_iso2") countryIso2: String,
        @Query("delivery_method_code") deliveryMethodCode: String,
    ): Envelope<List<BeneficiaryFieldDto>>

    @GET("api/v1/beneficiaries")
    suspend fun beneficiaries(): Envelope<List<BeneficiaryDto>>

    @GET("api/v1/beneficiaries/{id}")
    suspend fun beneficiary(@Path("id") id: Long): Envelope<BeneficiaryDto>

    @POST("api/v1/beneficiaries")
    suspend fun createBeneficiary(@Body body: CreateBeneficiaryDto): Envelope<BeneficiaryDto>

    @POST("api/v1/beneficiaries/{id}/instruments")
    suspend fun saveInstrument(@Path("id") id: Long, @Body body: SaveInstrumentDto): Envelope<BeneficiaryDto>

    @DELETE("api/v1/beneficiaries/{id}")
    suspend fun deleteBeneficiary(@Path("id") id: Long): Response<Unit>

    @POST("api/v1/quotes")
    suspend fun quote(@Body body: QuoteRequestDto): Envelope<QuoteDto>

    /** Whether this send must list the goods it pays for: a payment for goods to a company in China. */
    @GET("api/v1/quotes/{reference}/goods")
    suspend fun goodsRequirement(
        @Path("reference") reference: String,
        @Query("beneficiary_id") beneficiaryId: Long,
    ): Envelope<GoodsRequirementDto>

    @GET("api/v1/transactions")
    suspend fun transactions(): Envelope<List<TransactionDto>>

    @GET("api/v1/transactions/{reference}")
    suspend fun transaction(@Path("reference") reference: String): Envelope<TransactionDto>

    @POST("api/v1/transactions")
    suspend fun createTransaction(@Body body: CreateTransactionDto): Envelope<TransactionDto>

    /** Asks Safaricom to show an M-Pesa prompt on the customer's phone for this transfer. */
    @POST("api/v1/transactions/{reference}/mpesa")
    suspend fun startMpesa(@Path("reference") reference: String, @Body body: MpesaRequest): Envelope<MpesaSessionDto>

    @Multipart
    @POST("api/v1/documents")
    suspend fun uploadDocument(
        @Part("document_type_code") documentTypeCode: RequestBody,
        @Part file: MultipartBody.Part,
    ): Envelope<TransferDocumentDto>

    /** What an uploaded PDF invoice seems to bill, read on the server, to pre-fill the goods. */
    @GET("api/v1/documents/{id}/goods")
    suspend fun invoiceGoods(@Path("id") id: Long): Envelope<InvoiceGoodsDto>

    @GET("api/v1/wallet")
    suspend fun wallet(): Envelope<WalletDto>

    @POST("api/v1/devices")
    suspend fun registerDevice(@Body body: DeviceRequest): Response<Unit>

    // --- sign-up: a login with no customer yet -------------------------------

    @GET("api/v1/countries")
    suspend fun countries(): Envelope<List<CountryOptionDto>>

    @POST("api/v1/sign-ups")
    suspend fun signUp(@Body body: SignUpRequest): Envelope<SignedUpDto>

    @GET("api/v1/sign-ups/current")
    suspend fun currentSignUp(): Envelope<SignUpDto>

    @POST("api/v1/sign-ups/current/email")
    suspend fun resendSignUpEmail(): Response<Unit>

    @POST("api/v1/sign-ups/current/phone/code")
    suspend fun sendSignUpPhoneCode(): Response<Unit>

    @POST("api/v1/sign-ups/current/phone")
    suspend fun confirmSignUpPhone(@Body body: PhoneCodeRequest): Envelope<SignUpDto>

    @POST("api/v1/sign-ups/current/individual")
    suspend fun finishSignUpAsIndividual(@Body body: IndividualDetailsRequest): Envelope<CustomerDto>

    // --- verification ----------------------------------------------------------

    @GET("api/v1/customers/me/kyc")
    suspend fun kyc(): Envelope<KycStateDto>

    @POST("api/v1/customers/me/kyc/session")
    suspend fun kycSession(): Envelope<KycSessionDto>

    @Multipart
    @POST("api/v1/customers/me/kyc/documents")
    suspend fun uploadIdentity(
        @Part("document_type") documentType: RequestBody,
        @Part("document_number") documentNumber: RequestBody,
        @Part("issuing_country_iso2") issuingCountryIso2: RequestBody,
        @Part("expires_at") expiresAt: RequestBody,
        @Part file: MultipartBody.Part,
    ): Envelope<KycStateDto>

    @POST("api/v1/customers/me/kyc/submit")
    suspend fun submitKyc(): Envelope<KycStateDto>

    @POST("api/v1/customers/me/kyc/restart")
    suspend fun restartKyc(): Envelope<KycStateDto>
}
