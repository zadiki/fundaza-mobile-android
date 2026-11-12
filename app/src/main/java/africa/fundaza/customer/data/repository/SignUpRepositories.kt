package africa.fundaza.customer.data.repository

import africa.fundaza.customer.core.network.ApiCaller
import africa.fundaza.customer.core.network.ApiError
import africa.fundaza.customer.core.network.ApiResult
import africa.fundaza.customer.core.security.SessionManager
import africa.fundaza.customer.core.security.StoredToken
import africa.fundaza.customer.data.remote.FundazaApi
import africa.fundaza.customer.data.remote.dto.IndividualDetailsRequest
import africa.fundaza.customer.data.remote.dto.PhoneCodeRequest
import africa.fundaza.customer.data.remote.dto.SignUpRequest
import africa.fundaza.customer.data.remote.parseInstant
import africa.fundaza.customer.data.remote.toDomain
import africa.fundaza.customer.domain.model.CountryOption
import africa.fundaza.customer.domain.model.IndividualDetails
import africa.fundaza.customer.domain.model.KycState
import africa.fundaza.customer.domain.model.NewIdentityDocument
import africa.fundaza.customer.domain.model.NewSignUp
import africa.fundaza.customer.domain.model.SignUp
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.HttpException
import retrofit2.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Self-registration over the API. Every rule (who may sign up, what must be
 * confirmed, and in what order) is the server's; this only carries the answers
 * and keeps the session in step with them.
 */
@Singleton
class SignUpRepositoryImpl @Inject constructor(
    private val api: FundazaApi,
    private val call: ApiCaller,
    private val session: SessionManager,
    private val auth: AuthRepositoryImpl,
) : SignUpRepository {

    override suspend fun countries(): ApiResult<List<CountryOption>> =
        call { api.countries().data.map { it.toDomain() } }

    override suspend fun register(details: NewSignUp): ApiResult<Unit> {
        val result = call {
            api.signUp(
                SignUpRequest(
                    fullName = details.fullName.trim(),
                    email = details.email.trim(),
                    phone = details.phone.filterNot { it.isWhitespace() },
                    residenceIso2 = details.residenceIso2,
                    password = details.password,
                    passwordConfirmation = details.passwordConfirmation,
                    deviceName = auth.deviceName(),
                ),
            ).data
        }
        if (result is ApiResult.Failure) return result
        val signedUp = (result as ApiResult.Success).value
        session.beginWith(StoredToken(signedUp.token, parseInstant(signedUp.expiresAt)))
        session.signingUp(signedUp.signUp.toDomain())
        return ApiResult.Success(Unit)
    }

    override suspend fun refresh(): ApiResult<SignUp> = follow(call { api.currentSignUp().data.toDomain() })

    override suspend fun resendEmail(): ApiResult<Unit> = call { api.resendSignUpEmail().orThrow() }

    override suspend fun sendPhoneCode(): ApiResult<Unit> = call { api.sendSignUpPhoneCode().orThrow() }

    override suspend fun confirmPhone(code: String): ApiResult<SignUp> =
        follow(call { api.confirmSignUpPhone(PhoneCodeRequest(code.trim())).data.toDomain() })

    override suspend fun finishAsIndividual(details: IndividualDetails): ApiResult<Unit> {
        val created = call {
            api.finishSignUpAsIndividual(
                IndividualDetailsRequest(
                    firstName = details.firstName.trim(),
                    lastName = details.lastName.trim(),
                    dateOfBirth = details.dateOfBirth.toString(),
                    nationalityIso2 = details.nationalityIso2,
                    addressLine1 = details.addressLine1?.trim()?.ifEmpty { null },
                    addressCity = details.addressCity?.trim()?.ifEmpty { null },
                ),
            )
        }
        if (created is ApiResult.Failure) return created

        // The same login now acts for the new customer; ask who it is so permissions are the server's.
        return when (val me = call { api.me().data.toDomain() }) {
            is ApiResult.Success -> {
                session.signedIn(me.value, justJoined = true)
                ApiResult.Success(Unit)
            }
            is ApiResult.Failure -> me
        }
    }

    /** Keeps the session's copy of the sign-up current; a 404 means it finished elsewhere. */
    private suspend fun follow(result: ApiResult<SignUp>): ApiResult<SignUp> {
        when (result) {
            is ApiResult.Success -> session.signingUp(result.value)
            is ApiResult.Failure -> if (result.error is ApiError.NotFound) auth.restore()
        }
        return result
    }
}

@Singleton
class KycRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val api: FundazaApi,
    private val call: ApiCaller,
) : KycRepository {

    override suspend fun state(): ApiResult<KycState> = call { api.kyc().data.toDomain() }

    override suspend fun providerToken(): ApiResult<String> = call { api.kycSession().data.token }

    override suspend fun uploadIdentity(document: NewIdentityDocument): ApiResult<KycState> {
        val uri = Uri.parse(document.contentUri)
        val resolver = context.contentResolver
        val (name, size) = withContext(Dispatchers.IO) {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
                ?.use { c -> if (c.moveToFirst()) (c.getString(0) ?: "identity") to c.getLong(1) else null }
        } ?: ("identity" to -1L)
        if (size > MAX_BYTES) {
            return ApiResult.Failure(ApiError.Validation("That file is larger than 5 MB. Choose a smaller photo or scan."))
        }
        val bytes = withContext(Dispatchers.IO) { resolver.openInputStream(uri)?.use { it.readBytes() } }
            ?: return ApiResult.Failure(ApiError.Validation("We couldn't open that file."))
        val mime = resolver.getType(uri)?.toMediaTypeOrNull()
        val text = "text/plain".toMediaTypeOrNull()

        return call {
            api.uploadIdentity(
                documentType = document.typeCode.toRequestBody(text),
                documentNumber = document.number.trim().toRequestBody(text),
                issuingCountryIso2 = document.issuingCountryIso2.toRequestBody(text),
                expiresAt = document.expiresAt.toString().toRequestBody(text),
                file = MultipartBody.Part.createFormData("file", name, bytes.toRequestBody(mime)),
            ).data.toDomain()
        }
    }

    override suspend fun submit(): ApiResult<KycState> = call { api.submitKyc().data.toDomain() }

    override suspend fun restart(): ApiResult<KycState> = call { api.restartKyc().data.toDomain() }

    private companion object {
        /** The server's limit for an identity scan. */
        const val MAX_BYTES = 5L * 1024 * 1024
    }
}

private fun Response<Unit>.orThrow() {
    if (!isSuccessful) throw HttpException(this)
}
