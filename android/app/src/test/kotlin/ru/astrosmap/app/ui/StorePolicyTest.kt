package ru.astrosmap.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.astrosmap.app.BuildConfig

class StorePolicyTest {
    @Test fun flavorExposesOnlyItsIntendedPurchaseOptions() {
        when (BuildConfig.STORE_ID) {
            "googleplay" -> {
                assertFalse(BuildConfig.SHOW_BILLING)
                assertFalse(BuildConfig.SHOW_EXTERNAL_PURCHASE_LINKS)
                assertEquals("none", BuildConfig.PAYMENT_PROVIDER)
            }
            "standard" -> {
                assertTrue(BuildConfig.SHOW_EXTERNAL_PURCHASE_LINKS)
                assertEquals("external_web", BuildConfig.PAYMENT_PROVIDER)
            }
            else -> error("Unreviewed store flavor: ${BuildConfig.STORE_ID}")
        }
    }
}
