package ru.astrosmap.app.ui.tools

import android.Manifest
import android.app.TimePickerDialog
import android.content.Context
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.astrosmap.app.R
import ru.astrosmap.app.data.EventReminderPlan
import ru.astrosmap.app.data.EventReminders
import ru.astrosmap.app.data.TokenStore
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject

@HiltViewModel
class EventReminderViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val tokens: TokenStore,
) : ViewModel() {
    private val _revision = MutableStateFlow(0)
    val revision = _revision.asStateFlow()
    fun schedule(plan: EventReminderPlan, at: Instant, result: (Boolean) -> Unit) {
        viewModelScope.launch {
            val token = tokens.get()
            var operation: androidx.work.Operation? = null
            tokens.withCurrentSession(plan.owner, token) { operation = EventReminders.schedule(context, plan, at) }
            val scheduled = try {
                val enqueued = operation
                if (enqueued == null) false else {
                    withContext(Dispatchers.IO) { enqueued.result.get() }
                    true
                }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) {
                tokens.withCurrentSession(plan.owner, token) {
                    runCatching { EventReminders.discardIfCurrent(context, plan.id, at.toEpochMilli()) }
                }
                false
            }
            _revision.value++
            result(scheduled)
        }
    }
    fun cancel(plan: EventReminderPlan) {
        viewModelScope.launch {
            val token = tokens.get()
            tokens.withCurrentSession(plan.owner, token) { EventReminders.cancel(context, plan.id) }
            _revision.value++
        }
    }
}

/** Explicit one-event opt-in; neither daily notification consent nor user settings are changed. */
@Composable
fun EventReminderButton(
    eventDate: String, eventTitle: String, city: String, zoneId: String, owner: String?,
    viewModel: EventReminderViewModel = hiltViewModel(),
) {
    if (owner == null) return
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val plan = remember(owner, eventDate, eventTitle, city, zoneId) {
        EventReminderPlan(owner, eventDate, eventTitle, city, zoneId)
    }
    val day = remember(eventDate) { runCatching { LocalDate.parse(eventDate) }.getOrNull() } ?: return
    val zone = remember(zoneId) { runCatching { ZoneId.of(zoneId) }.getOrNull() } ?: return
    val revision by viewModel.revision.collectAsState()
    val scheduled = remember(plan.id, revision) { EventReminders.scheduled(context, plan) }
    var dialog by remember(plan.id) { mutableStateOf(false) }
    var hour by remember(plan.id) { mutableIntStateOf(9) }
    var minute by remember(plan.id) { mutableIntStateOf(0) }
    var saving by remember(plan.id) { mutableStateOf(false) }
    var feedback by remember(plan.id) { mutableIntStateOf(0) }
    var pending by remember { mutableStateOf<Pair<EventReminderPlan, Instant>?>(null) }
    fun save(value: Pair<EventReminderPlan, Instant>) {
        saving = true
        viewModel.schedule(value.first, value.second) { success ->
            saving = false
            feedback = if (success) R.string.event_reminder_saved else R.string.event_reminder_failed
            if (success) dialog = false
        }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val value = pending
        pending = null
        if (granted && value != null) save(value) else { dialog = false; feedback = R.string.event_reminder_permission }
    }
    Column {
        TextButton(onClick = {
            feedback = 0
            if (scheduled) viewModel.cancel(plan) else {
                val now = ZonedDateTime.now(zone)
                if (day == now.toLocalDate()) {
                    val suggested = now.plusMinutes(2)
                    hour = suggested.hour; minute = suggested.minute
                }
                dialog = true
            }
        }) { Text(stringResource(if (scheduled) R.string.event_reminder_cancel else R.string.event_reminder_add)) }
        if (feedback != 0) Text(stringResource(feedback), style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 12.dp))
    }
    if (dialog) {
        val instant = plan.instant(hour, minute)
        AlertDialog(onDismissRequest = { dialog = false },
            title = { Text(stringResource(R.string.event_reminder_title)) },
            text = {
                Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(eventTitle, style = MaterialTheme.typography.titleMedium)
                    Text(day.format(DateTimeFormatter.ofPattern("d MMMM yyyy", locale)))
                    Text(listOf(city, zoneId).filter { it.isNotBlank() }.joinToString(" · "))
                    TextButton(onClick = {
                        TimePickerDialog(context, { _, h, m -> hour = h; minute = m }, hour, minute, true).show()
                    }) { Text(stringResource(R.string.event_reminder_time, hour, minute)) }
                    Text(stringResource(R.string.event_reminder_approximate), style = MaterialTheme.typography.bodySmall)
                    if (instant == null) Text(stringResource(R.string.event_reminder_invalid), color = MaterialTheme.colorScheme.error)
                    if (feedback != 0) Text(stringResource(feedback), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error)
                }
            },
            confirmButton = {
                TextButton(enabled = instant != null && !saving && pending == null, onClick = {
                    val value = plan to (plan.instant(hour, minute) ?: return@TextButton)
                    if (Build.VERSION.SDK_INT >= 33 && !EventReminders.notificationsAllowed(context)) {
                        pending = value
                        ru.astrosmap.app.data.DailyNotify.markPromptShown(context)
                        permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else if (EventReminders.notificationsAllowed(context)) save(value)
                    else { dialog = false; feedback = R.string.event_reminder_permission }
                }) { Text(stringResource(R.string.event_reminder_confirm)) }
            },
            dismissButton = { TextButton(onClick = { dialog = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}
