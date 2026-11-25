package com.wordrush.core

/**
 * Слово — базовый «актив» игры.
 *
 * Модель ценности «как у доменов»: редким в языке словам это НЕ помогает,
 * ценность определяется спросом. Частотность (frequencyIpm) — лишь
 * ориентир для стартовой оценки estValue, а реальную цену ставит рынок.
 */
data class Word(
    val id: String,
    val lemma: String,
    val pos: String = "",
    val frequencyIpm: Double = 0.0,
    val rarityTier: RarityTier = RarityTier.COMMON,
    val estValue: Long = 10L,
    val definition: String = "",
    val examples: List<String> = emptyList(),
)

/** Тир редкости — косметический «стат» карточки, на выпадение и цену НЕ влияет. */
enum class RarityTier(val label: String) {
    COMMON("Обычное"),
    UNCOMMON("Необычное"),
    RARE("Редкое"),
    EPIC("Эпическое"),
    LEGENDARY("Легендарное"),
    MYTHIC("Мифическое"),
}

fun rarityFromFrequency(ipm: Double): RarityTier = when {
    ipm >= 10.0 -> RarityTier.COMMON
    ipm >= 1.0 -> RarityTier.UNCOMMON
    ipm >= 0.1 -> RarityTier.RARE
    ipm >= 0.01 -> RarityTier.EPIC
    ipm >= 0.001 -> RarityTier.LEGENDARY
    else -> RarityTier.MYTHIC
}

/** Стартовая оценка стоимости по частоте (лог-шкала, 5..1000). */
fun estimateValue(ipm: Double, minLog: Double, maxLog: Double): Long {
    if (ipm <= 0.0) return 5L
    if (maxLog <= minLog) return 500L
    val norm = ((kotlin.math.log10(ipm) - minLog) / (maxLog - minLog)).coerceIn(0.0, 1.0)
    return (5 + 995 * norm).toLong()
}
