package agent.model

import agent.executor.DeepSeekExecutor
import agent.executor.ExecutorInfo
import kotlinx.serialization.Serializable

@Serializable
data class MobileTesterConfig(
    var executorInfo: ExecutorInfo = DeepSeekExecutor(),
    var llmTemperature: Double = 0.0,
    var maxAgentIterations: Int = 50,
    var logTokensConsumption: Boolean = false
)