package africa.fundaza.customer.ui.feature.recipients

import africa.fundaza.customer.domain.model.BeneficiaryField
import africa.fundaza.customer.domain.model.RecipientType
import africa.fundaza.customer.ui.components.Banner
import africa.fundaza.customer.ui.components.BannerTone
import africa.fundaza.customer.ui.components.ChoiceChip
import africa.fundaza.customer.ui.components.ErrorState
import africa.fundaza.customer.ui.components.Eyebrow
import africa.fundaza.customer.ui.components.FundazaTopBar
import africa.fundaza.customer.ui.components.LoadState
import africa.fundaza.customer.ui.components.LoadingState
import africa.fundaza.customer.ui.components.PrimaryButton
import africa.fundaza.customer.ui.components.SelectField
import africa.fundaza.customer.ui.components.flagOf
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddRecipientScreen(
    onBack: () -> Unit,
    onSaved: (Long) -> Unit,
    viewModel: AddRecipientViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is AddRecipientEvent.Saved -> onSaved(event.beneficiaryId)
            }
        }
    }

    val title = if (state.isNewRecipient) "New recipient" else "Add a way to receive"

    Scaffold(
        topBar = { FundazaTopBar(title, onBack = onBack) },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.background) {
                Box(Modifier.navigationBarsPadding().imePadding().padding(horizontal = 20.dp, vertical = 12.dp)) {
                    PrimaryButton(
                        text = if (state.isNewRecipient) "Save recipient" else "Save details",
                        onClick = viewModel::save,
                        loading = state.saving,
                        enabled = state.fields is LoadState.Loaded,
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (val corridors = state.corridors) {
                LoadState.Loading -> LoadingState()
                is LoadState.Failed -> ErrorState(corridors.message, onRetry = viewModel::loadCorridors)
                is LoadState.Loaded -> Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
                ) {
                    if (!state.isNewRecipient) {
                        Text(
                            state.existingName?.let { "New payout details for $it." } ?: "New payout details.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(16.dp))
                    }

                    if (state.isNewRecipient) {
                        Eyebrow("Who are they?")
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            RecipientType.entries.forEach { t ->
                                ChoiceChip(t.label, selected = state.type == t, onClick = { viewModel.selectType(t) })
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = state.fullName,
                            onValueChange = viewModel::onNameChange,
                            label = { Text(if (state.type == RecipientType.BUSINESS) "Registered business name" else "Full name, as on their ID") },
                            singleLine = true,
                            isError = state.errors["full_name"] != null,
                            supportingText = state.errors["full_name"]?.let { { Text(it) } },
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        SelectField(
                            label = "Relationship to you",
                            options = state.relationshipOptions,
                            selected = state.relationship,
                            optionLabel = { it.label },
                            onSelect = viewModel::selectRelationship,
                            error = state.errors["relationship"],
                        )
                        Spacer(Modifier.height(24.dp))
                    }

                    Eyebrow("Where and how they receive")
                    Spacer(Modifier.height(10.dp))
                    SelectField(
                        label = "Country",
                        options = state.countries,
                        selected = state.country,
                        optionLabel = { "${flagOf(it.iso2)}  ${it.name}" },
                        onSelect = viewModel::selectCountry,
                        enabled = !state.countryLocked,
                        error = state.errors["country"],
                    )
                    if (state.currencies.size > 1) {
                        Spacer(Modifier.height(8.dp))
                        SelectField(
                            label = "Paid out in",
                            options = state.currencies,
                            selected = state.currency,
                            optionLabel = { it },
                            onSelect = viewModel::selectCurrency,
                            enabled = !state.currencyLocked,
                        )
                    }

                    when (val methods = state.methods) {
                        null -> {}
                        LoadState.Loading -> Box(Modifier.fillMaxWidth().padding(16.dp)) { CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) }
                        is LoadState.Failed -> Banner(methods.message, tone = BannerTone.Error, action = { TextButton(onClick = viewModel::retryMethods) { Text("Try again") } })
                        is LoadState.Loaded -> {
                            Spacer(Modifier.height(12.dp))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                methods.value.forEach { m ->
                                    ChoiceChip(
                                        m.name,
                                        selected = state.deliveryMethod?.code == m.code,
                                        onClick = { if (!state.methodLocked) viewModel.selectDeliveryMethod(m) },
                                    )
                                }
                            }
                            state.errors["delivery_method"]?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 6.dp))
                            }
                        }
                    }

                    when (val fields = state.fields) {
                        null -> {}
                        LoadState.Loading -> Box(Modifier.fillMaxWidth().padding(16.dp)) { CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) }
                        is LoadState.Failed -> Banner(fields.message, tone = BannerTone.Error, action = { TextButton(onClick = viewModel::retryFields) { Text("Try again") } })
                        is LoadState.Loaded -> {
                            Spacer(Modifier.height(24.dp))
                            Eyebrow("${state.deliveryMethod?.name ?: "Payout"} details")
                            Spacer(Modifier.height(10.dp))
                            fields.value.forEach { field ->
                                DynamicField(field, state.values[field.key].orEmpty(), state.errors[field.key]) {
                                    viewModel.onFieldChange(field.key, it)
                                }
                            }
                        }
                    }

                    state.formError?.let {
                        Spacer(Modifier.height(16.dp))
                        Banner(it, tone = BannerTone.Error, icon = Icons.Outlined.ErrorOutline)
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}

/** One input built from the server's definition of what this destination needs. */
@Composable
private fun DynamicField(field: BeneficiaryField, value: String, error: String?, onChange: (String) -> Unit) {
    val keyboard = when {
        field.key.contains("msisdn") || field.key.contains("phone") -> KeyboardType.Phone
        field.dataType.equals("NUMBER", ignoreCase = true) || field.dataType.equals("INTEGER", ignoreCase = true) -> KeyboardType.Number
        field.dataType.equals("EMAIL", ignoreCase = true) -> KeyboardType.Email
        else -> KeyboardType.Text
    }
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(if (field.required) field.label else "${field.label} (optional)") },
        singleLine = true,
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(keyboardType = keyboard, imeAction = ImeAction.Next),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
    )
}
