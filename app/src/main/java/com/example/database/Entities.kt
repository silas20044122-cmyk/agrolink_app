package com.example.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "scan_history")
data class ScanHistory(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val cropType: String,
    val diseaseName: String,
    val confidenceScore: Double,
    val treatment: String,
    val prevention: String,
    val fertilizer: String,
    val urgency: String, // "High", "Medium", "Low" / "Haraka", "Kiasi", "Chini"
    val timestamp: Long = System.currentTimeMillis(),
    val localImageUri: String? = null
)

@Entity(tableName = "chat_messages")
data class ChatMessage(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val text: String,
    val isUser: Boolean,
    val language: String, // "English" or "Kiswahili"
    val timestamp: Long = System.currentTimeMillis(),
    val voiceDurationSecs: Int? = null // if it was voice input
)

@Entity(tableName = "weather_cache")
data class WeatherCache(
    @PrimaryKey val id: Int = 1,
    val currentTemp: Double,
    val rainfallChance: Double,
    val humidity: Double,
    val windSpeed: Double,
    val uvIndex: Double,
    val condition: String,
    val recommendationsJson: String, // JSON array of suggestions
    val forecast7DayJson: String, // JSON array of forecast items
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "market_cache")
data class MarketCache(
    @PrimaryKey val id: Int = 1,
    val pricesJson: String, // JSON array of market prices
    val listingsJson: String, // JSON array of custom listings
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "user_profile")
data class UserProfile(
    @PrimaryKey val id: Int = 1,
    val fullName: String = "John Kahiga",
    val county: String = "Nyeri",
    val farmSizeAcres: Double = 2.5,
    val cropTypesJson: String = "[\"Maize\", \"Tomatoes\", \"Potatoes\"]",
    val language: String = "English", // "English" or "Kiswahili"
    val interestsJson: String = "[\"Organic Farming\", \"Market Alerts\"]",
    val timestamp: Long = System.currentTimeMillis()
)
