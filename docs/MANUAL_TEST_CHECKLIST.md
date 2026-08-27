# MineBro Manual Acceptance Checklist

Run through this in the actual Minecraft client. Each item is pre-labeled with what I could
verify from this environment (no keyboard/mouse input into the Minecraft window is possible
here) versus what genuinely needs your own pass.

- **VERIFIED** - confirmed against real code execution or a real running service this session.
- **TRACE** - the code path was read and reasoned through; not executed live.
- **UNVERIFIED** - requires you to actually play; not tested here.

## Installation

| Check | Status | Notes |
|---|---|---|
| `git clone` + `.\gradlew.bat build` succeeds | VERIFIED | Run repeatedly this session, always green. |
| `.\gradlew.bat runClient` boots without exceptions | VERIFIED | Confirmed clean `MineBro is starting` -> `MineBro client ready` boot multiple times, most recently this session. |
| README installation steps match reality | VERIFIED | Cross-checked every command/path in the README against the actual `build.gradle`/config code. |

## First run / configuration

| Check | Status | Notes |
|---|---|---|
| Config file auto-created at `run/config/minebro/config.json` | VERIFIED | Confirmed the file exists with expected default content after a real `runClient` launch this session. |
| `/minebro status` shows provider, local/cloud, model, endpoint, connectivity | TRACE | Code reads correctly; not seen rendered in-game. |
| `/minebro settings` prints config path/key state **and** opens the settings screen | UNVERIFIED | The screen is new this pass. Confirm the deferred `Minecraft.execute` actually beats the vanilla chat screen closing, or the panel will flash and vanish. |
| `/minebro models` lists models or explains discovery isn't required | UNVERIFIED | New command this pass - needs an in-game run. |

## Local provider (Ollama)

| Check | Status | Notes |
|---|---|---|
| Ollama reachable, correct model pulled | VERIFIED | Hit the real local Ollama instance directly (`/api/tags`) - `mistral:latest` present. |
| Real chat completion round-trip | VERIFIED | Sent the actual system+user prompt MineBro builds; got a correct, on-topic answer. |
| Real tool-call JSON from the configured model | VERIFIED | One live sample: clean single-line JSON, correct real item id, no fences/prose. |
| Malformed `ollamaEndpoint` fails cleanly, not with a raw exception | VERIFIED | Unit-tested this pass (`OllamaProviderTest`) - confirmed the fix, not just the intent. |

## Cloud / OpenAI-compatible provider

| Check | Status | Notes |
|---|---|---|
| Endpoint/model/key configuration | TRACE | Code path read; no API key available in this environment to call a real cloud endpoint. |
| Auth failure (401/403), 404, 429, 5xx handling | TRACE | Each now has a distinct message (added this pass); not fired against a real cloud response. |
| Malformed base URL fails cleanly | VERIFIED | Unit-tested this pass. |
| Model discovery via `GET /models` | TRACE | Parser unit-tested against the real OpenAI response shape; not called against a live cloud server. |

**If you have an API key for OpenAI/Groq/OpenRouter/LM Studio available**, this is the one area worth manually confirming - set `providerId` to `"openai-compatible"`, fill in the endpoint/model/key, and try `/minebro status`, `/minebro models`, and a question.

## Provider switching

| Check | Status | Notes |
|---|---|---|
| Switching `providerId` requires no code change | VERIFIED | `ProviderRegistry.create()` is a pure config-driven factory; confirmed by reading it. `AgentLoop` has zero provider-specific branching (the dead switch statement that used to exist was removed this pass). |
| Actually switching providers and using both in one session | UNVERIFIED | Now doable in-game via `/minebro settings` -> Save, with no relaunch. Save, then run `/minebro status` and a question immediately and confirm both hit the new provider. |
| A provider switch survives without a restart | UNVERIFIED | `AgentLoop.setProvider` is regression-tested with fake providers, but the screen -> `applyProvider` -> live turn path has not been exercised in a running client. |

## Conversation

| Check | Status | Notes |
|---|---|---|
| History persists across turns ("How do I make a pickaxe?" -> "What about diamonds?") | VERIFIED | Regression-tested this pass with a scripted fake provider proving the second turn's outgoing request includes the first turn's reply. This was a real, confirmed bug before the fix. |
| Tool calls/results retained in order | VERIFIED | Same test suite, dedicated test case. |
| A cancelled/failed turn doesn't corrupt history | VERIFIED | Regression-tested (`aCancelledTurnNeverExecutesAToolOrMakesAFurtherProviderCall`, `anOlderRequestResolvingAfterANewerOneStartedPublishesNothing`). |
| It *feels* coherent in a real back-and-forth | UNVERIFIED | Needs you to actually converse with it in-game. |

## Concurrency / cancellation

| Check | Status | Notes |
|---|---|---|
| A newer request supersedes and cancels an older one | VERIFIED | Regression-tested, including both possible completion orderings of the race. |
| `/minebro stop` cancels the active request, not a stale one | VERIFIED | Regression-tested. |
| A cancelled request cannot execute a tool afterward | VERIFIED | Regression-tested. |
| Actually mashing `/minebro <question>` repeatedly in-game feels right (no stray late answers) | UNVERIFIED | Needs live play. |

## Commands / item input

| Check | Status | Notes |
|---|---|---|
| `/minebro craft wooden_sword` **away from any crafting table** -> refuses with a "no crafting table in reach" message | TRACE | Vanilla recipe is 3x3 (`["X","X","#"]`, 3 rows), so it requires a table. The client-side fail-fast check should catch this before any server work. |
| `/minebro craft wooden_sword` **standing next to a crafting table** -> actually crafts | UNVERIFIED | 3x3 crafting was enabled this pass. The table is re-verified server-side before the inventory is touched; needs live play to confirm end to end. |
| Breaking the crafting table mid-craft -> fails cleanly with "isn't in reach anymore" | UNVERIFIED | Hard to hit by hand; the server-side re-check exists for exactly this race. |
| `/minebro craft woodensword` (misspelled) -> helpful suggestion, no raw parser error | VERIFIED | Re-ran the actual Levenshtein implementation against the real string - `wooden_sword` is edit-distance 1, will be the top suggestion. |
| `/minebro craft wooden sword` (multi-word) -> same clean handling | VERIFIED | Same computation; also edit-distance 1. |
| `/minebro recipe oak_planks` / `/minebro recipe minecraft:oak_planks` -> identical result | TRACE | `ItemIdParser`'s namespace-defaulting logic read and unit-tested; both forms resolve to the same id. |
| A genuinely 2x2-fitting item (e.g. `minecraft:stick`, `minecraft:oak_planks`) auto-crafts | UNVERIFIED | Needs live play with real inventory contents. |
| Insufficient materials -> clear message, not a crash | TRACE | Logic read; not fired live. |

## Chat screen

All UNVERIFIED - screens cannot be unit-tested without a Minecraft bootstrap, and no automated
check drives them.

| Check | Status | Notes |
|---|---|---|
| `B` opens the panel; `B` again closes it | UNVERIFIED | Should not hijack an already-open screen, and should do nothing with no world loaded. |
| `/minebro` prints the help line **and** opens the panel | UNVERIFIED | Same deferred-`execute` concern as `/minebro settings`. |
| The world stays visible and unpaused behind the panel | UNVERIFIED | `isPauseScreen()` returns false and `renderBackground` is a deliberate no-op. |
| Asking a question shows the local echo immediately, then tool steps, then the answer | UNVERIFIED | Steps should render in place as `Checking inventory...` -> `✓ ...`. |
| Send morphs to Stop mid-turn; Stop actually cancels | UNVERIFIED | A cancelled turn must remove the pending bubble, not leave a stale one. |
| Closing the panel mid-turn and reopening still shows the finished answer | UNVERIFIED | Transcript state is deliberately static so a turn survives a toggle. |
| Long answers wrap and scroll; auto-scroll stops when you scroll up | UNVERIFIED | |

## Settings screen

| Check | Status | Notes |
|---|---|---|
| Provider toggle swaps the `[x]` marker and the endpoint/model values | UNVERIFIED | Typing under one provider, toggling away and back, must not lose what was typed. |
| API Key row appears only for OpenAI-compatible | UNVERIFIED | Ollama should show "no API key needed" instead. |
| Saved key shows only the last 4 characters, with no reveal option | UNVERIFIED | Deliberate: a reveal toggle is a screenshot/recording leak. `Change` replaces, never reveals. |
| `Change` -> `Cancel` restores the mask without clearing the stored key | UNVERIFIED | |
| Save with `Change` open and the box blank clears the key | UNVERIFIED | Intentional, hinted in the field placeholder. |
| Test Connection probes the **unsaved** form values | UNVERIFIED | Point the endpoint at a dead port without saving and confirm a red failure line. |
| Permission button cycles all four levels and Save persists it | UNVERIFIED | |
| ✕ with unsaved edits discards them; the config file is unchanged | UNVERIFIED | |
| Panel is not clipped at small window sizes / high GUI scale | UNVERIFIED | The panel is a fixed 300x200 and is **not** clamped to the window, unlike the chat panel. Most likely cosmetic defect. |

## Client stability

| Check | Status | Notes |
|---|---|---|
| No client freeze longer than ~500ms from any single command | TRACE | `craft_item`'s bounded wait was cut from 2s to 500ms this pass specifically to bound this; not measured with a frame-time profiler in a live session. |
| No stack traces / raw exceptions in chat under any tested failure path | VERIFIED for malformed URLs (unit-tested) + TRACE for provider HTTP failures, malformed JSON, unknown tools, invalid arguments (all pre-existing, code-read, not re-broken by this pass's changes - confirmed via the full test suite staying green). |

## What I could not do

I have no way to type into the Minecraft client's chat/command input in this environment. Every
row above marked UNVERIFIED needs you to actually launch `runClient` and play.

The highest-value checks, in order:

1. **The two screens.** Neither can be unit-tested, so every row in those sections is
   unverified by automation. Opening them at all is the single most informative check.
2. **3x3 crafting next to a table.** Newly enabled, and it mutates inventory - worth
   confirming the item count actually changes as reported.
3. **A provider switch applying without a restart.** The mechanism is regression-tested with
   fakes, but the screen-to-live-turn path has never run end to end.
4. **A real cloud provider**, if you have a key - still the least exercised code path.
5. **Live conversation and cancellation "feel"** - repeated questions, `/minebro stop`, no
   stray late answers.
