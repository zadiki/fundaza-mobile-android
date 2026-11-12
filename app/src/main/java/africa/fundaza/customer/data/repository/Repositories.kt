package africa.fundaza.customer.data.repository

import africa.fundaza.customer.core.network.ApiResult
import africa.fundaza.customer.core.security.SessionState
import africa.fundaza.customer.domain.model.CountryOption
import africa.fundaza.customer.domain.model.IndividualDetails
import africa.fundaza.customer.domain.model.KycState
import africa.fundaza.customer.domain.model.NewIdentityDocument
import africa.fundaza.customer.domain.model.NewSignUp
import africa.fundaza.customer.domain.model.SignUp
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
    /** A login whose sign-up is unfinished; the session now follows that sign-up. */
    data object SigningUp : SignInResult
}

/** Self-registration: a login first, then the steps the server asks for, then the customer. */
interface SignUpRepository {
    suspend fun countries(): ApiResult<List<CountryOption>>
    /** Creates the login and signs this device in; the session moves to [SessionState.SigningUp]. */
    suspend fun register(details: NewSignUp): ApiResult<Unit>
    /** Re-reads the sign-up (after the email link was followed in a browser, say) and updates the session. */
    suspend fun refresh(): ApiResult<SignUp>
    suspend fun resendEmail(): ApiResult<Unit>
    suspend fun sendPhoneCode(): ApiResult<Unit>
    suspend fun confirmPhone(code: String): ApiResult<SignUp>
    /** Writes the customer; the session moves to a signed-in customer who has not yet verified. */
    suspend fun finishAsIndividual(details: IndividualDetails): ApiResult<Unit>
}

/** The signed-in customer's own identity verification. */
interface KycRepository {
    suspend fun state(): ApiResult<KycState>
    /** A short-lived token for the provider SDK. Never stored. */
    suspend fun providerToken(): ApiResult<String>
    suspend fun uploadIdentity(document: NewIdentityDocument): ApiResult<KycState>
    suspend fun submit(): ApiResult<KycState>
    suspend fun restart(): ApiResult<KycState>
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
