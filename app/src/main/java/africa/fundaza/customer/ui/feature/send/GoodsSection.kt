package africa.fundaza.customer.ui.feature.send

import africa.fundaza.customer.core.money.Money
import africa.fundaza.customer.domain.model.GoodsCategory
import africa.fundaza.customer.domain.model.GoodsDraft
import africa.fundaza.customer.domain.model.GoodsLineDraft
import africa.fundaza.customer.ui.components.Banner
import africa.fundaza.customer.ui.components.BannerTone
import africa.fundaza.customer.ui.components.DateField
import africa.fundaza.customer.ui.components.Eyebrow
import africa.fundaza.customer.ui.components.SelectField
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.time.LocalDate

/**
 * The goods a payment for goods to a company in China pays for. Our partner in China pays a
 * company for goods only with a list of what was bought, so each product is listed as on the
 * invoice, priced in the payout currency. The goods add up to what the company receives, and
 * the send is priced on that total whenever it changes.
 */
@Composable
internal fun GoodsSection(state: SendUiState, vm: SendViewModel) {
    val requirement = state.goodsRequirement ?: return
    val currency = requirement.currency
    val goods = state.goods

    Spacer(Modifier.height(28.dp))
    Eyebrow("The goods you are paying for")
    Spacer(Modifier.height(8.dp))
    Text(
        "Our partner in China pays a company for goods only with a list of what was bought. List each product " +
            "as it is on the invoice, priced in $currency. The goods add up to what the company receives.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    if (state.latestInvoiceId != null) {
        Spacer(Modifier.height(4.dp))
        TextButton(onClick = { vm.readInvoice() }, enabled = !state.readingInvoice) {
            if (state.readingInvoice) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text("Reading the invoice…")
            } else {
                Icon(Icons.Outlined.AutoAwesome, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Fill in from the invoice")
            }
        }
    }
    state.invoiceNote?.let {
        Spacer(Modifier.height(4.dp))
        Banner(it, tone = BannerTone.Info, icon = Icons.Outlined.Info)
    }

    Spacer(Modifier.height(12.dp))
    GoodsText(
        label = "Who sold the goods (platform or merchant)",
        value = goods.merchantName,
        onChange = { v -> vm.updateGoods { it.copy(merchantName = v.take(200)) } },
    )
    Spacer(Modifier.height(4.dp))
    DateField(
        label = "Date of the sale",
        value = runCatching { LocalDate.parse(goods.tradeDate) }.getOrNull(),
        onChange = { d -> vm.updateGoods { it.copy(tradeDate = d.toString()) } },
        allowed = { !it.isAfter(state.today) },
        modifier = Modifier.fillMaxWidth(),
    )

    goods.lines.forEachIndexed { index, line ->
        Spacer(Modifier.height(12.dp))
        GoodsLineCard(
            number = index + 1,
            line = line,
            currency = currency,
            categories = requirement.categories,
            removable = goods.lines.size > 1,
            onChange = { change -> vm.updateGoods { it.withLine(line.key, change) } },
            onRemove = { vm.updateGoods { it.withoutLine(line.key) } },
        )
    }
    if (goods.lines.size < GoodsDraft.MAX_LINES) {
        TextButton(onClick = { vm.updateGoods { it.withLine() } }) {
            Icon(Icons.Outlined.Add, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Add another product")
        }
    }

    Spacer(Modifier.height(8.dp))
    GoodsTotal(state, currency)

    Spacer(Modifier.height(12.dp))
    GoodsText(
        label = "Link to the sale (optional)",
        value = goods.salesLink,
        onChange = { v -> vm.updateGoods { it.copy(salesLink = v.take(500)) } },
        keyboard = KeyboardType.Uri,
        error = if (goods.salesLink.isNotBlank() && !goods.salesLink.trim().startsWith("https://")) "The sales link starts with https://." else null,
    )
    GoodsText(
        label = "Shipping company (optional)",
        value = goods.logisticsCompany,
        onChange = { v -> vm.updateGoods { it.copy(logisticsCompany = v.take(200)) } },
    )
    GoodsText(
        label = "Tracking number (optional)",
        value = goods.trackingNumber,
        onChange = { v -> vm.updateGoods { it.copy(trackingNumber = v.take(100)) } },
    )
}

@Composable
private fun GoodsLineCard(
    number: Int,
    line: GoodsLineDraft,
    currency: String,
    categories: List<GoodsCategory>,
    removable: Boolean,
    onChange: ((GoodsLineDraft) -> GoodsLineDraft) -> Unit,
    onRemove: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Product $number", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            if (removable) {
                IconButton(onClick = onRemove) { Icon(Icons.Outlined.Close, "Remove product $number") }
            }
        }
        GoodsText(
            label = "Product name",
            value = line.productName,
            onChange = { v -> onChange { it.copy(productName = v.take(200)) } },
        )
        SelectField(
            label = "Category",
            options = categories,
            selected = categories.firstOrNull { it.code == line.categoryCode },
            optionLabel = { it.label },
            onSelect = { c -> onChange { it.copy(categoryCode = c.code) } },
            placeholder = "Choose a category",
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GoodsText(
                label = "Unit price ($currency)",
                value = line.unitPrice,
                onChange = { v -> onChange { it.copy(unitPrice = decimalInput(v, 2)) } },
                keyboard = KeyboardType.Decimal,
                modifier = Modifier.weight(1f),
            )
            GoodsText(
                label = "Quantity",
                value = line.quantity,
                onChange = { v -> onChange { it.copy(quantity = decimalInput(v, 4)) } },
                keyboard = KeyboardType.Decimal,
                modifier = Modifier.weight(1f),
            )
        }
        line.total?.let {
            Text(
                "Total: ${Money(it, currency).format()}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** The goods' total against the price held: what the company receives. */
@Composable
private fun GoodsTotal(state: SendUiState, currency: String) {
    val total = state.goodsTotal
    Column(
        Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("The goods add up to", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(
                total?.let { Money(it, currency).format() } ?: "—",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            when {
                total == null -> "Give every product a price and a quantity, and the price is worked out on their total."
                state.quoting -> "Getting a price for the goods’ total…"
                state.goodsPriced -> "This is what the company receives. The price is worked out again whenever it changes."
                else -> "The goods add up to ${Money(total, currency).format()}. Wait for a price on that total before you send."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        state.quoteError?.takeIf { total != null && !state.goodsPriced }?.let {
            Spacer(Modifier.height(8.dp))
            Banner(it, tone = BannerTone.Error, icon = Icons.Outlined.Info)
        }
    }
}

@Composable
private fun GoodsText(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
    keyboard: KeyboardType = KeyboardType.Text,
    error: String? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(keyboardType = keyboard, imeAction = ImeAction.Next),
        shape = RoundedCornerShape(14.dp),
        modifier = modifier,
    )
}

/** Digits and one point, at most [places] after it: a price to the cent, a quantity to four places. */
internal fun decimalInput(raw: String, places: Int): String {
    val cleaned = raw.replace(',', '.').filter { it.isDigit() || it == '.' }
    val point = cleaned.indexOf('.')
    if (point < 0) return cleaned
    val whole = cleaned.substring(0, point)
    val fraction = cleaned.substring(point + 1).filter { it.isDigit() }.take(places)
    return "$whole.$fraction"
}
