package africa.fundaza.customer.core.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/** Where the phone is right now, as an ISO 3166 alpha-2 code ("KE"), or null if unknown. */
interface CountryLocator {
    suspend fun currentCountry(): String?
}

/**
 * Best guess at the sender's current country, most precise first:
 *  1. the device's last known location, reverse-geocoded (needs the location permission);
 *  2. the mobile network it is attached to (no permission; follows the phone when roaming);
 *  3. the SIM's home country.
 */
@Singleton
class DeviceCountryLocator @Inject constructor(
    @ApplicationContext private val context: Context,
) : CountryLocator {

    override suspend fun currentCountry(): String? =
        fromLocation() ?: fromTelephony()

    private fun hasLocationPermission(): Boolean =
        listOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION).any {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }

    @SuppressLint("MissingPermission") // checked in hasLocationPermission()
    private suspend fun fromLocation(): String? {
        if (!hasLocationPermission() || !Geocoder.isPresent()) return null
        val manager = context.getSystemService(LocationManager::class.java) ?: return null
        val location = runCatching {
            manager.getProviders(true)
                .mapNotNull { manager.getLastKnownLocation(it) }
                .maxByOrNull(Location::getTime)
        }.getOrNull() ?: return null
        return withTimeoutOrNull(GEOCODE_TIMEOUT_MS) { countryAt(location) }
    }

    private suspend fun countryAt(location: Location): String? {
        val geocoder = Geocoder(context, Locale.ENGLISH)
        val address: Address? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            suspendCancellableCoroutine { cont ->
                geocoder.getFromLocation(
                    location.latitude,
                    location.longitude,
                    1,
                    object : Geocoder.GeocodeListener {
                        override fun onGeocode(addresses: MutableList<Address>) { cont.resume(addresses.firstOrNull()) }
                        override fun onError(errorMessage: String?) { cont.resume(null) }
                    },
                )
            }
        } else {
            withContext(Dispatchers.IO) {
                @Suppress("DEPRECATION")
                runCatching { geocoder.getFromLocation(location.latitude, location.longitude, 1)?.firstOrNull() }.getOrNull()
            }
        }
        return address?.countryCode?.normalised()
    }

    private fun fromTelephony(): String? {
        val telephony = context.getSystemService(TelephonyManager::class.java) ?: return null
        val network = telephony.networkCountryIso.takeIf { telephony.phoneType != TelephonyManager.PHONE_TYPE_CDMA }
        return network?.normalised() ?: telephony.simCountryIso?.normalised()
    }

    private fun String.normalised(): String? = trim().uppercase(Locale.ROOT).takeIf { it.length == 2 }

    private companion object {
        const val GEOCODE_TIMEOUT_MS = 4_000L
    }
}
