package com.example.data.api

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

@JsonClass(generateAdapter = true)
data class Part(
    @field:Json(name = "text") val text: String? = null
)

@JsonClass(generateAdapter = true)
data class Content(
    @field:Json(name = "parts") val parts: List<Part>
)

@JsonClass(generateAdapter = true)
data class GenerationConfig(
    @field:Json(name = "temperature") val temperature: Float? = null,
    @field:Json(name = "responseMimeType") val responseMimeType: String? = null
)

@JsonClass(generateAdapter = true)
data class GenerateContentRequest(
    @field:Json(name = "contents") val contents: List<Content>,
    @field:Json(name = "generationConfig") val generationConfig: GenerationConfig? = null,
    @field:Json(name = "systemInstruction") val systemInstruction: Content? = null
)

@JsonClass(generateAdapter = true)
data class Candidate(
    @field:Json(name = "content") val content: Content
)

@JsonClass(generateAdapter = true)
data class GenerateContentResponse(
    @field:Json(name = "candidates") val candidates: List<Candidate>? = null
)

@JsonClass(generateAdapter = true)
data class TranslationResult(
    @field:Json(name = "translation") val translation: String,
    @field:Json(name = "alternatives") val alternatives: List<String>,
    @field:Json(name = "explanations") val explanations: List<String>? = emptyList()
)

interface GeminiApiService {
    @POST("v1beta/models/{model}:generateContent")
    suspend fun generateContent(
        @Path("model") model: String,
        @Query("key") apiKey: String,
        @Body request: GenerateContentRequest
    ): GenerateContentResponse
}

object RetrofitClient {
    private const val BASE_URL = "https://generativelanguage.googleapis.com/"

    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .addInterceptor(loggingInterceptor)
        .build()

    val service: GeminiApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(GeminiApiService::class.java)
    }

    val moshiInstance: Moshi get() = moshi
}

@JsonClass(generateAdapter = true)
data class GroupTranslationResult(
    @field:Json(name = "translations") val translations: Map<String, String>,
    @field:Json(name = "alternatives") val alternatives: List<String>,
    @field:Json(name = "explanations") val explanations: List<String>? = emptyList()
)

