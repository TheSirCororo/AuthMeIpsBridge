package ru.cororo.mcipsauth

/**
 * An AuthMe command typed by a player, parsed from the raw chat command line.
 * Only the commands whose passwords have to be mirrored to the forum are recognized.
 */
internal sealed interface AuthCommand {
    data class Login(val password: String) : AuthCommand

    data class Register(val password: String) : AuthCommand

    data class ChangePassword(val oldPassword: String, val newPassword: String) : AuthCommand

    companion object {
        // Labels and aliases from AuthMe's plugin.yml
        private val LOGIN = setOf("login", "l", "log")
        private val REGISTER = setOf("register", "reg")
        private val CHANGE_PASSWORD = setOf("changepassword", "changepass", "cp")

        private val WHITESPACE = Regex("\\s+")

        fun parse(message: String): AuthCommand? {
            val args = message.trim().split(WHITESPACE)
            val label = args[0].removePrefix("/").lowercase().removePrefix("authme:")

            return when {
                label in LOGIN && args.size >= 2 -> Login(args[1])
                // /register <password> [confirmation]
                label in REGISTER && args.size >= 2 -> Register(args[1])
                label in CHANGE_PASSWORD && args.size >= 3 -> ChangePassword(args[1], args[2])
                else -> null
            }
        }
    }
}
