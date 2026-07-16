package ru.astrosmap.app.ui.view

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import ru.astrosmap.app.R
import ru.astrosmap.app.astro.AspectHit
import ru.astrosmap.app.astro.ChartPoint
import ru.astrosmap.app.data.ChartTexts
import ru.astrosmap.app.data.Titled
import ru.astrosmap.app.ui.AstroLabels
import ru.astrosmap.app.ui.chart.ChartWheel
import ru.astrosmap.app.ui.theme.GoodColor

/** Экран карты: колесо, позиции и аспекты (раскрываются в трактовку), текстовые разделы. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChartViewScreen(
    onEdit: () -> Unit,
    onClosed: () -> Unit,
    viewModel: ChartViewViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    var confirmDelete by remember { mutableStateOf(false) }
    val expanded = remember { mutableStateOf(setOf<String>()) }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            text = { Text(stringResource(R.string.delete_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.delete()
                    onClosed()
                }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    val e = state.entity
                    if (e != null) Text("${e.name} · ${e.day}.${e.month}.${e.year}", maxLines = 1)
                },
                actions = {
                    if (state.entity != null) {
                        val context = androidx.compose.ui.platform.LocalContext.current
                        IconButton(onClick = {
                            val e = state.entity ?: return@IconButton
                            state.chart?.let {
                                ru.astrosmap.app.ui.chart.ChartExport.share(context, it, "${e.name} · ${e.day}.${e.month}.${e.year}")
                            }
                        }) {
                            Icon(painterResource(R.drawable.ic_share), stringResource(R.string.share))
                        }
                        IconButton(onClick = { viewModel.edit(); onEdit() }) {
                            Icon(painterResource(R.drawable.ic_edit), stringResource(R.string.edit))
                        }
                        if (state.savedId == null) {
                            IconButton(onClick = viewModel::save) {
                                Icon(painterResource(R.drawable.ic_save), stringResource(R.string.save))
                            }
                        } else {
                            IconButton(onClick = { confirmDelete = true }) {
                                Icon(painterResource(R.drawable.ic_delete), stringResource(R.string.delete))
                            }
                        }
                    }
                },
            )
        },
    ) { padding ->
        val chart = state.chart
        if (chart == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }
        val texts = state.texts
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            item {
                ChartWheel(
                    chart = chart,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .padding(8.dp),
                )
            }
            if (state.textsOffline && texts == null) {
                item {
                    Text(
                        stringResource(R.string.texts_offline),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            }
            item { SectionTitle(stringResource(R.string.planets)) }
            items(chart.points) { p ->
                PlanetRow(p, texts?.planetTexts?.get(p.name), expanded)
            }
            items(chart.angles.filter { it.name in listOf("Ascendant", "Medium_Coeli") }) { p ->
                PlanetRow(p, texts?.planetTexts?.get(p.name), expanded)
            }
            item { SectionTitle(stringResource(R.string.aspects)) }
            items(chart.aspects) { a ->
                AspectRow(a, texts?.aspectTexts?.get(ChartTexts.aspectKey(a.p1, a.aspect, a.p2)), expanded)
            }
            if (texts != null) {
                textSection(texts.storySections.takeIf { it.isNotEmpty() }, null)
                textSection(texts.bigThree.takeIf { it.isNotEmpty() }, R.string.big_three)
                textSection(texts.temperament.takeIf { it.isNotEmpty() }, R.string.portrait)
                textSection(
                    texts.spheres.map { Titled(sphereLabel(it.title), it.text) }.takeIf { it.isNotEmpty() },
                    R.string.spheres,
                )
            }
        }
    }
}

private fun sphereLabel(key: String): String = when (key) {
    "love" -> if (AstroLabels.isRu()) "Любовь" else "Love"
    "career" -> if (AstroLabels.isRu()) "Карьера" else "Career"
    "health" -> if (AstroLabels.isRu()) "Здоровье" else "Health"
    else -> key
}

private fun androidx.compose.foundation.lazy.LazyListScope.textSection(blocks: List<Titled>?, titleRes: Int?) {
    if (blocks == null) return
    item {
        if (titleRes != null) SectionTitle(stringResource(titleRes))
        else SectionTitle(stringResource(R.string.about_you))
    }
    items(blocks) { block ->
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
            if (block.title.isNotBlank()) {
                Text(
                    block.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
            Text(block.text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
    )
}

@Composable
private fun PlanetRow(
    p: ChartPoint,
    paragraphs: List<Titled>?,
    expanded: androidx.compose.runtime.MutableState<Set<String>>,
) {
    val key = "planet:${p.name}"
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = paragraphs != null) {
                expanded.value = if (key in expanded.value) expanded.value - key else expanded.value + key
            }
            .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(AstroLabels.pointGlyphs[p.name] ?: "", color = MaterialTheme.colorScheme.primary)
            Text(AstroLabels.point(p.name), Modifier.weight(1f))
            if (p.retrograde) {
                Text("R", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
            }
            Text("${AstroLabels.signGlyphs[p.sign]} ${AstroLabels.sign(p.sign)}", color = GoodColor)
            Text(AstroLabels.degMin(p.position))
            Text(
                stringResource(R.string.house_short, p.houseNum),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (key in expanded.value && paragraphs != null) {
            for (block in paragraphs) {
                Column(Modifier.padding(top = 6.dp)) {
                    if (block.title.isNotBlank()) {
                        Text(
                            block.title,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                    }
                    Text(block.text, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun AspectRow(
    a: AspectHit,
    interp: String?,
    expanded: androidx.compose.runtime.MutableState<Set<String>>,
) {
    val key = "aspect:${a.p1}|${a.aspect}|${a.p2}"
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = interp != null) {
                expanded.value = if (key in expanded.value) expanded.value - key else expanded.value + key
            }
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(AstroLabels.pointGlyphs[a.p1] ?: a.p1, color = MaterialTheme.colorScheme.primary)
            Text(AstroLabels.aspectGlyphs[a.aspect] ?: "", color = MaterialTheme.colorScheme.secondary)
            Text(AstroLabels.pointGlyphs[a.p2] ?: a.p2, color = MaterialTheme.colorScheme.primary)
            Text(AstroLabels.aspect(a.aspect), Modifier.weight(1f))
            Text(
                "%.2f°".format(a.orbit),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (key in expanded.value && interp != null) {
            Text(
                interp,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}
