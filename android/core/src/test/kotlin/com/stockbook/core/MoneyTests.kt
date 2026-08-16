package com.stockbook.core

import com.stockbook.core.model.Currency
import com.stockbook.core.money.Money
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The exact expectations the iOS build asserts, so the two never diverge. */
class MoneyTests {

    @Test
    fun `whole numbers carry no decimals`() {
        assertEquals("SAR 194", Money.text(194.0))
        assertEquals("SAR 0", Money.text(0.0))
    }

    @Test
    fun `anything else carries exactly two`() {
        assertEquals("SAR 0.25", Money.text(0.25))
        assertEquals("SAR 0.50", Money.text(0.5))
    }

    @Test
    fun `thousands are grouped`() {
        assertEquals("SAR 1,240", Money.text(1240.0))
        assertEquals("SAR 1,240.50", Money.text(1240.5))
    }

    @Test
    fun `values round to the currency's minor unit`() {
        assertEquals("SAR 194", Money.text(193.999))
        assertEquals("SAR 0.01", Money.text(0.005))
    }

    @Test
    fun `negative zero prints as zero`() {
        assertEquals("SAR 0", Money.text(-0.0))
    }

    @Test
    fun `the symbol is the currency's`() {
        assertEquals("₹12", Money.text(12.0, Currency.INR))
    }

    @Test
    fun `parsing tells empty from zero`() {
        assertEquals(0.0, Money.parse("0"))
        assertNull(Money.parse(""))
        assertNull(Money.parse("   "))
        assertNull(Money.parse("nonsense"))
        assertEquals(1240.5, Money.parse("1,240.50"))
    }
}
