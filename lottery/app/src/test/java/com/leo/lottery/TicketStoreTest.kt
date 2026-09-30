package com.leo.lottery

import com.leo.lottery.core.Game
import com.leo.lottery.core.Ticket
import com.leo.lottery.data.TicketStore
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class TicketStoreTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun ticket(id: String = "a") = Ticket(
        id = id,
        game = Game.SSQ,
        createdAt = 1_700_000_000_000L,
        seed = "0123456789abcdef",
        take = 2,
        zone1 = listOf(1, 5, 9, 14, 22, 31),
        zone2 = listOf(7),
        targetIssue = "2026114",
    )

    @Test
    fun `roundtrip preserves all fields`() {
        val file = File(tmp.root, "tickets.json")
        val store = TicketStore(file)
        val original = listOf(ticket("a"), ticket("b"))
        store.save(original)
        val loaded = store.load()
        assertEquals(original, loaded)
    }

    @Test
    fun `load missing file returns empty`() {
        val store = TicketStore(File(tmp.root, "none.json"))
        assertTrue(store.load().isEmpty())
    }

    @Test
    fun `unknown fields ignored for forward compat`() {
        val file = File(tmp.root, "tickets.json")
        file.writeText(
            """{"tickets":[{"id":"x","game":"SSQ","createdAt":1,"seed":"f","take":1,
               "zone1":[1,2,3,4,5,6],"zone2":[1],"targetIssue":"2026001","futureField":42}]}"""
        )
        val loaded = TicketStore(file).load()
        assertEquals(1, loaded.size)
        assertEquals("x", loaded[0].id)
    }

    @Test
    fun `save overwrites atomically`() {
        val file = File(tmp.root, "tickets.json")
        val store = TicketStore(file)
        store.save(listOf(ticket("first")))
        store.save(listOf(ticket("second")))
        assertEquals("second", store.load().single().id)
        assertTrue(!File(tmp.root, "tickets.json.tmp").exists())
    }
}
