-- =====================================================================
-- myFifty — schema Supabase
-- Esegui questo file per intero nel SQL Editor del tuo progetto Supabase.
-- È idempotente: puoi rilanciarlo senza perdere dati.
-- =====================================================================

create extension if not exists pgcrypto with schema extensions;

-- ---------------------------------------------------------------------
-- Profili utente (uno per ogni account auth.users)
-- ---------------------------------------------------------------------
create table if not exists public.profiles (
  id           uuid primary key references auth.users (id) on delete cascade,
  display_name text not null default '',
  created_at   timestamptz not null default now()
);

-- ---------------------------------------------------------------------
-- Case (gruppi di spesa)
-- ---------------------------------------------------------------------
create table if not exists public.households (
  id          uuid primary key default gen_random_uuid(),
  name        text not null check (char_length(name) between 1 and 60),
  invite_code text not null unique,
  currency    text not null default 'EUR',
  created_by  uuid not null default auth.uid() references public.profiles (id),
  created_at  timestamptz not null default now()
);

create table if not exists public.household_members (
  household_id uuid not null references public.households (id) on delete cascade,
  user_id      uuid not null references public.profiles (id) on delete cascade,
  role         text not null default 'member' check (role in ('owner', 'member')),
  joined_at    timestamptz not null default now(),
  primary key (household_id, user_id)
);
create index if not exists household_members_user_idx on public.household_members (user_id);

-- ---------------------------------------------------------------------
-- Movimenti
--   kind = 'expense'    spesa pagata da paid_by, divisa tra split_among
--   kind = 'refund'     rimborso ricevuto da paid_by (es. conguaglio a
--                       credito) che spetta a split_among
--   kind = 'settlement' pagamento diretto da paid_by a split_among[1]
-- Gli importi sono in centesimi per evitare errori di arrotondamento.
-- ---------------------------------------------------------------------
create table if not exists public.expenses (
  id           uuid primary key default gen_random_uuid(),
  household_id uuid not null references public.households (id) on delete cascade,
  kind         text not null default 'expense' check (kind in ('expense', 'refund', 'settlement')),
  description  text not null default '' check (char_length(description) <= 120),
  category     text not null default 'other',
  amount_cents bigint not null check (amount_cents > 0 and amount_cents < 100000000000),
  expense_date date not null default current_date,
  paid_by      uuid not null references public.profiles (id),
  split_among  uuid[] not null check (cardinality(split_among) > 0),
  recurring    boolean not null default false,
  notes        text check (notes is null or char_length(notes) <= 500),
  created_by   uuid default auth.uid() references public.profiles (id),
  created_at   timestamptz not null default now(),
  updated_by   uuid references public.profiles (id),
  updated_at   timestamptz not null default now()
);
create index if not exists expenses_household_date_idx on public.expenses (household_id, expense_date desc);

-- ---------------------------------------------------------------------
-- Funzioni di supporto
-- ---------------------------------------------------------------------

-- true se l'utente corrente è membro della casa (security definer per
-- evitare ricorsione nelle policy di household_members)
create or replace function public.is_member(p_household uuid)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
  select exists (
    select 1 from public.household_members m
    where m.household_id = p_household and m.user_id = auth.uid()
  );
$$;

-- true se l'utente corrente condivide almeno una casa con p_user
create or replace function public.shares_household(p_user uuid)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
  select exists (
    select 1
    from public.household_members a
    join public.household_members b on a.household_id = b.household_id
    where a.user_id = auth.uid() and b.user_id = p_user
  );
$$;

create or replace function public.new_invite_code()
returns text
language sql
volatile
set search_path = ''
as $$
  -- 8 caratteri senza simboli ambigui (niente 0/O, 1/I)
  select string_agg(substr('ABCDEFGHJKLMNPQRSTUVWXYZ23456789', 1 + floor(random() * 32)::int, 1), '')
  from generate_series(1, 8);
$$;

-- Profilo creato automaticamente alla registrazione
create or replace function public.handle_new_user()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
  insert into public.profiles (id, display_name)
  values (
    new.id,
    coalesce(nullif(trim(new.raw_user_meta_data ->> 'display_name'), ''), split_part(new.email, '@', 1))
  )
  on conflict (id) do nothing;
  return new;
end;
$$;

drop trigger if exists on_auth_user_created on auth.users;
create trigger on_auth_user_created
  after insert on auth.users
  for each row execute function public.handle_new_user();

-- Profili per gli utenti già esistenti (se lo schema viene applicato dopo)
insert into public.profiles (id, display_name)
select u.id, coalesce(nullif(trim(u.raw_user_meta_data ->> 'display_name'), ''), split_part(u.email, '@', 1))
from auth.users u
on conflict (id) do nothing;

-- Validazione movimenti: chi paga e chi partecipa devono essere membri
create or replace function public.validate_expense()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_missing int;
begin
  if auth.uid() is not null and not public.is_member(new.household_id) then
    raise exception 'non sei membro di questa casa';
  end if;

  if not exists (select 1 from public.household_members
                 where household_id = new.household_id and user_id = new.paid_by) then
    raise exception 'paid_by non è membro della casa';
  end if;

  select count(*) into v_missing
  from unnest(new.split_among) as s(uid)
  where not exists (select 1 from public.household_members
                    where household_id = new.household_id and user_id = s.uid);
  if v_missing > 0 then
    raise exception 'split_among contiene utenti che non sono membri della casa';
  end if;

  if new.kind = 'settlement' and cardinality(new.split_among) <> 1 then
    raise exception 'un saldo tra membri deve avere un solo destinatario';
  end if;

  if tg_op = 'UPDATE' then
    new.household_id := old.household_id;  -- non si sposta un movimento tra case
    new.created_by   := old.created_by;
    new.created_at   := old.created_at;
  end if;
  new.updated_at := now();
  new.updated_by := auth.uid();
  return new;
end;
$$;

drop trigger if exists expenses_validate on public.expenses;
create trigger expenses_validate
  before insert or update on public.expenses
  for each row execute function public.validate_expense();

-- ---------------------------------------------------------------------
-- RPC chiamate dall'app
-- ---------------------------------------------------------------------
create or replace function public.create_household(p_name text, p_currency text default 'EUR')
returns public.households
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_h public.households;
begin
  if auth.uid() is null then
    raise exception 'non autenticato';
  end if;
  insert into public.households (name, invite_code, currency, created_by)
  values (trim(p_name), public.new_invite_code(), coalesce(nullif(p_currency, ''), 'EUR'), auth.uid())
  returning * into v_h;
  insert into public.household_members (household_id, user_id, role)
  values (v_h.id, auth.uid(), 'owner');
  return v_h;
end;
$$;

create or replace function public.join_household(p_code text)
returns public.households
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_h public.households;
begin
  if auth.uid() is null then
    raise exception 'non autenticato';
  end if;
  select * into v_h from public.households where invite_code = upper(trim(p_code));
  if not found then
    raise exception 'codice invito non valido';
  end if;
  insert into public.household_members (household_id, user_id, role)
  values (v_h.id, auth.uid(), 'member')
  on conflict do nothing;
  return v_h;
end;
$$;

create or replace function public.regenerate_invite_code(p_household uuid)
returns text
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_code text;
begin
  if not public.is_member(p_household) then
    raise exception 'non sei membro di questa casa';
  end if;
  v_code := public.new_invite_code();
  update public.households set invite_code = v_code where id = p_household;
  return v_code;
end;
$$;

create or replace function public.leave_household(p_household uuid)
returns void
language plpgsql
security definer
set search_path = ''
as $$
begin
  delete from public.household_members
  where household_id = p_household and user_id = auth.uid();
  -- se non resta nessuno, la casa (e i suoi movimenti) viene eliminata
  delete from public.households h
  where h.id = p_household
    and not exists (select 1 from public.household_members m where m.household_id = h.id);
end;
$$;

revoke all on function public.create_household(text, text) from public, anon;
revoke all on function public.join_household(text) from public, anon;
revoke all on function public.regenerate_invite_code(uuid) from public, anon;
revoke all on function public.leave_household(uuid) from public, anon;
grant execute on function public.create_household(text, text) to authenticated;
grant execute on function public.join_household(text) to authenticated;
grant execute on function public.regenerate_invite_code(uuid) to authenticated;
grant execute on function public.leave_household(uuid) to authenticated;

-- ---------------------------------------------------------------------
-- Row Level Security
-- ---------------------------------------------------------------------
alter table public.profiles          enable row level security;
alter table public.households        enable row level security;
alter table public.household_members enable row level security;
alter table public.expenses          enable row level security;

grant select, update on public.profiles to authenticated;
grant select, update on public.households to authenticated;
grant select on public.household_members to authenticated;
grant select, insert, update, delete on public.expenses to authenticated;

drop policy if exists "profiles_select" on public.profiles;
create policy "profiles_select" on public.profiles for select to authenticated
  using (id = auth.uid() or public.shares_household(id));

drop policy if exists "profiles_update_self" on public.profiles;
create policy "profiles_update_self" on public.profiles for update to authenticated
  using (id = auth.uid()) with check (id = auth.uid());

drop policy if exists "households_select" on public.households;
create policy "households_select" on public.households for select to authenticated
  using (public.is_member(id));

drop policy if exists "households_update" on public.households;
create policy "households_update" on public.households for update to authenticated
  using (public.is_member(id)) with check (public.is_member(id));

drop policy if exists "members_select" on public.household_members;
create policy "members_select" on public.household_members for select to authenticated
  using (public.is_member(household_id));

drop policy if exists "expenses_select" on public.expenses;
create policy "expenses_select" on public.expenses for select to authenticated
  using (public.is_member(household_id));

drop policy if exists "expenses_insert" on public.expenses;
create policy "expenses_insert" on public.expenses for insert to authenticated
  with check (public.is_member(household_id));

drop policy if exists "expenses_update" on public.expenses;
create policy "expenses_update" on public.expenses for update to authenticated
  using (public.is_member(household_id)) with check (public.is_member(household_id));

drop policy if exists "expenses_delete" on public.expenses;
create policy "expenses_delete" on public.expenses for delete to authenticated
  using (public.is_member(household_id));

-- ---------------------------------------------------------------------
-- Realtime: l'app riceve subito le modifiche fatte dagli altri membri
-- ---------------------------------------------------------------------
alter table public.expenses replica identity full;
alter table public.household_members replica identity full;

do $$
begin
  if not exists (select 1 from pg_publication_tables
                 where pubname = 'supabase_realtime' and schemaname = 'public' and tablename = 'expenses') then
    alter publication supabase_realtime add table public.expenses;
  end if;
  if not exists (select 1 from pg_publication_tables
                 where pubname = 'supabase_realtime' and schemaname = 'public' and tablename = 'household_members') then
    alter publication supabase_realtime add table public.household_members;
  end if;
end;
$$;
