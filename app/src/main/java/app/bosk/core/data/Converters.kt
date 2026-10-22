package app.bosk.core.data

import androidx.room.TypeConverter
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/** Room type converters. Lists are stored as JSON strings. */
class Converters {
    @TypeConverter
    fun fromTags(tags: List<String>): String =
        json.encodeToString(ListSerializer(String.serializer()), tags)

    @TypeConverter
    fun toTags(value: String): List<String> =
        if (value.isBlank()) emptyList()
        else json.decodeFromString(ListSerializer(String.serializer()), value)

    companion object {
        private val json = Json { ignoreUnknownKeys = true }
    }
}
