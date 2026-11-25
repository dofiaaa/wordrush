package com.wordrush.core

/** Экономические константы. Без донатов: монеты только создаются игрой. */
object Econ {
    /** Комиссия рынка, % от суммы сделки (сток монет). */
    const val MARKET_FEE_PERCENT = 5L

    /** Срок аренды слова. По истечении слово возвращается в пул (сток + защита от «скупердяев»). */
    const val LEASE_PERIOD_MS = 30L * 24 * 60 * 60 * 1000

    /** Стоимость продления аренды (сток). */
    const val LEASE_COST = 20L

    /** Ежедневный бонус (кран — источник монет). */
    const val DAILY_REWARD = 50L

    /** Награда за каждый спин (кран). */
    const val SPIN_REWARD = 10L

    /** Перезарядка рулетки. Спины всегда бесплатные, ограничены только временем. */
    const val SPIN_COOLDOWN_MS = 10_000L

    const val DAY_MS = 24L * 60 * 60 * 1000

    fun marketFee(amount: Long): Long = maxOf(1L, amount * MARKET_FEE_PERCENT / 100L)
}
