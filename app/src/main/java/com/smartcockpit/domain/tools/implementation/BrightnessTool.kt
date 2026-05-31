package com.smartcockpit.domain.tools.implementation

import com.smartcockpit.domain.tools.Tool
import com.smartcockpit.domain.tools.ToolResult
import com.smartcockpit.os.DisplayController
import javax.inject.Inject

class BrightnessTool @Inject constructor(
    private val displayController: DisplayController
) : Tool {
    override val name: String = "ADJUST_BRIGHTNESS"
    override val description: String = "Set screen brightness level (0-255)"

    override suspend fun execute(params: Map<String, Any>): ToolResult {
        val level = (params["level"] as? Number)?.toInt() ?: return ToolResult("error", message = "Level missing")
        displayController.setBrightness(level)
        return ToolResult("success", message = "Brightness set to $level")
    }
}
