package com.example.sendit.data

import androidx.room.TypeConverter
import java.nio.ByteBuffer
import org.json.JSONArray

// Main class for reading and writing climbing data. Called by the worker and analyser.
class RoomConverters {
    // Stores floats as bytes, preserving missing confidence values represented by NaN.
    @TypeConverter
    fun coordinatesToBytes(coordinates: FloatArray): ByteArray {
        val buffer = ByteBuffer.allocate(coordinates.size * Float.SIZE_BYTES)
        buffer.asFloatBuffer().put(coordinates)
        return buffer.array()
    }

    // Restores the same coordinate order from the stored bytes.
    @TypeConverter
    fun bytesToCoordinates(bytes: ByteArray): FloatArray {
        val buffer = ByteBuffer.wrap(bytes)
        return FloatArray(bytes.size / Float.SIZE_BYTES) { buffer.float }
    }

    // Stores route tags without relying on a separator that could appear in a tag.
    @TypeConverter
    fun tagsToJson(tags: Set<String>): String = JSONArray(tags.toList()).toString()

    // Restores the UML's set of route tags.
    @TypeConverter
    fun jsonToTags(json: String): Set<String> {
        val array = JSONArray(json)
        return (0 until array.length()).map { array.getString(it) }.toSet()
    }
}
