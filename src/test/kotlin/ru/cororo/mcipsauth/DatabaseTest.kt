package ru.cororo.mcipsauth

import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DatabaseTest {
    @TempDir
    lateinit var dir: File

    private fun open() = Database(File(dir, "database.h2"))

    @Test
    fun `returns null for unknown user`() {
        open().use { assertNull(it.getForumId("Steve")) }
    }

    @Test
    fun `stores and replaces forum id`() {
        open().use { db ->
            db.setForumId("Steve", 1)
            assertEquals(1, db.getForumId("Steve"))

            db.setForumId("Steve", 2)
            assertEquals(2, db.getForumId("Steve"))
        }
    }

    @Test
    fun `removes user`() {
        open().use { db ->
            db.setForumId("Steve", 1)
            db.setForumId("Alex", 2)
            db.removeUser("Steve")

            assertNull(db.getForumId("Steve"))
            assertEquals(2, db.getForumId("Alex"))
        }
    }

    @Test
    fun `persists between connections`() {
        open().use { it.setForumId("Steve", 42) }
        open().use { assertEquals(42, it.getForumId("Steve")) }
    }

    @Test
    fun `treats names as data, not sql`() {
        open().use { db ->
            db.setForumId("Steve", 1)
            db.removeUser("' OR '1'='1")
            assertEquals(1, db.getForumId("Steve"))
        }
    }
}
