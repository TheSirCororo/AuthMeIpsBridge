package ru.cororo.mcipsauth

import fr.xephi.authme.api.v3.AuthMeApi
import fr.xephi.authme.events.LoginEvent
import fr.xephi.authme.events.RegisterEvent
import fr.xephi.authme.events.UnregisterByAdminEvent
import fr.xephi.authme.events.UnregisterByPlayerEvent
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerCommandPreprocessEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.logging.Level

class McIpsAuth : JavaPlugin(), Listener {
    private lateinit var authMeApi: AuthMeApi
    private lateinit var settings: Config
    private lateinit var ipsClient: IpsClient
    private lateinit var database: Database

    // Passwords typed in /login or /register, held until AuthMe fires LoginEvent/RegisterEvent
    private val typedPasswords = ConcurrentHashMap<UUID, String>()

    // Lowercased names queued by /fixips to be registered on the forum again on next login
    private val pendingFix: MutableSet<String> = ConcurrentHashMap.newKeySet()

    override fun onEnable() {
        saveDefaultConfig()

        settings = try {
            Config.parse(
                forumUrl = config.getString("forum_url"),
                apiKey = config.getString("api_key"),
                startGroup = config.getInt("start_group"),
                startValidated = config.getInt("start_validated"),
            )
        } catch (ex: IllegalArgumentException) {
            logger.severe("Invalid config.yml: ${ex.message}. Plugin is disabled.")
            server.pluginManager.disablePlugin(this)
            return
        }
        if (settings.isInsecure) {
            logger.warning("forum_url uses plain HTTP: the API key and player passwords are sent unencrypted! Use https://")
        }

        authMeApi = AuthMeApi.getInstance()
        ipsClient = IpsClient(settings.forumUrl, settings.apiKey)
        database = Database(File(server.worldContainer, "plugins/AuthMeIpsBridge/database.h2"))

        server.pluginManager.registerEvents(this, this)
    }

    override fun onDisable() {
        if (::database.isInitialized) {
            database.close()
        }
        typedPasswords.clear()
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onCommand(event: PlayerCommandPreprocessEvent) {
        val player = event.player

        when (val command = AuthCommand.parse(event.message)) {
            is AuthCommand.Login -> rememberPassword(player, command.password)
            is AuthCommand.Register -> rememberPassword(player, command.password)

            is AuthCommand.ChangePassword -> {
                if (!authMeApi.isAuthenticated(player) || command.oldPassword == command.newPassword) return
                syncPasswordChange(player.name, command.newPassword, attempt = 1)
            }

            null -> {}
        }
    }

    @EventHandler
    fun onLogin(event: LoginEvent) {
        syncRegistration(event.player)
    }

    @EventHandler
    fun onRegister(event: RegisterEvent) {
        syncRegistration(event.player)
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        typedPasswords.remove(event.player.uniqueId)
    }

    @EventHandler
    fun onUnregister(event: UnregisterByPlayerEvent) {
        event.player?.let { deleteForumMember(it.name) }
    }

    @EventHandler
    fun onUnregister(event: UnregisterByAdminEvent) {
        deleteForumMember(event.playerName)
    }

    private fun rememberPassword(player: Player, password: String) {
        if (!authMeApi.isAuthenticated(player)) {
            typedPasswords[player.uniqueId] = password
        }
    }

    private fun syncRegistration(player: Player) {
        val password = typedPasswords.remove(player.uniqueId) ?: return
        val name = player.name
        val ip = player.address?.address?.hostAddress

        runAsync {
            val fix = name.lowercase() in pendingFix
            if (!fix && database.getForumId(name) != null) return@runAsync

            // The typed command may differ from what AuthMe accepted (e.g. email registration)
            if (!authMeApi.checkPassword(name, password)) {
                logger.warning("Typed password of $name does not match the AuthMe account, forum registration skipped")
                return@runAsync
            }

            logger.info("Registering $name in IPS forum...")
            when (val result = ipsClient.createMember(name, password, settings.startGroup, settings.startValidated, ip)) {
                is IpsResult.Success -> {
                    database.setForumId(name, result.value)
                    pendingFix.remove(name.lowercase())
                    logger.info("Player $name was registered successfully with id ${result.value}!")
                }

                is IpsResult.Failure -> logger.severe("Register error for $name: ${result.message}")
            }
        }
    }

    private fun syncPasswordChange(name: String, newPassword: String, attempt: Int) {
        // AuthMe changes the password asynchronously, so wait until the new one is stored
        runAsync(delay = PASSWORD_CHECK_DELAY) {
            if (!authMeApi.checkPassword(name, newPassword)) {
                if (attempt < PASSWORD_CHECK_ATTEMPTS) {
                    syncPasswordChange(name, newPassword, attempt + 1)
                }
                return@runAsync
            }

            val forumId = database.getForumId(name)
            if (forumId == null) {
                logger.warning("Player $name was not found on table!")
                return@runAsync
            }

            logger.info("Changing password for $name on IPS forum...")
            when (val result = ipsClient.changePassword(forumId, newPassword)) {
                is IpsResult.Success -> logger.info("Player $name changed password successfully!")
                is IpsResult.Failure -> logger.severe("Password change error for $name: ${result.message}")
            }
        }
    }

    private fun deleteForumMember(name: String) {
        pendingFix.remove(name.lowercase())

        runAsync {
            val forumId = database.getForumId(name) ?: return@runAsync

            logger.info("Unregistering $name from forum...")
            when (val result = ipsClient.deleteMember(forumId)) {
                is IpsResult.Success -> logger.info("Forum member $forumId of $name was deleted")
                is IpsResult.Failure ->
                    logger.severe("Failed to delete forum member $forumId of $name, delete it manually: ${result.message}")
            }
            database.removeUser(name)
        }
    }

    private fun runAsync(delay: Long = 0L, action: () -> Unit) {
        server.scheduler.runTaskLaterAsynchronously(this, Runnable {
            try {
                action()
            } catch (ex: Exception) {
                logger.log(Level.SEVERE, "Forum synchronization failed", ex)
            }
        }, delay)
    }

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<String>): Boolean {
        if (!sender.hasPermission("mcipsauth.admin")) {
            sender.sendMessage(config.getString("messages.no_perms").orEmpty())
            return true
        }

        if (args.isEmpty()) {
            sender.sendMessage(config.getString("messages.fixips_usage").orEmpty())
            return true
        }

        pendingFix.add(args[0].lowercase())
        sender.sendMessage(config.getString("messages.fixips_success").orEmpty())
        return true
    }

    private companion object {
        const val PASSWORD_CHECK_DELAY = 20L
        const val PASSWORD_CHECK_ATTEMPTS = 5
    }
}
