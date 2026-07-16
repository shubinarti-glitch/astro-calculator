package ru.astrosmap.app.ui.form

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import ru.astrosmap.app.R
import ru.astrosmap.app.ui.AstroLabels

/** Форма данных рождения: имя, дата, время, город из офлайн-справочника. */
@Composable
fun ChartFormScreen(
    onCalculated: () -> Unit,
    viewModel: ChartFormViewModel = hiltViewModel(),
) {
    androidx.compose.runtime.LaunchedEffect(Unit) { viewModel.maybeConsumeEdit() }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedTextField(
            value = viewModel.name,
            onValueChange = { viewModel.name = it.take(80) },
            label = { Text(stringResource(R.string.form_name)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Text(stringResource(R.string.form_date), style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberField(viewModel.day, { viewModel.day = it }, stringResource(R.string.form_day), Modifier.weight(1f))
            NumberField(viewModel.month, { viewModel.month = it }, stringResource(R.string.form_month), Modifier.weight(1f))
            NumberField(viewModel.year, { viewModel.year = it }, stringResource(R.string.form_year), Modifier.weight(1.4f))
        }

        Text(stringResource(R.string.form_time), style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberField(viewModel.hour, { viewModel.hour = it }, stringResource(R.string.form_hour), Modifier.weight(1f))
            NumberField(viewModel.minute, { viewModel.minute = it }, stringResource(R.string.form_minute), Modifier.weight(1f))
        }

        OutlinedTextField(
            value = viewModel.cityQuery,
            onValueChange = viewModel::onCityQuery,
            label = { Text(stringResource(R.string.form_city)) },
            singleLine = true,
            isError = viewModel.errorRes != null && viewModel.selectedCity == null,
            modifier = Modifier.fillMaxWidth(),
        )
        viewModel.suggestions.forEach { city ->
            Surface(
                onClick = { viewModel.pickCity(city) },
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    city.label(AstroLabels.isRu()),
                    Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                )
            }
        }

        viewModel.errorRes?.let {
            Text(stringResource(it), color = MaterialTheme.colorScheme.error)
        }

        Button(
            onClick = { if (viewModel.calculate()) onCalculated() },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.form_calculate))
        }
    }
}

@Composable
private fun NumberField(value: String, onChange: (String) -> Unit, label: String, modifier: Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.filter(Char::isDigit).take(4)) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
    )
}
