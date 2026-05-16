package agent.tool.mobile.test.utils

import kotlinx.serialization.Serializable

@Serializable
data class UiMatchResult(
    val value: String,
    val cx: Int,
    val cy: Int
)
