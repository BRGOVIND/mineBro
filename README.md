# MineBro

MineBro is an AI companion for Minecraft Java Edition. So its not that big of a deal, as a longtime minecraft player i would never use this.
But for someone starting out and if they dont like to take time and figure out every bits and pieaces on their own, mineBro is for them.

The model reasons and phrases answers; deterministic Java code reads and mutates the game and
decides success or failure. The model never invents an inventory count, a recipe, or a
success/failure result.

Full design documentation lives in [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) (system
architecture) and [`docs/DESIGN.md`](docs/DESIGN.md) (UI/UX design).

## Key Features

- AI backend: Ollama or any OpenAI-compatible API
- Async pipeline: Non-blocking and cancellable
- Grounded tools: Real Minecraft state for inventory, recipes, and crafting
- Verified actions: Confirms results before reporting success
- In-game UI: Chat, settings, and live tool execution
- HUD avatar: Visual feedback for MineBro's state

## Tech Stack

- Minecraft Java Edition 1.21.1
- Fabric Loader and Fabric API
- Java 21
- Gradle with Fabric Loom
- Ollama or any OpenAI-compatible HTTP endpoint (LM Studio, llama.cpp, vLLM, OpenRouter, OpenAI)


## Installation and Setup

### Requirements

- Minecraft Java Edition 1.21.1
- Java 21 (JDK)
- Git
- One of:
  - [Ollama](https://ollama.com/) installed locally, or
  - Any OpenAI-compatible HTTP endpoint (LM Studio, llama.cpp `server`, vLLM, OpenRouter, OpenAI)

Fabric Loader is downloaded automatically by the Gradle build; Its already in there. dw

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
providers) whether an API key is configured.
`/minebro models` lists models the provider reports, when it supports discovery - a provider that doesn't is not a problem, since any model name can always be entered directly in the config file.

MineBro is not limited to a fixed list of "supported" cloud vendors - it supports its two
built-in adapters (Ollama, and any server implementing the OpenAI-compatible shape above) thats what it was made for first, 
through configuration, without source changes. It does not claim to support arbitrary,
differently-shaped APIs (e.g. Anthropic's or Google's own message formats) unless a dedicated
adapter for them is actually implemented.

## Usage ( Use it well, I'd still recommend playing the game urself )

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

## Architecture

guys if someones actually going througgh it, read architecture.md, I made claude draft it.
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
