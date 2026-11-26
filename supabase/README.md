# Бэкенд на Supabase (бесплатный тариф)

## Быстрый старт

1. Создайте проект на https://supabase.com (бесплатный тариф, без карты).
2. Включите анонимный вход: Authentication → Sign In / Providers → Email →
   включите «Allow anonymous sign-ins».
3. Скопируйте Project URL и anon public key: Settings → API.
4. Выполните schema.sql в SQL Editor (целиком).
5. Выполните words_seed.sql в SQL Editor (или пересоздайте: python3 supabase/seed_words.py).
6. Готово — клиент подключается через Supabase SDK.

## Как это устроено

- Все изменения (spin, buy, trade, renew) — через RPC-функции (SECURITY DEFINER),
  которые выполняются с правами владельца и атомарно проверяют владение и баланс.
- Чтение публичных данных — обычные SELECT (RLS разрешает чтение).
- Владение и монеты нельзя подделать с клиента: клиент шлёт только «намерение».

## Таблицы

profiles, words, ownership, listings, bids, trades, transactions

## RPC-функции (все возвращают jsonb: {ok: true, ...} | {ok: false, error})

- spin()
- daily_reward()
- list_for_sale(word_id, price, type, buy_now_price)
- cancel_listing(listing_id)
- place_bid(listing_id, amount)
- buy(listing_id)
- create_trade(offered_word_id, requested_word_id, to_user_id)
- accept_trade(trade_id) / decline_trade(trade_id) / cancel_trade(trade_id)
- renew_lease(word_id)
- get_leaderboard(limit)

## Чтение (SELECT, RLS уже фильтрует)

- Моя коллекция: select * from ownership (вернёт только ваши строки)
- Лоты: select * from listings
- Обмены: select * from trades where status = 'pending'
- Топ: select * from get_leaderboard(100)

## Аренда и пул

Просроченные слова возвращаются в пул лениво: expire_leases() вызывается в начале
spin / list_for_sale / buy / accept_trade. Для периодической очистки добавьте pg_cron
(раз в час) или запланированную Edge Function.

## Константы экономики

Заданы в schema.sql (см. комментарий) и должны совпадать с core/Econ.kt:
комиссия 5%, аренда 30 дней / 20 монет, ежедневный бонус 50, награда за спин 10,
кулдаун спина 10 с.
