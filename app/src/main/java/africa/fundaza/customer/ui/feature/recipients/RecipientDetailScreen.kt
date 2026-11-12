package africa.fundaza.customer.ui.feature.recipients

import africa.fundaza.customer.data.remote.prettyCode
import android.content.ClipData
import africa.fundaza.customer.domain.model.BeneficiaryWay
import africa.fundaza.customer.ui.theme.MonoStyle
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.util.Locale
import africa.fundaza.customer.domain.model.Beneficiary
import africa.fundaza.customer.ui.components.Avatar
import africa.fundaza.customer.ui.components.Banner
import africa.fundaza.customer.ui.components.BannerTone
import africa.fundaza.customer.ui.components.ErrorState
import africa.fundaza.customer.ui.components.Eyebrow
import africa.fundaza.customer.ui.components.FCard
import africa.fundaza.customer.ui.components.FundazaTopBar
import africa.fundaza.customer.ui.components.LoadState
import africa.fundaza.customer.ui.components.LoadingState
import africa.fundaza.customer.ui.components.SecondaryButton
import africa.fundaza.customer.ui.components.flagOf
import africa.fundaza.customer.ui.navigation.AddRecipientRoute
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun RecipientDetailScreen(
    onBack: () -> Unit,
    onAddWay: (AddRecipientRoute) -> Unit,
    viewModel: RecipientDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.deleted.collect { onBack() } }
    // Coming back from adding a way: show it.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.load() }

    Scaffold(
        topBar = {
            FundazaTopBar(
                "Recipient",
                onBack = onBack,
                actions = {
                    if (state.recipient is LoadState.Loaded) {
                        IconButton(onClick = { confirmDelete = true }, enabled = !state.deleting) {
                            Icon(Icons.Outlined.DeleteOutline, "Remove recipient")
                        }
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (val r = state.recipient) {
                LoadState.Loading -> LoadingState()
                is LoadState.Failed -> ErrorState(r.message, onRetry = viewModel::load)
                is LoadState.Loaded -> Details(r.value, state.deleteError) {
                    onAddWay(AddRecipientRoute(beneficiaryId = r.value.id))
                }
            }
        }
    }

    if (confirmDelete) {
        val name = state.recipient.valueOrNull?.fullName ?: "this recipient"
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Remove $name?") },
            text = { Text("They'll leave your list. Transfers you've already made to them are kept.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.delete()
                }) { Text("Remove", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Keep") } },
        )
    }
}

private val HeroTop = Color(0xFF0B2A21)
private val HeroBottom = Color(0xFF14523F)
private val Marigold = Color(0xFFF2A93B)
private val PaperOnHero = Color(0xFFF6F1E7)

@Composable
private fun Details(person: Beneficiary, deleteError: String?, onAddWay: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp),
    ) {
        Hero(person)

        if (deleteError != null) {
            Spacer(Modifier.height(16.dp))
            Banner(deleteError, tone = BannerTone.Error, icon = Icons.Outlined.ErrorOutline)
        }

        Spacer(Modifier.height(28.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Eyebrow("Ways to receive", Modifier.weight(1f))
            if (person.ways.isNotEmpty()) {
                Text(
                    person.ways.size.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(12.dp))

        if (person.ways.isEmpty()) {
            FCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MethodBadge(Icons.Outlined.AccountBalance)
                    Spacer(Modifier.width(14.dp))
                    Text(
                        "No payout details yet. Add a bank account or mobile wallet to send to " +
                            "${person.fullName.substringBefore(' ').toDisplayName()}.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        person.ways.forEach { way ->
            WayCard(way)
            Spacer(Modifier.height(14.dp))
        }

        Spacer(Modifier.height(4.dp))
        SecondaryButton("Add another way to receive", onClick = onAddWay)
        Spacer(Modifier.height(24.dp))
    }
}

/** Who they are, on the same forest panel as the home screen. */
@Composable
private fun Hero(person: Beneficiary) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(Brush.verticalGradient(listOf(HeroTop, HeroBottom)))
            .padding(horizontal = 20.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(76.dp).clip(CircleShape).background(Marigold),
            contentAlignment = Alignment.Center,
        ) {
            Text(person.initials, style = MaterialTheme.typography.titleLarge, color = HeroTop, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(16.dp))
        Text(
            person.fullName.toDisplayName(),
            style = MaterialTheme.typography.headlineSmall,
            color = PaperOnHero,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HeroChip("${flagOf(person.countryIso2)}  ${countryName(person.countryIso2)}")
            VerificationChip(person.verificationStatus)
        }
    }
}

@Composable
private fun HeroChip(text: String, dot: Color? = null) {
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(PaperOnHero.copy(alpha = 0.12f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (dot != null) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge, color = PaperOnHero)
    }
}

@Composable
private fun VerificationChip(status: String) {
    val dot = when (status.uppercase()) {
        "VERIFIED" -> Color(0xFF6FD3A6)
        "PENDING" -> Marigold
        "FAILED", "REJECTED" -> Color(0xFFF08A73)
        else -> PaperOnHero.copy(alpha = 0.5f)
    }
    HeroChip(status.prettyCode(), dot)
}

@Composable
private fun MethodBadge(icon: ImageVector) {
    Box(
        Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun WayCard(way: BeneficiaryWay) {
    val fields = way.fields.filterValues { it.isNotBlank() }
    FCard(contentPadding = PaddingValues(0.dp)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            MethodBadge(methodIcon(way.deliveryMethodCode))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(way.deliveryMethodName, style = MaterialTheme.typography.titleMedium)
                Text(
                    "${flagOf(way.countryIso2)}  ${countryName(way.countryIso2)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                way.currency,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.secondaryContainer)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
        fields.entries.forEach { (key, value) ->
            HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
            FieldRow(key, value)
        }
    }
}

@Composable
private fun FieldRow(key: String, raw: String) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val label = fieldLabel(key)
    val value = fieldValue(key, raw)
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(2.dp))
            Text(
                value,
                style = if (looksLikeCode(raw)) MonoStyle.copy(fontSize = 15.sp) else MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        if (isCopyable(key)) {
            IconButton(onClick = { scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(label, raw))) } }) {
                Icon(
                    Icons.Outlined.ContentCopy,
                    contentDescription = "Copy $label",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        } else {
            Spacer(Modifier.width(12.dp))
        }
    }
}

private fun methodIcon(code: String): ImageVector = when {
    "BANK" in code -> Icons.Outlined.AccountBalance
    "MOBILE" in code || "WALLET" in code -> Icons.Outlined.PhoneAndroid
    "CASH" in code -> Icons.Outlined.Payments
    else -> Icons.Outlined.SwapHoriz
}

private val ACRONYMS = setOf("id", "swift", "iban", "bic", "msisdn", "ifsc", "bsb", "aba", "cnaps")

/** chinese_id_number → "Chinese ID number". */
internal fun fieldLabel(key: String): String =
    key.split('_', ' ').filter { it.isNotBlank() }.mapIndexed { i, w ->
        when {
            w.lowercase() in ACRONYMS -> w.uppercase()
            i == 0 -> w.lowercase().replaceFirstChar { it.uppercase() }
            else -> w.lowercase()
        }
    }.joinToString(" ")

/** Country codes become names: "HKG" → "Hong Kong". Everything else is shown as saved. */
internal fun fieldValue(key: String, value: String): String =
    if (key.endsWith("country") && value.length in 2..3 && value.all { it.isLetter() }) countryName(value) else value

private val ISO3_TO_ISO2: Map<String, String> by lazy {
    Locale.getISOCountries().associateBy { runCatching { Locale("", it).isO3Country }.getOrDefault("") }
}

internal fun countryName(code: String): String {
    val iso2 = if (code.length == 3) ISO3_TO_ISO2[code.uppercase()] ?: return code else code
    return Locale("", iso2.uppercase()).getDisplayCountry(Locale.ENGLISH).ifBlank { code }
}

/** Account numbers, SWIFT codes and the like: worth copying exactly. */
private fun isCopyable(key: String): Boolean =
    listOf("number", "code", "iban", "msisdn", "swift").any { it in key.lowercase() } && !key.endsWith("country")

private fun looksLikeCode(value: String): Boolean =
    value.length >= 4 && value.none { it.isWhitespace() } && value.any { it.isDigit() || it.isUpperCase() } &&
        value.none { it.isLowerCase() }

/** "BEIJING MANAGEMENT (ASIA) LIMITED" → "Beijing Management (Asia) Limited"; mixed case is left alone. */
internal fun String.toDisplayName(): String =
    if (any { it.isLowerCase() }) this
    else lowercase().split(' ').joinToString(" ") { w ->
        w.replaceFirstChar { it.uppercase() }.let { if (it.startsWith("(")) "(" + it.drop(1).replaceFirstChar { c -> c.uppercase() } else it }
    }
