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

        val statement = connection.createStatement()
        statement.executeUpdate(
            "CREATE TABLE IF NOT EXISTS `forum_users` (" +
                    "`username` VARCHAR(16) NOT NULL PRIMARY KEY," +
                    "`forum_id` BIGINT NOT NULL);"
        )

        statement.close()
    }

    fun addUser(username: String, forumId: Long) {
        val prepareStatement =
            connection.prepareStatement("INSERT INTO `forum_users` (username, forum_id) VALUES (?, ?);")
        prepareStatement.setString(1, username)
        prepareStatement.setLong(2, forumId)
        prepareStatement.executeUpdate()
    }

    fun getForumId(username: String): Long? {
        val prepareStatement = connection.prepareStatement("SELECT `forum_id` FROM `forum_users` WHERE `username`=?;")
        prepareStatement.setString(1, username)
        return try {
            val rs = prepareStatement.executeQuery()
            rs.next()
            rs.getLong("forum_id")
        } catch (ex: Exception) {
            null
        }
    }

    fun removeUser(username: String) {
        val preparedStatement = connection.prepareStatement("DELETE FROM `forum_users` WHERE `username`=?;")
        preparedStatement.setString(1, username)
        preparedStatement.executeUpdate()
    }

    override fun close() {
        connection.close()
    }
}