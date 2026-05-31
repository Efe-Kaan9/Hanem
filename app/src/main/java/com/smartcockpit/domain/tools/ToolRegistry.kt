package com.smartcockpit.domain.tools

import com.smartcockpit.domain.tools.implementation.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ToolRegistry @Inject constructor(
    private val weatherTool: WeatherTool,
    private val brightnessTool: BrightnessTool
) {
    private val tools = listOf(
        weatherTool,
        brightnessTool
    )

    fun getTools(): List<Tool> = tools

    fun getTool(name: String): Tool? = tools.find { it.name == name }
}
