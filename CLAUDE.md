# mobile-tester-agent

Agentic AI backend that drives Android UI testing via ADB. A Ktor HTTP server receives test scenarios and delegates execution to a Koog-powered AI agent that reasons step-by-step and interacts with a connected Android device or emulator.

## Architecture

```
HTTP Request (POST /run-test)
  └── Routing.kt
        └── MobileTestAgent (singleton object)
              ├── AIAgentConfig  (prompt, model, max iterations, temperature)
              ├── TestingStrategy (Koog strategy graph)
              └── ToolRegistry → MobileTestTools
                    ├── AdbUtils        (raw ADB execution)
                    ├── UiAutomatorUtils (UI element queries & interaction)
                    └── MediaUtils       (screenshot, screen recording)
```

## Key source files

| File | Role |
|------|------|
| `src/main/kotlin/agent/MobileTestAgent.kt` | Agent singleton — builds and runs the Koog `AIAgent` |
| `src/main/kotlin/agent/strategy/TestingStrategy.kt` | Koog strategy graph (LLM → tools → LLM loop with history compression) |
| `src/main/kotlin/agent/tool/mobile/test/MobileTestTools.kt` | All `@Tool` methods exposed to the LLM |
| `src/main/kotlin/agent/tool/mobile/test/utils/AdbUtils.kt` | ADB command execution helpers |
| `src/main/kotlin/agent/tool/mobile/test/utils/UiAutomatorUtils.kt` | UI hierarchy parsing and element interaction |
| `src/main/kotlin/agent/tool/mobile/test/utils/MediaUtils.kt` | Screenshot and screen-recording capture |
| `src/main/kotlin/agent/model/MobileTesterConfig.kt` | Runtime config (executor, temperature, iterations) |
| `src/main/kotlin/agent/executor/` | LLM executor implementations (Gemini, OpenRouter, Ollama Llama, Ollama Gwen) |
| `src/main/kotlin/server/Routing.kt` | `POST /run-test` and `POST /config` endpoints |
| `src/main/kotlin/server/Application.kt` | Ktor entry point |

## API endpoints

### `POST /run-test`
Runs a test scenario on the connected Android device.

```json
{
  "goal": "Log in and navigate to the profile screen",
  "steps": [
    "Tap the login button",
    "Enter username 'test@example.com'",
    "Enter password '123456'",
    "Tap submit",
    "Verify profile screen is visible"
  ]
}
```

### `POST /config`
Updates the agent configuration at runtime (no restart needed).

```json
{
  "executorType": "GEMINI",
  "llmTemperature": 0.0,
  "maxAgentIterations": 50,
  "logTokensConsumption": false
}
```

Swagger UI is available at `/swagger`.

## LLM executors

The active executor is set via `POST /config`. Available implementations in `agent/executor/`:

| Class | Model | Env var required |
|-------|-------|------------------|
| `GeminiExecutor` | `gemini-2.0-flash` | `GEMINI_API_KEY` |
| `OpenRouterExecutor` | configurable | `OPENROUTER_API_KEY` |
| `OllamaLlamaExecutor` | Llama (local Ollama) | — |
| `OllamaGwenExecutor` | Gwen (local Ollama) | — |

Default executor is `GeminiExecutor`.

## Environment variables

Copy `.env.example` to `.env` (loaded automatically via `dotenv-kotlin`):

```
GEMINI_API_KEY=your_key_here
OPENROUTER_API_KEY=your_key_here   # optional
```

## Running locally

Requires a connected Android device or running emulator visible to ADB.

```bash
# start the server (port 8080 by default)
./gradlew run

# verify ADB device is detected
adb devices
```

Use the IntelliJ run configuration **mobile-tester-agent** (Gradle `run` task).

## Testing strategy (`TestingStrategy.kt`)

The Koog strategy graph follows this loop:

1. `nodeCallLLM` — ask the LLM for the next tool call(s)
2. `nodeExecuteToolMultiple` — execute tools in parallel
3. If token usage > 1000 → `nodeCompressHistory` before sending results
4. `nodeSendToolResultMultiple` — send results back to the LLM
5. Repeat until LLM emits an assistant message (finish)

## MobileTestTools — available tools

| Tool | Description |
|------|-------------|
| `startTestingScenario(appName)` | Connect device and open app — **called once, first** |
| `findUiElementsByText(text)` | Query UI hierarchy for matching elements |
| `tap(text, position)` | Tap a UI element by text/content-desc/resource-id |
| `scrollVertically(distance, durationMs)` | Vertical scroll (positive = up) |
| `scrollHorizontally(distance, durationMs)` | Horizontal scroll (positive = right) |
| `inputText(selector, text)` | Type text into a field |
| `goBack()` | Press Android back button |
| `hideKeyboard()` | Dismiss the on-screen keyboard |
| `takeScreenshot(goalName)` | Capture and pull screenshot |
| `startScreenRecording()` | Start video recording |
| `stopScreenRecording()` | Stop and pull video |
| `connectDevice()` | Connect/reconnect ADB device |
| `deviceInformation()` | Dump device info (model, OS, battery…) |
| `closeApp()` | Force-stop foreground app — **called once, last** |

## Adding a new tool

1. Add a method to `MobileTestTools` annotated with `@Tool` and `@LLMDescription`.
2. The method is automatically registered via `tools(MobileTestTools())` in `MobileTestAgent`.
3. No changes to `ToolRegistry` or strategy are required.

## Adding a new LLM executor

1. Create a class in `agent/executor/` implementing `ExecutorInfo`.
2. Wire it into the `POST /config` deserialization in `MobileTesterConfigAPI`.

## Dependencies

- **Ktor 3.1.3** — HTTP server (Netty engine)
- **Koog agents** — Koog.ai agentic framework
- **dotenv-kotlin** — `.env` file loading
- **kotlinx.serialization** — JSON (de)serialization
- **Logback** — logging
- **JDK 21** (Amazon Corretto 21 on this machine)
- **Gradle 8.11.1**
