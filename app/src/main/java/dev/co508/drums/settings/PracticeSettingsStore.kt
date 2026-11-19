package dev.co508.drums.settings

import android.content.Context
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.Serializer
import androidx.datastore.dataStoreFile
import androidx.datastore.migrations.SharedPreferencesMigration
import dev.co508.drums.lesson.Lesson
import dev.co508.drums.ui.practice.PracticeView
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/** One application-owned store. Accepted edits survive leaving the screen immediately. */
class PracticeSettingsStore(
    context: Context,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {
    private val store =
        DataStoreFactory.create(
            serializer = PracticePreferencesSerializer,
            migrations =
                listOf(
                    SharedPreferencesMigration(context, "practice", setOf("view")) { old, current ->
                        current.copy(view = old.getString("view", current.view) ?: current.view)
                    },
                ),
            scope = scope,
            produceFile = { context.dataStoreFile("practice.json") },
        )

    val data = store.data

    private sealed interface Operation {
        class Edit(
            val transform: (PracticePreferences) -> PracticePreferences,
        ) : Operation

        class Read(
            val result: CompletableDeferred<PracticePreferences>,
        ) : Operation
    }

    private val writes = Channel<Operation>(Channel.UNLIMITED)
    private val failure = MutableStateFlow<String?>(null)
    val writeError = failure.asStateFlow()

    init {
        scope.launch {
            for (operation in writes) {
                try {
                    when (operation) {
                        is Operation.Edit -> {
                            store.updateData(operation.transform)
                            failure.value = null
                        }
                        is Operation.Read -> operation.result.complete(store.data.first())
                    }
                } catch (error: IOException) {
                    if (operation is Operation.Read) operation.result.completeExceptionally(error)
                    failure.value = "Couldn't save settings. Your next change will try again."
                }
            }
        }
    }

    fun saveLesson(
        lesson: Lesson,
        value: LessonPreferences,
    ) {
        writes.trySend(Operation.Edit { it.copy(lessons = it.lessons + (lesson.preferenceKey to value.normalized())) })
    }

    fun saveView(view: PracticeView) {
        writes.trySend(Operation.Edit { it.copy(view = view.name) })
    }

    fun saveDefaults(defaults: AppDefaults) {
        writes.trySend(Operation.Edit { it.copy(defaults = defaults.normalized()) })
    }

    /** Library edits touch only favorites, never a stale snapshot of playback preferences. */
    fun toggleFavorite(lesson: Lesson) {
        val key = lesson.preferenceKey
        writes.trySend(
            Operation.Edit { current ->
                current.copy(
                    favorites =
                        if (key in
                            current.favorites
                        ) {
                            current.favorites - key
                        } else {
                            current.favorites + key
                        },
                )
            },
        )
    }

    fun saveZoom(pixelsPerBeat: Float) {
        writes.trySend(
            Operation.Edit {
                it.copy(pixelsPerBeat = pixelsPerBeat).let { value ->
                    value.copy(pixelsPerBeat = value.zoomPixelsPerBeat)
                }
            },
        )
    }

    /** Opening another screen must see earlier accepted edits, even during rapid navigation. */
    suspend fun snapshot(): PracticePreferences {
        val result = CompletableDeferred<PracticePreferences>()
        writes.send(Operation.Read(result))
        return result.await()
    }
}

object PracticePreferencesSerializer : Serializer<PracticePreferences> {
    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
    override val defaultValue = PracticePreferences()

    override suspend fun readFrom(input: InputStream): PracticePreferences =
        try {
            json.decodeFromString<PracticePreferences>(input.readBytes().decodeToString())
        } catch (error: SerializationException) {
            throw CorruptionException("Cannot read practice settings", error)
        }

    override suspend fun writeTo(
        t: PracticePreferences,
        output: OutputStream,
    ) {
        output.write(json.encodeToString(t).encodeToByteArray())
    }
}
