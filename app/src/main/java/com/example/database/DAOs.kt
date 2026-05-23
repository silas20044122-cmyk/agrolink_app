package com.example.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface AgroDao {
    // --- Scan History ---
    @Query("SELECT * FROM scan_history ORDER BY timestamp DESC")
    fun getAllScans(): Flow<List<ScanHistory>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScan(scan: ScanHistory)

    @Query("DELETE FROM scan_history WHERE id = :id")
    suspend fun deleteScanById(id: Int)

    @Query("DELETE FROM scan_history")
    suspend fun clearScanHistory()

    // --- Chat Messages ---
    @Query("SELECT * FROM chat_messages ORDER BY timestamp ASC")
    fun getChatMessages(): Flow<List<ChatMessage>>

    @Query("SELECT COUNT(*) FROM chat_messages")
    suspend fun getChatCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChatMessage(message: ChatMessage)

    @Query("DELETE FROM chat_messages")
    suspend fun clearChatHistory()

    // --- Weather Cache ---
    @Query("SELECT * FROM weather_cache WHERE id = 1")
    suspend fun getWeatherCache(): WeatherCache?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWeatherCache(cache: WeatherCache)

    // --- Market Cache ---
    @Query("SELECT * FROM market_cache WHERE id = 1")
    suspend fun getMarketCache(): MarketCache?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMarketCache(cache: MarketCache)

    // --- User Profile ---
    @Query("SELECT * FROM user_profile WHERE id = 1")
    fun getUserProfileFlow(): Flow<UserProfile?>

    @Query("SELECT * FROM user_profile WHERE id = 1")
    suspend fun getUserProfile(): UserProfile?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUserProfile(profile: UserProfile)
}
