package com.accesspath.domain

/** A place the camera found on a sign, for the surveyor to confirm with one tap. */
data class SignSuggestion(val type: String, val name: String)

/**
 * Turns text read from signs (by on-device text recognition) into place suggestions: washrooms, lifts, stairs,
 * ramps, entrances and exits, and named or numbered rooms. Only short sign-like lines count, so posters and
 * notices are ignored. Nothing is added without the surveyor confirming it.
 */
object SignReader {
    private const val MAX_LINE = 40

    private val washroom = Regex("""\b(toilets?|washrooms?|rest ?rooms?|lavatory|lavatories|w\.?c\.?)\b|शौचालय""", RegexOption.IGNORE_CASE)
    private val gents = Regex("""\b(gents|men|male|boys)\b|पुरुष""", RegexOption.IGNORE_CASE)
    private val ladies = Regex("""\b(ladies|women|female|girls)\b|महिला""", RegexOption.IGNORE_CASE)
    private val accessible = Regex("""\b(accessible|disabled|handicap(ped)?|divyang|wheelchair|pwd)\b|♿""", RegexOption.IGNORE_CASE)
    private val lift = Regex("""\b(lifts?|elevators?)\b|लिफ्ट""", RegexOption.IGNORE_CASE)
    private val stairs = Regex("""\b(stairs?|stair ?case|staircase|steps)\b|सीढ़ी""", RegexOption.IGNORE_CASE)
    private val ramp = Regex("""\bramps?\b|रैम्प""", RegexOption.IGNORE_CASE)
    private val exit = Regex("""\b(exit|way out|emergency exit|fire exit)\b|निकास""", RegexOption.IGNORE_CASE)
    private val entrance = Regex("""\b(entrance|entry|way in|main gate|gate( no\.?)? ?\d*|reception)\b|प्रवेश""", RegexOption.IGNORE_CASE)
    private val roomWord = Regex(
        """\b(room|rm|lab|laboratory|lecture hall|lecture theatre|lt|class ?room|cabin|office|hall|library|canteen|cafeteria|""" +
            """auditorium|seminar hall|conference|staff ?room|hod|dean|department|dept|block|wing|store|server room|pantry|""" +
            """first aid|medical room|sick room|admin|accounts|exam(ination)? (cell|hall))\b""",
        RegexOption.IGNORE_CASE
    )
    /** A bare door number such as 204, B-204, CS101 or G12. */
    private val roomNumber = Regex("""^[A-Z]{0,3}[- ]?\d{1,4}[A-Z]?$""")

    fun read(lines: List<String>): List<SignSuggestion> {
        val clean = lines.map { it.replace(Regex("""\s+"""), " ").trim() }.filter { it.length in 2..MAX_LINE }
        if (clean.isEmpty()) return emptyList()
        val all = clean.joinToString(" ")
        val out = mutableListOf<SignSuggestion>()
        // A washroom sign, or a door that only says GENTS / LADIES.
        val genderOnly = clean.any { gents.matches(it) || ladies.matches(it) }
        if (washroom.containsMatchIn(all) || genderOnly) {
            val who = when {
                accessible.containsMatchIn(all) -> "Accessible washroom"
                gents.containsMatchIn(all) && !ladies.containsMatchIn(all) -> "Washroom (Gents)"
                ladies.containsMatchIn(all) && !gents.containsMatchIn(all) -> "Washroom (Ladies)"
                else -> "Washroom"
            }
            out += SignSuggestion("WASHROOM", who)
        }
        if (lift.containsMatchIn(all)) out += SignSuggestion("ELEVATOR", "Lift")
        if (ramp.containsMatchIn(all)) out += SignSuggestion("RAMP", "Ramp")
        if (stairs.containsMatchIn(all) && !lift.containsMatchIn(all)) out += SignSuggestion("STAIRS", "Stairs")
        if (exit.containsMatchIn(all)) out += SignSuggestion("EXIT", if (Regex("emergency|fire", RegexOption.IGNORE_CASE).containsMatchIn(all)) "Emergency exit" else "Exit")
        else entrance.find(all)?.let { m -> out += SignSuggestion("ENTRANCE", titleCase(m.value)) }
        // Rooms: a line with a room word ("Lab 3", "Room 204", "Library"), or a bare door number.
        if (out.isEmpty()) {
            clean.firstOrNull { roomWord.containsMatchIn(it) }?.let { out += SignSuggestion("ROOM", titleCase(it)) }
                ?: clean.firstOrNull { roomNumber.matches(it.uppercase()) }?.let { out += SignSuggestion("ROOM", "Room ${it.uppercase()}") }
        }
        return out.distinct()
    }

    private fun titleCase(s: String): String = s.trim().split(' ').joinToString(" ") { w ->
        if (w.any(Char::isDigit) || (w.length <= 3 && w.all { it.isUpperCase() })) w
        else w.lowercase().replaceFirstChar { it.titlecase() }
    }
}
