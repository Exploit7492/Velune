-- HANDOVER_CAMPAIGN.md §35 — corrected against the ACTUAL live schema
-- (public.track_campaigns), confirmed via information_schema.columns,
-- not this repo's checked-in v2 campaign_schema.sql (which describes
-- a different, apparently never-deployed "campaigns" table).
--
-- Also confirmed via pg_get_functiondef returning zero rows:
-- get_next_campaign_for_queue_slot does not exist on this project at
-- all yet. This is not a null-genre patch to an existing function --
-- it's the first time this function is being created. Every prior
-- call to it from the app has been silently failing (caught by
-- CampaignRepository.kt's own try/catch, logged, treated as "no
-- eligible campaign") for every queue, genre-tile ones included --
-- so campaign injection has likely never actually worked end-to-end,
-- not just for non-genre queues.

alter table public.track_campaigns
    add column if not exists last_served_at timestamptz;

create index if not exists track_campaigns_serving_idx
    on public.track_campaigns (is_active, last_served_at nulls first);

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
    -- Eligible: is_active, not paused, not completed, not scheduled
    -- for the future -- and, if a genre was passed, target_genres
    -- must contain it. p_genre = null matches every campaign
    -- regardless of genre (the "inject on every queue" behavior).
    select tc.id
    into v_id
    from public.track_campaigns tc
    where tc.is_active = true
      and tc.is_paused = false
      and tc.completed_at is null
      and (tc.scheduled_for is null or tc.scheduled_for <= now())
      and (p_genre is null or p_genre = any(tc.target_genres))
    order by tc.last_served_at asc nulls first, tc.created_at asc
    for update skip locked
    limit 1;

    if v_id is null then
        return;
    end if;

    update public.track_campaigns
    set last_served_at = now()
    where track_campaigns.id = v_id;

    return query
    select tc.id, tc.source_url, tc.resolved_song_id
    from public.track_campaigns tc
    where tc.id = v_id;
end;
$$;

grant execute on function public.get_next_campaign_for_queue_slot(text) to anon;
