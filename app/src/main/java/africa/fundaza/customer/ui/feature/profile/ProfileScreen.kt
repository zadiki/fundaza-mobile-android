package africa.fundaza.customer.ui.feature.profile

import africa.fundaza.customer.BuildConfig
import africa.fundaza.customer.data.remote.prettyCode
import africa.fundaza.customer.domain.model.Customer
import africa.fundaza.customer.ui.components.Avatar
import africa.fundaza.customer.ui.components.Banner
import africa.fundaza.customer.ui.components.BannerTone
import africa.fundaza.customer.ui.components.Eyebrow
import africa.fundaza.customer.ui.components.FCard
import africa.fundaza.customer.ui.components.FundazaTopBar
import africa.fundaza.customer.ui.components.KeyValueRow
import africa.fundaza.customer.ui.components.LoadState
import africa.fundaza.customer.ui.components.SecondaryButton
import africa.fundaza.customer.ui.components.flagOf
import africa.fundaza.customer.ui.components.initialsOf
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun ProfileScreen(
    onSignOut: () -> Unit,
    onVerify: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirm by rememberSaveable { mutableStateOf(false) }

    Scaffold(modifier = modifier, topBar = { FundazaTopBar("Profile") }, contentWindowInsets = WindowInsets(0)) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        ) {
            val user = state.user
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(user?.name?.initialsOf() ?: "?", size = 64)
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(user?.name.orEmpty(), style = MaterialTheme.typography.headlineMedium)
                    Text(user?.email.orEmpty(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(24.dp))

            when (val c = state.customer) {
                LoadState.Loading -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                is LoadState.Failed -> Banner(c.message, tone = BannerTone.Error, action = { TextButton(onClick = viewModel::load) { Text("Try again") } })
                is LoadState.Loaded -> CustomerDetails(c.value, onVerify)
            }

            Spacer(Modifier.height(32.dp))
            SecondaryButton("Sign out", onClick = { confirm = true }, enabled = !state.signingOut)
            Spacer(Modifier.height(16.dp))
            Text(
                "Fundaza ${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
    }

    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Sign out?") },
            text = { Text("This device will stop receiving updates about your transfers until you sign in again.") },
            confirmButton = {
                TextButton(onClick = {
                    confirm = false
                    viewModel.markSigningOut()
                    onSignOut()
                }) { Text("Sign out") }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun CustomerDetails(customer: Customer, onVerify: () -> Unit) {
    val kyc = customer.kyc
    if (kyc.verified) {
        Banner(
            title = "Identity verified" + (kyc.tier?.let { " · Tier $it" } ?: ""),
            text = kyc.expiresAt?.let { "Valid until $it." } ?: "You can send up to your tier's limits.",
            tone = BannerTone.Info,
            icon = Icons.Outlined.Verified,
        )
    } else {
        Banner(
            title = if (kyc.status == "VERIFIED") "Verification expired" else "Not yet verified",
            text = if (kyc.status == "PENDING") "We're checking your details. Tap to see where it stands." else "Tap to verify with your national ID or passport.",
            tone = BannerTone.Warning,
            icon = Icons.Outlined.VerifiedUser,
            modifier = Modifier.clickable(onClick = onVerify),
        )
    }
    Spacer(Modifier.height(20.dp))
    Eyebrow("Account")
    Spacer(Modifier.height(8.dp))
    FCard {
        KeyValueRow("Customer number", customer.reference, mono = true)
        KeyValueRow("Account type", customer.type.prettyCode())
        customer.phone?.let { KeyValueRow("Phone", it, mono = true) }
        KeyValueRow("Country of residence", "${flagOf(customer.residenceIso2)} ${customer.residenceIso2}")
        KeyValueRow("Status", customer.status.prettyCode())
    }
    Spacer(Modifier.height(12.dp))
    Text(
        "To change these details, contact Fundaza support or visit an agent.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
