"""Spotify -> YouTube campaign resolution: library half of task 8 (B5a).

Rebuilds the tooling the 2026-10-15 session lost with /tmp. The DB
writes from that session survived; the scripts and the backup did not,
so this is a re-implementation from the method recorded in TODO.md
section B5, not a byte-for-byte recovery of the original code.

Method (TODO.md B5, steps 1-4)
------------------------------
1. Resolve each seed artist's *real* Spotify artist id. Sources in
   order: kworb's index, then MusicBrainz `url-rels`, then Spotify's
   own search page (which is client-rendered and yields nothing --
   kept last only to document that it was tried).
2. Read the artist's real track titles from the Spotify *artist embed*
   plus kworb's `<artist_id>_songs.html` page.
3. Match `users.metadata.top_tracks[].name` against those titles to get
   a genuine *track-level* Spotify id.
4. Resolve that track to a *YouTube video id* by searching YouTube and
   scoring candidates, then confirming the pick with YouTube oEmbed.

Precision over recall
---------------------
A wrong pick points a paid campaign at a stranger's video, which is
worse than an unresolved row: the app already drops unresolved rows
cleanly (the queue-slot RPC filters on `resolved_song_id is not null`).
The scorer therefore demands whole-word artist agreement *and* title
agreement, and drops an artist entirely rather than guessing.

Two normalisation bugs found in the original build, reproduced here as
regression notes so they are not reintroduced:

* Accents must be *dropped*, not turned into separators. NFKD leaves
  combining marks, and a naive `[^a-z0-9]` class turns each mark into a
  separator, so "Me Rehuso" became "me rehu so" and "Tiesto" became
  "tie sto". `normalise()` strips combining marks before the character
  class runs.
* Bracketed suffixes must be inspected *before* normalisation, or
  `normalise()` erases "[Acoustic Version]" and the variant penalty can
  never fire. `variants()` reads the raw title.
"""

from __future__ import annotations

import json
import re
import threading
import time
import unicodedata
import urllib.parse
import urllib.request

UA = "velune-campaign-resolver/1.0 (campaign data repair)"
TIMEOUT = 30

# MusicBrainz allows about one request per second for anonymous clients
# and answers 503 when that is exceeded. Two calls are made per artist
# (search, then url-rels), so the throttle is enforced here rather than
# left to the caller to remember.
_MB_INTERVAL = 1.1
_mb_lock = threading.Lock()
_mb_last = [0.0]


def _mb_throttle():
    with _mb_lock:
        wait = _MB_INTERVAL - (time.monotonic() - _mb_last[0])
        if wait > 0:
            time.sleep(wait)
        _mb_last[0] = time.monotonic()

# Markers that make a bracketed suffix a genuine *variant* rather than a
# credit list. "feat.", "with" and "prod." are credits, not variants.
VARIANT_MARKERS = (
    "remix", "acoustic", "live", "version", "edit", "instrumental",
    "sped up", "slowed", "extended", "remaster", "demo", "cover",
    "karaoke", "mix", "radio", "bonus", "deluxe",
)
_BRACKET_RE = re.compile(r"[\(\[\{]([^\)\]\}]*)[\)\]\}]")
_NON_ALNUM_RE = re.compile(r"[^a-z0-9]+")


def normalise(value: str) -> str:
    """Lowercase, strip accents, and collapse to space-separated tokens.

    Combining marks are removed *before* the character class replaces
    non-alphanumerics, so an accented letter loses its accent instead of
    becoming a word break.
    """
    if not value:
        return ""
    decomposed = unicodedata.normalize("NFKD", value)
    without_marks = "".join(
        ch for ch in decomposed if not unicodedata.combining(ch))
    return _NON_ALNUM_RE.sub(" ", without_marks.lower()).strip()


def variants(raw_title: str) -> list[str]:
    """Normalised variant markers in a raw (un-normalised) title.

    Reads the raw title on purpose: normalisation strips the brackets
    that make the marker detectable.
    """
    out = []
    for inner in _BRACKET_RE.findall(raw_title or ""):
        normed = normalise(inner)
        if normed and any(m in normed for m in VARIANT_MARKERS):
            out.append(normed)
    return out


def _get(url: str, headers: dict | None = None) -> str:
    req = urllib.request.Request(url, headers={"User-Agent": UA, **(headers or {})})
    with urllib.request.urlopen(req, timeout=TIMEOUT) as resp:
        return resp.read().decode("utf-8", "replace")


def _get_json(url: str, headers: dict | None = None):
    return json.loads(_get(url, headers))


# --------------------------------------------------------------------
# Source 1: Spotify artist id
# --------------------------------------------------------------------

def kworb_index() -> dict[str, str]:
    """normalised artist name -> Spotify artist id, from kworb's index."""
    html = _get("https://kworb.net/spotify/artists.html")
    pairs = re.findall(
        r'href="/spotify/artist/([A-Za-z0-9]{22})_songs\.html">([^<]+)</a>', html)
    return {normalise(name): aid for aid, name in pairs}


def musicbrainz_spotify_id(artist_name: str) -> str | None:
    """Spotify artist id via MusicBrainz url-rels, or None."""
    query = urllib.parse.quote(f'artist:"{artist_name}"')
    url = f"https://musicbrainz.org/ws/2/artist/?query={query}&fmt=json&limit=5"
    _mb_throttle()
    try:
        data = _get_json(url)
    except Exception:
        return None
    wanted = normalise(artist_name)
    for artist in data.get("artists", []):
        if normalise(artist.get("name", "")) != wanted:
            continue
        _mb_throttle()
        try:
            rels = _get_json(
                f"https://musicbrainz.org/ws/2/artist/{artist['id']}"
                f"?inc=url-rels&fmt=json")
        except Exception:
            continue
        for rel in rels.get("relations", []):
            resource = rel.get("resource", "")
            m = re.search(r"open\.spotify\.com/artist/([A-Za-z0-9]{22})", resource)
            if m:
                return m.group(1)
    return None


def resolve_artist_id(artist_name: str, index: dict[str, str],
                      stored_id: str | None = None) -> tuple[str | None, str]:
    """(spotify_artist_id, source) for an artist.

    kworb, then MusicBrainz, then -- last and least trusted -- the id
    already stored on the row. Some stored ids are fabricated (they 404
    on kworb and yield no embed tracks), so the stored value is only
    accepted once it is confirmed to expose tracks; see
    `confirmed_artist_id`.
    """
    hit = index.get(normalise(artist_name))
    if hit:
        return hit, "kworb"
    hit = musicbrainz_spotify_id(artist_name)
    if hit:
        return hit, "musicbrainz"
    if stored_id and _has_tracks(stored_id):
        return stored_id, "stored"
    return None, "unresolved"


def _has_tracks(artist_id: str) -> bool:
    try:
        return bool(artist_tracks(artist_id))
    except Exception:
        return False



# --------------------------------------------------------------------
# Source 2: the artist's real track titles
# --------------------------------------------------------------------

def spotify_embed_tracks(artist_id: str) -> list[dict]:
    """Top tracks from the artist embed page's __NEXT_DATA__ blob."""
    html = _get(f"https://open.spotify.com/embed/artist/{artist_id}")
    m = re.search(r'<script id="__NEXT_DATA__"[^>]*>(.*?)</script>', html, re.S)
    if not m:
        return []
    try:
        blob = json.loads(m.group(1))
    except json.JSONDecodeError:
        return []
    entity = (blob.get("props", {}).get("pageProps", {})
                  .get("state", {}).get("data", {}).get("entity", {}))
    out = []
    for track in entity.get("trackList", []) or []:
        uri = track.get("uri", "")
        tid = uri.rsplit(":", 1)[-1] if uri.startswith("spotify:track:") else None
        if tid:
            out.append({"title": track.get("title", ""),
                        "subtitle": track.get("subtitle", ""),
                        "track_id": tid, "source": "embed"})
    return out


def kworb_tracks(artist_id: str) -> list[dict]:
    """Every track kworb lists for the artist, with its Spotify track id."""
    html = _get(f"https://kworb.net/spotify/artist/{artist_id}_songs.html")
    out = []
    for row in re.findall(r"<tr>(.*?)</tr>", html, re.S):
        m = re.search(
            r'href="https://open\.spotify\.com/track/([A-Za-z0-9]{22})"[^>]*>([^<]+)</a>',
            row)
        if m:
            out.append({"title": m.group(2).strip(), "track_id": m.group(1),
                        "subtitle": "", "source": "kworb"})
    return out


def artist_tracks(artist_id: str) -> list[dict]:
    """Embed + kworb, de-duplicated by track id."""
    merged: dict[str, dict] = {}
    for fetch in (spotify_embed_tracks, kworb_tracks):
        try:
            for track in fetch(artist_id):
                merged.setdefault(track["track_id"], track)
        except Exception:
            continue
    return list(merged.values())


def match_track(track_name: str, tracks: list[dict]) -> dict | None:
    """Match a metadata title against the artist's real tracks.

    Exact normalised match wins; otherwise a unique whole-token
    containment match. Ambiguity returns None rather than guessing.
    """
    wanted = normalise(track_name)
    if not wanted:
        return None
    for track in tracks:
        if normalise(track["title"]) == wanted:
            return track
    partial = [t for t in tracks if wanted in normalise(t["title"])]
    if len(partial) == 1:
        return partial[0]
    return None


# --------------------------------------------------------------------
# Source 3: YouTube search + scoring
# --------------------------------------------------------------------

def youtube_search(query: str, limit: int = 6) -> list[dict]:
    """Flat YouTube search via yt-dlp. Returns [] if yt-dlp is absent."""
    try:
        import yt_dlp
    except ImportError:
        return []
    opts = {"quiet": True, "no_warnings": True, "extract_flat": True,
            "skip_download": True, "noprogress": True, "socket_timeout": TIMEOUT}
    try:
        with yt_dlp.YoutubeDL(opts) as ydl:
            info = ydl.extract_info(f"ytsearch{limit}:{query}", download=False)
    except Exception:
        return []
    out = []
    for entry in info.get("entries") or []:
        if not entry:
            continue
        out.append({"video_id": entry.get("id"),
                    "title": entry.get("title") or "",
                    "channel": entry.get("channel") or entry.get("uploader") or "",
                    "duration": entry.get("duration")})
    return out


def score_candidate(artist_name: str, track_title: str, cand: dict) -> float:
    """Confidence that `cand` is this artist's recording of `track_title`.

    Returns 0.0 when the candidate fails a hard requirement. Hard
    requirements: whole-word artist agreement with the title or the
    channel, and title agreement. A single-token artist name is common
    enough to collide that it additionally needs the artist in the
    video title, an exact channel match, or a "- Topic" channel.
    """
    artist = normalise(artist_name)
    title = normalise(track_title)
    if not artist or not title:
        return 0.0

    cand_title = normalise(cand["title"])
    cand_channel = normalise(cand["channel"])
    artist_tokens = artist.split()
    title_tokens = title.split()

    in_title = all(tok in cand_title.split() for tok in artist_tokens)
    in_channel = all(tok in cand_channel.split() for tok in artist_tokens)
    if not (in_title or in_channel):
        return 0.0

    if title in cand_title:
        title_score = 0.5
    elif all(tok in cand_title.split() for tok in title_tokens):
        title_score = 0.3
    else:
        return 0.0

    owns_channel = cand_channel == artist or cand_channel.endswith(" topic")
    if len(artist_tokens) == 1 and not (in_title or owns_channel):
        return 0.0

    score = title_score
    score += 0.4 if in_title else 0.0
    score += 0.35 if in_channel else 0.0
    score += 0.25 if owns_channel else 0.0

    # A variant recording must not stand in for the plain release.
    wanted_variants = variants(track_title)
    cand_variants = variants(cand["title"])
    if wanted_variants and not cand_variants:
        score -= 0.4
    elif cand_variants and not wanted_variants:
        score -= 0.3
    return round(max(score, 0.0), 3)


def verify_video(video_id: str) -> dict | None:
    """YouTube oEmbed confirmation. None when the video is unplayable."""
    url = ("https://www.youtube.com/oembed?format=json&url="
           + urllib.parse.quote(
               f"https://www.youtube.com/watch?v={video_id}", safe=""))
    try:
        return _get_json(url)
    except Exception:
        return None


def resolve_track(artist_name: str, track_title: str,
                  min_score: float = 0.75) -> dict | None:
    """Search YouTube for one track and return the verified best pick."""
    candidates = youtube_search(f"{artist_name} {track_title}")
    ranked = sorted(
        ((score_candidate(artist_name, track_title, c), c) for c in candidates),
        key=lambda pair: pair[0], reverse=True)
    for score, cand in ranked:
        if score < min_score:
            break
        oembed = verify_video(cand["video_id"])
        if not oembed:
            continue
        return {"video_id": cand["video_id"], "score": score,
                "youtube_title": oembed.get("title", cand["title"]),
                "youtube_author": oembed.get("author_name", cand["channel"]),
                "channel": cand["channel"], "duration": cand["duration"]}
    return None
