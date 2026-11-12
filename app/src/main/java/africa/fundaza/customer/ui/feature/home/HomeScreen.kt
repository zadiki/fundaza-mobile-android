package africa.fundaza.customer.ui.feature.home

import africa.fundaza.customer.core.money.Money
import africa.fundaza.customer.domain.model.Customer
import africa.fundaza.customer.ui.components.Banner
import africa.fundaza.customer.ui.components.BannerTone
import africa.fundaza.customer.ui.components.Eyebrow
import africa.fundaza.customer.ui.components.FCard
import africa.fundaza.customer.ui.components.LoadState
import africa.fundaza.customer.ui.components.TransferRow
import africa.fundaza.customer.ui.theme.Bricolage
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.LocalTime

private val HeaderTop = Color(0xFF0B2A21)
private val HeaderBottom = Color(0xFF14523F)
private val MarigoldC = Color(0xFFF2A93B)
private val PaperC = Color(0xFFF6F1E7)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onSend: () -> Unit,
    onWallet: () -> Unit,
    onTransfer: (String) -> Unit,
    onAllTransfers: () -> Unit,
    onVerify: () -> Unit,
    modifier: Modifier = Modifier,
    onReady: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.loading) {
        if (!state.loading) onReady()
    }
    // One shared pulse drives every placeholder, so the screen loads as one thing.
    val pulse by rememberInfiniteTransition(label = "placeholder").animateFloat(
        initialValue = 0.35f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
        label = "pulse",
    )

    PullToRefreshBox(
        isRefreshing = state.refreshing,
        onRefresh = viewModel::refresh,
        modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
    ) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
            item { Header(state, pulse, onSend, onWallet) }

            val customer = state.customer
            if (customer != null && !customer.kyc.verified) {
                item { KycBanner(customer, onVerify) }
            }

            if (state.canViewTransfers) {
                item {
                    Row(
                        Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 28.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Recent transfers", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                        TextButton(onClick = onAllTransfers) { Text("See all") }
                    }
                }
                item { Recent(state, pulse, onTransfer, onSend) }
            }
        }
    }
}

@Composable
private fun Header(state: HomeUiState, pulse: Float, onSend: () -> Unit, onWallet: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
            .background(Brush.verticalGradient(listOf(HeaderTop, HeaderBottom)))
            .statusBarsPadding()
            .padding(top = 20.dp, bottom = 28.dp),
    ) {
        Column(Modifier.padding(horizontal = 24.dp)) {
            Text(
                "fundaza",
                color = MarigoldC,
                fontFamily = Bricolage,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 18.sp,
            )
            Spacer(Modifier.height(24.dp))
            Text(
                greeting() + if (state.firstName.isNotBlank()) ",\n${state.firstName}" else "",
                style = MaterialTheme.typography.displaySmall,
                color = PaperC,
            )
        }

        val wallet = state.wallet
        if (wallet != null) {
            Spacer(Modifier.height(24.dp))
            when {
                state.loading || wallet is LoadState.Loading -> BalancePlaceholder(pulse)
                wallet is LoadState.Failed -> Text(
                    wallet.message,
                    color = PaperC.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
                wallet is LoadState.Loaded -> Balances(wallet.value.balances, onWallet)
            }
        }

        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onSend,
            enabled = state.canSend,
            modifier = Modifier.padding(horizontal = 24.dp).fillMaxWidth().height(60.dp),
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MarigoldC,
                contentColor = HeaderTop,
                disabledContainerColor = PaperC.copy(alpha = 0.15f),
                disabledContentColor = PaperC.copy(alpha = 0.5f),
            ),
        ) {
            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text("Send money", style = MaterialTheme.typography.titleMedium)
        }
        if (!state.canSend) {
            Text(
                "Your account can't send transfers yet. Contact Fundaza support.",
                color = PaperC.copy(alpha = 0.7f),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun Balances(balances: List<Money>, onWallet: () -> Unit) {
    val shown = balances.ifEmpty { emptyList() }
    LazyRow(
        contentPadding = PaddingValues(horizontal = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (shown.isEmpty()) {
            item { BalanceTile(null, onWallet) }
        }
        items(shown) { BalanceTile(it, onWallet) }
    }
}

@Composable
private fun BalanceTile(money: Money?, onClick: () -> Unit) {
    Column(
        Modifier
            .width(220.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(PaperC.copy(alpha = 0.08f))
            .clickable(onClick = onClick)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.AccountBalanceWallet, null, tint = MarigoldC, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Eyebrow(if (money != null) "${money.currency} wallet" else "Wallet", color = PaperC.copy(alpha = 0.7f))
        }
        Spacer(Modifier.height(10.dp))
        Text(
            money?.formatAmount() ?: "0.00",
            style = MaterialTheme.typography.headlineLarge,
            color = PaperC,
            maxLines = 1,
        )
        Text(
            if (money == null) "Refunds land here" else "Tap for activity",
            style = MaterialTheme.typography.bodySmall,
            color = PaperC.copy(alpha = 0.55f),
        )
    }
}

@Composable
private fun KycBanner(customer: Customer, onVerify: () -> Unit) {
    val status = customer.kyc.status
    Banner(
        title = when (status) {
            "VERIFIED", "EXPIRED" -> "Your verification has expired"
            "PENDING" -> "We're checking your details"
            "REJECTED" -> "We couldn't verify you"
            else -> "Verify your identity"
        },
        text = when (status) {
            "VERIFIED", "EXPIRED" -> "Send an in-date ID to keep sending. Tap to renew it."
            "PENDING" -> "We'll tell you when it's done. Tap to see where it stands."
            "REJECTED" -> "Tap to see what to do next."
            else -> "Verify with your national ID or passport before you send. It takes a few minutes."
        },
        tone = BannerTone.Warning,
        icon = Icons.Outlined.VerifiedUser,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp).clickable(onClick = onVerify),
    )
}

@Composable
private fun Recent(state: HomeUiState, pulse: Float, onTransfer: (String) -> Unit, onSend: () -> Unit) {
    FCard(Modifier.padding(horizontal = 20.dp), contentPadding = PaddingValues(vertical = 6.dp)) {
        val recent = state.recent
        if (state.loading || recent is LoadState.Loading) {
            repeat(3) { index ->
                if (index > 0) HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.outlineVariant)
                TransferRowPlaceholder(pulse)
            }
        } else when (recent) {
            LoadState.Loading -> Unit
            is LoadState.Failed -> Text(
                recent.message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(20.dp),
            )
            is LoadState.Loaded -> if (recent.value.isEmpty()) {
                Column(Modifier.padding(20.dp)) {
                    Text("No transfers yet", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Your first one takes about two minutes. You'll see every fee before you pay.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (state.canSend) {
                        TextButton(onClick = onSend, contentPadding = PaddingValues(0.dp)) {
                            Text("Start a transfer")
                            Spacer(Modifier.width(4.dp))
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, null, Modifier.size(16.dp))
                        }
                    }
                }
            } else {
                recent.value.forEachIndexed { index, transfer ->
                    if (index > 0) HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    TransferRow(
                        transfer,
                        transfer.beneficiaryId?.let { state.recipientNames[it] },
                        onClick = { onTransfer(transfer.reference) },
                    )
                }
            }
        }
    }
}

@Composable
private fun BalancePlaceholder(pulse: Float) {
    Column(
        Modifier
            .padding(horizontal = 24.dp)
            .width(220.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(PaperC.copy(alpha = 0.08f))
            .padding(16.dp),
    ) {
        Bone(PaperC, pulse * 0.4f, width = 90.dp, height = 12.dp)
        Spacer(Modifier.height(14.dp))
        Bone(PaperC, pulse * 0.4f, width = 140.dp, height = 28.dp)
        Spacer(Modifier.height(8.dp))
        Bone(PaperC, pulse * 0.4f, width = 100.dp, height = 12.dp)
    }
}

@Composable
private fun TransferRowPlaceholder(pulse: Float) {
    val bone = MaterialTheme.colorScheme.outlineVariant
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(44.dp).clip(CircleShape).background(bone.copy(alpha = pulse)))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Bone(bone, pulse, width = 130.dp, height = 14.dp)
            Spacer(Modifier.height(8.dp))
            Bone(bone, pulse, width = 80.dp, height = 12.dp)
        }
        Bone(bone, pulse, width = 64.dp, height = 14.dp)
    }
}

@Composable
private fun Bone(color: Color, alpha: Float, width: Dp, height: Dp) {
    Box(Modifier.width(width).height(height).clip(RoundedCornerShape(6.dp)).background(color.copy(alpha = alpha)))
}

private fun greeting(): String = when (LocalTime.now().hour) {
    in 5..11 -> "Good morning"
    in 12..16 -> "Good afternoon"
    else -> "Good evening"
}
