# Security Policy

## Supported versions

MineBro targets a single Minecraft version at a time. Only the most recent release receives
security fixes.

| Version | Minecraft | Supported |
|---|---|---|
| 1.0.x | 1.21.1 | Yes |
| older | — | No |

## Reporting a vulnerability

Please do not open a public issue for a security problem.

Report it privately to the maintainers through GitHub's private vulnerability reporting
("Report a vulnerability" on the repository's Security tab). Include what you did, what
happened, and what you expected. A proof of concept helps, but a clear description is enough.

Expect an acknowledgement within a few days. If a fix is warranted, we will coordinate
disclosure timing with you.

## Handling of API keys

MineBro can be pointed at a remote provider that requires an API key. How that key is handled
is the most security-sensitive part of the mod, so it is documented explicitly.

- Keys are read from an environment variable named by `openAiCompatApiKeyEnvVar` (default
  `MINEBRO_OPENAI_API_KEY`), or, as a fallback, from `openAiCompatApiKey` in the config file.
  The environment variable takes priority when both are present.
- The config file lives under the Minecraft instance directory (`config/minebro/config.json`),
  outside the source tree. In the development environment that resolves to `run/`, which is
  git-ignored.
- Keys are never written to logs. Anywhere a key is displayed — `/minebro settings`, the
  settings screen — it is passed through a redactor that shows only the last four characters.
- The settings screen deliberately has no "reveal key" toggle. A saved key can be replaced,
  never displayed, so that a screenshot or a screen recording cannot leak it.
- Keys are sent only to the endpoint you configure, as an `Authorization` header. MineBro
  contacts no other network host of its own accord.

If you store a key directly in the config file rather than an environment variable, treat that
file as a secret: do not commit it, and do not include it in a bug report.

## Scope of what the mod can do

MineBro's model can only invoke a fixed, registered set of tools. It cannot execute arbitrary
code, run shell commands, read or write files, or make network requests of its own. Tools are
additionally gated by a configurable permission level (`READ_ONLY`, `SAFE_ACTIONS`,
`GAMEPLAY_ACTIONS`, `DESTRUCTIVE_ACTIONS`), checked at execution time.

If you find a way to make the model reach beyond that boundary — invoking an unregistered
tool, bypassing the permission gate, or causing a tool to act outside its documented effect —
that is a vulnerability and we would like to hear about it.
