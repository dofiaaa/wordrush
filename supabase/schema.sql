-- =====================================================================
-- СловоВойна — схема бэкенда на Supabase (PostgreSQL)
-- Запускать в Supabase SQL Editor целиком, затем supabase/words_seed.sql
-- =====================================================================

-- ---------- Таблицы ----------

create table if not exists public.profiles (
  id uuid primary key references auth.users (id) on delete cascade,
  username text not null,
  coins bigint not null default 0,
  last_spin_at timestamptz,
  last_daily_at timestamptz,
  created_at timestamptz not null default now()
);

create table if not exists public.words (
  id text primary key,
  lemma text not null,
  pos text not null default '',
  frequency_ipm double precision not null default 0,
  rarity_tier text not null default 'common',
  est_value bigint not null default 10,
  definition text not null default '',
  examples jsonb not null default '[]'::jsonb
);

create table if not exists public.ownership (
  word_id text primary key references public.words (id) on delete cascade,
  user_id uuid not null references public.profiles (id) on delete cascade,
  lease_expires_at timestamptz not null
);
create index if not exists ownership_user_idx on public.ownership (user_id);

create table if not exists public.listings (
  id bigint generated always as identity primary key,
  word_id text not null unique references public.words (id) on delete cascade,
  seller_id uuid not null references public.profiles (id) on delete cascade,
  type text not null check (type in ('sale', 'auction')),
  price bigint not null check (price > 0),
  buy_now_price bigint not null default 0,
  created_at timestamptz not null default now()
);

create table if not exists public.bids (
  id bigint generated always as identity primary key,
  listing_id bigint not null references public.listings (id) on delete cascade,
  user_id uuid not null references public.profiles (id) on delete cascade,
  amount bigint not null check (amount > 0),
  created_at timestamptz not null default now()
);
create index if not exists bids_listing_idx on public.bids (listing_id);

create table if not exists public.trades (
  id bigint generated always as identity primary key,
  from_user_id uuid not null references public.profiles (id) on delete cascade,
  to_user_id uuid references public.profiles (id) on delete cascade,
  offered_word_id text not null references public.words (id) on delete cascade,
  requested_word_id text not null references public.words (id) on delete cascade,
  status text not null default 'pending' check (status in ('pending', 'accepted', 'declined', 'cancelled')),
  created_at timestamptz not null default now()
);

create table if not exists public.transactions (
  id bigint generated always as identity primary key,
  user_id uuid not null references public.profiles (id) on delete cascade,
  type text not null,
  amount bigint not null,
  counterparty_id uuid,
  word_id text,
  created_at timestamptz not null default now()
);

-- ---------- Автосоздание профиля при регистрации ----------

create or replace function public.handle_new_user()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
  insert into public.profiles (id, username, coins)
  values (new.id, 'Игрок' || substr(md5(new.id::text), 1, 6), 50);
  return new;
end;
$$;

drop trigger if exists on_auth_user_created on auth.users;
create trigger on_auth_user_created
  after insert on auth.users
  for each row execute function public.handle_new_user();

-- ---------- Экономические константы (зеркало core/Econ.kt) ----------
-- MARKET_FEE_PERCENT = 5
-- LEASE_PERIOD      = 30 дней
-- LEASE_COST        = 20
-- DAILY_REWARD      = 50
-- SPIN_REWARD       = 10
-- SPIN_COOLDOWN     = 10 секунд (демо; в релизе можно 60)

-- ---------- Хелпер: возврат просроченных слов в пул ----------

create or replace function public.expire_leases()
returns void
language plpgsql
security definer
set search_path = public
as $$
begin
  delete from public.listings
  where word_id in (
    select word_id from public.ownership where lease_expires_at < now()
  );
  delete from public.ownership where lease_expires_at < now();
end;
$$;

-- ---------- Рулетка ----------

create or replace function public.spin()
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  v_user uuid := auth.uid();
  v_word_id text := null;
  v_cooldown interval := interval '10 seconds';
  v_reward bigint := 10;
  v_lease interval := interval '30 days';
begin
  if v_user is null then
    return jsonb_build_object('ok', false, 'error', 'not authenticated');
  end if;

  if exists (
    select 1 from public.profiles p
    where p.id = v_user and p.last_spin_at is not null
      and p.last_spin_at > now() - v_cooldown
  ) then
    return jsonb_build_object('ok', false, 'error', 'cooldown');
  end if;

  perform public.expire_leases();

  for i in 1..5 loop
    v_word_id := null;
    insert into public.ownership (word_id, user_id, lease_expires_at)
    select w.id, v_user, now() + v_lease
    from public.words w
    where not exists (select 1 from public.ownership o where o.word_id = w.id)
    order by random()
    limit 1
    on conflict (word_id) do nothing
    returning word_id into v_word_id;
    exit when v_word_id is not null;
  end loop;

  if v_word_id is null then
    return jsonb_build_object('ok', false, 'error', 'no words left');
  end if;

  update public.profiles set last_spin_at = now(), coins = coins + v_reward where id = v_user;
  insert into public.transactions (user_id, type, amount, word_id)
  values (v_user, 'spin_reward', v_reward, v_word_id);

  return jsonb_build_object('ok', true, 'word_id', v_word_id);
end;
$$;

-- ---------- Ежедневный бонус ----------

create or replace function public.daily_reward()
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  v_user uuid := auth.uid();
  v_reward bigint := 50;
begin
  if v_user is null then
    return jsonb_build_object('ok', false, 'error', 'not authenticated');
  end if;

  if exists (
    select 1 from public.profiles p
    where p.id = v_user and p.last_daily_at is not null
      and p.last_daily_at > now() - interval '1 day'
  ) then
    return jsonb_build_object('ok', false, 'error', 'already claimed');
  end if;

  update public.profiles set last_daily_at = now(), coins = coins + v_reward where id = v_user;
  insert into public.transactions (user_id, type, amount) values (v_user, 'daily_reward', v_reward);
  return jsonb_build_object('ok', true, 'reward', v_reward);
end;
$$;

-- ---------- Рынок ----------

create or replace function public.list_for_sale(
  p_word_id text,
  p_price bigint,
  p_type text default 'sale',
  p_buy_now bigint default 0
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  v_user uuid := auth.uid();
begin
  if v_user is null then
    return jsonb_build_object('ok', false, 'error', 'not authenticated');
  end if;
  if p_price <= 0 then
    return jsonb_build_object('ok', false, 'error', 'bad price');
  end if;
  if p_type not in ('sale', 'auction') then
    return jsonb_build_object('ok', false, 'error', 'bad type');
  end if;

  perform public.expire_leases();

  if not exists (
    select 1 from public.ownership o
    where o.word_id = p_word_id and o.user_id = v_user and o.lease_expires_at > now()
  ) then
    return jsonb_build_object('ok', false, 'error', 'not owner');
  end if;

  insert into public.listings (word_id, seller_id, type, price, buy_now_price)
  values (p_word_id, v_user, p_type, p_price, p_buy_now)
  on conflict (word_id) do update
    set type = excluded.type,
        price = excluded.price,
        buy_now_price = excluded.buy_now_price,
        seller_id = excluded.seller_id,
        created_at = now();

  return jsonb_build_object('ok', true);
end;
$$;

create or replace function public.cancel_listing(p_listing_id bigint)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  v_user uuid := auth.uid();
begin
  if v_user is null then
    return jsonb_build_object('ok', false, 'error', 'not authenticated');
  end if;
  delete from public.listings where id = p_listing_id and seller_id = v_user;
  return jsonb_build_object('ok', true);
end;
$$;

create or replace function public.place_bid(p_listing_id bigint, p_amount bigint)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  v_user uuid := auth.uid();
  v_listing record;
  v_current bigint;
begin
  if v_user is null then
    return jsonb_build_object('ok', false, 'error', 'not authenticated');
  end if;

  select * into v_listing from public.listings where id = p_listing_id for update;
  if not found then
    return jsonb_build_object('ok', false, 'error', 'not found');
  end if;
  if v_listing.type <> 'auction' then
    return jsonb_build_object('ok', false, 'error', 'not auction');
  end if;

  select coalesce(max(amount), v_listing.price) into v_current
  from public.bids where listing_id = p_listing_id;
  if p_amount <= v_current then
    return jsonb_build_object('ok', false, 'error', 'too low');
  end if;

  if (select coins from public.profiles where id = v_user) < p_amount then
    return jsonb_build_object('ok', false, 'error', 'not enough coins');
  end if;

  insert into public.bids (listing_id, user_id, amount) values (p_listing_id, v_user, p_amount);
  return jsonb_build_object('ok', true);
end;
$$;

create or replace function public.buy(p_listing_id bigint)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  v_user uuid := auth.uid();
  v_listing record;
  v_price bigint;
  v_fee bigint;
  v_lease interval := interval '30 days';
  v_lock uuid;
begin
  if v_user is null then
    return jsonb_build_object('ok', false, 'error', 'not authenticated');
  end if;

  perform public.expire_leases();

  select * into v_listing from public.listings where id = p_listing_id for update;
  if not found then
    return jsonb_build_object('ok', false, 'error', 'not found');
  end if;
  if v_listing.seller_id = v_user then
    return jsonb_build_object('ok', false, 'error', 'own listing');
  end if;

  if not exists (
    select 1 from public.ownership o
    where o.word_id = v_listing.word_id and o.user_id = v_listing.seller_id
      and o.lease_expires_at > now()
  ) then
    delete from public.listings where id = p_listing_id;
    return jsonb_build_object('ok', false, 'error', 'already sold');
  end if;

  if v_listing.type = 'auction' then
    select coalesce(max(amount), v_listing.price) into v_price
    from public.bids where listing_id = p_listing_id;
  else
    v_price := v_listing.price;
  end if;

  -- блокируем балансы в детерминированном порядке (по id) — защита от дедлока
  for v_lock in
    select id from public.profiles where id in (v_user, v_listing.seller_id) order by id for update
  loop
    null;
  end loop;

  if (select coins from public.profiles where id = v_user) < v_price then
    return jsonb_build_object('ok', false, 'error', 'not enough coins');
  end if;

  v_fee := greatest(1, v_price * 5 / 100);

  update public.profiles set coins = coins - v_price where id = v_user;
  update public.profiles set coins = coins + (v_price - v_fee) where id = v_listing.seller_id;

  delete from public.ownership where word_id = v_listing.word_id;
  insert into public.ownership (word_id, user_id, lease_expires_at)
  values (v_listing.word_id, v_user, now() + v_lease);

  delete from public.bids where listing_id = p_listing_id;
  delete from public.listings where id = p_listing_id;

  insert into public.transactions (user_id, type, amount, counterparty_id, word_id)
  values (v_user, 'buy', -v_price, v_listing.seller_id, v_listing.word_id);
  insert into public.transactions (user_id, type, amount, counterparty_id, word_id)
  values (v_listing.seller_id, 'sell', v_price - v_fee, v_user, v_listing.word_id);

  return jsonb_build_object('ok', true, 'word_id', v_listing.word_id, 'price', v_price);
end;
$$;

-- ---------- Обмен ----------

create or replace function public.create_trade(
  p_offered_word_id text,
  p_requested_word_id text,
  p_to_user_id uuid default null
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  v_user uuid := auth.uid();
begin
  if v_user is null then
    return jsonb_build_object('ok', false, 'error', 'not authenticated');
  end if;
  if p_offered_word_id = p_requested_word_id then
    return jsonb_build_object('ok', false, 'error', 'same word');
  end if;
  if not exists (select 1 from public.words where id = p_requested_word_id) then
    return jsonb_build_object('ok', false, 'error', 'requested word not found');
  end if;
  if not exists (
    select 1 from public.ownership o
    where o.word_id = p_offered_word_id and o.user_id = v_user and o.lease_expires_at > now()
  ) then
    return jsonb_build_object('ok', false, 'error', 'not owner');
  end if;

  insert into public.trades (from_user_id, to_user_id, offered_word_id, requested_word_id)
  values (v_user, p_to_user_id, p_offered_word_id, p_requested_word_id);

  return jsonb_build_object('ok', true);
end;
$$;

create or replace function public.accept_trade(p_trade_id bigint)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  v_user uuid := auth.uid();
  v_trade record;
begin
  if v_user is null then
    return jsonb_build_object('ok', false, 'error', 'not authenticated');
  end if;

  perform public.expire_leases();

  select * into v_trade from public.trades where id = p_trade_id for update;
  if not found then
    return jsonb_build_object('ok', false, 'error', 'not found');
  end if;
  if v_trade.status <> 'pending' then
    return jsonb_build_object('ok', false, 'error', 'not pending');
  end if;
  if v_trade.to_user_id is not null and v_trade.to_user_id <> v_user then
    return jsonb_build_object('ok', false, 'error', 'not for you');
  end if;

  if not exists (
    select 1 from public.ownership o
    where o.word_id = v_trade.requested_word_id and o.user_id = v_user and o.lease_expires_at > now()
  ) then
    return jsonb_build_object('ok', false, 'error', 'you do not own requested word');
  end if;
  if not exists (
    select 1 from public.ownership o
    where o.word_id = v_trade.offered_word_id and o.user_id = v_trade.from_user_id and o.lease_expires_at > now()
  ) then
    return jsonb_build_object('ok', false, 'error', 'author lost offered word');
  end if;

  update public.ownership set user_id = v_user where word_id = v_trade.offered_word_id;
  update public.ownership set user_id = v_trade.from_user_id where word_id = v_trade.requested_word_id;
  update public.trades set status = 'accepted' where id = p_trade_id;

  return jsonb_build_object('ok', true);
end;
$$;

create or replace function public.decline_trade(p_trade_id bigint)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  v_user uuid := auth.uid();
begin
  if v_user is null then
    return jsonb_build_object('ok', false, 'error', 'not authenticated');
  end if;
  update public.trades set status = 'declined'
  where id = p_trade_id and status = 'pending'
    and (from_user_id = v_user or to_user_id = v_user);
  return jsonb_build_object('ok', true);
end;
$$;

create or replace function public.cancel_trade(p_trade_id bigint)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  v_user uuid := auth.uid();
begin
  if v_user is null then
    return jsonb_build_object('ok', false, 'error', 'not authenticated');
  end if;
  update public.trades set status = 'cancelled'
  where id = p_trade_id and status = 'pending' and from_user_id = v_user;
  return jsonb_build_object('ok', true);
end;
$$;

-- ---------- Аренда ----------

create or replace function public.renew_lease(p_word_id text)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  v_user uuid := auth.uid();
  v_cost bigint := 20;
  v_lease interval := interval '30 days';
begin
  if v_user is null then
    return jsonb_build_object('ok', false, 'error', 'not authenticated');
  end if;

  if not exists (
    select 1 from public.ownership o where o.word_id = p_word_id and o.user_id = v_user
  ) then
    return jsonb_build_object('ok', false, 'error', 'not owner');
  end if;
  if (select coins from public.profiles where id = v_user) < v_cost then
    return jsonb_build_object('ok', false, 'error', 'not enough coins');
  end if;

  update public.profiles set coins = coins - v_cost where id = v_user;
  update public.ownership
  set lease_expires_at = greatest(lease_expires_at, now()) + v_lease
  where word_id = p_word_id and user_id = v_user;

  insert into public.transactions (user_id, type, amount, word_id) values (v_user, 'renew_lease', -v_cost, p_word_id);
  return jsonb_build_object('ok', true);
end;
$$;

-- ---------- Топ ----------

create or replace function public.get_leaderboard(p_limit integer default 100)
returns table(user_id uuid, username text, capital bigint, word_count bigint)
language sql
security definer
set search_path = public
as $$
  select p.id, p.username, coalesce(sum(w.est_value), 0)::bigint as capital, count(o.word_id)::bigint as word_count
  from public.profiles p
  left join public.ownership o on o.user_id = p.id
  left join public.words w on w.id = o.word_id
  group by p.id, p.username
  order by capital desc, word_count desc
  limit p_limit;
$$;

-- ---------- RLS ----------

alter table public.profiles enable row level security;
alter table public.words enable row level security;
alter table public.ownership enable row level security;
alter table public.listings enable row level security;
alter table public.bids enable row level security;
alter table public.trades enable row level security;
alter table public.transactions enable row level security;

create policy "profiles read" on public.profiles for select using (true);
create policy "words read" on public.words for select using (true);
create policy "ownership own read" on public.ownership for select using (user_id = auth.uid());
create policy "listings read" on public.listings for select using (true);
create policy "bids read" on public.bids for select using (true);
create policy "trades read" on public.trades for select using (status = 'pending' or from_user_id = auth.uid() or to_user_id = auth.uid());
create policy "transactions own read" on public.transactions for select using (user_id = auth.uid());

-- ---------- Права ----------

grant usage on schema public to anon, authenticated;
grant select on public.profiles, public.words, public.ownership, public.listings, public.bids, public.trades, public.transactions to anon, authenticated;

grant execute on function public.spin() to authenticated;
grant execute on function public.daily_reward() to authenticated;
grant execute on function public.list_for_sale(text, bigint, text, bigint) to authenticated;
grant execute on function public.cancel_listing(bigint) to authenticated;
grant execute on function public.place_bid(bigint, bigint) to authenticated;
grant execute on function public.buy(bigint) to authenticated;
grant execute on function public.create_trade(text, text, uuid) to authenticated;
grant execute on function public.accept_trade(bigint) to authenticated;
grant execute on function public.decline_trade(bigint) to authenticated;
grant execute on function public.cancel_trade(bigint) to authenticated;
grant execute on function public.renew_lease(text) to authenticated;
grant execute on function public.get_leaderboard(integer) to anon, authenticated;
