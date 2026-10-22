package ru.astrosmap.app.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import java.lang.reflect.Proxy
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import ru.astrosmap.app.astro.AstroEngine
import ru.astrosmap.app.data.*
import ru.astrosmap.app.data.api.AstroApi
import ru.astrosmap.app.ui.journal.*
import ru.astrosmap.app.ui.theme.AstroTheme

/** Real isolated Room + real viewmodel; no network or real accounts. */
class JournalAdviceJourneyTest {
    @get:Rule val compose = createComposeRule()

    private fun journey(test: (JournalViewModel, JournalDao) -> Unit) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        assumeTrue(context.packageName.endsWith(".debug"))
        val tokens = TokenStore(context)
        assumeTrue(runBlocking { tokens.get() == null })
        val journal = Room.inMemoryDatabaseBuilder(context, JournalDb::class.java).build()
        val charts = Room.inMemoryDatabaseBuilder(context, ChartDb::class.java).build()
        val api = Proxy.newProxyInstance(AstroApi::class.java.classLoader, arrayOf(AstroApi::class.java)) { _, method, _ ->
            if (method.name == "events") Unit else throw IllegalStateException("Unexpected network call: ${method.name}")
        } as AstroApi
        val store = ViewModelStore()
        lateinit var vm: JournalViewModel
        instrumentation.runOnMainSync {
            vm = JournalViewModel(context, journal.journalDao(), ChartDao(charts.chartDao(), tokens),
                AstroEngine(null), api, tokens, Analytics(context, api))
            store.put("isolated-journal", vm)
        }
        try {
            compose.setContent { AstroTheme { JournalScreen(vm) } }
            compose.waitUntil(20_000) { vm.ready }
            test(vm, journal.journalDao())
        } finally {
            instrumentation.runOnMainSync { store.clear() }
            journal.close()
            charts.close()
        }
    }

    @Test fun adviceOnAnotherDatePreservesBothUnsavedDrafts() = journey { vm, dao ->
        val today = vm.selectedDate
        val tomorrow = today.plusDays(1)
        compose.runOnIdle {
            vm.note = "Unsaved original note"
            vm.addAdvice(AdviceDraft(tomorrow, "Synthetic advice", "guest")) {}
        }
        compose.waitUntil(20_000) { vm.selectedDate == tomorrow && vm.note == "Synthetic advice" }
        compose.runOnIdle { vm.note = "Tomorrow draft"; vm.load(today) }
        compose.waitUntil(20_000) { vm.selectedDate == today && vm.note == "Unsaved original note" }
        compose.runOnIdle { vm.load(tomorrow) }
        compose.waitUntil(20_000) { vm.selectedDate == tomorrow && vm.note == "Tomorrow draft" }
        assertTrue(runBlocking { dao.all("guest").isEmpty() }) // Advice never auto-saves.
    }

    @Test fun saveUsesClickedSnapshotWithoutDiscardingLaterTyping() = journey { vm, dao ->
        val day = vm.selectedDate.toEpochDay()
        compose.runOnIdle {
            vm.note = "At save click"
            vm.save()
            vm.note = "Typed while saving"
        }
        compose.waitUntil(20_000) { runBlocking { dao.byDate("guest", day)?.note == "At save click" } }
        compose.runOnIdle {
            assertEquals("Typed while saving", vm.note)
            assertFalse(vm.saved)
        }
    }
}
