# Контракт серверного API (для подключения после прототипа)

Сейчас игра работает на локальном in-memory движке. Для мультиплеера нужен сервер,
который является **единственным источником истины** о владении и монетах.

## Аутентификация

- `POST /auth/anonymous` — вход без регистрации (гостевой токен).
- `POST /auth/refresh` — продление токена.
- `GET /me` — профиль: `{ id, name, coins, portfolio_value }`.

## Коллекция и рулетка

- `POST /spin` — серверный RNG, возвращает выпавшее слово (равновероятно из пула).
- `GET /collection` — мои слова (id, lease_expires_at, est_value).
- `POST /words/:id/renew` — продлить аренду (списывает монеты).
- `GET /words/:id` — карточка слова (лемма, определение, владелец, история цены).
- `POST /daily` — ежедневный бонус.

## Рынок

- `GET /market` — открытые лоты (тип: sale | auction, цена, продавец).
- `POST /market` — выставить слово: `{ word_id, type, price, buy_now_price? }`.
- `POST /market/:listingId/buy` — купить (атомарно переводит монеты и владение).
- `POST /market/:listingId/bid` — ставка на аукцион.
- `DELETE /market/:listingId` — снять свой лот.

## Обмен

- `POST /trades` — создать: `{ offered_word_id, requested_word_id, to_user_id? }`.
- `POST /trades/:id/accept` — принять (обмен владения 1-на-1).
- `POST /trades/:id/decline` / `POST /trades/:id/cancel`.
- `GET /trades` — открытые предложения.

## Топ

- `GET /top?limit=100` — аккаунты по капиталу портфеля.

## Реализация (вариант)

Firebase: Auth + Firestore + Cloud Functions.

- `users/{uid}` — монеты, last_spin_at, last_daily_at.
- `words/{lemma}` — владелец, lease_expires_at, история сделок.
- `listings/{id}` — лот; `bids/{listingId}/{bidId}` — ставки.
- `trades/{id}` — обмен.

Ключевые правила:

- `spin`, `buy`, `accept_trade` — Cloud Functions с Firestore-транзакциями (атомарность).
- Валидация владения и баланса — на сервере, никогда на клиенте.
- Топ — запланированная функция, пересчитывающая рейтинг раз в N минут.
