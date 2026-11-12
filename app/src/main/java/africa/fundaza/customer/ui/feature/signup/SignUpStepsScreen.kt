package africa.fundaza.customer.ui.feature.signup

import africa.fundaza.customer.domain.model.SignUp
import africa.fundaza.customer.domain.model.SignUpStep
import africa.fundaza.customer.ui.components.Banner
import africa.fundaza.customer.ui.components.BannerTone
import africa.fundaza.customer.ui.components.DateField
import africa.fundaza.customer.ui.components.FundazaTopBar
import africa.fundaza.customer.ui.components.PrimaryButton
import africa.fundaza.customer.ui.components.SecondaryButton
import africa.fundaza.customer.ui.components.SelectField
import africa.fundaza.customer.ui.components.flagOf
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.MarkEmailUnread
import androidx.compose.material.icons.outlined.Sms
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.LocalDate

/**
 * Everything between "account created" and "customer exists": confirm the
 * email, confirm the phone when the operator asks for it, then the person's
 * own details. The server says which step is next; this screen follows it.
 */
@Composable
fun SignUpStepsScreen(
    signUp: SignUp,
    onSignOut: () -> Unit,
    viewModel: SignUpStepsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirmLeave by rememberSaveable { mutableStateOf(false) }

    // The email link is opened in a browser; coming back to the app is the moment to look again.
    LifecycleResumeEffect(signUp.nextStep) {
        if (signUp.nextStep == SignUpStep.EMAIL) viewModel.checkEmail(userAsked = false)
        onPauseOrDispose { }
    }

    Scaffold(
        topBar = {
            FundazaTopBar(
                "Finish signing up",
                actions = { TextButton(onClick = { confirmLeave = true }) { Text("Sign out") } },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 8.dp),
        ) {
            Steps(signUp)
            Spacer(Modifier.height(24.dp))
            AnimatedContent(signUp.nextStep, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "step") { step ->
                Column {
                    when (step) {
                        SignUpStep.EMAIL -> EmailStep(signUp, state, viewModel)
                        SignUpStep.PHONE -> PhoneStep(signUp, state, viewModel)
                        SignUpStep.DETAILS -> DetailsStep(state, viewModel)
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text("Sign out?") },
            text = { Text("Your account is kept. Sign in again with the same email and password to carry on where you left off.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmLeave = false
                    onSignOut()
                }) { Text("Sign out") }
            },
            dismissButton = { TextButton(onClick = { confirmLeave = false }) { Text("Stay") } },
        )
    }
}

/** Where they are: account, email, (phone), details, then ID. */
@Composable
private fun Steps(signUp: SignUp) {
    val steps = buildList {
        add("Email" to signUp.emailVerified)
        if (signUp.phoneVerificationRequired) add("Phone" to signUp.phoneVerified)
        add("Details" to false)
        add("ID" to false)
    }
    val current = when (signUp.nextStep) {
        SignUpStep.EMAIL -> "Email"
        SignUpStep.PHONE -> "Phone"
        SignUpStep.DETAILS -> "Details"
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        steps.forEach { (label, done) ->
            val active = label == current
            Column(Modifier.weight(1f)) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(
                            when {
                                done -> MaterialTheme.colorScheme.primary
                                active -> MaterialTheme.colorScheme.secondary
                                else -> MaterialTheme.colorScheme.outlineVariant
                            },
                        ),
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun StepHeader(icon: androidx.compose.ui.graphics.vector.ImageVector?, title: String, body: String) {
    if (icon != null) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 12.dp))
    }
    Text(title, style = MaterialTheme.typography.headlineSmall)
    Spacer(Modifier.height(8.dp))
    Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(20.dp))
}

@Composable
private fun EmailStep(signUp: SignUp, state: SignUpStepsUiState, vm: SignUpStepsViewModel) {
    StepHeader(
        Icons.Outlined.MarkEmailUnread,
        "Confirm your email",
        "We sent a link to ${signUp.email}. Open it on this phone or any other device, then come back here. " +
            "If the page asks you to sign in, use the email and password you just chose.",
    )
    PrimaryButton(text = "I've confirmed it", loading = state.checking, onClick = { vm.checkEmail() })
    Spacer(Modifier.height(12.dp))
    SecondaryButton(text = if (state.resending) "Sending…" else "Send the link again", onClick = vm::resendEmail, enabled = !state.resending)
    state.emailNote?.let {
        Spacer(Modifier.height(16.dp))
        Banner(it, tone = BannerTone.Info, icon = Icons.Outlined.Info)
    }
}

@Composable
private fun PhoneStep(signUp: SignUp, state: SignUpStepsUiState, vm: SignUpStepsViewModel) {
    StepHeader(
        Icons.Outlined.Sms,
        "Confirm your phone",
        "We'll text a 6-digit code to ${signUp.phone}.",
    )
    if (!state.codeSent) {
        PrimaryButton(text = "Text me a code", loading = state.sendingCode, onClick = vm::sendCode)
    } else {
        OutlinedTextField(
            value = state.code,
            onValueChange = vm::onCode,
            label = { Text("Code") },
            singleLine = true,
            isError = state.errors["code"] != null,
            supportingText = state.errors["code"]?.let { { Text(it) } },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        PrimaryButton(text = "Confirm", loading = state.confirmingCode, onClick = vm::confirmCode)
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = vm::sendCode, enabled = !state.sendingCode) { Text("Text me a new code") }
    }
    state.formError?.let {
        Spacer(Modifier.height(12.dp))
        Banner(it, tone = BannerTone.Error, icon = Icons.Outlined.ErrorOutline)
    }
}

@Composable
private fun DetailsStep(state: SignUpStepsUiState, vm: SignUpStepsViewModel) {
    StepHeader(
        null,
        "About you",
        "Exactly as they appear on your national ID or passport. We check them against your ID in the next step.",
    )
    val fieldModifier = Modifier.fillMaxWidth()
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedTextField(
            value = state.firstName,
            onValueChange = vm::onFirstName,
            label = { Text("First and middle names") },
            singleLine = true,
            isError = state.errors["first_name"] != null,
            supportingText = state.errors["first_name"]?.let { { Text(it) } },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
            shape = RoundedCornerShape(14.dp),
            modifier = fieldModifier,
        )
        OutlinedTextField(
            value = state.lastName,
            onValueChange = vm::onLastName,
            label = { Text("Last name") },
            singleLine = true,
            isError = state.errors["last_name"] != null,
            supportingText = state.errors["last_name"]?.let { { Text(it) } },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
            shape = RoundedCornerShape(14.dp),
            modifier = fieldModifier,
        )
        DateField(
            label = "Date of birth",
            value = state.dateOfBirth,
            onChange = vm::onDateOfBirth,
            allowed = { it.isBefore(LocalDate.now()) },
            initialYear = LocalDate.now().year - 30,
            error = state.errors["date_of_birth"],
            modifier = fieldModifier,
        )
        SelectField(
            label = "Nationality",
            options = state.countries,
            selected = state.nationality,
            optionLabel = { "${flagOf(it.iso2)}  ${it.name}" },
            onSelect = vm::onNationality,
            error = state.errors["nationality_iso2"],
        )
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(
            value = state.addressLine1,
            onValueChange = vm::onAddressLine1,
            label = { Text("Home address (optional)") },
            singleLine = true,
            isError = state.errors["address_line1"] != null,
            supportingText = state.errors["address_line1"]?.let { { Text(it) } },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
            shape = RoundedCornerShape(14.dp),
            modifier = fieldModifier,
        )
        OutlinedTextField(
            value = state.addressCity,
            onValueChange = vm::onAddressCity,
            label = { Text("Town or city") },
            singleLine = true,
            isError = state.errors["address_city"] != null,
            supportingText = state.errors["address_city"]?.let { { Text(it) } },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
            shape = RoundedCornerShape(14.dp),
            modifier = fieldModifier,
        )
    }
    state.formError?.let {
        Spacer(Modifier.height(12.dp))
        Banner(it, tone = BannerTone.Error, icon = Icons.Outlined.ErrorOutline)
    }
    Spacer(Modifier.height(16.dp))
    PrimaryButton(text = "Continue to ID check", loading = state.finishing, onClick = vm::finish)
    Spacer(Modifier.height(8.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            "Signing up a company? Do it on fundaza.africa, where you can declare its owners.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
