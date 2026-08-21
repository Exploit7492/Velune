-- Campaign / promoted-content ad slot schema (v2).
--
-- Design principles, all real, none simulated:
--   1. You insert a URL, a start date, and an end date. Everything else
--      the app needs to actually play the content (title, artist,
--      thumbnail) is resolved live from YouTube at fetch time, not typed
--      in here — see CampaignUrlResolver.kt. That's the "app resolves the
--      media automatically" part.
--   2. Visibility is a date window, enforced twice: once in the RLS
--      policy itself (so an expired/future campaign can never leak to any
--      client no matter what the app's query looks like), and again in
--      the app's own query, as defense in depth.
--   3. `is_live` is a human-set truthful flag for genuine YouTube
--      livestreams — Velune has no Radio/Podcast/Show content types, so
--      this is scoped to the one kind of content this app can actually
--      play that is ever really "live": see CampaignCard.kt's doc.
--   4. play_count only ever moves through the atomic RPC at the bottom of
--      this file — same reasoning as v1, see CampaignRepository.kt.

drop table if exists public.campaigns cascade;

create table public.campaigns (
    id uuid primary key default gen_random_uuid(),

    -- The one thing a campaign creator actually types in. A YouTube
    -- watch/share URL (youtube.com, youtu.be, or music.youtube.com) —
    -- CampaignUrlResolver extracts the video id from this and resolves
    -- real metadata from it at fetch time.
    source_url text not null,

    -- Cached extraction of the video id from source_url, so the app
    -- doesn't have to re-parse the URL on every read. Still fully
    -- re-derivable from source_url alone if this ever needs backfilling.
    resolved_song_id text,

    -- Truthful, human-set: "this points at a genuine YouTube livestream."
    -- Only ever rendered as the red LIVE badge when true — never inferred,
    -- never defaulted to true. See CampaignCard.kt for the honesty
    -- reasoning and why this is scoped to video content only.
    is_live boolean not null default false,

    -- Moderation flag: a human reviewed and approved this campaign for
    -- the promoted slot. Not a claim about the artist's identity or
    -- popularity — see CampaignCard.kt.
    certified boolean not null default false,

    -- Manual kill switch, independent of the date window — lets someone
    -- pause a campaign immediately without waiting for/editing dates.
    active boolean not null default true,

    -- The visibility window. A campaign is only ever eligible to show if
    -- active = true AND now() is inside [start_date, end_date].
    start_date timestamptz not null,
    end_date timestamptz not null,
    constraint campaigns_valid_window check (end_date > start_date),

    cta_label text not null default 'Play',

    -- Real, atomically-incremented count — see increment_campaign_play().
    play_count bigint not null default 0,

    created_at timestamptz not null default now()
);

create index campaigns_visibility_idx
    on public.campaigns (active, start_date, end_date);

alter table public.campaigns enable row level security;

-- Layer 1 of the date-window enforcement: even if the app's own query
-- forgot to filter by date, Postgres itself will never return an
-- expired/future/inactive row to an anon client.
create policy "Public can read live campaigns only"
    on public.campaigns
    for select
    using (
        active = true
        and now() between start_date and end_date
    );

-- Atomic increment, callable by anon via RPC only (no direct table grants
-- at all — see the v1 schema's comment for why SECURITY DEFINER is what
-- makes that safe). Layer 2 of the date-window enforcement: a play can't
-- be recorded against a campaign that has since expired or been paused,
-- even by a client with a stale cached campaign id.
create or replace function public.increment_campaign_play(campaign_id_input uuid)
returns void
language plpgsql
security definer
set search_path = public
as $$
begin
    update public.campaigns
    set play_count = play_count + 1
    where id = campaign_id_input
      and active = true
      and now() between start_date and end_date;
end;
$$;

grant execute on function public.increment_campaign_play(uuid) to anon;

-- Admin policies for CampaignAdminScreen/CampaignAdminRepository — a
-- signed-in Supabase Auth session (`authenticated` role) can see and
-- manage every row, not just currently-live ones, unlike the anon policy
-- above. This is what makes it safe to hold real write access from
-- inside the app at all: see CampaignAdminRepository.kt's doc for why
-- this is a signed-in session rather than the service-role key.
--
-- CAVEAT, stated plainly: `auth.role() = 'authenticated'` grants full
-- campaign management to *any* signed-in user, not one specific admin
-- account. That's the right tradeoff for a single-admin personal project
-- — anyone who can sign in is, by construction, the one person with
-- credentials to a project only you control. It stops being the right
-- policy the moment a second real account exists that shouldn't have
-- this access; at that point, replace `auth.role() = 'authenticated'`
-- below with a check against a specific admin user id (`auth.uid() =
-- '<your-user-id>'::uuid`) or a custom role claim instead of widening
-- trust to "anyone who can sign in."
create policy "Authenticated admin can view all campaigns"
    on public.campaigns
    for select
    to authenticated
    using (true);

create policy "Authenticated admin can create campaigns"
    on public.campaigns
    for insert
    to authenticated
    with check (true);

create policy "Authenticated admin can update campaigns"
    on public.campaigns
    for update
    to authenticated
    using (true)
    with check (true);

create policy "Authenticated admin can delete campaigns"
    on public.campaigns
    for delete
    to authenticated
    using (true);

-- Create your admin account once, from the Supabase dashboard
-- (Authentication → Users → Add user) or via the CLI — not from this
-- file. There's deliberately no SQL here that creates a login; that's a
-- credential, not schema.

-- Example row for testing — delete or edit once you have a real campaign.
-- insert into public.campaigns (source_url, start_date, end_date, certified, is_live)
-- values (
--     'https://music.youtube.com/watch?v=your-video-id',
--     now(),
--     now() + interval '14 days',
--     false,
--     false
-- );
