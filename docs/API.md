# API бэкенда (Supabase)

Бэкенд — Supabase (Postgres). Клиент общается через Supabase SDK: PostgREST для
чтения, RPC для изменений. Все мутации выполняет сервер (SECURITY DEFINER).

## Аутентификация

- signInAnonymously() — анонимный вход, выдаёт пользователя (auth.uid()).
- Профиль в таблице profiles создаётся автоматически триггером (старт: 50 монет).

## Чтение (PostgREST SELECT)

- /rest/v1/words — словарь (публично).
- /rest/v1/ownership — моя коллекция (RLS: только своё).
- /rest/v1/listings — лоты (публично).
- /rest/v1/trades?status=eq.pending — открытые обмены.
- /rest/v1/bids — ставки.
- /rest/v1/profiles — профили.

## RPC (изменения)

| RPC | Аргументы | Описание |
| --- | --- | --- |
| spin() | — | случайное невыбитое слово, +10 монет |
| daily_reward() | — | +50 монет раз в сутки |
| list_for_sale(word_id, price, type, buy_now_price) | text, bigint, text, bigint | выставить слово |
| cancel_listing(listing_id) | bigint | снять свой лот |
| place_bid(listing_id, amount) | bigint, bigint | ставка на аукцион |
| buy(listing_id) | bigint | купить (атомарно) |
| create_trade(offered, requested, to_user_id) | text, text, uuid | предложить обмен |
| accept_trade(trade_id) | bigint | принять обмен |
| decline_trade(trade_id) | bigint | отклонить |
| cancel_trade(trade_id) | bigint | отменить (автор) |
| renew_lease(word_id) | text | продлить аренду, -20 монет |
| get_leaderboard(limit) | integer | топ по капиталу |

Все функции возвращают jsonb: {ok: true, ...} или {ok: false, error: "..."}.

## Константы экономики (совпадают с core/Econ.kt)

Комиссия 5%, аренда 30 дней / 20 монет, ежедневный бонус 50, награда за спин 10,
кулдаун спина 10 секунд.
