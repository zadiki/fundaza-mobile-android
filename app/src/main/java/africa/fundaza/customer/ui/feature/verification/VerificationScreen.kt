package africa.fundaza.customer.ui.feature.verification

import africa.fundaza.customer.domain.model.KycMode
import africa.fundaza.customer.domain.model.KycState
import africa.fundaza.customer.ui.components.Banner
import africa.fundaza.customer.ui.components.BannerTone
import africa.fundaza.customer.ui.components.DateField
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
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddAPhoto
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.HourglassTop
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.ReportProblem
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.LocalDate

/**
 * The customer's own identity verification. What it offers is what the server
 * says can be done next (`can_*`), so it never offers something that will be
 * refused. It never shows screening: the server never sends it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VerificationScreen(onBack: () -> Unit, viewModel: VerificationViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val launch by viewModel.launch.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(launch) {
        val token = launch?.token ?: return@LaunchedEffect
        viewModel.launched()
        val activity = context.findActivity() ?: return@LaunchedEffect
        ProviderVerification.launch(
            activity = activity,
            token = token,
            freshToken = viewModel::freshTokenBlocking,
            onSubmitted = viewModel::providerSubmitted,
            onClosed = { viewModel.load(refresh = true) },
            onFailed = viewModel::providerFailed,
        )
    }

    Scaffold(topBar = { FundazaTopBar("Verify your identity", onBack = onBack) }) { padding ->
        when (val kyc = state.kyc) {
            LoadState.Loading -> LoadingState(Modifier.padding(padding))
            is LoadState.Failed -> ErrorState(kyc.message, onRetry = { viewModel.load() }, modifier = Modifier.padding(padding))
            is LoadState.Loaded -> PullToRefreshBox(
                isRefreshing = state.refreshing,
                onRefresh = { viewModel.load(refresh = true) },
                modifier = Modifier.padding(padding).fillMaxSize(),
            ) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                ) {
                    StatusCard(kyc.value)
                    state.message?.let {
                        Spacer(Modifier.height(12.dp))
                        Banner(it, tone = BannerTone.Info, icon = Icons.Outlined.Info)
                    }
                    Spacer(Modifier.height(20.dp))
                    Actions(kyc.value, state, viewModel)
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
private fun StatusCard(kyc: KycState) {
    val (title, body, tone, icon) = when {
        kyc.verified -> Quad(
            "You're verified" + (kyc.tier?.let { " · $it" } ?: ""),
            kyc.expiresAt?.let { "Valid until $it. You can send up to your tier's limits." } ?: "You can send up to your tier's limits.",
            BannerTone.Info,
            Icons.Outlined.Verified,
        )
        kyc.pending -> Quad(
            "We're checking your details",
            "This usually takes a few minutes, sometimes longer. We'll tell you by notification, email or text when it's done.",
            BannerTone.Warning,
            Icons.Outlined.HourglassTop,
        )
        kyc.restart?.rejected == true -> Quad(
            "We couldn't verify you",
            "Check the email we sent for the reason. You can start again with clearer photos or a different document.",
            BannerTone.Error,
            Icons.Outlined.ReportProblem,
        )
        kyc.status == "VERIFIED" || kyc.status == "EXPIRED" -> Quad(
            "Your verification has expired",
            "Send an in-date ID to keep sending money.",
            BannerTone.Warning,
            Icons.Outlined.VerifiedUser,
        )
        else -> Quad(
            "Verify to start sending",
            "The law asks us to confirm who every customer is before they send money. You'll need your national ID or passport.",
            BannerTone.Warning,
            Icons.Outlined.VerifiedUser,
        )
    }
    Banner(body, title = title, tone = tone, icon = icon)
}

private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)

@Composable
private fun Actions(kyc: KycState, state: VerificationUiState, vm: VerificationViewModel) {
    kyc.restart?.let { restart ->
        val left = restart.attemptsLeft
        if (left == 0) {
            Text(
                "You've used all your attempts. Contact Fundaza support and we'll reopen it for you.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            PrimaryButton(text = "Start again", loading = state.busy, onClick = vm::restart)
            left?.let {
                Spacer(Modifier.height(6.dp))
                Text(
                    if (it == 1) "You can start again once more." else "You can start again $it more times.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        return
    }

    if (kyc.mode == KycMode.HOSTED && kyc.canStartVerification) {
        Text(
            "You'll take a photo of your ID and a short selfie. It takes about three minutes.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        PrimaryButton(
            text = if (kyc.pending) "Continue verification" else "Verify with your ID",
            loading = state.busy,
            onClick = vm::startHosted,
        )
        return
    }

    if (kyc.mode == KycMode.DOCUMENTS) {
        if (kyc.documents.isNotEmpty()) {
            Eyebrow("On file")
            Spacer(Modifier.height(8.dp))
            FCard {
                kyc.documents.forEach { doc ->
                    KeyValueRow(
                        doc.typeName,
                        listOfNotNull(doc.issuingCountryIso2?.let(::flagOf), doc.expiresAt?.let { "expires $it" }).joinToString("  "),
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
        }
        if (kyc.canAddIdentity) {
            IdentityForm(kyc, state, vm)
        }
        if (kyc.canSubmit) {
            Spacer(Modifier.height(20.dp))
            PrimaryButton(text = "Submit for review", loading = state.busy, onClick = vm::submit)
        }
        return
    }

    if (!kyc.verified && !kyc.pending) {
        Text(
            "Finish verification on fundaza.africa or at any Fundaza agent.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun IdentityForm(kyc: KycState, state: VerificationUiState, vm: VerificationViewModel) {
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.onFile(uri.toString(), context.displayName(uri))
    }

    Eyebrow(if (kyc.documents.isEmpty()) "Add your ID" else "Replace your ID")
    Spacer(Modifier.height(8.dp))
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SelectField(
            label = "Document",
            options = kyc.documentTypes,
            selected = state.type,
            optionLabel = { it.name },
            onSelect = vm::onType,
            error = state.errors["document_type"],
        )
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(
            value = state.number,
            onValueChange = vm::onNumber,
            label = { Text("Document number") },
            singleLine = true,
            isError = state.errors["document_number"] != null,
            supportingText = state.errors["document_number"]?.let { { Text(it) } },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth(),
        )
        SelectField(
            label = "Issued by",
            options = state.countries,
            selected = state.issuingCountry,
            optionLabel = { "${flagOf(it.iso2)}  ${it.name}" },
            onSelect = vm::onIssuingCountry,
            error = state.errors["issuing_country_iso2"],
        )
        Spacer(Modifier.height(4.dp))
        DateField(
            label = "Expiry date",
            value = state.expiresAt,
            onChange = vm::onExpiry,
            allowed = { !it.isBefore(LocalDate.now()) },
            error = state.errors["expires_at"],
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .border(1.5.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
                .clickable { picker.launch(arrayOf("image/*", "application/pdf")) }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(if (state.fileUri == null) Icons.Outlined.AddAPhoto else Icons.Outlined.Badge, null, Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(
                state.fileName ?: "Add a photo or scan of the document",
                style = MaterialTheme.typography.labelLarge,
            )
        }
        state.errors["file"]?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }
    Spacer(Modifier.height(16.dp))
    SecondaryButton(text = if (state.busy) "Saving…" else "Save document", onClick = vm::upload, enabled = !state.busy)
}

private fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun Context.displayName(uri: Uri): String? =
    contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { if (it.moveToFirst()) it.getString(0) else null }
