#!/usr/bin/env python3
"""Генерирует supabase/words_seed.sql из app/src/main/assets/words.json.

Запуск: python3 supabase/seed_words.py
Затем выполните supabase/words_seed.sql в Supabase SQL Editor.
"""
import json
import os

BASE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(BASE, "app", "src", "main", "assets", "words.json")
OUT = os.path.join(BASE, "supabase", "words_seed.sql")
BATCH = 500
NL = chr(10)


def esc(s):
    return s.replace("'", "''")


def main():
    with open(SRC, encoding="utf-8") as f:
        data = json.load(f)
    words = data["words"]

    parts = ["-- СловоВойна: сид словаря (сгенерировано seed_words.py)", "begin;"]
    cols = "(id, lemma, pos, frequency_ipm, rarity_tier, est_value, definition, examples)"

    for i in range(0, len(words), BATCH):
        chunk = words[i:i + BATCH]
        rows = []
        for w in chunk:
            examples = json.dumps(w.get("examples", []), ensure_ascii=False).replace("'", "''")
            row = "(" + ",".join([
                "'" + esc(str(w["id"])) + "'",
                "'" + esc(str(w["lemma"])) + "'",
                "'" + esc(str(w.get("pos", ""))) + "'",
                str(w.get("frequency_ipm", 0)),
                "'" + esc(str(w.get("rarity_tier", "common"))) + "'",
                str(w.get("est_value", 10)),
                "'" + esc(str(w.get("definition", ""))) + "'",
                "'" + examples + "'::jsonb",
            ]) + ")"
            rows.append(row)
        parts.append("insert into public.words " + cols + " values")
        parts.append(("," + NL).join(rows) + ";")

    parts.append("commit;")
    with open(OUT, "w", encoding="utf-8") as f:
        f.write(NL.join(parts) + NL)
    print("OK:", len(words), "слов ->", OUT)


if __name__ == "__main__":
    main()
