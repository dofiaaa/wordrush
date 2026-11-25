package com.wordrush.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wordrush.core.Listing
import com.wordrush.core.ListingType
import com.wordrush.core.TradeOffer
import com.wordrush.core.Word
import com.wordrush.ui.GameUiState
import com.wordrush.ui.GameViewModel
import com.wordrush.ui.components.WordCard

@Composable
fun MarketScreen(vm: GameViewModel, state: GameUiState, modifier: Modifier = Modifier) {
    var saleWord by remember { mutableStateOf<Word?>(null) }
    var tradeWord by remember { mutableStateOf<Word?>(null) }
    var bidListing by remember { mutableStateOf<Listing?>(null) }

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Text("Рынок", style = MaterialTheme.typography.headlineSmall)
        Text("Монеты: " + state.coins, style = MaterialTheme.typography.bodyMedium)
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 16.dp),
        ) {
            item { Text("Мои слова", style = MaterialTheme.typography.titleMedium) }
            if (state.collection.isEmpty()) item { Text("Коллекция пуста") }
            items(state.collection, key = { it.id }) { w ->
                WordCard(w) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { saleWord = w }) { Text("Продать") }
                        OutlinedButton(onClick = { tradeWord = w }) { Text("Обменять") }
                    }
                }
            }

            item { Text("Лоты на рынке", style = MaterialTheme.typography.titleMedium) }
            if (state.listings.isEmpty()) item { Text("Лотов нет") }
            items(state.listings, key = { it.id }) { l ->
                ListingRow(l, state, vm, onBid = { bidListing = l })
            }

            item { Text("Обмены", style = MaterialTheme.typography.titleMedium) }
            if (state.trades.isEmpty()) item { Text("Предложений нет") }
            items(state.trades, key = { it.id }) { t ->
                TradeRow(t, state, vm)
            }
        }
    }

    saleWord?.let { w ->
        SaleDialog(w, onDismiss = { saleWord = null }, onConfirm = { price -> vm.listForSale(w.id, price); saleWord = null })
    }
    tradeWord?.let { w ->
        TradeDialog(w, onDismiss = { tradeWord = null }, onConfirm = { req -> vm.placeTrade(w.id, req); tradeWord = null })
    }
    bidListing?.let { l ->
        BidDialog(l, onDismiss = { bidListing = null }, onConfirm = { a -> vm.placeBid(l.id, a); bidListing = null })
    }
}

@Composable
private fun ListingRow(l: Listing, state: GameUiState, vm: GameViewModel, onBid: () -> Unit) {
    val lemma = state.words[l.wordId]?.lemma ?: l.wordId
    val seller = state.names[l.sellerId] ?: l.sellerId
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text(lemma, style = MaterialTheme.typography.titleMedium)
            Text("Продавец: " + seller + "  ·  Цена: " + l.price, style = MaterialTheme.typography.bodySmall)
            if (l.type == ListingType.AUCTION) {
                Text("Аукцион", style = MaterialTheme.typography.labelSmall)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { vm.buy(l.id) }) { Text("Купить") }
                if (l.type == ListingType.AUCTION) {
                    OutlinedButton(onClick = onBid) { Text("Ставка") }
                }
            }
        }
    }
}

@Composable
private fun TradeRow(t: TradeOffer, state: GameUiState, vm: GameViewModel) {
    val offered = state.words[t.offeredWordId]?.lemma ?: t.offeredWordId
    val requested = state.words[t.requestedWordId]?.lemma ?: t.requestedWordId
    val from = state.names[t.fromUserId] ?: t.fromUserId
    val mine = t.fromUserId == state.meId
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            val who = if (mine) "Вы" else from
            Text(who + " отдаёт «" + offered + "» за «" + requested + "»", style = MaterialTheme.typography.bodyMedium)
            if (mine) {
                OutlinedButton(onClick = { vm.cancelTrade(t.id) }) { Text("Отменить") }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { vm.acceptTrade(t.id) }) { Text("Принять") }
                    OutlinedButton(onClick = { vm.declineTrade(t.id) }) { Text("Отклонить") }
                }
            }
        }
    }
}

@Composable
private fun SaleDialog(word: Word, onDismiss: () -> Unit, onConfirm: (Long) -> Unit) {
    var text by remember { mutableStateOf(word.estValue.toString()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Продать: " + word.lemma) },
        text = {
            Column {
                Text("Оценочная стоимость: " + word.estValue)
                OutlinedTextField(value = text, onValueChange = { text = it }, label = { Text("Цена") })
            }
        },
        confirmButton = { TextButton(onClick = { text.toLongOrNull()?.let(onConfirm) }) { Text("Выставить") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

@Composable
private fun TradeDialog(word: Word, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Обменять: " + word.lemma) },
        text = {
            Column {
                Text("Вы отдаёте: " + word.lemma)
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Какое слово хотите получить? (лемма)") },
                )
            }
        },
        confirmButton = { TextButton(onClick = { if (text.isNotBlank()) onConfirm(text.trim()) }) { Text("Предложить") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

@Composable
private fun BidDialog(l: Listing, onDismiss: () -> Unit, onConfirm: (Long) -> Unit) {
    var text by remember { mutableStateOf(l.price.toString()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ставка") },
        text = {
            Column {
                Text("Стартовая цена: " + l.price)
                OutlinedTextField(value = text, onValueChange = { text = it }, label = { Text("Ваша ставка") })
            }
        },
        confirmButton = { TextButton(onClick = { text.toLongOrNull()?.let(onConfirm) }) { Text("Сделать ставку") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}
