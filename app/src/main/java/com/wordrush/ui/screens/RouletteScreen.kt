package com.wordrush.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wordrush.core.Econ
import com.wordrush.ui.GameUiState
import com.wordrush.ui.GameViewModel
import com.wordrush.ui.components.RouletteWheel
import kotlinx.coroutines.delay
import kotlin.random.Random

@Composable
fun RouletteScreen(vm: GameViewModel, state: GameUiState, modifier: Modifier = Modifier) {
    var target by remember { mutableStateOf(0f) }
    val angle by animateFloatAsState(
        targetValue = target,
        animationSpec = tween(2200, easing = FastOutSlowInEasing),
        label = "wheel",
    )

    // лёгкий тикер перезарядки
    LaunchedEffect(state.spinCooldownMs) {
        while (state.spinCooldownMs > 0L) {
            delay(1000L)
            vm.refresh()
        }
    }

    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Рулетка", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Монеты: " + state.coins + "  ·  В пуле: " + state.poolSize,
            style = MaterialTheme.typography.bodyMedium,
        )

        RouletteWheel(
            labels = state.wheelWords.map { it.lemma },
            rotation = angle,
            modifier = Modifier.size(260.dp),
        )

        Button(
            onClick = {
                target += 360f * 6 + Random.nextFloat() * 360f
                vm.spin()
            },
            enabled = state.spinCooldownMs == 0L && state.poolSize > 0,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                if (state.spinCooldownMs > 0L) {
                    "Перезарядка " + (state.spinCooldownMs / 1000L + 1L) + " с"
                } else {
                    "Крутить (бесплатно)"
                }
            )
        }

        OutlinedButton(onClick = { vm.dailyReward() }, modifier = Modifier.fillMaxWidth()) {
            Text("Ежедневный бонус +" + Econ.DAILY_REWARD)
        }

        if (state.message.isNotBlank()) {
            Text(state.message, style = MaterialTheme.typography.bodyLarge)
        }
    }
}
