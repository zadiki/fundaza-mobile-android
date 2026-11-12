package africa.fundaza.customer.ui.navigation

import kotlinx.serialization.Serializable

@Serializable data object LoginRoute
@Serializable data object RegisterRoute

// Tabs
@Serializable data object HomeRoute
@Serializable data object TransfersRoute
@Serializable data object RecipientsRoute
@Serializable data object ProfileRoute

// Pushed screens
@Serializable data object SendRoute
@Serializable data object WalletRoute
@Serializable data object VerificationRoute
@Serializable data class TransferDetailRoute(val reference: String, val justSent: Boolean = false)
@Serializable data class RecipientDetailRoute(val id: Long)

/**
 * Add a recipient, or — when [beneficiaryId] is set — add a new way for an
 * existing one to receive. Country, method and currency prefill (and lock)
 * the form when the send flow already knows them.
 */
@Serializable
data class AddRecipientRoute(
    val country: String? = null,
    val deliveryMethod: String? = null,
    val currency: String? = null,
    val beneficiaryId: Long = NO_BENEFICIARY,
) {
    companion object {
        const val NO_BENEFICIARY = -1L
    }
}

/** Key under which AddRecipient hands the saved beneficiary's id back to the caller. */
const val RESULT_BENEFICIARY_ID = "result_beneficiary_id"
