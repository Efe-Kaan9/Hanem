package com.smartcockpit.domain.tools

interface Tool {
    val name: String
    val description: String
    suspend fun execute(params: Map<String, Any>): ToolResult
}

data class ToolResult(
    val status: String,
    val data: Any? = null,
    val message: String? = null
)
