package com.narisraz.sortiedebus

import org.junit.Assert.assertEquals
import org.junit.Test

// Same signatures as the web and Swift versions: the generator must stay identical on every platform.
class GeneratorTest {
    private fun sig(g: Level) = "${g.cols}x${g.rows} c=" + g.cones.joinToString(",") { "${it.first}${it.second}" } + " " +
        g.buses.joinToString(",") { "${it.r}${it.c}${it.len}${it.dir}${it.color}${if (it.mystery) "m" else ""}${it.ice}" } +
        " q=" + g.queue.joinToString("")

    @Test fun signatures() {
        val expected = mapOf(1 to -38054352, 4 to 590730788, 7 to 1412722698, 9 to -1056493102, 12 to -1146206637, 20 to -1323705822)
        for ((lv, h) in expected) assertEquals("niveau $lv", h, sig(generateLevel(lv)).hashCode())
        assertEquals("défi", 81630199, sig(generateLevel(DAILY_LEVEL, 20261014)).hashCode())
    }
}
