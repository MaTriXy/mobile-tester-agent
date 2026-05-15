package agent.tool.mobile.test.utils

object UiAutomatorUtils {

    fun dumpUiHierarchy(): String {
        val dumpResult = AdbUtils.runAdb("shell", "uiautomator", "dump")
        if (dumpResult.contains("Error")) {
            return "ERROR: Failed to dump UI hierarchy: $dumpResult"
        }
        val xmlPath = "/sdcard/window_dump.xml"
        val xml = AdbUtils.runAdb("shell", "cat", xmlPath)
        if (xml.isBlank() || xml.contains("Error") || xml.startsWith("Failed"))
            return "ERROR: Failed to read UI hierarchy XML."
        return xml
    }

    fun getScreenSize(): Pair<Int, Int>? {
        val output = AdbUtils.runAdb("shell", "wm", "size")
        val match = Regex("(\\d+)x(\\d+)").find(output) ?: return null
        return match.groupValues[1].toInt() to match.groupValues[2].toInt()
    }

    /**
     * Finds UI nodes whose chosen attribute contains [text] (case-insensitive substring).
     * The text passed in MAY differ slightly from on-screen text (synonym, case, partial phrase).
     *
     * @param selectorType one of "text" | "content-desc" | "resource-id" | "any" (default).
     * @return list of matches; empty if nothing found.
     */
    fun findUiElementsByText(text: String, selectorType: String = "any"): List<UiMatchResult> {
        val xml = dumpUiHierarchy()
        if (xml.startsWith("ERROR:")) return emptyList()
        val escaped = Regex.escape(text)
        val attrClause = when (selectorType.lowercase()) {
            "text" -> "text=\"([^\"]*$escaped[^\"]*)\""
            "content-desc" -> "content-desc=\"([^\"]*$escaped[^\"]*)\""
            "resource-id" -> "resource-id=\"([^\"]*$escaped[^\"]*)\""
            else -> "(?:text=\"([^\"]*$escaped[^\"]*)\"|content-desc=\"([^\"]*$escaped[^\"]*)\"|resource-id=\"([^\"]*$escaped[^\"]*)\")"
        }
        val regex = Regex(
            "<node[^>]*$attrClause[^>]*bounds=\"\\[(\\d+),(\\d+)\\]\\[(\\d+),(\\d+)\\]\"",
            RegexOption.IGNORE_CASE
        )
        return regex.findAll(xml).toList().map { UiMatchResult(it.groupValues) }
    }

    fun tapByText(matches: List<UiMatchResult>, position: Int): String {
        if (matches.isEmpty()) return "NOT_FOUND: no UI elements to tap"
        if (position !in matches.indices) {
            return "ERROR: position $position out of bounds (${matches.size} matches)"
        }
        val groups = matches[position].groupValues
        val x1 = groups[groups.size - 4].toInt()
        val y1 = groups[groups.size - 3].toInt()
        val x2 = groups[groups.size - 2].toInt()
        val y2 = groups[groups.size - 1].toInt()
        val centerX = (x1 + x2) / 2
        val centerY = (y1 + y2) / 2
        val tapResult = AdbUtils.runAdb("shell", "input", "tap", centerX.toString(), centerY.toString())
        return when {
            tapResult.isBlank() || tapResult == "\n" -> "TAPPED: ($centerX,$centerY)"
            tapResult.contains("Error") -> "ERROR: tap failed: $tapResult"
            else -> "TAPPED: ($centerX,$centerY) output=$tapResult"
        }
    }

    fun inputTextBySelector(selector: String, text: String, selectorType: String = "any"): String {
        return try {
            val matches = findUiElementsByText(selector, selectorType)
            if (matches.isEmpty()) return "NOT_FOUND: no input field matches selector '$selector'"
            val tapResult = tapByText(matches, 0)
            if (tapResult.startsWith("ERROR") || tapResult.startsWith("NOT_FOUND")) {
                return tapResult
            }
            val encodedText = text.replace(" ", "%s")
            val inputResult = AdbUtils.runAdb("shell", "input", "text", encodedText)
            if (inputResult.contains("Error")) "ERROR: input text failed: $inputResult"
            else "OK: typed '$text' into '$selector'"
        } catch (e: Exception) {
            "ERROR: ${e.message}"
        }
    }

    fun tapByCoordinates(x: Int, y: Int): String {
        val tapResult = AdbUtils.runAdb("shell", "input", "tap", x.toString(), y.toString())
        return when {
            tapResult.isBlank() -> "TAPPED: ($x,$y)"
            tapResult.contains("Error") -> "ERROR: tap failed: $tapResult"
            else -> "TAPPED: ($x,$y) output=$tapResult"
        }
    }

    private fun swipeScreen(startX: Int, startY: Int, endX: Int, endY: Int, durationMs: Int): String {
        return AdbUtils.runAdb(
            "shell", "input", "swipe",
            startX.toString(), startY.toString(), endX.toString(), endY.toString(), durationMs.toString()
        )
    }

    /**
     * Vertical scroll. Adapts start coordinates to actual screen size.
     * Positive distance = scroll up (swipe up); negative = scroll down.
     */
    fun scrollScreenVertically(distance: Int = 1000, durationMs: Int = 300): String {
        val (width, height) = getScreenSize() ?: (1080 to 1920)
        val startX = width / 2
        val startY = if (distance > 0) (height * 0.75).toInt() else (height * 0.25).toInt()
        val endY = (startY - distance).coerceIn(0, height)
        val result = swipeScreen(startX, startY, startX, endY, durationMs)
        return if (result.contains("Error")) "ERROR: vertical scroll failed: $result"
        else "OK: scrolled vertically distance=$distance"
    }

    /**
     * Horizontal scroll. Adapts start coordinates to actual screen size.
     * Positive distance = scroll right; negative = scroll left.
     */
    fun scrollScreenHorizontally(distance: Int = 1000, durationMs: Int = 300): String {
        val (width, height) = getScreenSize() ?: (1080 to 1920)
        val startY = height / 2
        val startX = if (distance > 0) (width * 0.25).toInt() else (width * 0.75).toInt()
        val endX = (startX + distance).coerceIn(0, width)
        val result = swipeScreen(startX, startY, endX, startY, durationMs)
        return if (result.contains("Error")) "ERROR: horizontal scroll failed: $result"
        else "OK: scrolled horizontally distance=$distance"
    }
}
