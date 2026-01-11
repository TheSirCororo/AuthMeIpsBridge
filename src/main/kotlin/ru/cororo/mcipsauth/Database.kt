package ru.cororo.mcipsauth

import org.bukkit.Bukkit
import java.io.Closeable
import java.sql.Connection
import java.sql.DriverManager

object Database : Closeable {
    private lateinit var connection: Connection

    fun load() {
        Class.forName("org.h2.Driver")
        connection = DriverManager.getConnection(
            "jdbc:h2:${Bukkit.getWorldContainer().absolutePath}/plugins/AuthMeIpsBridge/database.h2",
            "sa",
            ""
        )

        connection.createStatement().use { statement ->
            statement.executeUpdate(
                "CREATE TABLE IF NOT EXISTS `forum_users` (" +
                        "`username` VARCHAR(16) NOT NULL PRIMARY KEY," +
                        "`forum_id` BIGINT NOT NULL);"
            )
        }
    }

    fun addUser(username: String, forumId: Long) {
        connection.prepareStatement("INSERT INTO `forum_users` (username, forum_id) VALUES (?, ?)")
            .use { preparedStatement ->
                preparedStatement.setString(1, username)
                preparedStatement.setLong(2, forumId)
                preparedStatement.executeUpdate()
            }
    }

    fun getForumId(username: String): Long? {
        connection.prepareStatement("SELECT `forum_id` FROM `forum_users` WHERE `username`=?")
            .use { preparedStatement ->
                preparedStatement.setString(1, username)
                return try {
                    val rs = preparedStatement.executeQuery()
                    rs.next()
                    rs.getLong("forum_id")
                } catch (_: Exception) {
                    null
                }
            }
    }

    fun removeUser(username: String) {
        connection.prepareStatement("DELETE FROM `forum_users` WHERE `username`=?").use { preparedStatement ->
            preparedStatement.setString(1, username)
            preparedStatement.executeUpdate()
        }
    }

    override fun close() {
        connection.close()
    }
}