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
--
-- HANDOVER_CAMPAIGN.md §36 — deployed §35's version, ran the smoke
-- test (select * from get_next_campaign_for_queue_slot(null)), and it
-- picked a real row -- but that row's source_url was a Spotify link
-- with resolved_song_id null, which the app can never turn into a
-- playable YouTube MediaItem. Added a YouTube-only condition to the
-- eligibility filter below (see that block's own comment). This
-- `create or replace function` is safe to re-run over §35's version --
-- the alter table/create index above are already idempotent
-- (`if not exists`), so running this whole file again on top of §35's
-- already-applied version changes only the function body, nothing
-- else.

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
    --
    -- HANDOVER_CAMPAIGN.md §36 — also YouTube-playable only. This app
    -- can only ever turn a YouTube video id into a MediaItem
    -- (CampaignUrlResolver.extractVideoId only recognizes youtube.com/
    -- youtu.be/music.youtube.com share-link shapes; there is no
    -- Spotify/other-platform playback path anywhere in this app). A
    -- campaign with a non-YouTube source_url and no resolved_song_id
    -- would be picked, marked served, then silently dropped by
    -- CampaignRepository.kt's own "could not extract a video id"
    -- fallback — consuming its fairness turn for a slot that was
    -- always going to end up empty. Filtering it out here, before the
    -- pick, means a slot that returns a row is always actually
    -- playable, and a campaign that can never play doesn't waste a
    -- turn other campaigns could have used.
    select tc.id
    into v_id
    from public.track_campaigns tc
    where tc.is_active = true
      and tc.is_paused = false
      and tc.completed_at is null
      and (tc.scheduled_for is null or tc.scheduled_for <= now())
      and (p_genre is null or p_genre = any(tc.target_genres))
      and (
        tc.resolved_song_id is not null
        or tc.source_url ilike '%youtube.com/watch%'
        or tc.source_url ilike '%music.youtube.com/watch%'
        or tc.source_url ilike '%youtu.be/%'
      )
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
