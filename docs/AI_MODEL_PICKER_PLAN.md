# AI models and OpenRouter connection: plan

> Status: **done, 15 Oct 2026.** All six steps (a to f) are built, checked on the Go 10.3 and
> merged into main (last: be04817). Kept as the record of what was decided and why.
> Line numbers were checked on 15 Oct 2026 and drift; find code by name.
> "Decided" = the owner chose it. "Proposed" = recommended at the time; all were then built.
> Open: a spending limit on the "Boox Day" OpenRouter key (owner, later).

---

## 1. Goals

- AI works as soon as the user connects OpenRouter. Nothing to set up.
- Users who want to can pick the OpenRouter model for each AI job, with its reasoning effort and
  output tokens. Everything else stays hidden and uses safe defaults.
- A bad, blocked or retired model never breaks a feature: Boox Day's own models are always the backup.
- Simple for users. Nothing like the old booxday.com admin page (three models per job, tiers, charts).

## 2. Decisions

| # | Topic | Choice | Status |
|---|---|---|---|
| 1 | Model list | Search; Show: All / Top paid / Top free / Top reasoning; Sort: Most popular / Newest first / Price low to high / Price high to low; at most 200 rows; Refresh button | Decided |
| 2 | Model row | Model name, lab, price in and out per 1M tokens | Decided |
| 3 | After picking a model | Reasoning effort (built from the model's own options) and output tokens, with recommended values; the rest hidden | Decided |
| 4 | Input tokens | Shown as information only (the API has no input setting) | Proposed |
| 5 | Automatic setup | Every job starts on "Boox Day's choice"; two sets (with credit / no credit) | Decided in principle; sets proposed |
| 6 | Adding the key | "Connect OpenRouter" (OAuth PKCE) first, paste as backup | Decided |
| 7 | Return from OpenRouter | One-page GitHub Pages site | Decided; live since 15 Oct 2026 |
| 8 | Jobs | Five: Reading handwriting, Voice, Writing and summaries, Planning and suggestions, Search indexing | Proposed |
| 9 | Failure note on the card | "Your model didn't answer last time; Boox Day's model was used" | Proposed |

---

## 3. What the user sees

### 3.1 Connecting OpenRouter

Where: AI Settings, OpenRouter key card (`ui/components/ApiKeySettingsCard.kt`), and the
"Add your OpenRouter key" popup (`ui/dialogs/AiKeyRequiredPopup.kt`).

1. Tap **Connect OpenRouter**.
2. The tablet's browser opens OpenRouter. Sign in or sign up (Google, GitHub or email).
3. Tap **Authorize**. OpenRouter creates a key labelled "Boox Day".
4. The return page shows **Open Boox Day**. Tap it. Boox Day saves the key (encrypted), checks it,
   and shows "AI is ready".

Backup: the existing paste field and Paste button stay, for keys made elsewhere. The same field also
accepts the connection code the return page shows when its button can't open the app.

"AI is ready" message, for example:
- With credit: "Key works. AI is ready."
- No credit yet: "Key works. No credit yet: AI uses free models, 37 of 50 free requests left today.
  Search indexing starts when you add credit." + link to openrouter.ai/settings/credits.

### 3.2 AI Models card

In AI Settings, right after the AI Features switch (`ui/dialogs/AiSettingsDialog.kt:137`). Greyed out
when AI is off, like the Personalization button. One row per job:

```text
AI Models
Choose the model for each job. If it fails, Boox Day uses its own.

Reading handwriting                                        >
  GPT-5 Nano · Boox Day's choice
Voice                                                      >
  Gemini 3.1 Flash Lite · Boox Day's choice
Writing and summaries                                      >
Planning and suggestions                                   >
Search indexing                                            >
```

The card saves settings itself, like `ui/dialogs/PreferencesDialog.kt:1168`, so `MainScreen.kt` is not
touched (its root lambda is at the JVM method-size limit).

### 3.3 Model list (one per job)

```text
Reading handwriting            Updated 2 h ago  [Refresh]  [X]
[ Search models...                                    ]
Show:  [All] [Top paid] [Top free] [Top reasoning]
Sort:  [Most popular v]

Boox Day's choice: GPT-5 Nano                        (pinned)
DeepSeek V4.1 Flash
  DeepSeek · $0.15 in · $0.60 out per 1M tokens
GLM 5.3 Flash
  Z.ai · $0.02 in · $0.79 out per 1M tokens
...                                       Showing 185 models
```

- **Search** covers every model that can do the job, not only the 200 shown.
- **Show** (one at a time): All, Top paid (price above 0), Top free (price 0 in and out),
  Top reasoning (model supports reasoning).
- **Sort**: Most popular (default; OpenRouter's own ranking), Newest first (creation date),
  Price low to high / high to low (average of in and out price, as OpenRouter sorts).
- **200 rows at most**: the first 200 after Show, Sort and search.
- **Row**: model name (OpenRouter name without the "Lab: " prefix), lab, prices. "Free" when both are 0.
- **Selected row**: black with white text (`docs/ui-patterns.md:279`).
- **Refresh** downloads the list again; it also refreshes by itself when older than a day.
- E-ink rules: `Popup`, `noRippleClickable`, vector icons, `EInkTheme` tokens, no animation,
  "Loading models..." as plain text. No new `EInkTheme` tokens (another agent is editing that file).

### 3.4 Model settings (after tapping a model)

```text
Gemini 3.1 Flash Lite
Google · $0.25 in · $1.50 out per 1M tokens

Reasoning effort
[Off] [Minimal] [Low (Recommended)] [Medium] [High]

Max output tokens    [-]  5,000  [+]    Recommended: 5,000
Can read up to 1,048,576 tokens. Can write up to 65,536.

                                   [Back]   [Use Model]
```

Two buttons only, per `docs/DIALOG_RULEBOOK.md:392`. Back returns to the list. Going back to Boox Day's
choice = tap its pinned row, then Use Model.

Hidden, with today's values: temperature (set per feature), other sampling settings, provider
routing (Search indexing stays on zero-data-retention providers), reasoning token budgets.

### 3.5 Failure note (proposed)

When a pick fails and Boox Day's model answers instead, the card row says so until the pick works
again. If a picked model is no longer on OpenRouter, the row says "No longer available".

---

## 4. Automatic setup: "Boox Day's choice"

- Every job starts on Boox Day's choice. **Model names are not copied into the user's settings**:
  copies go stale. Example: today's voice backup `google/gemini-2.0-flash-lite-001`
  (`api/ModelConfigProvider.kt:58`) is retired (OpenRouter lists no providers for it).
- Boox Day's choice is updated with app updates. The daily model list check skips models that are
  gone or retiring.
- Two sets, chosen from what OpenRouter says about the key (`GET /api/v1/key`, no tokens):
  `is_free_tier` = the account never bought credit.

| Job | With credit | No credit yet |
|---|---|---|
| Reading handwriting | GPT-5 Nano → Gemini 3.1 Flash Lite → Gemma 4 26B (free) | Gemma 4 26B (free) → Gemma 4 31B (free) |
| Voice | Gemini 3.1 Flash Lite → Nemotron 3 Nano Omni (free) | Nemotron 3 Nano Omni (free) |
| Writing and summaries | GPT-5 Nano → Gemini 3.1 Flash Lite → Nemotron 3 Super (free) | Nemotron 3 Super (free) → Gemma 4 31B (free) |
| Planning and suggestions | Gemini 3 Flash Preview → Nemotron 3 Super (free) → Gemma 4 31B (free) | Nemotron 3 Super (free) → Gemma 4 31B (free) |
| Search indexing | Gemini 3.1 Flash Lite (about $0.0005 a page, 14 Oct 2026 test) | Waits for credit: no free model keeps pages private |

- **No-credit bug fix (needed today):** with no credit, every AI feature fails. The first paid model
  returns 402, `AiClient` maps it to "out of credit" (`api/ai/AiClient.kt:124`, `:190`) and
  `AiRetryExecutor` stops at once (`ai/AiRetryExecutor.kt:96`), so the free models at the end of
  each list are never tried. The key card says free models work (`ApiKeySettingsCard.kt:276`).
  Fix: on "no credit", skip paid models, try the free ones, tell the user once.
- Credit can also run out later. A normal key can't read the balance (`/api/v1/credits` needs a
  management key), so the rule above also runs at request time.
- Free-only accounts: one pass through the models, not three (`AiRetryExecutor` does up to 3 × 3 =
  9 requests per action when models fail). Free use is capped at 50 requests a day (1,000 after
  buying $10 of credit), 20 a minute.
- Free models only from the account's allowed list (`GET /api/v1/models/user`): some free models
  run on providers that may keep or train on prompts, and OpenRouter has a separate account setting
  for free models.
- Settings for Boox Day's choice: the same recommended reasoning effort and output tokens as for
  picks (sections 6 and 7). Today the app sends "reasoning on" (= Medium) for some models and nothing
  for others: compare on a few real pages before switching.
- Planning's first model is a preview. OpenRouter can retire previews early; the daily check covers it.

---

## 5. Model data

- One request: `GET https://openrouter.ai/api/v1/models?sort=most-popular&limit=1000` (documented;
  about 80 KB compressed). The order is the popularity rank. Filtering, sorting and search run on the
  tablet: instant and offline.
- With a saved key, also `GET /api/v1/models/user` (the models the account's privacy settings allow).
- Saved to a cache file (not `AppSettings`). Keep only: id, name, created, prices, input and output
  types, supported parameters, `reasoning` object, context length, max output, expiry date.
- Always hidden: `:batch` copies (77; they don't answer live requests), `~` "latest" aliases (19),
  `openrouter/*` and other routers (variable price), models retiring within 30 days.

What each job needs (hidden filters), with counts on 15 Oct 2026:

| Job | Model must | Models | Shown |
|---|---|---|---|
| Reading handwriting | read images, return JSON | 185 | 185 |
| Voice | read audio | 27 | 27 |
| Writing and summaries | return JSON | 307 | 200 |
| Planning and suggestions | return JSON | 307 | 200 |
| Search indexing | read images, return JSON, have a zero-data-retention provider (`GET /api/v1/endpoints/zdr`) | 129 (none free) | 129 |

"Return JSON" = `structured_outputs` or `response_format` in `supported_parameters`. The features ask
for JSON (`jsonSchema(...)` / `jsonOutput()` in the AI services).

---

## 6. Reasoning effort rules

Built from each model's `reasoning` object, as OpenRouter's docs describe
(guides/best-practices/reasoning-tokens). On 15 Oct 2026: 336 of 465 models support reasoning,
in 28 different level combinations; 130 have no levels (on/off only); 133 can't turn it off.

- Levels, low to high: Off (`none`), Minimal, Low, Medium, High, Extra high (`xhigh`), Max.
- Show only the model's `supported_efforts`. `null` = all levels. Missing = no levels (Off / On).
- `mandatory: true`: no Off, and never send `effort: "none"` (the model rejects it).
- No levels, not mandatory: Off / On (`enabled: false` / `enabled: true`; On = Medium).
- No levels, mandatory: "Always reasons", no control. No reasoning support: one line, no control.
- Unknown future level names: shown as written, in OpenRouter's order.
- Recommended (pre-selected) per job: Reading handwriting Low, Voice Off, Writing Low, Planning
  Medium, Search indexing Off. If the model lacks it: the nearest level, the lower one on a tie.
  Examples: GPT-5 Nano for Search indexing → Minimal; GLM 5.3 Flash for Planning → Low.
- Request time: check the saved level against the latest saved options. If it's gone, send no level
  (the model's own default) and show a note. If OpenRouter still refuses, the backup models answer.
- Send rules: a level → `reasoning.effort`; Off → `effort: "none"` when offered, else
  `enabled: false`; On → `enabled: true`.

## 7. Output tokens rules

- Reasoning uses the same limit. OpenRouter gives reasoning about 10% (Minimal), 20% (Low),
  50% (Medium), 80% (High) or 95% (Extra high, Max) of `max_tokens` on budget-based models. Too low:
  empty answer, still billed. Too high on an expensive model: OpenRouter holds credit for the full
  limit while the request runs and can refuse it (402, in-flight budget) even with money left.
- Recommended = job answer size ÷ (1 − reasoning share), rounded up to the next 1,000, capped at the
  model's max output and at 64,000.
- Job answer size (today's values): Reading handwriting 4,000, Voice 4,000, Writing 4,000,
  Planning 8,000, Search indexing 3,000.
- Example, 4,000 job: Off 4,000 · Minimal 5,000 · Low 5,000 · Medium 8,000 · High 20,000 · Max 64,000.
- The value follows the effort choice until the user changes it. − / + stop at the model's limit.
  Below the recommendation: "Answers may be cut off."
- Features that need more keep it: the request uses the larger of the job setting and the feature's
  own value (`ai/AgenticPlanningService.kt:732` asks for 8,192).
- Input tokens: shown only ("Can read up to N tokens"). The app could only limit input by cutting
  the page, voice clip or text it sends.

---

## 8. Connect OpenRouter: technical

### 8.1 OAuth PKCE (OpenRouter guides/overview/auth/oauth)

1. App makes a random `code_verifier`, `code_challenge` = base64url(SHA-256(verifier)), and a
   random `state`. Both are saved in encrypted prefs (`security/UserApiKeyStore.kt` uses
   `LazyEncryptedPrefs`) with the time, so a restart doesn't lose them. Valid 10 minutes.
2. Open in the browser:
   `https://openrouter.ai/auth?callback_url=<return page>&code_challenge=<c>&code_challenge_method=S256&key_label=Boox%20Day&state=<s>`
3. OpenRouter sends the browser to the return page with `?code=...&state=...`.
4. The page's button opens `booxday-connect://openrouter?code=...&state=...`.
5. App checks `state`, then `POST https://openrouter.ai/api/v1/auth/keys`
   `{"code", "code_verifier", "code_challenge_method": "S256"}` → `{"key", "user_id"}`.
6. Key → `OpenRouterKeyCheck` → `UserOpenRouterKey.save` → automatic setup → "AI is ready".
   Clear the verifier and state.

Errors: 400 wrong method, 403 bad code or verifier, 403 expired (after 10 minutes): show
"Connection timed out. Tap Connect OpenRouter again."

### 8.2 Return page (GitHub Pages)

- What it is: GitHub's free hosting turns files in a repo branch into a public web page. One HTML
  file, no server, nothing stored.
- Branch `gh-pages` with `openrouter/index.html` and `.nojekyll`.
  URL: `https://fortolano.github.io/booxday/openrouter/`. **Live since 15 Oct 2026** (commit `3d4a0fd`
  on `gh-pages`; Pages source = `gh-pages` / root). Checked: with a code it shows the button with the
  right `booxday-connect://` link, removes the code from the address bar, Copy Code works, and it
  loads nothing from other sites. Opened without a code it shows "Not connected".
  Never merge `gh-pages` into `main`.
- Page: reads `code` and `state`; shows **Open Boox Day** (link to `booxday-connect://...`); below it,
  the code with **Copy code** and "Paste it in Boox Day: AI Settings, OpenRouter key". No code (denied
  or opened directly): "Not connected. Go back to Boox Day and try again."
- Privacy: no external scripts, fonts or analytics; `<meta name="referrer" content="no-referrer">`;
  remove the code from the address bar after reading it (`history.replaceState`).
- E-ink: black on white, large text and buttons, no animation.
- To change the page: commit to `gh-pages` only (it holds just `index.html`, `.nojekyll` and
  `openrouter/index.html`), with the owner's OK to push. GitHub republishes in about a minute.
- Constant `ProjectInfo.CONNECT_PAGE_URL` next to `REPO_URL` (`ProjectInfo.kt:5`).

### 8.3 App side of the link

- **Not `booxday://` and not `MainActivity`.** `MainActivity` uses the standard launch mode and takes
  every `booxday://` link (`AndroidManifest.xml:60-79`). A link from the browser would start a second
  `MainActivity` with a second canvas, against the Onyx raw-drawing state.
- New small `OpenRouterConnectActivity` (no canvas, plain "Connecting to OpenRouter..." text), its own
  scheme `booxday-connect`, host `openrouter`. It does steps 5 and 6, then brings the app forward with
  `FLAG_ACTIVITY_NEW_TASK | CLEAR_TOP | SINGLE_TOP` (existing `MainActivity.onNewIntent` at
  `MainActivity.kt:234` needs no change) and finishes.
- A copied code pasted into the key field goes through the same exchange.

---

## 9. Storage

- `AppSettings.aiModels` (new group, same pattern as `ocrSettings`, `AppSettings.kt:208`). Per job:
  model id, name, lab, reasoning level, max output tokens, and the model's saved options (levels,
  mandatory, max output, input limit, input types). Empty = Boox Day's choice. The settings JSON
  already accepts new fields (`data/db/Kv.kt:144`), so no database change. Exported in backups:
  nothing secret.
- Model list: cache file in the app's cache folder, with its download time.
- Credit state: from the last key check, kept in memory and refreshed when AI Settings opens and at
  app start.
- PKCE verifier and state: encrypted prefs, 10 minutes, then deleted.

## 10. Code changes by file

Changed:
- `api/ModelConfigProvider.kt`: chain = [user pick] + Boox Day's choice (credit or free set), no
  duplicates; picks are always `AiModel.Custom` with the job's capabilities (never the old built-in
  lookup at `:162`, whose old capability lists can make `AiRequestBuilder.enableReasoning()` throw,
  `api/ai/AiRequestBuilder.kt:128`, e.g. from `booxnotes/BooxNoteIndexer.kt:4363`); skip models
  missing from the latest list; replace the retired voice backup (`:58`).
- `api/ai/AiModel.kt`: `AiModel.Custom` carries the reasoning setting.
- `api/ai/AiClient.kt`: reasoning from the model's setting (`:219`); an empty answer that hit the
  limit is an error, never the reasoning text (`:139`); read OpenRouter's 402 reason (no credit vs
  in-flight budget vs key limit) (`:124`, `:190`).
- `ai/AiRetryExecutor.kt`: no credit → skip paid, try free (`:96`); one pass for free-only accounts.
- Single-model calls get a backup (pick, then Boox Day's choice): `speech/TranscriptionService.kt:420`,
  `ai/VoiceTextParser.kt:73`, `ai/VoiceEventParser.kt:35`, `ai/VoicePreferencesParser.kt:51`,
  `ai/MemoContextExtractor.kt:181` and `:288`.
- `ocr/OpenRouterOcrService.kt`: use the pick (`:54`), fall back to Gemini 3.1 Flash Lite on a model
  error, keep zero data retention, reasoning from the setting instead of always off (`:80`).
- `data/datastore/AppSettings.kt`: the `aiModels` group.
- `ui/components/ApiKeySettingsCard.kt`, `ui/dialogs/AiKeyRequiredPopup.kt`: Connect button, "AI is
  ready", paste field accepts a code.
- `ui/dialogs/AiSettingsDialog.kt`: the AI Models card.
- `AndroidManifest.xml`: `OpenRouterConnectActivity` with the `booxday-connect` scheme.
- `ProjectInfo.kt`: `CONNECT_PAGE_URL`.

New:
- `api/OpenRouterModelCatalog.kt`: download, cache, filters, sorts, search, 200 cap.
- `api/AiModelSettings.kt`: pure rules (reasoning options, recommended level, recommended tokens).
- `api/OpenRouterConnect.kt`: PKCE, auth URL, code exchange.
- `OpenRouterConnectActivity.kt`.
- `ui/components/AiModelsCard.kt`, `ui/dialogs/ModelPickerPopup.kt` (list and settings step).
- `gh-pages` branch: `openrouter/index.html`, `.nojekyll`.

Not touched: `MainScreen.kt`, `MainActivity.kt`, `EInkTheme.kt`, `BooxNoteIndexer.kt`.

## 11. Tests (focused JVM tests, run with `--tests`)

- PKCE: RFC 7636 example (verifier `dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk` → challenge
  `E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM`); wrong state refused; expired after 10 minutes.
- Reasoning options from real model data (GPT-5 Nano, GLM 5.3 Flash, Gemini 3.1 Flash Lite,
  Claude Haiku 5.5, on/off-only, mandatory without levels, no reasoning, `null` levels); nearest
  level; never "none" for mandatory models.
- Output tokens: formula, rounding, caps, the larger-of rule.
- Catalog: job filters, hidden kinds, Show, sorts, 200 cap, search beyond the cap. Fixture: a saved
  copy of the real API answer.
- Chains: pick first, no duplicates, credit and free sets, missing models skipped.
- `AiClient`: empty answer that hit the limit → error; 402 reasons.
- `AiRetryExecutor`: no credit → free models tried; one pass for free-only accounts.
- Settings JSON without `aiModels` still loads.
- On the tablets, only with the owner's OK: Connect in each tablet's browser; voice, handwriting and
  planning with a pick; offline with a saved list.

## 12. Build order

Each UI step is shown to the owner before the next one starts.

- (a) Storage, chains and fallback, central reasoning and token handling, the `AiClient` and
  `AiRetryExecutor` fixes, the no-credit fix and the two automatic sets. No UI. With tests.
- (b) Connect OpenRouter, the return page, and "AI is ready".
- (c) The AI Models card.
- (d) The model list: search, Show, Sort, Refresh.
- (e) The settings step: reasoning effort and output tokens.
- (f) The failure note.

## 13. Check during the build

- `/api/v1/models/user` answer shape, and the free-requests-left field of `/api/v1/key`
  (`free_model_daily_requests`): need a key; check with the owner's OK.
- Whether each tablet's browser opens `booxday-connect://` from the page button.
- OpenRouter's sign-in and Authorize pages on e-ink.
- Which free models the owner's account settings allow.
- Quality with the recommended reasoning levels on a few real pages, before switching Boox Day's choice.

## 14. Also found (not part of this plan)

- `docs/ai-integration.md` is from Dec 2025 and describes models and limits that no longer exist.
- 8 of the 9 built-in models in `api/ai/AiModel.kt` are no longer on OpenRouter (only GPT-5 Nano is).
- `ModelConfigProvider.toReasoningConfig` (`:139`) has no callers; section 6 replaces it.
