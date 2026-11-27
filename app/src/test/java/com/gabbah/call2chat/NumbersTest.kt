package com.gabbah.call2chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NumbersTest {
    @Test fun italianMobile() = assertEquals("393331234567", Numbers.toWhatsApp("333 123 4567", "IT"))
    @Test fun withPlus() = assertEquals("393331234567", Numbers.toWhatsApp("+39 333 1234567", "IT"))
    @Test fun withZeros() = assertEquals("447911123456", Numbers.toWhatsApp("0044 7911 123456", "IT"))
    @Test fun foreignWhileInItaly() = assertEquals("33612345678", Numbers.toWhatsApp("+33612345678", "IT"))
    @Test fun landline() = assertEquals("390111234567", Numbers.toWhatsApp("011 1234567", "IT"))
    @Test fun tooShort() = assertNull(Numbers.toWhatsApp("123", "IT"))
    @Test fun pretty() = assertEquals("+39 333 123 4567", Numbers.pretty("393331234567"))
}
