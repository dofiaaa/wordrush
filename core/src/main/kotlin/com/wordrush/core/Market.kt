package com.wordrush.core

enum class ListingType { SALE, AUCTION }

/** Лот на рынке: продажа по фикс. цене или аукцион. */
data class Listing(
    val id: String,
    val wordId: String,
    val sellerId: String,
    val type: ListingType,
    val price: Long,          // SALE: фикс. цена; AUCTION: стартовая
    val buyNowPrice: Long = 0L, // AUCTION: мгновенный выкуп (0 = нет)
    val createdAt: Long = 0L,
)

data class Bid(
    val userId: String,
    val amount: Long,
    val at: Long = 0L,
)

enum class TradeStatus { PENDING, ACCEPTED, DECLINED, CANCELLED }

/** Обмен (бартер 1-на-1): отдаю offeredWordId, хочу requestedWordId. */
data class TradeOffer(
    val id: String,
    val fromUserId: String,
    val toUserId: String?,       // null = открытое предложение любому владельцу
    val offeredWordId: String,
    val requestedWordId: String,
    val status: TradeStatus = TradeStatus.PENDING,
    val createdAt: Long = 0L,
)
