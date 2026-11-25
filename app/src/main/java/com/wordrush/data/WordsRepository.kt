package com.wordrush.data

import android.content.Context
import com.wordrush.core.RarityTier
import com.wordrush.core.Word
import org.json.JSONObject

/** Загружает словарь из assets/words.json (генерируется tools/build_dictionary.py). */
class WordsRepository(private val context: Context) {

    fun load(): List<Word> {
        val text = context.assets.open("words.json").bufferedReader().use { it.readText() }
        val root = JSONObject(text)
        val arr = root.getJSONArray("words")
        val result = ArrayList<Word>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val exArr = o.optJSONArray("examples")
            val examples = ArrayList<String>()
            if (exArr != null) {
                for (j in 0 until exArr.length()) examples.add(exArr.getString(j))
            }
            result.add(
                Word(
                    id = o.getString("id"),
                    lemma = o.getString("lemma"),
                    pos = o.optString("pos", ""),
                    frequencyIpm = o.optDouble("frequency_ipm", 0.0),
                    rarityTier = parseTier(o.optString("rarity_tier", "common")),
                    estValue = o.optLong("est_value", 10L),
                    definition = o.optString("definition", ""),
                    examples = examples,
                )
            )
        }
        return result
    }

    private fun parseTier(s: String): RarityTier = when (s.lowercase()) {
        "uncommon" -> RarityTier.UNCOMMON
        "rare" -> RarityTier.RARE
        "epic" -> RarityTier.EPIC
        "legendary" -> RarityTier.LEGENDARY
        "mythic" -> RarityTier.MYTHIC
        else -> RarityTier.COMMON
    }
}
