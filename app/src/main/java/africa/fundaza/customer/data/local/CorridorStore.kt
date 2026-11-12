package africa.fundaza.customer.data.local

import africa.fundaza.customer.data.remote.dto.CorridorDto
import africa.fundaza.customer.data.remote.dto.CorridorMethodsDto
import android.content.Context
import android.util.AtomicFile
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** One pull of the routes we serve, as the server sent them. */
@Serializable
data class CorridorSnapshot(
    @SerialName("fetched_at_ms") val fetchedAtMs: Long,
    val corridors: List<CorridorDto>,
    /** Payment and delivery methods, by corridor id. */
    val methods: Map<Long, CorridorMethodsDto> = emptyMap(),
)

/** Where the last [CorridorSnapshot] survives app restarts. */
interface CorridorStore {
    suspend fun read(): CorridorSnapshot?
    suspend fun write(snapshot: CorridorSnapshot)
    suspend fun clear()
}

/** A JSON file in app-private storage, replaced atomically so a crash never leaves half a file. */
@Singleton
class FileCorridorStore @Inject constructor(
    @ApplicationContext context: Context,
    private val json: Json,
) : CorridorStore {

    private val file = AtomicFile(File(context.filesDir, "corridors.json"))

    override suspend fun read(): CorridorSnapshot? = withContext(Dispatchers.IO) {
        runCatching {
            json.decodeFromString(CorridorSnapshot.serializer(), file.readFully().decodeToString())
        }.getOrNull() // missing, or written by an older app version: just fetch again
    }

    override suspend fun write(snapshot: CorridorSnapshot) = withContext(Dispatchers.IO) {
        val out = file.startWrite()
        try {
            out.write(json.encodeToString(CorridorSnapshot.serializer(), snapshot).encodeToByteArray())
            file.finishWrite(out)
        } catch (e: Exception) {
            file.failWrite(out)
        }
    }

    override suspend fun clear() = withContext(Dispatchers.IO) { file.delete() }
}
