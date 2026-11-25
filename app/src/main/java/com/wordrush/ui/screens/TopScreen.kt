package com.wordrush.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wordrush.ui.GameUiState
import com.wordrush.ui.GameViewModel

@Composable
fun TopScreen(vm: GameViewModel, state: GameUiState, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Text("Топ по капиталу", style = MaterialTheme.typography.headlineSmall)
        Text("Капитал = сумма оценочных стоимостей слов", style = MaterialTheme.typography.bodySmall)
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 16.dp),
        ) {
            itemsIndexed(state.top) { i, (acc, cap) ->
                Card(Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text((i + 1).toString(), style = MaterialTheme.typography.titleLarge, modifier = Modifier.width(32.dp))
                        Text(acc.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        Column(horizontalAlignment = Alignment.End) {
                            Text(cap.toString(), style = MaterialTheme.typography.titleMedium)
                            Text(acc.ownedWordIds.size.toString() + " слов", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}
