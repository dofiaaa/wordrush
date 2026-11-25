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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wordrush.core.Econ
import com.wordrush.ui.GameUiState
import com.wordrush.ui.GameViewModel
import com.wordrush.ui.components.WordCard

@Composable
fun CollectionScreen(vm: GameViewModel, state: GameUiState, modifier: Modifier = Modifier) {
    val now = System.currentTimeMillis()
    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Text("Коллекция (" + state.collection.size + ")", style = MaterialTheme.typography.headlineSmall)
        if (state.collection.isEmpty()) {
            Text("Пока пусто — крутите рулетку, чтобы получить слова.")
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 16.dp),
            ) {
                items(state.collection, key = { it.id }) { w ->
                    val lease = state.leases[w.id] ?: 0L
                    val days = ((lease - now) / Econ.DAY_MS).coerceAtLeast(0L)
                    WordCard(w) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text("Аренда: " + days + " дн.", style = MaterialTheme.typography.labelMedium)
                            OutlinedButton(onClick = { vm.renewLease(w.id) }) {
                                Text("Продлить (" + Econ.LEASE_COST + ")")
                            }
                        }
                    }
                }
            }
        }
    }
}
