package africa.fundaza.customer.core.push

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/** Where this install's push token comes from. */
interface PushTokenSource {
    /** The current token, or null when push isn't available on this build or device. */
    suspend fun token(): String?
}

@Singleton
class FirebasePushTokenSource @Inject constructor(
    @ApplicationContext private val context: Context,
) : PushTokenSource {

    override suspend fun token(): String? {
        // A build without google-services.json never initialises Firebase; touching it would throw.
        if (FirebaseApp.getApps(context).isEmpty()) return null
        return try {
            suspendCancellableCoroutine { cont ->
                FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        cont.resume(task.result)
                    } else {
                        // No Play services, or Firebase unreachable. onNewToken will catch up later.
                        Log.w(TAG, "Couldn't get a push token", task.exception)
                        cont.resume(null)
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't get a push token", e)
            null
        }
    }

    private companion object {
        const val TAG = "Push"
    }
}
