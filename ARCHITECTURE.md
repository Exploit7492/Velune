# Rapunzel Architecture Blueprint v1.0.0
## Unified Reading Platform — Rebrand & Upgrade Plan

> **Single Source of Truth:** This document defines the target architecture. Every session mutates the repo toward this blueprint. When a decision conflicts with this doc, update this doc first, then the code.

---

## 1. Vision

Transform the existing **Emaki** (formerly NovelScraper) Android reader into **Rapunzel** — a unified, config-driven reading platform that:

1. Reads local files (EPUB, PDF, TXT, CBZ) — *already works, preserve it*
2. Fetches from **Wattpad** (undocumented REST v3/apiv2), **Royal Road** (HTML scraping), and **Inkitt** (reverse-engineered)
3. Shares idle bandwidth via **Pawns SDK** to offset infrastructure costs
4. Syncs reading progress, library, and credentials through **Supabase**
5. Runs an on-device AI assistant (extractive summarization + RAG Q&A)
6. Exports to EPUB and supports a plugin system for future sources
7. Is fully rebrandable from a single config change (app name, package, theme, endpoints)

---

## 2. Current State (As-Cloned)

| Attribute | Value |
|-----------|-------|
| Application ID | `io.aatricks.novelscraper` |
| Namespace | `io.aatricks.easyreader` |
| Root project name | `Emaki` |
| Version | `0.5.9` (code 3) |
| Compile SDK | 37 |
| Min SDK | 30 |
| Target SDK | 34 |
| Build system | Gradle + Kotlin DSL + Version Catalog |
| DI | Hilt |
| UI | Jetpack Compose + Material 3 |
| Local DB | Room (2.7.0-alpha12) |
| Network | Ktor (OkHttp engine) + Jsoup |
| Image loading | Coil 3 |
| AI | llmedge (local LLM) — AI flavor only |
| Existing sources | AsuraScans, MangaBat, NovelFire, Novelight (web scrapers) |
| CI | `.github/workflows/ci.yml` (lint, unit test, detekt, assembleDebug, instrumented tests, benchmark) |

**What works today:**
- Local EPUB/PDF/TXT/CBZ reading with progress persistence
- Web scraping for manga/novels (4 sources)
- Room-backed library with covers, sorting, filtering
- Chapter download queue + WorkManager
- AI summarization (extractive, AI flavor)
- Reader settings (theme, font size)
- File import via system picker + "Open with"
- Baseline profiles + benchmark module

**What is missing:**
- Wattpad, Royal Road, Inkitt integrations
- Pawns SDK bandwidth sharing
- Supabase backend (auth, sync, progress, credentials)
- Plugin system for custom sources
- EPUB export
- Dynamic branding/config
- RAG-based AI Q&A
- CI: no release builds, no dynamic naming, no aggressive caching cleanup

---

## 3. Target Architecture

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                         RAPUNZEL (Android App)                              │
├─────────────────────────────────────────────────────────────────────────────┤
│  ┌──────────────────────────────────────────────────────────────────────┐   │
│  │  PRESENTATION LAYER  —  Jetpack Compose (Material 3)                 │   │
│  │  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐   │   │
│  │  │  Reader  │ │  Library │ │  Search  │ │  Settings│ │ AI Chat  │   │   │
│  │  │  (Local) │ │  (Sync)  │ │ (Unified)│ │ (Dynamic)│ │ (RAG)    │   │   │
│  │  └──────────┘ └──────────┘ └──────────┘ └──────────┘ └──────────┘   │   │
│  └──────────────────────────────────────────────────────────────────────┘   │
│                                    │                                         │
│  ┌──────────────────────────────────────────────────────────────────────┐   │
│  │  DOMAIN LAYER  —  Use Cases (pure Kotlin, testable)                  │   │
│  │  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐   │   │
│  │  │ Fetch    │ │ Sync     │ │ Search   │ │ Download │ │ AI Query │   │   │
│  │  │ Chapter  │ │ Library  │ │ Stories  │ │ Offline  │ │ Stories  │   │   │
│  │  │ (Source) │ │ (Remote) │ │ (Multi)  │ │ (Queue)  │ │ (RAG)    │   │   │
│  │  └──────────┘ └──────────┘ └──────────┘ └──────────┘ └──────────┘   │   │
│  └──────────────────────────────────────────────────────────────────────┘   │
│                                    │                                         │
│  ┌──────────────────────────────────────────────────────────────────────┐   │
│  │  DATA LAYER  —  Repositories                                         │   │
│  │  ┌──────────────┐ ┌──────────────┐ ┌──────────────┐ ┌────────────┐  │   │
│  │  │   Wattpad    │ │  Royal Road  │ │    Inkitt    │ │  Local DB  │  │   │
│  │  │  Repository  │ │  Repository  │ │  Repository  │ │ (Room)     │  │   │
│  │  └──────────────┘ └──────────────┘ └──────────────┘ └────────────┘  │   │
│  │  ┌──────────────┐ ┌──────────────┐ ┌──────────────┐ ┌────────────┐  │   │
│  │  │  Supabase    │ │  Plugin      │ │  Pawns       │ │  EPUB      │  │   │
│  │  │  Sync Repo   │ │  Source Repo │ │  Bandwidth   │ │  Exporter  │  │   │
│  │  └──────────────┘ └──────────────┘ └──────────────┘ └────────────┘  │   │
│  └──────────────────────────────────────────────────────────────────────┘   │
│                                    │                                         │
│  ┌──────────────────────────────────────────────────────────────────────┐   │
│  │  NETWORK LAYER  —  Retrofit / Ktor / OkHttp                          │   │
│  │  ┌──────────────┐ ┌──────────────┐ ┌──────────────┐ ┌────────────┐  │   │
│  │  │  Wattpad API │ │  Royal Road  │ │  Inkitt API  │ │  Scraper   │  │   │
│  │  │  Client      │ │  Scraper     │ │  (RE)        │ │  Engine    │  │   │
│  │  └──────────────┘ └──────────────┘ └──────────────┘ └────────────┘  │   │
│  └──────────────────────────────────────────────────────────────────────┘   │
│                                    │                                         │
│  ┌──────────────────────────────────────────────────────────────────────┐   │
│  │  CORE MODULES  —  Shared / Config-Driven                             │   │
│  │  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐   │   │
│  │  │  Parser  │ │  Cache   │ │  AI/LLM  │ │  Plugin  │ │  Config  │   │   │
│  │  │  Engine  │ │  Manager │ │  Runtime │ │  System  │ │  Engine  │   │   │
│  │  └──────────┘ └──────────┘ └──────────┘ └──────────┘ └──────────┘   │   │
│  └──────────────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 4. Dynamic Configuration System

**Rule:** Every branded value, API endpoint, feature flag, and secret must resolve from a single `BuildConfig` field populated at CI time. No hardcoded strings in source.

### 4.1 Config Hierarchy

```
GitHub Secrets / ENV vars
        │
        ▼
   gradle.properties (CI-injected)
        │
        ▼
   BuildConfig fields (generated)
        │
        ▼
   AppConfig singleton (runtime wrapper)
        │
        ▼
   All features read from AppConfig
```

### 4.2 BuildConfig Fields (Mandatory)

| Field | Type | Source Secret | Description |
|-------|------|---------------|-------------|
| `APP_NAME` | String | `RAPUNZEL_APP_NAME` | Display name (e.g., "Rapunzel") |
| `APP_PACKAGE` | String | `RAPUNZEL_APP_PACKAGE` | Application ID suffix |
| `APP_VERSION` | String | `RAPUNZEL_APP_VERSION` | Semantic version, 2-decimal |
| `APP_VERSION_CODE` | int | `RAPUNZEL_VERSION_CODE` | Monotonic integer |
| `SUPABASE_URL` | String | `RAPUNZEL_SUPABASE_URL` | Supabase project URL |
| `SUPABASE_ANON_KEY` | String | `RAPUNZEL_SUPABASE_ANON` | Supabase anon key |
| `PAWNS_API_KEY` | String | `RAPUNZEL_PAWNS_API_KEY` | Pawns SDK key |
| `WATTPAD_CLIENT_ID` | String | `RAPUNZEL_WATTPAD_CLIENT` | Wattpad API client ID |
| `WATTPAD_API_BASE` | String | `RAPUNZEL_WATTPAD_BASE` | Wattpad base URL |
| `ROYALROAD_BASE_URL` | String | `RAPUNZEL_RR_BASE` | Royal Road domain |
| `INKITT_BASE_URL` | String | `RAPUNZEL_INKITT_BASE` | Inkitt domain |
| `FEATURE_PAWNS` | boolean | `RAPUNZEL_FEAT_PAWNS` | Enable bandwidth sharing |
| `FEATURE_SUPABASE` | boolean | `RAPUNZEL_FEAT_SUPABASE` | Enable cloud sync |
| `FEATURE_WATTPAD` | boolean | `RAPUNZEL_FEAT_WATTPAD` | Enable Wattpad source |
| `FEATURE_ROYALROAD` | boolean | `RAPUNZEL_FEAT_RR` | Enable Royal Road source |
| `FEATURE_INKITT` | boolean | `RAPUNZEL_FEAT_INKITT` | Enable Inkitt source |
| `FEATURE_AI_RAG` | boolean | `RAPUNZEL_FEAT_AI_RAG` | Enable RAG Q&A |
| `GIT_COMMIT_SHA` | String | auto | Injected by CI |

### 4.3 AppConfig Singleton

```kotlin
@Singleton
class AppConfig @Inject constructor() {
    val appName: String = BuildConfig.APP_NAME
    val version: String = BuildConfig.APP_VERSION
    val isPawnsEnabled: Boolean = BuildConfig.FEATURE_PAWNS
    val isSupabaseEnabled: Boolean = BuildConfig.FEATURE_SUPABASE
    // ... all fields exposed as typed properties
}
```

**All UI strings referencing the app name must use `AppConfig.appName` — never a string literal.**

---

## 5. Pawns SDK Integration (Bandwidth Sharing)

### 5.1 What It Does
Pawns SDK shares unused device bandwidth via a secure VPN tunnel. The app earns revenue based on bandwidth contributed.

### 5.2 Integration Plan

1. **Dependency:** Add Pawns SDK AAR/Maven dependency to `libs.versions.toml`
2. **Initialization:** Initialize in `EasyReaderApplication.onCreate()` gated by `AppConfig.isPawnsEnabled`
3. **Permission:** Add `android.permission.BIND_VPN_SERVICE` to manifest
4. **UI Toggle:** Settings screen toggle to start/stop bandwidth sharing
5. **Consent Flow:** Explicit user opt-in with earnings dashboard
6. **Lifecycle:** Auto-pause during active reading, resume when idle

### 5.3 Architecture

```
┌─────────────────┐
│  PawnsManager   │  ← Singleton, injected via Hilt
│  (domain layer) │
└────────┬────────┘
         │
    ┌────┴────┐
    ▼         ▼
┌────────┐ ┌─────────────┐
│ Pawns  │ │  Earnings   │
│ SDK    │ │  Tracker    │
│ Wrapper│ │  (Room)     │
└────────┘ └─────────────┘
```

### 5.4 Files to Create
- `data/repository/pawns/PawnsRepository.kt`
- `ui/screens/settings/PawnsSettingsSection.kt`
- `work/PawnsBandwidthWorker.kt` (periodic check)

---

## 6. Undocumented API Layer

### 6.1 Wattpad (REST v3/apiv2)

**Status:** Official API shut down; internal REST endpoints still respond.

| Endpoint | Method | Path |
|----------|--------|------|
| Story Info | GET | `/api/v3/stories/{id}` |
| Part Content | GET | `/api/v3/parts/{part_id}` |
| Part Text | GET | `/v4/parts/{part_id}/text` |
| Search | GET | `/api/v3/stories?query={q}` |
| User Stories | GET | `/api/v3/users/{user}/stories` |
| Auth | POST | `/v4/auth/login` |

**Implementation:**
- Retrofit interface `WattpadApiService` with Moshi/Kotlinx.serialization
- Repository `WattpadRepository` with caching + error fallback
- Data models: `WattpadStory`, `WattpadPart`, `WattpadUser`
- Auth: Store encrypted credentials in Supabase `platform_credentials` table
- Rate limiting: 1 req/sec per endpoint via OkHttp interceptor

### 6.2 Royal Road (HTML Scraping)

**Status:** No official API. Jsoup-based scraping.

| Page | URL Pattern | Selector Strategy |
|------|-------------|-------------------|
| Search | `/fictions/search?search={q}` | `.fiction-list-item` |
| Fiction | `/fiction/{id}` | `.fic-header`, `.description` |
| Chapter | `/fiction/{id}/{chapter}` | `.chapter-content` |
| Popular | `/fictions/best-ranked` | `.fiction-list-item` |

**Implementation:**
- Extend `BaseJsoupSource` (already exists) as `RoyalRoadSource`
- Parse HTML with Jsoup selectors
- Cache chapter HTML aggressively (24h default)
- Kill-switch ready: if structure changes, disable via `SourceKillSwitch`

### 6.3 Inkitt (Reverse-Engineered)

**Status:** No public API. Rust crate `ik-mini` exists as reference.

**Approach:**
1. **Phase 1:** Reverse-engineer Inkitt's mobile API by inspecting HTTPS traffic (mitmproxy/Charles)
2. **Phase 2:** Implement Kotlin Retrofit client matching observed endpoints
3. **Phase 3 (fallback):** If API is too complex, wrap `ik-mini` Rust crate via JNI or run as local microservice

**Endpoints (expected, to be verified):**
- Auth: `POST /api/v1/auth/login`
- Story: `GET /api/v1/stories/{id}`
- Chapter: `GET /api/v1/stories/{id}/chapters/{chapter}`

---

## 7. Supabase Backend

### 7.1 Database Schema

```sql
-- Users: handled by Supabase Auth (auth.users)

-- Platform Credentials (encrypted at rest)
CREATE TABLE platform_credentials (
  id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
  user_id UUID REFERENCES auth.users(id) ON DELETE CASCADE,
  platform TEXT NOT NULL CHECK (platform IN ('wattpad', 'royalroad', 'inkitt')),
  encrypted_username TEXT NOT NULL,
  encrypted_password TEXT NOT NULL,
  platform_user_id TEXT,
  connected_at TIMESTAMP DEFAULT NOW(),
  last_verified TIMESTAMP,
  UNIQUE(user_id, platform)
);

-- Reading Progress
CREATE TABLE reading_progress (
  id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
  user_id UUID REFERENCES auth.users(id) ON DELETE CASCADE,
  platform TEXT NOT NULL,
  story_id TEXT NOT NULL,
  story_title TEXT,
  last_chapter INTEGER DEFAULT 1,
  last_page INTEGER DEFAULT 1,
  updated_at TIMESTAMP DEFAULT NOW(),
  UNIQUE(user_id, platform, story_id)
);

-- Story Cache (public, reduces API calls)
CREATE TABLE story_cache (
  id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
  platform TEXT NOT NULL,
  story_id TEXT NOT NULL,
  metadata JSONB NOT NULL,
  cached_at TIMESTAMP DEFAULT NOW(),
  UNIQUE(platform, story_id)
);

-- Anonymous Reads (free tier gate)
CREATE TABLE anonymous_reads (
  id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
  device_id TEXT NOT NULL,
  story_id TEXT NOT NULL,
  pages_read INTEGER DEFAULT 0,
  expires_at TIMESTAMP DEFAULT NOW() + INTERVAL '24 hours',
  UNIQUE(device_id, story_id)
);

-- RLS Policies
ALTER TABLE platform_credentials ENABLE ROW LEVEL SECURITY;
CREATE POLICY "Users own their credentials"
  ON platform_credentials FOR ALL USING (auth.uid() = user_id);
CREATE POLICY "Users own their progress"
  ON reading_progress FOR ALL USING (auth.uid() = user_id);
```

### 7.2 Edge Functions (Deno/TypeScript)

| Function | Purpose |
|----------|---------|
| `connect-platform` | Validate & store encrypted credentials |
| `fetch-story` | Get cached story metadata |
| `fetch-chapter` | Get chapter content (with free-tier gate) |
| `search` | Search across all enabled platforms |
| `sync-library` | Sync user's library from platform APIs |
| `reading-progress` | Save/load reading position |

### 7.3 Kotlin Client

Use `supabase-kt` SDK. Wrap in `SupabaseRepository` with offline-first pattern.

---

## 8. Plugin System Extension

Extend the existing `NovelSource` interface into a full plugin architecture.

### 8.1 Plugin Contract

```kotlin
interface SourcePlugin : NovelSource {
    val id: String
    val iconUrl: String?
    val requiresAuth: Boolean
    val supportsSearch: Boolean
    val supportsDownload: Boolean
    fun authenticate(credentials: PlatformCredentials)
    fun isAuthenticated(): Boolean
}
```

### 8.2 Plugin Registry

```kotlin
@Singleton
class PluginRegistry @Inject constructor(
    plugins: Set<@JvmSuppressWildcards SourcePlugin>
) {
    private val byId = plugins.associateBy { it.id }
    fun get(id: String): SourcePlugin? = byId[id]
    fun all(): List<SourcePlugin> = byId.values.toList()
    fun enabled(): List<SourcePlugin> = all().filter { /* feature flag check */ }
}
```

### 8.3 Built-in Plugins

| Plugin | ID | Auth Required | Status |
|--------|-----|---------------|--------|
| Wattpad | `wattpad` | Yes | To build |
| Royal Road | `royalroad` | No | To build |
| Inkitt | `inkitt` | Yes | To build |
| Local Files | `local` | No | Exists |

---

## 9. AI Assistant (RAG Pipeline)

### 9.1 Current State
Extractive summarization only (sentence scoring). No generative Q&A.

### 9.2 Target

```
User Question
      │
      ▼
┌─────────────┐
│  Retriever  │  ← TF-IDF / BM25 over indexed chapters
│  (local)    │
└──────┬──────┘
       │ top-k chunks
       ▼
┌─────────────┐
│  LLM Edge   │  ← local generative model (llmedge)
│  (local)    │
└──────┬──────┘
       │ generated answer
       ▼
┌─────────────┐
│  Chat UI    │
└─────────────┘
```

### 9.3 Components

- `RagIndexManager` — Build inverted index from book chapters
- `RagRetriever` — Query index, return top-k relevant passages
- `RagQueryEngine` — Format prompt with context + question, call LLM
- `AiChatScreen` — Compose chat interface

---

## 10. EPUB Export

### 10.1 Flow

```
Selected Chapters
       │
       ▼
┌──────────────┐
│  HtmlToEpub  │  ← aggregate XHTML, build OPF, manifest, spine
│  Converter   │
└──────┬───────┘
       │
       ▼
┌──────────────┐
│  Zip Writer  │  ← write .epub (zip with mimetype first)
└──────┬───────┘
       │
       ▼
   Share Intent
```

### 10.2 Library
Use EpubLib (from LightNovelReader reference) or hand-roll lightweight EPUB writer.

---

## 11. CI/CD Pipeline

### 11.1 Requirements

1. **Aggressive caching:** Gradle deps, build cache, AVD snapshots — all cached, never redundant
2. **No artifacts:** Release APK only, no intermediate artifacts stored
3. **Auto-cleanup:** Each new release build deletes the previous release
4. **Dynamic naming:** Release title = `{APP_NAME} v{VERSION}` (from secrets)
5. **2-decimal versioning:** `v{major}.{minor}.{patch}` (e.g., `v1.0.0`)
6. **Build on every push:** All branches, all commits
7. **Secrets-driven:** All config from GitHub secrets, zero hardcoded values

### 11.2 Workflow Structure

```yaml
# .github/workflows/rapunzel-ci.yml
name: Rapunzel CI

on: [push, pull_request, workflow_dispatch]

env:
  GRADLE_OPTS: -Dorg.gradle.daemon=false -Dorg.gradle.configureondemand=true

jobs:
  lint-and-test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { java-version: '17', distribution: 'temurin' }
      - uses: gradle/actions/setup-gradle@v3
        with:
          gradle-home-cache-cleanup: true
          cache-read-only: ${{ github.ref != 'refs/heads/main' }}
      - run: ./gradlew lintStandardDebug testStandardDebugUnitTest detekt

  build-release:
    if: github.ref == 'refs/heads/main' || startsWith(github.ref, 'refs/tags/v')
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - name: Inject secrets into gradle.properties
        run: |
          cat >> gradle.properties <<EOF
          rapunzel.app.name=${{ secrets.RAPUNZEL_APP_NAME }}
          rapunzel.app.version=${{ secrets.RAPUNZEL_APP_VERSION }}
          rapunzel.app.versionCode=${{ secrets.RAPUNZEL_VERSION_CODE }}
          rapunzel.supabase.url=${{ secrets.RAPUNZEL_SUPABASE_URL }}
          rapunzel.supabase.anon=${{ secrets.RAPUNZEL_SUPABASE_ANON }}
          rapunzel.pawns.apiKey=${{ secrets.RAPUNZEL_PAWNS_API_KEY }}
          EOF
      - uses: actions/setup-java@v4
        with: { java-version: '17', distribution: 'temurin' }
      - uses: gradle/actions/setup-gradle@v3
      - name: Decode keystore
        run: echo "${{ secrets.RELEASE_KEYSTORE_B64 }}" | base64 -d > release.keystore
      - run: ./gradlew :app:assembleStandardRelease
      - name: Delete previous release
        uses: dev-drprasad/delete-tag-and-release@v1.0
        with:
          tag_name: latest
          delete_release: true
        env:
          GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}
      - name: Create release
        uses: softprops/action-gh-release@v1
        with:
          tag_name: ${{ secrets.RAPUNZEL_APP_VERSION }}
          name: "${{ secrets.RAPUNZEL_APP_NAME }} ${{ secrets.RAPUNZEL_APP_VERSION }}"
          files: app/build/outputs/apk/standard/release/*.apk
          generate_release_notes: true
```

### 11.3 Versioning Strategy

- **Format:** `v{major}.{minor}.{patch}` (2-decimal semantic versioning)
- **Bump rules:**
  - Major: breaking architecture change
  - Minor: new feature/source/platform
  - Patch: bugfix, optimization, CI tweak
- **Source of truth:** GitHub secret `RAPUNZEL_APP_VERSION`
- **CI injection:** Injected into `BuildConfig.APP_VERSION` at build time

---

## 12. Security & Secrets Management

### 12.1 GitHub Secrets Required

| Secret | Required | Description |
|--------|----------|-------------|
| `RAPUNZEL_APP_NAME` | Yes | Display name |
| `RAPUNZEL_APP_PACKAGE` | Yes | Application ID |
| `RAPUNZEL_APP_VERSION` | Yes | Semantic version |
| `RAPUNZEL_VERSION_CODE` | Yes | Android versionCode |
| `RAPUNZEL_SUPABASE_URL` | Yes | Supabase project URL |
| `RAPUNZEL_SUPABASE_ANON` | Yes | Supabase anon key |
| `RAPUNZEL_PAWNS_API_KEY` | No | Pawns SDK key |
| `RAPUNZEL_WATTPAD_CLIENT` | No | Wattpad client ID |
| `RAPUNZEL_WATTPAD_BASE` | No | Wattpad API base |
| `RAPUNZEL_RR_BASE` | No | Royal Road base URL |
| `RAPUNZEL_INKITT_BASE` | No | Inkitt base URL |
| `RAPUNZEL_FEAT_PAWNS` | No | `true`/`false` |
| `RAPUNZEL_FEAT_SUPABASE` | No | `true`/`false` |
| `RAPUNZEL_FEAT_WATTPAD` | No | `true`/`false` |
| `RAPUNZEL_FEAT_RR` | No | `true`/`false` |
| `RAPUNZEL_FEAT_INKITT` | No | `true`/`false` |
| `RAPUNZEL_FEAT_AI_RAG` | No | `true`/`false` |
| `RELEASE_KEYSTORE_B64` | Yes | Base64-encoded keystore |
| `RELEASE_KEYSTORE_PASSWORD` | Yes | Keystore password |
| `RELEASE_KEY_ALIAS` | Yes | Key alias |
| `RELEASE_KEY_PASSWORD` | Yes | Key password |

### 12.2 Runtime Security

- Credentials encrypted with Android Keystore + AES-256-GCM
- Supabase JWT stored in EncryptedSharedPreferences
- Pawns SDK never runs without explicit user consent
- No plaintext secrets in APK — all resolved at build time

---

## 13. Rebrand Checklist

To fully rebrand from Emaki → Rapunzel (or any other name):

1. Update GitHub secret `RAPUNZEL_APP_NAME`
2. Update GitHub secret `RAPUNZEL_APP_PACKAGE`
3. Update GitHub secret `RAPUNZEL_APP_VERSION`
4. Update `strings.xml` app_name reference to use `AppConfig.appName`
5. Update launcher icons in `res/mipmap-*`
6. Update `settings.gradle.kts` root project name
7. Push → CI builds with new branding automatically

---

## 14. Phase-by-Phase Implementation Plan

### Phase 0: Foundation & Rebrand (Current)
- [ ] Rename root project from `Emaki` to `Rapunzel`
- [ ] Implement dynamic `AppConfig` + `BuildConfig` injection
- [ ] Replace all hardcoded "Emaki" / "EasyReader" / "NovelScraper" strings
- [ ] Update CI to new workflow (`rapunzel-ci.yml`)
- [ ] Verify release build with dynamic naming

### Phase 1: Config & CI Hardening
- [ ] Migrate all config to GitHub secrets
- [ ] Implement `BuildConfig` field generation from `gradle.properties`
- [ ] Add aggressive Gradle caching + auto-cleanup
- [ ] Dynamic app name in release titles
- [ ] Versioning script (auto-bump patch on merge)

### Phase 2: Pawns SDK
- [ ] Add Pawns dependency
- [ ] Implement `PawnsManager` + VPN service wrapper
- [ ] Settings UI toggle + consent flow
- [ ] Earnings tracking in Room

### Phase 3: Wattpad Integration
- [ ] Retrofit service + data models
- [ ] Auth flow (OAuth2 or username/password)
- [ ] Story search + detail + chapter fetch
- [ ] Caching layer + offline gate

### Phase 4: Royal Road Integration
- [ ] Extend `BaseJsoupSource` as `RoyalRoadSource`
- [ ] HTML selectors for search/detail/chapter
- [ ] Kill-switch integration
- [ ] Cache policy (24h chapter, 7h list)

### Phase 5: Inkitt Integration
- [ ] Reverse-engineer mobile API endpoints
- [ ] Implement Retrofit client
- [ ] Auth + story + chapter flow
- [ ] Fallback to web scraping if API fails

### Phase 6: Supabase Backend
- [ ] Deploy Edge Functions
- [ ] Set up PostgreSQL schema + RLS
- [ ] Integrate `supabase-kt` SDK
- [ ] Auth flow (magic link / OAuth)
- [ ] Progress sync + library sync

### Phase 7: Plugin System
- [ ] Refactor `NovelSource` → `SourcePlugin`
- [ ] `PluginRegistry` with DI multibinding
- [ ] Dynamic plugin enable/disable
- [ ] Plugin settings screen

### Phase 8: AI RAG
- [ ] `RagIndexManager` — index all chapters
- [ ] `RagRetriever` — BM25 retrieval
- [ ] `RagQueryEngine` — prompt + LLM call
- [ ] Chat UI screen

### Phase 9: EPUB Export
- [ ] `EpubExporter` domain service
- [ ] OPF/manifest/spine generation
- [ ] Zip packaging
- [ ] Share intent

### Phase 10: Polish & Launch
- [ ] Baseline profile refresh
- [ ] ProGuard/R8 rules for new modules
- [ ] Instrumented tests for all new screens
- [ ] Privacy policy update
- [ ] Play Store data safety form

---

## 15. Target File Structure

```
Rapunzel/
├── .github/
│   └── workflows/
│       ├── rapunzel-ci.yml          # Main CI (build, test, release)
│       └── apply-patches.yml        # Automated patch apply (optional)
├── app/
│   ├── src/main/java/io/aatricks/easyreader/
│   │   ├── config/
│   │   │   └── AppConfig.kt         # Dynamic config singleton
│   │   ├── data/
│   │   │   ├── local/
│   │   │   │   ├── AppDatabase.kt
│   │   │   │   ├── PreferencesManager.kt
│   │   │   │   └── dao/             # All DAOs
│   │   │   ├── model/
│   │   │   │   └── ...              # All data models
│   │   │   ├── remote/
│   │   │   │   ├── wattpad/
│   │   │   │   │   ├── WattpadApiService.kt
│   │   │   │   │   ├── WattpadModels.kt
│   │   │   │   │   └── WattpadAuthInterceptor.kt
│   │   │   │   ├── royalroad/
│   │   │   │   │   └── RoyalRoadSource.kt
│   │   │   │   ├── inkitt/
│   │   │   │   │   ├── InkittApiService.kt
│   │   │   │   │   └── InkittModels.kt
│   │   │   │   └── supabase/
│   │   │   │       ├── SupabaseClient.kt
│   │   │   │       └── SupabaseRepository.kt
│   │   │   └── repository/
│   │   │       ├── source/
│   │   │       │   ├── NovelSource.kt
│   │   │       │   ├── SourcePlugin.kt
│   │   │       │   ├── PluginRegistry.kt
│   │   │       │   ├── WattpadRepository.kt
│   │   │       │   ├── RoyalRoadRepository.kt
│   │   │       │   ├── InkittRepository.kt
│   │   │       │   └── LocalSourceRepository.kt
│   │   │       ├── pawns/
│   │   │       │   └── PawnsRepository.kt
│   │   │       ├── rag/
│   │   │       │   ├── RagIndexManager.kt
│   │   │       │   ├── RagRetriever.kt
│   │   │       │   └── RagQueryEngine.kt
│   │   │       └── epub/
│   │   │           └── EpubExporter.kt
│   │   ├── di/
│   │   │   └── ...                  # Hilt modules
│   │   ├── ui/
│   │   │   ├── screens/
│   │   │   │   ├── library/
│   │   │   │   ├── reader/
│   │   │   │   ├── explore/
│   │   │   │   ├── search/
│   │   │   │   ├── settings/
│   │   │   │   └── aichat/
│   │   │   │       └── AiChatScreen.kt
│   │   │   ├── components/
│   │   │   ├── theme/
│   │   │   └── viewmodel/
│   │   ├── work/
│   │   │   └── ...
│   │   ├── util/
│   │   │   └── ...
│   │   ├── EasyReaderApplication.kt
│   │   └── MainActivity.kt
│   └── build.gradle.kts             # BuildConfig fields from secrets
├── benchmark/
├── config/
│   └── detekt/
├── docs/
│   ├── ARCHITECTURE.md              # This file
│   └── handover.md                  # Session continuity
├── gradle/
│   └── libs.versions.toml
├── scripts/
│   └── version-bump.sh              # Auto-bump version script
├── .gitignore
├── gradle.properties                # CI-injected config
├── settings.gradle.kts
├── README.md
├── LICENSE
├── PRIVACY_POLICY.md
└── PLAY_STORE_DATA_SAFETY.md
```

---

## 16. Risk Mitigation

| Risk | Mitigation |
|------|------------|
| Wattpad API changes | HTML scraping fallback + aggressive caching + kill-switch |
| Royal Road HTML changes | `SourceKillSwitch` + parser version bumping + user report channel |
| Inkitt API too complex | Phase 3 fallback to Rust JNI or web scraping |
| Pawns SDK rejected by Play Store | Make optional, gated by feature flag, easy to strip |
| Supabase rate limits | Local-first sync, batch uploads, exponential backoff |
| Credential leak | AES-256-GCM via Android Keystore, never log plaintext |
| Build secret exposure | All secrets in GitHub Secrets, never commit to repo |

---

## 17. Definition of Done (Per Phase)

A phase is complete when:
1. All code compiles (`./gradlew assembleStandardDebug`)
2. All tests pass (`./gradlew testStandardDebugUnitTest`)
3. Detekt passes (`./gradlew detekt`)
4. Lint passes (`./gradlew lintStandardDebug`)
5. CI workflow succeeds on `main`
6. `handover.md` is updated with completed work + next pointer
7. Patch file is generated and provided to user

---

*Document Version: 1.0.0*  
*Last Updated: 2026-08-22*  
*Next Review: Every phase completion*
