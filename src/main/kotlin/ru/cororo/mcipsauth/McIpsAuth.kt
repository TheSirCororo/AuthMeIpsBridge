package ru.cororo.mcipsauth

import com.google.gson.Gson
import com.google.gson.JsonObject
import fr.xephi.authme.api.v3.AuthMeApi
import org.apache.http.client.HttpClient
import org.apache.http.client.entity.UrlEncodedFormEntity
import org.apache.http.client.methods.HttpPost
import org.apache.http.impl.client.HttpClientBuilder
import org.apache.http.message.BasicNameValuePair
import org.apache.http.util.EntityUtils
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerCommandPreprocessEvent
import org.bukkit.plugin.java.JavaPlugin
import java.util.*


class McIpsAuth : JavaPlugin(), Listener {
    private lateinit var authMeApi: AuthMeApi
    private lateinit var apiKey: String
    private lateinit var forumUrl: String
    private var startGroup: Int = 0
    private var startValidated: Int = 1
    private val pendingAuth = mutableListOf<String>()

    override fun onEnable() {
        authMeApi = AuthMeApi.getInstance()
        saveDefaultConfig()
        apiKey = config.getString("api_key")!!
        forumUrl = config.getString("forum_url")!!
        startGroup = config.getInt("start_group")
        startValidated = config.getInt("start_validated")
        server.pluginManager.registerEvents(this, this)
    }

    @EventHandler(priority = EventPriority.LOWEST)
    fun onCommand(event: PlayerCommandPreprocessEvent) {
        val message = event.message.split(" ")
        if (message[0].equals("/register", true) || message[0].equals("/reg", true)) {
            val password = message.last()
            register(event.player, password)
        }
        if (message[0].equals("/log", true) || message[0].equals("/l", true) || message[0].equals("/login", true)) {
            if (event.player.name in pendingAuth) {
                val password = message.last()
                register(event.player, password)
            }
        }
    }

    private fun register(player: Player, password: String) {
        server.scheduler.runTaskLater(this, Runnable {
            if (authMeApi.isRegistered(player.name)) {
                val requestParams = mapOf(
                    "name" to player.name,
                    "password" to password,
                    "group" to startGroup.toString(),
                    "registrationIpAddress" to player.address.address.hostAddress,
                    "validated" to startValidated.toString()
                )
                logger.info("Registering ${player.name} in IPS forum...")
                val response = postRequest("$forumUrl/api/core/members", requestParams)
                val json = Gson().fromJson(response.replace("\n", "").trim(), JsonObject::class.java).asJsonObject
                try {
                    if (json.has("errorMessage")) {
                        logger.severe("Register error! Message is ${json.get("errorMessage").asString}")
                    } else {
                        logger.info("Player ${player.name} was registered successfully!")
                    }
                } catch (ex: Exception) {
                    logger.severe("Register error!")
                    ex.printStackTrace()
                }
            }
        }, 20L)
    }

    private fun postRequest(url: String, formData: Map<String, String>): String {
        val httpClient: HttpClient = HttpClientBuilder.create().build()
        val httpPost = HttpPost(url)
        httpPost.addHeader("Authorization", "Basic ${Base64.getEncoder().encodeToString("$apiKey:".toByteArray())}")
        httpPost.entity = UrlEncodedFormEntity(formData.map { BasicNameValuePair(it.key, it.value) })
        return EntityUtils.toString(httpClient.execute(httpPost).entity)
    }

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<String>): Boolean {
        if (!sender.hasPermission("mcipsauth.admin")) {
            sender.sendMessage("Нет прав!")
            return false
        }

        if (args.isEmpty()) {
            sender.sendMessage("/fixips [никнейм]")
            return false
        }

        sender.sendMessage("Добавили игрока в очередь на регистрацию на форуме... Пусть он перезайдёт и войдёт в аккаунт.")
        pendingAuth.add(args[0])
        return true
    }
}