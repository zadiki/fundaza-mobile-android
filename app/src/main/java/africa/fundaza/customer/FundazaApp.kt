package africa.fundaza.customer

import africa.fundaza.customer.core.push.DeviceRegistration
import africa.fundaza.customer.core.push.Notifier
import africa.fundaza.customer.data.repository.CorridorSync
import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class FundazaApp : Application() {

    @Inject lateinit var notifier: Notifier
    @Inject lateinit var deviceRegistration: DeviceRegistration
    @Inject lateinit var corridorSync: CorridorSync

    override fun onCreate() {
        super.onCreate()
        notifier.createChannels()
        deviceRegistration.start()
        corridorSync.start()
    }
}
