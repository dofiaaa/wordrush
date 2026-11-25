package com.wordrush.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wordrush.data.WordsRepository
import com.wordrush.ui.screens.CollectionScreen
import com.wordrush.ui.screens.MarketScreen
import com.wordrush.ui.screens.RouletteScreen
import com.wordrush.ui.screens.TopScreen
import com.wordrush.ui.theme.WordRushTheme

private val Tabs = listOf("Рулетка", "Коллекция", "Рынок", "Топ")
private val TabIcons = listOf("🎡", "📚", "🏷️", "🏆")

@Composable
fun App() {
    WordRushTheme {
        val context = LocalContext.current
        val words = remember { WordsRepository(context).load() }
        val vm: GameViewModel = viewModel()
        LaunchedEffect(Unit) { vm.init(words) }
        val state by vm.state.collectAsState()

        var tab by rememberSaveable { mutableStateOf(0) }

        Scaffold(
            bottomBar = {
                NavigationBar {
                    Tabs.forEachIndexed { i, title ->
                        NavigationBarItem(
                            selected = tab == i,
                            onClick = { tab = i },
                            icon = { Text(TabIcons[i]) },
                            label = { Text(title) },
                        )
                    }
                }
            }
        ) { padding ->
            val mod = Modifier.padding(padding)
            when (tab) {
                0 -> RouletteScreen(vm, state, mod)
                1 -> CollectionScreen(vm, state, mod)
                2 -> MarketScreen(vm, state, mod)
                3 -> TopScreen(vm, state, mod)
            }
        }
    }
}
