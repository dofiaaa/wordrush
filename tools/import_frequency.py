#!/usr/bin/env python3
"""Импорт реального частотного списка (формат "слово количество") в words.json.

Источник (OpenSubtitles, CC BY-SA 4.0 — укажите атрибуцию):
  https://raw.githubusercontent.com/hermitdave/FrequencyWords/master/content/2018/ru/ru_50k.txt

Использование:
  python3 import_frequency.py freq.txt app/src/main/assets/words.json --limit 30000
"""
import datetime
import json
import math
import sys

# Определения для частых слов (из демо-словаря), чтобы карточки были информативнее.
DEFINITIONS = {
    "пицца": ("сущ", "Итальянское блюдо — лепёшка с начинкой, запечённая в печи."),
    "деньги": ("сущ", "Средство платежа и накопления."),
    "любовь": ("сущ", "Глубокое чувство привязанности и заботы."),
    "дом": ("сущ", "Жилище, здание, где живут люди."),
    "время": ("сущ", "Мера длительности событий."),
    "человек": ("сущ", "Разумное существо."),
    "жизнь": ("сущ", "Существование, деятельность."),
    "работа": ("сущ", "Труд, занятие."),
    "друг": ("сущ", "Близкий товарищ."),
    "слово": ("сущ", "Единица речи."),
    "книга": ("сущ", "Печатное произведение."),
    "солнце": ("сущ", "Звезда, дающая свет и тепло."),
    "мама": ("сущ", "Родительница."),
    "машина": ("сущ", "Автомобиль, транспортное средство."),
    "море": ("сущ", "Большой водоём."),
    "небо": ("сущ", "Пространство над землёй."),
    "счастье": ("сущ", "Состояние полного удовлетворения."),
    "мир": ("сущ", "Отсутствие войны; вселенная."),
    "наука": ("сущ", "Система знаний о мире."),
    "кофе": ("сущ", "Напиток из обжаренных зёрен."),
    "наукоград": ("сущ", "Город с высокой концентрацией научных организаций."),
    "эфемерный": ("прил", "Недолговечный, мимолётный."),
    "эпистемология": ("сущ", "Раздел философии о природе познания."),
    "конъюнктура": ("сущ", "Сложившаяся обстановка, стечение обстоятельств."),
}


def rarity(ipm):
    if ipm >= 10:
        return "common"
    if ipm >= 1:
        return "uncommon"
    if ipm >= 0.1:
        return "rare"
    if ipm >= 0.01:
        return "epic"
    if ipm >= 0.001:
        return "legendary"
    return "mythic"


def main():
    if len(sys.argv) < 3:
        print("usage: import_frequency.py freq.txt out.json [--limit N]")
        sys.exit(1)
    src = sys.argv[1]
    out = sys.argv[2]
    limit = None
    if "--limit" in sys.argv:
        limit = int(sys.argv[sys.argv.index("--limit") + 1])

    pairs = []
    total = 0
    with open(src, encoding="utf-8") as f:
        for line in f:
            parts = line.split()
            if len(parts) < 2:
                continue
            word = parts[0]
            try:
                count = int(parts[1])
            except ValueError:
                continue
            pairs.append((word, count))
            total += count
            if limit is not None and len(pairs) >= limit:
                break

    ipms = [c / total * 1_000_000 for _, c in pairs if c > 0]
    min_log = math.log10(min(ipms)) if ipms else 0.0
    max_log = math.log10(max(ipms)) if ipms else 1.0

    words = []
    for word, count in pairs:
        ipm = count / total * 1_000_000
        if ipm <= 0:
            continue
        norm = (math.log10(ipm) - min_log) / (max_log - min_log)
        norm = max(0.0, min(1.0, norm))
        est = int(round(5 + 995 * norm))
        pos, definition = DEFINITIONS.get(word, ("", ""))
        words.append({
            "id": word,
            "lemma": word,
            "pos": pos,
            "frequency_ipm": round(ipm, 4),
            "rarity_tier": rarity(ipm),
            "est_value": est,
            "definition": definition,
            "examples": [],
        })

    data = {
        "version": 2,
        "generated_at": datetime.datetime.utcnow().isoformat() + "Z",
        "words": words,
    }
    with open(out, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, separators=(",", ":"))

    vals = [w["est_value"] for w in words]
    print("OK:", len(words), "слов ->", out)
    print("ipm min=" + str(round(min(ipms), 3)) + " max=" + str(round(max(ipms), 3)))
    print("est_value min=" + str(min(vals)) + " max=" + str(max(vals)))


if __name__ == "__main__":
    main()
