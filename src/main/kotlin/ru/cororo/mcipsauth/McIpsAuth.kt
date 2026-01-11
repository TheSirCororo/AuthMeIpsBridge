package ru.cororo.mcipsauth

import fr.xephi.authme.api.v3.AuthMeApi
import fr.xephi.authme.events.UnregisterByAdminEvent
import fr.xephi.authme.events.UnregisterByPlayerEvent
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerCommandPreprocessEvent
import org.bukkit.plugin.java.JavaPlugin

internal lateinit var parsedConfig: Config

class McIpsAuth : JavaPlugin(), Listener {
    private lateinit var authMeApi: AuthMeApi

    private val pendingAuth = mutableListOf<String>()

    override fun onEnable() {
        saveDefaultConfig()

        authMeApi = AuthMeApi.getInstance()
        parsedConfig = Config(
            apiKey = config.getString("api_key")!!,
            forumUrl = config.getString("forum_url")!!,
            startGroup = config.getInt("start_group"),
            startValidated = config.getInt("start_validated")
        )

        server.pluginManager.registerEvents(this, this)

        Database.load()
    }

    override fun onDisable() {
        Database.close()
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onCommand(event: PlayerCommandPreprocessEvent) {
        val message = event.message.split(" ")

        when (message[0].lowercase()) {
            "/register, /reg" -> {
                val password = message.last()
                register(event.player, password)
            }

            "/log", "/l", "/login" -> {
                val forumId = Database.getForumId(event.player.name)
                if (event.player.name in pendingAuth || forumId == null) {
                    val password = message.last()
                    register(event.player, password)
                }
            }

            "/changepassword" -> {
                if (message[1].equals("help", ignoreCase = true) || message.size < 3) {
                    return
                }

                changePassword(event.player, message[1], message[2])
            }
        }

        if (message[0].equals("/changepassword", ignoreCase = true)) {
            if (message[1].equals("help", ignoreCase = true) || message.size < 3) {
                return
            }

            changePassword(event.player, message[1], message[2])
        }
    }

    @EventHandler
    fun onUnregister(event: UnregisterByPlayerEvent) {
        unregister(event.player)
    }

    @EventHandler
    fun onUnregister(event: UnregisterByAdminEvent) {
        unregister(event.player)
    }

    private fun unregister(player: Player) {
        logger.info("Unregistering ${player.name} from forum...")
        val forumId = Database.getForumId(player.name)
        formRequest("${parsedConfig.forumUrl}/api/index.php?/core/members/$forumId", mapOf(), "DELETE")
        Database.removeUser(player.name)
    }

    private fun register(player: Player, password: String) {
        server.scheduler.runTaskLater(this, {
            if (!authMeApi.isAuthenticated(player)) return@runTaskLater

            val requestParams = mapOf(
                "name" to player.name,
                "password" to password,
                "group" to parsedConfig.startGroup.toString(),
                "registrationIpAddress" to player.address.address.hostAddress,
                "validated" to parsedConfig.startValidated.toString()
            )

            logger.info("Registering ${player.name} in IPS forum...")
            val response = formRequest("${parsedConfig.forumUrl}/api/index.php?/core/members", requestParams)
            val json = response.asJsonObject() ?: run {
                logger.severe("Error occured while handling response: $response")
                return@runTaskLater
            }

            try {
                if (json.has("errorMessage")) {
                    logger.severe("Register error! Message is ${json.get("errorMessage").asString}")
                } else {
                    val id = json.getAsJsonPrimitive("id").asLong
                    Database.addUser(player.name, id)

                    logger.info("Player ${player.name} was registered successfully with id $id!")
                }
            } catch (ex: Exception) {
                logger.severe("Register error!")
                ex.printStackTrace()
            }
        }, 20L)
    }

    private fun changePassword(player: Player, oldPassword: String, newPassword: String) {
        if (oldPassword == newPassword) return

        server.scheduler.runTaskLater(this, {
            if (!authMeApi.isAuthenticated(player)) return@runTaskLater

            logger.info("Changing password for ${player.name} on IPS forum...")

            val forumId = Database.getForumId(player.name)
            if (forumId == null) {
                logger.warning("Player ${player.name} was not found on table!")
                return@runTaskLater
            }

            val parameters = mapOf("password" to newPassword)

            val response = formRequest("${parsedConfig.forumUrl}/api/index.php?/core/members/$forumId", parameters)
            val json = response.asJsonObject() ?: run {
                logger.severe("Error occured while handling response: $response")
                return@runTaskLater
            }

            try {
                if (json.has("errorMessage")) {
                    logger.severe("Password change error! Message is ${json.get("errorMessage").asString}")
                } else {
                    logger.info("Player ${player.name} changed password successfully!")
                }
            } catch (ex: Exception) {
                logger.severe("Password change error!")
                ex.printStackTrace()
            }
        }, 10L)
    }

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<String>): Boolean {
        if (!sender.hasPermission("mcipsauth.admin")) {
            sender.sendMessage(config.getString("messages.no_perms")!!)
            return false
        }

        if (args.isEmpty()) {
            sender.sendMessage(config.getString("messages.fixips_usage")!!)
            return false
        }

        sender.sendMessage(config.getString("messages.fixips_success")!!)
        pendingAuth.add(args[0])
        return true
    }
}

internal data class Config(
    val apiKey: String,
    val forumUrl: String,
    val startGroup: Int,
    val startValidated: Int,
)
