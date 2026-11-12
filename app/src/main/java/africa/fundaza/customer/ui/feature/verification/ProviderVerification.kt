package africa.fundaza.customer.ui.feature.verification

import africa.fundaza.customer.BuildConfig
import android.app.Activity
import com.sumsub.sns.core.SNSMobileSDK
import com.sumsub.sns.core.data.listener.SNSCompleteHandler
import com.sumsub.sns.core.data.listener.SNSErrorHandler
import com.sumsub.sns.core.data.listener.SNSStateChangedHandler
import com.sumsub.sns.core.data.listener.TokenExpirationHandler
import com.sumsub.sns.core.data.model.SNSSDKState
import java.util.Locale

/**
 * The identity provider's (Sumsub's) own capture screens: document photo and
 * selfie. The provider decides the documents; the server still decides the
 * screening, and an approval waits for both.
 */
object ProviderVerification {

    fun launch(
        activity: Activity,
        token: String,
        freshToken: () -> String?,
        onSubmitted: () -> Unit,
        onClosed: () -> Unit,
        onFailed: (String) -> Unit,
    ) {
        var submitted = false
        SNSMobileSDK.Builder(activity)
            // Runs on the SDK's background thread; the token is only ever held here and in memory.
            .withAccessToken(token, onTokenExpiration = object : TokenExpirationHandler {
                override fun onTokenExpired(): String? = freshToken()
            })
            .withStateChangedHandler(object : SNSStateChangedHandler {
                override fun onStateChanged(previousState: SNSSDKState, currentState: SNSSDKState) {
                    // Pending: everything has been sent and the provider is reviewing it.
                    if (currentState is SNSSDKState.Pending && !submitted) {
                        submitted = true
                        onSubmitted()
                    }
                }
            })
            .withCompleteHandler(object : SNSCompleteHandler {
                override fun onComplete(result: com.sumsub.sns.core.data.model.SNSCompletionResult, state: SNSSDKState) {
                    onClosed()
                }
            })
            .withErrorHandler(object : SNSErrorHandler {
                override fun onError(exception: com.sumsub.sns.core.data.model.SNSException) {
                    onFailed("Something interrupted the verification. Try again to pick up where you left off.")
                }
            })
            .withLocale(Locale.getDefault())
            .withDebug(BuildConfig.DEBUG)
            .build()
            .launch()
    }
}
