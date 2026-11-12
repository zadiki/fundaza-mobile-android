package africa.fundaza.customer.ui.feature.signup

import africa.fundaza.customer.ui.components.Banner
import africa.fundaza.customer.ui.components.BannerTone
import africa.fundaza.customer.ui.components.ErrorState
import africa.fundaza.customer.ui.components.FundazaTopBar
import africa.fundaza.customer.ui.components.LoadState
import africa.fundaza.customer.ui.components.LoadingState
import africa.fundaza.customer.ui.components.PrimaryButton
import africa.fundaza.customer.ui.components.SelectField
import africa.fundaza.customer.ui.components.flagOf
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * The first step of signing up: who to reach and where they live. Everything
 * else (confirming the email, perhaps the phone, then the person's details and
 * their ID) follows once the account exists.
 */
@Composable
fun RegisterScreen(onBack: () -> Unit, viewModel: RegisterViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val focus = LocalFocusManager.current
    var showPassword by rememberSaveable { mutableStateOf(false) }

    Scaffold(topBar = { FundazaTopBar("Create your account", onBack = onBack) }) { padding ->
        when (val countries = state.countries) {
            LoadState.Loading -> LoadingState(Modifier.padding(padding))
            is LoadState.Failed -> ErrorState(countries.message, onRetry = viewModel::loadCountries, modifier = Modifier.padding(padding))
            is LoadState.Loaded -> Column(
                Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .imePadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    "It takes a few minutes. Have your national ID or passport nearby: we'll ask for it after you confirm your email.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))

                Field(
                    value = state.fullName,
                    onChange = viewModel::onFullName,
                    label = "Full name, as on your ID",
                    error = state.errors["full_name"],
                    keyboard = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                )
                Field(
                    value = state.email,
                    onChange = viewModel::onEmail,
                    label = "Email address",
                    error = state.errors["email"],
                    keyboard = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next, autoCorrectEnabled = false),
                )
                SelectField(
                    label = "Country you live in",
                    options = countries.value,
                    selected = state.residence,
                    optionLabel = { "${flagOf(it.iso2)}  ${it.name}" },
                    onSelect = viewModel::onResidence,
                    error = state.errors["residence_iso2"],
                )
                Spacer(Modifier.height(4.dp))
                Field(
                    value = state.phone,
                    onChange = viewModel::onPhone,
                    label = "Mobile number",
                    error = state.errors["phone"],
                    prefix = state.residence?.dialCode?.takeIf { it.isNotBlank() && !state.phone.startsWith("+") },
                    hint = "We may text a code to it to confirm it's yours.",
                    keyboard = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Next),
                )
                Field(
                    value = state.password,
                    onChange = viewModel::onPassword,
                    label = "Password",
                    error = state.errors["password"],
                    secret = !showPassword,
                    onToggleSecret = { showPassword = !showPassword },
                    keyboard = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
                )
                Field(
                    value = state.confirmation,
                    onChange = viewModel::onConfirmation,
                    label = "Password again",
                    secret = !showPassword,
                    keyboard = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                )

                state.formError?.let {
                    Spacer(Modifier.height(8.dp))
                    Banner(it, tone = BannerTone.Error, icon = Icons.Outlined.ErrorOutline)
                }

                Spacer(Modifier.height(16.dp))
                PrimaryButton(
                    text = "Create account",
                    loading = state.submitting,
                    onClick = {
                        focus.clearFocus()
                        viewModel.submit()
                    },
                )
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun Field(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    keyboard: KeyboardOptions,
    error: String? = null,
    hint: String? = null,
    prefix: String? = null,
    secret: Boolean = false,
    onToggleSecret: (() -> Unit)? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        isError = error != null,
        supportingText = (error ?: hint)?.let { { Text(it) } },
        prefix = prefix?.let { { Text("$it ") } },
        visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
        trailingIcon = onToggleSecret?.let {
            {
                IconButton(onClick = it) {
                    Icon(
                        if (secret) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
                        contentDescription = if (secret) "Show password" else "Hide password",
                    )
                }
            }
        },
        keyboardOptions = keyboard,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
    )
}
