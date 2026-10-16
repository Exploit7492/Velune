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


# MusicBrainz answers 503 (and occasionally 429) under load, even at one
# request per second. Treating that as "artist not found" made the result
# depend on luck: the same artist resolved on one run and not the next.
# Retry the transient codes a few times with backoff before giving up.
_MB_RETRY_CODES = (429, 500, 502, 503, 504)
_MB_ATTEMPTS = 4


def _mb_get_json(url: str):
    """GET + parse JSON from MusicBrainz, retrying transient failures.

    Returns None only when every attempt fails or the body is not JSON;
    a definitive 404 returns None immediately.
    """
    last = None
    for attempt in range(_MB_ATTEMPTS):
        _mb_throttle()
        try:
            return _get_json(url)
        except urllib.error.HTTPError as exc:
            last = exc
            if exc.code not in _MB_RETRY_CODES:
                return None
        except Exception as exc:
            last = exc
        if attempt < _MB_ATTEMPTS - 1:
            time.sleep(_MB_INTERVAL * (2 ** attempt))
    if last is not None:
        print(f"  musicbrainz unavailable after {_MB_ATTEMPTS} attempts: "
              f"{type(last).__name__} {getattr(last, 'code', '')}")
    return None

# Markers that make a bracketed suffix a genuine *variant* rather than a
# credit list. "feat.", "with" and "prod." are credits, not variants.
VARIANT_MARKERS = (
    "remix", "acoustic", "live", "version", "edit", "instrumental",
    "sped up", "slowed", "extended", "remaster", "demo", "cover",
    "karaoke", "mix", "radio", "bonus", "deluxe",
)
_BRACKET_RE = re.compile(r"[\(\[\{]([^\)\]\}]*)[\)\]\}]")
_NON_ALNUM_RE = re.compile(r"[^a-z0-9]+")
_DIGITS_RE = re.compile(r"^[0-9]+$")

# Tokens that cannot identify a recording on their own. Spotify's titles
# carry volume and credit markers that YouTube titles routinely drop
# ("Vol. 52/" -> "Music Sessions #52"), so a *missing* token is tolerated
# only when it is one of these or a bare single digit (emoticons and
# punctuation produce those: "<3" -> "3", "Pt. 1" -> "1"). A missing
# meaningful word still fails, which is what keeps precision.
_ABBREV_TOKENS = frozenset({
    "vol", "feat", "ft", "vs", "pt", "ep", "lp", "ost", "rmx", "prod",
})


def _is_low_info(token: str) -> bool:
    """True when a title token carries no identifying information."""
    if token in _ABBREV_TOKENS:
        return True
    return len(token) == 1 and bool(_DIGITS_RE.match(token))


# NFKD decomposes most accented letters (é -> e + combining mark), but a
# handful of Latin letters are *single* code points with no decomposition:
# Polish ł, Danish/Norwegian ø, Croatian đ, Icelandic ð/æ, German ß.
# NFKD leaves them intact, so the character class in `normalise` would
# delete them outright -- "Podsiadło" became "podsiad o" and could never
# equal the real channel name, which is why that artist stayed unresolved.
# Lowercase keys only: this is applied after `.lower()`.
_TRANSLITERATE = str.maketrans({
    "ł": "l", "ø": "o", "đ": "d", "ð": "d", "þ": "th",
    "æ": "ae", "œ": "oe", "ß": "ss", "ı": "i", "ŋ": "n", "ħ": "h",
})


def normalise(value: str) -> str:
    """Lowercase, strip accents, and collapse to space-separated tokens.

    Combining marks are removed *before* the character class replaces
    non-alphanumerics, so an accented letter loses its accent instead of
    becoming a word break.

    Two cases NFKD alone does not cover, each found via an unresolved
    artist rather than by inspection -- see `_TRANSLITERATE` and the "&"
    note below.
    """
    if not value:
        return ""
    decomposed = unicodedata.normalize("NFKD", value)
    without_marks = "".join(
        ch for ch in decomposed if not unicodedata.combining(ch))
    # "&" -> "and" so "Marcus & Martinus" (the real channel) and "Marcus
    # and Martinus" (the seed's spelling) collapse to the same string.
    # Without this the two forms never compare equal and the artist is
    # rejected as a namesake.
    collapsed = without_marks.lower().replace("&", " and ")
    return _NON_ALNUM_RE.sub(" ", collapsed.translate(_TRANSLITERATE)).strip()


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


def _mb_spotify_from_artist(artist_id: str) -> str | None:
    """The Spotify artist URL on `artist_id`'s url-rels, if any."""
    rels = _mb_get_json(
        f"https://musicbrainz.org/ws/2/artist/{artist_id}"
        f"?inc=url-rels&fmt=json")
    if rels is None:
        return None
    for rel in rels.get("relations", []):
        # MusicBrainz nests the target as {"url": {"id", "resource"}}.
        # This read the flat rel["resource"], which is absent in the
        # current API, so every relation yielded None and the whole
        # MusicBrainz fallback was silently dead -- the reason nine
        # artists reported "no spotify artist id". Accept the old flat
        # shape too, so a payload change either way keeps working.
        resource = ((rel.get("url") or {}).get("resource")
                    or rel.get("resource") or "")
        m = re.search(r"open\.spotify\.com/artist/([A-Za-z0-9]{22})", resource)
        if m:
            return m.group(1)
    return None


def _mb_spotify_by_alias(artist_name: str) -> str | None:
    """Spotify id via an artist *alias*, requiring an exact alias match.

    MusicBrainz stores many artists only under their native script or
    diacritic spelling -- Son Tung MTP as "Sơn Tùng M-TP", Lee Young Ji
    as "이영지", Ivi Adamou as "Ήβη Αδάμου" -- so the exact-name search
    above finds nothing for the ASCII name the seed carries. The alias
    index does know them.

    Accepting an alias hit on score alone would be unsafe (searching
    "Ivi Adamou" also returns "Kat Adamou"). So the candidate is only
    accepted when the exact normalised name appears among its aliases.
    That is the same identity bar as the name path, just checked against
    a different field.
    """
    query = urllib.parse.quote(f'alias:"{artist_name}"')
    data = _mb_get_json(
        f"https://musicbrainz.org/ws/2/artist/?query={query}&fmt=json&limit=5")
    if data is None:
        return None
    wanted = normalise(artist_name)
    for artist in data.get("artists", []):
        full = _mb_get_json(
            f"https://musicbrainz.org/ws/2/artist/{artist['id']}"
            f"?inc=aliases+url-rels&fmt=json")
        if full is None:
            continue
        if not any(normalise(a.get("name", "")) == wanted
                   for a in full.get("aliases", [])):
            continue
        found = _mb_spotify_from_artist_id_payload(full)
        if found:
            return found
    return None


def _mb_spotify_from_artist_id_payload(rels: dict) -> str | None:
    """Spotify artist id from an already-fetched artist payload."""
    for rel in rels.get("relations", []):
        resource = ((rel.get("url") or {}).get("resource")
                    or rel.get("resource") or "")
        m = re.search(r"open\.spotify\.com/artist/([A-Za-z0-9]{22})", resource)
        if m:
            return m.group(1)
    return None


def musicbrainz_spotify_id(artist_name: str) -> str | None:
    """Spotify artist id via MusicBrainz, or None.

    Name match first; then an exact-alias match for artists MusicBrainz
    holds only under their native spelling. See `_mb_spotify_by_alias`.
    """
    query = urllib.parse.quote(f'artist:"{artist_name}"')
    url = f"https://musicbrainz.org/ws/2/artist/?query={query}&fmt=json&limit=5"
    data = _mb_get_json(url)
    if data is not None:
        wanted = normalise(artist_name)
        for artist in data.get("artists", []):
            if normalise(artist.get("name", "")) != wanted:
                continue
            found = _mb_spotify_from_artist(artist["id"])
            if found:
                return found
    return _mb_spotify_by_alias(artist_name)


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

    Order-insensitive on purpose. The seed metadata reorders words
    relative to the real release title -- "BZRP Music Sessions Vol. 53
    (Shakira)" against the actual "Shakira: Bzrp Music Sessions, Vol.
    53/" -- so a substring test alone missed matches that are obviously
    the same recording. Comparison is therefore on token *sets*, with
    low-information tokens ignored so "Vol." does not decide a match.

    Ambiguity still returns None rather than guessing: a title that
    matches two different real tracks equally well is not resolved.
    """
    wanted = set(t for t in normalise(track_name).split() if not _is_low_info(t))
    if not wanted:
        return None

    scored: list[tuple[float, dict]] = []
    for track in tracks:
        have = set(t for t in normalise(track["title"]).split()
                   if not _is_low_info(t))
        if not have:
            continue
        if have == wanted:
            scored.append((1.0, track))
            continue
        # Every wanted token present is the strong signal; the reverse
        # containment (candidate has extra words, e.g. a credit list) is
        # weaker and scored below it.
        if wanted <= have:
            scored.append((0.9, track))
        elif have <= wanted:
            scored.append((0.8, track))

    if not scored:
        return None
    best = max(s for s, _ in scored)
    winners = [t for s, t in scored if s == best]
    if len(winners) == 1:
        return winners[0]

    # Several real tracks match equally well. That is usually one song
    # in multiple versions ("Why This Kolaveri Di? - The Soup of Love"
    # vs "Why This Kolaveri Di? - Trending Version"), so refusing
    # outright loses a genuinely resolvable artist. Prefer the release
    # that is closest to the plain title -- fewest extra tokens, then no
    # variant marker -- which picks the canonical version. A tie that
    # survives both tiebreaks is still refused.
    def rank(track: dict) -> tuple[int, int]:
        extra = len(set(normalise(track["title"]).split()) - wanted)
        return (extra, 1 if variants(track["title"]) else 0)

    winners.sort(key=rank)
    if rank(winners[0]) == rank(winners[1]):
        return None
    return winners[0]


# --------------------------------------------------------------------
# Source 3: YouTube search + scoring
# --------------------------------------------------------------------

def require_ytdlp() -> None:
    """Fail loudly when the YouTube source is unavailable.

    `youtube_search` returns [] on ImportError, which makes a missing
    yt-dlp look exactly like "no candidate videos". Every artist then
    reports "no verified youtube match" and the run looks like a data
    problem. Callers that treat [] as a real answer should call this
    first.
    """
    try:
        import yt_dlp  # noqa: F401
    except ImportError as exc:
        raise SystemExit(
            "yt-dlp is required for YouTube search but is not installed. "
            "Without it every artist reports 'no verified youtube match'. "
            "Install it with: pip install yt-dlp") from exc


def youtube_search(query: str, limit: int = 6) -> list[dict]:
    """Flat YouTube search via yt-dlp.

    Returns [] when yt-dlp is absent. That is intentional for callers, but
    it also means a missing dependency is indistinguishable from "no
    results" -- which is exactly how a missing yt-dlp presented: every
    artist reported "no verified youtube match". `require_ytdlp()` exists
    so a caller that cannot tolerate that can check up front; the
    resolver's CLI calls it.
    """
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
        # A token-for-token failure used to return 0.0 outright, which
        # rejected correct matches on cosmetic differences: Spotify's
        # "Quevedo: Bzrp Music Sessions, Vol. 52/" against YouTube's
        # "QUEVEDO || BZRP Music Sessions #52" failed on the missing
        # "vol". Require every *meaningful* token instead -- the missing
        # ones must all be low-information -- and at least one real token
        # must survive. A missing meaningful word still fails.
        present = cand_title.split()
        missing = [t for t in title_tokens if t not in present]
        if not missing or not all(_is_low_info(t) for t in missing):
            return 0.0
        if not any(not _is_low_info(t) for t in title_tokens if t in present):
            return 0.0
        title_score = 0.2

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
    """Search YouTube for one track and return the verified best pick.

    Among candidates that clear `min_score`, one on the artist's own
    channel outranks a higher-scoring upload on a stranger's. Ranking on
    score alone picked lyric/fan re-uploads ("Roy Lyrics" over the
    artist's own video) that also passed: for a paid campaign that risks
    a takedown mid-flight and credits the wrong uploader. `_owns_channel`
    is the same strict test the artist fallback uses, so a namesake
    channel still cannot win.
    """
    candidates = youtube_search(f"{artist_name} {track_title}")
    ranked = sorted(
        ((score_candidate(artist_name, track_title, c), c) for c in candidates),
        key=lambda pair: (
            _owns_channel(normalise(artist_name),
                          normalise(pair[1].get("channel") or "")),
            pair[0]),
        reverse=True)
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


# Suffix words that mark a channel as the artist's own rather than a
# stranger's: "Soolking Officiel", "PLK TV", "Mora", "TruenoVEVO".
_CHANNEL_SUFFIXES = frozenset({
    "vevo", "topic", "official", "officiel", "music", "tv", "records",
    "oficial", "channel", "band", "sound",
})


def _owns_channel(artist: str, channel: str) -> bool:
    """True when `channel` is the artist's own channel, not a namesake.

    Deliberately strict. An earlier, looser version accepted any channel
    whose tokens contained the artist name, which pulled in a news
    channel for "Luciano" (its title happened to read "... LUCIANO
    CESA ...") and a YouTuber named "fynn" for the singer Fynn. A paid
    campaign pointed at a stranger's video is worse than an unresolved
    row, so only an exact name match, a name-plus-suffix match, or a
    "- Topic" channel qualifies.
    """
    if not channel:
        return False
    if channel == artist:
        return True
    if channel.endswith(" topic"):
        return True
    tokens = channel.split()
    if tokens and tokens[0] == artist:
        return all(tok in _CHANNEL_SUFFIXES for tok in tokens[1:])
    # "TruenoVEVO" and "PLKTV" normalise to one token, so the suffix is
    # not separable by whitespace. Accept a concatenated suffix only when
    # it is one of the known markers.
    if channel.startswith(artist) and channel != artist:
        remainder = channel[len(artist):].strip()
        if remainder in _CHANNEL_SUFFIXES:
            return True
    return False


def resolve_artist_channel_track(artist_name: str, limit: int = 8,
                                 known_titles: list[str] | None = None
                                 ) -> dict | None:
    """A real, playable video the artist genuinely owns.

    Last-resort fallback for seeds whose metadata titles are fabricated
    (see TODO B5f): the named song does not exist, so no title match is
    possible. This returns a different recording by the *correct* artist
    instead, and only when the upload is on the artist's own channel
    (see `_owns_channel`). Every pick is confirmed playable via oEmbed.

    When `known_titles` (the artist's real discography) is supplied, the
    video title must also match one of those titles. That is what rules
    out a same-named channel: "fynn" the YouTuber and "Fynn" the singer
    share a name, but only the singer's uploads match his real tracks.

    It deliberately does not claim a title match: `matched_title` is None
    and `method` records the weaker provenance, so a caller can decide
    whether that is acceptable rather than being told a fabricated title
    resolved.
    """
    artist = normalise(artist_name)
    if not artist:
        return None
    for cand in youtube_search(artist_name, limit):
        if not _owns_channel(artist, normalise(cand.get("channel") or "")):
            continue
        # The upload must name the artist. A bare name match on the
        # channel alone is not enough when two different people share the
        # name: the YouTuber "fynn" owns the channel "fynn", but none of
        # his uploads mention the singer, so this rejects him.
        cand_title = normalise(cand.get("title") or "")
        if not all(tok in cand_title.split() for tok in artist.split()):
            continue
        if known_titles:
            if not any(t and normalise(t) in cand_title for t in known_titles):
                continue
        oembed = verify_video(cand["video_id"])
        if not oembed:
            continue
        return {"video_id": cand["video_id"],
                "score": None,
                "youtube_title": oembed.get("title", cand["title"]),
                "youtube_author": oembed.get("author_name", cand["channel"]),
                "channel": cand["channel"],
                "duration": cand.get("duration"),
                "matched_title": None,
                "method": "artist_channel"}
    return None
