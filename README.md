# MineBro

MineBro is an AI companion for Minecraft Java Edition. It answers questions and performs a
small set of real in-game actions - checking inventory, looking up recipes, crafting - using
Minecraft's actual game state as the source of truth rather than a language model's guess.

The model reasons and phrases answers; deterministic Java code reads and mutates the game and
decides success or failure. The model never invents an inventory count, a recipe, or a
success/failure result.

Full design documentation lives in [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) (system
architecture) and [`docs/DESIGN.md`](docs/DESIGN.md) (UI/UX design).

## Key Features

- Provider-agnostic AI backend: local (Ollama) or any OpenAI-compatible HTTP endpoint
- Fully asynchronous request pipeline; never blocks the client thread
- Deterministic, hallucination-resistant tool execution (inventory, recipes, crafting) backed
  by real Minecraft state, not model output
- Verified crafting execution: an action is only reported as successful after the inventory
  change is confirmed
- Cancellable in-flight requests
- In-game chat screen showing tool steps as they execute, and a settings screen that applies
  provider changes without restarting Minecraft
- Minimal HUD avatar with state feedback (idle, thinking, error, offline)

## Tech Stack

- Minecraft Java Edition 1.21.1
- Fabric Loader and Fabric API
- Java 21
- Gradle with Fabric Loom
- Ollama or any OpenAI-compatible HTTP endpoint (LM Studio, llama.cpp, vLLM, OpenRouter, OpenAI)

## Architecture

```
/minebro <question> -> AgentLoop -> AIProvider -> tools -> Minecraft state -> ToolResult -> chat
```

The AI model can only call a fixed set of tools (inventory lookup, recipe lookup, crafting,
etc.). Every tool reads or mutates the real game and returns a structured result; the model
never has direct access to game state or execution. See `docs/ARCHITECTURE.md` for the full
design, including the provider abstraction, threading model, and hallucination mitigations.

Source layout:

```
src/main/java/com/minebro/            common: provider, tool, agent, recipe, context, config
src/client/java/com/minebro/client/   client-only: HUD, screens, keybinds, commands, bridges
src/test/java/com/minebro/            unit tests
```

## Installation and Setup

### Requirements

- Minecraft Java Edition 1.21.1
- Java 21 (JDK)
- Git
- One of:
  - [Ollama](https://ollama.com/) installed locally, or
  - Any OpenAI-compatible HTTP endpoint (LM Studio, llama.cpp `server`, vLLM, OpenRouter, OpenAI)

Fabric Loader is downloaded automatically by the Gradle build; no manual install needed.

### 1. Clone the project

```bash
git clone <repository-url>
cd minebro-template-1.21.1
```

### 2. Set up an AI provider

**Option A: Ollama (local, default)**

```bash
ollama pull mistral
```

Ollama listens on `http://localhost:11434` by default, which matches MineBro's default
configuration, so no further setup is required. Verify it is running:

```bash
curl http://localhost:11434/api/tags
```

**Option B: OpenAI-compatible endpoint**

See [Configuration](#configuration) below to point MineBro at LM Studio, llama.cpp, vLLM,
OpenRouter, OpenAI, or another compatible server.

### 3. Build the project

```powershell
.\gradlew.bat build
```

### 4. Run the development client

```powershell
.\gradlew.bat runClient
```

This launches Minecraft with the mod loaded. MineBro writes its config file on first launch
(see below).

## Configuration

MineBro writes a config file to `config/minebro/config.json` relative to the Minecraft
instance directory (in the dev environment: `run/config/minebro/config.json`) on first run.
Provider, endpoint, model, API key, and permission level can be changed in-game via
`/minebro settings` and apply immediately. The remaining fields are edited in this file and
require a restart:

```json
{
  "providerId": "ollama",
  "ollamaEndpoint": "http://localhost:11434",
  "ollamaModel": "mistral",
  "openAiCompatEndpoint": "https://api.openai.com/v1",
  "openAiCompatModel": "gpt-4o-mini",
  "openAiCompatApiKey": "",
  "openAiCompatApiKeyEnvVar": "MINEBRO_OPENAI_API_KEY",
  "permissionLevel": "SAFE_ACTIONS"
}
```

- `providerId`: `"ollama"` or `"openai-compatible"`.
- `openAiCompatApiKeyEnvVar`: name of an environment variable holding the API key (default
  `MINEBRO_OPENAI_API_KEY`). Preferred over storing the key directly in `openAiCompatApiKey`.
  The config file lives outside the source tree (under `run/`, which is git-ignored) and is
  never logged or displayed in full.
- `permissionLevel`: `READ_ONLY`, `SAFE_ACTIONS`, `GAMEPLAY_ACTIONS`, or `DESTRUCTIVE_ACTIONS`.
  Default `SAFE_ACTIONS` enables all read-only tools plus crafting.

MineBro does not and will not support ChatGPT Plus or Claude Pro subscriptions; only
documented HTTP APIs with a user-supplied key, or a local runtime with an HTTP endpoint.

## AI Providers

Two provider types, selected by `providerId`:

- **`"ollama"`** - local, no API key. Set `ollamaEndpoint`/`ollamaModel`.
- **`"openai-compatible"`** - any server (local or cloud) that speaks the OpenAI
  `/v1/chat/completions` and `/v1/models` shape: LM Studio, llama.cpp, vLLM, OpenAI, Groq,
  OpenRouter, or a custom server you run yourself. Set `openAiCompatEndpoint`/
  `openAiCompatModel`/an API key if the server requires one. There is no per-vendor code -
  any endpoint speaking this standard shape works through configuration alone.

Example: pointing MineBro at a self-hosted OpenAI-compatible server instead of a named vendor:

```json
{
  "providerId": "openai-compatible",
  "openAiCompatEndpoint": "https://my-ai-server.example.com/v1",
  "openAiCompatModel": "my-model",
  "openAiCompatApiKeyEnvVar": "MINEBRO_OPENAI_API_KEY"
}
```

`/minebro status` reports whether the current provider is reachable and (for cloud/custom
providers) whether an API key is configured. `/minebro models` lists models the provider
reports, when it supports discovery - a provider that doesn't is not a problem, since any
model name can always be entered directly in the config file.

MineBro is not limited to a fixed list of "supported" cloud vendors - it supports its two
built-in adapters (Ollama, and any server implementing the OpenAI-compatible shape above)
through configuration, without source changes. It does not claim to support arbitrary,
differently-shaped APIs (e.g. Anthropic's or Google's own message formats) unless a dedicated
adapter for them is actually implemented.

## Usage

| Command | Description |
|---|---|
| `/minebro` | Lists available commands and opens the chat screen (also bound to `B`). |
| `/minebro <question>` | Sends the question and current game state to the configured model. |
| `/minebro inventory` | Prints aggregated inventory. Deterministic, no model call. |
| `/minebro recipe <item>` | Looks up the real recipe(s) for an item. Deterministic, no model call. |
| `/minebro craft <item> [quantity]` | Attempts to craft immediately. Deterministic, no model call. |
| `/minebro status` | Shows the configured provider/model, whether it's local or cloud, and checks connectivity. |
| `/minebro settings` | Shows current config and config file path, and opens the settings screen. |
| `/minebro models` | Lists models the current provider reports, if it supports discovery. |
| `/minebro stop` | Cancels an in-flight request. |

`inventory`, `recipe`, `craft`, and `status` are deterministic and do not depend on the model.
Only free-text questions are routed through the model.

## Development / Build Commands

```powershell
.\gradlew.bat build       # build the mod
.\gradlew.bat runClient   # launch the dev client
.\gradlew.bat test        # run unit tests
```

## Testing

Unit tests cover pure logic: ingredient aggregation, JSON tool-call parsing, provider
request/response shaping, tool executor gate order, cancellation, and the tool-result contract.
Run with:

```powershell
.\gradlew.bat test
```

Behavior that depends on live Minecraft state (inventory, recipes, world) is verified manually
through the development client, since constructing real Minecraft registry objects in a unit
test requires a full game bootstrap that is not available in the test environment.

## Current Limitations

- Crafting requires a crafting table to be within reach for 3x3 recipes; recipes are executed
  against the player's inventory rather than through a rendered crafting grid.
- No native provider tool-calling; MineBro always uses a JSON-prompt convention for
  compatibility across models.
- Raising the permission level in the settings screen takes effect immediately for the tool
  execution gate, but newly permitted tools are not advertised to the model until Minecraft
  is restarted.
- Movement, block placing/breaking, container interaction, and combat are not implemented.
- Multiplayer and dedicated servers are out of scope; crafting is restricted to local
  singleplayer worlds.

See `docs/ARCHITECTURE.md` for the full roadmap.

## Contributing

Build instructions, source layout, and the conventions a change is expected to follow are in
[`CONTRIBUTING.md`](CONTRIBUTING.md). Participation is governed by
[`CODE_OF_CONDUCT.md`](CODE_OF_CONDUCT.md).

To report a security problem — including anything involving API key handling — see
[`SECURITY.md`](SECURITY.md). Please do not open a public issue for it.

Release history is in [`CHANGELOG.md`](CHANGELOG.md).

## License

CC0, inherited from the Fabric example template this project started from.
