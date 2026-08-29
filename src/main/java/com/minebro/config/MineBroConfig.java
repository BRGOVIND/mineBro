package com.minebro.config;

import com.minebro.tool.PermissionLevel;

/**
 * Plain mutable POJO (not a record tree) so Gson round-trips it without ceremony and users can
 * hand-edit the JSON file. Never put a literal API key default here; never log a populated
 * {@code openAiCompatApiKey} field verbatim (see provider.http.SecretRedactor).
 */
public class MineBroConfig {

    public String providerId = "ollama";

    public String ollamaEndpoint = "http://localhost:11434";
    public String ollamaModel = "mistral";

    public String openAiCompatEndpoint = "https://api.openai.com/v1";
    public String openAiCompatModel = "gpt-4o-mini";
    /** Stored in the local config file only - outside the source tree, never committed. Prefer the env var below. */
    public String openAiCompatApiKey = "";
    /** If set, overrides {@link #openAiCompatApiKey}. Recommended over storing the key in the JSON file. */
    public String openAiCompatApiKeyEnvVar = "MINEBRO_OPENAI_API_KEY";

    public double temperature = 0.2;
    public int maxTokens = 512;
    public int requestTimeoutSeconds = 60;

    public int maxToolIterations = 6;
    public int maxToolCallsPerTurn = 3;

    public PermissionLevel permissionLevel = PermissionLevel.SAFE_ACTIONS;

    public boolean debugLogRawResponses = false;

    /**
     * Suppresses the avatar's looping, oscillating and translating motion (DESIGN.md §12.5): the
     * idle flicker, the wake bloom, the rune rotation, the orbiting dot, the error shake, and the
     * chat panel's open/close tween. Colour crossfades are deliberately left running - the doc
     * classes them as low-vestibular-impact, and they carry state information a player still needs.
     */
    public boolean reducedMotion = false;

    public String resolveApiKey() {
        if (openAiCompatApiKeyEnvVar != null && !openAiCompatApiKeyEnvVar.isBlank()) {
            String fromEnv = System.getenv(openAiCompatApiKeyEnvVar);
            if (fromEnv != null && !fromEnv.isBlank()) {
                return fromEnv;
            }
        }
        return openAiCompatApiKey;
    }
}
