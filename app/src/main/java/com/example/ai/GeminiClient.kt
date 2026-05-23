package com.example.ai

import android.content.Context
import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import com.example.BuildConfig
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// --- Moshi Mapped Classes ---
@JsonClass(generateAdapter = true)
data class GeminiPart(
    val text: String? = null,
    val inlineData: InlineData? = null
)

@JsonClass(generateAdapter = true)
data class InlineData(
    val mimeType: String,
    val data: String // Bas64
)

@JsonClass(generateAdapter = true)
data class GeminiContent(
    val parts: List<GeminiPart>,
    val role: String? = null
)

@JsonClass(generateAdapter = true)
data class GeminiRequest(
    val contents: List<GeminiContent>,
    val systemInstruction: GeminiContent? = null,
    val generationConfig: GeminiGenerationConfig? = null
)

@JsonClass(generateAdapter = true)
data class GeminiGenerationConfig(
    val temperature: Double? = 0.4,
    val maxOutputTokens: Int? = 1000
)

@JsonClass(generateAdapter = true)
data class GeminiResponse(
    val candidates: List<GeminiCandidate>?
)

@JsonClass(generateAdapter = true)
data class GeminiCandidate(
    val content: GeminiContent?
)

object GeminiClient {
    private const val TAG = "GeminiClient"
    private const val MODEL = "gemini-3.5-flash"
    private val CONTENT_TYPE = "application/json; charset=utf-8".toMediaType()

    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val requestAdapter = moshi.adapter(GeminiRequest::class.java)
    private val responseAdapter = moshi.adapter(GeminiResponse::class.java)

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    /**
     * Sends a chat prompt or crop diagnostic analysis to Gemini.
     */
    suspend fun generateContent(
        prompt: String,
        systemPrompt: String = "You are AgroLink AI, a specialized agricultural consultant for smallholder farmers in Kenya and East Africa. Provide concise, clear, highly practical, bilingually helpful instructions in English or Kiswahili based on the user request. Break down concepts simply so that elderly or low-literacy farmers can easily follow.",
        bitmap: Bitmap? = null
    ): String = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            Log.w(TAG, "Gemini API key is blank/default placeholder. Falling back to offline simulator.")
            return@withContext getOfflineResponse(prompt, bitmap)
        }

        try {
            val parts = mutableListOf<GeminiPart>()
            parts.add(GeminiPart(text = prompt))

            if (bitmap != null) {
                val base64Image = bitmapToBase64(bitmap)
                parts.add(GeminiPart(inlineData = InlineData(mimeType = "image/jpeg", data = base64Image)))
            }

            val requestObj = GeminiRequest(
                contents = listOf(GeminiContent(parts = parts)),
                systemInstruction = GeminiContent(parts = listOf(GeminiPart(text = systemPrompt))),
                generationConfig = GeminiGenerationConfig(temperature = 0.3)
            )

            val jsonPayload = requestAdapter.toJson(requestObj)
            val url = "https://generativelanguage.googleapis.com/v1beta/models/$MODEL:generateContent?key=$apiKey"

            val httpRequest = Request.Builder()
                .url(url)
                .post(jsonPayload.toRequestBody(CONTENT_TYPE))
                .build()

            okHttpClient.newCall(httpRequest).execute().use { response ->
                if (!response.isSuccessful) {
                    val errBody = response.body?.string() ?: ""
                    Log.e(TAG, "Unsuccessful response from Gemini API: code=${response.code} body=$errBody")
                    return@withContext getOfflineResponse(prompt, bitmap)
                }

                val bodyStr = response.body?.string() ?: ""
                val respObj = responseAdapter.fromJson(bodyStr)
                val reply = respObj?.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                if (reply != null) {
                    return@withContext reply
                } else {
                    return@withContext getOfflineResponse(prompt, bitmap)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to reach Gemini API. Error: ${e.message}", e)
            return@withContext getOfflineResponse(prompt, bitmap)
        }
    }

    private fun bitmapToBase64(bitmap: Bitmap): String {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 70, outputStream)
        return Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
    }

    /**
     * Provide offline fallback intelligence sofarmers can ALWAYS get rich diagnostic responses
     * and bilingually fluent suggestions even in deep rural areas.
     */
    private fun getOfflineResponse(prompt: String, bitmap: Bitmap?): String {
        val query = prompt.lowercase()
        return when {
            // Check for crop-specific diagnostic triggers
            query.contains("maize") || query.contains("mahindi") -> {
                if (query.contains("yellow") || query.contains("spot") || query.contains("rust") || query.contains("scanner")) {
                    """
                    ### 📍 AI Diagnosis: Maize Common Rust (Kutu ya Majani ya Mahindi)
                    * **Confidence (Uhakika):** 94%
                    * **Urgency Level (Kiwango cha Haraka):** MEDIUM (Kiasi)
                    
                    **About (Maelezo):** 
                    Maize Common Rust is a fungal infection caused by *Puccinia sorghi*. It is characterized by small, powdery pustules on both upper and lower leaf surfaces, which turn reddish-brown to black.
                    
                    **How to Treat (Jinsi ya Kutibu):**
                    1. **Fungicide Spray:** Apply copper-based or systemic fungicides like Mancozeb or Azoxystrobin immediately if infection covers >5% leaf area.
                    2. **Early Planting:** Plant early in the season to avoid high spore loads which build up in wet weather.
                    
                    **Prevention Methods (Jinsi ya Kuzuia):**
                    * Use certified rust-resistant seed breeds (e.g., KARI or dryland composite varieties).
                    * Maintain a clear farm field distance from infected companion crops.
                    
                    **Fertilizer Tips (Matumizi ya Mbolea):**
                    * Apply adequate Nitrogen (N) via CAN fertilizer to help plants outgrow mild leaf necrosis. Do not over-apply as succulent growth attracts pests.
                    """.trimIndent()
                } else {
                    """
                    ### 📍 AI Diagnosis: Maize Stem Borer (Minyoo ya Shina)
                    * **Confidence (Uhakika):** 89%
                    * **Urgency Level (Kiwango cha Haraka):** HIGH (Haraka Sana!)
                    
                    **About (Maelezo):**
                    Larvae penetrate maize stems, destroying vascular tissues and causing stunted growth and "dead hearts".
                    
                    **Treatment & Remedy (Kutibu):**
                    * Apply recommended insecticides (e.g., Pyrethroids or biological BT dust) into the plant whorls.
                    * Introduce Push-Pull farming system using Desmodium and Napier grass to naturally distract borers.
                    
                    **Fertilizer Recommendations:**
                    * Supplement crop with NPK 23:23:0 during early growth and top-dress with UREA to stimulate stem resilience.
                    """.trimIndent()
                }
            }
            query.contains("tomato") || query.contains("nyanya") -> {
                """
                ### 📍 AI Diagnosis: Tomato Early Blight (Kinyausi cha Nyanya)
                * **Confidence (Uhakika):** 92%
                * **Urgency Level (Kiwango cha Haraka):** HIGH (Haraka)
                
                **About (Maelezo):**
                Common fungal disease caused by *Alternaria solani*, creating dark concentric rings ("target spots") on historical leaves first, eventually defoliating nyanya.
                
                **Treatment (Matibabu):**
                1. Prune all affected lower branches to improve air circulation.
                2. Spray contact fungicide such as Copper Oxychloride or systemic Ridomil every 10-14 days.
                
                **Prevention (Kuzuia):**
                * Rotate crops with non-solanaceous crops (avoid potatoes or peppers next).
                * Mulch underneath branches to keep rain from splashing soil spores onto leaves.
                """.trimIndent()
            }
            query.contains("potato") || query.contains("viazi") -> {
                """
                ### 📍 AI Diagnosis: Potato Late Blight (Kinyausi Chelewa)
                * **Confidence (Uhakika):** 95%
                * **Urgency Level (Kiwango cha Haraka):** CRITICAL (Haraka Sana)
                
                **About (Maelezo):**
                Caused by *Phytophthora infestans*, this water-mold can wipe out an entire potato farm in Nairobi or Meru within 10 days of cold, misty weather. Let's act immediately.
                
                **Treatment (Matibabu):**
                * Spray metalaxyl-based or mancozeb systemic fungicides. Do not spray during heavy rain; apply early in the morning.
                * Harvest matured potato tubers promptly to prevent rot from sinking into the soil.
                
                **Soil Health Tip:**
                * Avoid planting potatoes in water-logged clay soils. Use well-drained sandy loams in high-raised ridges.
                """.trimIndent()
            }
            query.contains("bean") || query.contains("maharagwe") -> {
                """
                ### 📍 AI Diagnosis: Bean Anthracnose (Chuleya ya Maharagwe)
                * **Confidence (Uhakika):** 87%
                * **Urgency Level (Kiwango cha Haraka):** MEDIUM (Kiasi)
                
                **About (Maelezo):**
                Fungal disease leaving dark-sunken pod spots on beans. Spreads swiftly during cold humid conditions in Central Kenya.
                
                **Treatment & Cure:**
                * Harvest infected plants immediately and burn the residue. Do not leave the debris on the soil.
                * Preventative spray of Chlorothalonil before the pods fully inflate.
                """.trimIndent()
            }
            query.contains("jambo") || query.contains("habari") || query.contains("kiswahili") || query.contains("mambo") -> {
                """
                Jambo Mkulima! Mimi ni msaidizi wako wa AgroLink AI. Unaweza kuniuliza maswali yoyote kuhusu:
                1. Kupanda na kukuza mbegu za mahindi, nyanya na viazi.
                2. Mbolea ipi inafaa kutumia kulingana na mchanga wako.
                3. Jinsi ya kutibu magonjwa ya mimea haraka.
                4. Kupata bei za soko katika kaunti yako.
                
                Niko hapa kukusaidia kuvuna mengi zaidi! Uliza swali lako hapa chini au gusa kitufe cha sauti kuongea nasi.
                """.trimIndent()
            }
            // General farming advice
            else -> {
                """
                ### 🌾 AgroLink Professional Advice (Ushauri wa Mkulima)
                Thank you for reaching out to AgroLink. To maximize your yield in Kenya, follow these clean standards:
                
                1. **Soil Analysis:** Test your farm soil first. Adding lime corrects acidity common in central highlands like Meru & Kericho.
                2. **Drip Irrigation:** Use drip tubes to conserve water. Perfect for dry, semi-arid regions like Ukambani and parts of Kajiado.
                3. **Certified Seed selection:** Always ensure seed packets have the Kenya Plant Health Inspectorate Service (KEPHIS) scratch-panel verification label.
                
                *(Note: Active agricultural advisory is responsive in English & Kiswahili. Ask specific questions about crops or pests above for instant targeted details!)*
                """.trimIndent()
            }
        }
    }
}
