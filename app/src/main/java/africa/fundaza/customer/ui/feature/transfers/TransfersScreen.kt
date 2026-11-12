package africa.fundaza.customer.ui.feature.transfers

import africa.fundaza.customer.ui.components.ChoiceChip
import africa.fundaza.customer.ui.components.ErrorState
import africa.fundaza.customer.ui.components.FundazaTopBar
import africa.fundaza.customer.ui.components.LoadState
import africa.fundaza.customer.ui.components.LoadingState
import africa.fundaza.customer.ui.components.MessageState
import africa.fundaza.customer.ui.components.TransferRow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransfersScreen(
    onTransfer: (String) -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TransfersViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        topBar = { FundazaTopBar("Transfers") },
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (val transfers = state.transfers) {
                LoadState.Loading -> LoadingState()
                is LoadState.Failed -> ErrorState(transfers.message, onRetry = { viewModel.load() })
                is LoadState.Loaded -> PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = viewModel::refresh) {
                    if (transfers.value.isEmpty()) {
                        MessageState(
                            icon = Icons.Outlined.SwapHoriz,
                            title = "Nothing sent yet",
                            body = "Transfers you make appear here, with every step from payment to payout.",
                            actionLabel = "Send money",
                            onAction = onSend,
                        )
                    } else {
                        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
                            item {
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    items(TransferFilter.entries) { f ->
                                        ChoiceChip(f.label, selected = state.filter == f, onClick = { viewModel.setFilter(f) })
                                    }
                                }
                            }
                            if (state.visible.isEmpty()) {
                                item {
                                    Box(Modifier.padding(top = 32.dp)) {
                                        MessageState(
                                            icon = Icons.Outlined.SwapHoriz,
                                            title = "None here",
                                            body = "No transfers match “${state.filter.label}”.",
                                        )
                                    }
                                }
                            }
                            items(state.visible, key = { it.reference }) { t ->
                                TransferRow(t, t.beneficiaryId?.let { state.recipientNames[it] }, onClick = { onTransfer(t.reference) })
                                HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.outlineVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}
