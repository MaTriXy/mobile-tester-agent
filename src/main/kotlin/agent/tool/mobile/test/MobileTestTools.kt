package agent.tool.mobile.test

import agent.tool.mobile.test.utils.AdbUtils
import agent.tool.mobile.test.utils.UiAutomatorUtils
import agent.tool.mobile.test.utils.UiMatchResult
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.agents.core.tools.reflect.ToolSet

/**
 * All tools return strings prefixed with a STATUS token the agent can pattern-match:
 *   OK | TAPPED | VISIBLE | NOT_VISIBLE | NOT_FOUND | AMBIGUOUS | ERROR | TIMEOUT
 */
class MobileTestTools : ToolSet {

    @Tool
    @LLMDescription(
        "Connect the device/emulator and optionally launch an app. Call ONCE as the first action. " +
                "If appPackage is provided, launches that package via monkey. " +
                "Returns 'OK: ...' or 'ERROR: ...'."
    )
    fun startTestingScenario(
        @LLMDescription("Optional Android package name to launch, e.g. com.android.settings.")
        appPackage: String = ""
    ): String {
        val connect = AdbUtils.connectDevice()
        if (connect.contains("No devices") || connect.startsWith("Failed") || connect.startsWith("Error")) {
            return "ERROR: $connect"
        }
        if (appPackage.isNotBlank()) {
            val launch = AdbUtils.runAdb("shell", "monkey", "-p", appPackage, "-c", "android.intent.category.LAUNCHER", "1")
            if (launch.contains("Error") || launch.contains("No activities")) {
                return "ERROR: connected but failed to launch '$appPackage': $launch"
            }
            return "OK: connected; launched $appPackage"
        }
        return "OK: $connect"
    }

    @Tool
    @LLMDescription(
        "Find UI elements whose text, content-desc, or resource-id contains the given string (case-insensitive, partial). " +
                "Use when you need to inspect candidates before tapping, or to verify presence. " +
                "Search text may differ slightly from on-screen text (case, partial, synonym) — try variations if empty. " +
                "Returns a list of matches (empty list = not found)."
    )
    fun findUiElementsByText(
        @LLMDescription("Text fragment to search for (case-insensitive substring match).")
        text: String,
        @LLMDescription("Attribute to filter on: 'text', 'content-desc', 'resource-id', or 'any' (default).")
        selectorType: String = "any"
    ): List<UiMatchResult> {
        return UiAutomatorUtils.findUiElementsByText(text, selectorType)
    }

    @Tool
    @LLMDescription(
        "Tap a UI element by selector. Preferred over tapByCoordinates. " +
                "Returns 'TAPPED: (x,y)' on success, 'NOT_FOUND: ...' if no match, " +
                "'AMBIGUOUS: <n> matches — retry with position=<i>' if multiple matches and position is out of range. " +
                "When ambiguous, retry with position=1, 2, ... — do not re-search with different text."
    )
    fun tap(
        @LLMDescription("Selector value (text, content-desc, or resource-id fragment).")
        text: String,
        @LLMDescription("Attribute to match on: 'text', 'content-desc', 'resource-id', or 'any' (default).")
        selectorType: String = "any",
        @LLMDescription("0-based index when multiple elements match. Default 0 = first match.")
        position: Int = 0
    ): String {
        val matches = UiAutomatorUtils.findUiElementsByText(text, selectorType)
        return when {
            matches.isEmpty() -> "NOT_FOUND: no element matches '$text' (selectorType=$selectorType)"
            position !in matches.indices && matches.size > 1 ->
                "AMBIGUOUS: ${matches.size} matches for '$text' — retry with position in 0..${matches.size - 1}"
            else -> UiAutomatorUtils.tapByText(matches, position.coerceIn(0, matches.size - 1))
        }
    }

    @Tool
    @LLMDescription(
        "Tap exact screen coordinates. Use ONLY as a fallback when selector-based tap cannot find the element. " +
                "Returns 'TAPPED: (x,y)' or 'ERROR: ...'."
    )
    fun tapByCoordinates(
        @LLMDescription("X coordinate in pixels.") x: Int,
        @LLMDescription("Y coordinate in pixels.") y: Int
    ): String = UiAutomatorUtils.tapByCoordinates(x, y)

    @Tool
    @LLMDescription(
        "Verify a UI element is visible on the current screen. Call after an action to confirm the expected state. " +
                "Returns 'VISIBLE: <n> match(es)' or 'NOT_VISIBLE: ...'. " +
                "If NOT_VISIBLE, consider scrolling or waiting before declaring the step failed."
    )
    fun verifyElementVisible(
        @LLMDescription("Text fragment expected on screen.") text: String,
        @LLMDescription("Attribute to match on: 'text', 'content-desc', 'resource-id', or 'any' (default).")
        selectorType: String = "any"
    ): String {
        val matches = UiAutomatorUtils.findUiElementsByText(text, selectorType)
        return if (matches.isEmpty()) "NOT_VISIBLE: '$text' not on screen"
        else "VISIBLE: ${matches.size} match(es) for '$text'"
    }

    @Tool
    @LLMDescription(
        "Verify a UI element is NOT visible. Use to confirm a screen transition has occurred (the old element is gone). " +
                "Returns 'OK: gone' or 'NOT_VISIBLE: still present'."
    )
    fun verifyElementNotVisible(
        @LLMDescription("Text fragment that should NOT be on screen.") text: String,
        @LLMDescription("Attribute to match on: 'text', 'content-desc', 'resource-id', or 'any' (default).")
        selectorType: String = "any"
    ): String {
        val matches = UiAutomatorUtils.findUiElementsByText(text, selectorType)
        return if (matches.isEmpty()) "OK: '$text' is not visible (as expected)"
        else "NOT_VISIBLE: '$text' is still visible (${matches.size} match(es))"
    }

    @Tool
    @LLMDescription(
        "Pause execution for the given milliseconds. Use after navigation, animations, or async loads (typically 300–1500ms). " +
                "Bounded 50..10000. Returns 'OK: waited <ms>ms'."
    )
    suspend fun wait(
        @LLMDescription("Milliseconds to sleep (50..10000).") ms: Int
    ): String {
        val bounded = ms.coerceIn(50, 10000)
        kotlinx.coroutines.delay(bounded.toLong())
        return "OK: waited ${bounded}ms"
    }

    @Tool
    @LLMDescription(
        "Dump the raw UI hierarchy XML of the current screen. Use ONLY when stuck — prefer findUiElementsByText for targeted queries. " +
                "Truncated to ~6KB. Returns the XML string or 'ERROR: ...'."
    )
    fun getScreenDump(): String {
        val xml = UiAutomatorUtils.dumpUiHierarchy()
        if (xml.startsWith("ERROR:")) return xml
        return if (xml.length > 6000) xml.take(6000) + "\n... [truncated, use findUiElementsByText for targeted search]"
        else xml
    }

    @Tool
    @LLMDescription(
        "Get the device screen size in pixels. Returns 'OK: <width>x<height>' or 'ERROR: ...'. " +
                "Useful before computing scroll distances on tablets."
    )
    fun getScreenSize(): String {
        val size = UiAutomatorUtils.getScreenSize() ?: return "ERROR: could not read screen size"
        return "OK: ${size.first}x${size.second}"
    }

    @Tool
    @LLMDescription(
        "Swipe up ~70% of screen height (reveals content below). Adaptive to device size. " +
                "Prefer this over scrollVertically for normal scrolling."
    )
    fun swipeUp(): String {
        val (w, h) = UiAutomatorUtils.getScreenSize() ?: (1080 to 1920)
        return UiAutomatorUtils.scrollScreenVertically(distance = (h * 0.6).toInt(), durationMs = 300)
            .let { if (it.startsWith("OK")) "OK: swiped up on ${w}x${h}" else it }
    }

    @Tool
    @LLMDescription(
        "Swipe down ~70% of screen height (reveals content above). Adaptive to device size. " +
                "Prefer this over scrollVertically for normal scrolling."
    )
    fun swipeDown(): String {
        val (w, h) = UiAutomatorUtils.getScreenSize() ?: (1080 to 1920)
        return UiAutomatorUtils.scrollScreenVertically(distance = -(h * 0.6).toInt(), durationMs = 300)
            .let { if (it.startsWith("OK")) "OK: swiped down on ${w}x${h}" else it }
    }

    @Tool
    @LLMDescription(
        "Precise vertical scroll by pixel distance. Use swipeUp/swipeDown for normal scrolling. " +
                "Positive distance = swipe up (scroll content up); negative = swipe down."
    )
    fun scrollVertically(
        @LLMDescription("Pixels. Positive = up, negative = down.") distance: Int = 1000,
        @LLMDescription("Swipe duration in ms.") durationMs: Int = 300
    ): String = UiAutomatorUtils.scrollScreenVertically(distance, durationMs)

    @Tool
    @LLMDescription(
        "Precise horizontal scroll by pixel distance. Positive distance = swipe right; negative = swipe left."
    )
    fun scrollHorizontally(
        @LLMDescription("Pixels. Positive = right, negative = left.") distance: Int = 1000,
        @LLMDescription("Swipe duration in ms.") durationMs: Int = 300
    ): String = UiAutomatorUtils.scrollScreenHorizontally(distance, durationMs)

    @Tool
    @LLMDescription(
        "Type text into an input field identified by selector. Auto-hides the keyboard after typing. " +
                "Returns 'OK: typed ...', 'NOT_FOUND: ...', or 'ERROR: ...'."
    )
    fun inputText(
        @LLMDescription("Selector matching the input field's text, hint (content-desc), or resource-id.")
        fieldSelector: String,
        @LLMDescription("Text to type.")
        text: String,
        @LLMDescription("Attribute to match on: 'text', 'content-desc', 'resource-id', or 'any' (default).")
        selectorType: String = "any"
    ): String {
        val result = UiAutomatorUtils.inputTextBySelector(fieldSelector, text, selectorType)
        if (result.startsWith("OK")) hideKeyboard()
        return result
    }

    @Tool
    @LLMDescription("Press the Android back button. Returns 'OK: ...' or 'ERROR: ...'.")
    fun goBack(): String {
        val result = AdbUtils.runAdb("shell", "input", "keyevent", "4")
        return if (result.contains("Error")) "ERROR: back failed: $result" else "OK: pressed back"
    }

    @Tool
    @LLMDescription(
        "Hide the on-screen keyboard. inputText auto-hides, so call this only after manual key events. " +
                "Returns 'OK: ...' or 'ERROR: ...'."
    )
    fun hideKeyboard(): String {
        var result = AdbUtils.runAdb("shell", "input", "text", "\"\"")
        if (result.contains("Error") || result.isBlank()) {
            result = AdbUtils.runAdb("shell", "input", "keyevent", "111")
            if (result.contains("Error") || result.isBlank()) {
                result = AdbUtils.runAdb("shell", "input", "keyevent", "4")
            }
        }
        return if (result.contains("Error")) "ERROR: hide keyboard failed: $result" else "OK: keyboard hidden"
    }

    @Tool
    @LLMDescription(
        "Launch an Android app by package name (e.g. com.android.settings). " +
                "Use mid-test when scenario switches apps. Returns 'OK: ...' or 'ERROR: ...'."
    )
    fun launchAppByPackage(
        @LLMDescription("Android package name, e.g. com.android.settings.") packageName: String
    ): String {
        val result = AdbUtils.runAdb(
            "shell", "monkey", "-p", packageName, "-c", "android.intent.category.LAUNCHER", "1"
        )
        return if (result.contains("Error") || result.contains("No activities")) "ERROR: launch failed: $result"
        else "OK: launched $packageName"
    }

    @Tool
    @LLMDescription(
        "Get detailed device info (manufacturer, model, Android version, SDK, battery, IP). " +
                "Returns a formatted string or 'ERROR: ...'."
    )
    fun deviceInformation(): String = AdbUtils.deviceInformation()

    @Tool
    @LLMDescription(
        "Force-stop the current foreground app. Call ONCE as the final action of the test scenario. " +
                "Returns 'OK: ...' or 'ERROR: ...'."
    )
    fun closeApp(): String {
        val result = AdbUtils.closeCurrentApp()
        return if (result.startsWith("Failed") || result.startsWith("Error")) "ERROR: $result"
        else "OK: $result"
    }
}
