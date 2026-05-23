package com.example.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.GeminiClient
import com.example.database.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

class AgroViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getDatabase(application)
    private val dao = database.agroDao()

    // --- State Observables (Room Reactive Flows) ---
    val scanHistory: StateFlow<List<ScanHistory>> = dao.getAllScans()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val chatMessages: StateFlow<List<ChatMessage>> = dao.getChatMessages()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val userProfile: StateFlow<UserProfile> = dao.getUserProfileFlow()
        .map { it ?: UserProfile() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserProfile())

    // --- Local Custom Live UI State ---
    private val _isAnalyzing = MutableStateFlow(false)
    val isAnalyzing: StateFlow<Boolean> = _isAnalyzing.asStateFlow()

    private val _isChatLoading = MutableStateFlow(false)
    val isChatLoading: StateFlow<Boolean> = _isChatLoading.asStateFlow()

    // Active diagnostic result displayed in Scanner Screen
    private val _activeScanResult = MutableStateFlow<ScanHistory?>(null)
    val activeScanResult: StateFlow<ScanHistory?> = _activeScanResult.asStateFlow()

    // --- System Language Toggle State ---
    // Track language centrally. If profile exists, profile language prevails.
    val appLanguage = userProfile.map { it.language }.stateIn(viewModelScope, SharingStarted.Eagerly, "English")

    // --- Weather & Market Memory State ---
    private val _weatherState = MutableStateFlow<WeatherState>(getFallbackWeatherState("Nyeri"))
    val weatherState: StateFlow<WeatherState> = _weatherState.asStateFlow()

    private val _marketState = MutableStateFlow<MarketState>(getFallbackMarketState())
    val marketState: StateFlow<MarketState> = _marketState.asStateFlow()

    // --- Text-to-Speech (TTS) Engine ---
    private var tts: TextToSpeech? = null
    private var isTtsReady = false
    private val _isTtsSpeaking = MutableStateFlow(false)
    val isTtsSpeaking: StateFlow<Boolean> = _isTtsSpeaking.asStateFlow()

    init {
        // Initialize TTS safely with progress callback monitoring and wrap with try-catch
        try {
            tts = TextToSpeech(application) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    try {
                        tts?.let { activeTts ->
                            val swahiliResult = activeTts.setLanguage(Locale("sw", "KE"))
                            if (swahiliResult == TextToSpeech.LANG_MISSING_DATA || swahiliResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                                activeTts.language = Locale.ENGLISH
                            }

                            // Register Utterance Progress Listener to reset isSpeaking state automatically
                            activeTts.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
                                override fun onStart(utteranceId: String?) {
                                    _isTtsSpeaking.value = true
                                }

                                override fun onDone(utteranceId: String?) {
                                    _isTtsSpeaking.value = false
                                }

                                @Deprecated("Deprecated in Java")
                                override fun onError(utteranceId: String?) {
                                    _isTtsSpeaking.value = false
                                }

                                override fun onError(utteranceId: String?, errorCode: Int) {
                                    _isTtsSpeaking.value = false
                                }
                            })
                            isTtsReady = true
                        }
                    } catch (innerEx: Exception) {
                        Log.e("AgroViewModel", "TTS initialization failed to set locale/progress listeners: ${innerEx.message}")
                        isTtsReady = false
                    }
                } else {
                    isTtsReady = false
                }
            }
        } catch (e: Exception) {
            Log.e("AgroViewModel", "TTS engine construction failed or unsupported by OS: ${e.message}")
            tts = null
            isTtsReady = false
        }

        // Prefill DB caches on launch if empty
        viewModelScope.launch {
            if (dao.getUserProfile() == null) {
                dao.insertUserProfile(UserProfile())
            }
            loadAndCacheWeather()
            loadAndCacheMarket()
            
            // Welcome message in Chatbot
            val welcomeEn = "Jambo! Welcome to AgroLink Farming AI. Ask me any question in English or Kiswahili about planting, soil fertility, pest control, or market prices in your county!"
            val welcomeSw = "Jambo Mkulima! Karibu kwenye AgroLink Farming AI. Niulize swali lolote kwa Kiingereza au Kiswahili kuhusu upandaji, rutuba ya udongo, kudhibiti wadudu, au bei za mazao katika kaunti yako!"
            
            // Only add introductory messages if message list is completely empty
            try {
                if (dao.getChatCount() == 0) {
                    dao.insertChatMessage(ChatMessage(text = welcomeEn, isUser = false, language = "English"))
                    dao.insertChatMessage(ChatMessage(text = welcomeSw, isUser = false, language = "Kiswahili"))
                }
            } catch (e: Exception) {
                Log.e("AgroViewModel", "Failed to prefill chatbot messages: ${e.message}")
            }
        }
    }

    // --- User Profile Actions ---
    fun updateProfile(
        name: String,
        county: String,
        farmSize: Double,
        crops: List<String>,
        lang: String,
        interests: List<String>
    ) {
        viewModelScope.launch {
            val cropJson = JSONArray(crops).toString()
            val interestJson = JSONArray(interests).toString()
            val updated = UserProfile(
                id = 1,
                fullName = name,
                county = county,
                farmSizeAcres = farmSize,
                cropTypesJson = cropJson,
                language = lang,
                interestsJson = interestJson
            )
            dao.insertUserProfile(updated)
            // Reload regional weather since user changed County!
            loadAndCacheWeather(county)
        }
    }

    // --- TTS Actions ---
    fun speakText(text: String) {
        if (tts != null && isTtsReady) {
            try {
                _isTtsSpeaking.value = true
                // Strip markdown notations for clean vocalization
                val cleanStr = text.replace(Regex("[#*`_\\-]"), "")
                tts?.speak(cleanStr, TextToSpeech.QUEUE_FLUSH, null, "AgroLinkSpeech")
            } catch (e: Exception) {
                Log.e("AgroViewModel", "TTS speak failed: ${e.message}")
                _isTtsSpeaking.value = false
            }
        } else {
            Log.w("AgroViewModel", "TTS speak ignored because engine is not ready or failed to bind.")
        }
    }

    fun stopSpeaking() {
        tts?.stop()
        _isTtsSpeaking.value = false
    }

    // --- Weather Cache Controller ---
    private suspend fun loadAndCacheWeather(county: String? = null) {
        val selectedCounty = county ?: dao.getUserProfile()?.county ?: "Nyeri"
        val cached = dao.getWeatherCache()
        if (cached != null && (System.currentTimeMillis() - cached.timestamp < 3600000) && county == null) {
            // Read cached state
            _weatherState.value = WeatherState(
                county = selectedCounty,
                temp = cached.currentTemp,
                rainChance = cached.rainfallChance,
                humidity = cached.humidity,
                wind = cached.windSpeed,
                uv = cached.uvIndex,
                conditionEn = cached.condition,
                recommendations = parseJsonStringArray(cached.recommendationsJson),
                forecast7Day = parseJsonForecastArray(cached.forecast7DayJson)
            )
        } else {
            // Regenerate high quality county forecast
            val state = getFallbackWeatherState(selectedCounty)
            _weatherState.value = state
            // Write cache
            val cacheObj = WeatherCache(
                currentTemp = state.temp,
                rainfallChance = state.rainChance,
                humidity = state.humidity,
                windSpeed = state.wind,
                uvIndex = state.uv,
                condition = state.conditionEn,
                recommendationsJson = JSONArray(state.recommendations).toString(),
                forecast7DayJson = serialize7DayForecast(state.forecast7Day)
            )
            dao.insertWeatherCache(cacheObj)
        }
    }

    // --- Market Cache Controller ---
    private suspend fun loadAndCacheMarket() {
        val cached = dao.getMarketCache()
        if (cached != null) {
            _marketState.value = MarketState(
                marketPrices = parseJsonPricesArray(cached.pricesJson),
                farmerListings = parseJsonListingsArray(cached.listingsJson)
            )
        } else {
            val state = getFallbackMarketState()
            _marketState.value = state
            val cacheObj = MarketCache(
                pricesJson = serializeMarketPrices(state.marketPrices),
                listingsJson = serializeFarmerListings(state.farmerListings)
            )
            dao.insertMarketCache(cacheObj)
        }
    }

    fun addFarmerListing(cropName: String, qtyLabel: String, priceLabel: String, contact: String) {
        viewModelScope.launch {
            val countyName = dao.getUserProfile()?.county ?: "Nyeri"
            val newElement = FarmerListing(
                cropType = cropName,
                quantity = qtyLabel,
                priceString = priceLabel,
                farmerName = dao.getUserProfile()?.fullName ?: "F. Mkulima",
                county = countyName,
                contactPhone = contact,
                isMyListing = true
            )
            // Save to state
            val updatedListings = listOf(newElement) + _marketState.value.farmerListings
            _marketState.value = _marketState.value.copy(farmerListings = updatedListings)

            // Cache back to local Room database for offline persistence
            dao.insertMarketCache(
                MarketCache(
                    pricesJson = serializeMarketPrices(_marketState.value.marketPrices),
                    listingsJson = serializeFarmerListings(_marketState.value.farmerListings)
                )
            )
        }
    }

    // --- Chat Room Engine ---
    fun sendChatMessage(text: String) {
        if (text.isBlank()) return

        viewModelScope.launch {
            val activeLang = userProfile.value.language
            // 1. Save user query
            val userMsg = ChatMessage(text = text, isUser = true, language = activeLang)
            dao.insertChatMessage(userMsg)

            _isChatLoading.value = true

            // 2. Fetch full chat context for Gemini
            val history = chatMessages.value.takeLast(10)
            val promptBuilder = StringBuilder()
            promptBuilder.append("User context details: County = ${userProfile.value.county}, Crops = ${userProfile.value.cropTypesJson}, Preferred language = $activeLang.\n\n")
            history.forEach {
                val prefix = if (it.isUser) "Farmer: " else "System/Advisor: "
                promptBuilder.append("$prefix${it.text}\n")
            }
            promptBuilder.append("Advisor Response (reply directly in $activeLang, keep it extremely simple, highly practical and localized for smallholders):")

            // 3. Ask Gemini Client
            val reply = GeminiClient.generateContent(
                prompt = promptBuilder.toString(),
                systemPrompt = getChatbotSystemRole(activeLang, userProfile.value.county, userProfile.value.fullName)
            )

            // 4. Save response to database
            val responseMsg = ChatMessage(text = reply, isUser = false, language = activeLang)
            dao.insertChatMessage(responseMsg)

            _isChatLoading.value = false

            // Auto speak response for accessibility support if enabled
            speakText(reply)
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            dao.clearChatHistory()
        }
    }

    // --- Crop Disease Scanner Engine ---
    fun analyzeCropDisease(cropType: String, bitmap: Bitmap?, sampleTag: String = "") {
        _isAnalyzing.value = true
        _activeScanResult.value = null

        viewModelScope.launch {
            val activeLang = userProfile.value.language
            val prompt = if (sampleTag.isNotEmpty()) {
                "Perform a rigorous agro-analysis for $cropType with disease characteristics: $sampleTag. Provide highly structured diagnostic analysis containing: Name of Disease, Confidence Score (%), Detailed treatment suggestions, preventive crop dusting / weeding methods, recommended organic/synthetic fertilizer, and urgency level."
            } else {
                "Diagnose the crop $cropType. Check if this leaves from $cropType has a disease or deficiency. Provide bilingually helpful Kenyan agricultural tips."
            }

            // Call client
            val analysisText = GeminiClient.generateContent(
                prompt = prompt,
                systemPrompt = getScannerSystemRole(activeLang),
                bitmap = bitmap
            )

            // Parse result safely
            val parsedResult = extractDiagnosisFields(cropType, analysisText)
            
            // Save to local database
            dao.insertScan(parsedResult)
            
            _activeScanResult.value = parsedResult
            _isAnalyzing.value = false
        }
    }

    fun clearScans() {
        viewModelScope.launch {
            dao.clearScanHistory()
            _activeScanResult.value = null
        }
    }

    override fun onCleared() {
        super.onCleared()
        try {
            tts?.shutdown()
        } catch (e: Exception) {
            Log.e("AgroViewModel", "TTS shutdown failed: ${e.message}")
        }
    }

    // --- Helpers for parsing and fallbacks ---

    private fun getChatbotSystemRole(language: String, county: String, name: String): String {
        return """
            You are AgroLink Advisor, an elite, bilingually fluent AI farming consultant serving Kenyan smallholders.
            User is $name in $county County.
            Current response language preference: $language.
            Follow these constraints:
            * Speak with deep local knowledge (refer to Agrovet inputs, Kephis certified seeds, local rainfall patterns in $county County, organic remedies like neem wood dust 'Mwarobaini' or volcanic ash, and Kenyan shillings 'KES').
            * For Kiswahili, speak beautifully, in simple, accessible, respectful terms.
            * Avoid long academic essays. Emphasize step-by-step action sheets with bullet points:
              - Hatua ya kwanza (Step one)
              - Hatua ya pili (Step two)
            * If the user is asking about climate or drought, present water conservation techniques like mulching or micro-irrigation.
        """.trimIndent()
    }

    private fun getScannerSystemRole(language: String): String {
        return """
            You are AgroLink Vision, a highly sophisticated crop pathogen diagnostic system expert.
            Analyze details/image of the leaf/crop and reply strictly in $language.
            Break down the report with the following explicit sections:
            - Disease Title / Name (Kichwa cha Maambukizi)
            - Confidence Level % (Kiwango cha Kujiamini %)
            - Symptoms (Dalili)
            - How to treat (Jinsi ya Kutibu)
            - Prevention (Kuzuia)
            - Recommended fertiliser/top-dress
            - Urgency (Haraka / Kiasi)
        """.trimIndent()
    }

    private fun extractDiagnosisFields(cropType: String, markdown: String): ScanHistory {
        // Safe extraction of fields from the Markdown block
        val lines = markdown.split("\n")
        var disease = "Healthy / Unknown Disease"
        var confidence = 85.0
        var urgency = "Medium"
        var treatment = markdown
        var prevention = "Use certified clean seeds, select disease resistant varieties, rotate crops regularly."
        var fertilizer = "NPK 17:17:17 at transplanting or CAN for top dressing."

        try {
            for (line in lines) {
                val clean = line.replace(Regex("[#*`_\\-]"), "").trim()
                if (clean.contains("Diagnosis", ignoreCase = true) || clean.contains("Disease", ignoreCase = true) || clean.contains("Maambukizi", ignoreCase = true) || clean.contains("Kichwa", ignoreCase = true)) {
                    val split = clean.split(":")
                    if (split.size > 1) disease = split[1].trim()
                }
                if (clean.contains("Confidence", ignoreCase = true) || clean.contains("Uhakika", ignoreCase = true)) {
                    val numeric = clean.replace(Regex("[^0-9.]"), "")
                    confidence = numeric.toDoubleOrNull() ?: 85.0
                }
                if (clean.contains("Urgency", ignoreCase = true) || clean.contains("Haraka", ignoreCase = true)) {
                    val split = clean.split(":")
                    if (split.size > 1) urgency = split[1].trim()
                }
                if (clean.contains("Fertiliser", ignoreCase = true) || clean.contains("Mbolea", ignoreCase = true)) {
                    val split = clean.split(":")
                    if (split.size > 1) fertilizer = split[1].trim()
                }
            }
            
            // Refine Treatment to look cute if too blocky
            if (markdown.length > 300) {
                val sections = markdown.split("###")
                for (section in sections) {
                    val sClean = section.trim()
                    if (sClean.startsWith("How to treat", ignoreCase = true) || sClean.startsWith("Kutibu", ignoreCase = true) || sClean.startsWith("Treatment", ignoreCase = true)) {
                        treatment = sClean.substringAfter("\n").trim()
                    }
                    if (sClean.startsWith("Prevention", ignoreCase = true) || sClean.startsWith("Kuzuia", ignoreCase = true)) {
                        prevention = sClean.substringAfter("\n").trim()
                    }
                }
            }
        } catch (_: Exception) {}

        if (disease.length > 60) {
            disease = disease.take(57) + "..."
        }

        return ScanHistory(
            cropType = cropType,
            diseaseName = disease.replace(Regex("^[\\s:]+"), ""),
            confidenceScore = if (confidence > 100) 95.0 else if (confidence < 10) 85.0 else confidence,
            treatment = treatment.take(1500),
            prevention = prevention.take(800),
            fertilizer = fertilizer.take(500),
            urgency = urgency
        )
    }

    private fun getFallbackWeatherState(county: String): WeatherState {
        // High quality county recommendations
        val recs = when (county) {
            "Nyeri", "Meru", "Kirinyaga" -> listOf(
                "Cold misty morning. Protect potato plantings against Early Blight using preventative copper sprays early today.",
                "Favorable sunny afternoon in $county highland areas. Turning soil in coffee blocks advised.",
                "Plant seeds of cold-hardy beans. Postpone nitrogen top-dressing to tomorrow to avoid nitrogen leaching in wet spots."
            )
            "Kitui", "Makueni", "Machakos" -> listOf(
                "Dry wind warning. Apply heavy mulch layers on tomato and chili fields to conserve soil moisture.",
                "Temperatures are rising! Avoid noon-time fertilizer spraying because it causes leaf scorch.",
                "Optimal conditions for sorghum and millet planting. Check on drip tubes for blockages."
            )
            "Uasin Gishu", "Trans Nzoia", "Eldoret" -> listOf(
                "Mild rain forecasted. Excellent period to introduce top dressing with CAN on maize plants (knee-high).",
                "Clean your sprayer tanks! Check safety masks before starting large-scale pesticide application.",
                "Optimal crop canopy growth. Look out for Fall Armyworm on maize leaves."
            )
            "Kisumu", "Kwale", "Mombasa" -> listOf(
                "High humidity levels. Monitor onions for Downy Mildew and garlic sectors.",
                "Warm night breezes. Great timing for organic seaweed spray or liquid manure feed.",
                "Rain predictions up. Clean all water harvesting drainage channels surrounding your greenhouse."
            )
            else -> listOf(
                "Moderate daytime temperatures. Check soil moisture content before launching irrigating pumps.",
                "Examine crops daily for early indications of insect tunnels.",
                "Postpone crop dusting if heavy wind currents are blowing across fields."
            )
        }

        val forecastList = listOf(
            ForecastItem("Today", 21.0, "Rainy", 75),
            ForecastItem("Sun", 23.0, "Sunny Intervals", 20),
            ForecastItem("Mon", 24.0, "Cloudy", 15),
            ForecastItem("Tue", 22.0, "Showers", 45),
            ForecastItem("Wed", 21.0, "Heavy Rain", 60),
            ForecastItem("Thu", 23.0, "Sunny", 10),
            ForecastItem("Fri", 25.0, "Warm & Clear", 5)
        )

        val alert = when (county) {
            "Kitui", "Makueni" -> "Drought Advisory: High water evaporation. Mulching critical."
            "Nyandarua", "Nyeri" -> "Blight outbreak warning in Nyandarua County. Spray fungicides preemptively."
            "Uasin Gishu" -> "Fall Armyworm threat in adjacent blocks. Scan your leaves immediately."
            else -> "County check: Standard weather. Soil health monitoring advised."
        }

        return WeatherState(
            county = county,
            temp = if (county == "Machakos" || county == "Kitui") 26.5 else 21.5,
            rainChance = if (county == "Nyeri" || county == "Kirinyaga") 65.0 else 25.0,
            humidity = 72.0,
            wind = 14.0,
            uv = 5.0,
            conditionEn = if (county == "Kitui") "Sunny & Dry" else "Cloudy with Showers",
            recommendations = recs,
            forecast7Day = forecastList,
            climateAlert = alert
        )
    }

    private fun getFallbackMarketState(): MarketState {
        val prices = listOf(
            MarketPrice("White Maize", "90kg Bag", "Nairobi (Marikiti)", 3800.0, "Stable", "➡️"),
            MarketPrice("Round Tomatoes", "Crate (60kg)", "Nairobi (Marikiti)", 5400.0, "Up", "📈"),
            MarketPrice("Irish Potatoes", "50kg Bag", "Nyeri (Ruring'u)", 2800.0, "Down", "📉"),
            MarketPrice("Dry Yellow Beans", "90kg Bag", "Eldoret (Main)", 12500.0, "Up", "📈"),
            MarketPrice("Red Onions", "1kg Crates", "Mombasa (Kongowea)", 110.0, "Stable", "➡️"),
            MarketPrice("Green Peas", "50kg Bag", "Meru (Gakoromone)", 4200.0, "Up", "📈")
        )

        val listings = listOf(
            FarmerListing("Premium F1 Tomato Harvest", "12 crates available", "KES 5,000/crate", "Wanjiku N.", "Kiambu", "0722123456"),
            FarmerListing("Certified Dry Maize Grains (90kg)", "40 bags in store", "KES 3,100/bag", "Kiprop J.", "Eldoret", "0733987654"),
            FarmerListing("Organic Shangi Potatoes", "80 bags ready", "KES 2,500/bag", "Mwangi S.", "Nyandarua", "0711554433"),
            FarmerListing("Taveta Red Sweet Onions", "15 bags harvested", "KES 95/kg", "Amina A.", "Taveta", "0700112233")
        )

        return MarketState(prices, listings)
    }

    // JSON serialization utilities to assist Room compatibility

    private fun parseJsonStringArray(json: String): List<String> {
        return try {
            val arr = JSONArray(json)
            val list = mutableListOf<String>()
            for (i in 0 until arr.length()) {
                list.add(arr.getString(i))
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun parseJsonForecastArray(json: String): List<ForecastItem> {
        val list = mutableListOf<ForecastItem>()
        try {
            val arr = JSONArray(json)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    ForecastItem(
                        day = obj.getString("day"),
                        temp = obj.getDouble("temp"),
                        condition = obj.getString("condition"),
                        rainPct = obj.getInt("rainPct")
                    )
                )
            }
        } catch (_: Exception) {}
        return list.ifEmpty {
            listOf(ForecastItem("Today", 21.0, "Rainy", 70))
        }
    }

    private fun serialize7DayForecast(items: List<ForecastItem>): String {
        val arr = JSONArray()
        items.forEach {
            val obj = JSONObject()
            obj.put("day", it.day)
            obj.put("temp", it.temp)
            obj.put("condition", it.condition)
            obj.put("rainPct", it.rainPct)
            arr.put(obj)
        }
        return arr.toString()
    }

    private fun parseJsonPricesArray(json: String): List<MarketPrice> {
        val list = mutableListOf<MarketPrice>()
        try {
            val arr = JSONArray(json)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    MarketPrice(
                        commodity = obj.getString("commodity"),
                        unit = obj.getString("unit"),
                        marketName = obj.getString("marketName"),
                        avgPrice = obj.getDouble("avgPrice"),
                        trend = obj.getString("trend"),
                        trendIcon = obj.getString("trendIcon")
                    )
                )
            }
        } catch (_: Exception) {}
        return list
    }

    private fun serializeMarketPrices(prices: List<MarketPrice>): String {
        val arr = JSONArray()
        prices.forEach {
            val obj = JSONObject()
            obj.put("commodity", it.commodity)
            obj.put("unit", it.unit)
            obj.put("marketName", it.marketName)
            obj.put("avgPrice", it.avgPrice)
            obj.put("trend", it.trend)
            obj.put("trendIcon", it.trendIcon)
            arr.put(obj)
        }
        return arr.toString()
    }

    private fun parseJsonListingsArray(json: String): List<FarmerListing> {
        val list = mutableListOf<FarmerListing>()
        try {
            val arr = JSONArray(json)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    FarmerListing(
                        cropType = obj.getString("cropType"),
                        quantity = obj.getString("quantity"),
                        priceString = obj.getString("priceString"),
                        farmerName = obj.getString("farmerName"),
                        county = obj.getString("county"),
                        contactPhone = obj.getString("contactPhone"),
                        isMyListing = obj.optBoolean("isMyListing", false)
                    )
                )
            }
        } catch (_: Exception) {}
        return list
    }

    private fun serializeFarmerListings(listings: List<FarmerListing>): String {
        val arr = JSONArray()
        listings.forEach {
            val obj = JSONObject()
            obj.put("cropType", it.cropType)
            obj.put("quantity", it.quantity)
            obj.put("priceString", it.priceString)
            obj.put("farmerName", it.farmerName)
            obj.put("county", it.county)
            obj.put("contactPhone", it.contactPhone)
            obj.put("isMyListing", it.isMyListing)
            arr.put(obj)
        }
        return arr.toString()
    }

    // --- Supabase Platform Integration States ---
    private val _supabaseAuthStatus = MutableStateFlow<String>("NOT_AUTHENTICATED") // NOT_AUTHENTICATED, AUTHENTICATED, ERROR, LOADING
    val supabaseAuthStatus: StateFlow<String> = _supabaseAuthStatus.asStateFlow()

    private val _supabaseProfile = MutableStateFlow<com.example.network.SupabaseProfile?>(null)
    val supabaseProfile: StateFlow<com.example.network.SupabaseProfile?> = _supabaseProfile.asStateFlow()

    private val _supabaseFarms = MutableStateFlow<List<com.example.network.SupabaseFarm>>(emptyList())
    val supabaseFarms: StateFlow<List<com.example.network.SupabaseFarm>> = _supabaseFarms.asStateFlow()

    private val _supabaseCrops = MutableStateFlow<List<com.example.network.SupabaseCrop>>(emptyList())
    val supabaseCrops: StateFlow<List<com.example.network.SupabaseCrop>> = _supabaseCrops.asStateFlow()

    private val _supabaseTransportRequests = MutableStateFlow<List<com.example.network.SupabaseTransportRequest>>(emptyList())
    val supabaseTransportRequests: StateFlow<List<com.example.network.SupabaseTransportRequest>> = _supabaseTransportRequests.asStateFlow()

    private val _supabaseTransporters = MutableStateFlow<List<com.example.network.SupabaseTransporter>>(emptyList())
    val supabaseTransporters: StateFlow<List<com.example.network.SupabaseTransporter>> = _supabaseTransporters.asStateFlow()

    private val _supabaseSharedGroups = MutableStateFlow<List<com.example.network.SupabaseSharedDeliveryGroup>>(emptyList())
    val supabaseSharedGroups: StateFlow<List<com.example.network.SupabaseSharedDeliveryGroup>> = _supabaseSharedGroups.asStateFlow()

    private val _supabaseCommunityPosts = MutableStateFlow<List<com.example.network.SupabaseCommunityPost>>(emptyList())
    val supabaseCommunityPosts: StateFlow<List<com.example.network.SupabaseCommunityPost>> = _supabaseCommunityPosts.asStateFlow()

    fun triggerInitialSupabaseLoading() {
        syncSupabaseData()
    }

    fun syncSupabaseData() {
        viewModelScope.launch {
            if (com.example.network.SupabaseClient.isAuthenticated) {
                try {
                    val p = com.example.network.SupabaseClient.fetchProfile()
                    _supabaseProfile.value = p
                    _supabaseFarms.value = com.example.network.SupabaseClient.fetchFarms()
                    _supabaseCrops.value = com.example.network.SupabaseClient.fetchCrops()
                    _supabaseTransportRequests.value = com.example.network.SupabaseClient.fetchTransportRequests()
                    _supabaseCommunityPosts.value = com.example.network.SupabaseClient.fetchCommunityPosts()
                } catch (e: Exception) {
                    Log.e("AgroViewModel", "Supabase initial fetch failed: ${e.message}")
                }
            } else {
                // In simulated mode, load default mock lists automatically so the features immediately look amazing!
                if (com.example.network.SupabaseClient.isLocalMockMode) {
                    val p = com.example.network.SupabaseClient.fetchProfile()
                    _supabaseProfile.value = p
                    _supabaseFarms.value = com.example.network.SupabaseClient.fetchFarms()
                    _supabaseCrops.value = com.example.network.SupabaseClient.fetchCrops()
                    _supabaseTransportRequests.value = com.example.network.SupabaseClient.fetchTransportRequests()
                    _supabaseCommunityPosts.value = com.example.network.SupabaseClient.fetchCommunityPosts()
                }
            }
            // Always retrieve public list items
            try {
                _supabaseTransporters.value = com.example.network.SupabaseClient.fetchTransporters()
                _supabaseSharedGroups.value = com.example.network.SupabaseClient.fetchSharedDeliveryGroups()
            } catch (_: Exception) {}
        }
    }

    fun supabaseLogin(email: String, pword: String, onFinished: (Boolean) -> Unit) {
        _supabaseAuthStatus.value = "LOADING"
        viewModelScope.launch {
            val success = com.example.network.SupabaseClient.signIn(email, pword)
            if (success) {
                _supabaseAuthStatus.value = "AUTHENTICATED"
                syncSupabaseData()
                onFinished(true)
            } else {
                _supabaseAuthStatus.value = "ERROR"
                onFinished(false)
            }
        }
    }

    fun supabaseRegister(email: String, pword: String, displayName: String?, onFinished: (Boolean) -> Unit) {
        _supabaseAuthStatus.value = "LOADING"
        viewModelScope.launch {
            val success = com.example.network.SupabaseClient.signUp(email, pword, displayName)
            if (success) {
                _supabaseAuthStatus.value = "AUTHENTICATED"
                syncSupabaseData()
                onFinished(true)
            } else {
                _supabaseAuthStatus.value = "ERROR"
                onFinished(false)
            }
        }
    }

    fun supabaseLogout() {
        com.example.network.SupabaseClient.logout()
        _supabaseAuthStatus.value = "NOT_AUTHENTICATED"
        _supabaseProfile.value = null
        _supabaseFarms.value = emptyList()
        _supabaseCrops.value = emptyList()
        _supabaseTransportRequests.value = emptyList()
    }

    fun addSupabaseFarm(name: String, location: String, area: String, county: String) {
        val uid = com.example.network.SupabaseClient.currentUserId ?: "simulated-uid-3344"
        val farm = com.example.network.SupabaseFarm(
            id = java.util.UUID.randomUUID().toString(),
            farmerId = uid,
            name = name,
            location = location,
            totalArea = area,
            county = county,
            subCounty = ""
        )
        viewModelScope.launch {
            val success = com.example.network.SupabaseClient.addFarm(farm)
            if (success) {
                _supabaseFarms.value = com.example.network.SupabaseClient.fetchFarms()
            }
        }
    }

    fun addSupabaseCrop(farmId: String?, name: String, variety: String, plantingDate: String, expectedHarvest: String) {
        val uid = com.example.network.SupabaseClient.currentUserId ?: "simulated-uid-3344"
        val crop = com.example.network.SupabaseCrop(
            id = java.util.UUID.randomUUID().toString(),
            farmId = farmId,
            farmerId = uid,
            name = name,
            variety = variety,
            plantingDate = plantingDate,
            expectedHarvest = expectedHarvest,
            status = "planted",
            healthScore = 100.0
        )
        viewModelScope.launch {
            val success = com.example.network.SupabaseClient.addCrop(crop)
            if (success) {
                _supabaseCrops.value = com.example.network.SupabaseClient.fetchCrops()
            }
        }
    }

    fun raiseSupabaseTransportRequest(produceType: String, quantity: Double, unit: String, pickup: String, dest: String, date: String, urgency: String, notes: String) {
        val uid = com.example.network.SupabaseClient.currentUserId ?: "simulated-uid-3344"
        val tr = com.example.network.SupabaseTransportRequest(
            id = java.util.UUID.randomUUID().toString(),
            farmerId = uid,
            produceType = produceType,
            quantity = quantity,
            unit = unit,
            pickupLocation = pickup,
            destination = dest,
            preferredDate = date,
            urgency = urgency,
            status = "pending",
            notes = notes
        )
        viewModelScope.launch {
            val success = com.example.network.SupabaseClient.addTransportRequest(tr)
            if (success) {
                _supabaseTransportRequests.value = com.example.network.SupabaseClient.fetchTransportRequests()
            }
        }
    }

    fun addSupabaseCommunityPost(content: String, category: String) {
        val uid = com.example.network.SupabaseClient.currentUserId ?: "simulated-uid-3344"
        val name = _supabaseProfile.value?.displayName ?: "F. Mkulima"
        val post = com.example.network.SupabaseCommunityPost(
            id = java.util.UUID.randomUUID().toString(),
            authorId = uid,
            authorName = name,
            content = content,
            category = category,
            createdAt = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US).format(java.util.Date())
        )
        viewModelScope.launch {
            val success = com.example.network.SupabaseClient.addCommunityPost(post)
            if (success) {
                _supabaseCommunityPosts.value = com.example.network.SupabaseClient.fetchCommunityPosts()
            }
        }
    }

    fun toggleSupabaseLike(postId: String) {
        viewModelScope.launch {
            val success = com.example.network.SupabaseClient.toggleLike(postId)
            if (success) {
                _supabaseCommunityPosts.value = com.example.network.SupabaseClient.fetchCommunityPosts()
            }
        }
    }

    fun loadSupabaseComments(postId: String, onLoaded: (List<com.example.network.SupabaseCommunityComment>) -> Unit) {
        viewModelScope.launch {
            val list = com.example.network.SupabaseClient.fetchComments(postId)
            onLoaded(list)
        }
    }

    fun addSupabaseComment(postId: String, content: String) {
        val uid = com.example.network.SupabaseClient.currentUserId ?: "simulated-uid-3344"
        val name = _supabaseProfile.value?.displayName ?: "F. Mkulima"
        val comment = com.example.network.SupabaseCommunityComment(
            id = java.util.UUID.randomUUID().toString(),
            postId = postId,
            authorId = uid,
            authorName = name,
            content = content,
            createdAt = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US).format(java.util.Date())
        )
        viewModelScope.launch {
            val success = com.example.network.SupabaseClient.addComment(comment)
            if (success) {
                _supabaseCommunityPosts.value = com.example.network.SupabaseClient.fetchCommunityPosts()
            }
        }
    }
}

// --- Weather Data Models ---
data class WeatherState(
    val county: String,
    val temp: Double,
    val rainChance: Double,
    val humidity: Double,
    val wind: Double,
    val uv: Double,
    val conditionEn: String,
    val recommendations: List<String>,
    val forecast7Day: List<ForecastItem>,
    val climateAlert: String = "No critical alerts."
)

data class ForecastItem(
    val day: String,
    val temp: Double,
    val condition: String,
    val rainPct: Int
)

// --- Market Data Models ---
data class MarketState(
    val marketPrices: List<MarketPrice>,
    val farmerListings: List<FarmerListing>
)

data class MarketPrice(
    val commodity: String,
    val unit: String,
    val marketName: String,
    val avgPrice: Double,
    val trend: String, // "Up", "Down", "Stable"
    val trendIcon: String // "📈", "📉", "➡️"
)

data class FarmerListing(
    val cropType: String,
    val quantity: String,
    val priceString: String,
    val farmerName: String,
    val county: String,
    val contactPhone: String,
    val isMyListing: Boolean = false
)
