package com.accesspath

import com.accesspath.domain.*
import org.junit.Assert.*
import org.junit.Test

/** AI mapping: what the camera reads on signs, and the cleaned walk. */
class AiMappingTest {
    private fun types(vararg lines: String) = SignReader.read(lines.toList()).map { it.type to it.name }

    @Test fun washroomSignsBecomeWashrooms() {
        assertEquals(listOf("WASHROOM" to "Washroom (Gents)"), types("GENTS TOILET"))
        assertEquals(listOf("WASHROOM" to "Washroom (Ladies)"), types("LADIES"))
        assertEquals(listOf("WASHROOM" to "Accessible washroom"), types("Accessible Toilet", "♿"))
        assertEquals(listOf("WASHROOM" to "Washroom"), types("WASHROOM"))
    }

    @Test fun liftsStairsRampsAndExits() {
        assertEquals(listOf("ELEVATOR" to "Lift"), types("LIFT"))
        assertEquals(listOf("STAIRS" to "Stairs"), types("Staircase"))
        assertEquals(listOf("RAMP" to "Ramp"), types("Ramp →"))
        assertEquals(listOf("EXIT" to "Emergency exit"), types("EMERGENCY EXIT"))
        assertEquals(listOf("ENTRANCE" to "Main Gate"), types("MAIN GATE"))
    }

    @Test fun roomsByNameOrNumber() {
        assertEquals(listOf("ROOM" to "Lab 3"), types("LAB 3"))
        assertEquals(listOf("ROOM" to "Room B-204"), types("B-204"))
        assertEquals(listOf("ROOM" to "Library"), types("LIBRARY"))
    }

    @Test fun postersAndNoiseAreIgnored() {
        assertTrue(types("Please keep the campus clean and switch off the fans when you leave").isEmpty())
        assertTrue(types("Hello").isEmpty())
        assertTrue(types().isEmpty())
    }

    @Test fun zigZagWalkBecomesAStraightLineWithItsCorner() {
        // 20 m north with ±0.8 m GPS wobble, then 10 m east.
        val trail = (0..40).map { TrailPoint(if (it % 2 == 0) 0.8 else -0.8, -it * 0.5) } + (1..20).map { TrailPoint(it * 0.5, -20.0) }
        val kept = PathSimplifier.keep(trail)
        assertEquals(listOf(0, 40, trail.lastIndex), kept)
        // A named place in the middle is always kept.
        assertTrue(15 in PathSimplifier.keep(trail, setOf(15)))
    }
}
