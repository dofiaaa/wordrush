package com.wordrush.ui

import androidx.lifecycle.ViewModel
import com.wordrush.core.Account
import com.wordrush.core.Econ
import com.wordrush.core.GameEngine
import com.wordrush.core.Listing
import com.wordrush.core.Outcome
import com.wordrush.core.TradeOffer
import com.wordrush.core.Word
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class GameUiState(
    val ready: Boolean = false,
    val meId: String = "",
    val coins: Long = 0L,
    val poolSize: Int = 0,
    val collection: List<Word> = emptyList(),
    val leases: Map<String, Long> = emptyMap(),
    val listings: List<Listing> = emptyList(),
    val trades: List<TradeOffer> = emptyList(),
    val top: List<Pair<Account, Long>> = emptyList(),
    val wheelWords: List<Word> = emptyList(),
    val words: Map<String, Word> = emptyMap(),
    val names: Map<String, String> = emptyMap(),
    val message: String = "",
    val spinCooldownMs: Long = 0L,
)

/**
 * Локальный ViewModel поверх чистого GameEngine.
 * Когда появится сервер, здесь будет слой репозитория/синхронизации,
 * а GameEngine останется той же чистой логикой.
 */
class GameViewModel : ViewModel() {

    private val engine = GameEngine()
    private val _state = MutableStateFlow(GameUiState())
    val state: StateFlow<GameUiState> = _state.asStateFlow()

    fun init(words: List<Word>) {
        if (_state.value.ready) return
        engine.loadWords(words)
        val me = engine.registerAccount("Вы")
        _state.value = GameUiState(meId = me.id)
        seedBots()
        refresh("Добро пожаловать! Крутите рулетку и собирайте слова.")
        _state.value = _state.value.copy(ready = true)
    }

    private fun seedBots() {
        val names = listOf("Лексика", "Словоед", "Филолог", "Эрудит", "Грамотей", "Языковед", "Толкователь", "Собиратель")
        val bots = names.map { engine.registerAccount(it) }
        bots.forEach { engine.seedCollection(it.id, 8) }
        bots.take(4).forEachIndexed { i, bot ->
            val owned = engine.ownedWords(bot.id)
            if (owned.isNotEmpty()) {
                val w = owned[i % owned.size]
                engine.listForSale(bot.id, w.id, price = (w.estValue * 2).coerceAtLeast(10L))
            }
        }
        val a = bots.getOrNull(0)
        val b = bots.getOrNull(1)
        if (a != null && b != null) {
            val aw = engine.ownedWords(a.id)
            val bw = engine.ownedWords(b.id)
            if (aw.isNotEmpty() && bw.isNotEmpty()) {
                engine.placeTrade(a.id, aw[0].id, bw[0].id)
            }
        }
    }

    fun refresh(msg: String? = null) {
        val prev = _state.value.message
        val me = engine.account(_state.value.meId)
        val now = System.currentTimeMillis()
        val cooldown = if (me != null && me.lastSpinAt > 0L) {
            (Econ.SPIN_COOLDOWN_MS - (now - me.lastSpinAt)).coerceAtLeast(0L)
        } else 0L
        _state.value = GameUiState(
            ready = true,
            meId = _state.value.meId,
            coins = me?.coins ?: 0L,
            poolSize = engine.poolSize,
            collection = engine.ownedWords(_state.value.meId),
            leases = me?.leases?.toMap() ?: emptyMap(),
            listings = engine.openListings(),
            trades = engine.openTrades(),
            top = engine.topByCapital(20),
            wheelWords = engine.poolSample(8),
            words = engine.allWords(),
            names = engine.allAccounts().associate { it.id to it.name },
            message = msg ?: prev,
            spinCooldownMs = cooldown,
        )
    }

    private fun report(r: Outcome): Unit = refresh(
        when (r) {
            is Outcome.Success -> r.message
            is Outcome.Error -> r.message
        }
    )

    fun spin() { report(engine.spin(_state.value.meId)) }
    fun dailyReward() { report(engine.dailyReward(_state.value.meId)) }
    fun renewLease(wordId: String) { report(engine.renewLease(_state.value.meId, wordId)) }
    fun listForSale(wordId: String, price: Long) { report(engine.listForSale(_state.value.meId, wordId, price)) }
    fun cancelListing(listingId: String) { report(engine.cancelListing(listingId, _state.value.meId)) }
    fun buy(listingId: String) { report(engine.buy(listingId, _state.value.meId)) }
    fun placeBid(listingId: String, amount: Long) { report(engine.placeBid(listingId, _state.value.meId, amount)) }
    fun placeTrade(offeredWordId: String, requestedWordId: String) { report(engine.placeTrade(_state.value.meId, offeredWordId, requestedWordId)) }
    fun acceptTrade(tradeId: String) { report(engine.acceptTrade(tradeId, _state.value.meId)) }
    fun declineTrade(tradeId: String) { report(engine.declineTrade(tradeId, _state.value.meId)) }
    fun cancelTrade(tradeId: String) { report(engine.cancelTrade(tradeId, _state.value.meId)) }
}
