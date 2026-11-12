package africa.fundaza.customer.ui.feature.transfers

import africa.fundaza.customer.core.money.formatRate
import africa.fundaza.customer.domain.model.StatusStep
import africa.fundaza.customer.domain.model.StatusTone
import africa.fundaza.customer.domain.model.TransferDetail
import africa.fundaza.customer.domain.model.TransferStatus
import africa.fundaza.customer.ui.components.Banner
import africa.fundaza.customer.ui.components.BannerTone
import africa.fundaza.customer.ui.components.ErrorState
import africa.fundaza.customer.ui.components.Eyebrow
import africa.fundaza.customer.ui.components.FCard
import africa.fundaza.customer.ui.components.FundazaTopBar
import africa.fundaza.customer.ui.components.KeyValueRow
import africa.fundaza.customer.ui.components.LoadState
import africa.fundaza.customer.ui.components.LoadingState
import africa.fundaza.customer.ui.components.StatusPill
import africa.fundaza.customer.ui.theme.LocalStatusColors
import africa.fundaza.customer.ui.theme.MonoStyle
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import android.content.ClipData
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import kotlinx.coroutines.launch
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private val timeFormat = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withZone(ZoneId.systemDefault())

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransferDetailScreen(
    justSent: Boolean,
    onBack: () -> Unit,
    onRecipient: (Long) -> Unit,
    viewModel: TransferDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(topBar = { FundazaTopBar("Transfer", onBack = onBack) }) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (val detail = state.detail) {
                LoadState.Loading -> LoadingState()
                is LoadState.Failed -> ErrorState(detail.message, onRetry = { viewModel.load() })
                is LoadState.Loaded -> PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = viewModel::refresh) {
                    Column(
                        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
                    ) {
                        Content(detail.value, state.recipient?.fullName, justSent, onRecipient)
                    }
                }
            }
        }
    }
}

@Composable
private fun Content(detail: TransferDetail, recipientName: String?, justSent: Boolean, onRecipient: (Long) -> Unit) {
    val t = detail.transfer
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()

    if (justSent && t.status == TransferStatus.AWAITING_PAYMENT) {
        Banner(
            title = "Transfer created",
            text = "Now pay ${t.totalPayable.format()} so we can send it. We'll notify you when your payment is confirmed and again when it's paid out.",
            tone = BannerTone.Info,
            icon = Icons.Outlined.CheckCircle,
        )
        Spacer(Modifier.height(20.dp))
    }

    StatusPill(t.status)
    Spacer(Modifier.height(12.dp))
    Text(t.receiveAmount.format(), style = MaterialTheme.typography.displaySmall)
    if (recipientName != null) {
        Text(
            "to $recipientName",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.clickable { t.beneficiaryId?.let(onRecipient) },
        )
    }
    Spacer(Modifier.height(4.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(t.reference, style = MonoStyle, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f, fill = false))
        IconButton(onClick = { scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("Transfer reference", t.reference))) } }) {
            Icon(Icons.Outlined.ContentCopy, "Copy reference", Modifier.size(16.dp))
        }
    }

    Spacer(Modifier.height(20.dp))
    Tracker(t.status)

    Spacer(Modifier.height(20.dp))
    FCard {
        KeyValueRow("You sent", t.sendAmount.format())
        t.charges.forEach { KeyValueRow(it.label, it.amount.format()) }
        HorizontalDivider(Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
        KeyValueRow("Total paid", t.totalPayable.format(), strong = true)
        KeyValueRow("Exchange rate", formatRate(t.exchangeRate, t.sendAmount.currency, t.receiveAmount.currency), mono = true)
        t.date?.let { KeyValueRow("Date", it.toString(), mono = true) }
    }

    if (detail.history.isNotEmpty()) {
        Spacer(Modifier.height(28.dp))
        Eyebrow("What's happened")
        Spacer(Modifier.height(12.dp))
        Timeline(detail.history.reversed())
    }
}

/** The normal path, with where this transfer is on it. Off-path endings show as a single state. */
@Composable
private fun Tracker(status: TransferStatus) {
    val c = LocalStatusColors.current
    val path = TransferStatus.happyPath
    val index = path.indexOf(status)
    if (index < 0 && status.tone != StatusTone.InProgress) return

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        path.forEachIndexed { i, _ ->
            val done = index >= 0 && i <= index
            Box(
                Modifier
                    .weight(1f)
                    .height(6.dp)
                    .clip(CircleShape)
                    .background(if (done) c.successFg else MaterialTheme.colorScheme.outlineVariant),
            )
        }
    }
    Spacer(Modifier.height(6.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("Paid in", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Paid out", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Timeline(steps: List<StatusStep>) {
    val c = LocalStatusColors.current
    steps.forEachIndexed { i, step ->
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(20.dp)) {
                Box(
                    Modifier
                        .padding(top = 4.dp)
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(if (i == 0) MaterialTheme.colorScheme.secondary else c.successFg.copy(alpha = 0.5f)),
                )
                if (i < steps.lastIndex) {
                    Box(Modifier.width(2.dp).fillMaxHeight().background(MaterialTheme.colorScheme.outlineVariant))
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.padding(bottom = 20.dp)) {
                Text(step.status.label, style = MaterialTheme.typography.titleSmall)
                step.note?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                step.changedAt?.let {
                    Text(timeFormat.format(it), style = MonoStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
