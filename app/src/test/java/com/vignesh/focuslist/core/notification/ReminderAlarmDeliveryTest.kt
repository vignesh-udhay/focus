package com.vignesh.focuslist.core.notification

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The deliberately narrow vendor boundary around D-042's alarm-clock path. */
class ReminderAlarmDeliveryTest {

    @Test
    fun `OnePlus uses alarm clock delivery`() {
        assertTrue(usesAlarmClockDelivery("OnePlus"))
        assertTrue(usesAlarmClockDelivery(" oneplus "))
    }

    @Test
    fun `unmeasured OxygenOS relatives keep ordinary exact delivery`() {
        assertFalse(usesAlarmClockDelivery("OPPO"))
        assertFalse(usesAlarmClockDelivery("realme"))
    }

    @Test
    fun `other manufacturers keep ordinary exact delivery`() {
        assertFalse(usesAlarmClockDelivery("Samsung"))
        assertFalse(usesAlarmClockDelivery("Xiaomi"))
        assertFalse(usesAlarmClockDelivery("Google"))
    }
}
