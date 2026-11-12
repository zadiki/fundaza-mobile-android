package africa.fundaza.customer.ui.feature.wallet

import africa.fundaza.customer.domain.model.Wallet
import africa.fundaza.customer.domain.model.WalletMovement
import africa.fundaza.customer.ui.components.ErrorState
import africa.fundaza.customer.ui.components.Eyebrow
import africa.fundaza.customer.ui.components.FCard
import africa.fundaza.customer.ui.components.FundazaTopBar
import africa.fundaza.customer.ui.components.LoadState
import africa.fundaza.customer.ui.components.LoadingState
import africa.fundaza.customer.ui.components.MoneyText
import africa.fundaza.customer.ui.theme.LocalStatusColors
import africa.fundaza.customer.ui.theme.MonoStyle
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.CallMade
import androidx.compose.material.icons.automirrored.outlined.CallReceived
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private val when_ = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withZone(ZoneId.systemDefault())

@Composable
fun WalletScreen(
    onBack: () -> Unit,
    onTransfer: (String) -> Unit,
    viewModel: WalletViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Scaffold(topBar = { FundazaTopBar("Wallet", onBack = onBack) }) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (val s = state) {
                LoadState.Loading -> LoadingState()
                is LoadState.Failed -> ErrorState(s.message, onRetry = viewModel::load)
                is LoadState.Loaded -> Content(s.value, onTransfer)
            }
        }
    }
}

@Composable
private fun Content(wallet: Wallet, onTransfer: (String) -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp)) {
        item {
            FCard {
                Eyebrow("Balance")
                Spacer(Modifier.height(8.dp))
                if (wallet.balances.isEmpty()) {
                    Text("Nothing held", style = MaterialTheme.typography.headlineMedium)
                }
                wallet.balances.forEach { MoneyText(it, style = MaterialTheme.typography.headlineLarge, modifier = Modifier.padding(vertical = 2.dp)) }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Money comes back here when a transfer is reversed or refunded. There's no top-up.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(24.dp))
            Eyebrow("Activity")
            Spacer(Modifier.height(8.dp))
            if (wallet.movements.isEmpty()) {
                Text("No activity yet.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        items(wallet.movements) { MovementRow(it, onTransfer) }
    }
}

@Composable
private fun MovementRow(m: WalletMovement, onTransfer: (String) -> Unit) {
    val c = LocalStatusColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = m.transactionReference != null) { m.transactionReference?.let(onTransfer) }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(if (m.incoming) c.successBg else c.returnedBg),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (m.incoming) Icons.AutoMirrored.Outlined.CallReceived else Icons.AutoMirrored.Outlined.CallMade,
                null,
                tint = if (m.incoming) c.successFg else c.returnedFg,
                modifier = Modifier.size(18.dp),
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(m.description.ifBlank { if (m.incoming) "Credit" else "Debit" }, style = MaterialTheme.typography.bodyMedium)
            m.postedAt?.let { Text(when_.format(it), style = MonoStyle, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Text(
            (if (m.incoming) "+" else "−") + m.amount.format(),
            style = MaterialTheme.typography.titleSmall,
            color = if (m.incoming) c.successFg else MaterialTheme.colorScheme.onSurface,
        )
    }
}
