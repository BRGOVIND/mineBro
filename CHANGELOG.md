# Changelog

All notable changes to MineBro are documented here.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this
project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

- **Animated avatar.** The HUD badge is now a drawn rune-orb glyph with the full per-state
  animation set from `docs/DESIGN.md` §3.1: an occasional idle "candle flicker", a stepped
  inner rune while thinking, an orbiting dot while a tool runs, a one-shot flash on responding,
  colour crossfades into success and error, a single shake on error, and a fade when offline.
- **The chat panel grows out of the avatar.** Pressing `B` blooms the badge and expands the
  panel from it over 120ms, collapsing back on close. The badge now stays visible behind the
  chat panel instead of disappearing the moment it opens. `Esc`, the header's ✕, the `B`
  keybind, and `/minebro chat` all animate identically.
- **`reducedMotion` setting**, toggled from the settings screen or the config file. Suppresses
  looping, oscillating and translating motion; colour crossfades stay, since they carry state
  information rather than decoration.
- Animation timing primitives (`com.minebro.core.anim`) with unit tests, kept free of any
  Minecraft imports so they are testable under the project's pure-logic test rule.

### Notes

- The glyph is drawn with fill rectangles rather than a sprite sheet. `docs/DESIGN.md` §3.4
  recommends a texture atlas, reasoning that the rotating rune and orbit dot are unachievable
  with a font glyph - true of a font glyph, but not of drawing. This keeps the full animation
  set without a sprite-sheet pipeline, and stays a one-method swap if an atlas is produced.

## [1.0.0] — 2026-08-27

First complete version. Minecraft 1.21.1, Fabric, Java 21.

### Added

- **AI provider abstraction** with two adapters: Ollama (local, no API key) and a universal
  OpenAI-compatible adapter that works with any server implementing the
  `/v1/chat/completions` and `/v1/models` shape — LM Studio, llama.cpp, vLLM, OpenRouter,
  OpenAI, or a self-hosted server — through configuration alone, with no source changes.
- **Agent loop** with JSON-prompt tool calling, iteration caps, and conversation history that
  persists across turns.
- **Deterministic tools** backed by real game state: inventory lookup, position, player
  status, recipe lookup, craftability check, and crafting.
- **Verified crafting**: an action is reported as successful only after the resulting
  inventory change is confirmed. Covers both 2x2 recipes and 3x3 recipes when a crafting
  table is within reach, with the table re-verified server-side before the inventory is
  touched.
- **Chat screen**, opened with the `B` key or `/minebro`, showing tool steps as they execute
  and allowing an in-flight request to be cancelled.
- **Settings screen**, opened with `/minebro settings`: provider selection, endpoint, model,
  API key, and permission level, with a connection test against unsaved values. Changes apply
  immediately without restarting Minecraft.
- **HUD avatar** reflecting idle, thinking, error, and offline states.
- **Commands**: `/minebro`, `/minebro <question>`, `inventory`, `recipe`, `craft`, `status`,
  `models`, `settings`, `stop`.
- **Permission levels** (`READ_ONLY`, `SAFE_ACTIONS`, `GAMEPLAY_ACTIONS`,
  `DESTRUCTIVE_ACTIONS`) enforced at tool execution time.
- **Cancellation and request supersession**: a newer question silences an older in-flight one
  rather than letting a stale answer surface.
- **API key redaction** everywhere a key is displayed, with no reveal affordance.

### Known limitations

- Movement, block placing and breaking, container interaction, and combat are not implemented.
- No native provider tool-calling; a JSON-prompt convention is used for compatibility across
  models.
- Multiplayer and dedicated servers are out of scope. Crafting is restricted to local
  singleplayer worlds.
- Raising the permission level from the settings screen takes effect immediately for the
  execution gate, but newly permitted tools are not advertised to the model until Minecraft
  is restarted.
