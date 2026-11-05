-- AccessPath backend schema for Supabase.
-- Run once in the Supabase dashboard: SQL Editor -> New query -> paste -> Run.
-- Safe to re-run: every object is created only if missing or replaced.
--
-- Roles: student (default), reviewer, admin. Make yourself admin after your first sign-in:
--   update public.profiles set role = 'admin' where email = 'you@example.com';

create schema if not exists private;

-- ---------------------------------------------------------------------------
-- People and roles
-- ---------------------------------------------------------------------------
create table if not exists public.profiles (
  id uuid primary key references auth.users (id) on delete cascade,
  email text,
  display_name text,
  role text not null default 'student' check (role in ('student', 'reviewer', 'admin')),
  created_at timestamptz not null default now()
);
alter table public.profiles enable row level security;

-- Role of the signed-in user ('anon' when signed out). Security definer so policies can read
-- profiles without recursing into profiles' own RLS. Lives in a schema the API does not expose.
create or replace function private.user_role()
returns text
language sql
stable
security definer
set search_path = ''
as $$
  select coalesce((select p.role from public.profiles p where p.id = (select auth.uid())), 'anon');
$$;
grant usage on schema private to authenticated, anon;
revoke execute on function private.user_role() from public;
grant execute on function private.user_role() to authenticated, anon;

create or replace function private.is_reviewer()
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
  select private.user_role() in ('reviewer', 'admin');
$$;
revoke execute on function private.is_reviewer() from public;
grant execute on function private.is_reviewer() to authenticated, anon;

-- Create a profile for every new account (Google or email).
create or replace function private.handle_new_user()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
  insert into public.profiles (id, email, display_name)
  values (
    new.id,
    new.email,
    coalesce(new.raw_user_meta_data ->> 'full_name', new.raw_user_meta_data ->> 'name', split_part(new.email, '@', 1))
  )
  on conflict (id) do nothing;
  return new;
end;
$$;
drop trigger if exists on_auth_user_created on auth.users;
create trigger on_auth_user_created
  after insert on auth.users
  for each row execute function private.handle_new_user();

-- Nobody but an admin may change a role (including their own).
create or replace function private.protect_role()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
  if new.role is distinct from old.role and private.user_role() <> 'admin' then
    raise exception 'Only an admin can change roles';
  end if;
  if new.id is distinct from old.id or new.email is distinct from old.email then
    raise exception 'Profile id and email cannot be changed';
  end if;
  return new;
end;
$$;
drop trigger if exists protect_role on public.profiles;
create trigger protect_role before update on public.profiles
  for each row execute function private.protect_role();

drop policy if exists profiles_select on public.profiles;
create policy profiles_select on public.profiles for select to authenticated
  using (id = (select auth.uid()) or (select private.user_role()) = 'admin');
drop policy if exists profiles_update on public.profiles;
create policy profiles_update on public.profiles for update to authenticated
  using (id = (select auth.uid()) or (select private.user_role()) = 'admin')
  with check (id = (select auth.uid()) or (select private.user_role()) = 'admin');

-- ---------------------------------------------------------------------------
-- Campus map (published by admins, read by everyone)
-- ---------------------------------------------------------------------------
create table if not exists public.campus_maps (
  id text primary key,
  version text not null,
  payload jsonb not null,
  updated_at timestamptz not null default now(),
  updated_by uuid references auth.users (id) on delete set null default auth.uid()
);
alter table public.campus_maps enable row level security;
drop policy if exists maps_read on public.campus_maps;
create policy maps_read on public.campus_maps for select to anon, authenticated using (true);
drop policy if exists maps_write on public.campus_maps;
create policy maps_write on public.campus_maps for all to authenticated
  using ((select private.user_role()) = 'admin')
  with check ((select private.user_role()) = 'admin');

-- ---------------------------------------------------------------------------
-- Lift / facility status (reviewers write, everyone reads)
-- ---------------------------------------------------------------------------
create table if not exists public.facility_status (
  map_id text not null,
  facility_id text not null,
  availability text not null check (availability in ('AVAILABLE', 'UNAVAILABLE', 'UNKNOWN')),
  reason text not null check (char_length(btrim(reason)) between 5 and 1000),
  updated_at timestamptz not null default now(),
  updated_by uuid references auth.users (id) on delete set null default auth.uid(),
  primary key (map_id, facility_id)
);
alter table public.facility_status enable row level security;
drop policy if exists facility_read on public.facility_status;
create policy facility_read on public.facility_status for select to anon, authenticated using (true);
drop policy if exists facility_write on public.facility_status;
create policy facility_write on public.facility_status for all to authenticated
  using ((select private.is_reviewer())) with check ((select private.is_reviewer()));

-- ---------------------------------------------------------------------------
-- Temporary path closures (reviewers write, everyone reads)
-- ---------------------------------------------------------------------------
create table if not exists public.closures (
  map_id text not null,
  edge_id text not null,
  closed boolean not null,
  reason text not null check (char_length(btrim(reason)) between 5 and 1000),
  updated_at timestamptz not null default now(),
  updated_by uuid references auth.users (id) on delete set null default auth.uid(),
  primary key (map_id, edge_id)
);
alter table public.closures enable row level security;
drop policy if exists closures_read on public.closures;
create policy closures_read on public.closures for select to anon, authenticated using (true);
drop policy if exists closures_write on public.closures;
create policy closures_write on public.closures for all to authenticated
  using ((select private.is_reviewer())) with check ((select private.is_reviewer()));

-- ---------------------------------------------------------------------------
-- Barrier reports (signed-in users report, reviewers decide, everyone reads)
-- ---------------------------------------------------------------------------
create table if not exists public.reports (
  id uuid primary key,
  map_id text not null,
  edge_id text not null,
  category text not null check (category in ('BLOCKED_PATH', 'LIFT_NOT_WORKING', 'DAMAGED_RAMP', 'TOO_NARROW', 'STEP_OR_THRESHOLD', 'OTHER')),
  description text not null check (char_length(btrim(description)) between 5 and 1000),
  status text not null default 'PENDING' check (status in ('PENDING', 'VERIFIED', 'REJECTED', 'RESOLVED')),
  reason text not null default '',
  created_at timestamptz not null default now(),
  created_by uuid not null default auth.uid() references auth.users (id) on delete cascade,
  reviewed_at timestamptz,
  reviewed_by uuid references auth.users (id) on delete set null
);
create index if not exists reports_map_id_idx on public.reports (map_id);
create index if not exists reports_created_by_idx on public.reports (created_by);
alter table public.reports enable row level security;
drop policy if exists reports_read on public.reports;
create policy reports_read on public.reports for select to anon, authenticated using (true);
drop policy if exists reports_insert on public.reports;
create policy reports_insert on public.reports for insert to authenticated
  with check (created_by = (select auth.uid()) and status = 'PENDING');
drop policy if exists reports_review on public.reports;
create policy reports_review on public.reports for update to authenticated
  using ((select private.is_reviewer())) with check ((select private.is_reviewer()));

-- Same rules as the app: PENDING -> VERIFIED/REJECTED, VERIFIED -> RESOLVED, with a 5-1000 character
-- reason. Only status and reason may change; who and when are filled in by the server.
create or replace function private.review_report()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
  if (new.map_id, new.edge_id, new.category, new.description, new.created_at, new.created_by)
     is distinct from (old.map_id, old.edge_id, old.category, old.description, old.created_at, old.created_by) then
    raise exception 'Only status and reason can change on a report';
  end if;
  if new.status is distinct from old.status then
    if not ((old.status = 'PENDING' and new.status in ('VERIFIED', 'REJECTED'))
         or (old.status = 'VERIFIED' and new.status = 'RESOLVED')) then
      raise exception 'Invalid report transition % -> %', old.status, new.status;
    end if;
    if char_length(btrim(new.reason)) not between 5 and 1000 then
      raise exception 'A decision needs a reason of 5 to 1000 characters';
    end if;
    new.reviewed_at := now();
    new.reviewed_by := (select auth.uid());
  end if;
  return new;
end;
$$;
drop trigger if exists review_report on public.reports;
create trigger review_report before update on public.reports
  for each row execute function private.review_report();

-- ---------------------------------------------------------------------------
-- Activity log, written by triggers (reviewers read)
-- ---------------------------------------------------------------------------
create table if not exists public.audit_events (
  id uuid primary key default gen_random_uuid(),
  map_id text not null,
  action text not null,
  target text not null,
  before_value text not null default '',
  after_value text not null default '',
  reason text not null default '',
  actor uuid default auth.uid(),
  created_at timestamptz not null default now()
);
create index if not exists audit_map_time_idx on public.audit_events (map_id, created_at desc);
alter table public.audit_events enable row level security;
drop policy if exists audit_read on public.audit_events;
create policy audit_read on public.audit_events for select to authenticated using ((select private.is_reviewer()));

create or replace function private.log_change()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
  if tg_table_name = 'reports' and new.status is distinct from old.status then
    insert into public.audit_events (map_id, action, target, before_value, after_value, reason)
    values (new.map_id, 'Review report', new.id::text, old.status, new.status, new.reason);
  elsif tg_table_name = 'facility_status' then
    insert into public.audit_events (map_id, action, target, before_value, after_value, reason)
    values (new.map_id, 'Update facility', new.facility_id,
            coalesce(case when tg_op = 'UPDATE' then old.availability end, ''), new.availability, new.reason);
  elsif tg_table_name = 'closures' then
    insert into public.audit_events (map_id, action, target, before_value, after_value, reason)
    values (new.map_id, case when new.closed then 'Close path' else 'Reopen path' end, new.edge_id,
            case when tg_op = 'UPDATE' and old.closed then 'Closed' else 'Open' end,
            case when new.closed then 'Closed' else 'Open' end, new.reason);
  elsif tg_table_name = 'campus_maps' then
    insert into public.audit_events (map_id, action, target, before_value, after_value, reason)
    values (new.id, 'Publish map', new.id, coalesce(case when tg_op = 'UPDATE' then old.version end, ''), new.version, 'Map published');
  end if;
  return new;
end;
$$;
drop trigger if exists log_reports on public.reports;
create trigger log_reports after update on public.reports for each row execute function private.log_change();
drop trigger if exists log_facility on public.facility_status;
create trigger log_facility after insert or update on public.facility_status for each row execute function private.log_change();
drop trigger if exists log_closures on public.closures;
create trigger log_closures after insert or update on public.closures for each row execute function private.log_change();
drop trigger if exists log_maps on public.campus_maps;
create trigger log_maps after insert or update on public.campus_maps for each row execute function private.log_change();

-- ---------------------------------------------------------------------------
-- Review a report (called by the app). Runs as the caller, so RLS and the trigger above apply.
-- ---------------------------------------------------------------------------
-- Returns the number of reports changed: 0 means not found or not allowed.
drop function if exists public.review_report(uuid, text, text);
create or replace function public.review_report(report_id uuid, new_status text, decision_reason text)
returns integer
language sql
security invoker
set search_path = ''
as $$
  with changed as (
    update public.reports set status = new_status, reason = btrim(decision_reason) where id = report_id returning 1
  )
  select count(*)::integer from changed;
$$;
revoke execute on function public.review_report(uuid, text, text) from public, anon;
grant execute on function public.review_report(uuid, text, text) to authenticated;

-- ---------------------------------------------------------------------------------------------
-- Camera surveys: draft areas recorded with the phone camera, shared with every phone.
-- Anyone can read; a signed-in person can add their own; only reviewers can delete.
-- ---------------------------------------------------------------------------------------------
create table if not exists public.surveys (
  id text primary key,
  map_id text not null,
  name text not null default '',
  payload jsonb not null,
  created_by uuid default auth.uid() references auth.users (id) on delete set null,
  created_at timestamptz not null default now()
);
create index if not exists surveys_map_id_idx on public.surveys (map_id, created_at);
alter table public.surveys enable row level security;

drop policy if exists "surveys readable" on public.surveys;
create policy "surveys readable" on public.surveys for select using (true);

drop policy if exists "surveys insert own" on public.surveys;
create policy "surveys insert own" on public.surveys for insert to authenticated
  with check (created_by = (select auth.uid()) and octet_length(payload::text) < 2000000);

drop policy if exists "surveys reviewers delete" on public.surveys;
create policy "surveys reviewers delete" on public.surveys for delete to authenticated
  using ((select private.is_reviewer()));

-- ---------------------------------------------------------------------------------------------
-- Outdoor barriers: blockages outside buildings (digging, flooding, no ramp...) reported on the
-- spot. Anyone can read; a signed-in person adds their own; the reporter or a reviewer removes one.
-- The app hides reports older than 14 days.
-- ---------------------------------------------------------------------------------------------
create table if not exists public.outdoor_barriers (
  id uuid primary key,
  lat double precision not null check (lat between -90 and 90),
  lng double precision not null check (lng between -180 and 180),
  kind text not null check (kind in ('CONSTRUCTION', 'ROAD_BLOCKED', 'FLOODING', 'NO_RAMP', 'BROKEN_FOOTPATH', 'OTHER')),
  note text not null default '' check (char_length(note) <= 300),
  created_by uuid not null default auth.uid() references auth.users (id) on delete cascade,
  created_at timestamptz not null default now()
);
create index if not exists outdoor_barriers_lat_lng_idx on public.outdoor_barriers (lat, lng);
create index if not exists outdoor_barriers_created_by_idx on public.outdoor_barriers (created_by);
alter table public.outdoor_barriers enable row level security;

drop policy if exists outdoor_barriers_read on public.outdoor_barriers;
create policy outdoor_barriers_read on public.outdoor_barriers for select to anon, authenticated using (true);
drop policy if exists outdoor_barriers_insert on public.outdoor_barriers;
create policy outdoor_barriers_insert on public.outdoor_barriers for insert to authenticated
  with check (created_by = (select auth.uid()));
drop policy if exists outdoor_barriers_delete on public.outdoor_barriers;
create policy outdoor_barriers_delete on public.outdoor_barriers for delete to authenticated
  using (created_by = (select auth.uid()) or (select private.is_reviewer()));
-- ---------------------------------------------------------------------------------------------
-- Mapped areas need an admin's approval. PENDING and REJECTED areas are visible only to the
-- person who mapped them and to reviewers; APPROVED areas are visible to everyone.
-- ---------------------------------------------------------------------------------------------
alter table public.surveys add column if not exists status text not null default 'PENDING';
alter table public.surveys add column if not exists reason text not null default '';
alter table public.surveys add column if not exists reviewed_by uuid references auth.users (id) on delete set null;
alter table public.surveys add column if not exists reviewed_at timestamptz;
do $$ begin
  if not exists (select 1 from pg_constraint where conname = 'surveys_status_check') then
    alter table public.surveys add constraint surveys_status_check check (status in ('PENDING', 'APPROVED', 'REJECTED'));
  end if;
end $$;
create index if not exists surveys_status_idx on public.surveys (map_id, status);

drop policy if exists "surveys readable" on public.surveys;
create policy "surveys readable" on public.surveys for select
  using (status = 'APPROVED' or created_by = (select auth.uid()) or (select private.is_reviewer()));

drop policy if exists "surveys insert own" on public.surveys;
create policy "surveys insert own" on public.surveys for insert to authenticated
  with check (created_by = (select auth.uid()) and status = 'PENDING' and octet_length(payload::text) < 2000000);

-- Only reviewers decide, through this function, which also records who decided and when.
create or replace function public.review_survey(survey_id text, decision text, why text default '')
returns void
language plpgsql
security definer
set search_path = ''
as $$
begin
  if not (select private.is_reviewer()) then
    raise exception 'Only reviewers can approve or reject mapped areas';
  end if;
  if decision not in ('APPROVED', 'REJECTED') then
    raise exception 'Decision must be APPROVED or REJECTED';
  end if;
  if decision = 'REJECTED' and char_length(btrim(coalesce(why, ''))) not between 5 and 1000 then
    raise exception 'A rejection needs a reason of 5 to 1000 characters';
  end if;
  update public.surveys
     set status = decision, reason = btrim(coalesce(why, '')), reviewed_by = (select auth.uid()), reviewed_at = now()
   where id = survey_id;
end;
$$;
revoke execute on function public.review_survey(text, text, text) from public, anon;
grant execute on function public.review_survey(text, text, text) to authenticated;
