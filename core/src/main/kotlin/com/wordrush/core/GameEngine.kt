package com.wordrush.core

import kotlin.random.Random

/** Аккаунт игрока. */
data class Account(
    val id: String,
    val name: String,
    var coins: Long,
    val ownedWordIds: MutableSet<String> = mutableSetOf(),
    var lastSpinAt: Long = 0L,
    var lastDailyAt: Long = 0L,
    val leases: MutableMap<String, Long> = mutableMapOf(), // wordId -> expiresAt
)

sealed class Outcome {
    data class Success(val message: String) : Outcome()
    data class Error(val message: String) : Outcome()
}

/**
 * Ядро игры. Полностью чистое (без Android), детерминировано через rng/clock,
 * поэтому легко переносится на iOS (Kotlin Multiplatform) и покрывается тестами.
 */
class GameEngine(
    private val rng: Random = Random.Default,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    private val words = mutableMapOf<String, Word>()
    private val pool = mutableSetOf<String>()
    private val accounts = mutableMapOf<String, Account>()
    private val listings = mutableMapOf<String, Listing>()
    private val bids = mutableMapOf<String, MutableList<Bid>>()
    private val trades = mutableMapOf<String, TradeOffer>()
    private var listingSeq = 0
    private var tradeSeq = 0
    private var accountSeq = 0

    fun loadWords(ws: List<Word>) {
        words.clear()
        pool.clear()
        ws.forEach { words[it.id] = it; pool.add(it.id) }
    }

    val wordCount: Int get() = words.size
    val poolSize: Int get() = pool.size

    fun word(id: String): Word? = words[id]
    fun allWords(): Map<String, Word> = words.toMap()
    fun poolSample(n: Int): List<Word> = pool.asSequence().take(n).mapNotNull { words[it] }.toList()

    // ----- Аккаунты -----

    fun registerAccount(name: String): Account {
        val a = Account("u" + accountSeq++, name, coins = Econ.DAILY_REWARD)
        accounts[a.id] = a
        return a
    }

    fun account(id: String): Account? = accounts[id]
    fun allAccounts(): List<Account> = accounts.values.toList()

    // ----- Рулетка (равновероятный выбор из пула) -----

    private fun drawFor(acc: Account, now: Long): Word? {
        if (pool.isEmpty()) return null
        val wordId = pool.elementAt(rng.nextInt(pool.size))
        val w = words[wordId] ?: return null
        pool.remove(wordId)
        acc.ownedWordIds.add(wordId)
        acc.leases[wordId] = now + Econ.LEASE_PERIOD_MS
        return w
    }

    fun spin(accountId: String): Outcome {
        val acc = accounts[accountId] ?: return Outcome.Error("Аккаунт не найден")
        val now = clock()
        if (acc.lastSpinAt != 0L && now - acc.lastSpinAt < Econ.SPIN_COOLDOWN_MS) {
            val wait = (Econ.SPIN_COOLDOWN_MS - (now - acc.lastSpinAt)) / 1000L + 1L
            return Outcome.Error("Рулетка перезаряжается, подождите " + wait + " с")
        }
        expireLeases(now)
        val w = drawFor(acc, now) ?: return Outcome.Error("Все слова разобраны — скоро новая волна")
        acc.lastSpinAt = now
        acc.coins += Econ.SPIN_REWARD
        return Outcome.Success("Выпало слово: " + w.lemma)
    }

    /** Для демо/ботов: выдача слов без перезарядки и награды. */
    fun seedCollection(accountId: String, count: Int) {
        val acc = accounts[accountId] ?: return
        val now = clock()
        repeat(count) { drawFor(acc, now) }
    }

    // ----- Рынок -----

    fun listForSale(
        accountId: String,
        wordId: String,
        price: Long,
        type: ListingType = ListingType.SALE,
        buyNowPrice: Long = 0L,
    ): Outcome {
        val acc = accounts[accountId] ?: return Outcome.Error("Аккаунт не найден")
        if (wordId !in acc.ownedWordIds) return Outcome.Error("Слово не в вашей коллекции")
        if (listings.values.any { it.wordId == wordId && it.sellerId == accountId }) {
            return Outcome.Error("Слово уже выставлено")
        }
        if (price <= 0L) return Outcome.Error("Цена должна быть больше нуля")
        val id = "l" + listingSeq++
        listings[id] = Listing(id, wordId, accountId, type, price, buyNowPrice, clock())
        return Outcome.Success("Слово выставлено на рынок")
    }

    fun cancelListing(listingId: String, accountId: String): Outcome {
        val l = listings[listingId] ?: return Outcome.Error("Лот не найден")
        if (l.sellerId != accountId) return Outcome.Error("Это не ваш лот")
        listings.remove(listingId)
        bids.remove(listingId)
        return Outcome.Success("Лот снят с продажи")
    }

    fun placeBid(listingId: String, bidderId: String, amount: Long): Outcome {
        val l = listings[listingId] ?: return Outcome.Error("Лот не найден")
        if (l.type != ListingType.AUCTION) return Outcome.Error("Это не аукцион")
        val bidder = accounts[bidderId] ?: return Outcome.Error("Аккаунт не найден")
        val list = bids.getOrPut(listingId) { mutableListOf() }
        val current = list.maxByOrNull { it.amount }?.amount ?: l.price
        if (amount <= current) return Outcome.Error("Ставка должна быть выше " + current)
        if (bidder.coins < amount) return Outcome.Error("Недостаточно монет")
        list.add(Bid(bidderId, amount, clock()))
        return Outcome.Success("Ставка принята")
    }

    fun buy(listingId: String, buyerId: String): Outcome {
        val l = listings[listingId] ?: return Outcome.Error("Лот не найден")
        val seller = accounts[l.sellerId] ?: return Outcome.Error("Продавец не найден")
        val buyer = accounts[buyerId] ?: return Outcome.Error("Покупатель не найден")
        if (buyerId == l.sellerId) return Outcome.Error("Нельзя купить у себя")
        val now = clock()
        expireLeases(now)
        val price = when (l.type) {
            ListingType.SALE -> l.price
            ListingType.AUCTION -> bids[listingId]?.maxByOrNull { it.amount }?.amount ?: l.price
        }
        if (buyer.coins < price) return Outcome.Error("Недостаточно монет")
        val fee = Econ.marketFee(price)
        seller.coins += price - fee
        buyer.coins -= price
        seller.ownedWordIds.remove(l.wordId)
        seller.leases.remove(l.wordId)
        buyer.ownedWordIds.add(l.wordId)
        buyer.leases[l.wordId] = now + Econ.LEASE_PERIOD_MS
        listings.remove(listingId)
        bids.remove(listingId)
        val lemma = words[l.wordId]?.lemma ?: l.wordId
        return Outcome.Success("Куплено слово: " + lemma)
    }

    // ----- Обмен -----

    fun placeTrade(
        fromUserId: String,
        offeredWordId: String,
        requestedWordId: String,
        toUserId: String? = null,
    ): Outcome {
        val from = accounts[fromUserId] ?: return Outcome.Error("Аккаунт не найден")
        if (offeredWordId !in from.ownedWordIds) return Outcome.Error("Слово для обмена не в вашей коллекции")
        if (offeredWordId == requestedWordId) return Outcome.Error("Нельзя обменять слово само на себя")
        if (requestedWordId !in words) return Outcome.Error("Запрашиваемое слово не найдено")
        val id = "t" + tradeSeq++
        trades[id] = TradeOffer(id, fromUserId, toUserId, offeredWordId, requestedWordId, TradeStatus.PENDING, clock())
        return Outcome.Success("Предложение обмена создано")
    }

    fun acceptTrade(tradeId: String, acceptorId: String): Outcome {
        val t = trades[tradeId] ?: return Outcome.Error("Обмен не найден")
        if (t.status != TradeStatus.PENDING) return Outcome.Error("Обмен уже закрыт")
        if (t.toUserId != null && t.toUserId != acceptorId) return Outcome.Error("Предложение адресовано другому игроку")
        val acceptor = accounts[acceptorId] ?: return Outcome.Error("Аккаунт не найден")
        if (t.requestedWordId !in acceptor.ownedWordIds) return Outcome.Error("У вас нет запрашиваемого слова")
        val from = accounts[t.fromUserId] ?: return Outcome.Error("Автор предложения не найден")
        if (t.offeredWordId !in from.ownedWordIds) return Outcome.Error("Автор уже потерял предлагаемое слово")
        from.ownedWordIds.remove(t.offeredWordId)
        acceptor.ownedWordIds.remove(t.requestedWordId)
        from.ownedWordIds.add(t.requestedWordId)
        acceptor.ownedWordIds.add(t.offeredWordId)
        trades[tradeId] = t.copy(status = TradeStatus.ACCEPTED)
        return Outcome.Success("Обмен совершён")
    }

    fun declineTrade(tradeId: String, userId: String): Outcome {
        val t = trades[tradeId] ?: return Outcome.Error("Обмен не найден")
        if (t.fromUserId != userId && t.toUserId != userId) return Outcome.Error("Не ваше предложение")
        trades[tradeId] = t.copy(status = TradeStatus.DECLINED)
        return Outcome.Success("Обмен отклонён")
    }

    fun cancelTrade(tradeId: String, userId: String): Outcome {
        val t = trades[tradeId] ?: return Outcome.Error("Обмен не найден")
        if (t.fromUserId != userId) return Outcome.Error("Отменить может только автор")
        trades[tradeId] = t.copy(status = TradeStatus.CANCELLED)
        return Outcome.Success("Обмен отменён")
    }

    // ----- Аренда / экономика -----

    fun renewLease(accountId: String, wordId: String): Outcome {
        val acc = accounts[accountId] ?: return Outcome.Error("Аккаунт не найден")
        if (wordId !in acc.ownedWordIds) return Outcome.Error("Слово не в коллекции")
        if (acc.coins < Econ.LEASE_COST) return Outcome.Error("Недостаточно монет")
        acc.coins -= Econ.LEASE_COST
        val now = clock()
        val base = acc.leases[wordId] ?: now
        acc.leases[wordId] = maxOf(base, now) + Econ.LEASE_PERIOD_MS
        return Outcome.Success("Слово продлено на 30 дней")
    }

    fun dailyReward(accountId: String): Outcome {
        val acc = accounts[accountId] ?: return Outcome.Error("Аккаунт не найден")
        val now = clock()
        if (acc.lastDailyAt != 0L && now - acc.lastDailyAt < Econ.DAY_MS) {
            return Outcome.Error("Ежедневный бонус уже получен")
        }
        acc.lastDailyAt = now
        acc.coins += Econ.DAILY_REWARD
        return Outcome.Success("Получено " + Econ.DAILY_REWARD + " монет")
    }

    /** Возврат просроченных слов в пул + снятие их лотов. */
    fun expireLeases(now: Long = clock()) {
        accounts.values.forEach { acc ->
            val expired = acc.leases.filterValues { it < now }.keys.toList()
            expired.forEach { wordId ->
                acc.ownedWordIds.remove(wordId)
                acc.leases.remove(wordId)
                pool.add(wordId)
                listings.values.filter { it.wordId == wordId && it.sellerId == acc.id }
                    .forEach { listings.remove(it.id) }
            }
        }
    }

    // ----- Топ / витрина -----

    fun ownedWords(accountId: String): List<Word> =
        accounts[accountId]?.ownedWordIds?.mapNotNull { words[it] } ?: emptyList()

    fun portfolioValue(accountId: String): Long =
        accounts[accountId]?.ownedWordIds?.sumOf { words[it]?.estValue ?: 0L } ?: 0L

    fun topByCapital(limit: Int = 100): List<Pair<Account, Long>> =
        accounts.values
            .map { acc -> acc to acc.ownedWordIds.sumOf { words[it]?.estValue ?: 0L } }
            .sortedByDescending { it.second }
            .take(limit)

    fun openListings(): List<Listing> = listings.values.toList()
    fun openTrades(): List<TradeOffer> = trades.values.filter { it.status == TradeStatus.PENDING }
    fun bidsFor(listingId: String): List<Bid> = bids[listingId]?.toList() ?: emptyList()
}
