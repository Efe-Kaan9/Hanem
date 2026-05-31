package com.smartcockpit

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.smartcockpit.data.remote.NasaApiService
import com.smartcockpit.data.remote.PrayerApiService
import com.smartcockpit.data.remote.WeatherApiService
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * Local JUnit 4 Tests for AImbient Backend Modules.
 * Verifies real network connectivity and JSON parsing logic.
 */
class DashboardModulesTest {

    private fun createRetrofit(baseUrl: String): Retrofit {
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    @Test
    fun testNasaApodApiReturnsValidData() = runTest {
        val retrofit = createRetrofit("https://api.nasa.gov/")
        val service = retrofit.create(NasaApiService::class.java)

        val response = service.getApod(apiKey = "DEMO_KEY")
        
        assertNotNull("Response body should not be null", response)
        assertTrue("Title should not be empty", response.title.isNotBlank())
        assertTrue("URL should not be empty", response.url.isNotBlank())
        assertTrue("Media Type should not be empty", response.mediaType.isNotBlank())
        println("NASA APOD Test Success: Fetched '${response.title}'")
    }

    @Test
    fun testPrayerTimesApiReturnsDiyanetTimes() = runTest {
        val retrofit = createRetrofit("https://api.aladhan.com/")
        val service = retrofit.create(PrayerApiService::class.java)

        val response = service.getPrayerTimes(city = "Izmir", country = "Turkey", method = 13)
        
        assertNotNull("Response should not be null", response)
        val timings = response.data.timings
        assertNotNull("Fajr should exist", timings.fajr)
        assertNotNull("Sunrise should exist", timings.sunrise)
        assertNotNull("Dhuhr should exist", timings.dhuhr)
        assertNotNull("Asr should exist", timings.asr)
        assertNotNull("Maghrib should exist", timings.maghrib)
        assertNotNull("Isha should exist", timings.isha)
        println("Prayer Times Test Success: Noon in Izmir is ${timings.dhuhr}")
    }

    @Test
    fun testWeatherApiIntegration() = runTest {
        val retrofit = createRetrofit("https://api.open-meteo.com/")
        val service = retrofit.create(WeatherApiService::class.java)

        val response = service.getForecast(latitude = 38.4192, longitude = 27.1287) // Izmir
        
        assertNotNull("Weather response should not be null", response)
        assertNotNull("Current data should exist", response.current)
        assertNotNull("Hourly data should exist", response.hourly)
        assertNotNull("Daily data should exist", response.daily)
        
        assertTrue("Temperature should be a valid number", response.current.temperature != null)
        println("Weather API Test Success: Current temp in Izmir is ${response.current.temperature}°C")
    }

    @Test
    fun testC1PhrasesJsonParsingLogic() {
        val mockJson = """
            [
                {
                    "en": "To hit the ground running",
                    "tr": "Hızlı ve enerjik bir başlangıç yapmak"
                }
            ]
        """.trimIndent()

        val gson = Gson()
        val listType = object : TypeToken<List<Map<String, String>>>() {}.type
        val parsedList: List<Map<String, String>> = gson.fromJson(mockJson, listType)

        assertNotNull("Parsed list should not be null", parsedList)
        assertEquals("List should contain exactly 1 item", 1, parsedList.size)
        assertEquals("English phrase should match", "To hit the ground running", parsedList[0]["en"])
        assertEquals("Turkish translation should match", "Hızlı ve enerjik bir başlangıç yapmak", parsedList[0]["tr"])
        println("C1 Phrases Parsing Test Success: '${parsedList[0]["en"]}'")
    }
}
