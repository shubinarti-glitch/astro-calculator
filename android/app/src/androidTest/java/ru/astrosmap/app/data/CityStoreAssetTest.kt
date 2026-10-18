package ru.astrosmap.app.data

import android.content.ContextWrapper
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertTrue
import org.junit.Test

class CityStoreAssetTest {
    private fun isolatedContext(): ContextWrapper {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.cacheDir, "city-test-${UUID.randomUUID()}").apply { mkdirs() }
        return object : ContextWrapper(context) {
            override fun getFilesDir(): File = directory
        }
    }

    @Test fun concurrentReadersSeeCompleteCityAsset() {
        val context = isolatedContext()
        val start = CountDownLatch(1)
        val workers = Executors.newFixedThreadPool(2)
        try {
            val results = (1..2).map {
                workers.submit<Boolean> {
                    start.await()
                    CityStore(context).search("Moscow").any { it.nameEn == "Moscow" }
                }
            }
            start.countDown()
            results.forEach { assertTrue(it.get(30, TimeUnit.SECONDS)) }
        } finally {
            workers.shutdownNow()
        }
    }

    @Test fun incompleteCityCopyRecoversFromPackagedAsset() {
        for (length in listOf(0, 65_536)) {
            val context = isolatedContext()
            File(context.filesDir, "db").mkdirs()
            val partial = context.assets.open("db/cities.db").use { it.readBytes().copyOf(length) }
            File(context.filesDir, "db/cities_v2.db").writeBytes(partial)
            assertTrue(CityStore(context).search("Moscow").any { it.nameEn == "Moscow" })
        }
    }
}
