package com.example.network

import android.util.Log
import com.example.BuildConfig
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.UUID
import java.util.concurrent.TimeUnit

// --- SUPABASE DATA MODELS ---

@JsonClass(generateAdapter = true)
data class SupabaseAuthRequest(
    val email: String,
    val password: String,
    val data: Map<String, String>? = null
)

@JsonClass(generateAdapter = true)
data class SupabaseUser(
    val id: String,
    val email: String? = null
)

@JsonClass(generateAdapter = true)
data class SupabaseAuthResponse(
    @Json(name = "access_token") val accessToken: String?,
    @Json(name = "token_type") val tokenType: String?,
    @Json(name = "expires_in") val expiresIn: Long?,
    val user: SupabaseUser?
)

@JsonClass(generateAdapter = true)
data class SupabaseProfile(
    val id: String,
    val displayName: String?,
    val avatarUrl: String?,
    val location: String?,
    val farmingInterests: List<String> = emptyList(),
    val cropsGrown: List<String> = emptyList(),
    val reputationScore: Int = 0,
    val contributionsCount: Int = 0,
    val bio: String?,
    val updatedAt: String? = null
)

@JsonClass(generateAdapter = true)
data class SupabaseFarm(
    val id: String = UUID.randomUUID().toString(),
    val farmerId: String,
    val name: String,
    val location: String,
    val totalArea: String,
    val county: String,
    val subCounty: String? = null,
    val registrationDate: String? = null
)

@JsonClass(generateAdapter = true)
data class SupabaseCrop(
    val id: String = UUID.randomUUID().toString(),
    val farmId: String?,
    val farmerId: String,
    val name: String,
    val variety: String? = null,
    val plantingDate: String? = null,
    val expectedHarvest: String? = null,
    val status: String = "planted",
    val healthScore: Double = 100.0,
    val location: String? = null,
    val area: String? = null,
    val typeId: String? = null
)

@JsonClass(generateAdapter = true)
data class SupabaseTransportRequest(
    val id: String = UUID.randomUUID().toString(),
    val farmerId: String,
    val produceType: String,
    val quantity: Double,
    val unit: String,
    val pickupLocation: String,
    val destination: String,
    val preferredDate: String,
    val urgency: String = "medium",
    val status: String = "pending",
    val notes: String? = null,
    val createdAt: String? = null
)

@JsonClass(generateAdapter = true)
data class SupabaseTransporter(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val phone: String,
    val vehicleType: String,
    val maxCapacity: String,
    val currentLocation: String,
    val available: Boolean = true,
    val rating: Double = 5.0
)

@JsonClass(generateAdapter = true)
data class SupabaseSharedDeliveryGroup(
    val id: String = UUID.randomUUID().toString(),
    val destination: String,
    val transportDate: String,
    val estimatedSavings: Double = 0.0,
    val status: String = "planning"
)

@JsonClass(generateAdapter = true)
data class SupabaseSharedDeliveryMember(
    val id: String = UUID.randomUUID().toString(),
    val groupId: String,
    val requestId: String,
    val farmerId: String
)

@JsonClass(generateAdapter = true)
data class SupabaseCommunityPost(
    val id: String = UUID.randomUUID().toString(),
    val authorId: String,
    val authorName: String? = "Mkulima",
    val content: String,
    val imageUrl: String? = null,
    val category: String = "General",
    val likesCount: Int = 0,
    val commentsCount: Int = 0,
    val isTrending: Boolean = false,
    val createdAt: String? = null
)

@JsonClass(generateAdapter = true)
data class SupabaseCommunityComment(
    val id: String = UUID.randomUUID().toString(),
    val postId: String,
    val authorId: String,
    val authorName: String? = "Mkulima Commenter",
    val content: String,
    val createdAt: String? = null
)

@JsonClass(generateAdapter = true)
data class SupabaseChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val roomId: String,
    val authorId: String,
    val authorName: String? = "Speaker",
    val content: String,
    val imageUrl: String? = null,
    val createdAt: String? = null
)

@JsonClass(generateAdapter = true)
data class SupabaseNotification(
    val id: String = UUID.randomUUID().toString(),
    val userId: String,
    val title: String,
    val message: String,
    val type: String = "info",
    val read: Boolean = false,
    val createdAt: String? = null
)

@JsonClass(generateAdapter = true)
data class PostgrestRpcPayload(
    @Json(name = "target_post_id") val targetPostId: String,
    @Json(name = "user_id") val userId: String
)

// --- MAIN CLIENT INTEGRATION ---

object SupabaseClient {
    private const val TAG = "SupabaseClient"
    private val CONTENT_TYPE = "application/json".toMediaType()

    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    // Environment Injected Configuration
    private val apiBaseUrl: String = BuildConfig.SUPABASE_URL
    private val apiKeyAnon: String = BuildConfig.SUPABASE_ANON_KEY

    // Runtime Auth Session Memory
    var currentSessionToken: String? = null
        private set
    var currentUserId: String? = null
        private set
    var currentUserEmail: String? = null
        private set
    var isLocalMockMode: Boolean = false
        private set

    init {
        // Detect if credentials are unset or are placeholders
        isLocalMockMode = apiBaseUrl.isEmpty() || 
                          apiBaseUrl == "MY_SUPABASE_URL" || 
                          apiKeyAnon.isEmpty() || 
                          apiKeyAnon == "MY_SUPABASE_ANON_KEY"
                          
        if (isLocalMockMode) {
            Log.w(TAG, "Supabase environment variables are missing or set to placeholder. Operating in SIMULATION mode.")
        } else {
            Log.d(TAG, "Supabase initialized in live connection mode (URL: $apiBaseUrl)")
        }
    }

    // --- SESSION CONTEXTS ---
    fun logout() {
        currentSessionToken = null
        currentUserId = null
        currentUserEmail = null
        Log.i(TAG, "User logged out. Session cleared.")
    }

    // Is the user authenticated?
    val isAuthenticated: Boolean
        get() = currentUserId != null || (isLocalMockMode && simulatedUserId != null)

    // --- SIMULATOR STORAGE (Fallback Offline Demo) ---
    private var simulatedUserId: String? = null
    private var simulatedProfile = SupabaseProfile(
        id = "simulated-uid-3344",
        displayName = "John Kahiga",
        avatarUrl = "👨🌾",
        location = "Nyeri",
        farmingInterests = listOf("Organic Fertilizers", "Local Cooperatives"),
        cropsGrown = listOf("Maize", "Tomatoes"),
        bio = "Smallholder farmer in Nyeri scaling organic cash crops since 2019."
    )
    private val simulatedFarms = mutableListOf(
        SupabaseFarm("farm-1", "simulated-uid-3344", "Kahiga Organic Farm", "Nyeri Central", "3.2 Acres", "Nyeri", "Tetu")
    )
    private val simulatedCrops = mutableListOf(
        SupabaseCrop("crop-1", "farm-1", "simulated-uid-3344", "Hybrid Maize Section", "KAT-3", "2026-03-12", "2026-08-15", "planted", 94.0, "Zone A", "1.5 Acres")
    )
    private val simulatedTransportRequests = mutableListOf<SupabaseTransportRequest>()
    private val simulatedTransporters = mutableListOf(
        SupabaseTransporter("t-1", "Otieno Logistics", "+254711223344", "Closed Truck", "5 Tons", "Kakamega", true, 4.8),
        SupabaseTransporter("t-2", "Mumias Express Co.", "+254722334455", "Suzuki Bedford Pickup", "1.2 Tons", "Mumias", true, 4.5),
        SupabaseTransporter("t-3", "Western Hauliers Ltd.", "+254733445566", "Container Lorry", "12 Tons", "Eldoret", true, 4.9)
    )
    private val simulatedSharedGroups = mutableListOf(
        SupabaseSharedDeliveryGroup("g-1", "Aviation Market, Nairobi", "2026-06-05", 2800.0, "planning")
    )
    private val simulatedCommunityPosts = mutableListOf(
        SupabaseCommunityPost("p-1", "0000-0001", "David K.", "Has anyone seen Fall Armyworm in Eldoret this week? Seeing holes in my maize leaves.", null, "Crop Diseases", 12, 1, true, "2026-05-23T08:00:00Z"),
        SupabaseCommunityPost("p-2", "0000-0002", "Sarah M.", "Tomato prices are peaking at Nairobi Market (Wakulima). KES 4500 per crate today!", null, "Market Trends", 45, 0, true, "2026-05-23T09:12:00Z"),
        SupabaseCommunityPost("p-3", "0000-0003", "Peter O.", "Best fertilizer for late-stage maize in Kakamega? Thinking of using CAN.", null, "Fertilizers", 24, 0, false, "2026-05-22T14:30:00Z")
    )
    private val simulatedComments = mutableListOf(
        SupabaseCommunityComment("c-1", "p-1", "0000-0002", "Sarah M.", "Yes David, check the fields after rainfall. Apply neem oil oil or pyrethroids immediately.", "2026-05-23T08:30:00Z")
    )
    private val simulatedNotifications = mutableListOf(
        SupabaseNotification("n-1", "simulated-uid-3344", "Welcome!", "Your Supabase mobile integration is fully configured.", "success")
    )

    // --- AUTH ACTIONS ---

    suspend fun signUp(email: String, pword: String, displayName: String? = null): Boolean = withContext(Dispatchers.IO) {
        if (isLocalMockMode) {
            simulatedUserId = UUID.randomUUID().toString()
            currentUserId = simulatedUserId
            currentUserEmail = email
            simulatedProfile = simulatedProfile.copy(id = currentUserId!!, displayName = displayName ?: email.substringBefore("@"), location = "Nyeri")
            return@withContext true
        }

        val requestObj = SupabaseAuthRequest(
            email = email,
            password = pword,
            data = if (displayName != null) mapOf("display_name" to displayName) else null
        )
        val jsonPayload = moshi.adapter(SupabaseAuthRequest::class.java).toJson(requestObj)

        try {
            val endpoint = "$apiBaseUrl/auth/v1/signup"
            val request = Request.Builder()
                .url(endpoint)
                .addHeader("apikey", apiKeyAnon)
                .addHeader("Content-Type", "application/json")
                .post(jsonPayload.toRequestBody(CONTENT_TYPE))
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyStr = response.body?.string() ?: ""
                    val resObj = moshi.adapter(SupabaseAuthResponse::class.java).fromJson(bodyStr)
                    if (resObj != null) {
                        currentSessionToken = resObj.accessToken
                        currentUserId = resObj.user?.id
                        currentUserEmail = resObj.user?.email
                        Log.d(TAG, "SignUp Successful. UID: $currentUserId")
                        // Seed Profile
                        createOrUpdateProfile(
                            SupabaseProfile(
                                id = currentUserId!!,
                                displayName = displayName ?: email.substringBefore("@"),
                                avatarUrl = "👨🌾",
                                location = "Nyeri",
                                farmingInterests = listOf("General"),
                                cropsGrown = listOf("Maize"),
                                bio = "New farmer profile connected via Android app."
                            )
                        )
                        return@withContext true
                    }
                } else {
                    Log.e(TAG, "SignUp failed with code ${response.code}: ${response.body?.string()}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "SignUp network check crashed: ${e.message}", e)
        }
        return@withContext false
    }

    suspend fun signIn(email: String, pword: String): Boolean = withContext(Dispatchers.IO) {
        if (isLocalMockMode) {
            simulatedUserId = "simulated-uid-3344"
            currentUserId = simulatedUserId
            currentUserEmail = email
            return@withContext true
        }

        val requestObj = SupabaseAuthRequest(email = email, password = pword)
        val jsonPayload = moshi.adapter(SupabaseAuthRequest::class.java).toJson(requestObj)

        try {
            val endpoint = "$apiBaseUrl/auth/v1/token?grant_type=password"
            val request = Request.Builder()
                .url(endpoint)
                .addHeader("apikey", apiKeyAnon)
                .addHeader("Content-Type", "application/json")
                .post(jsonPayload.toRequestBody(CONTENT_TYPE))
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyStr = response.body?.string() ?: ""
                    val resObj = moshi.adapter(SupabaseAuthResponse::class.java).fromJson(bodyStr)
                    if (resObj != null) {
                        currentSessionToken = resObj.accessToken
                        currentUserId = resObj.user?.id
                        currentUserEmail = resObj.user?.email
                        Log.d(TAG, "SignIn Successful. Auth UUID: $currentUserId")
                        return@withContext true
                    }
                } else {
                    Log.e(TAG, "SignIn credentials rejected: Code ${response.code}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "SignIn query connectivity error: ${e.message}", e)
        }
        return@withContext false
    }

    // --- PROFILE CONTROLLER ---

    suspend fun fetchProfile(): SupabaseProfile? = withContext(Dispatchers.IO) {
        val uid = currentUserId ?: return@withContext if (isLocalMockMode) simulatedProfile else null
        if (isLocalMockMode) return@withContext simulatedProfile

        try {
            val endpoint = "$apiBaseUrl/rest/v1/farmer_profiles?id=eq.$uid&select=*"
            val request = Request.Builder()
                .url(endpoint)
                .addHeader("apikey", apiKeyAnon)
                .addHeader("Authorization", "Bearer ${currentSessionToken ?: apiKeyAnon}")
                .get()
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyStr = response.body?.string() ?: ""
                    val listType = Types.newParameterizedType(List::class.java, SupabaseProfile::class.java)
                    val profiles = moshi.adapter<List<SupabaseProfile>>(listType).fromJson(bodyStr)
                    return@withContext profiles?.firstOrNull()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Profile retrieval failed: ${e.message}")
        }
        return@withContext null
    }

    suspend fun createOrUpdateProfile(profile: SupabaseProfile): Boolean = withContext(Dispatchers.IO) {
        if (isLocalMockMode) {
            simulatedProfile = profile
            return@withContext true
        }

        val jsonPayload = moshi.adapter(SupabaseProfile::class.java).toJson(profile)
        try {
            val endpoint = "$apiBaseUrl/rest/v1/farmer_profiles"
            val request = Request.Builder()
                .url(endpoint)
                .addHeader("apikey", apiKeyAnon)
                .addHeader("Authorization", "Bearer ${currentSessionToken ?: apiKeyAnon}")
                .addHeader("Prefer", "resolution=merge-duplicates")
                .post(jsonPayload.toRequestBody(CONTENT_TYPE))
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    return@withContext true
                } else {
                    Log.e(TAG, "Profile push failed: ${response.code} error: ${response.body?.string()}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Profile push API error: ${e.message}")
        }
        return@withContext false
    }

    // --- FARMS & CROPS ---

    suspend fun fetchFarms(): List<SupabaseFarm> = withContext(Dispatchers.IO) {
        if (isLocalMockMode) return@withContext simulatedFarms
        val uid = currentUserId ?: return@withContext emptyList()

        try {
            val endpoint = "$apiBaseUrl/rest/v1/farms?farmerId=eq.$uid&select=*"
            val request = Request.Builder()
                .url(endpoint)
                .addHeader("apikey", apiKeyAnon)
                .addHeader("Authorization", "Bearer ${currentSessionToken ?: apiKeyAnon}")
                .get()
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyStr = response.body?.string() ?: ""
                    val listType = Types.newParameterizedType(List::class.java, SupabaseFarm::class.java)
                    return@withContext moshi.adapter<List<SupabaseFarm>>(listType).fromJson(bodyStr) ?: emptyList()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Farms fetch failed: ${e.message}")
        }
        return@withContext emptyList()
    }

    suspend fun addFarm(farm: SupabaseFarm): Boolean = withContext(Dispatchers.IO) {
        if (isLocalMockMode) {
            simulatedFarms.add(farm)
            return@withContext true
        }

        val jsonPayload = moshi.adapter(SupabaseFarm::class.java).toJson(farm)
        try {
            val endpoint = "$apiBaseUrl/rest/v1/farms"
            val request = Request.Builder()
                .url(endpoint)
                .addHeader("apikey", apiKeyAnon)
                .addHeader("Authorization", "Bearer ${currentSessionToken ?: apiKeyAnon}")
                .post(jsonPayload.toRequestBody(CONTENT_TYPE))
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                return@withContext response.isSuccessful
            }
        } catch (e: Exception) {
            Log.e(TAG, "Add farm failed: ${e.message}")
        }
        return@withContext false
    }

    suspend fun fetchCrops(): List<SupabaseCrop> = withContext(Dispatchers.IO) {
        if (isLocalMockMode) return@withContext simulatedCrops
        val uid = currentUserId ?: return@withContext emptyList()

        try {
            val endpoint = "$apiBaseUrl/rest/v1/crops?farmerId=eq.$uid&select=*"
            val request = Request.Builder()
                .url(endpoint)
                .addHeader("apikey", apiKeyAnon)
                .addHeader("Authorization", "Bearer ${currentSessionToken ?: apiKeyAnon}")
                .get()
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyStr = response.body?.string() ?: ""
                    val listType = Types.newParameterizedType(List::class.java, SupabaseCrop::class.java)
                    return@withContext moshi.adapter<List<SupabaseCrop>>(listType).fromJson(bodyStr) ?: emptyList()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Crops fetch failed: ${e.message}")
        }
        return@withContext emptyList()
    }

    suspend fun addCrop(crop: SupabaseCrop): Boolean = withContext(Dispatchers.IO) {
        if (isLocalMockMode) {
            simulatedCrops.add(crop)
            return@withContext true
        }

        val jsonPayload = moshi.adapter(SupabaseCrop::class.java).toJson(crop)
        try {
            val endpoint = "$apiBaseUrl/rest/v1/crops"
            val request = Request.Builder()
                .url(endpoint)
                .addHeader("apikey", apiKeyAnon)
                .addHeader("Authorization", "Bearer ${currentSessionToken ?: apiKeyAnon}")
                .post(jsonPayload.toRequestBody(CONTENT_TYPE))
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                return@withContext response.isSuccessful
            }
        } catch (e: Exception) {
            Log.e(TAG, "Add crop failed: ${e.message}")
        }
        return@withContext false
    }

    // --- ROAD CO-LOADING TRANSPORT SYSTEM ---

    suspend fun fetchTransportRequests(): List<SupabaseTransportRequest> = withContext(Dispatchers.IO) {
        if (isLocalMockMode) return@withContext simulatedTransportRequests
        val uid = currentUserId ?: return@withContext emptyList()

        try {
            val endpoint = "$apiBaseUrl/rest/v1/transport_requests?farmerId=eq.$uid&select=*"
            val request = Request.Builder()
                .url(endpoint)
                .addHeader("apikey", apiKeyAnon)
                .addHeader("Authorization", "Bearer ${currentSessionToken ?: apiKeyAnon}")
                .get()
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyStr = response.body?.string() ?: ""
                    val listType = Types.newParameterizedType(List::class.java, SupabaseTransportRequest::class.java)
                    return@withContext moshi.adapter<List<SupabaseTransportRequest>>(listType).fromJson(bodyStr) ?: emptyList()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Transport requests fetch exception: ${e.message}")
        }
        return@withContext emptyList()
    }

    suspend fun addTransportRequest(tr: SupabaseTransportRequest): Boolean = withContext(Dispatchers.IO) {
        if (isLocalMockMode) {
            simulatedTransportRequests.add(0, tr)
            return@withContext true
        }

        val jsonPayload = moshi.adapter(SupabaseTransportRequest::class.java).toJson(tr)
        try {
            val endpoint = "$apiBaseUrl/rest/v1/transport_requests"
            val request = Request.Builder()
                .url(endpoint)
                .addHeader("apikey", apiKeyAnon)
                .addHeader("Authorization", "Bearer ${currentSessionToken ?: apiKeyAnon}")
                .post(jsonPayload.toRequestBody(CONTENT_TYPE))
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                return@withContext response.isSuccessful
            }
        } catch (e: Exception) {
            Log.e(TAG, "Post transport request exceptions: ${e.message}")
        }
        return@withContext false
    }

    suspend fun fetchTransporters(): List<SupabaseTransporter> = withContext(Dispatchers.IO) {
        if (isLocalMockMode) return@withContext simulatedTransporters

        try {
            val endpoint = "$apiBaseUrl/rest/v1/transporters?select=*"
            val request = Request.Builder()
                .url(endpoint)
                .addHeader("apikey", apiKeyAnon)
                .addHeader("Authorization", "Bearer ${currentSessionToken ?: apiKeyAnon}")
                .get()
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyStr = response.body?.string() ?: ""
                    val listType = Types.newParameterizedType(List::class.java, SupabaseTransporter::class.java)
                    return@withContext moshi.adapter<List<SupabaseTransporter>>(listType).fromJson(bodyStr) ?: emptyList()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Transporters retrieval crash: ${e.message}")
        }
        return@withContext emptyList()
    }

    suspend fun fetchSharedDeliveryGroups(): List<SupabaseSharedDeliveryGroup> = withContext(Dispatchers.IO) {
        if (isLocalMockMode) return@withContext simulatedSharedGroups

        try {
            val endpoint = "$apiBaseUrl/rest/v1/shared_delivery_groups?select=*"
            val request = Request.Builder()
                .url(endpoint)
                .addHeader("apikey", apiKeyAnon)
                .addHeader("Authorization", "Bearer ${currentSessionToken ?: apiKeyAnon}")
                .get()
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyStr = response.body?.string() ?: ""
                    val listType = Types.newParameterizedType(List::class.java, SupabaseSharedDeliveryGroup::class.java)
                    return@withContext moshi.adapter<List<SupabaseSharedDeliveryGroup>>(listType).fromJson(bodyStr) ?: emptyList()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Delivery groups sync crash: ${e.message}")
        }
        return@withContext emptyList()
    }

    // --- COMMUNITY FORUM SYSTEM ---

    suspend fun fetchCommunityPosts(): List<SupabaseCommunityPost> = withContext(Dispatchers.IO) {
        if (isLocalMockMode) return@withContext simulatedCommunityPosts.sortedByDescending { it.createdAt }

        try {
            val endpoint = "$apiBaseUrl/rest/v1/community_posts?select=*,farmer_profiles(displayName)&order=createdAt.desc"
            val request = Request.Builder()
                .url(endpoint)
                .addHeader("apikey", apiKeyAnon)
                .addHeader("Authorization", "Bearer ${currentSessionToken ?: apiKeyAnon}")
                .get()
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyStr = response.body?.string() ?: ""
                    val listType = Types.newParameterizedType(List::class.java, SupabaseCommunityPost::class.java)
                    return@withContext moshi.adapter<List<SupabaseCommunityPost>>(listType).fromJson(bodyStr) ?: emptyList()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Community forum posts fetch exception: ${e.message}")
        }
        return@withContext emptyList()
    }

    suspend fun addCommunityPost(post: SupabaseCommunityPost): Boolean = withContext(Dispatchers.IO) {
        if (isLocalMockMode) {
            simulatedCommunityPosts.add(0, post)
            return@withContext true
        }

        val jsonPayload = moshi.adapter(SupabaseCommunityPost::class.java).toJson(post)
        try {
            val endpoint = "$apiBaseUrl/rest/v1/community_posts"
            val request = Request.Builder()
                .url(endpoint)
                .addHeader("apikey", apiKeyAnon)
                .addHeader("Authorization", "Bearer ${currentSessionToken ?: apiKeyAnon}")
                .post(jsonPayload.toRequestBody(CONTENT_TYPE))
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                return@withContext response.isSuccessful
            }
        } catch (e: Exception) {
            Log.e(TAG, "Post community story crash: ${e.message}")
        }
        return@withContext false
    }

    suspend fun toggleLike(postId: String): Boolean = withContext(Dispatchers.IO) {
        val uid = currentUserId ?: if (isLocalMockMode) simulatedUserId ?: "mock-id-1" else return@withContext false
        if (isLocalMockMode) {
            val postIdx = simulatedCommunityPosts.indexOfFirst { it.id == postId }
            if (postIdx != -1) {
                val post = simulatedCommunityPosts[postIdx]
                val randomDiff = if (Math.random() > 0.4) 1 else -1
                simulatedCommunityPosts[postIdx] = post.copy(
                    likesCount = maxOf(0, post.likesCount + randomDiff)
                )
                return@withContext true
            }
            return@withContext false
        }

        // Call RPC toggle_post_like on Postgres Database
        val payload = PostgrestRpcPayload(targetPostId = postId, userId = uid)
        val jsonPayload = moshi.adapter(PostgrestRpcPayload::class.java).toJson(payload)

        try {
            val endpoint = "$apiBaseUrl/rest/v1/rpc/toggle_post_like"
            val request = Request.Builder()
                .url(endpoint)
                .addHeader("apikey", apiKeyAnon)
                .addHeader("Authorization", "Bearer ${currentSessionToken ?: apiKeyAnon}")
                .post(jsonPayload.toRequestBody(CONTENT_TYPE))
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                return@withContext response.isSuccessful
            }
        } catch (e: Exception) {
            Log.e(TAG, "Like toggling failed: ${e.message}")
        }
        return@withContext false
    }

    suspend fun fetchComments(postId: String): List<SupabaseCommunityComment> = withContext(Dispatchers.IO) {
        if (isLocalMockMode) return@withContext simulatedComments.filter { it.postId == postId }

        try {
            val endpoint = "$apiBaseUrl/rest/v1/community_comments?postId=eq.$postId&select=*"
            val request = Request.Builder()
                .url(endpoint)
                .addHeader("apikey", apiKeyAnon)
                .addHeader("Authorization", "Bearer ${currentSessionToken ?: apiKeyAnon}")
                .get()
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyStr = response.body?.string() ?: ""
                    val listType = Types.newParameterizedType(List::class.java, SupabaseCommunityComment::class.java)
                    return@withContext moshi.adapter<List<SupabaseCommunityComment>>(listType).fromJson(bodyStr) ?: emptyList()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Comments retrieval failed: ${e.message}")
        }
        return@withContext emptyList()
    }

    suspend fun addComment(comment: SupabaseCommunityComment): Boolean = withContext(Dispatchers.IO) {
        if (isLocalMockMode) {
            simulatedComments.add(comment)
            // Increment local post counter
            val postIdx = simulatedCommunityPosts.indexOfFirst { it.id == comment.postId }
            if (postIdx != -1) {
                simulatedCommunityPosts[postIdx] = simulatedCommunityPosts[postIdx].copy(
                    commentsCount = simulatedCommunityPosts[postIdx].commentsCount + 1
                )
            }
            return@withContext true
        }

        val jsonPayload = moshi.adapter(SupabaseCommunityComment::class.java).toJson(comment)
        try {
            val endpoint = "$apiBaseUrl/rest/v1/community_comments"
            val request = Request.Builder()
                .url(endpoint)
                .addHeader("apikey", apiKeyAnon)
                .addHeader("Authorization", "Bearer ${currentSessionToken ?: apiKeyAnon}")
                .post(jsonPayload.toRequestBody(CONTENT_TYPE))
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                return@withContext response.isSuccessful
            }
        } catch (e: Exception) {
            Log.e(TAG, "Post comment failed: ${e.message}")
        }
        return@withContext false
    }

    // --- NOTIFICATIONS FLOW ---

    suspend fun fetchNotifications(): List<SupabaseNotification> = withContext(Dispatchers.IO) {
        if (isLocalMockMode) return@withContext simulatedNotifications
        val uid = currentUserId ?: return@withContext emptyList()

        try {
            val endpoint = "$apiBaseUrl/rest/v1/notifications?userId=eq.$uid&select=*"
            val request = Request.Builder()
                .url(endpoint)
                .addHeader("apikey", apiKeyAnon)
                .addHeader("Authorization", "Bearer ${currentSessionToken ?: apiKeyAnon}")
                .get()
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyStr = response.body?.string() ?: ""
                    val listType = Types.newParameterizedType(List::class.java, SupabaseNotification::class.java)
                    return@withContext moshi.adapter<List<SupabaseNotification>>(listType).fromJson(bodyStr) ?: emptyList()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Notifications retrieval failed: ${e.message}")
        }
        return@withContext emptyList()
    }
}
