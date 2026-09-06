-- HANDOVER_CAMPAIGN.md §33 — queue-slot injection RPC, rewritten
-- against the v2 `campaigns` schema (campaign_schema.sql), and made
-- genre-optional to match CampaignRepository.fetchNextCampaignForQueueSlot's
-- new (genre: String? = null) signature.
--
-- Run this in the Supabase SQL editor AFTER campaign_schema.sql has
-- already been applied (it assumes public.campaigns already exists in
-- the v2 shape — source_url, resolved_song_id, active, start_date,
-- end_date). It does not touch the v1 track_campaigns table some of
-- this repo's older doc comments reference — see the note at the
-- bottom of this file if that table is what your project actually has
-- live instead of v2's campaigns table.
--
-- What this adds:
--   1. last_served_at — the fairness bookkeeping column v2's schema
--      never had (v1's track_campaigns apparently did, per
--      CampaignRepository.kt's own doc comments, but v2 dropped it
--      along with the rest of that table).
--   2. get_next_campaign_for_queue_slot(p_genre) — atomically picks
--      the least-recently-served eligible campaign and marks it
--      served, in one transaction, via SELECT ... FOR UPDATE SKIP
--      LOCKED (safe under concurrent calls from multiple listeners'
--      queues at once — two simultaneous callers can never be handed
--      the same row).
--
-- p_genre is accepted (so the app's existing RPC call, which always
-- sends a p_genre argument — null or a real value — keeps working
-- without an app-side signature change) but NOT filtered on: v2's
-- campaigns table has no genre column at all, so there is nothing to
-- filter by. If you need real genre-scoped campaigns again, that's a
-- schema change (a genre column + backfill), not just this function —
-- flagging rather than guessing at a shape you haven't asked for.

alter table public.campaigns
    add column if not exists last_served_at timestamptz;

-- Speeds up the "least-recently-served" ordering below; nulls (never
-- served) sort first so a brand-new campaign always gets a turn before
-- older ones cycle again.
create index if not exists campaigns_serving_idx
    on public.campaigns (active, last_served_at nulls first);

create or replace function public.get_next_campaign_for_queue_slot(p_genre text default null)
returns table (
    id uuid,
    source_url text,
    resolved_song_id text
)
language plpgsql
security definer
set search_path = public
as $$
declare
    v_id uuid;
begin
    -- Pick + lock one eligible row. SKIP LOCKED means a concurrent
    -- call already holding a lock on a candidate row is invisible to
    -- this one, so two simultaneous callers never pick the same
    -- campaign.
    select c.id
    into v_id
    from public.campaigns c
    where c.active = true
      and now() between c.start_date and c.end_date
    order by c.last_served_at asc nulls first, c.created_at asc
    for update skip locked
    limit 1;

    -- No eligible campaign right now — normal, expected, not an
    -- error. Empty result set, same as the app already treats a
    -- zero-length response.
    if v_id is null then
        return;
    end if;

    update public.campaigns
    set last_served_at = now()
    where campaigns.id = v_id;

    return query
    select c.id, c.source_url, c.resolved_song_id
    from public.campaigns c
    where c.id = v_id;
end;
$$;

grant execute on function public.get_next_campaign_for_queue_slot(text) to anon;

-- ---------------------------------------------------------------------
-- If your live project is actually still on the OLDER schema (a
-- track_campaigns table with its own genre/fairness columns, per the
-- v1-era comments in CampaignRepository.kt/CampaignInjectedQueue.kt)
-- rather than this repo's checked-in v2 campaign_schema.sql, the fix
-- is different: don't run the block above against that table's real
-- columns blind. Instead, run this first to see what you actually have:
--
--   select pg_get_functiondef(oid)
--   from pg_proc
--   where proname = 'get_next_campaign_for_queue_slot';
--
-- and share that output — the real fix there is almost certainly just
-- adding "(p_genre is null or genre = p_genre)" to whatever WHERE
-- clause that function already has, not a full rewrite. Don't guess
-- column names for a table this sandbox has never seen a definition
-- for.
