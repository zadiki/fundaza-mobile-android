package africa.fundaza.customer.data.repository

import africa.fundaza.customer.core.security.SessionManager
import africa.fundaza.customer.core.security.SessionState
import africa.fundaza.customer.core.util.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps the on-device copy of the routes current, so the send and add-recipient screens
 * open instantly:
 *  - a fresh sign-in pulls them straight away;
 *  - while signed in they are pulled again whenever the copy turns [CorridorRepositoryImpl.CORRIDOR_TTL] old;
 *  - signing out forgets them.
 * Screens that ask while the copy is stale (say the app slept through the refresh) pull then.
 */
@Singleton
class CorridorSync @Inject constructor(
    private val session: SessionManager,
    private val corridors: CorridorRepository,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private sealed interface Who {
        data class Customer(val id: Long) : Who
        data object Nobody : Who
    }

    fun start() {
        scope.launch {
            var previous: Who? = null
            session.state
                .mapNotNull {
                    when (it) {
                        is SessionState.SignedIn -> Who.Customer(it.user.id)
                        is SessionState.SignedOut -> Who.Nobody
                        // Restoring / offline at launch: keep whatever copy we have.
                        else -> null
                    }
                }
                .distinctUntilChanged()
                .collectLatest { who ->
                    val before = previous
                    previous = who
                    when (who) {
                        Who.Nobody -> corridors.clear()
                        is Who.Customer -> {
                            // Someone just signed in (rather than the app reopening on a saved session).
                            if (before != null) corridors.corridors(forceRefresh = true)
                            keepFresh()
                        }
                    }
                }
        }
    }

    /** Runs until sign-out cancels it (collectLatest). */
    private suspend fun keepFresh(): Nothing {
        while (true) {
            corridors.corridors() // pulls only if the copy is missing or stale
            delay(maxOf(corridors.timeUntilStale(), RETRY_AFTER).toMillis())
        }
    }

    private companion object {
        /** After a failed pull, wait this long before trying again. */
        val RETRY_AFTER: Duration = Duration.ofMinutes(5)
    }
}
