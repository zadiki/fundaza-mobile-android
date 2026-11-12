package africa.fundaza.customer.di

import africa.fundaza.customer.core.location.CountryLocator
import africa.fundaza.customer.data.local.CorridorStore
import africa.fundaza.customer.data.local.FileCorridorStore
import africa.fundaza.customer.core.location.DeviceCountryLocator
import africa.fundaza.customer.BuildConfig
import africa.fundaza.customer.core.network.ApiCaller
import africa.fundaza.customer.core.network.AuthInterceptor
import africa.fundaza.customer.core.push.FirebasePushTokenSource
import africa.fundaza.customer.core.push.PushTokenSource
import africa.fundaza.customer.core.security.KeystoreTokenStore
import africa.fundaza.customer.core.security.TokenStore
import africa.fundaza.customer.core.util.ApplicationScope
import africa.fundaza.customer.data.remote.FundazaApi
import africa.fundaza.customer.data.repository.AuthRepository
import africa.fundaza.customer.data.repository.AuthRepositoryImpl
import africa.fundaza.customer.data.repository.BeneficiaryRepository
import africa.fundaza.customer.data.repository.BeneficiaryRepositoryImpl
import africa.fundaza.customer.data.repository.CorridorRepository
import africa.fundaza.customer.data.repository.CorridorRepositoryImpl
import africa.fundaza.customer.data.repository.CustomerRepository
import africa.fundaza.customer.data.repository.CustomerRepositoryImpl
import africa.fundaza.customer.data.repository.DeviceRepository
import africa.fundaza.customer.data.repository.DeviceRepositoryImpl
import africa.fundaza.customer.data.repository.DocumentRepository
import africa.fundaza.customer.data.repository.DocumentRepositoryImpl
import africa.fundaza.customer.data.repository.TransferRepository
import africa.fundaza.customer.data.repository.TransferRepositoryImpl
import africa.fundaza.customer.data.repository.WalletRepository
import africa.fundaza.customer.data.repository.WalletRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun json(): Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
    }

    @Provides
    @Singleton
    fun apiCaller(json: Json): ApiCaller = ApiCaller(json)

    @Provides
    @Singleton
    fun okHttp(auth: AuthInterceptor): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(auth)
        .apply {
            if (BuildConfig.DEBUG) {
                addInterceptor(
                    HttpLoggingInterceptor().apply {
                        level = HttpLoggingInterceptor.Level.BODY
                        redactHeader("Authorization")
                    },
                )
            }
        }
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    @Provides
    @Singleton
    fun api(client: OkHttpClient, json: Json): FundazaApi = Retrofit.Builder()
        .baseUrl(BuildConfig.API_BASE_URL)
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(FundazaApi::class.java)

    @Provides
    @Singleton
    @ApplicationScope
    fun applicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds abstract fun tokenStore(impl: KeystoreTokenStore): TokenStore
    @Binds abstract fun auth(impl: AuthRepositoryImpl): AuthRepository
    @Binds abstract fun customer(impl: CustomerRepositoryImpl): CustomerRepository
    @Binds abstract fun corridors(impl: CorridorRepositoryImpl): CorridorRepository
    @Binds abstract fun beneficiaries(impl: BeneficiaryRepositoryImpl): BeneficiaryRepository
    @Binds abstract fun transfers(impl: TransferRepositoryImpl): TransferRepository
    @Binds abstract fun documents(impl: DocumentRepositoryImpl): DocumentRepository
    @Binds abstract fun wallet(impl: WalletRepositoryImpl): WalletRepository
    @Binds abstract fun devices(impl: DeviceRepositoryImpl): DeviceRepository
    @Binds abstract fun pushTokens(impl: FirebasePushTokenSource): PushTokenSource
    @Binds abstract fun countryLocator(impl: DeviceCountryLocator): CountryLocator
    @Binds abstract fun corridorStore(impl: FileCorridorStore): CorridorStore
}
