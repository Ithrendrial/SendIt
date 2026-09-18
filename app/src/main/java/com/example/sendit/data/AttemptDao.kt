package com.example.sendit.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AttemptDao {
    @Insert
    suspend fun insert(attempt: AttemptEntity): Long

    @Query("SELECT * FROM attempts ORDER BY createdAt DESC")
    fun getAll(): Flow<List<AttemptEntity>>

    @Query("SELECT * FROM attempts WHERE id = :id")
    suspend fun getById(id: Long): AttemptEntity?

    @Query("DELETE FROM attempts WHERE id = :id")
    suspend fun deleteById(id: Long)
}
