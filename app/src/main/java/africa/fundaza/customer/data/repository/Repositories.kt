package africa.fundaza.customer.data.repository

import africa.fundaza.customer.core.network.ApiResult
import africa.fundaza.customer.domain.model.Beneficiary
import africa.fundaza.customer.domain.model.BeneficiaryField
import africa.fundaza.customer.domain.model.Corridor
import africa.fundaza.customer.domain.model.CorridorMethods
import africa.fundaza.customer.domain.model.CurrentUser
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

sealed interface SignInResult {
    data class SignedIn(val user: CurrentUser) : SignInResult
    /** Valid credentials, but not a customer login — agents and staff use the web. */
    data object NotACustomer : SignInResult
}

interface AuthRepository {
    /** Restores a stored session at launch. Leaves the session signed in or signed out. */
    suspend fun restore()
    suspend fun signIn(email: String, password: String): ApiResult<SignInResult>
    suspend fun signOut()
    suspend fun refreshUser(): ApiResult<CurrentUser>
}

interface CustomerRepository {
    suspend fun customer(): ApiResult<Customer>
}

interface CorridorRepository {
    suspend fun corridors(forceRefresh: Boolean = false): ApiResult<List<Corridor>>
    suspend fun methods(corridorId: Long): ApiResult<CorridorMethods>
    /** How long the local copy is good for; zero when there is none or it has gone stale. */
    suspend fun timeUntilStale(): java.time.Duration
    /** Forget the local copy (on sign-out). */
    suspend fun clear()
}

interface BeneficiaryRepository {
    suspend fun beneficiaries(): ApiResult<List<Beneficiary>>
    suspend fun beneficiary(id: Long): ApiResult<Beneficiary>
    suspend fun fields(countryIso2: String, deliveryMethod: String): ApiResult<List<BeneficiaryField>>
    suspend fun create(new: NewBeneficiary): ApiResult<Beneficiary>
    suspend fun addWay(beneficiaryId: Long, way: NewWay): ApiResult<Beneficiary>
    suspend fun delete(id: Long): ApiResult<Unit>
}

interface TransferRepository {
    suspend fun quote(request: QuoteRequest): ApiResult<Quote>
    suspend fun create(
        quoteReference: String,
        beneficiaryId: Long,
        purpose: SendingPurpose?,
        documentIds: List<Long>,
        /** The customer's password; the server requires it when paying from the wallet. */
        password: String? = null,
    ): ApiResult<Transfer>
    suspend fun transfers(): ApiResult<List<Transfer>>
    suspend fun transfer(reference: String): ApiResult<TransferDetail>

    /** Sends an M-Pesa prompt to [phone] (else the customer's own number). Returns the number prompted. */
    suspend fun startMpesa(reference: String, phone: String?): ApiResult<String>
}

interface DocumentRepository {
    /** [contentUri] is a `content://` URI string from the system file picker. */
    suspend fun upload(type: TransferDocumentType, contentUri: String): ApiResult<TransferDocument>
}

interface WalletRepository {
    suspend fun wallet(): ApiResult<Wallet>
}

interface DeviceRepository {
    /** Tells the server this install's push token belongs to the signed-in customer. */
    suspend fun register(pushToken: String): ApiResult<Unit>
}
