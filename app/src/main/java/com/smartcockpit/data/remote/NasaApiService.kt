package com.smartcockpit.data.remote

import com.google.gson.annotations.SerializedName
import retrofit2.http.GET
import retrofit2.http.Query

interface NasaApiService {
    @GET("planetary/apod")
    suspend fun getApod(
        @Query("api_key") apiKey: String = "DEMO_KEY",
        @Query("date") date: String? = null
    ): NasaApodResponse
}

data class NasaApodResponse(
    val title: String,
    val explanation: String,
    val url: String,
    @SerializedName("hdurl")
    val hdUrl: String?,
    @SerializedName("media_type")
    val mediaType: String,
    val date: String
)
