package com.example.data.api

import android.util.Log
import com.example.BuildConfig
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.json.JSONObject
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

interface GeminiApiService {
    @POST("v1beta/models/gemini-3.5-flash:generateContent")
    suspend fun generateContent(
        @Query("key") apiKey: String,
        @Body request: GenerateContentRequest
    ): GenerateContentResponse
}

data class GenerateContentRequest(
    val contents: List<Content>
)

data class Content(
    val parts: List<Part>
)

data class Part(
    val text: String
)

data class GenerateContentResponse(
    val candidates: List<Candidate>?
)

data class Candidate(
    val content: Content?
)

object GeminiTransitEstimator {
    private const val TAG = "TransitEstimator"
    private const val BASE_URL = "https://generativelanguage.googleapis.com/"

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        })
        .build()

    private val apiService: GeminiApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(GeminiApiService::class.java)
    }

    data class TransitResult(
        val durationMinutes: Int,
        val distanceMiles: Double,
        val routeSummary: String,
        val isAIEstimated: Boolean
    )

    suspend fun estimateTransit(
        fromAddress: String,
        toAddress: String,
        mode: String
    ): TransitResult {
        // Try getting API key from BuildConfig safely
        val apiKey = try {
            BuildConfig.GEMINI_API_KEY
        } catch (e: Throwable) {
            ""
        }

        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            Log.w(TAG, "Gemini API key is not configured. Falling back to local estimate.")
            return calculateLocalEstimate(fromAddress, toAddress, mode)
        }

        val prompt = """
            Estimate the typical travel duration (as an integer in minutes) and distance (as a decimal in miles) 
            between these two locations:
            Origin: "$fromAddress"
            Destination: "$toAddress"
            Travel Mode: "$mode"
            
            Return ONLY a raw, complete JSON object matching the following structure without any other formatting:
            {
              "durationMinutes": 25,
              "distanceMiles": 12.4,
              "routeSummary": "Via Highway I-880"
            }
            Do not include any code block tick marks (such as ```json or ```). Just the pure JSON block.
        """.trimIndent()

        val request = GenerateContentRequest(
            contents = listOf(Content(parts = listOf(Part(text = prompt))))
        )

        return try {
            val response = apiService.generateContent(apiKey, request)
            val responseText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                ?: throw Exception("No response contents from Gemini")
            
            Log.d(TAG, "Gemini Response: $responseText")
            val cleaned = responseText.replace("```json", "").replace("```", "").trim()
            val json = JSONObject(cleaned)
            val duration = json.getInt("durationMinutes")
            val distance = json.getDouble("distanceMiles")
            val summary = json.optString("routeSummary", "Via local streets")

            TransitResult(
                durationMinutes = duration,
                distanceMiles = distance,
                routeSummary = summary,
                isAIEstimated = true
            )
        } catch (e: Exception) {
            Log.e(TAG, "Gemini estimate request failed: ${e.message}. Using local mock calculations.", e)
            calculateLocalEstimate(fromAddress, toAddress, mode)
        }
    }

    private fun calculateLocalEstimate(
        fromAddress: String,
        toAddress: String,
        mode: String
    ): TransitResult {
        if (fromAddress.trim().lowercase() == toAddress.trim().lowercase()) {
            return TransitResult(0, 0.0, "Same location", false)
        }

        // Fallback calculations using clean seed from hashes
        val hash = (fromAddress.hashCode() + toAddress.hashCode()).coerceAtLeast(1)
        val baseDist = ((hash % 12) + 2).toDouble() + (hash % 10) / 10.0 // 2.0 to 14.0 miles

        val (speedMph, modeSummary) = when (mode) {
            "Transit" -> Pair(18.0, "Local Bus/Transit")
            "Bicycling" -> Pair(11.0, "Dedicated Bike Path")
            "Walking" -> Pair(3.0, "Pedestrian Walkway")
            else -> Pair(40.0, "Via Public Highways") // Driving
        }

        val durationHours = baseDist / speedMph
        val durationMins = (durationHours * 60).toInt().coerceAtLeast(8)

        return TransitResult(
            durationMinutes = durationMins,
            distanceMiles = baseDist,
            routeSummary = "$modeSummary (Offline Heuristic)",
            isAIEstimated = false
        )
    }
}
