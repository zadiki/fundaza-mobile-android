package africa.fundaza.customer.data.repository

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import africa.fundaza.customer.core.network.ApiCaller
import africa.fundaza.customer.core.network.ApiError
import africa.fundaza.customer.core.network.ApiResult
import africa.fundaza.customer.data.remote.FundazaApi
import africa.fundaza.customer.data.remote.dto.CreateBeneficiaryDto
import africa.fundaza.customer.data.remote.dto.CreateTransactionDto
import africa.fundaza.customer.data.remote.dto.DeviceRequest
import africa.fundaza.customer.data.remote.dto.QuoteRequestDto
import africa.fundaza.customer.data.remote.dto.SaveInstrumentDto
import africa.fundaza.customer.data.remote.toDetail
import africa.fundaza.customer.data.remote.toDomain
import africa.fundaza.customer.data.remote.toTransfer
import africa.fundaza.customer.domain.model.Beneficiary
import africa.fundaza.customer.domain.model.BeneficiaryField
import africa.fundaza.customer.domain.model.Corridor
import africa.fundaza.customer.domain.model.CorridorMethods
import africa.fundaza.customer.domain.model.Customer
import africa.fundaza.customer.domain.model.NewBeneficiary
import africa.fundaza.customer.domain.model.NewWay
import africa.fundaza.customer.domain.model.Quote
import africa.fundaza.customer.domain.model.QuoteRequest
import africa.fundaza.customer.domain.model.SendingPurpose
import africa.fundaza.customer.domain.model.Transfer
import africa.fundaza.customer.domain.model.TransferDetail
import africa.fundaza.customer.domain.model.TransferDocument
import africa.fundaza.customer.domain.model.TransferDocumentType
import africa.fundaza.customer.domain.model.Wallet
import africa.fundaza.customer.data.local.CorridorSnapshot
import africa.fundaza.customer.data.local.CorridorStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.time.Clock
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CustomerRepositoryImpl @Inject constructor(
    private val api: FundazaApi,
    private val call: ApiCaller,
) : CustomerRepository {
    override suspend fun customer(): ApiResult<Customer> = call { api.customer().data.toDomain() }
}

/**
 * Routes change rarely, so they are kept on the device: pulled at sign-in, served from the
 * local copy for [CORRIDOR_TTL], then pulled again. If a pull fails (offline, server down)
 * the last copy keeps the screens working rather than blocking the customer.
 */
@Singleton
class CorridorRepositoryImpl @Inject constructor(
    private val api: FundazaApi,
    private val call: ApiCaller,
    private val store: CorridorStore,
    private val clock: Clock,
) : CorridorRepository {
    private val mutex = Mutex()
    private var snapshot: CorridorSnapshot? = null
    private var loadedFromDisk = false

    override suspend fun corridors(forceRefresh: Boolean): ApiResult<List<Corridor>> = mutex.withLock {
        val cached = current()
        if (!forceRefresh && cached != null && isFresh(cached)) return ApiResult.Success(cached.domainCorridors())
        when (val pulled = pull()) {
            is ApiResult.Success -> ApiResult.Success(pulled.value.domainCorridors())
            is ApiResult.Failure -> cached?.let { ApiResult.Success(it.domainCorridors()) } ?: pulled
        }
    }

    override suspend fun methods(corridorId: Long): ApiResult<CorridorMethods> {
        mutex.withLock { current()?.methods?.get(corridorId) }?.let { return ApiResult.Success(it.toDomain()) }
        val result = call { api.corridorMethods(corridorId).data }
        if (result is ApiResult.Success) {
            mutex.withLock {
                current()?.let { save(it.copy(methods = it.methods + (corridorId to result.value))) }
            }
        }
        return when (result) {
            is ApiResult.Success -> ApiResult.Success(result.value.toDomain())
            is ApiResult.Failure -> result
        }
    }

    override suspend fun timeUntilStale(): Duration = mutex.withLock {
        val cached = current() ?: return Duration.ZERO
        val age = Duration.ofMillis(clock.millis() - cached.fetchedAtMs)
        CORRIDOR_TTL.minus(age).takeUnless { it.isNegative } ?: Duration.ZERO
    }

    override suspend fun clear() = mutex.withLock {
        snapshot = null
        loadedFromDisk = true
        store.clear()
    }

    /** Pulls every route and, alongside, each route's methods, so no screen waits on them later. */
    private suspend fun pull(): ApiResult<CorridorSnapshot> {
        val corridors = call { api.corridors().data }
        if (corridors is ApiResult.Failure) return corridors
        val list = (corridors as ApiResult.Success).value
        val methods = coroutineScope {
            list.map { c -> async { c.id to call { api.corridorMethods(c.id).data } } }.awaitAll()
        }.mapNotNull { (id, r) -> (r as? ApiResult.Success)?.value?.let { id to it } }.toMap()
        val fresh = CorridorSnapshot(clock.millis(), list, methods)
        save(fresh)
        return ApiResult.Success(fresh)
    }

    private suspend fun current(): CorridorSnapshot? {
        if (!loadedFromDisk) {
            snapshot = store.read()
            loadedFromDisk = true
        }
        return snapshot
    }

    private suspend fun save(value: CorridorSnapshot) {
        snapshot = value
        store.write(value)
    }

    private fun isFresh(s: CorridorSnapshot): Boolean =
        clock.millis() - s.fetchedAtMs in 0 until CORRIDOR_TTL.toMillis()

    private fun CorridorSnapshot.domainCorridors() = corridors.map { it.toDomain() }

    companion object {
        val CORRIDOR_TTL: Duration = Duration.ofHours(3)
    }
}

@Singleton
class BeneficiaryRepositoryImpl @Inject constructor(
    private val api: FundazaApi,
    private val call: ApiCaller,
) : BeneficiaryRepository {

    override suspend fun beneficiaries(): ApiResult<List<Beneficiary>> =
        call { api.beneficiaries().data.map { it.toDomain() } }

    override suspend fun beneficiary(id: Long): ApiResult<Beneficiary> =
        call { api.beneficiary(id).data.toDomain() }

    override suspend fun fields(countryIso2: String, deliveryMethod: String): ApiResult<List<BeneficiaryField>> =
        call { api.beneficiaryFields(countryIso2, deliveryMethod).data.map { it.toDomain() } }

    override suspend fun create(new: NewBeneficiary): ApiResult<Beneficiary> = call {
        api.createBeneficiary(
            CreateBeneficiaryDto(
                fullName = new.fullName.trim(),
                type = new.type.code,
                relationship = new.relationship?.name,
                countryIso2 = new.countryIso2,
                currencyCode = new.currency,
                deliveryMethodCode = new.deliveryMethod,
                fields = new.fields.takeIf { new.deliveryMethod != null }?.mapValues { it.value.trim() },
            ),
        ).data.toDomain()
    }

    override suspend fun addWay(beneficiaryId: Long, way: NewWay): ApiResult<Beneficiary> = call {
        api.saveInstrument(
            beneficiaryId,
            SaveInstrumentDto(way.deliveryMethod, way.countryIso2, way.currency, way.fields.mapValues { it.value.trim() }),
        ).data.toDomain()
    }

    override suspend fun delete(id: Long): ApiResult<Unit> = call {
        val response = api.deleteBeneficiary(id)
        if (!response.isSuccessful) throw retrofit2.HttpException(response)
    }
}

@Singleton
class TransferRepositoryImpl @Inject constructor(
    private val api: FundazaApi,
    private val call: ApiCaller,
) : TransferRepository {

    override suspend fun quote(request: QuoteRequest): ApiResult<Quote> = call {
        api.quote(
            QuoteRequestDto(
                sendCountryIso2 = request.sendCountry,
                receiveCountryIso2 = request.receiveCountry,
                sendCurrencyCode = request.sendCurrency,
                receiveCurrencyCode = request.receiveCurrency,
                sendAmount = request.sendAmount.stripTrailingZeros().toPlainString(),
                deliveryMethodCode = request.deliveryMethod,
                paymentMethodCode = request.paymentMethod,
                fastArrival = request.fastArrival,
            ),
        ).data.toDomain()
    }

    override suspend fun create(
        quoteReference: String,
        beneficiaryId: Long,
        purpose: SendingPurpose?,
        documentIds: List<Long>,
    ): ApiResult<Transfer> = call {
        api.createTransaction(
            CreateTransactionDto(quoteReference, beneficiaryId, purpose?.name, documentIds.ifEmpty { null }),
        ).data.toTransfer()
    }

    override suspend fun transfers(): ApiResult<List<Transfer>> =
        call { api.transactions().data.map { it.toTransfer() } }

    override suspend fun transfer(reference: String): ApiResult<TransferDetail> =
        call { api.transaction(reference).data.toDetail() }
}

@Singleton
class DocumentRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val api: FundazaApi,
    private val call: ApiCaller,
) : DocumentRepository {

    override suspend fun upload(type: TransferDocumentType, contentUri: String): ApiResult<TransferDocument> {
        val uri = Uri.parse(contentUri)
        val resolver = context.contentResolver
        val (name, size) = withContext(Dispatchers.IO) {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
                ?.use { c ->
                    if (c.moveToFirst()) c.getString(0) to c.getLong(1) else null
                }
        } ?: ("document" to -1L)
        if (size > MAX_BYTES) {
            return ApiResult.Failure(ApiError.Validation("That file is larger than 10 MB. Choose a smaller one."))
        }
        val bytes = withContext(Dispatchers.IO) {
            resolver.openInputStream(uri)?.use { it.readBytes() }
        } ?: return ApiResult.Failure(ApiError.Validation("We couldn't open that file."))
        val mime = resolver.getType(uri)?.toMediaTypeOrNull()

        return call {
            api.uploadDocument(
                documentTypeCode = type.name.toRequestBody("text/plain".toMediaTypeOrNull()),
                file = MultipartBody.Part.createFormData("file", name, bytes.toRequestBody(mime)),
            ).data.toDomain()
        }
    }

    private companion object {
        const val MAX_BYTES = 10L * 1024 * 1024
    }
}

@Singleton
class WalletRepositoryImpl @Inject constructor(
    private val api: FundazaApi,
    private val call: ApiCaller,
) : WalletRepository {
    override suspend fun wallet(): ApiResult<Wallet> = call { api.wallet().data.toDomain() }
}

class DeviceRepositoryImpl @Inject constructor(
    private val api: FundazaApi,
    private val call: ApiCaller,
) : DeviceRepository {
    override suspend fun register(pushToken: String): ApiResult<Unit> = call {
        val response = api.registerDevice(DeviceRequest(pushToken))
        if (!response.isSuccessful) throw retrofit2.HttpException(response)
    }
}
