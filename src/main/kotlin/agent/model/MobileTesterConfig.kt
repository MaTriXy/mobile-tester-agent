package agent.model

import agent.executor.ExecutorInfo
import agent.executor.HaikuExecutor
import kotlinx.serialization.Serializable

@Serializable
data class MobileTesterConfig(
    var executorInfo: ExecutorInfo = HaikuExecutor(),
    var llmTemperature: Double = 0.0,
    var maxAgentIterations: Int = 50,
    var logTokensConsumption: Boolean = false
)