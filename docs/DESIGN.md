# MineBro — Phase 2: UI/UX & Visual Design

**Status:** Design blueprint. No implementation. Input to Phase 3.
**Authoritative upstream document:** `docs/ARCHITECTURE.md` (Phase 1). Every tool name,
permission level, provider, avatar state, command, and version tag in this document is
taken from that document; nothing below invents a tool, a permission level, a provider, or
a feature Phase 1 deferred. Where this document proposes something Phase 1 did not specify
(e.g. a color palette, an animation curve, an eighth avatar state), that is called out
explicitly as a Phase 2 addition and given a version tag consistent with Phase 1's roadmap
(§4 of Phase 1).

Ground facts carried over from Phase 1, restated because they constrain every visual
decision below: Minecraft 1.21.1, Fabric Loader 0.19.3, Fabric API 0.116.15+1.21.1, Java 21,
**official Mojang mappings**, split source sets (`main` / `client`). All UI code in this
document belongs exclusively in `src/client/java/com/minebro/client/`. Any class or method
name below is a Mojang-mapped name. Anything uncertain against the currently-resolved
Fabric API surface is marked **VERIFY:** rather than asserted.

---

## 1. MineBro Visual Identity

### 1.1 What MineBro is not, visually

Before defining what MineBro looks like, rule out what it must not look like, because the
failure mode is well-worn: a rounded white chat bubble, a gradient-blue circular avatar with
a soft drop shadow, a rocket-ship or star-burst logo, sans-serif marketing type, confetti/
sparkle micro-animations, and 🔥/✨/🚀 emoji as UI furniture. That whole vocabulary belongs to
web SaaS chat widgets (Intercom, ChatGPT, Copilot) and reads as *pasted on top of* Minecraft
rather than *native to* it. MineBro must never use a rounded rectangle with a soft shadow —
Minecraft's own UI has no soft shadows and no rounded corners anywhere, and breaking that
rule is the single fastest way to look like a foreign object.

### 1.2 Brand pillars

Three words drive every visual decision, taken directly from Phase 1's product vision
(§1) and the brief's personality section: **grounded** (truth over flourish — the model
is never the authority, so the UI should never *perform* confidence it hasn't earned),
**small** (a companion, not a control panel — MineBro occupies one hotbar slot's worth of
HUD, not a sidebar), **warm-competent** (helpful and slightly playful, never childish,
never corporate-cold).

### 1.3 Color system

Two families: a **neutral structural palette** (stone/parchment, used for every panel,
border, and body text) and a small **accent palette** (used only for the avatar, state
indicators, and interactive highlights — never for large fills).

**Neutrals — "Deepslate & Parchment":**

| Token | Hex | Use |
|---|---|---|
| `bg.panel.dark` | `#28242099` over blurred backdrop, `#2B2A26` solid fallback | Chat panel, settings screen background fill |
| `bg.panel.raised` | `#3A3630` | Raised sub-panels, message bubbles (MineBro side) |
| `bg.panel.inset` | `#1E1B18` | Input field background, recessed slots |
| `border.outer` | `#101010` | 1px outer panel border (matches vanilla's near-black frame) |
| `border.highlight` | `#5A5449` | 1px inner highlight, top/left edges only (vanilla-style bevel) |
| `text.primary` | `#E7E1D6` (warm off-white, not pure `#FFFFFF`) | Body text |
| `text.secondary` | `#B4AC9C` | Timestamps, subtitles, disabled labels |
| `text.disabled` | `#6E6A60` | Disabled controls |

The off-white (`#E7E1D6` rather than `#FFFFFF`) and warm greys are deliberate: Minecraft's
own GUI text (`#FFFFFF` on a stone-textured `#C6C6C6` panel with the classic drop-shadow)
already reads warm because of the surrounding texture. A pure cool white next to warm stone
brown looks like a foreign layer. Parchment-warm neutrals let MineBro's panels sit next to
vanilla inventory/chat without a visible seam.

**Accent — "Beacon Amber" (signature brand color):**

| Token | Hex | Use |
|---|---|---|
| `accent.brand` | `#E8A93C` | Avatar idle glow, logo mark, focus rings, active-tab underline, `WORKING` state |
| `accent.brand.dim` | `#8A692B` | Brand accent at 40% perceived brightness, for subtle/inactive occurrences |
| `accent.cognition` | `#4FD8D0` (aqua-teal, carried from Phase 1 §11.4's `THINKING` color) | Thinking/processing pulses only |
| `accent.positive` | `#6FCB6A` | Success, `RESPONDING` completion tick |
| `accent.negative` | `#E05555` | Error, destructive-confirmation emphasis |
| `accent.neutral.off` | `#6B6B6B` | Offline/idle-dim |

**Why amber, and explicitly not blue.** Blue-to-purple gradients are the default "this is
an AI" signifier across ChatGPT, Copilot, Bing Chat, Gemini, and a dozen Discord bots — it
has become wallpaper, and worse, it reads as *cold* and *corporate*, which directly
contradicts the brief's "not overly corporate" requirement. Amber does three jobs blue
cannot: (1) it is a **Minecraft-native hue family already** — torches, glowstone, beacons,
XP orbs, and the golden-hour "safe to be outside" lighting all live in this range, so
MineBro's glow reads as "a warm light source in your world" rather than "a UI sticker"; (2)
it is **legible against Minecraft's actual backgrounds** — amber holds contrast against sky,
grass, stone, and cave darkness alike, where a saturated blue disappears against ocean/sky
biomes and clashes with the enchanting-table blue/purple that Minecraft already uses for
"magic"; (3) it lets Phase 1's own placeholder palette (§11.4, where `WORKING` is already
gold `0xFFAA00`) become the *brand* color instead of a throwaway spinner color — MineBro's
identity and its "actively helping" state are visually the same color, which is a coherent
story: the amber glow *is* MineBro, whether idle-dim or actively-working-bright. Cool aqua
is deliberately reserved as the *secondary* "thinking/cognition" accent (again carried
forward from Phase 1's existing `THINKING` color) so the palette still signals "this is
software with a reasoning step" without making that the brand's whole identity.

### 1.4 Typography

**Primary: Minecraft's built-in font (`minecraft:default`, the vanilla bitmap/Unicode
font used by `Font`/`GuiGraphics#drawString`).** Every piece of MineBro UI — chat text,
buttons, labels, settings — uses the vanilla font at the standard scale used elsewhere in
Minecraft's own screens. This is a hard rule, not a default-because-lazy: a supplementary
custom font is the second-fastest way (after rounded-blue-bubbles) to look like a plugin
rather than part of the game, because every other word on screen (inventory tooltips, chat,
F3, other mods' text) is set in the vanilla font, and a mismatched font sits out immediately
even at a glance.

**No supplementary UI font is introduced.** Where the brief asks for "typography" as a
system to define, the system *is* "use `Font#draw`/`GuiGraphics#drawString` consistently,
never `Font#drawInBatch` with a custom `FontSet`, and never ship a `.ttf`/bitmap font
resource." The only typographic variation MineBro uses is what vanilla already exposes:
**bold** for message-author labels and confirmation titles (`Component.literal(...).
withStyle(ChatFormatting.BOLD)`), **italic** for system/meta lines ("MineBro is offline"),
and **color** via `ChatFormatting`/ARGB int for semantic emphasis (item names, warnings).
Do not use underline except for the settings screen's keyboard-focus indicator.

Text sizing is expressed in the vanilla unit: 1 line of default-scale vanilla text is 9px
tall (8px cap-height + 1px shadow) at GUI scale 1x-equivalent logical pixels — call this
`1 lh` (line height) throughout this document. All spacing below is defined in `lh` and in
whole logical pixels (`px`, meaning `GuiGraphics`-space / scaled coordinates, i.e.
`graphics.guiWidth()`/`guiHeight()` units — **never raw window pixels**, per Phase 1 §11.3).

### 1.5 Iconography

16×16-grid pixel icons, 1px solid outline, max 3 colors per icon (outline + fill + one
accent), no anti-aliasing, no gradients within an icon — this matches the visual density of
vanilla item/effect icons so MineBro's iconography (tool-step icons, provider logos in the
settings list, the avatar's state glyphs if the atlas approach is used) sits comfortably
next to them. Provider "logos" in the settings screen are **not** the real vendor wordmarks/
logos (trademark and asset-licensing risk, and they'd all be blue/green tech-brand marks
that fight the palette) — instead each provider gets a small monochrome glyph in
`accent.brand` (a droplet for Ollama/local, a bracket-pair for any OpenAI-compatible local
runtime, a plug for a generic HTTP endpoint) with the provider's plain-text name doing the
actual identification.

### 1.6 Panel treatment: vanilla 9-slice, not a modern flat/blurred panel — with one exception

**Decision: MineBro's interactive screens (chat panel, settings, confirmation dialog) use
Minecraft's own 9-slice panel language** — the same visual family as the inventory
background, the vanilla button texture, and dialog/confirm screens (`ConfirmScreen`-style),
i.e. a beveled rectangle with a 1px near-black outer border, a 1px lighter inner highlight
on the top/left, a mid-value stone-toned fill, and hard corners. Concretely this means
drawing panels with `GuiGraphics#blitSprite`/`GuiGraphics#fill` using the same 9-slice
technique vanilla buttons and backgrounds use (nine-patch texture with fixed-width edges),
not a single flat rounded `fill()` rectangle.

**Justification.** The brief explicitly warns against overdesigning and against looking
like "a generic web AI chatbot pasted into Minecraft." A soft, blurred, semi-transparent
flat panel (the modern web-chat convention) is exactly that pasted-on look — Minecraft has
no blur shader active during normal gameplay UI, so a blurred panel would be the single
most obviously-foreign element on screen. The vanilla 9-slice panel is instantly legible as
"a Minecraft dialog" to every player who has ever opened a crafting table or a server
disconnect screen, which buys MineBro trust for free: it looks like it belongs, before the
player has read a word of it.

**The one deliberate modern accent, kept minimal:** a **2px `accent.brand` top-edge rule**
on MineBro-owned panels only (chat screen, settings screen, confirmation screen) — a single
thin amber line inset 0px from the top border, drawn *after* the vanilla-style panel, purely
as a signature so a screenshot is unambiguously "MineBro" versus a vanilla or another mod's
screen. Everything else about the panel is vanilla-shaped. This is the entire "modern
accent" budget for the visual system — resist adding a second one. Background blur/
translucency is used only for the tiny HUD avatar badge itself (§4), never for a full
screen, since the avatar overlays live gameplay and needs to stay legible against any
background, which vanilla achieves for its own HUD elements (hunger/health icons) via a
drop-shadow, not a blur — MineBro's avatar uses the same drop-shadow technique, not a blur.

### 1.7 Borders, shadows, transparency — concrete values

- Panel outer border: 1px, `border.outer` (`#101010`), solid.
- Panel inner highlight: 1px, `border.highlight`, top and left edges only (vanilla bevel
  convention — light source implied from top-left).
- Panel fill: `bg.panel.dark` at 88% opacity (`0xE0` alpha) when drawn over gameplay (chat
  screen, confirmation screen pause gameplay rendering behind a darkened background per
  vanilla `Screen#renderBackground`); 100% opaque when the world isn't visible behind it.
- Text shadow: always on (`GuiGraphics#drawString(..., dropShadow=true)`), matching every
  other piece of Minecraft text — never disable it for a "flatter" look.
- No box-shadow / glow-blur effects on panels. The avatar (§2-§3) is the one element allowed
  a soft radial glow, because it represents a light source, not a UI surface.

---

## 2. Avatar Concept

### 2.1 Form

MineBro's avatar is **not a face, not a portrait, and not a chat-bubble icon.** It is a
small **glowing rune-orb** — a compact abstract glyph reminiscent of an enchanting-table
rune or a Redstone-lit crystal, roughly a rounded octagon/gem silhouette at 12–14px within
its 16×16 tile, rendered mostly in `accent.brand` amber with a 1px near-black outline and a
2–3px soft radial glow bleeding outward at low opacity. This choice is deliberate against
three alternatives that were rejected:

- **A cartoon face/mascot** — rejected because the brief explicitly says "not childish" and
  a face invites an uncanny, over-cute treatment that ages badly and pushes toward "OMG!!!"
  energy.
- **A chat-bubble/speech-icon** — rejected because it's the literal web-chatbot cliché the
  brief asks to avoid, and it doesn't animate meaningfully (a bubble can't "think").
  Note: the reserved emoji-style glyphs in Phase 1 §11.4 (`•`, `»`, `⚙`, `✔`, `✘`, `⊘`) are
  a legitimate *fallback* rendering path (§3.4 below) but are not the primary visual design.
- **A miniature player/mob head** — rejected because it implies MineBro *is* an entity in
  the world (a pet, a familiar) which Phase 1's architecture explicitly does not support
  (no `move_to`, no world presence, no entity) — the visual should not promise embodiment
  the product doesn't have.

A rune/glyph reads as "a small intelligent presence" without anthropomorphizing, fits
Minecraft's existing "glowing magical symbol" visual vocabulary (enchanting table, end
portal frame, sculk sensor), and — critically for animation — a geometric glyph can
convincingly *pulse, rotate, and flicker* to communicate state in a way a static face
cannot without looking like a bad emoji.

### 2.2 Silhouette and sizing

Base sprite: **16×16px**, matching Phase 1's HUD sizing target of "approximately one
hotbar slot" (a hotbar slot's icon area is 16×16 at 1.0 scale). Provide a 32×32 variant for
`avatarScale` values above 1.5 (Phase 1 §12.3 allows 0.5–3.0) to avoid visible upscaling
blur — Minecraft's `NEAREST`-filtered texture blitting will look chunky-crisp at 2x from a
16px source, which is fine and in-style, but a 32×32 source keeps 2x–3x scales crisp
without needing a 3rd asset. **VERIFY:** confirm `GuiGraphics#blitSprite` mipmap/filter
behavior at non-integer scale factors before finalizing whether a 32×32 source is strictly
necessary — this is a rendering-fidelity check, not a design-blocking one.

### 2.3 Personality expressed visually

The brief's personality words map directly onto restraint rules for the avatar:

| Trait | Visual rule |
|---|---|
| Helpful | Always faces "toward" useful info — the avatar's glow brightens (not bounces) when it has something to say (`RESPONDING`/`SUCCESS`), rather than demanding attention. |
| Slightly playful | The idle animation (§3) has a small, infrequent (~every 8–12s) "flicker" — a single-frame brightness blip, like a candle catching a draft — never a bounce, spin, or wiggle loop. |
| Intelligent | The `THINKING` state uses a rotating inner glyph (rune segments cycling), not a generic three-dot ellipsis alone, though the ellipsis is used redundantly in text for accessibility (§12). |
| Concise | No idle looping animation exceeds ~1.5s per cycle; nothing about the avatar should reward staring at it. |
| Not childish | No easing overshoot/bounce (`elastic`/`back` easing is banned everywhere in this spec, §13). |
| Not corporate | No progress-bar chrome, no percentage counters, no "typing…" indicator styled like a messaging app. |
| Not annoying | Every non-`IDLE`/`OFFLINE` state decays back to `IDLE` automatically (Phase 1 §12.1's state machine) — the avatar never demands a click to dismiss itself. |
| Minecraft-native | Built entirely from the palette in §1.3, the vanilla font, and 16×16-grid pixel art. |

---

## 3. Avatar State System

### 3.1 Canonical states (Phase 1 §12.1 — authoritative, do not diverge)

Phase 1 froze this enum for the MVP-through-v0.6 timeline:

```java
public enum AvatarState { IDLE, THINKING, RESPONDING, WORKING, SUCCESS, ERROR, OFFLINE }
```

Every visual/animation spec below targets exactly these seven values plus one
presentation-layer sub-state (`LISTENING`, §3.2) and one proposed roadmap addition
(`WARNING`, §3.3) — both called out as *not* part of the Phase 1 enum so an implementing
engineer does not add unplanned states to v0.1's state machine.

| State | Trigger (from Phase 1 §12.1) | Visual treatment | Animation |
|---|---|---|---|
| `OFFLINE` | health probe fails / no reachable provider | Glyph rendered at 35% opacity, desaturated to `accent.neutral.off`, no glow | **Static**, no loop. One-shot 400ms fade-to-dim on entry (ease-out). |
| `IDLE` | default / after any decay | Glyph at full opacity in `accent.brand`, soft 2px glow at 60% intensity | **Loop**, 8–12s randomized interval: a single 120ms brightness pulse (60%→100%→60% glow intensity) — the "candle flicker." Ease: `sine` in/out. Not a continuous breathing loop — continuous breathing reads as "waiting for you," which is the wrong signal for a tool that should feel present but not needy. |
| `THINKING` | request submitted | Glyph holds steady; the inner rune segments (3 short strokes inside the octagon) rotate through 3 positions | **Loop**, 900ms per full rotation (3 × 300ms steps, no easing — a stepped/discrete rotation, like a gear, not a smooth spin — smooth spin reads as a generic web spinner). Color shifts to `accent.cognition` (aqua) for the inner rune only; outer glyph stays amber. Subtitle text shows an ASCII ellipsis animation (`.` `..` `...`, 400ms/step) as a redundant, accessibility-friendly signal (§12.3). |
| `WORKING` | tool call issued (nested inside `THINKING`, per Phase 1's state diagram: `THINKING → WORKING → THINKING`) | Outer glyph gains a **rotating single orbit dot** (a 2px amber dot circling the glyph at a 2px radius) | **Loop**, 700ms per orbit, `linear` easing (mechanical, not organic — this is "a gear is turning," distinct from `THINKING`'s cognitive pulse). When a multi-iteration loop is in progress, the subtitle shows the iteration hint from Phase 1 §12.1 (`⚙ 2/6`) — render as plain text, not a progress bar. |
| `RESPONDING` | first text token / non-streaming completion | Glyph glow flashes once to 100% then settles to a steady bright state in `accent.brand`, slightly brighter than `IDLE`'s resting glow | **One-shot** 250ms ease-out flash-in, then holds (no loop) until transition to `SUCCESS` or the response finishes rendering in chat. |
| `SUCCESS` | response complete | Glyph tinted `accent.positive` green, brief outline-only checkmark **inset inside** the glyph (not replacing it — the glyph shape stays constant across all states; only its internal glyph-fill and outline color change, so the silhouette never "jumps" between states) | **One-shot** 200ms ease-out crossfade to green, holds 2000ms (Phase 1 §12.1's fixed decay timer), then 300ms ease-in crossfade back to `IDLE` amber. |
| `ERROR` | typed error (Phase 1 §20.1's `MineBroError` taxonomy) | Glyph tinted `accent.negative` red, single **short horizontal shake** of the whole 16×16 sprite (±1px, not a rotation) | **One-shot** 150ms shake (3 cycles of ±1px at 50ms each, `linear`), then holds red for the remaining 4000ms decay window (Phase 1 §12.1), then 300ms crossfade back to `IDLE`. The shake fires once on entry only — it must never repeat during the hold, or it reads as an alarm rather than a notice. |

All state-color changes are **crossfades on the glyph fill, never a hard cut** (minimum
150ms, per §13's global animation minimum) — a hard color swap at 60fps is legible as a
glitch, not a state change, at this sprite size.

### 3.2 `LISTENING` — a presentation sub-state of `IDLE`, not a new enum value

The brief asks for a `listening` state. Phase 1's MVP has no voice input and no passive
chat-interception (§13.4 of Phase 1 explicitly rejects chat-prefix interception), so there
is no event in the v0.1–v0.6 architecture that means "the model is listening" in the way a
voice assistant's mic-active state does. The correct mapping is: **`LISTENING` is what the
avatar looks like while `MineBroChatScreen`'s `EditBox` has input focus and the player has
not yet submitted a message** — it is not a new `AvatarStateSink` transition, it is a purely
client-side rendering flag (`isChatInputFocused: boolean`) read directly by
`HudAvatarRenderer`/the in-screen avatar badge, orthogonal to `AvatarState`. Visually:
`IDLE`'s glyph, but the 2px glow pulses in sync with each keystroke (a single 80ms glow-tick
per keypress, capped at one flash per 150ms so fast typing doesn't strobe) rather than on
the 8–12s idle timer. This is intentionally a small, low-cost addition — it needs no state
machine change, ships whenever the chat screen ships (v0.5), and gives the "listening"
feeling the brief wants without inventing a feature Phase 1 didn't design for.

### 3.3 `WARNING` — a proposed v1.0 enum addition, not built before then

The brief also asks for a `warning` state, distinct from `error`. Phase 1's error taxonomy
(§20.1–20.2) already distinguishes **terminal** failures (`ProviderAuthFailed`,
`LoopLimitExceeded`) from **recoverable, non-exceptional** conditions that are "the normal,
healthy path" (`ValidationFailed`, `UnknownTool` with suggestions) — but Phase 1's avatar
state machine routes everything non-happy-path through the single `ERROR` state. That is a
reasonable MVP simplification (fewer states = less to build for v0.1), but it does mean
"you're 2 iron short" currently gets the same red-shake treatment as "the provider is
unreachable," which overstates the former. **Recommendation for v1.0 ("Polish", per Phase 1
§4), not before:** add an eighth enum value, `WARNING`, for exactly the conditions that are
non-terminal and don't warrant the red shake — degraded-mode notices (remote server disabled
tools, JSON_PROMPT fallback active, a claim-check correction fired, `ValidationFailed`
surfaced as a chat correction rather than a dead end). Visual treatment: glyph tinted a
muted gold-orange (`#D8873A`, between `accent.brand` and `accent.negative` — deliberately
*not* reusing `accent.brand` itself, so it doesn't look like ordinary idle/working amber),
no shake, no flash — just a steady tint held for 3000ms then a 300ms crossfade to `IDLE`.
**This is a Phase 2 proposal for Phase 3 to schedule, not a v0.1 requirement** — do not add
it to the `AvatarState` enum until the v1.0 milestone is actually being built, per Phase 1's
own instruction that anything from a later milestone appearing early is "a schedule
failure" (Phase 1 §25.2).

### 3.4 Asset plan — reconciling with Phase 1's D4

Phase 1 (§25.2 Decision D4) leaves open "text glyphs vs. texture atlas from day one,"
recommending the atlas pending R8 (font glyph coverage, §11.4/§25.1 of Phase 1). This
design is built texture-first (the rune-glyph concept in §2 above only works as a small
custom sprite; several of the animations described — rotating inner rune segments, orbit
dot, shake — are not achievable with a single monospace text glyph). **Recommendation:
resolve D4 as "atlas," confirming Phase 1's own leaning**, and treat the vanilla-font-glyph
table from Phase 1 §11.4 (`•`, spinner braille, `»`, `⚙`, `✔`, `✘`, `⊘`) purely as the v0.1
bring-up fallback while the atlas is produced — acceptable because v0.1's exit criterion
(Phase 1 §4) is about threading/cancellation correctness, not visual polish. Ship
`assets/minebro/textures/gui/avatar/avatar_states.png`: one sprite sheet, 16×16 per frame,
laid out as a simple horizontal frame strip per state (max 4 frames per state, per §2.3's
"nothing should reward staring at it" — long animation loops are unnecessary at this
sprite size and cost more texture memory than they're worth), with fixed per-state frame
durations declared in a small `avatar_states.json` sidecar (`{"state":"THINKING",
"frames":3,"frameDurationMs":300}` etc.) — exactly the "simple sprite sheet with fixed
frame duration, no animation DSL" Phase 1 §12.2 specifies.

---

## 4. HUD Placement Specification

### 4.1 Anchor and offset — adopting and detailing Phase 1's default

Phase 1 §11.3 fixes the default as `Anchor.MIDDLE_LEFT`, offset `(6, 0)`, scale `1.0`. This
design keeps that default and specifies it precisely enough to implement without
re-deriving it:

```java
public record AvatarPlacement(Anchor anchor, int offsetX, int offsetY, float scale) {}
// Default: AvatarPlacement(Anchor.MIDDLE_LEFT, 6, 0, 1.0f)
```

- **Anchor point:** the vertical midpoint of the left edge of the scaled GUI viewport —
  `x = 0`, `y = graphics.guiHeight() / 2` in scaled coordinates (Phase 1 §11.3's mandate to
  compute against `guiWidth()`/`guiHeight()`, never window pixels, is load-bearing here).
- **Offset:** `+6px` right, `+0px` vertical, applied to the anchor before drawing — i.e. the
  avatar's **left edge** sits at scaled-x = 6, vertically centered on the viewport's
  midline. 6px mirrors vanilla's own left-edge inset used by the effects-icon column and
  keeps MineBro visually aligned with where Minecraft already puts left-edge HUD chrome.
- **Sprite box:** 16×16 logical px at `scale = 1.0`, so the occupied rectangle at default
  settings is `x:[6,22] y:[guiHeight()/2 - 8, guiHeight()/2 + 8]`.
- **Scale range:** 0.5–3.0 (Phase 1 §12.3). Scaling is anchored at the sprite's own center,
  not its top-left, so increasing scale grows the badge symmetrically around the anchor
  point rather than drifting it downward/rightward — implement via
  `pose().translate(anchorX, anchorY, 0); pose().scale(s, s, 1f); pose().translate(-8, -8,
  0);` (translate to center, scale, then draw the 16×16 sprite from its own origin) so the
  visual center never moves as the player adjusts scale in settings.

### 4.2 Why `MIDDLE_LEFT` and not another anchor — collision analysis

Minecraft's vanilla HUD occupies, at every GUI scale, roughly these fixed regions (in
scaled-viewport-relative terms, not absolute pixels, since they scale together with GUI
scale):

| Region | Occupied by |
|---|---|
| Bottom-center | Hotbar, held-item name popup, XP bar directly above it |
| Bottom-left, ~0–90px from bottom | Health hearts, armor, hunger, air bubbles (stacked directly above the hotbar's left half) |
| Bottom-right | (mirrors bottom-left in some resource packs/mods, otherwise empty in vanilla) |
| Top-left | F3 debug overlay when active; some mods (minimap, coordinates HUD) claim this by convention |
| Top-center | Boss bars, title/subtitle text |
| Top-right | (empty in vanilla; some mods place potion-effect icons here since 1.9, actually **left of hotbar** by default — see note) |
| Left/right edges, vertical middle band | **Empty in vanilla at every GUI scale and every aspect ratio** |

The one caveat worth flagging explicitly: **vanilla status-effect icons render top-right by
default**, not middle-left, so `MIDDLE_LEFT` does not collide with them under default
settings. Some resource packs and HUD mods relocate effect icons to top-left, which is why
Phase 1 also rejected top-left (§11.3) — MineBro's avatar deliberately does not compete for
that region. The vertical-middle band on either edge is unclaimed by every vanilla element
and by the overwhelming majority of popular HUD mods (minimap mods use corners, not
mid-edges), which is the actual reason `MIDDLE_LEFT` was chosen: **it is the one region nothing else wants.**

### 4.3 Behavior across GUI scale 1×–4×

Because all placement math is done in `GuiGraphics`-scaled coordinates (§4.1), the avatar's
position **relative to other HUD elements stays constant automatically** as GUI scale
changes — this is the entire point of computing against `guiWidth()/guiHeight()` rather than
raw window pixels (Phase 1 §11.3). What must be manually verified at each scale (per Phase 1
§22.1 L4's manual test matrix) is not position math but **legibility**: at GUI scale 4× on a
low resolution window, the scaled viewport shrinks and every HUD element (including
MineBro's 16px badge) occupies a larger fraction of visible screen space — this is
correct and expected (it's the same effect vanilla hotbar icons get), not a MineBro-specific
bug, so no special-casing is needed beyond confirming the sprite doesn't get clipped if
`guiHeight()` becomes small enough that `guiHeight()/2 - 8` goes negative (only possible at
extreme GUI scale + tiny window combinations) — clamp the anchor's y to
`max(8, guiHeight()/2)` defensively.

### 4.4 Behavior across aspect ratios (4:3, 16:9, 21:9)

`MIDDLE_LEFT` is aspect-ratio-neutral by construction: it is anchored to the left edge and
vertical center, both of which exist identically regardless of width. The one thing that
changes with aspect ratio is *how far* the avatar sits from the player's likely focus area
(crosshair, screen center) — on 21:9 ultrawide, the avatar sits proportionally closer to the
edge of peripheral vision than on 4:3. This is treated as **acceptable and correct**, not a
bug to compensate for: an avatar that repositioned itself based on aspect ratio would
violate the "configurable position" requirement's spirit (a player who has picked a spot
expects it to stay put) and would add anchor-interpolation complexity for a cosmetic
concern. §11.4 of Phase 1's manual test matrix already covers 4:3/16:9/21:9 explicitly —
the test should assert "no clipping, no overlap with vanilla elements," not "identical
apparent size or distance-from-center."

### 4.5 Screens and hidden-HUD states

Per Phase 1 §11.2's manual guard order, the avatar must not render when `mc.options.hideGui`
is true (F1), when the debug overlay is showing (F3), or when a `Screen` is open and
`config.showAvatarInScreens()` is false (default false — Phase 1 §14.2's default config
sets `avatarShowInScreens: false`). This design adds one refinement: when
`avatarShowInScreens` is enabled by the user, the avatar renders at **60% opacity and 0.8×
scale** while any non-MineBro `Screen` is open (inventory, chat, another mod's GUI), so it
reads clearly as a secondary/background presence and never competes visually with the
foreground screen's own content — full opacity and scale are reserved for gameplay (no
screen open) and for MineBro's own screens (§5, §8, §9), where the avatar appears as a small
badge in the screen's own header rather than the HUD-anchored version (§5.2).

---

## 5. Chat UI Specification

### 5.1 Entry points and exit

Per Phase 1's interaction progression (§11.5): v0.1 is command-only
(`/minebro ask <text>`, output to vanilla chat — no custom screen exists yet, so there is
no "chat UI" to design for v0.1, only chat-log formatting, §5.5). The panel this section
specifies is **`MineBroChatScreen`, targeted at v0.5** per Phase 1's roadmap, opened by
the `MineBroKeybinds` default keybind (`B`, per Phase 1 §11.1) and, once the command bug is
fixed (§11 below), by `/minebro settings`'s sibling entry point or a future dedicated
`/minebro chat` client command. **Click-to-open on the avatar (Phase 1 §11.5 step 3) is
explicitly deprioritized** — Phase 1 flags the underlying mouse-hit-testing-while-no-screen-
is-open problem as a genuine, underestimated constraint, and this design agrees it should
ship last or not at all; the keybind is the primary, always-available entry point and must
never be designed as a fallback to a click interaction that may never ship.

Closing: `Escape` (standard `Screen` behavior, returns to gameplay — Minecraft
automatically un-pauses single-player if `Screen#isPauseScreen()` returns false, which
`MineBroChatScreen` must override to return **false**, since Phase 1 §5 P2 requires the game
never be blocked by MineBro UI — a chat panel that pauses singleplayer would technically
violate that principle even though it doesn't block the *render/tick* loop directly, because
it changes gameplay-visible behavior the player didn't ask for). The same keybind (`B`)
toggles closed if pressed again while the screen is open. `Ctrl+Shift+B` (Phase 1 §16.5's
kill switch) works identically whether or not the chat screen is open.

### 5.2 Layout

A **non-fullscreen, non-centered panel**, anchored to the same side as the HUD avatar (left)
so opening it doesn't require the player's eye to jump across the screen:

```
┌──────────────────────────────────────────┐  ← top-edge accent rule (§1.6), 2px accent.brand
│ [badge] MineBro                      [x] │  ← header, 16lh tall
├──────────────────────────────────────────┤
│                                            │
│  ┌ MineBro ────────────────────────┐      │
│  │ You have 3 iron ingots and       │      │  ← message list, scrollable
│  │ 7 sticks. Missing 2 more iron    │      │
│  │ for a pickaxe.                   │      │
│  └───────────────────────────────────┘     │
│                                            │
│                    ┌─ You ──────────────┐ │
│                    │ can I make an iron  │ │
│                    │ pickaxe?            │ │
│                    └─────────────────────┘ │
│                                            │
├──────────────────────────────────────────┤
│ ▸ Ask MineBro…                      [Send]│  ← EditBox + send button, 16lh
└──────────────────────────────────────────┘
```

Concrete dimensions (scaled/logical px, matching §1.4's `lh` unit):

- Panel width: `260px` fixed (not proportional to window width — a fixed width keeps line
  wrapping predictable and matches vanilla's own fixed-width dialogs like the world-select
  screen's list). Panel height: `min(0.6 * guiHeight(), 220px)`, so it never eats more than
  60% of vertical space and caps out at a sane maximum on tall/ultrawide windows.
- Position: left-anchored, `x = 8px` from the left edge of the viewport, vertically centered
  (`y = (guiHeight() - panelHeight) / 2`) — deliberately close to, but not overlapping, the
  HUD avatar's own position, so the two read as connected without the panel covering the
  avatar it was opened from. When the screen is open the HUD-anchored avatar renders at 60%
  opacity per §4.5; the screen's own header badge (top-left of the panel, 12×12px) is the
  full-opacity, authoritative avatar during this interaction.
- Header: `1lh` padding all sides, avatar badge left-aligned, "MineBro" label in bold
  `text.primary`, close button (`✕`, vanilla-styled small button) right-aligned.
- Message list: fills remaining vertical space above the input row, `4px` internal padding,
  vertically scrollable (mouse wheel + a thin scrollbar in `border.highlight`, matching
  vanilla list widgets), newest message at the bottom, auto-scrolls to bottom on new content
  unless the player has manually scrolled up (in which case a small "↓ new message" pill
  appears at the bottom of the list rather than force-scrolling — a courtesy borrowed from
  every well-behaved chat UI, and one of the few conventions worth borrowing from that
  space).
- Input row: an `EditBox` (Mojang-mapped `net.minecraft.client.gui.components.EditBox`)
  occupying most of the width, a small "Send" `Button` to its right (also submittable via
  Enter — `EditBox` already supports a responder for this). Input row height `16lh`,
  matching the header for visual symmetry.

### 5.3 Message treatment

Player messages: right-aligned, `bg.panel.raised` fill, no author label needed (position
alone communicates authorship, matching the convention every messaging surface already
teaches, which is one of the few borrowings from that vocabulary worth keeping since it
costs nothing and adds real clarity). MineBro messages: left-aligned, `bg.panel.inset` fill
(slightly recessed, since MineBro is "answering into" the space), small avatar badge (8×8,
current `AvatarState` color-tinted) at the top-left corner of the bubble instead of a text
author label — this reinforces the avatar-as-identity concept from §2 rather than
introducing a redundant "MineBro:" text label. Tool-execution step lists (§7) render
**inside** the MineBro message bubble they belong to, above the final text answer, in
`text.secondary` at a slightly smaller effective line spacing (`0.8 * lh` between step
lines) so they read as process, with the final answer visually "settling" below them in
full `text.primary` weight.

System/meta lines (offline notices, permission-level changes, `/minebro clear`
confirmations) render as centered, italic, `text.secondary`, full-width, no bubble — visually
distinct from both conversational parties, matching how vanilla multiplayer chat renders
join/leave messages differently from player chat.

### 5.4 Bubble shapes

No rounded corners (§1.6) — bubbles are simple 9-slice rectangles, `1lh` internal padding,
`border.outer` 1px border, `4px` vertical gap between consecutive messages, `1px` gap
between consecutive messages from the *same* speaker (visually groups a burst of short
MineBro tool-step updates without excessive whitespace).

### 5.5 Vanilla chat-log fallback (v0.1–v0.4, before the screen exists)

Until `MineBroChatScreen` ships, all output goes to the vanilla chat log per Phase 1 §3.2.
This design specifies that formatting now so it doesn't need re-deriving later: every
MineBro chat-log line is prefixed `[MineBro]` in `accent.brand`-equivalent chat color
(`ChatFormatting` doesn't have a true amber, so use `ChatFormatting.GOLD` as the closest
built-in match — **do not** introduce a custom RGB `Style` for chat-log text; vanilla chat
does support `Style.withColor(TextColor.fromRgb(...))`, so a precise amber is possible, but
`GOLD` is recommended for v0.1 for simplicity and immediate visual consistency with other
mods' gold-prefixed log lines), followed by plain-colored body text. Tool-step lines
(§7) in the chat-log fallback render as their own prefixed lines
(`[MineBro] ✓ Checking inventory…`) rather than attempting any grouping vanilla chat can't
represent.

---

## 6. Thinking State

### 6.1 What must be visible, and why (Phase 1 P2 + P4)

Phase 1's P2 ("never block the client thread") and P4 ("fail loudly and specifically") are
UX requirements as much as engineering ones: because a local model can take 5–20 seconds
(Phase 1 §17.2), the thinking state has to carry the player through a real wait without
reading as a freeze. Four things must be visible simultaneously, mapped to the four phases
Phase 1's pipeline (§5.1) actually goes through:

| Phase (Phase 1 §5.1) | Avatar state | Chat-panel / chat-log signal |
|---|---|---|
| Request received, dispatched to worker thread | `THINKING` begins immediately (same tick as submit) | Player's own message appears instantly (§5.3) — it must never wait for a response before rendering, since it's a local echo, not a server round-trip |
| Model reasoning / awaiting provider response | `THINKING` (rotating rune, §3.1) | Subtitle: `"thinking…"` with the ellipsis animation (§3.1); in the chat panel, a lightweight typing-indicator row (three static dots, no bubble) appears at the bottom of the message list |
| Tool execution in progress | `WORKING` (orbit dot, §3.1) | Subtitle: tool-specific text via the translation table (§7); step lines begin appearing in the (eventual) MineBro bubble in real time as each tool resolves |
| Response text arriving | `RESPONDING` | If streaming (Phase 1 §17.2/D8, targeted v0.5): text appends into the bubble token-by-token, no artificial typewriter delay beyond the model's own token arrival rate. If non-streaming: the full answer appears at once when the future completes. |

### 6.2 Cancellation affordance

The kill switch (`Ctrl+Shift+B` or `/minebro stop`, Phase 1 §16.5) must be discoverable
during a long wait, not just documented. While `AvatarState` is `THINKING` or `WORKING`, the
chat panel's send button (§5.2) morphs into a **"Stop" button** (same position, relabeled,
`accent.negative`-tinted border) — pressing it calls the same cancellation path as the
keybind/command. This is a low-cost addition (a `Button#setMessage`/state swap on the
existing widget) that gives players who don't know the keybind an obvious way out of a wait
they've decided not to continue.

### 6.3 Explicit non-freeze guarantee, restated as a UI contract

Because P2 is a hard architectural invariant (Phase 1 §5.4), the *design* obligation is
narrower than it looks: the avatar/chat UI must **never simulate a wait using a blocking
call** — every visual change described above (spinner tick, ellipsis, orbit dot) is driven
by the client render loop's own per-frame delta (already running every frame regardless of
request state) reading the current `AvatarState` and an elapsed-time value, never by a
`Thread.sleep` or a blocking `Future#get()` inside a render or input callback. This is worth
stating explicitly in the design doc because it is the exact place a well-meaning
implementation could accidentally reintroduce a freeze (e.g. calling `.get()` on the
provider future from inside `MineBroChatScreen#render` "just to show a synchronous
loading state").

---

## 7. Tool Execution UI

### 7.1 The translation principle

Per the brief and Phase 1's tool-result contract (§7.2 of Phase 1: `success`, `code`,
`reason`, `data`), the UI must **never render raw JSON, tool names, argument objects, or
`ToolResultCode` enum values** to the player. Every tool call/result pair is translated
through a small, hand-written lookup keyed on `(toolId, phase, code)` into a short present-
or-past-tense phrase. This lookup is a `client`-side concern (a `ToolStepPresenter` or
similar, consuming the already-structured `ToolResult` — never re-parsing text), separate
from and downstream of the model's own final phrased answer.

### 7.2 Step-list rendering

Each tool call in a turn renders as one line, prefixed with a small status glyph that
mirrors the avatar's own state vocabulary rather than inventing a second icon language:

| Phase | Glyph | Color | Example text |
|---|---|---|---|
| Issued (call sent, no result yet) | `⚒` (or the orbit-dot motif from §3.1 rendered inline at text scale) | `accent.brand` | `⚒ Checking inventory…` |
| Resolved, `success: true` | `✓` | `accent.positive` | `✓ Checking inventory — found 3 iron, 7 sticks` |
| Resolved, `success: false`, recoverable code | `!` | the proposed `WARNING` tint (§3.3) if shipped, else `accent.negative` at reduced weight | `! Missing ingredients — need 2 more iron` |
| Resolved, `success: false`, denied/refused | `✕` | `accent.negative` | `✕ Permission denied — breaking blocks isn't allowed yet` |

Lines update in place (issued → resolved) rather than appending a second line, so a
multi-tool turn reads as a short, live-updating checklist rather than a growing log — this
directly implements the brief's example (`✓ Checking inventory`, `✓ Found 3 iron`,
`⚒ Crafting...`, `✓ Done`).

### 7.3 MVP-realistic examples vs. later-roadmap examples — explicit flagging

Per this brief's instruction to flag which concrete examples are realistic now versus
later, mapped against Phase 1's tool table (§7.4 of Phase 1):

**MVP-realistic (v0.2–v0.3, read-only tools, ship first):**
- `⚒ Checking your inventory…` → `✓ Checking inventory — 34 oak log, 3 iron ingot, 7 stick`
  (`get_inventory`)
- `⚒ Checking your status…` → `✓ Health 18/20, hunger 14/20` (`get_player_status`)
- `⚒ Looking around…` → `✓ Found a crafting table nearby` (`get_nearby_blocks`,
  `get_nearby_entities` — remember these are radius-capped and, per Phase 1 §19.3,
  disabled or LOS-gated on remote servers; the step list should say
  `! Can't check surroundings — disabled on this server` when that gate fires, using the
  same `code`-driven translation, never a silent omission)
- `⚒ Looking up the recipe…` → `✓ Iron Pickaxe needs 3 iron ingot, 2 stick` (`get_recipe`)
- `⚒ Checking if you can craft that…` → `✓ Yes — you have everything` /
  `! Missing 2 iron ingot` (`check_can_craft`)

**v0.4 (first mutating tools, `SAFE_ACTIONS`) — design the pattern now, not realistic
until v0.4 ships:**
- `⚒ Crafting Iron Pickaxe…` → `✓ Crafted 1 Iron Pickaxe` (`craft_item`) — per Phase 1
  §7.5, this may also resolve to `! No crafting table in reach` etc.
- `⚒ Eating…` → `✓ Ate 1 Cooked Beef, restored 4 hunger` (`eat_food`)

**v0.6+ (`GAMEPLAY_ACTIONS`/`DESTRUCTIVE_ACTIONS`) — pattern only, do not build UI copy
for these before the tools themselves exist, and every one of these additionally routes
through the confirmation UI (§8) before the step list ever shows "issued":**
- `⚒ Opening the chest…` (`open_container`)
- `⚒ Placing torch…` (`place_block`)
- `⚒ Breaking 14 blocks…` (`break_block` — this is the brief's own example, and it is
  explicitly a v0.6 feature per Phase 1, not MVP)
- `⚒ Attacking…` (`attack`)

**Never build UI copy for:** `move_to` — cut entirely from Phase 1's roadmap (§3.3 of
Phase 1); no step-list phrase should exist for it, including as a "coming soon" placeholder,
because Phase 1's position is that it may never be built at all.

### 7.4 Failure presentation

When `LoopLimitExceeded` fires (Phase 1 §7.6's iteration/wall-clock caps), the step list
does not disappear — it stays visible exactly as accumulated, with a final system-styled
line appended: *"I got stuck going in circles — here's what I found so far."* (Phase 1
§7.6's own specified copy). This matters because it turns a failure into partial value
instead of a blank screen, consistent with P4.

---

## 8. Confirmation UI

### 8.1 Screen, not modal — the load-bearing constraint

Per Phase 1 §16.4 and this brief's instruction, `ConfirmActionScreen` is **a second,
ordinary `Screen` subclass**, not a blocking dialog. Concretely: `ActionAuthority#request`
(Phase 1 §16.4) returns a `CompletableFuture<AuthDecision>` immediately; the worker/agent
loop `await`s that future asynchronously (it is already off the client thread — Phase 1
§5.4) while `ScreenActionAuthority` opens `ConfirmActionScreen` via
`Minecraft.getInstance().setScreen(...)` on the client thread and completes the future only
from that screen's own button callbacks. **The game keeps rendering and ticking normally
the entire time the confirmation screen is open** — the player can even press Escape to
close it (treated as `DENY`, per the timeout-equivalent path) and keep playing, and the
30-second timeout (Phase 1 §16.4) counts down in real time via the same per-frame delta
mechanism as §6.3, not a blocking wait.

### 8.2 Layout

```
┌──────────────────────────────────────────┐
│ [badge] MineBro wants to:                │  ← title, bold
├──────────────────────────────────────────┤
│  Break 14 blocks                          │  ← resolved action summary (bold, larger)
│                                            │
│  minecraft:oak_log × 8                    │  ← concrete effect list (Phase 1 §15.3:
│  minecraft:stone × 6                      │     "the concrete resolved effect,
│                                            │      not the tool name")
│  at (128, 64, -344) … (131, 64, -341)     │
│                                            │
│  ⏱ 24s to decide                          │  ← live countdown, decays to auto-DENY
├──────────────────────────────────────────┤
│  [ Allow ]  [ Allow for session ]  [ Deny ]│
└──────────────────────────────────────────┘
```

- Same panel treatment as §1.6/§5 (vanilla 9-slice, `accent.brand` top rule) — visually a
  sibling of the chat panel, not a separate visual system, so the player recognizes it as
  "MineBro" even at a glance.
- Title always follows the exact pattern the brief specifies: *"MineBro wants to
  [action]"* — never the raw tool id.
- The effect list is **always the resolved, concrete data** from `ActionRequest#details`
  (Phase 1 §16.4) — item names and counts, coordinates, entity names — never a
  restatement of the tool's arguments as sent to the model. This is a direct implementation
  of Phase 1 §15.3's security posture: the confirmation's value depends entirely on showing
  the *real* effect, since that's what makes a prompt-injection-induced call harmless even
  if it reaches this screen.
- Three buttons, vanilla `Button` styling: **Allow** (single use), **Allow for session**
  (Phase 1 §16.4's `ALLOW_FOR_SESSION`, remembered per `(tool, argument-shape)` until world
  unload — labeled exactly this way so the player understands the scope of what they're
  granting), **Deny** (default-focused button, so pressing Enter without moving the mouse
  denies rather than allows — a deliberate safety default for a screen whose entire purpose
  is gating destructive actions).
- Countdown text ticks every second, switches to `accent.negative` in its final 5 seconds,
  and auto-resolves to `DENY` at zero (Phase 1 §16.4's `TIMED_OUT` → `DENY` mapping) —
  closing the screen itself when it does, returning the player to gameplay rather than
  leaving a stale dialog on screen.

### 8.3 Always-confirm cases get no "skip" affordance

Per Phase 1 §16.3, some cases (protected blocks — chests, spawners, beds, etc. — and any
`break_block` call on ≥5 blocks) **always** show this screen regardless of the
`confirmDestructive` setting. The design must not offer a "don't ask again" checkbox on
this screen for those cases — only `ALLOW_FOR_SESSION`'s narrower per-argument-shape memory
is available, and even that should be visually absent (button not rendered, not
greyed-out) when Phase 1's rule set marks the action as always-confirm, so the UI doesn't
imply an option that the permission gate would silently reject anyway.

---

## 9. Provider Settings UI

### 9.1 Screen structure

`MineBroConfigScreen` (Phase 1 §14.5, v0.5), hand-rolled `Screen` with the tab set Phase 1
specifies: **Provider**, **Generation**, **Tools & Permissions**, **Interface**, **Memory**.
This section details the **Provider** tab, since it's the one the brief calls out
explicitly; the others follow the same panel/typography/spacing system already defined
(§1) and don't need bespoke treatment.

### 9.2 Provider tab layout

```
┌──────────────────────────────────────────┐
│ [badge] MineBro Settings   Provider ▾ ... │  ← tab strip
├──────────────────────────────────────────┤
│  ( ) Ollama (local)                       │  ← radio list, one row per
│  (•) LM Studio / local server (OpenAI-    │     Phase 1 §6.4 adapter
│      compatible)                          │
│  ( ) OpenAI-compatible (custom endpoint)  │
│  ( ) Anthropic                            │
│  ( ) Google Gemini                        │
├──────────────────────────────────────────┤
│  Endpoint:  [http://localhost:1234/v1  ]  │
│  Model:     [local-model            ▾]  [Test connection]│
│  API Key:   [•••••••••••••••4f2a    ]  [Change]         │
│                                            │
│  Status: ● Connected — model responds     │  ← live health indicator
└──────────────────────────────────────────┘
```

Radio rows map **exactly** to Phase 1's adapter set (§6.4/§18.1): `OllamaProvider`,
`OpenAiCompatibleProvider` (covering LM Studio, llama.cpp, vLLM, and OpenAI itself — one
row, with the endpoint field defaulting per a small preset dropdown next to it rather than
five separate radio rows for what is architecturally one adapter — Phase 1 is explicit
that these differ only in base URL/auth/model id), `AnthropicProvider`, `GeminiProvider`.
**`EchoProvider` and `BridgeProvider` never appear in this list** — they are test/reserved
adapters per Phase 1 §6.4 and have no end-user-facing settings row, ever.

### 9.3 Field behavior

- **Endpoint** field: plain `EditBox`, pre-filled with the provider's documented default
  (`http://localhost:11434` for Ollama, `http://localhost:1234/v1` for the LM Studio
  preset, etc.), editable for any custom OpenAI-compatible server.
- **Model** field: a combo affordance — `EditBox` for manual entry plus a dropdown populated
  from `AIProvider#listModels()` (Phase 1 §6.2) when the provider supports it, refreshed by
  the same "Test connection" action rather than auto-polling on every keystroke.
- **API Key field — never plaintext by default.** Rendered as a **masked `EditBox`**
  showing only the last 4 characters (`sk-…4f2a`, matching Phase 1 §14.3's exact masking
  spec) with the rest replaced by `•`. A **"Change"** button (not an inline unmask toggle)
  clears the field for fresh input — there is deliberately no "show password" eye-icon
  toggle, because Phase 1 §14.3 states keys are never copied to the clipboard or displayed
  in full by the settings UI, and an unmask toggle invites exactly that exposure (e.g. in a
  screen-recording or a screenshot shared for a bug report). Typing a new key shows it in
  plaintext *while actively typing* (unavoidable with a standard `EditBox` and not a real
  exposure risk since it's the player's own live input), then re-masks to the `sk-…xxxx`
  form on focus-loss/save.
- **"Test connection"** button: calls `AIProvider#health()` (Phase 1 §6.2), shows a spinner
  state (borrowing the `THINKING` glyph at small scale) while pending, then updates the
  **Status** line below with a colored dot (`accent.positive` connected /
  `accent.negative` error, text detail e.g. "Connected — model responds" or
  "Unreachable — connection refused at :1234") — never a popup/toast, consistent with the
  brief's "no giant notifications" instruction (§10 below) applying here too.
- Missing-credential state (Phase 1 §14.3's `MISSING_CREDENTIALS`): the API Key field gets
  a 1px `accent.negative` outline and an inline caption below it
  ("No API key found — enter one, or set `MINEBRO_ANTHROPIC_API_KEY`"), never a blocking
  alert.

### 9.4 Consumer-subscription dead end (Phase 1 §6.5)

If a future free-text "connect my ChatGPT/Claude account" affordance is ever proposed for
this screen, it must not be built — Phase 1 §6.5 is explicit that only documented HTTP
APIs with a user-supplied key, or official local runtimes, are supported. This settings
screen accordingly has no "sign in with [vendor]" button, ever, and no field that could be
mistaken for a session-cookie paste target.

---

## 10. Connection Status UI

### 10.1 The avatar is the entire notification surface

Per the brief's "without giant notifications" requirement and Phase 1's P4 (fail loudly and
*specifically*, but never intrusively-loudly), MineBro has **no toast/banner notification
system at all** for connection state. `OFFLINE` (§3.1) already exists as an avatar state
precisely so a separate notification mechanism is unnecessary. The full status vocabulary:

| Player-facing state | Avatar rendering | Where full detail lives |
|---|---|---|
| Online | `IDLE` amber, normal glow | — |
| Thinking (mid-request) | `THINKING`/`WORKING` (§3.1) | subtitle text on hover |
| Offline (provider unreachable) | `OFFLINE`, dimmed/desaturated (§3.1) | one line in vanilla chat the *first* time it happens per session (Phase 1 §MVP-criterion-1's example: `"MineBro is offline — can't reach Ollama at http://localhost:11434"`), then silence — the dimmed avatar itself is the ongoing indicator, it does not re-announce every time a command is attempted while still offline |
| Provider error (auth failed, bad model, etc.) | `ERROR` red-shake, then holds red slightly longer than a normal `ERROR` decay is not warranted here — **use the standard 4000ms decay** (Phase 1 §12.1) even for provider errors, then return to `OFFLINE` styling if the provider is still unreachable, not back to `IDLE` | `/minebro status` (deterministic, no LLM call, per Phase 1 §13.2) is the canonical place for full diagnostic detail — latency, token usage, tool-calling mode, current provider/model |

### 10.2 Hover/subtitle as the "more detail on demand" mechanism

Every non-`IDLE` state carries an optional subtitle (Phase 1 §12.1) shown when the player's
cursor rests over the avatar for >400ms (a deliberate small delay so it doesn't fire from
incidental cursor movement during normal play) — rendered as a small single-line tooltip in
vanilla's own tooltip style (dark background, `border.outer` border, no avatar-specific
styling needed here since vanilla's tooltip chrome is already exactly the right register).
This is the *only* place additional text appears uninvited, and even then only on a
deliberate hover, keeping the "no giant notifications" promise intact while still giving
curious players a path to more information than the glyph alone conveys.

---

## 11. Command UX

### 11.1 The client-command bug, and why it's a UI-design concern

Phase 1 §13.1 identifies that `/minebro` is currently registered via
`CommandRegistrationCallback` (server-side, confirmed in
`src/main/java/com/minebro/MineBro.java`'s current `onInitialize()`), which means it does
not exist at all when the player is on a vanilla remote server — directly contradicting the
"client-side companion" product framing. This matters for this design document specifically
because **`/minebro settings` is meant to open a client-only `Screen`
(`MineBroConfigScreen`)** — a `Screen` can only be opened via `Minecraft.getInstance().
setScreen(...)`, which is only reachable from client-side code. A command executed via
`CommandSourceStack` (the server-side path) has no access to `Minecraft`/`Screen` at all —
so **the current bug isn't just a multiplayer-availability defect, it is a structural
blocker for ever wiring `/minebro settings` up correctly**, independent of any UI decision
in this document. Phase 1 D2 already recommends fixing this in v0.1; this document adds
the concrete reason the UI layer specifically depends on that fix: every command in the
tree below that opens a screen (`settings`) or drives purely-local state (`stop`, `ask`,
the read-only data commands) must be a `ClientCommandRegistrationCallback` registration
(`src/client/java/com/minebro/client/command/MineBroClientCommands.java`, per Phase 1
§13.1's own sketch), full stop — there is no UI design that works around a server-registered
command needing to open a client `Screen`.

### 11.2 Command UX table (brief's command list, cross-referenced to Phase 1 §13.2)

| Command | UX behavior | LLM involved? | Version |
|---|---|---|---|
| `/minebro` | Bare invocation prints a one-line status summary directly to chat (provider, model, health dot, permission level) — deliberately the *same* kind of terse, data-first output as `/minebro status`, so a player who forgets the subcommand still gets something useful rather than a "did you mean" error | No | v0.1 (once migrated to client-side) |
| `/minebro ask <question>` | Primary entry point pre-v0.5; after the chat screen ships, still valid as a fast one-shot path for players who don't want to open a panel. Output goes to vanilla chat with the `[MineBro]` prefix (§5.5) pre-v0.5, and additionally appends into `MineBroChatScreen`'s history once it exists, so switching between command-driven and panel-driven use is seamless | Yes | v0.1 |
| `/minebro inventory` | Instant, deterministic, formatted item list straight to chat — **never** shows a `THINKING` avatar state, since no model call happens (Phase 1 §13.2's explicit "just show me the data" path) | No | v0.3 |
| `/minebro recipe <item>` | Same treatment as `inventory` — instant, deterministic recipe printout | No | v0.3 |
| `/minebro craft <item> [qty]` | Direct tool invocation bypassing the model (Phase 1 §13.2) — this still goes through the **full confirmation/permission gate** (§8) if the resolved action requires it; "bypassing the model" means skipping the LLM's tool-selection step, not skipping validation | No (direct tool call) | v0.4 |
| `/minebro status` | Deterministic diagnostic dump — provider, model, health, latency percentiles, tool-calling mode, token usage this session (Phase 1 §21.2) — the canonical destination for "what's actually going on," referenced from §10.1 above | No | v0.1 |
| `/minebro model` / `/minebro model <name>` | Lists or switches models; when switching, plays a brief `WORKING` avatar pulse while `listModels()`/validation resolves, then confirms in chat | No | v0.2 |
| `/minebro settings` | Opens `MineBroConfigScreen` (§9) — **requires the client-command fix (§11.1)** to function at all | No | v0.5 |
| `/minebro stop` | Immediate: cancels in-flight request, closes any open `ConfirmActionScreen` with `DENY`, avatar snaps to `IDLE` within the 250ms bound Phase 1's acceptance criteria require (§3.4 of Phase 1) — no confirmation of its own, ever, since it is itself the panic button | No | v0.1 |

### 11.3 Natural-language routing UX note

Phase 1 §13.3's realism table is authoritative for *which* utterances are answerable at
which version; this design's only addition is presentational: when a player asks something
in `/minebro ask` or the chat panel that Phase 1 has explicitly placed in v2+ ("build me a
starter house," "get me enough wood for a house"), the response should not attempt a
degraded/partial answer that implies the feature half-exists — it should give the honest,
in-character refusal Phase 1's vision section implies ("that's beyond what I can do right
now — I can tell you what a house needs, but I can't gather or build it"), styled as a
normal `RESPONDING`/`SUCCESS` turn, not an `ERROR` — declining a request is not a failure
state, and should not look like one.

---

## 12. Accessibility Specification

### 12.1 Color blindness

No state anywhere in this system is communicated by color alone:

- Avatar states pair color with **shape change** (the inner glyph's rune-rotation vs.
  orbit-dot vs. shake vs. checkmark-inset are all distinguishable in greyscale) and with
  **redundant text** (the subtitle/hover tooltip, §10.2).
- Tool-step glyphs (§7.2) pair color with **distinct symbols** (`⚒`/`✓`/`!`/`✕`), not
  color-only dots.
- The confirmation screen's countdown (§8.2) pairs its late-stage red tint with the numeric
  countdown text itself, which remains legible regardless of color perception.
- Provider status dots (§9.3, §10.1) are accompanied by text labels ("Connected",
  "Unreachable") in the same breath, never a bare colored dot.

### 12.2 Contrast

All body text (`text.primary` `#E7E1D6` on `bg.panel.dark` `#2B2A26`) targets a contrast
ratio comfortably above WCAG AA's 4.5:1 for normal text (this pairing computes to roughly
11:1). `text.secondary` on the same background (`#B4AC9C` on `#2B2A26`) computes to
roughly 6.8:1, still comfortably AA-compliant for the smaller/secondary text it's used for.
Any future palette adjustment must re-check against these two pairings before shipping,
since they're the two most frequently rendered text/background combinations in the whole
system.

### 12.3 Redundant signaling for the thinking state specifically

Because `THINKING`'s primary signal is an animated rotation (§3.1), which relies on motion
perception, it is always paired with the plain-text ellipsis animation (`.`/`..`/`...`)
described in §3.1 and, in the chat panel, the static three-dot typing-indicator row (§6.1) —
so a player who has reduced-motion settings active (§12.5) or who simply glances at a still
frame still gets the "still working" signal from the text state, not just the animation.

### 12.4 Keyboard navigation

`MineBroChatScreen`, `MineBroConfigScreen`, and `ConfirmActionScreen` are all standard
`Screen` subclasses and must support the platform's existing `Tab`/`Shift+Tab` focus
traversal between widgets (`Screen#getFocused`, widget `nextFocusPath` handling that
`AbstractWidget`/`EditBox`/`Button` already provide) — this is close to free if the screens
are built from vanilla widget classes rather than fully custom-drawn controls, which is
itself a reason to prefer `EditBox`/`Button`/vanilla list widgets over bespoke click-region
code wherever possible (§15 reinforces this from the implementation side). Concretely:
- `MineBroChatScreen`: `Tab` cycles input box → send/stop button → message list (for
  scroll-by-keyboard) → close button.
- `ConfirmActionScreen`: `Tab` cycles Allow → Allow-for-session → Deny, with **Deny
  pre-focused on open** (§8.2's safety default), and `Enter`/`Space` activates the focused
  button per vanilla `Button` behavior — no custom key handling needed.
- `MineBroConfigScreen`: standard tab-strip navigation plus in-tab widget traversal;
  arrow keys move within a radio group (Provider tab's provider list) per vanilla radio/list
  conventions.

### 12.5 Reduced motion

A single settings toggle, `reducedMotion` (proposed addition to Phase 1's `ui` config
block, §14.2 of Phase 1 — a natural sibling of the existing `avatarShowSubtitle` flag), when
enabled: disables the idle "candle flicker" (§3.1) entirely (avatar holds a static glow),
replaces `THINKING`'s rotating-segment animation with a static rune plus the text ellipsis
only, replaces `WORKING`'s orbiting dot with a static dot at a fixed position that simply
changes brightness, and removes `ERROR`'s shake (color change and hold remain — the
*information* still needs to reach the player, only the motion is suppressed). All
crossfades (state-to-state color transitions) remain, since a crossfade is a low-vestibular-
impact transition type, not the kind of motion reduced-motion settings are meant to
suppress — only looping/oscillating/translating motion is gated by this flag.

### 12.6 Scale and GUI-scale interaction

Covered in depth in §4 and §14; the accessibility-specific note is that **avatar scale
(0.5–3.0, Phase 1 §12.3) is independent of Minecraft's own GUI scale setting** — a player
who needs a larger GUI scale for readability and *also* wants a larger avatar can set both
without one fighting the other, since avatar scale is applied as an additional transform on
top of the already-GUI-scaled coordinate space (§4.1), not as a replacement for it.

### 12.7 Localization

All player-facing strings in this entire spec — tool-step phrases (§7), confirmation titles
(§8), settings labels (§9), status subtitles (§3, §10) — are `Component.translatable(...)`
keys resolved through `assets/minebro/lang/en_us.json` (Phase 1 §23's resource layout),
**never hardcoded literals in the renderer**, including the tool-step translation table in
§7.1, which should be structured as translation keys parameterized with the already-
localized item display names (`ItemStack#getHoverName()`, Phase 1 §8.2) rather than raw
item ids — e.g. `minebro.tool.get_inventory.result` → `"found %s"` with the item summary
substituted in, so the *sentence* localizes even though the underlying item ids driving it
do not need to.

---

## 13. Animation Specification

### 13.1 Global principles

- **No overshoot easing anywhere** (`ChatFormatting`-adjacent motion should never use
  `back`/`elastic`/`bounce` curves) — per §2.3's "not childish" mandate, every motion in
  this system uses one of exactly three easing families: `linear` (mechanical states —
  `WORKING`'s orbit, countdown timers), `sine` in/out (organic/ambient states — `IDLE`'s
  flicker, crossfades), or a plain stepped/discrete transition (`THINKING`'s rune rotation,
  deliberately *not* smoothed, to read as "processing," not "spinning").
- **Minimum perceptible transition: 150ms.** Anything shorter reads as a glitch at typical
  frame rates rather than an intentional change; anything used for a *hold* (not a
  transition) has no minimum.
- **Maximum ambient loop length: 1.5s per cycle** for any *idle-adjacent* animation — long
  loops invite staring, which contradicts "not annoying" and "concise."
- **One-shot vs. loop is a deliberate per-state choice, not incidental** — states that
  represent an ongoing process (`THINKING`, `WORKING`, `IDLE`'s ambient presence) loop;
  states that represent a completed event (`RESPONDING`'s entry flash, `SUCCESS`,
  `ERROR`'s shake) are one-shot, decaying to a hold, then crossfading onward. This
  distinction is what makes the state machine legible without reading any text — a looping
  animation always means "still happening," a settled/held state always means "this already
  happened."

### 13.2 Full timing table

| Animation | Type | Duration | Easing | Notes |
|---|---|---|---|---|
| Idle flicker | Loop (irregular interval) | 120ms pulse, 8–12s interval (randomized per-cycle to avoid a mechanical-feeling metronome) | `sine` in-out | §3.1 |
| Thinking rune rotation | Loop | 900ms/cycle (3×300ms steps) | stepped, no interpolation between steps | §3.1 |
| Thinking ellipsis (text) | Loop | 1200ms/cycle (3×400ms) | none (discrete text swap) | §3.1, §12.3 |
| Working orbit dot | Loop | 700ms/cycle | `linear` | §3.1 |
| Responding entry flash | One-shot | 250ms | `ease-out` | §3.1 |
| Success crossfade in | One-shot | 200ms | `ease-out` | §3.1 |
| Success hold | Hold | 2000ms | n/a | fixed, per Phase 1 §12.1 |
| Success crossfade out (→idle) | One-shot | 300ms | `ease-in` | §3.1 |
| Error shake | One-shot | 150ms (3×50ms cycles) | `linear` | fires once only, §3.1 |
| Error hold | Hold | 4000ms | n/a | fixed, per Phase 1 §12.1 |
| Error crossfade out | One-shot | 300ms | `ease-in` | §3.1 |
| Offline fade-in | One-shot | 400ms | `ease-out` | §3.1 |
| Listening keystroke glow-tick | One-shot, retriggerable, rate-capped | 80ms, min 150ms between triggers | `ease-out` | §3.2 |
| Warning hold (proposed, v1.0) | Hold | 3000ms | n/a | §3.3 |
| Warning crossfade out | One-shot | 300ms | `ease-in` | §3.3 |
| Confirmation countdown final-5s color shift | One-shot per second-tick | instantaneous text recolor, no tween | n/a | §8.2 |
| Chat panel open/close | One-shot | 120ms | `ease-out` (open) / `ease-in` (close) | simple vertical slide-in from the avatar's HUD position, reinforcing that the panel *is* the avatar, expanded |
| Confirmation screen open | One-shot | 100ms | `ease-out` | slightly faster than the chat panel's open, since it's an interrupt the player should register quickly |

### 13.3 Rate limiting

Per Phase 1 §12.1, `AvatarStateSink` transitions are rate-limited to one per 50ms so a fast
tool loop doesn't strobe the avatar. This design's UI-side complement: the **chat panel's
step-list lines** (§7.2) apply the same discipline — if two tool calls resolve within
50ms of each other, their step-line updates are coalesced into the same render frame rather
than triggering two separate crossfade-in animations back to back, which would otherwise
look like flickering text.

---

## 14. Responsive / Scaling Behavior

### 14.1 GUI scale 1×–4× — summary (detailed collision analysis in §4)

All layout math throughout this document (HUD avatar, chat panel, confirmation screen,
settings screen) is expressed in `GuiGraphics`-scaled coordinates, which means GUI scale
changes are handled **automatically and correctly by construction** — nothing in this
design requires scale-specific branching logic for *position*. What does need explicit
handling per scale tier:

| GUI scale | Consideration |
|---|---|
| 1× (or "Auto" resolving to 1×) on very high resolutions | Panels at fixed logical widths (chat: 260px, §5.2) will occupy a small fraction of the screen — acceptable and matches how small vanilla dialogs (e.g. the "Save & Quit" confirm) also stay fixed-width rather than stretching to fill an ultra-wide window. |
| 2× (common default) | Baseline design target — all pixel values in this document were chosen assuming this tier reads comfortably. |
| 3×–4× on smaller windows | Fixed-width panels may approach or exceed available width on small windows at high GUI scale. `MineBroChatScreen`/`MineBroConfigScreen` must clamp their own width to `min(260px, guiWidth() - 16px)` and re-flow message-bubble text wrapping accordingly (`Font#split`/`StringSplitter`, standard vanilla text-wrapping utilities) rather than clipping or overflowing. |

### 14.2 Ultrawide (21:9) and narrow (4:3) aspect ratios

Because every anchor in this system is edge/corner-relative (§4.2, §5.2's left-anchor,
§9's tab-strip screen which vanilla already centers appropriately), no special-casing is
needed for extreme aspect ratios beyond the width-clamping in §14.1 — a 21:9 window simply
has more empty space to the right of a left-anchored panel, which is correct and expected,
not a gap to be filled. The one thing worth testing explicitly (and already called out in
Phase 1 §22.1 L4's manual matrix) is that the confirmation screen (§8), which vanilla-style
screens often center by default, is deliberately **not centered** in this design — it uses
the same left-anchored treatment as the chat panel (§5.2) for visual consistency with "this
is MineBro talking to you from its usual spot," rather than vanilla's typical full-center
dialog placement. This is a conscious deviation from vanilla dialog convention, justified by
brand consistency (§1.6's identity system) outweighing the minor convention mismatch.

### 14.3 Minimum viewport handling

Below a practical minimum window size (Minecraft's own minimum window dimensions already
constrain this somewhat), if `guiWidth() - 16px` would produce a chat panel narrower than
roughly 160px (a width too narrow to comfortably wrap most sentences), the design falls
back to the vanilla vertical chat-log output (§5.5) even in versions where
`MineBroChatScreen` exists — i.e. the screen should refuse to open (with a chat-log message
explaining why, e.g. *"Window too small for the chat panel — try /minebro ask instead"*)
rather than render a broken, illegibly-narrow panel. This is an edge case unlikely to be hit
in practice but costs little to guard against explicitly.

---

## 15. Exact Implementation Guidance for Fabric HUD Rendering / Screens

This section is written for the engineer implementing Phase 3. Every class/method name is
Mojang-mapped. Every point of genuine uncertainty against the currently-resolved Fabric API
surface is marked **VERIFY:**, matching Phase 1's own R1/R3/R4/R6 research items rather than
asserting confidently past what's actually known.

### 15.1 Registering the HUD render layer

Phase 1 §11.2/§25.1-R1 already flags this as the highest-priority open research item and
gives two candidate shapes. This design's guidance: **build against an internal
`AvatarHudRenderer.render(GuiGraphics graphics, DeltaTracker delta)` method that is called
by whichever registration API is actually present**, so the choice of registration API
doesn't ripple through the rest of the avatar code.

**Shape A — `HudRenderCallback` (older, broadly available):**

```java
// src/client/java/com/minebro/client/hud/MineBroHud.java
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;

public final class MineBroHud {
    public static void register() {
        HudRenderCallback.EVENT.register(MineBroHud::onHudRender);
    }

    private static void onHudRender(GuiGraphics graphics, DeltaTracker delta) {
        // Manual guards, in this exact order (Phase 1 §11.2):
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui) return;
        // VERIFY: exact accessor name for "is F3 debug screen showing" in this
        // Fabric/Minecraft build — Phase 1 flagged this as unconfirmed (§25.1-R1).
        // Likely mc.getDebugOverlay().showDebugScreen() or an equivalent on
        // Minecraft/DebugScreenOverlay; confirm against decompiled sources before coding.
        if (mc.getDebugOverlay().showDebugScreen()) return;
        if (mc.screen != null && !MineBroClient.config().ui().avatarShowInScreens()) return;
        if (mc.player == null || mc.level == null) return;

        AvatarHudRenderer.render(graphics, delta);
    }
}
```

**Shape B — layered HUD API (`HudElementRegistry`/`HudLayerRegistrationCallback`,
preferred if present, per Phase 1's own recommendation):**

```java
// VERIFY: exact package/class names for this Fabric API build — Phase 1 R1 flags this
// as unconfirmed for 0.116.15+1.21.1. Likely
// net.fabricmc.fabric.api.client.rendering.v1.HudElementRegistry /
// net.fabricmc.fabric.api.client.rendering.v1.HudLayerRegistrationCallback, with a
// registration call shaped roughly like:
HudElementRegistry.attachElementAfter(
    VanillaHudElements.HOTBAR,                  // VERIFY: exact vanilla layer identifier
    ResourceLocation.fromNamespaceAndPath("minebro", "avatar"),
    MineBroHud::onHudRender                     // same guarded render method as Shape A
);
```

**Recommendation:** attempt Shape B first during the Phase 3 spike (it composes correctly
with other HUD mods and inherits vanilla's hide-HUD handling per Phase 1's own reasoning);
fall back to Shape A only if the layered API is confirmed absent from this exact resolved
Fabric API version. Either way, `AvatarHudRenderer.render(...)` and the guard block are
identical, so the spike's only real decision is which registration call wraps them.

### 15.2 The avatar as a small, self-contained widget/element — not a full `AbstractWidget`

The HUD-anchored avatar is **not** built as an `AbstractWidget`/`Renderable` inside a
`Screen` — it renders every frame regardless of whether any `Screen` is open, which is
exactly what `HudRenderCallback`/the layered HUD API are for, and is architecturally
distinct from the *in-screen* avatar badge used in the chat/confirmation/settings screens
(§5.2, §8.2, §9.2), which *is* a small custom `Renderable`-implementing class embedded in
those screens' widget lists. Recommend two thin classes sharing one drawing routine:

```java
// Shared drawing logic, callable from both contexts:
public final class AvatarSprite {
    public static void draw(GuiGraphics graphics, int x, int y, float scale,
                             AvatarState state, float animT, boolean listening) {
        // pushPose / translate-to-center / scale / draw sprite frame / popPose,
        // per §4.1's centered-scaling technique.
    }
}

// Context 1 — HUD-anchored, driven by AvatarHudRenderer (see §15.1):
public final class AvatarHudRenderer {
    public static void render(GuiGraphics graphics, DeltaTracker delta) {
        AvatarPlacement p = MineBroClient.config().ui().avatarPlacement();
        int x = resolveAnchorX(graphics, p);   // §4.1 math
        int y = resolveAnchorY(graphics, p);
        AvatarSprite.draw(graphics, x, y, p.scale(),
                           HudAvatarController.currentState(),
                           HudAvatarController.animationTime(delta),
                           false);
    }
}

// Context 2 — embedded in a Screen's own widget list (chat/settings/confirm headers):
public final class AvatarBadgeWidget extends AbstractWidget {
    @Override protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        AvatarSprite.draw(graphics, getX(), getY(), 0.75f,
                           HudAvatarController.currentState(),
                           HudAvatarController.animationTime(partialTick),
                           MineBroChatScreen.isInputFocused());
    }
    // narrationMessage / updateWidgetNarration: return a translatable description of
    // current state for screen-reader support, e.g. "MineBro: thinking".
}
```

`HudAvatarController` (Phase 1 §5.5/§11.1's `AvatarStateSink` implementation) owns the
state machine and an internal animation clock (elapsed-time-since-last-transition,
per-state); both rendering contexts read from it but never mutate it, keeping "who owns
avatar state" unambiguous.

### 15.3 `MineBroChatScreen` — structure

```java
public final class MineBroChatScreen extends Screen {
    private EditBox input;
    private Button sendOrStopButton;
    private MessageListWidget messageList;   // custom, extends
                                              // net.minecraft.client.gui.components.
                                              // ObjectSelectionList<MessageEntry> or a
                                              // hand-rolled scrollable panel — VERIFY:
                                              // whether ObjectSelectionList's row-selection
                                              // semantics (it's built for selectable rows,
                                              // not free-flowing chat bubbles of varying
                                              // height) is a good fit before committing;
                                              // a hand-rolled scroll container using
                                              // GuiGraphics#enableScissor is the safer
                                              // fallback if row-height variability makes
                                              // ObjectSelectionList awkward.

    public MineBroChatScreen() { super(Component.translatable("minebro.chat.title")); }

    @Override public boolean isPauseScreen() { return false; }  // §5.1 — never pause solo play

    @Override protected void init() {
        int panelWidth = Math.min(260, this.width - 16);
        int panelHeight = Math.min((int) (this.height * 0.6), 220);
        int panelX = 8;
        int panelY = (this.height - panelHeight) / 2;

        this.messageList = addRenderableWidget(new MessageListWidget(
                this.minecraft, panelWidth - 8, panelHeight - 32 /* header+input rows */,
                panelY + 20, 14 /* row-ish spacing hint */));

        this.input = addRenderableWidget(new EditBox(this.font,
                panelX + 4, panelY + panelHeight - 16, panelWidth - 40, 14,
                Component.translatable("minebro.chat.input")));
        this.input.setResponder(text -> {});   // no live validation needed
        this.setInitialFocus(this.input);       // chat opens with input focused — this IS
                                                 // the "LISTENING" trigger from §3.2

        this.sendOrStopButton = addRenderableWidget(Button.builder(
                Component.translatable("minebro.chat.send"),
                b -> onSubmit())
            .bounds(panelX + panelWidth - 34, panelY + panelHeight - 16, 30, 14)
            .build());
    }

    private void onSubmit() {
        if (isRequestInFlight()) { MineBroClient.conversation().cancel(); return; } // "Stop" mode, §6.2
        String text = this.input.getValue();
        if (text.isBlank()) return;
        this.input.setValue("");
        this.messageList.appendPlayerMessage(text);
        MineBroClient.conversation().submit(text);   // async — never blocks this method
    }

    @Override public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ENTER && this.input.isFocused()) { onSubmit(); return true; }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
```

Streaming/state updates arrive via `ChatSink`/`AvatarStateSink` callbacks (Phase 1 §5.5)
already marshalled onto the client thread by `MainThreadExecutor` — `MineBroChatScreen`
should register a listener with those sinks in `init()`/`removed()` (add/remove symmetric)
rather than polling, so message-list updates and the send/stop button's relabeling (§6.2)
happen reactively as the agent loop progresses.

### 15.4 `ConfirmActionScreen` — the single most load-bearing implementation detail

**This is the one place in the whole spec where getting the pattern wrong actually breaks
Phase 1's P2 invariant, not just the visual design.** The screen must be constructed so
that opening it **never blocks** the caller — the naive-but-wrong implementation is to have
`ScreenActionAuthority#request` open the screen and then synchronously wait for a button
click before returning, which is impossible to do correctly without blocking a thread (and
blocking the *client* thread with it, since `setScreen` must happen there). The correct
shape:

```java
// client/sink/ScreenActionAuthority.java — implements the `main`-declared ActionAuthority
public final class ScreenActionAuthority implements ActionAuthority {
    @Override
    public CompletableFuture<AuthDecision> request(ActionRequest request) {
        CompletableFuture<AuthDecision> future = new CompletableFuture<>();
        // Called from the worker thread (agent loop) — marshal the screen-open itself
        // onto the client thread; do NOT call Minecraft.getInstance() off-thread.
        MineBroClient.mainThread().run(() ->
            Minecraft.getInstance().setScreen(
                new ConfirmActionScreen(request, future::complete)));
        return future;   // returned immediately — the agent loop's caller awaits this
                          // future asynchronously via .thenApply/.thenCompose, exactly
                          // like any other step in Phase 1's agent loop (§5.1)
    }
}

public final class ConfirmActionScreen extends Screen {
    private final ActionRequest request;
    private final Consumer<AuthDecision> onDecision;
    private long openedAtMillis;
    private boolean decided = false;

    public ConfirmActionScreen(ActionRequest request, Consumer<AuthDecision> onDecision) {
        super(Component.translatable("minebro.confirm.title"));
        this.request = request;
        this.onDecision = onDecision;
    }

    @Override protected void init() {
        this.openedAtMillis = System.currentTimeMillis();
        // Deny pre-focused (§8.2 safety default), Allow / Allow-for-session / Deny buttons.
        Button deny = Button.builder(Component.translatable("minebro.confirm.deny"),
                b -> decide(AuthDecision.DENY)).bounds(/*...*/).build();
        addRenderableWidget(Button.builder(Component.translatable("minebro.confirm.allow"),
                b -> decide(AuthDecision.ALLOW)).bounds(/*...*/).build());
        addRenderableWidget(Button.builder(Component.translatable("minebro.confirm.allow_session"),
                b -> decide(AuthDecision.ALLOW_FOR_SESSION)).bounds(/*...*/).build());
        addRenderableWidget(deny);
        setInitialFocus(deny);
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        long elapsed = System.currentTimeMillis() - this.openedAtMillis;
        long remainingMs = request.timeout().toMillis() - elapsed;   // 30s default, Phase 1 §16.4
        if (remainingMs <= 0 && !decided) { decide(AuthDecision.TIMED_OUT); return; }
        // draw countdown text, tinted accent.negative in the final 5s (§8.2)
    }

    @Override public void onClose() {
        if (!decided) decide(AuthDecision.DENY);   // Escape = deny, per §8.1
        super.onClose();
    }

    private void decide(AuthDecision decision) {
        if (this.decided) return;               // guard against double-fire (e.g. timeout
        this.decided = true;                     // racing a click in the same frame)
        this.onDecision.accept(decision);
        this.minecraft.setScreen(null);          // return to gameplay immediately
    }
}
```

The properties that make this "a second lightweight `Screen`, not a blocking modal" are:
(1) `request()` returns its future before any button has been clicked — the agent loop's
worker thread is never parked waiting on this screen; (2) the countdown and timeout are
driven by `render()`'s own per-frame `partialTick`/wall-clock read, not a scheduled
blocking wait; (3) `Screen#isPauseScreen()`'s default (`true` for most vanilla screens) is
**acceptable to leave as default here**, unlike the chat screen — pausing singleplayer while
a destructive-action confirmation is pending is reasonable and arguably desirable (it stops
the world advancing while the player decides whether to let MineBro break 14 blocks), and
Phase 1's P2 is about the *client thread/render loop*, not about whether singleplayer's
game-logic pause flag is set; **VERIFY** this pause-on-open behavior matches product intent
during Phase 3 review, since it's a legitimate design choice either way, not a correctness
requirement like the chat screen's non-pausing behavior is.

### 15.5 Client command registration for the settings/chat entry points

Directly extending Phase 1 §13.1's own sketch, with the settings/chat-opening cases added:

```java
// src/client/java/com/minebro/client/command/MineBroClientCommands.java
ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
    dispatcher.register(
        ClientCommandManager.literal("minebro")
            .executes(ctx -> { printStatusSummary(ctx.getSource()); return Command.SINGLE_SUCCESS; })
            .then(ClientCommandManager.literal("settings")
                .executes(ctx -> {
                    Minecraft.getInstance().setScreen(new MineBroConfigScreen(null));
                    return Command.SINGLE_SUCCESS;
                }))
            .then(ClientCommandManager.literal("ask")
                .then(ClientCommandManager.argument("question", StringArgumentType.greedyString())
                    .executes(ctx -> {
                        MineBroClient.conversation().submit(
                            StringArgumentType.getString(ctx, "question"));
                        return Command.SINGLE_SUCCESS;
                    })))
            // .stop / .status / .inventory / .recipe / .craft / .model — same pattern,
            // each dispatching to the appropriate client-side controller/tool call,
            // never to a CommandSourceStack-shaped server path.
    ));
```

`Minecraft.getInstance().setScreen(...)` is only reachable here because this is a
**client** command (`ClientCommandManager`/`FabricClientCommandSource`) — this is the
concrete, code-level version of §11.1's point: had `/minebro settings` been left on the
current server-side registration (`CommandRegistrationCallback` in
`src/main/java/com/minebro/MineBro.java`), there would be no `Minecraft` instance
reachable from the command executor at all, and this design's settings-screen entry point
simply could not be implemented. **VERIFY** (Phase 1 §25.1-R6): confirm
`ClientCommandRegistrationCallback`/`ClientCommandManager`/`FabricClientCommandSource`'s
exact package paths and the `registryAccess` parameter shape against the resolved
0.116.15+1.21.1 artifact, and confirm client commands correctly coexist with (shadow, in
singleplayer) the currently-registered server-side `/minebro` command during the migration
window rather than conflicting with it.

### 15.6 Threading discipline recap for UI code specifically

Every one of the classes above is invoked from the client thread already (render
callbacks, screen `init`/button callbacks, command executors), so **UI code itself never
needs to hop threads to read game state** — the risk is entirely in the other direction:
UI code must never call a provider or perform blocking I/O directly. `MineBroChatScreen`'s
`onSubmit()` and `ConfirmActionScreen`'s decision callbacks only ever call into
`ConversationController`/complete a `CompletableFuture`, both of which are fire-and-forget
from the UI's perspective (Phase 1 §5.4/§5.5) — if an implementing engineer ever finds
themselves writing `.get()` or `.join()` on a future inside any `Screen` method, that is
the exact anti-pattern Phase 1's threading model (§5.4) and this document's §6.3 both
warn against, and it should be treated as a bug regardless of how small the blocking window
appears to be in testing.

---

*End of document. Phase 2 complete: no source files were modified, no build was run, no
Java was written. All version tags above reference Phase 1's roadmap (`docs/ARCHITECTURE.md`
§4); this document introduces no feature, tool, permission level, or provider outside that
roadmap, and flags its two proposed additions (the `LISTENING` presentation sub-state, §3.2,
and the `WARNING` enum extension proposed for v1.0, §3.3) explicitly as such.*
