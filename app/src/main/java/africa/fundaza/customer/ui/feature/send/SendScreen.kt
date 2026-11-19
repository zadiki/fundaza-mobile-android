package africa.fundaza.customer.ui.feature.send

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import africa.fundaza.customer.domain.model.Method
import africa.fundaza.customer.core.money.Money
import africa.fundaza.customer.core.money.formatRate
import africa.fundaza.customer.data.remote.prettyCode
import africa.fundaza.customer.domain.model.ChargeType
import africa.fundaza.customer.domain.model.Corridor
import africa.fundaza.customer.domain.model.Quote
import africa.fundaza.customer.domain.model.SendingPurpose
import africa.fundaza.customer.domain.model.TransferDocumentType
import africa.fundaza.customer.ui.components.Avatar
import africa.fundaza.customer.ui.components.Banner
import africa.fundaza.customer.ui.components.BannerTone
import africa.fundaza.customer.ui.components.ChoiceChip
import africa.fundaza.customer.ui.components.ErrorState
import africa.fundaza.customer.ui.components.Eyebrow
import africa.fundaza.customer.ui.components.FCard
import africa.fundaza.customer.ui.components.FundazaTopBar
import africa.fundaza.customer.ui.components.KeyValueRow
import africa.fundaza.customer.ui.components.LoadState
import africa.fundaza.customer.ui.components.LoadingState
import africa.fundaza.customer.ui.components.PrimaryButton
import africa.fundaza.customer.ui.components.SecondaryButton
import africa.fundaza.customer.ui.components.SelectField
import africa.fundaza.customer.ui.components.flagOf
import africa.fundaza.customer.ui.components.formatMinSec
import africa.fundaza.customer.ui.components.rememberSecondsUntil
import africa.fundaza.customer.ui.navigation.AddRecipientRoute
import africa.fundaza.customer.ui.theme.Bricolage
import africa.fundaza.customer.ui.theme.MonoStyle
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import africa.fundaza.customer.ui.components.RollingText
import africa.fundaza.customer.ui.components.reveal
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SendScreen(
    onBack: () -> Unit,
    createdBeneficiaryId: Long?,
    onCreatedBeneficiaryConsumed: () -> Unit,
    onAddRecipient: (AddRecipientRoute) -> Unit,
    onSent: (String) -> Unit,
    viewModel: SendViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is SendEvent.Sent -> onSent(event.reference)
            }
        }
    }
    LaunchedEffect(createdBeneficiaryId) {
        if (createdBeneficiaryId != null) {
            viewModel.onRecipientSaved(createdBeneficiaryId)
            onCreatedBeneficiaryConsumed()
        }
    }

    // Ask once per visit to read the phone's location, to tell which country they send from.
    val context = LocalContext.current
    var askedForLocation by rememberSaveable { mutableStateOf(false) }
    val locationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.onLocationPermissionGranted()
    }
    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted && !askedForLocation) {
            askedForLocation = true
            locationPermission.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
    }

    val goBack = { if (!viewModel.back()) onBack() }
    BackHandler(onBack = goBack)

    val imeVisible = WindowInsets.isImeVisible

    Scaffold(
        topBar = {
            Column {
                FundazaTopBar(title = state.step.title, onBack = goBack)
                StepBar(state.step)
            }
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.background) {
                // While typing, the keyboard owns the bottom of the screen: hide the button so the list keeps the room.
                if (!imeVisible) Column(Modifier.navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp)) {
                    if (state.step == SendStep.Review) {
                        PrimaryButton(
                            text = state.quote?.let {
                                if (state.paysFromWallet) "Pay ${it.totalPayable.format()} from wallet" else "Send ${it.totalPayable.format()}"
                            } ?: "Send",
                            onClick = viewModel::confirm,
                            enabled = state.canContinue,
                            loading = state.submitting,
                        )
                    } else {
                        PrimaryButton("Continue", onClick = viewModel::next, enabled = state.canContinue)
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).consumeWindowInsets(padding).imePadding().fillMaxSize()) {
            when (val corridors = state.corridors) {
                LoadState.Loading -> LoadingState()
                is LoadState.Failed -> ErrorState(corridors.message, onRetry = viewModel::loadCorridors)
                is LoadState.Loaded -> if (corridors.value.isEmpty()) {
                    ErrorState("No routes are open for sending right now. Please check back later.", onRetry = viewModel::loadCorridors)
                } else {
                    AnimatedContent(
                        targetState = state.step,
                        transitionSpec = {
                            val forward = targetState.ordinal > initialState.ordinal
                            (slideInHorizontally { if (forward) it / 3 else -it / 3 } + fadeIn()) togetherWith
                                (slideOutHorizontally { if (forward) -it / 3 else it / 3 } + fadeOut())
                        },
                        label = "step",
                    ) { step ->
                        val scroll = rememberScrollState()
                        Column(
                            Modifier
                                .fillMaxSize()
                                .verticalScroll(scroll)
                                .padding(horizontal = 20.dp, vertical = 12.dp),
                        ) {
                            when (step) {
                                SendStep.Amount -> AmountStep(state, viewModel)
                                SendStep.Recipient -> RecipientStep(state, viewModel, scroll, onAddRecipient)
                                SendStep.Details -> DetailsStep(state, viewModel)
                                SendStep.Review -> ReviewStep(state, viewModel)
                            }
                            Spacer(Modifier.height(24.dp))
                        }
                    }
                }
            }
        }
    }

    if (state.passwordPrompt) {
        WalletPasswordDialog(
            amount = state.quote?.totalPayable?.format(),
            submitting = state.submitting,
            error = state.passwordError,
            onConfirm = viewModel::confirmWithPassword,
            onDismiss = viewModel::dismissPasswordPrompt,
        )
    }
}

/** What happens after sending, for the way to pay chosen — so nobody is surprised on the next screen. */
private fun paymentHint(method: Method): String = when {
    method.isWallet -> "Paid straight from your wallet. We'll ask for your password before it goes."
    method.code == Method.MOBILE_MONEY -> "After you confirm, we'll send an M-Pesa prompt to your phone, or you can pay by paybill."
    method.code == "BANK_TRANSFER" -> "After you confirm, we'll show the account and reference to pay into."
    method.code == "CARD" -> "Card payment opens on the Fundaza website after you confirm."
    else -> "After you confirm, we'll show you how to pay."
}

/** Paying from the wallet moves money at once, so the customer says their password again first. */
@Composable
private fun WalletPasswordDialog(
    amount: String?,
    submitting: Boolean,
    error: String?,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var password by rememberSaveable { mutableStateOf("") }
    var visible by rememberSaveable { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.Lock, contentDescription = null) },
        title = { Text("Confirm with your password") },
        text = {
            Column {
                Text(
                    amount?.let { "$it will leave your wallet straight away. Enter your Fundaza password to confirm." }
                        ?: "Enter your Fundaza password to pay from your wallet.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    singleLine = true,
                    enabled = !submitting,
                    isError = error != null,
                    supportingText = error?.let { { Text(it) } },
                    visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onConfirm(password) }),
                    trailingIcon = {
                        IconButton(onClick = { visible = !visible }) {
                            Icon(
                                if (visible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                contentDescription = if (visible) "Hide password" else "Show password",
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(password) }, enabled = !submitting && password.isNotBlank()) {
                Text(if (submitting) "Paying…" else "Pay")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !submitting) { Text("Cancel") }
        },
    )
}

@Composable
private fun StepBar(step: SendStep) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        SendStep.entries.forEach { s ->
            val color by animateColorAsState(
                if (s.ordinal <= step.ordinal) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outlineVariant,
                label = "step",
            )
            Box(Modifier.weight(1f).height(4.dp).clip(CircleShape).background(color))
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Step 1: route and amount

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AmountStep(state: SendUiState, vm: SendViewModel) {
    // The sender is taken to be sending from the country they're in, so only where to is asked.
    SelectField(
        label = "Sending to",
        options = state.destinations,
        selected = state.corridor,
        optionLabel = { "${flagOf(it.destination.iso2)}  ${it.destination.name}" },
        onSelect = vm::selectCorridor,
    )
    Spacer(Modifier.height(16.dp))

    // The two halves of the transfer, stacked like a ticket.
    FCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
        Column(Modifier.padding(20.dp)) {
            Eyebrow("You send")
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                BasicTextField(
                    value = state.amountInput,
                    onValueChange = vm::onAmountChange,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    textStyle = MaterialTheme.typography.displayMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.secondary),
                    modifier = Modifier.weight(1f),
                    decorationBox = { inner ->
                        if (state.amountInput.isEmpty()) {
                            Text("0", style = MaterialTheme.typography.displayMedium, color = MaterialTheme.colorScheme.outline)
                        }
                        inner()
                    },
                )
                CurrencyPicker(state.corridor?.sendCurrencies.orEmpty(), state.sendCurrency, vm::selectSendCurrency)
            }
            AnimatedVisibility(state.amountError != null) {
                Text(
                    state.amountError.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
        QuoteLine(state)
        Column(Modifier.padding(20.dp)) {
            Eyebrow("They receive")
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    val quote = state.quote
                    // The figure rolls to its new value as the amount is typed.
                    RollingText(
                        quote?.receiveAmount?.formatAmount() ?: "—",
                        style = MaterialTheme.typography.displayMedium,
                        color = if (quote != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    )
                }
                CurrencyPicker(state.corridor?.receiveCurrencies.orEmpty(), state.receiveCurrency, vm::selectReceiveCurrency)
            }
        }
    }

    if (state.quoteError != null) {
        Spacer(Modifier.height(12.dp))
        Banner(
            state.quoteError,
            tone = BannerTone.Error,
            icon = Icons.Outlined.ErrorOutline,
            action = { TextButton(onClick = vm::retryQuote, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) { Text("Try again") } },
        )
    }

    when (val methods = state.methods) {
        null, LoadState.Loading -> {}
        is LoadState.Failed -> {
            Spacer(Modifier.height(12.dp))
            Banner(methods.message, tone = BannerTone.Error)
        }
        is LoadState.Loaded -> {
            if (methods.value.delivery.isNotEmpty()) {
                Spacer(Modifier.height(24.dp))
                Eyebrow("How they get it")
                Spacer(Modifier.height(10.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    methods.value.delivery.forEach { m ->
                        ChoiceChip(m.name, selected = state.deliveryMethod?.code == m.code, onClick = { vm.selectDeliveryMethod(m) })
                    }
                }
            }
        }
    }

    val fastPrice = state.quote?.fastArrivalPrice
    if (fastPrice != null || state.fastArrival) {
        Spacer(Modifier.height(20.dp))
        FCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Bolt, null, tint = MaterialTheme.colorScheme.secondary)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Fast arrival", style = MaterialTheme.typography.titleSmall)
                    Text(
                        fastPrice?.let { "Adds ${it.format()}" } ?: "Priority payout",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = state.fastArrival, onCheckedChange = vm::setFastArrival)
            }
        }
    }

    state.quote?.let { quote ->
        Spacer(Modifier.height(20.dp))
        Breakdown(quote)
    }
}

/** The perforation between "you send" and "they receive", carrying the rate. */
@Composable
private fun QuoteLine(state: SendUiState) {
    val quote = state.quote
    // Loading, priced and empty cross-fade rather than snapping between each other.
    val phase = when {
        state.quoting -> 0
        quote != null -> 1
        else -> 2
    }
    AnimatedContent(
        targetState = phase,
        transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(150)) },
        modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant),
        label = "quoteLine",
    ) { shownPhase ->
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when {
            shownPhase == 0 -> {
                CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
                Text("Getting today's rate…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            shownPhase == 1 && quote != null -> {
                Text(
                    formatRate(quote.customerRate, quote.sendAmount.currency, quote.receiveAmount.currency),
                    style = MonoStyle,
                    modifier = Modifier.weight(1f),
                )
                QuoteTimer(quote)
            }
            else -> Text(
                "Enter an amount to see the rate and fees",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    }
}

@Composable
private fun QuoteTimer(quote: Quote) {
    val left by rememberSecondsUntil(quote.expiresAt)
    val seconds = left ?: return
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Outlined.Schedule, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(4.dp))
        Text(
            if (seconds > 0) formatMinSec(seconds) else "expired",
            style = MonoStyle,
            color = if (seconds < 60) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CurrencyPicker(options: List<String>, selected: String?, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable(enabled = options.size > 1) { expanded = true }
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(selected ?: "—", style = MaterialTheme.typography.titleMedium.copy(fontFamily = Bricolage))
            if (options.size > 1) Text(" ▾", style = MaterialTheme.typography.bodySmall)
        }
        androidx.compose.material3.DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { code ->
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text(code) },
                    onClick = {
                        onSelect(code)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun Breakdown(quote: Quote, modifier: Modifier = Modifier) {
    FCard(modifier.animateContentSize()) {
        KeyValueRow("You send", quote.sendAmount.format())
        quote.charges.forEach { charge ->
            KeyValueRow(
                if (charge.type == ChargeType.TAX) charge.label else "+ ${charge.label}",
                charge.amount.format(),
            )
        }
        HorizontalDivider(Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
        KeyValueRow("Total to pay", quote.totalPayable.format(), strong = true)
    }
}

// ---------------------------------------------------------------------------------------------
// Step 2: recipient

@Composable
private fun RecipientStep(state: SendUiState, vm: SendViewModel, scroll: ScrollState, onAddRecipient: (AddRecipientRoute) -> Unit) {
    val country = state.destination
    val method = state.deliveryMethod
    val currency = state.receiveCurrency

    Text(
        "Paying ${state.quote?.receiveAmount?.format().orEmpty()} by ${method?.name?.lowercase().orEmpty()} in ${state.corridor?.destination?.name.orEmpty()}.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(16.dp))

    when (val recipients = state.recipients) {
        LoadState.Loading -> Box(Modifier.fillMaxWidth().height(160.dp)) { LoadingState() }
        is LoadState.Failed -> Banner(
            recipients.message,
            tone = BannerTone.Error,
            action = { TextButton(onClick = { vm.loadRecipients() }) { Text("Try again") } },
        )
        is LoadState.Loaded -> {
            val destinationName = state.corridor?.destination?.name ?: "their country"
            val newRecipient = { onAddRecipient(AddRecipientRoute(country, method?.code, currency)) }
            val hasAnyone = state.readyRecipients.isNotEmpty() || state.recipientsNeedingDetails.isNotEmpty()
            if (hasAnyone) {
                RecipientSearch(
                    query = state.recipientQuery,
                    onQueryChange = vm::onRecipientQueryChange,
                    onNewRecipient = newRecipient,
                    scroll = scroll,
                )
                if (state.recipientQuery.isNotBlank()) {
                    val matches = state.shownReadyRecipients.size + state.shownRecipientsNeedingDetails.size
                    Text(
                        if (matches == 1) "1 match" else "$matches matches",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp, top = 8.dp),
                    )
                }
                Spacer(Modifier.height(16.dp))
                if (state.shownReadyRecipients.isEmpty() && state.shownRecipientsNeedingDetails.isEmpty()) {
                    Text("No one matches “${state.recipientQuery.trim()}”", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Check the spelling, or add them as a new recipient.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(16.dp))
                }
            }
            if (!hasAnyone) {
                Text("No one here yet", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Add the person you're paying. We'll ask only for what $destinationName requires.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
            }

            if (state.shownReadyRecipients.isNotEmpty()) {
                Eyebrow("Ready to receive")
                Spacer(Modifier.height(10.dp))
            }
            state.shownReadyRecipients.forEach { (person, way) ->
                val selected = state.recipientId == person.id
                val border by animateFloatAsState(if (selected) 2f else 1f, label = "border")
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .border(
                            border.dp,
                            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                            RoundedCornerShape(18.dp),
                        )
                        .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                        .clickable { vm.selectRecipient(person.id) }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Avatar(person.initials)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(person.fullName, style = MaterialTheme.typography.titleSmall)
                        Text("${way.deliveryMethodName} · ${way.summary}", style = MonoStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    RadioButton(selected = selected, onClick = { vm.selectRecipient(person.id) })
                }
            }

            if (state.shownRecipientsNeedingDetails.isNotEmpty() && country != null && method != null) {
                Spacer(Modifier.height(if (state.shownReadyRecipients.isEmpty()) 0.dp else 16.dp))
                Eyebrow("Add a way to receive")
                Text(
                    "Tap someone to add their ${method.name.lowercase()} details for $destinationName${currency?.let { " in $it" }.orEmpty()}.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 10.dp),
                )
                state.shownRecipientsNeedingDetails.forEach { person ->
                    val current = person.ways.firstOrNull()
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(18.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                            .clickable { onAddRecipient(AddRecipientRoute(country, method.code, currency, person.id)) }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Avatar(person.initials)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(person.fullName, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                current?.let { "${flagOf(it.countryIso2)}  Receives by ${it.deliveryMethodName.lowercase()} · ${it.currency}" }
                                    ?: "No payout details yet",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Row(
                            Modifier
                                .clip(RoundedCornerShape(50))
                                .background(MaterialTheme.colorScheme.primaryContainer)
                                .padding(start = 8.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Outlined.Add, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Add", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            SecondaryButton(text = "New recipient", onClick = newRecipient)
        }
    }
}

/**
 * A pill-shaped search box with a round "new recipient" button beside it. On focus it scrolls itself
 * to the top of the page so the matches show between it and the keyboard.
 */
@Composable
private fun RecipientSearch(
    query: String,
    onQueryChange: (String) -> Unit,
    onNewRecipient: () -> Unit,
    scroll: ScrollState,
) {
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    var focused by remember { mutableStateOf(false) }
    var top by remember { mutableIntStateOf(0) }
    val borderColor by animateColorAsState(
        if (focused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
        label = "searchBorder",
    )
    val shape = RoundedCornerShape(50)

    Row(
        Modifier.fillMaxWidth().onGloballyPositioned { top = it.positionInParent().y.toInt() },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
            modifier = Modifier
                .weight(1f)
                .height(52.dp)
                .onFocusChanged { state ->
                    focused = state.isFocused
                    if (state.isFocused) scope.launch {
                        // Give the keyboard a moment to open so the page has its final height.
                        delay(250)
                        scroll.animateScrollTo(top)
                    }
                },
            decorationBox = { field ->
                Row(
                    Modifier
                        .fillMaxSize()
                        .clip(shape)
                        .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                        .border(if (focused) 2.dp else 1.dp, borderColor, shape)
                        .padding(start = 16.dp, end = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Outlined.Search,
                        contentDescription = null,
                        tint = if (focused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                        if (query.isEmpty()) {
                            Text(
                                "Search recipients",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        field()
                    }
                    AnimatedVisibility(query.isNotEmpty(), enter = fadeIn(), exit = fadeOut()) {
                        IconButton(onClick = { onQueryChange("") }, modifier = Modifier.size(40.dp)) {
                            Icon(Icons.Outlined.Close, "Clear search", modifier = Modifier.size(18.dp))
                        }
                    }
                }
            },
        )
        Spacer(Modifier.width(10.dp))
        FilledTonalIconButton(onClick = onNewRecipient, modifier = Modifier.size(52.dp), shape = CircleShape) {
            Icon(Icons.Outlined.PersonAdd, "New recipient")
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Step 3: purpose and documents

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetailsStep(state: SendUiState, vm: SendViewModel) {
    Text(
        "Regulators ask us to record why money is sent. Pick the closest reason.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(16.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SendingPurpose.entries.forEach { p ->
            ChoiceChip(p.label, selected = state.purpose == p, onClick = { vm.selectPurpose(p) })
        }
    }

    Spacer(Modifier.height(28.dp))
    Eyebrow(if (state.documentsRequired) "Supporting documents · required" else "Supporting documents · optional")
    Spacer(Modifier.height(8.dp))
    if (state.documentsRequired) {
        Banner(
            "A ${state.purpose?.label?.lowercase()} needs evidence: an invoice, a sale agreement, or proof of where the money came from.",
            tone = BannerTone.Warning,
            icon = Icons.Outlined.Info,
        )
        Spacer(Modifier.height(12.dp))
    }

    state.documents.forEach { doc ->
        Row(
            Modifier.fillMaxWidth().padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.Description, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(doc.fileName, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(doc.typeName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = { vm.removeDocument(doc.id) }) { Icon(Icons.Outlined.Close, "Remove ${doc.fileName}") }
        }
    }

    var type by rememberSaveable { mutableStateOf(TransferDocumentType.INVOICE) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.uploadDocument(type, uri.toString())
    }
    Spacer(Modifier.height(8.dp))
    SelectField(
        label = "Document type",
        options = TransferDocumentType.entries,
        selected = type,
        optionLabel = { it.label },
        onSelect = { type = it },
    )
    Spacer(Modifier.height(8.dp))
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .border(1.5.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
            .clickable(enabled = !state.uploading) { picker.launch(arrayOf("application/pdf", "image/*")) }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (state.uploading) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(10.dp))
            Text("Uploading…", style = MaterialTheme.typography.labelLarge)
        } else {
            Icon(Icons.Outlined.AttachFile, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Attach a PDF or photo", style = MaterialTheme.typography.labelLarge)
        }
    }
    state.documentError?.let {
        Spacer(Modifier.height(8.dp))
        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    }

    if (state.checkingGoods) {
        Spacer(Modifier.height(16.dp))
        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
    }
    if (state.goodsNeeded) GoodsSection(state, vm)
}

// ---------------------------------------------------------------------------------------------
// Step 4: review

/** The way to pay, asked on the last step; the hint says what happens once they confirm. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PaymentChoice(state: SendUiState, vm: SendViewModel) {
    val payments = state.paymentChoices
    if (payments.isNotEmpty()) {
        Spacer(Modifier.height(20.dp))
        Eyebrow("How you pay", modifier = Modifier.reveal(order = 1))
        Spacer(Modifier.height(10.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            payments.forEachIndexed { i, m ->
                ChoiceChip(
                    modifier = Modifier.reveal(order = 2 + i),
                    text = m.name,
                    selected = state.paymentMethod?.code == m.code,
                    onClick = { vm.selectPaymentMethod(m) },
                    enabled = m.available,
                    detail = m.balance?.let { "Balance ${it.format()}" },
                )
            }
        }
        AnimatedContent(
            targetState = state.paymentMethod,
            transitionSpec = {
                (fadeIn(tween(250)) + slideInVertically { it / 2 }) togetherWith fadeOut(tween(120)) using SizeTransform(clip = false)
            },
            label = "paymentHint",
        ) { chosen ->
            if (chosen != null) {
                Text(
                    paymentHint(chosen),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun ReviewStep(state: SendUiState, vm: SendViewModel) {
    val (person, way) = state.selectedRecipient ?: return

    if (state.repriced) {
        Banner(
            title = "The rate was refreshed",
            text = "Your earlier price expired, so we priced it again. Check the figures below before you send.",
            tone = BannerTone.Warning,
            icon = Icons.Outlined.Info,
        )
        Spacer(Modifier.height(16.dp))
    }

    // The headline: what lands. Changing the way to pay re-prices, so the figure may be in flight.
    val shown = state.quote
    Column(
        Modifier
            .reveal(order = 0)
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.primary)
            .padding(24.dp),
    ) {
        Eyebrow("${person.fullName.substringBefore(' ')} receives", color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f))
        Spacer(Modifier.height(6.dp))
        RollingText(
            shown?.receiveAmount?.format() ?: "—",
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.secondary,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "${way.deliveryMethodName} · ${way.summary}",
            style = MonoStyle,
            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f),
        )
    }

    // How the customer pays comes last: what they send and to whom is settled, and the fees below follow the choice.
    PaymentChoice(state, vm)

    if (state.quoteError != null) {
        Spacer(Modifier.height(12.dp))
        Banner(
            state.quoteError,
            tone = BannerTone.Error,
            icon = Icons.Outlined.ErrorOutline,
            action = { TextButton(onClick = vm::retryQuote, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) { Text("Try again") } },
        )
    }

    val quote = shown ?: run {
        if (state.quoting) {
            Spacer(Modifier.height(24.dp))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
            }
        }
        return
    }

    Spacer(Modifier.height(16.dp))
    Breakdown(quote, Modifier.reveal(order = 3))

    Spacer(Modifier.height(16.dp))
    FCard(Modifier.reveal(order = 4)) {
        KeyValueRow("Recipient", person.fullName)
        KeyValueRow("Exchange rate", formatRate(quote.customerRate, quote.sendAmount.currency, quote.receiveAmount.currency), mono = true)
        state.paymentMethod?.let { KeyValueRow("You pay by", it.name) }
        state.paymentMethod?.balance?.let { KeyValueRow("Wallet balance", it.format()) }
        state.purpose?.let { KeyValueRow("Reason", it.label) }
        if (state.documents.isNotEmpty()) KeyValueRow("Documents", "${state.documents.size} attached")
        if (quote.fastArrivalChosen) KeyValueRow("Fast arrival", "Yes")
    }

    Spacer(Modifier.height(16.dp))
    val left by rememberSecondsUntil(quote.expiresAt)
    left?.let { seconds ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Schedule, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(6.dp))
            Text(
                if (seconds > 0) "This price is held for ${formatMinSec(seconds)}" else "This price has expired — sending will re-price it first",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    state.submitError?.let {
        Spacer(Modifier.height(12.dp))
        Banner(it, tone = BannerTone.Error, icon = Icons.Outlined.ErrorOutline)
    }
}
