# MineBro

MineBro is an AI companion for Minecraft Java Edition. So its not that big of a deal, as a longtime minecraft player i would never use this.
But for someone starting out and if they dont like to take time and figure out every bits and pieaces on their own, mineBro is for them.

The model reasons and phrases answers; deterministic Java code reads and mutates the game and
decides success or failure. The model never invents an inventory count, a recipe, or a
success/failure result.

## Features

- AI backend: Ollama or any OpenAI-compatible API
- Async and cancellable requests
- Grounded Minecraft inventory, recipe, and player state
- Verified crafting and tool actions
- In-game chat and settings
- Floating HUD avatar with live state feedback
- Deterministic commands that bypass the model when possible
- Provider switching without restarting Minecraft

## Tech Stack

- Minecraft Java Edition 1.21.1
- Fabric
- Java 21
- Gradle / Fabric Loom
- Ollama or any OpenAI-compatible HTTP endpoint

## Installation

### Requirements

- Minecraft Java Edition 1.21.1
- Java 21 JDK
- Git
- Ollama or an OpenAI-compatible API

### Clone

```bash
git clone https://github.com/BRGOVIND/mineBro.git
cd mineBro
