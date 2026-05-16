package agent.tool.mobile.test

import agent.tool.mobile.test.utils.MediaUtils
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.agents.core.tools.reflect.ToolSet

class ReportingTools : ToolSet {

    @Tool
    @LLMDescription(
        "Take a screenshot of the current screen and pull it to a remote path. " +
                "`goalName` is the test goal name. " +
                "Returns the local file path or error message. "
    )
    suspend fun takeScreenshot(
        @LLMDescription("The name of the goal for which the screenshot is being taken.")
        goalName: String
    ): String {
        return MediaUtils.takeScreenshot(goalName)
    }

    @Tool
    @LLMDescription(
        "Start recording a video of the device screen. " +
                "This will record until you call stopScreenRecording. " +
                "Returns a message indicating recording has started or an error."
    )
    suspend fun startScreenRecording(): String {
        return MediaUtils.startScreenRecording()
    }

    @Tool
    @LLMDescription(
        "Stop the ongoing screen recording, pull the video to the local machine, " +
                "and return the local file path or error message."
    )
    suspend fun stopScreenRecording(): String {
        return MediaUtils.stopScreenRecording()
    }
}
