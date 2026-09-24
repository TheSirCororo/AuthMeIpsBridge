package ru.cororo.mcipsauth

import java.io.Closeable
import java.io.File
import java.sql.Connection
import java.sql.DriverManager

/**
 * Maps Minecraft usernames to IPS member ids. Methods are synchronized because they are called
 * from async scheduler threads.
 */
internal class Database(file: File) : Closeable {
    private val connection: Connection

    init {
        Class.forName("org.h2.Driver")
        connection = DriverManager.getConnection("jdbc:h2:${file.absolutePath}", "sa", "")

        connection.createStatement().use { statement ->
            statement.executeUpdate(
                "CREATE TABLE IF NOT EXISTS `forum_users` (" +
                        "`username` VARCHAR(16) NOT NULL PRIMARY KEY," +
                        "`forum_id` BIGINT NOT NULL);"
            )
        }
    }

    /** Inserts the mapping or replaces an existing one (e.g. after /fixips re-registration). */
    @Synchronized
    fun setForumId(username: String, forumId: Long) {
        connection.prepareStatement("MERGE INTO `forum_users` (username, forum_id) KEY (username) VALUES (?, ?)")
            .use { preparedStatement ->
                preparedStatement.setString(1, username)
                preparedStatement.setLong(2, forumId)
                preparedStatement.executeUpdate()
            }
    }

    @Synchronized
    fun getForumId(username: String): Long? {
        connection.prepareStatement("SELECT `forum_id` FROM `forum_users` WHERE `username`=?")
            .use { preparedStatement ->
                preparedStatement.setString(1, username)
                preparedStatement.executeQuery().use { rs ->
                    return if (rs.next()) rs.getLong("forum_id") else null
                }
            }
    }

    @Synchronized
    fun removeUser(username: String) {
        connection.prepareStatement("DELETE FROM `forum_users` WHERE `username`=?").use { preparedStatement ->
            preparedStatement.setString(1, username)
            preparedStatement.executeUpdate()
        }
    }

    @Synchronized
    override fun close() {
        connection.close()
    }
}
