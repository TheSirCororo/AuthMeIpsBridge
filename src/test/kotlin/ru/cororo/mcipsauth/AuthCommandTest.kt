package ru.cororo.mcipsauth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AuthCommandTest {
    @Test
    fun `parses login and its aliases`() {
        for (label in listOf("/login", "/l", "/log", "/LOGIN", "/authme:login")) {
            assertEquals(AuthCommand.Login("secret"), AuthCommand.parse("$label secret"), label)
        }
    }

    @Test
    fun `takes the first argument of register as the password`() {
        assertEquals(AuthCommand.Register("secret"), AuthCommand.parse("/register secret secret"))
        assertEquals(AuthCommand.Register("secret"), AuthCommand.parse("/reg secret"))
    }

    @Test
    fun `parses change password and its aliases`() {
        for (label in listOf("/changepassword", "/changepass", "/cp", "/ChangePassword")) {
            assertEquals(AuthCommand.ChangePassword("old", "new"), AuthCommand.parse("$label old new"), label)
        }
    }

    @Test
    fun `ignores repeated whitespace`() {
        assertEquals(AuthCommand.ChangePassword("old", "new"), AuthCommand.parse("  /cp   old  new "))
    }

    @Test
    fun `returns null when arguments are missing`() {
        assertNull(AuthCommand.parse("/login"))
        assertNull(AuthCommand.parse("/register"))
        assertNull(AuthCommand.parse("/changepassword"))
        assertNull(AuthCommand.parse("/changepassword help"))
        assertNull(AuthCommand.parse("/"))
        assertNull(AuthCommand.parse(""))
    }

    @Test
    fun `ignores unrelated commands`() {
        assertNull(AuthCommand.parse("/logout"))
        assertNull(AuthCommand.parse("/loginx secret"))
        assertNull(AuthCommand.parse("/other:login secret"))
        assertNull(AuthCommand.parse("/msg Steve /login secret"))
    }
}
