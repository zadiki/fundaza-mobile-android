package africa.fundaza.customer.core.push

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class FundazaMessagingService : FirebaseMessagingService() {

    @Inject lateinit var registration: DeviceRegistration
    @Inject lateinit var notifier: Notifier

    override fun onNewToken(token: String) {
        registration.onNewToken(token)
    }

    /** Only called while the app is in the foreground; otherwise the system shows it. */
    override fun onMessageReceived(message: RemoteMessage) {
        val n = message.notification ?: return
        notifier.show(n.title, n.body)
    }
}
