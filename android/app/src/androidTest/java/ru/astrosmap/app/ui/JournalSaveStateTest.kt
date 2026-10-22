package ru.astrosmap.app.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import java.lang.reflect.Proxy
import kotlinx.coroutines.CompletableDeferred
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

/** Save confirmation must describe the current editor, including delayed or failed writes. */
class JournalSaveStateTest {
    @get:Rule val compose = createComposeRule()

    private fun journey(wrap: (JournalDao) -> JournalDao = { it }, test: (JournalViewModel, JournalDao) -> Unit) {
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
            vm = JournalViewModel(context, wrap(journal.journalDao()), ChartDao(charts.chartDao(), tokens),
                AstroEngine(null), api, tokens, Analytics(context, api))
            store.put("journal-save-state", vm)
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

    @Test fun confirmationClearsWhenSavedEntryIsEdited() = journey { vm, dao ->
        compose.runOnIdle { vm.note = "Persisted note"; vm.save() }
        compose.waitUntil(20_000) { vm.isCurrentSaved && !vm.saving }
        assertEquals("Persisted note", runBlocking { dao.byDate("guest", vm.selectedDate.toEpochDay())?.note })
        compose.runOnIdle {
            assertEquals(1, vm.saveRevision)
            vm.note = "Unsaved edit"
            assertFalse(vm.isCurrentSaved)
        }
    }

    @Test fun failedWriteNeverConfirmsAndAllowsRetry() = journey(wrap = { dao ->
        object : JournalDao by dao {
            var fail = true
            override suspend fun upsert(entry: JournalEntry) {
                if (fail) { fail = false; throw IllegalStateException("Synthetic write failure") }
                dao.upsert(entry)
            }
        }
    }) { vm, dao ->
        compose.runOnIdle { vm.note = "Keep this draft"; vm.save() }
        compose.waitUntil(20_000) { vm.saveFailed && !vm.saving }
        compose.runOnIdle {
            assertFalse(vm.isCurrentSaved)
            assertEquals(0, vm.saveRevision)
            assertEquals("Keep this draft", vm.note)
        }
        assertTrue(runBlocking { dao.all("guest").isEmpty() })
        compose.runOnIdle { vm.save() }
        compose.waitUntil(20_000) { vm.isCurrentSaved && !vm.saving }
        compose.runOnIdle { assertFalse(vm.saveFailed); assertEquals(1, vm.saveRevision) }
        assertEquals("Keep this draft", runBlocking { dao.byDate("guest", vm.selectedDate.toEpochDay())?.note })
    }

    @Test fun delayedWriteDoesNotConfirmAnotherDate() {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        journey(wrap = { dao ->
            object : JournalDao by dao {
                override suspend fun upsert(entry: JournalEntry) {
                    entered.complete(Unit)
                    release.await()
                    dao.upsert(entry)
                }
            }
        }) { vm, dao ->
            val original = vm.selectedDate
            compose.runOnIdle { vm.note = "Original date"; vm.save() }
            compose.waitUntil(20_000) { entered.isCompleted }
            compose.runOnIdle { vm.load(original.plusDays(1)) }
            compose.waitUntil(20_000) { vm.selectedDate == original.plusDays(1) }
            release.complete(Unit)
            compose.waitUntil(20_000) { runBlocking { dao.byDate("guest", original.toEpochDay()) != null } }
            compose.runOnIdle {
                assertFalse(vm.isCurrentSaved)
                assertFalse(vm.saving)
                assertEquals(0, vm.saveRevision)
            }
        }
    }
}
