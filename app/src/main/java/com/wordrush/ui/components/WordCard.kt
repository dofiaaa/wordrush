package com.wordrush.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wordrush.core.Word

@Composable
fun WordCard(word: Word, trailing: (@Composable () -> Unit)? = null) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(word.lemma, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                AssistChip(onClick = {}, label = { Text(word.rarityTier.label) })
            }
            if (word.definition.isNotBlank()) {
                Text(word.definition, style = MaterialTheme.typography.bodySmall)
            }
            Text("Оценочная стоимость: " + word.estValue, style = MaterialTheme.typography.labelMedium)
            if (trailing != null) trailing()
        }
    }
}
