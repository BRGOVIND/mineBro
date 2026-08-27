# Contributing to MineBro

Thanks for your interest in MineBro. This document covers how to build the project, how it is
laid out, and the conventions a change is expected to follow.

## Requirements

- Java 21 (JDK)
- Minecraft Java Edition 1.21.1
- Ollama, or any OpenAI-compatible HTTP endpoint, if you want to exercise the model path

Fabric Loader and Fabric API are resolved by the Gradle build; no manual install is needed.

## Build and run

```powershell
.\gradlew.bat build       # compile, run unit tests, produce the mod jar
.\gradlew.bat test        # unit tests only
.\gradlew.bat runClient   # launch the development client with the mod loaded
```

On Linux/macOS use `./gradlew` instead of `.\gradlew.bat`.

## Source layout

MineBro uses Fabric's split source sets. Which set a class belongs in is a correctness
concern, not a style preference: common code must not reference client-only Minecraft types.

```
src/main/java/com/minebro/       common code, safe on either side
  agent/        the tool-calling loop, conversation state, prompt assembly
  config/       config file model and persistence
  context/      game-state snapshots handed to the model
  core/         cancellation, threading primitives
  provider/     AI provider interface, registry, HTTP adapters, wire models
  recipe/       recipe indexing, craftability solving, ingredient math
  tool/         tool interface, registry, permission gate, tool implementations

src/client/java/com/minebro/client/   client-only code
  command/      /minebro client commands
  hud/          HUD avatar and its state
  input/        key bindings
  screen/       chat and settings screens
  state/        client runtime and integrated-server bridge
  thread/       client-thread marshalling

src/test/java/com/minebro/            unit tests, mirroring the packages above
```

## Core design rule

The model reasons and phrases answers. Deterministic Java code reads and mutates the game and
decides success or failure. A change that lets model output stand in for real game state —
an inventory count, a recipe, or a success/failure result — will not be accepted.

Concretely:

- Never report an action as successful until the game state change has been verified.
- Never let the model supply a value that can be read from Minecraft directly.
- Never block the client thread. Provider calls and the agent loop run off-thread; only
  `ToolExecutor` hops back onto the client thread, per tool call.

## Testing

Unit tests cover pure logic only: ingredient aggregation, JSON tool-call parsing, provider
request/response shaping, tool executor gate ordering, cancellation, and the tool-result
contract.

Tests must not require a Minecraft bootstrap. Constructing real registry objects (`Item`,
`Ingredient`, anything touching `BuiltInRegistries`) in a plain JUnit test throws during static
initialization, so behavior that genuinely depends on live game state is verified by hand in
the development client instead. `docs/MANUAL_TEST_CHECKLIST.md` tracks that manual pass.

If you add logic that can be expressed without Minecraft types, add a test for it. If it
cannot, say so in the pull request and note what you verified manually.

## Conventions

- Match the surrounding code. The codebase favours explanatory comments where a decision is
  non-obvious, and no comment where the code already says it.
- Keep public API surface small; prefer package-private unless a class is genuinely shared.
- Do not reformat files you are not otherwise changing.

## Pull requests

Before opening one:

1. `.\gradlew.bat build` passes, tests included.
2. `.\gradlew.bat runClient` still starts cleanly if you touched client code.
3. The manual checklist items relevant to your change have been re-run, if applicable.

Describe what you verified and what you did not. An honest "not tested in multiplayer" is more
useful than silence.
