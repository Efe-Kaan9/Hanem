package com.smartcockpit.domain.tools.implementation

import com.smartcockpit.data.local.dao.WeatherDao
import com.smartcockpit.domain.tools.Tool
import com.smartcockpit.domain.tools.ToolResult
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class WeatherTool @Inject constructor(
    private val weatherDao: WeatherDao
) : Tool {
    override val name: String = "WEATHER_GET"
    override val description: String = "Get the latest cached weather"

    override suspend fun execute(params: Map<String, Any>): ToolResult {
        val weather = weatherDao.getCachedWeather().first()
        return if (weather != null) {
            ToolResult("success", data = weather)
        } else {
            ToolResult("error", message = "Weather cache is empty")
        }
    }
}
