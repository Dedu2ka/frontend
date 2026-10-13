package com.example.olo.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AuthValidationTest {
    @Test fun trimsOnlyLeadingAndTrailingWhitespace() {
        assertNull(validateAuthInput(" alice ", " StrongPass1! "))
        assertEquals("Логин может содержать только латинские буквы и цифры", validateAuthInput("al ice", "StrongPass1!"))
        assertEquals("Пароль может содержать только латинские буквы, цифры и спецсимволы", validateAuthInput("alice", "Strong Pass1!"))
    }
}
