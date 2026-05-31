package com.example.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface GlossaryDao {
    @Query("SELECT * FROM glossary_terms ORDER BY timestamp DESC")
    fun getAllTerms(): Flow<List<GlossaryTerm>>

    @Query("SELECT * FROM glossary_terms")
    suspend fun getAllTermsList(): List<GlossaryTerm>

    @Query("SELECT * FROM glossary_terms WHERE sourceLanguage = :source AND targetLanguage = :target")
    suspend fun getTermsByLanguagePair(source: String, target: String): List<GlossaryTerm>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTerm(term: GlossaryTerm): Long

    @Query("DELETE FROM glossary_terms WHERE id = :id")
    suspend fun deleteTermById(id: Int)

    @Query("SELECT * FROM glossary_terms WHERE sourcePhrase LIKE '%' || :query || '%' OR translatedPhrase LIKE '%' || :query || '%'")
    fun searchTerms(query: String): Flow<List<GlossaryTerm>>
}
