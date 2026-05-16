package agent.executor

import ai.koog.prompt.executor.clients.anthropic.AnthropicModels
import ai.koog.prompt.executor.llms.all.simpleAnthropicExecutor
import io.github.cdimascio.dotenv.dotenv

class HaikuExecutor : ExecutorInfo {
    val dotenv = dotenv()
    override val executor = simpleAnthropicExecutor(dotenv["CLAUDE_API_KEY"])
    override val llmModel = AnthropicModels.Haiku_4_5
}