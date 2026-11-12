package africa.fundaza.customer.ui.feature.recipients

import africa.fundaza.customer.domain.model.Beneficiary
import africa.fundaza.customer.ui.components.Avatar
import africa.fundaza.customer.ui.components.ErrorState
import africa.fundaza.customer.ui.components.FundazaTopBar
import africa.fundaza.customer.ui.components.LoadState
import africa.fundaza.customer.ui.components.LoadingState
import africa.fundaza.customer.ui.components.MessageState
import africa.fundaza.customer.ui.components.flagOf
import africa.fundaza.customer.ui.theme.MonoStyle
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipientsScreen(
    onRecipient: (Long) -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RecipientsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        topBar = { FundazaTopBar("Recipients") },
        contentWindowInsets = WindowInsets(0),
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAdd,
                icon = { Icon(Icons.Outlined.PersonAdd, null) },
                text = { Text("Add") },
                containerColor = MaterialTheme.colorScheme.secondary,
                contentColor = MaterialTheme.colorScheme.onSecondary,
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (val recipients = state.recipients) {
                LoadState.Loading -> LoadingState()
                is LoadState.Failed -> ErrorState(recipients.message, onRetry = { viewModel.load() })
                is LoadState.Loaded -> PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = viewModel::refresh) {
                    if (recipients.value.isEmpty()) {
                        MessageState(
                            icon = Icons.Outlined.People,
                            title = "No recipients yet",
                            body = "Save the people you send to, and their details are ready next time.",
                            actionLabel = "Add a recipient",
                            onAction = onAdd,
                        )
                    } else {
                        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp)) {
                            item {
                                OutlinedTextField(
                                    value = state.query,
                                    onValueChange = viewModel::onQueryChange,
                                    placeholder = { Text("Search by name") },
                                    leadingIcon = { Icon(Icons.Outlined.Search, null) },
                                    singleLine = true,
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                                )
                            }
                            items(state.visible, key = { it.id }) { RecipientRow(it) { onRecipient(it.id) } }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RecipientRow(person: Beneficiary, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(person.initials)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(person.fullName, style = MaterialTheme.typography.titleSmall)
            val ways = person.ways
            Text(
                when {
                    ways.isEmpty() -> "No payout details yet"
                    ways.size == 1 -> "${ways[0].deliveryMethodName} · ${ways[0].summary}"
                    else -> "${ways.size} ways to receive"
                },
                style = MonoStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(flagOf(person.countryIso2), style = MaterialTheme.typography.titleLarge)
    }
}
